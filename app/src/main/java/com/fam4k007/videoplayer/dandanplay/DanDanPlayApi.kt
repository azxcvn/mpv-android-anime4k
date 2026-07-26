package com.fam4k007.videoplayer.dandanplay

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * DanDanPlay API 服务
 * API 文档: https://api.dandanplay.net/swagger/index.html
 */
class DanDanPlayApi(private val customBaseUrl: String? = null) {
    companion object {
        private const val TAG = "DanDanPlayApi"
        private const val DEFAULT_BASE_URL = "https://api.dandanplay.net"
        
        // API 密钥 - 从BuildConfig读取
        private val APP_ID = com.fam4k007.videoplayer.BuildConfig.DANDANPLAY_APP_ID
        private val APP_SECRET = com.fam4k007.videoplayer.BuildConfig.DANDANPLAY_APP_SECRET
    }

    private val baseUrl: String
        get() {
            val url = customBaseUrl?.takeIf { it.isNotBlank() }?.trimEnd('/')
            if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
                Log.w(TAG, "Invalid customBaseUrl (no scheme): $url, falling back to default")
                return DEFAULT_BASE_URL
            }
            return url ?: DEFAULT_BASE_URL
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 生成签名（签名验证模式）
     * 算法: base64(sha256(AppId + Timestamp + Path + AppSecret))
     */
    private fun generateSignature(timestamp: Long, path: String): String {
        val data = "$APP_ID$timestamp$path$APP_SECRET"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * 搜索动漫
     * GET /api/v2/search/episodes
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    suspend fun searchAnime(keyword: String): Result<SearchAnimeResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                // API路径（用于签名计算）
                val path = "/api/v2/search/episodes"
                val url = "$baseUrl$path?anime=$encodedKeyword"
                
                // 生成时间戳和签名
                val timestamp = System.currentTimeMillis() / 1000
                val signature = generateSignature(timestamp, path)
                
                Log.d(TAG, "========== DanDanPlay API 搜索 ==========")
                Log.d(TAG, "原始关键词: $keyword")
                Log.d(TAG, "编码后关键词: $encodedKeyword")
                Log.d(TAG, "API路径: $path")
                Log.d(TAG, "完整URL: $url")
                Log.d(TAG, "时间戳: $timestamp")
                Log.d(TAG, "签名: $signature")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Encoding", "gzip, deflate")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    // 添加DanDanPlay API必需的身份验证头（签名验证模式）
                    .addHeader("X-AppId", APP_ID)
                    .addHeader("X-Timestamp", timestamp.toString())
                    .addHeader("X-Signature", signature)
                    .build()

                Log.d(TAG, "请求头:")
                request.headers.forEach { (name, value) ->
                    Log.d(TAG, "  $name: $value")
                }

                val response = client.newCall(request).execute()
                
                Log.d(TAG, "响应状态码: ${response.code}")
                Log.d(TAG, "响应消息: ${response.message}")
                Log.d(TAG, "Content-Encoding: ${response.header("Content-Encoding")}")
                Log.d(TAG, "响应头:")
                response.headers.forEach { (name, value) ->
                    Log.d(TAG, "  $name: $value")
                }
                
                // 检查错误信息头（403时会有详细错误信息）
                response.header("X-Error-Message")?.let { errorMsg ->
                    Log.e(TAG, "服务器错误信息: $errorMsg")
                }
                
                // 处理 GZIP 压缩（与getDanmaku一致）
                val responseBody = if (response.header("Content-Encoding") == "gzip") {
                    Log.d(TAG, "解压GZIP响应")
                    val gzipStream = GZIPInputStream(response.body?.byteStream())
                    val outputStream = ByteArrayOutputStream()
                    gzipStream.copyTo(outputStream)
                    outputStream.toString("UTF-8")
                } else {
                    response.body?.string()
                }
                
                Log.d(TAG, "响应体长度: ${responseBody?.length ?: 0}")
                Log.d(TAG, "响应体内容: ${responseBody?.take(500)}")  // 只打印前500字符

                if (response.isSuccessful && responseBody != null) {
                    val searchResponse = gson.fromJson(responseBody, SearchAnimeResponse::class.java)
                    Log.d(TAG, "解析成功，找到 ${searchResponse.animes.size} 个结果")
                    Result.success(searchResponse)
                } else {
                    val errorMsg = "Search failed: ${response.code} - ${response.message}"
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "错误响应体: $responseBody")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "搜索异常", e)
                Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "异常消息: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * 获取弹幕
     * GET /api/v2/comment/{episodeId}
     * @param episodeId 剧集ID
     * @return 弹幕列表
     */
    suspend fun getDanmaku(episodeId: Int): Result<DanmakuResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // API路径（用于签名计算）
                val path = "/api/v2/comment/$episodeId"
                val url = "$baseUrl$path?withRelated=true"
                
