package com.fam4k007.videoplayer.subtitle

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 字幕搜索下载管理器
 * 使用 Wyzie API 进行字幕搜索和下载
 */
class SubtitleDownloadManager(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val baseUrl = "https://sub.wyzie.io"
    private val apiKey = com.fam4k007.videoplayer.BuildConfig.WYZIE_API_KEY
    
    companion object {
        private const val TAG = "SubtitleDownloadManager"
    }
    
    /**
     * 搜索结果封装
     */
    sealed class SearchResult {
        data class Success(val subtitles: List<SubtitleInfo>) : SearchResult()
        data class Error(val message: String) : SearchResult()
    }
    
    /**
     * 媒体搜索结果封装
     */
    sealed class MediaSearchResult {
        data class Success(val media: List<TmdbMediaResult>) : MediaSearchResult()
        data class Error(val message: String) : MediaSearchResult()
    }
    
    /**
     * 下载结果封装
     */
    sealed class DownloadResult {
        data class Success(val filePath: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
    
    /**
     * 搜索TMDB媒体信息
     * @param query 搜索关键词
     * @return 媒体结果列表
     */
    suspend fun searchMedia(query: String): MediaSearchResult = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext MediaSearchResult.Error("Search keyword cannot be empty")
            }
            
            Log.d(TAG, "开始搜索媒体: $query")
            
            val url = "$baseUrl/api/tmdb/search?q=${URLEncoder.encode(query, "UTF-8")}&key=${URLEncoder.encode(apiKey, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB 搜索失败: ${response.code}")
                return@withContext MediaSearchResult.Success(emptyList())
            }
            
            val body = response.body?.string() ?: return@withContext MediaSearchResult.Success(emptyList())
            
            // 解析 JSON
            val json = gson.fromJson(body, Map::class.java)
            val results = json["results"] as? List<*> ?: return@withContext MediaSearchResult.Success(emptyList())
            
            // 转换为 TmdbMediaResult
            val mediaResults = results.mapNotNull { item ->
                try {
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = (map["id"] as? Double)?.toInt() ?: return@mapNotNull null
                    val mediaType = map["mediaType"] as? String ?: "movie"
                    val title = map["title"] as? String ?: return@mapNotNull null
                    val releaseYear = map["releaseYear"] as? String
                    val poster = map["poster"] as? String
                    val overview = map["overview"] as? String
                    
                    TmdbMediaResult(
                        id = id,
                        mediaType = mediaType,
                        title = title,
                        releaseYear = releaseYear,
                        poster = poster,
                        overview = overview
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "解析媒体结果失败", e)
                    null
                }
            }
            
            Log.d(TAG, "找到 ${mediaResults.size} 个媒体结果")
            MediaSearchResult.Success(mediaResults)
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索媒体失败", e)
            MediaSearchResult.Error("Search failed: ${e.message}")
        }
    }
    
    /**
     * 通过媒体ID搜索字幕
     * @param mediaId TMDB媒体ID
     * @param options 搜索选项（语言、来源、格式）
     */
    suspend fun searchSubtitlesByMediaId(
        mediaId: Int,
        options: SearchOptions = SearchOptions()
    ): SearchResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始搜索字幕 for ID: $mediaId")
            
            // 使用 ID 搜索字幕
            val subtitles = fetchSubtitles(mediaId.toString(), options)
            
            Log.d(TAG, "搜索完成，找到 ${subtitles.size} 个字幕")
            SearchResult.Success(subtitles)
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e)
            SearchResult.Error("Search failed: ${e.message}")
        }
    }
    
    /**
     * 搜索字幕
     * @param query 搜索关键词（视频标题）
     * @param options 搜索选项（语言、来源、格式）
     */
    suspend fun searchSubtitles(
        query: String,
        options: SearchOptions = SearchOptions()
    ): SearchResult = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext SearchResult.Error("Search keyword cannot be empty")
            }
            
            Log.d(TAG, "开始搜索字幕: $query")
            
            // 第一步：通过 TMDB API 查找视频 ID
            val tmdbId = searchTmdbId(query)
            if (tmdbId == null) {
                Log.w(TAG, "未找到视频ID，尝试直接搜索")
                // 如果找不到 ID，返回空结果而不是错误
                return@withContext SearchResult.Success(emptyList())
            }
            
            Log.d(TAG, "找到视频ID: $tmdbId")
            
            // 第二步：使用 ID 搜索字幕
            val subtitles = fetchSubtitles(tmdbId, options)
            
            Log.d(TAG, "搜索完成，找到 ${subtitles.size} 个字幕")
            SearchResult.Success(subtitles)
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e)
            SearchResult.Error("Search failed: ${e.message}")
        }
    }
    
    /**
     * 通过 TMDB API 搜索视频 ID
     */
    private fun searchTmdbId(query: String): String? {
        return try {
            val url = "$baseUrl/api/tmdb/search?q=${URLEncoder.encode(query, "UTF-8")}&key=${URLEncoder.encode(apiKey, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB 搜索失败: ${response.code}")
                return null
            }
            
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, Map::class.java)
            val results = json["results"] as? List<*> ?: return null
            
            if (results.isEmpty()) {
                return null
            }
            
            // 取第一个结果的 ID
            val firstResult = results[0] as? Map<*, *>
            val id = firstResult?.get("id")
            
            id?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "TMDB ID 搜索异常", e)
            null
        }
    }
    
    /**
     * 通过 ID 获取字幕列表
     */
    private fun fetchSubtitles(
        id: String,
        options: SearchOptions
    ): List<SubtitleInfo> {
        return try {
            // 构建 URL
            val url = buildString {
                append("$baseUrl/search?id=${URLEncoder.encode(id, "UTF-8")}&key=${URLEncoder.encode(apiKey, "UTF-8")}")
                
                // 语言参数
                if (!options.languages.contains("all") && options.languages.isNotEmpty()) {
                    val langs = options.languages.joinToString(",")
                    append("&language=${URLEncoder.encode(langs, "UTF-8")}")
                }
                
                // 格式参数
                if (!options.formats.contains("all") && options.formats.isNotEmpty()) {
                    options.formats.forEach { format ->
                        append("&${URLEncoder.encode(format, "UTF-8")}=true")
                    }
                }
                
                // 来源参数
                if (!options.sources.contains("all") && options.sources.isNotEmpty()) {
                    options.sources.forEach { source ->
                        append("&${URLEncoder.encode(source, "UTF-8")}=true")
                    }
                }
                
                append("&unzip=true")
            }
            
            Log.d(TAG, "请求URL: $url")
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "字幕获取失败: ${response.code}")
                return emptyList()
            }
            
            val body = response.body?.string() ?: return emptyList()
            
            // 解析 JSON
            val type = object : TypeToken<List<SubtitleApiResponse>>() {}.type
            val apiResponses: List<SubtitleApiResponse> = gson.fromJson(body, type)
            
            // 转换为 SubtitleInfo
            apiResponses.map { it.toSubtitleInfo() }
                .filter { subtitle ->
                    // 本地过滤语言（确保准确性）
                    if (options.languages.contains("all")) {
                        true
                    } else {
                        val lang = subtitle.language?.lowercase()
                        options.languages.any { it.lowercase() == lang }
                    }
                }
                .sortedByDescending { it.downloadCount ?: 0 }  // 按下载次数排序
            
        } catch (e: Exception) {
            Log.e(TAG, "字幕获取异常", e)
            emptyList()
        }
    }
    
    /**
     * 下载字幕文件
     * @param subtitle 字幕信息
     * @param saveUri 保存目录 URI
     * @param videoFileName 视频文件名（用于命名字幕文件）
     */
    suspend fun downloadSubtitle(
        subtitle: SubtitleInfo,
        saveUri: Uri,
        videoFileName: String
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载字幕: ${subtitle.displayName}")
            
            // 下载字幕内容
            val request = Request.Builder().url(subtitle.url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Download failed: HTTP ${response.code}")
            }
            
            val bytes = response.body?.bytes() 
                ?: return@withContext DownloadResult.Error("Download content is empty")
            
            // 确定文件扩展名
            val extension = subtitle.format?.lowercase() ?: "srt"
            
            // 构建文件名：视频名.语言.扩展名
            val baseFileName = videoFileName.substringBeforeLast(".")
            val language = subtitle.language ?: "unknown"
            val fileName = "${baseFileName}.${language}.${extension}"
            
            // 保存到指定目录
            val parentDir = DocumentFile.fromTreeUri(context, saveUri)
            if (parentDir?.exists() != true) {
                return@withContext DownloadResult.Error("Save directory does not exist")
            }
            
            // 检查是否已存在同名文件，如果存在则先删除
            val existingFile = parentDir.findFile(fileName)
            existingFile?.delete()
            
            // 创建新文件
            val subFile = parentDir.createFile("application/octet-stream", fileName)
            if (subFile == null) {
                return@withContext DownloadResult.Error("Failed to create file")
            }
            
            // 写入内容
            context.contentResolver.openOutputStream(subFile.uri)?.use { outputStream ->
                outputStream.write(bytes)
            }
            
            Log.d(TAG, "字幕下载成功: $fileName")
            DownloadResult.Success(fileName)
            
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            DownloadResult.Error("Download failed: ${e.message}")
        }
    }
}

/**
 * API 响应数据类
 */
private data class SubtitleApiResponse(
    val id: String? = null,
    val url: String,
    val format: String? = null,
    val display: String? = null,
    val language: String? = null,
    val fileName: String? = null,
    val release: String? = null,
    val source: String? = null,
    val isHearingImpaired: Boolean = false,
    val downloadCount: Int? = null
) {
    fun toSubtitleInfo() = SubtitleInfo(
        id = id,
        url = url,
        fileName = fileName,
        language = language,
        languageDisplay = display,
        format = format,
        source = source,
        release = release,
        isHearingImpaired = isHearingImpaired,
        downloadCount = downloadCount
    )
}
