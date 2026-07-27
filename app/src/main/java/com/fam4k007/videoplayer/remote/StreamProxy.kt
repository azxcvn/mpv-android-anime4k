package com.fam4k007.videoplayer.remote

import com.fam4k007.videoplayer.utils.Logger
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 本地 HTTP 流代理
 * 解决 mpv 内置 mbedTLS 与部分 CDN（如 Bilibili）TLS 不兼容的问题：
 * 用 OkHttp（Android 原生 TLS）下载远程流，mpv 从 localhost 播放。
 */
object StreamProxy {

    private const val TAG = "StreamProxy"
    private var server: ProxyServer? = null
    private var port: Int = -1

    /** 已注册的流映射：路径 → (URL, Headers) */
    private val streams = mutableMapOf<String, Pair<String, Map<String, String>>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Synchronized
    fun start(): String? {
        if (server != null && port > 0) return "http://127.0.0.1:$port"

        server = ProxyServer(streams, client).apply {
            try {
                start()
                port = listeningPort
                Logger.d(TAG, "StreamProxy started on port $port")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start StreamProxy", e)
                port = -1
            }
        }
        return if (port > 0) "http://127.0.0.1:$port" else null
    }

    @Synchronized
    fun register(key: String, url: String, headers: Map<String, String>): String {
        streams[key] = url to headers
        return "http://127.0.0.1:$port/$key"
    }

    fun registerStreams(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>
    ): Pair<String?, String?> {
        val base = start() ?: return null to null
        val localVideo = register("video", videoUrl, headers)
        val localAudio = audioUrl?.let { register("audio", it, headers) }
        Logger.d(TAG, "Registered: video=$localVideo, audio=$localAudio")
        return localVideo to localAudio
    }

    @Synchronized
    fun stop() {
        streams.clear()
        server?.stop()
        server = null
        port = -1
        Logger.d(TAG, "StreamProxy stopped")
    }
}

private class ProxyServer(
    private val streams: MutableMap<String, Pair<String, Map<String, String>>>,
    private val client: OkHttpClient,
) : NanoHTTPD(0) {

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.substring(1)
        val stream = streams[path]
        if (stream == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
        val (url, headers) = stream
        Logger.d("StreamProxy", "Proxying: $path -> $url")

        return try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            session.headers["range"]?.let { requestBuilder.addHeader("Range", it) }

            val okResp = client.newCall(requestBuilder.build()).execute()
            val contentType = okResp.header("Content-Type") ?: "application/octet-stream"
            val bodyStream = okResp.body?.byteStream()

            if (bodyStream != null) {
                val contentLength = okResp.header("Content-Length")
                val resp = if (contentLength != null) {
                    newFixedLengthResponse(Response.Status.OK, contentType, bodyStream, contentLength.toLong())
                } else {
                    // totalBytes = -1 触发 chunked 传输，避免将大文件全部读入内存
                    newFixedLengthResponse(Response.Status.OK, contentType, bodyStream, -1L)
                }
                okResp.header("Content-Range")?.let { resp.addHeader("Content-Range", it) }
                okResp.header("Accept-Ranges")?.let { resp.addHeader("Accept-Ranges", it) }
                resp
            } else {
                newFixedLengthResponse(Response.Status.NO_CONTENT, contentType, "")
            }
        } catch (e: Exception) {
            Logger.w("StreamProxy", "Proxy error for $url: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy Error: ${e.message}")
        }
    }
}