                // 生成时间戳和签名
                val timestamp = System.currentTimeMillis() / 1000
                val signature = generateSignature(timestamp, path)
                
                Log.d(TAG, "========== DanDanPlay API 获取弹幕 ==========")
                Log.d(TAG, "剧集ID: $episodeId")
                Log.d(TAG, "API路径: $path")
                Log.d(TAG, "完整URL: $url")
                Log.d(TAG, "时间戳: $timestamp")
                Log.d(TAG, "签名: $signature")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Encoding", "gzip, deflate")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    // 添加DanDanPlay API必需的身份验证头（签名验证模式）
                    .addHeader("X-AppId", APP_ID)
                    .addHeader("X-Timestamp", timestamp.toString())
                    .addHeader("X-Signature", signature)
                    .build()

                Log.d(TAG, "请求头:")
                request.headers.forEach { (name, value) ->
                    Log.d(TAG, "  $name: $value")
                }

                val response = client.newCall(request).execute()
                
                Log.d(TAG, "响应状态码: ${response.code}")
                Log.d(TAG, "响应消息: ${response.message}")
                Log.d(TAG, "Content-Encoding: ${response.header("Content-Encoding")}")
                
                // 检查错误信息头
                response.header("X-Error-Message")?.let { errorMsg ->
                    Log.e(TAG, "服务器错误信息: $errorMsg")
                }
                
                // 处理 GZIP 压缩
                val responseBody = if (response.header("Content-Encoding") == "gzip") {
                    Log.d(TAG, "解压GZIP响应")
                    val gzipStream = GZIPInputStream(response.body?.byteStream())
                    val outputStream = ByteArrayOutputStream()
                    gzipStream.copyTo(outputStream)
                    outputStream.toString("UTF-8")
                } else {
                    response.body?.string()
                }

                Log.d(TAG, "响应体长度: ${responseBody?.length ?: 0}")
                Log.d(TAG, "响应体内容: ${responseBody?.take(500)}")

                if (response.isSuccessful && responseBody != null) {
                    val danmakuResponse = gson.fromJson(responseBody, DanmakuResponse::class.java)
                    Log.d(TAG, "解析成功，获得 ${danmakuResponse.count} 条弹幕")
                    Result.success(danmakuResponse)
                } else {
                    val errorMsg = "Failed to fetch danmaku: ${response.code} - ${response.message}"
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "错误响应体: $responseBody")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取弹幕异常", e)
                Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "异常消息: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * 将弹幕转换为 Bilibili XML 格式
     */
    fun convertToXml(danmakuResponse: DanmakuResponse): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        xmlBuilder.append("<i>\n")
        xmlBuilder.append("  <chatserver>chat.bilibili.com</chatserver>\n")
        xmlBuilder.append("  <chatid>0</chatid>\n")
        xmlBuilder.append("  <mission>0</mission>\n")
        xmlBuilder.append("  <maxlimit>8000</maxlimit>\n")
        xmlBuilder.append("  <state>0</state>\n")
        xmlBuilder.append("  <real_name>0</real_name>\n")
        xmlBuilder.append("  <source>DanDanPlay</source>\n")

