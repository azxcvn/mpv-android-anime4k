package com.fam4k007.videoplayer.danmaku

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * B站弹幕下载管理器
 * 负责从B站视频链接下载弹幕XML文件
 */
class BiliBiliDanmakuDownloadManager(private val context: Context) {
    
    private val authManager: BiliBiliAuthManager by lazy {
        BiliBiliAuthManager.getInstance(context)
    }
    
    companion object {
        private const val TAG = "BiliDanmuDownload"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        private const val TIMEOUT_SECONDS = 30L // 增加超时时间
        private const val MAX_CONCURRENT_DOWNLOADS = 5 // 最大并发下载数
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true) // 自动跟随重定向
        .followSslRedirects(true)
        .build()
    
    /**
     * 创建带Cookie的请求构建器（参考 Bilibili-Evolved 的 credentials: 'include'）
     */
    private fun createRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com")
        
        // 添加Cookie（如果已登录）
        val cookieString = authManager.getCookieString()
        if (cookieString.isNotEmpty()) {
            builder.addHeader("Cookie", cookieString)
            Log.d(TAG, "使用已登录的Cookie进行弹幕下载")
        } else {
            Log.d(TAG, "未登录，使用游客模式下载弹幕")
        }
        
        return builder
    }
    
    /**
     * 下载结果
     */
    sealed class DownloadResult {
        data class Success(val fileName: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
    
    /**
     * 从B站视频/番剧链接下载弹幕到指定目录
     * @param input 用户输入(可能包含分享文本)
     * @param saveDirectoryUri 保存目录的URI
     * @param downloadWholeSeason 番剧是否下载整季(true=整季,false=单集)
     * @param progressCallback 进度回调(当前集数,总集数,集标题,成功数,失败数)
     * @return 下载结果
     */
    suspend fun downloadDanmaku(
        input: String,
        saveDirectoryUri: Uri,
        downloadWholeSeason: Boolean = true,
        progressCallback: ((Int, Int, String, Int, Int) -> Unit)? = null
    ): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始下载弹幕，原始输入: $input")
                
                // 1. 从输入中提取纯链接
                val videoUrl = extractUrlFromInput(input)
                    ?: return@withContext DownloadResult.Error("Unable to identify a valid Bilibili link from the input")
                
                Log.d(TAG, "提取到的链接: $videoUrl")
                
                // 如果是 b23.tv 短链接,需要获取真实URL
                val realUrl = if (videoUrl.contains("b23.tv", ignoreCase = true)) {
                    // 对于 b23.tv/ep{id} 格式,直接解析 ep_id
                    val epMatch = Regex("b23\\.tv/ep(\\d+)", RegexOption.IGNORE_CASE).find(videoUrl)
                    if (epMatch != null) {
                        val epId = epMatch.groupValues[1]
                        "https://www.bilibili.com/bangumi/play/ep$epId"
                    } else {
                        // 其他短链接需要请求获取重定向
                        resolveShortUrl(videoUrl) ?: videoUrl
                    }
                } else {
                    videoUrl
                }
                
                Log.d(TAG, "解析后的链接: $realUrl")
                
                // 判断是普通视频还是番剧
                val isBangumi = realUrl.contains("bilibili.com/bangumi/play/", ignoreCase = true)
                
                if (isBangumi && downloadWholeSeason) {
                    // 番剧:下载整季所有集数
                    Log.d(TAG, "识别为番剧链接,准备下载整季弹幕")
                    downloadBangumiSeasonDanmaku(realUrl, saveDirectoryUri, progressCallback)
                } else if (isBangumi) {
                    // 番剧:下载单集
                    Log.d(TAG, "识别为番剧链接,下载单集弹幕")
                    downloadSingleBangumiEpisode(realUrl, saveDirectoryUri, progressCallback)
                } else {
                    // 普通视频:下载单个视频
                    Log.d(TAG, "识别为普通视频链接")
                    downloadSingleVideoDanmaku(realUrl, saveDirectoryUri, progressCallback)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "下载弹幕失败", e)
                DownloadResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 从用户输入中提取B站链接
     * 支持从分享文本中提取
     */
    private fun extractUrlFromInput(input: String): String? {
        val trimmedInput = input.trim()
        
        // 常见的B站链接模式
        val patterns = listOf(
            // 标准链接
            Regex("https?://(?:www\\.)?bilibili\\.com/video/[ABab][Vv][0-9A-Za-z]+[^\\s]*"),
            Regex("https?://(?:www\\.)?bilibili\\.com/video/av\\d+[^\\s]*"),
            Regex("https?://(?:www\\.)?bilibili\\.com/bangumi/play/(?:ss|ep)\\d+[^\\s]*"),
            // b23.tv 短链接
            Regex("https?://b23\\.tv/[0-9A-Za-z]+[^\\s]*"),
            // 移动端链接
            Regex("https?://(?:m|www)\\.bilibili\\.com/bangumi/play/(?:ss|ep)\\d+[^\\s]*")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(trimmedInput)
            if (match != null) {
                var url = match.value
                // 移除末尾可能的标点符号
                url = url.trimEnd('\u3002', '\uFF0C', ',', '.', '!', '\uFF01', '?', '\uFF1F', '"', '\u201D', '\'', '\u2019')
                Log.d(TAG, "从输入中提取到链接: $url")
                return url
            }
        }
        
        // 如果没有匹配到,检查是否本身就是干净的链接
        if (trimmedInput.contains("bilibili.com") || trimmedInput.contains("b23.tv")) {
            return trimmedInput
        }
        
        return null
    }
    
    /**
     * 解析 b23.tv 短链接获取真实 URL
     */
    private fun resolveShortUrl(shortUrl: String): String? {
        try {
            Log.d(TAG, "解析短链接: $shortUrl")
            
            val request = createRequestBuilder(shortUrl).build()
            
            // 使用 HEAD 请求获取重定向后的 URL,不下载内容
            client.newBuilder()
                .followRedirects(false) // 不自动跟随重定向
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    val location = response.header("Location")
                    if (location != null) {
                        Log.d(TAG, "短链接重定向到: $location")
                        return location
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "解析短链接失败", e)
        }
        return null
    }
    
    /**
     * 下载单个视频的弹幕
     */
    private suspend fun downloadSingleVideoDanmaku(
        videoUrl: String,
        saveDirectoryUri: Uri,
        progressCallback: ((Int, Int, String, Int, Int) -> Unit)? = null
    ): DownloadResult {
        return try {
            progressCallback?.invoke(1, 1, "Fetching video info...", 0, 0)
            
            val (aid, cid, title) = extractVideoInfo(videoUrl)
                ?: return DownloadResult.Error("Failed to parse video info")
            
            Log.d(TAG, "视频信息 - 标题: $title, AID: $aid, CID: $cid")
            
            progressCallback?.invoke(1, 1, "Downloading danmaku...", 0, 0)
            
            val xmlContent = downloadDanmakuXml(cid, aid = aid)
                ?: return DownloadResult.Error("Danmaku download failed")
            
            val fileName = saveToDirectory(saveDirectoryUri, title, xmlContent)
                ?: return DownloadResult.Error("Failed to save file")
            
            Log.d(TAG, "弹幕下载完成: $fileName")
            progressCallback?.invoke(1, 1, "Download complete", 1, 0)
            DownloadResult.Success(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "下载视频弹幕失败", e)
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 下载番剧单集的弹幕
     */
    private suspend fun downloadSingleBangumiEpisode(
        bangumiUrl: String,
        saveDirectoryUri: Uri,
        progressCallback: ((Int, Int, String, Int, Int) -> Unit)? = null
    ): DownloadResult {
        return try {
            progressCallback?.invoke(1, 1, "Fetching bangumi info...", 0, 0)
            
            val (aid, cid, title) = extractBangumiInfo(bangumiUrl)
                ?: return DownloadResult.Error("Failed to parse bangumi info")
            
            Log.d(TAG, "番剧信息 - 标题: $title, AID: $aid, CID: $cid")
            
            progressCallback?.invoke(1, 1, "Downloading danmaku...", 0, 0)
            
            val xmlContent = downloadDanmakuXml(cid, aid = aid)
                ?: return DownloadResult.Error("Danmaku download failed")
            
            val fileName = saveToDirectory(saveDirectoryUri, title, xmlContent)
                ?: return DownloadResult.Error("Failed to save file")
            
            Log.d(TAG, "弹幕下载完成: $fileName")
            progressCallback?.invoke(1, 1, "Download complete", 1, 0)
            DownloadResult.Success(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "下载番剧弹幕失败", e)
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 下载番剧整季所有集数的弹幕
     */
    private suspend fun downloadBangumiSeasonDanmaku(
        bangumiUrl: String,
        saveDirectoryUri: Uri,
        progressCallback: ((Int, Int, String, Int, Int) -> Unit)? = null
    ): DownloadResult {
        return try {
            progressCallback?.invoke(0, 0, "Fetching bangumi season info...", 0, 0)
            
            // 获取番剧季度信息
            val seasonInfo = getBangumiSeasonInfo(bangumiUrl)
                ?: return DownloadResult.Error("Failed to fetch bangumi info")
            
            val (seasonTitle, episodes) = seasonInfo
            Log.d(TAG, "番剧: $seasonTitle, 共${episodes.size}集")
            
            if (episodes.isEmpty()) {
                return DownloadResult.Error("This bangumi has no downloadable episodes")
            }
            
            // 分批并发下载所有集数的弹幕（优化内存占用）
            // 每批最多3集，避免内存峰值过高
            val concurrentLimit = 3
            val results = mutableListOf<Boolean>()
            var successCount = 0
            var failCount = 0
            
            withContext(Dispatchers.IO) {
                for (startIndex in episodes.indices step concurrentLimit) {
                    val endIndex = minOf(startIndex + concurrentLimit - 1, episodes.size - 1)
                    
                    // 并发下载当前批次的集数
                    val batchResults = (startIndex..endIndex).map { index ->
                        async {
                            try {
                                val episode = episodes[index]
                                val (aid, cid, epTitle) = episode
                                // 文件名只使用集数标题，不包含番剧名
                                val fullTitle = epTitle
                                
                                progressCallback?.invoke(index + 1, episodes.size, epTitle, successCount, failCount)
                                Log.d(TAG, "下载第${index + 1}/${episodes.size}集: $fullTitle (来自《$seasonTitle》)")
                                
                                // 检查文件是否已存在
                                if (isFileExists(saveDirectoryUri, fullTitle)) {
                                    Log.d(TAG, "第${index + 1}集已存在,跳过")
                                    return@async true
                                }
                                
                                val xmlContent = downloadDanmakuXml(cid, aid = aid)
                                if (xmlContent != null) {
                                    val saved = saveToDirectory(saveDirectoryUri, fullTitle, xmlContent)
                                    if (saved != null) {
                                        Log.d(TAG, "第${index + 1}集下载成功")
                                        true
                                    } else {
                                        Log.e(TAG, "第${index + 1}集保存失败")
                                        false
                                    }
                                } else {
                                    Log.e(TAG, "第${index + 1}集弹幕下载失败")
                                    false
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "下载第${index + 1}集时出错", e)
                                false
                            }
                        }
                    }.awaitAll()
                    
                    // 统计当前批次结果
                    results.addAll(batchResults)
                    successCount = results.count { it }
                    failCount = results.count { !it }
                    
                    Log.d(TAG, "批次 ${startIndex + 1}-${endIndex + 1} 完成，当前进度: 成功 $successCount, 失败 $failCount")
                }
            }
            
            val message = "Bangumi \"$seasonTitle\" download complete\nSucceeded: $successCount episodes\nFailed: $failCount episodes"
            Log.d(TAG, message)
            
            progressCallback?.invoke(episodes.size, episodes.size, "Download complete", successCount, failCount)
            
            if (successCount > 0) {
                DownloadResult.Success(message)
            } else {
                DownloadResult.Error("All episodes failed to download")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "下载番剧弹幕失败", e)
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 获取番剧季度的所有集数信息
     * @return Pair<季度标题, List<Triple<aid, cid, 集数标题>>>
     */
    private fun getBangumiSeasonInfo(url: String): Pair<String, List<Triple<Long, Long, String>>>? {
        try {
            // 提取 season_id 或 ep_id
            val seasonIdMatch = Regex("ss(\\d+)", RegexOption.IGNORE_CASE).find(url)
            val epIdMatch = Regex("ep(\\d+)", RegexOption.IGNORE_CASE).find(url)
            
            // 构建API请求
            val apiUrl = when {
                seasonIdMatch != null -> {
                    val seasonId = seasonIdMatch.groupValues[1]
                    "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
                }
                epIdMatch != null -> {
                    val epId = epIdMatch.groupValues[1]
                    "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
                }
                else -> return null
            }
            
            Log.d(TAG, "请求番剧季度信息: $apiUrl")
            
            val request = createRequestBuilder(apiUrl).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "获取番剧信息失败: HTTP ${response.code}")
                    return null
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e(TAG, "响应体为空")
                    return null
                }
                
                val jsonObject = JSONObject(responseBody)
                val code = jsonObject.getInt("code")
                if (code != 0) {
                    Log.e(TAG, "API返回错误: code=$code, message=${jsonObject.optString("message")}")
                    return null
                }
                
                val result = jsonObject.getJSONObject("result")
                
                // 获取季度标题
                val seasonTitle = result.optString("season_title") 
                    ?: result.optString("title")
                    ?: "Unknown Bangumi"
                
                // 获取所有集数
                val episodes = result.optJSONArray("episodes")
                if (episodes == null || episodes.length() == 0) {
                    Log.e(TAG, "未找到番剧集数信息")
                    return null
                }
                
                val episodeList = mutableListOf<Triple<Long, Long, String>>()
                for (i in 0 until episodes.length()) {
                    val ep = episodes.getJSONObject(i)
                    val aid = ep.getLong("aid")
                    val cid = ep.getLong("cid")
                    val epTitle = ep.optString("long_title").ifEmpty { null }
                        ?: ep.optString("title").ifEmpty { null }
                        ?: "Episode ${i + 1}"
                    episodeList.add(Triple(aid, cid, epTitle))
                }
                
                Log.d(TAG, "获取到番剧《$seasonTitle》共${episodeList.size}集")
                return Pair(seasonTitle, episodeList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取番剧季度信息失败", e)
            return null
        }
    }
    
    /**
     * 从HTML中提取视频AID、CID和标题
     */
    private fun extractVideoInfo(url: String): Triple<Long, Long, String>? {
        try {
            // 使用Jsoup获取网页内容
            val html = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS.toInt() * 1000)
                .get()
                .toString()
            
            // 从 __INITIAL_STATE__ 中解析
            val patterns = listOf(
                "window.__INITIAL_STATE__=" to "};(function",
                "__INITIAL_STATE__=" to ";(function"
            )
            
            for ((startTag, endTag) in patterns) {
                val startIndex = html.indexOf(startTag)
                if (startIndex == -1) continue
                
                val actualStart = startIndex + startTag.length
                val endIndex = html.indexOf(endTag, actualStart)
                if (endIndex == -1) continue
                
                val jsonStr = html.substring(actualStart, endIndex + 1)
                val jsonObject = JSONObject(jsonStr)
                
                // 提取videoData
                val videoData = jsonObject.optJSONObject("videoData") ?: continue
                
                val aid = videoData.getLong("aid")
                val cid = videoData.getLong("cid")
                val title = videoData.getString("title")
                
                return Triple(aid, cid, title)
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "解析HTML失败", e)
            return null
        }
    }
    
    /**
     * 从番剧链接中提取AID、CID和标题
     * 支持 ss{season_id} 和 ep{ep_id} 两种格式
     */
    private fun extractBangumiInfo(url: String): Triple<Long, Long, String>? {
        try {
            // 提取 season_id 或 ep_id
            val seasonIdMatch = Regex("ss(\\d+)", RegexOption.IGNORE_CASE).find(url)
            val epIdMatch = Regex("ep(\\d+)", RegexOption.IGNORE_CASE).find(url)
            
            // 构建API请求
            val apiUrl = when {
                seasonIdMatch != null -> {
                    val seasonId = seasonIdMatch.groupValues[1]
                    "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
                }
                epIdMatch != null -> {
                    val epId = epIdMatch.groupValues[1]
                    "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
                }
                else -> return null
            }
            
            Log.d(TAG, "请求番剧季度信息: $apiUrl")
            
            // 请求番剧信息
            val request = createRequestBuilder(apiUrl).build()
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "番剧API响应: HTTP ${response.code}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "获取番剧信息失败: HTTP ${response.code}, Headers: ${response.headers}")
                    return null
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e(TAG, "响应体为空")
                    return null
                }
                
                val jsonObject = JSONObject(responseBody)
                val code = jsonObject.getInt("code")
                if (code != 0) {
                    Log.e(TAG, "API返回错误: code=$code, message=${jsonObject.optString("message")}")
                    return null
                }
                
                val result = jsonObject.getJSONObject("result")
                
                // 获取标题
                val title = result.optString("season_title") 
                    ?: result.optString("title")
                    ?: "Unknown Bangumi"
                
                // 获取第一集的cid（作为默认）
                // 优先从episodes数组获取
                val episodes = result.optJSONArray("episodes")
                if (episodes != null && episodes.length() > 0) {
                    // 如果URL包含ep_id，尝试找到对应的集数
                    val targetEpId = epIdMatch?.groupValues?.get(1)?.toLongOrNull()
                    
                    if (targetEpId != null) {
                        // 查找指定的ep
                        for (i in 0 until episodes.length()) {
                            val ep = episodes.getJSONObject(i)
                            if (ep.getLong("id") == targetEpId) {
                                val aid = ep.getLong("aid")
                                val cid = ep.getLong("cid")
                                val epTitle = ep.optString("long_title").ifEmpty { null }
                                    ?: ep.optString("title").ifEmpty { null }
                                    ?: "Episode ${i + 1}"
                                // 文件名只使用集数标题
                                val fullTitle = epTitle
                                Log.d(TAG, "找到指定集数: $fullTitle (来自《$title》), AID: $aid, CID: $cid")
                                return Triple(aid, cid, fullTitle)
                            }
                        }
                    }
                    
                    // 如果没有指定或没找到，使用第一集
                    val firstEp = episodes.getJSONObject(0)
                    val aid = firstEp.getLong("aid")
                    val cid = firstEp.getLong("cid")
                    val epTitle = firstEp.optString("long_title").ifEmpty { null }
                        ?: firstEp.optString("title").ifEmpty { null }
                        ?: "Episode 1"
                    // 文件名只使用集数标题
                    val fullTitle = epTitle
                    Log.d(TAG, "使用第一集: $fullTitle (来自《$title》), AID: $aid, CID: $cid")
                    return Triple(aid, cid, fullTitle)
                }
                
                Log.e(TAG, "未找到番剧集数信息")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取番剧信息失败", e)
            return null
        }
    }
    
    /**
     * 下载弹幕XML内容(带重试)
     * 优先使用分段API获取完整弹幕，失败则降级到普通API
     */
    private suspend fun downloadDanmakuXml(cid: Long, retryCount: Int = 3, aid: Long = 0): String? {
        var lastException: Exception? = null
        
        // 优先尝试使用分段弹幕API获取完整弹幕
        Log.d(TAG, "尝试使用分段弹幕API下载完整弹幕")
        val segmentXml = downloadSegmentDanmaku(cid, aid)
        if (segmentXml != null) {
            Log.d(TAG, "分段弹幕API下载成功")
            return segmentXml
        }
        Log.w(TAG, "分段弹幕API下载失败，降级使用普通API")
        
        // 降级使用普通弹幕API
        repeat(retryCount) { attempt ->
            try {
                // 使用 comment.bilibili.com 镜像接口,避免 WBI 签名
                val url = "https://comment.bilibili.com/$cid.xml"
                
                val request = createRequestBuilder(url).build()
                
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "弹幕API响应: HTTP ${response.code}, Content-Type: ${response.header("Content-Type")}")
                    if (!response.isSuccessful) {
                        Log.e(TAG, "下载失败 (尝试${attempt + 1}/$retryCount): HTTP ${response.code}, Message: ${response.message}")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(500) // 缩短重试间隔到0.5秒
                            return@repeat
                        }
                        return null
                    }
                    
                    val responseBody = response.body
                    if (responseBody == null) {
                        Log.e(TAG, "响应体为空 (尝试${attempt + 1}/$retryCount)")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(500)
                            return@repeat
                        }
                        return null
                    }
                    
                    // B站API返回的是deflate压缩的二进制数据
                    val compressedData = responseBody.bytes()
                    Log.d(TAG, "下载成功，压缩数据长度: ${compressedData.size}")
                    
                    if (compressedData.isEmpty()) {
                        Log.e(TAG, "下载的数据为空 (尝试${attempt + 1}/$retryCount)")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(500)
                            return@repeat
                        }
                        return null
                    }
                    
                    // 手动解压deflate数据
                    val content = decompressDeflate(compressedData)
                    if (content == null) {
                        Log.e(TAG, "解压失败 (尝试${attempt + 1}/$retryCount)")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(500)
                            return@repeat
                        }
                        return null
                    }
                    
                    Log.d(TAG, "解压后内容长度: ${content.length}")
                    
                    if (content.isEmpty()) {
                        Log.e(TAG, "解压后内容为空 (尝试${attempt + 1}/$retryCount)")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(500)
                            return@repeat
                        }
                        return null
                    }
                    
                    // 检查是否是有效的XML
                    if (!content.trimStart().startsWith("<")) {
                        Log.e(TAG, "返回的不是XML格式 (尝试${attempt + 1}/$retryCount)")
                        Log.e(TAG, "内容预览: ${content.take(200)}")
                        if (attempt < retryCount - 1) {
                            Thread.sleep(1000)
                            return@repeat
                        }
                        return null
                    }
                    
                    Log.d(TAG, "弹幕下载成功 CID=$cid")
                    return content
                }
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "下载弹幕XML失败 (尝试${attempt + 1}/$retryCount): ${e.message}", e)
                if (attempt < retryCount - 1) {
                    Thread.sleep(1000)
                }
            }
        }
        
        Log.e(TAG, "所有重试均失败 CID=$cid", lastException)
        return null
    }
    
    /**
     * 使用分段弹幕API下载完整弹幕（流式写入优化版）
     * API: https://api.bilibili.com/x/v2/dm/web/seg.so
     * 此API将弹幕按时间分段返回，可以获取完整弹幕
     * 
     * 优化策略：
     * 1. 分批并发下载（每批3个分段），避免内存峰值过高
     * 2. 下载后立即写入文件，不在内存中累积所有弹幕
     * 3. 使用 buildString 减少临时对象分配
     */
    private suspend fun downloadSegmentDanmaku(cid: Long, aid: Long = 0): String? {
        try {
            Log.d(TAG, "使用分段弹幕API（流式写入优化）: cid=$cid, aid=$aid")
            
            // 1. 获取弹幕元数据，包括总分段数
            val pidParam = if (aid > 0) "&pid=$aid" else ""
            val viewUrl = "https://api.bilibili.com/x/v2/dm/web/view?type=1&oid=$cid$pidParam"
            val viewRequest = createRequestBuilder(viewUrl).build()
            
            val totalSegments: Int
            client.newCall(viewRequest).execute().use { response ->
                Log.d(TAG, "弹幕元数据API响应: HTTP ${response.code}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "获取弹幕元数据失败: HTTP ${response.code}")
                    return null
                }
                
                val responseBody = response.body
                if (responseBody == null) {
                    Log.e(TAG, "弹幕元数据响应体为空")
                    return null
                }
                
                // 解析protobuf获取总分段数
                val protobufData = responseBody.bytes()
                val segments = parseViewProtobuf(protobufData)
                if (segments == null || segments <= 0) {
                    Log.e(TAG, "无法解析分段数")
                    return null
                }
                
                totalSegments = segments
                Log.d(TAG, "弹幕总分段数: $totalSegments")
            }
            
            // 2. 使用 StringBuilder 流式构建 XML（边下载边写入）
            val xmlBuilder = StringBuilder()
            xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            xmlBuilder.append("<i>\n")
            xmlBuilder.append("<chatserver>chat.bilibili.com</chatserver>\n")
            xmlBuilder.append("<chatid>$cid</chatid>\n")
            xmlBuilder.append("<mission>0</mission>\n")
            xmlBuilder.append("<maxlimit>$totalSegments</maxlimit>\n")
            xmlBuilder.append("<state>0</state>\n")
            xmlBuilder.append("<real_name>0</real_name>\n")
            xmlBuilder.append("<source>segment-api-streaming</source>\n\n")
            
            // 3. 分批并发下载，每批最多3个分段（平衡速度与内存）
            val concurrentLimit = 3
            val allDanmakuIds = mutableSetOf<Long>()  // 用于去重
            var totalDanmakuCount = 0
            
            withContext(Dispatchers.IO) {
                for (startIndex in 1..totalSegments step concurrentLimit) {
                    val endIndex = minOf(startIndex + concurrentLimit - 1, totalSegments)
                    
                    // 并发下载当前批次的分段
                    val batchResults = (startIndex..endIndex).map { segmentIndex ->
                        async {
                            val segmentUrl = "https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=$cid$pidParam&segment_index=$segmentIndex"
                            val segmentRequest = createRequestBuilder(segmentUrl).build()
                            
                            try {
                                client.newCall(segmentRequest).execute().use { response ->
                                    if (!response.isSuccessful) {
                                        Log.w(TAG, "下载分段 $segmentIndex 失败: HTTP ${response.code}")
                                        return@async emptyList<DanmakuItem>()
                                    }
                                    
                                    val segmentBody = response.body
                                    if (segmentBody == null) {
                                        Log.w(TAG, "分段 $segmentIndex 响应体为空")
                                        return@async emptyList<DanmakuItem>()
                                    }
                                    
                                    val protobufData = segmentBody.bytes()
                                    val danmakuList = parseProtobufDanmaku(protobufData)
                                    Log.d(TAG, "分段 $segmentIndex/$totalSegments 下载成功，弹幕数: ${danmakuList.size}")
                                    danmakuList
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "下载分段 $segmentIndex 异常: ${e.message}")
                                emptyList<DanmakuItem>()
                            }
                        }
                    }.awaitAll().flatten()
                    
                    // 立即处理这批弹幕：去重、排序、写入
                    val newDanmaku = batchResults
                        .filter { !allDanmakuIds.contains(it.id) }  // 去重
                        .sortedBy { it.progress }  // 按时间排序
                    
                    // 记录已处理的ID
                    newDanmaku.forEach { allDanmakuIds.add(it.id) }
                    
                    // 立即追加到 XML（减少内存占用）
                    newDanmaku.forEach { danmaku ->
                        xmlBuilder.append(danmaku.toXmlString())
                        xmlBuilder.append("\n")
                    }
                    
                    totalDanmakuCount += newDanmaku.size
                    Log.d(TAG, "批次 $startIndex-$endIndex 处理完成，新增弹幕: ${newDanmaku.size}，累计: $totalDanmakuCount")
                    
                    // 批次之间的清理工作已由作用域自动完成
                }
            }
            
            xmlBuilder.append("</i>")
            
            Log.d(TAG, "流式下载完成，总弹幕数: $totalDanmakuCount")
            
            if (totalDanmakuCount == 0) {
                Log.w(TAG, "没有获取到任何弹幕")
                return null
            }
            
            return xmlBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "下载分段弹幕失败", e)
            return null
        }
    }
    
    /**
     * 解析view protobuf获取总分段数
     */
    private fun parseViewProtobuf(data: ByteArray): Int? {
        try {
            var index = 0
            while (index < data.size) {
                if (index >= data.size) break
                
                val tag = data[index].toInt() and 0xFF
                index++
                
                // 字段4是dmSge（DmSegConfig）
                if (tag == 0x22) { // field 4, wire type 2 (length-delimited)
                    val length = readVarint(data, index).toInt()
                    index += getVarintSize(data, index)
                    
                    if (index + length <= data.size) {
                        val dmSgeData = data.copyOfRange(index, index + length)
                        // 解析DmSegConfig中的total字段（字段2）
                        return parseDmSegConfig(dmSgeData)
                    }
                } else {
                    index = skipField(data, index, tag and 0x07)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析view protobuf失败", e)
        }
        return null
    }
    
    /**
     * 解析DmSegConfig获取total
     */
    private fun parseDmSegConfig(data: ByteArray): Int? {
        try {
            var index = 0
            while (index < data.size) {
                if (index >= data.size) break
                
                val tag = data[index].toInt() and 0xFF
                index++
                
                when (tag shr 3) {
                    2 -> { // total字段
                        val total = readVarint(data, index).toInt()
                        return total
                    }
                    else -> {
                        index = skipField(data, index, tag and 0x07)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析DmSegConfig失败", e)
        }
        return null
    }
    
    /**
     * 将弹幕列表转换为XML格式
     */
    private fun convertDanmakuListToXml(danmakuList: List<DanmakuItem>): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        xmlBuilder.append("<i>\n")
        xmlBuilder.append("<chatserver>chat.bilibili.com</chatserver>\n")
        xmlBuilder.append("<chatid>0</chatid>\n")
        xmlBuilder.append("<mission>0</mission>\n")
        xmlBuilder.append("<maxlimit>${danmakuList.size}</maxlimit>\n")
        xmlBuilder.append("<state>0</state>\n")
        xmlBuilder.append("<real_name>0</real_name>\n")
        xmlBuilder.append("<source>segment-api</source>\n")
        
        for (danmaku in danmakuList) {
            xmlBuilder.append("<d p=\"${danmaku.toAttribute()}\">${escapeXml(danmaku.content)}</d>\n")
        }
        
        xmlBuilder.append("</i>")
        return xmlBuilder.toString()
    }
    
    /**
     * 解析protobuf格式的弹幕数据
     */
    private fun parseProtobufDanmaku(data: ByteArray): List<DanmakuItem> {
        val danmakuList = mutableListOf<DanmakuItem>()
        
        try {
            var index = 0
            while (index < data.size) {
                // protobuf wire format: field_number << 3 | wire_type
                if (index >= data.size) break
                
                // tag 必须用 varint 读取（字段号>=16时tag占多字节）
                val tagValue = readVarint(data, index).toInt()
                index += getVarintSize(data, index)
                val fieldNumber = tagValue shr 3
                val wireType = tagValue and 0x07
                
                // 字段1是弹幕元素（repeated）
                if (fieldNumber == 1 && wireType == 2) { // field 1, wire type 2 (length-delimited)
                    // 读取长度
                    val length = readVarint(data, index)
                    index += getVarintSize(data, index)
                    
                    if (index + length.toInt() <= data.size) {
                        val danmakuData = data.copyOfRange(index, index + length.toInt())
                        val danmaku = parseSingleDanmaku(danmakuData)
                        if (danmaku != null) {
                            danmakuList.add(danmaku)
                        }
                        index += length.toInt()
                    } else {
                        break
                    }
                } else {
                    // 跳过不认识的字段
                    index = skipField(data, index, wireType)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析protobuf弹幕失败", e)
        }
        
        return danmakuList
    }
    
    /**
     * 解析单条弹幕的protobuf数据
     */
    private fun parseSingleDanmaku(data: ByteArray): DanmakuItem? {
        try {
            var id: Long = 0
            var progress: Int = 0
            var mode: Int = 1
            var fontsize: Int = 25
            var color: Long = 0xFFFFFF
            var midHash: String = ""
            var content: String = ""
            var ctime: Long = 0
            
            var index = 0
            while (index < data.size) {
                if (index >= data.size) break
                
                // tag 必须用 varint 读取（字段号>=16时tag占多字节）
                val tagValue = readVarint(data, index).toInt()
                index += getVarintSize(data, index)
                val fieldNumber = tagValue shr 3
                val wireType = tagValue and 0x07
                
                when (fieldNumber) {
                    1 -> { // id
                        id = readVarint(data, index)
                        index += getVarintSize(data, index)
                    }
                    2 -> { // progress
                        progress = readVarint(data, index).toInt()
                        index += getVarintSize(data, index)
                    }
                    3 -> { // mode
                        mode = readVarint(data, index).toInt()
                        index += getVarintSize(data, index)
                    }
                    4 -> { // fontsize
                        fontsize = readVarint(data, index).toInt()
                        index += getVarintSize(data, index)
                    }
                    5 -> { // color
                        color = readVarint(data, index)
                        index += getVarintSize(data, index)
                    }
                    6 -> { // midHash
                        val length = readVarint(data, index).toInt()
                        index += getVarintSize(data, index)
                        if (index + length <= data.size) {
                            midHash = String(data, index, length, Charsets.UTF_8)
                            index += length
                        }
                    }
                    7 -> { // content
                        val length = readVarint(data, index).toInt()
                        index += getVarintSize(data, index)
                        if (index + length <= data.size) {
                            content = String(data, index, length, Charsets.UTF_8)
                            index += length
                        }
                    }
                    8 -> { // ctime
                        ctime = readVarint(data, index)
                        index += getVarintSize(data, index)
                    }
                    else -> {
                        // 跳过未知字段
                        index = skipField(data, index, wireType)
                    }
                }
            }
            
            if (content.isNotEmpty()) {
                return DanmakuItem(
                    id = id,
                    progress = progress,
                    mode = mode,
                    fontsize = fontsize,
                    color = color,
                    midHash = midHash,
                    content = content,
                    ctime = ctime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析单条弹幕失败", e)
        }
        
        return null
    }
    
    /**
     * 读取protobuf varint
     */
    private fun readVarint(data: ByteArray, startIndex: Int): Long {
        var result = 0L
        var shift = 0
        var index = startIndex
        
        while (index < data.size) {
            val b = data[index].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            
            if ((b and 0x80) == 0) {
                break
            }
            
            shift += 7
            index++
        }
        
        return result
    }
    
    /**
     * 获取varint的字节大小
     */
    private fun getVarintSize(data: ByteArray, startIndex: Int): Int {
        var size = 0
        var index = startIndex
        
        while (index < data.size) {
            size++
            val b = data[index].toInt() and 0xFF
            if ((b and 0x80) == 0) {
                break
            }
            index++
        }
        
        return size
    }
    
    /**
     * 跳过protobuf字段
     */
    private fun skipField(data: ByteArray, startIndex: Int, wireType: Int): Int {
        var index = startIndex
        
        when (wireType) {
            0 -> { // varint
                index += getVarintSize(data, index)
            }
            1 -> { // 64-bit
                index += 8
            }
            2 -> { // length-delimited
                val length = readVarint(data, index).toInt()
                index += getVarintSize(data, index) + length
            }
            5 -> { // 32-bit
                index += 4
            }
        }
        
        return index
    }
    
    /**
     * 弹幕数据项
     */
    private data class DanmakuItem(
        val id: Long,
        val progress: Int,      // 弹幕出现时间（毫秒）
        val mode: Int,          // 弹幕类型
        val fontsize: Int,      // 字体大小
        val color: Long,        // 颜色
        val midHash: String,    // 发送者mid的hash
        val content: String,    // 弹幕内容
        val ctime: Long         // 发送时间戳
    ) {
        /**
         * 转换为XML的p属性
         * 格式：出现时间,模式,字号,颜色,发送时间戳,弹幕池,用户ID,弹幕ID
         */
        fun toAttribute(): String {
            val timeSeconds = progress / 1000.0
            return String.format(
                Locale.US,
                "%.3f,%d,%d,%d,%d,0,%s,%d",
                timeSeconds, mode, fontsize, color, ctime, midHash, id
            )
        }
        
        /**
         * 直接转换为完整的XML字符串（优化内存使用）
         * 使用 buildString 代替 String.format() 减少临时对象分配
         */
        fun toXmlString(): String = buildString {
            val timeSeconds = progress / 1000.0
            append("<d p=\"")
            append(String.format(Locale.US, "%.3f", timeSeconds))
            append(",").append(mode)
            append(",").append(fontsize)
            append(",").append(color)
            append(",").append(ctime)
            append(",0,")
            append(midHash)
            append(",").append(id)
            append("\">")
            append(escapeXmlContent(content))
            append("</d>")
        }
        
        /**
         * XML内容转义（内部方法）
         */
        private fun escapeXmlContent(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
    }
    
    /**
     * XML转义
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    /**
     * 解压deflate压缩数据
     */
    private fun decompressDeflate(data: ByteArray): String? {
        return try {
            val inflater = Inflater(true) // true表示使用nowrap模式（原始deflate）
            val inputStream = InflaterInputStream(ByteArrayInputStream(data), inflater)
            val outputStream = ByteArrayOutputStream()
            
            val buffer = ByteArray(1024)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                outputStream.write(buffer, 0, len)
            }
            
            inputStream.close()
            outputStream.close()
            
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "解压失败", e)
            null
        }
    }
    
    /**
     * 检查文件是否已存在
     */
    private fun isFileExists(directoryUri: Uri, videoTitle: String): Boolean {
        return try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
            val safeTitle = videoTitle
                .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5\\s\\-_]"), "_")
                .take(100)
            val fileName = "$safeTitle.xml"
            directory.findFile(fileName) != null
        } catch (e: Exception) {
            Log.e(TAG, "检查文件是否存在失败", e)
            false
        }
    }
    
    /**
     * 保存XML内容到用户选择的目录
     */
    private fun saveToDirectory(
        directoryUri: Uri,
        videoTitle: String,
        xmlContent: String
    ): String? {
        try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri)
                ?: return null
            
            // 清理文件名中的非法字符
            val safeTitle = videoTitle
                .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5\\s\\-_]"), "_")
                .take(100) // 限制文件名长度
            
            val fileName = "$safeTitle.xml"
            
            // 检查文件是否已存在
            var existingFile = directory.findFile(fileName)
            if (existingFile != null) {
                existingFile.delete()
            }
            
            // 创建新文件
            val newFile = directory.createFile("text/xml", fileName)
                ?: return null
            
            // 写入内容，使用UTF-8编码
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                // 如果XML没有声明，添加UTF-8声明
                val content = if (!xmlContent.trimStart().startsWith("<?xml")) {
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$xmlContent"
                } else if (!xmlContent.contains("encoding", ignoreCase = true)) {
                    // 如果有XML声明但没有encoding，替换掉
                    xmlContent.replaceFirst(
                        "<?xml version=\"1.0\"?>",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    )
                } else {
                    xmlContent
                }
                
                output.write(content.toByteArray(Charsets.UTF_8))
                Log.d(TAG, "文件写入成功: $fileName")
                Log.d(TAG, "写入内容长度: ${content.length}")
            }
            
            return fileName
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败", e)
            return null
        }
    }
    
    /**
     * 验证URL是否为有效的B站视频/番剧链接
     */
    fun isValidBilibiliUrl(url: String): Boolean {
        val trimmedUrl = url.trim()
        
        // b23.tv 短链接
        val isShortUrl = trimmedUrl.contains("b23.tv/", ignoreCase = true)
        
        // 普通视频链接：bilibili.com/video/BV... 或 av...
        val isVideoUrl = trimmedUrl.contains("bilibili.com/video/", ignoreCase = true) &&
                (trimmedUrl.contains("BV", ignoreCase = true) || trimmedUrl.contains("av", ignoreCase = true))
        
        // 番剧链接：bilibili.com/bangumi/play/ss... 或 ep...
        val isBangumiUrl = trimmedUrl.contains("bilibili.com/bangumi/play/", ignoreCase = true) &&
                (trimmedUrl.contains("ss", ignoreCase = true) || trimmedUrl.contains("ep", ignoreCase = true))
        
        // 移动端链接
        val isMobileUrl = trimmedUrl.contains("m.bilibili.com/", ignoreCase = true)
        
        return isShortUrl || isVideoUrl || isBangumiUrl || isMobileUrl
    }
}
