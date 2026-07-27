package com.fam4k007.videoplayer.domain.webdav

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 本地 HTTP 流代理服务器
 * 用于将 WebDAV 远程文件代理为本地 HTTP 流，解决 mpv 直接处理
 * 嵌入凭据 URL 时的兼容性问题（seek、Range 请求等）。
 *
 * 参考：mpvEx / mpvRx-CN 的 NetworkStreamingProxy 实现
 */
class WebDavStreamingProxy private constructor() : NanoHTTPD("127.0.0.1", 0) {

    companion object {
        private const val TAG = "WebDavStreamingProxy"

        @Volatile
        private var instance: WebDavStreamingProxy? = null

        @JvmStatic
        fun getInstance(): WebDavStreamingProxy {
            return instance ?: synchronized(this) {
                instance ?: WebDavStreamingProxy().also {
                    it.start()
                    Log.d(TAG, "Proxy started on port ${it.listeningPort}")
                    instance = it
                }
            }
        }

        @JvmStatic
        fun stopInstance() {
            synchronized(this) {
                instance?.let { proxy ->
                    Log.d(TAG, "Stopping proxy on port ${proxy.listeningPort}")
                    proxy.stop()
                    proxy.cleanup()
                    instance = null
                }
            }
        }
    }

    // ==================== 流信息 ====================

    data class StreamInfo(
        val config: WebDavConfig,
        val filePath: String,
        val fileSize: Long = -1L,
        val mimeType: String = "video/mp4"
    )

    private val activeStreams = ConcurrentHashMap<String, StreamInfo>()

    // 每个流使用独立的 OkHttpClient，避免连接复用导致的冲突
    private val httpClients = ConcurrentHashMap<String, OkHttpClient>()

    // ==================== 流注册 ====================

    /**
     * 注册一个 WebDAV 流并返回本地代理 URL
     * @param streamId 唯一流ID
     * @param config WebDAV 配置
     * @param filePath 远程文件相对路径
     * @param fileSize 文件大小（-1 表示未知）
     * @param mimeType MIME 类型
     * @return 代理 URL（如 http://127.0.0.1:54321/streamId）
     */
    fun registerStream(
        streamId: String,
        config: WebDavConfig,
        filePath: String,
        fileSize: Long = -1L,
        mimeType: String = "video/mp4"
    ): String {
        val streamInfo = StreamInfo(
            config = config,
            filePath = filePath,
            fileSize = fileSize,
            mimeType = mimeType
        )
        activeStreams[streamId] = streamInfo

        // 为流创建专用的 OkHttpClient
        httpClients[streamId] = buildHttpClient(config)

        Log.d(TAG, "Registered stream: $streamId -> $filePath (mime=$mimeType)")
        return "http://127.0.0.1:$listeningPort/$streamId"
    }

    /**
     * 注销流并释放资源
     */
    fun unregisterStream(streamId: String) {
        activeStreams.remove(streamId)
        httpClients.remove(streamId)
        Log.d(TAG, "Unregistered stream: $streamId")
    }

    /**
     * 清理所有活动流
     */
    private fun cleanup() {
        activeStreams.keys.toList().forEach { unregisterStream(it) }
        Log.d(TAG, "All streams cleaned up")
    }

    // ==================== HTTP 请求处理 ====================

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val streamId = uri.removePrefix("/").split("/").firstOrNull() ?: ""
        val streamInfo = activeStreams[streamId]