        for (comment in danmakuResponse.comments) {
            try {
                // p 格式: "时间,模式,字号,颜色,时间戳,弹幕池,用户hash,弹幕ID"
                // DanDanPlay p 格式: "时间,模式,颜色,用户ID"
                val pParts = comment.p.split(",")
                if (pParts.size >= 3) {
                    val time = pParts[0]
                    val mode = pParts[1]
                    val color = if (pParts.size > 2) pParts[2] else "16777215"
                    
                    // 转换为 Bilibili 格式
                    // 格式: 时间,模式,字号,颜色,时间戳,弹幕池,用户hash,弹幕ID
                    val p = "$time,$mode,25,$color,${System.currentTimeMillis() / 1000},0,0,${comment.cid}"
                    val content = comment.m
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;")
                    
                    xmlBuilder.append("  <d p=\"$p\">$content</d>\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting danmaku: ${comment.m}", e)
            }
        }

        xmlBuilder.append("</i>")
        return xmlBuilder.toString()
    }

    /**
     * 计算文件哈希（前16MB的MD5）
     * @param filePath 文件路径
     * @return 文件哈希值（32位小写MD5）
     */
    suspend fun calculateFileHash(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File not found: $filePath")
            }

            val fileSize = file.length()
            val readSize = minOf(fileSize, 16 * 1024 * 1024L) // 最多读取16MB

            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int

                while (totalRead < readSize) {
                    bytesRead = input.read(buffer, 0, minOf(buffer.size.toLong(), readSize - totalRead).toInt())
                    if (bytesRead == -1) break
                    digest.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
            }

            val hash = digest.digest()
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating file hash", e)
            throw e
        }
    }

    /**
     * 使用文件哈希匹配弹幕
     * POST /api/v2/match
     * @param fileName 文件名
     * @param fileHash 文件哈希（MD5）
     * @param fileSize 文件大小（字节）
     * @return 匹配结果
     */
    suspend fun matchDanmaku(fileName: String, fileHash: String, fileSize: Long): MatchResponse = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis() / 1000
            val path = "/api/v2/match"

            val matchRequest = MatchRequest(
                fileName = fileName,
                fileHash = fileHash,
                fileSize = fileSize
                // 不传 matchMode 和 videoDuration，让API使用默认值
            )
            
            val requestJson = gson.toJson(matchRequest)
            Log.d(TAG, "Match request JSON: $requestJson")
            Log.d(TAG, "完整URL: $baseUrl$path")

            val requestBody = requestJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(requestBody)
                .addHeader("X-AppId", APP_ID)
                .addHeader("X-Timestamp", timestamp.toString())
                .addHeader("X-Signature", generateSignature(timestamp, path))
                .addHeader("Accept-Encoding", "gzip")
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Matching danmaku: fileName=$fileName, hash=$fileHash, size=$fileSize")

            val response = client.newCall(request).execute()
            
            // 处理GZIP压缩的响应
            val responseBody = response.body?.let { body ->
                val contentEncoding = response.header("Content-Encoding")
                if (contentEncoding == "gzip") {
                    // 解压GZIP
                    val gzipStream = GZIPInputStream(body.byteStream())
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(1024)
                    var count: Int
                    while (gzipStream.read(data).also { count = it } != -1) {
                        buffer.write(data, 0, count)
                    }
                    buffer.toString("UTF-8")
                } else {
                    body.string()
                }
            } ?: throw Exception("Empty response")

            Log.d(TAG, "Match response: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("Match failed: ${response.code} - $responseBody")
            }

            val matchResponse = gson.fromJson(responseBody, MatchResponse::class.java)
            Log.d(TAG, "Match result: isMatched=${matchResponse.isMatched}, matches count=${matchResponse.matches?.size ?: 0}")
            matchResponse
        } catch (e: Exception) {
            Log.e(TAG, "Error matching danmaku", e)
            throw e
        }
    }
}