        if (streamInfo == null) {
            Log.w(TAG, "Stream not found: $streamId")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Stream not found"
            )
        }

        val rangeHeader = session.headers["range"]
        val isHeadRequest = session.method == Method.HEAD

        return when {
            isHeadRequest -> handleHeadRequest(streamInfo)
            rangeHeader != null && rangeHeader.startsWith("bytes=") ->
                handleRangeRequest(streamId, streamInfo, rangeHeader)
            else -> handleFullRequest(streamId, streamInfo)
        }
    }

    // ==================== HEAD 请求（探测定价） ====================

    private fun handleHeadRequest(streamInfo: StreamInfo): Response {
        val fileSize = if (streamInfo.fileSize > 0) streamInfo.fileSize else getFileSize(streamInfo)
        val response = newFixedLengthResponse(
            Response.Status.OK,
            streamInfo.mimeType,
            "".byteInputStream(),  // 空 body，NanoHTTPD 对 HEAD 会自动去除
            if (fileSize > 0) fileSize else 0
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (fileSize > 0) {
            response.addHeader("Content-Length", fileSize.toString())
        }
        return response
    }

    // ==================== 完整文件请求 ====================

    private fun handleFullRequest(streamId: String, streamInfo: StreamInfo): Response {
        try {
            val fileSize = if (streamInfo.fileSize > 0) streamInfo.fileSize else getFileSize(streamInfo)
            val inputStream = openInputStream(streamInfo, 0)

            val response = newFixedLengthResponse(
                Response.Status.OK,
                streamInfo.mimeType,
                inputStream,
                fileSize
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", fileSize.toString())
            if (fileSize > 0) {
                response.addHeader("Content-Range", "bytes 0-${fileSize - 1}/$fileSize")
            }
            return response
        } catch (e: Exception) {
            Log.e(TAG, "Full request failed for $streamId", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Failed to read file: ${e.message}"
            )
        }
    }

    // ==================== Range 请求（Seek） ====================

    private fun handleRangeRequest(
        streamId: String,
        streamInfo: StreamInfo,
        rangeHeader: String
    ): Response {
        try {
            val fileSize = if (streamInfo.fileSize > 0) streamInfo.fileSize else getFileSize(streamInfo)
            if (fileSize <= 0) {
                return handleFullRequest(streamId, streamInfo)
            }

            val rangeValue = rangeHeader.removePrefix("bytes=")
            val parts = rangeValue.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull()
            val rangeEnd = end ?: (fileSize - 1)
            val contentLength = rangeEnd - start + 1

            Log.d(TAG, "Range request for $streamId: bytes=$start-$rangeEnd/$fileSize")

            val inputStream = openInputStream(streamInfo, start)

            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                streamInfo.mimeType,
                inputStream,
                contentLength
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Range", "bytes $start-$rangeEnd/$fileSize")
            response.addHeader("Content-Length", contentLength.toString())
            return response
        } catch (e: Exception) {
            Log.e(TAG, "Range request failed for $streamId", e)
            // 降级为完整请求
            return handleFullRequest(streamId, streamInfo)
        }
    }

    // ==================== 底层 HTTP 操作 ====================

    /**
     * 打开到 WebDAV 服务器的输入流
     * @param streamInfo 流信息
     * @param offset 起始偏移（0 = 完整文件）
     */
    private fun openInputStream(streamInfo: StreamInfo, offset: Long): InputStream {
        val config = streamInfo.config
        val url = buildRemoteUrl(config, streamInfo.filePath)

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        if (offset > 0) {
            requestBuilder.addHeader("Range", "bytes=$offset-")
        }

        val client = httpClients[streamInfo.filePath] ?: buildHttpClient(config)
        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful && response.code != 206) {
            throw java.io.IOException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw java.io.IOException("Empty response body")
        return body.byteStream()
    }

    /**
     * 通过 PROPFIND 获取文件大小
     */
    private fun getFileSize(streamInfo: StreamInfo): Long {
        return try {
            val sardine = buildSardine(streamInfo.config)
            val url = buildRemoteUrl(streamInfo.config, streamInfo.filePath)
            val dirUrl = url.substringBeforeLast("/")
            val fileName = url.substringAfterLast("/")
            val resources = sardine.list(dirUrl, 1)
            resources.find { it.name == fileName }?.contentLength ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get file size for ${streamInfo.filePath}", e)
            -1L
        }
    }

    /**
     * 构建远程 WebDAV URL
     */
    private fun buildRemoteUrl(config: WebDavConfig, filePath: String): String {
        val cleanPath = filePath.trimStart('/')
        return if (cleanPath.isEmpty()) {
            config.serverUrl
        } else {
            "${config.serverUrl.trimEnd('/')}/$cleanPath"
        }
    }

    /**
     * 为指定配置构建 OkHttpClient
     */
    private fun buildHttpClient(config: WebDavConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // 信任所有 SSL 证书（自签名）
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {}
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }

        // 非匿名访问时添加 Basic Auth 拦截器
        if (!config.isAnonymous && config.account.isNotEmpty()) {
            builder.addInterceptor { chain ->
                val original = chain.request()
                val authenticated = original.newBuilder()
                    .header("Authorization", Credentials.basic(config.account, config.password))
                    .build()
                chain.proceed(authenticated)
            }
        }

        return builder.build()
    }

    /**
     * 构建 Sardine 实例（用于 PROPFIND）
     */
    private fun buildSardine(config: WebDavConfig): com.xyoye.sardine.Sardine {
        val okHttpClient = buildHttpClient(config)
        return com.xyoye.sardine.impl.OkHttpSardine(okHttpClient)
    }
}
