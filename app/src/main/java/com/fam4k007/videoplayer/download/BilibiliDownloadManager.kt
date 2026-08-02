package com.fam4k007.videoplayer.download

import android.content.Context
import android.util.Log
import com.fam4k007.videoplayer.utils.CookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BilibiliDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val TAG = "BilibiliDownloadManager"

    // 验证Cookie是否有效
    suspend fun verifyCookie(): Result<UserStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val cookie = CookieManager.getBilibiliCookie(context)
                if (cookie.isEmpty()) {
                    return@withContext Result.failure(Exception("Not logged in, please log in to Bilibili first"))
                }
                val request = Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/nav")
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Network request failed")
                val json = JSONObject(body)
                if (json.getInt("code") == 0) {
                    val data = json.getJSONObject("data")
                    val isLogin = data.getBoolean("isLogin")
                    if (!isLogin) {
                        return@withContext Result.failure(Exception("Cookie expired, please log in again"))
                    }
                    val vipInfo = data.optJSONObject("vip_info")
                    val vipStatus = vipInfo?.optInt("status", 0) ?: 0
                    val vipType = vipInfo?.optInt("type", 0) ?: 0
                    Result.success(UserStatus(
                        isLogin = true,
                        isVip = vipStatus == 1,
                        vipType = vipType,
                        uname = data.optString("uname", "Unknown User")
                    ))
                } else {
                    Result.failure(Exception("Verification failed: ${json.optString("message")}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cookie验证失败", e)
                Result.failure(e)
            }
        }
    }

    // 自动选择最佳画质（根据会员状态）
    suspend fun getBestQuality(): Int {
        val status = verifyCookie()
        return if (status.isSuccess && status.getOrNull()?.isVip == true) {
            127 // 8K/最高画质
        } else if (status.isSuccess) {
            80 // 1080P
        } else {
            64 // 720P
        }
    }

    // 自动识别视频/番剧/短链，区分API调用
    suspend fun getMediaInfo(id: String, cid: String, quality: Int = 64, isBangumi: Boolean = false, epId: String? = null, seasonId: String? = null): Result<VideoInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val headers = mapOf(
                    "Cookie" to CookieManager.getBilibiliCookie(context),
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to "https://www.bilibili.com"
                )
                val fragments = mutableListOf<DownloadFragment>()
                
                // 构建URL和参数
                val urlBuilder = if (isBangumi) {
                    // 番剧API
                    HttpUrl.Builder()
                        .scheme("https")
                        .host("api.bilibili.com")
                        .addPathSegments("pgc/player/web/playurl")
                        .addQueryParameter("cid", cid)
                        .addQueryParameter("qn", quality.toString())
                        .addQueryParameter("fnval", "4048")
                        .addQueryParameter("fourk", "1")
                        .apply {
                            epId?.let { addQueryParameter("ep_id", it) }
                            seasonId?.let { addQueryParameter("season_id", it) }
                        }
                } else {
                    // 普通视频API - 判断是BV号还是AV号
                    val isBV = id.startsWith("BV")
                    val cleanId = if (isBV) id else id.removePrefix("av")
                    
                    HttpUrl.Builder()
                        .scheme("https")
                        .host("api.bilibili.com")
                        .addPathSegments("x/player/playurl")
                        .apply {
                            if (isBV) {
                                addQueryParameter("bvid", cleanId)
                            } else {
                                addQueryParameter("avid", cleanId)
                            }
                        }
                        .addQueryParameter("cid", cid)
                        .addQueryParameter("qn", quality.toString())
                        .addQueryParameter("fnval", "4048")
                        .addQueryParameter("fourk", "1")
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .apply { headers.forEach { addHeader(it.key, it.value) } }
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val code = json.getInt("code")
                    if (code == 0) {
                        // 番剧API返回result，普通视频返回data
                        val dataObj = if (isBangumi) {
                            json.optJSONObject("result")
                        } else {
                            json.optJSONObject("data")
                        }
                        
                        if (dataObj == null) {
                            Log.e(TAG, "API返回结构异常: $responseBody")
                            return@withContext Result.failure(Exception("API returned empty data"))
                        }
                        
                        val dash = dataObj.optJSONObject("dash")
                        if (dash != null) {
                            val video = dash.optJSONArray("video")
                            val audio = dash.optJSONArray("audio")
                            
                            // 只取第一个（最高质量）video
                            video?.let {
                                if (it.length() > 0) {
                                    val v = it.getJSONObject(0)
                                    fragments.add(
                                        DownloadFragment(
                                            url = v.getString("baseUrl").replace("http:", "https:"),
                                            size = (v.optDouble("bandWidth", 0.0) * dash.optDouble("duration", 0.0) / 8).toLong(),
                                            type = "video"
                                        )
                                    )
                                    Log.d(TAG, "添加视频片段，大小估计: ${fragments[0].size / 1024 / 1024}MB")
                                }
                            }
                            
                            // 只取第一个（最高质量）audio
                            audio?.let {
                                if (it.length() > 0) {
                                    val a = it.getJSONObject(0)
                                    fragments.add(
                                        DownloadFragment(
                                            url = a.getString("baseUrl").replace("http:", "https:"),
                                            size = (a.optDouble("bandWidth", 0.0) * dash.optDouble("duration", 0.0) / 8).toLong(),
                                            type = "audio"
                                        )
                                    )
                                    Log.d(TAG, "添加音频片段，大小估计: ${fragments[1].size / 1024 / 1024}MB")
                                }
                            }
                        }
                        Result.success(VideoInfo(id, cid, fragments))
                    } else {
                        // 处理常见错误码
                        val message = when (code) {
                            -400 -> "Request error, please check the link"
                            -403, -404 -> "No access or video not found, may need login or premium"
                            -10403 -> "Premium membership required"
                            -352 -> "Risk control verification failed, please try again later"
                            else -> json.optString("message", "Unknown error")
                        }
                        Log.e(TAG, "API错误: code=$code, message=$message")
                        Result.failure(Exception("$message (code: $code)"))
                    }
                } else {
                    Result.failure(Exception("Network request failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取媒体信息失败", e)
                Result.failure(e)
            }
        }
    }

    // 下载文件
    suspend fun downloadFile(
        url: String,
        file: File,
        taskId: String,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", CookieManager.getBilibiliCookie(context))
                    .addHeader("Referer", "https://www.bilibili.com")
                    .build()

                val call = client.newCall(request)
                // 注册 Call 到共享存储，以便暂停时取消
                DownloadTaskStore.activeCalls[taskId] = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    DownloadTaskStore.activeCalls.remove(taskId)
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }

                val body = response.body ?: run {
                    DownloadTaskStore.activeCalls.remove(taskId)
                    return@withContext Result.failure(Exception("Response body is empty"))
                }

                val contentLength = body.contentLength()
                var bytesRead = 0L

                try {
                    file.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                // 每次读取后检查协程是否被取消
                                ensureActive()
                                output.write(buffer, 0, read)
                                bytesRead += read
                                onProgress(bytesRead, contentLength)
                            }
                        }
                    }
                } finally {
                    DownloadTaskStore.activeCalls.remove(taskId)
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                Log.d(TAG, "下载被取消: $url")
                throw e // 让协程框架处理取消
            } catch (e: java.io.IOException) {
                // 判断是否是因为取消导致的IO异常
                if (DownloadTaskStore.activeCalls[taskId]?.isCanceled() == true ||
                    DownloadTaskStore.getItem(taskId)?.status == "paused") {
                    Log.d(TAG, "下载因暂停中断: $url")
                    throw CancellationException("Download paused")
                }
                Log.e(TAG, "下载文件失败", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "下载文件失败", e)
                Result.failure(e)
            } finally {
                DownloadTaskStore.activeCalls.remove(taskId)
            }
        }
    }

    // 解析链接，自动识别视频/番剧/短链，返回aid/cid/title/类型/epId/seasonId
    suspend fun parseMediaUrl(url: String): MediaParseResult {
        return withContext(Dispatchers.IO) {
            try {
                val result = parseIdType(url)
                if (result == null) throw Exception("Unable to recognize Bilibili link or ID")
                if (result.type == MediaType.Video) {
                    val aid = result.id
                    // 直接使用aid（可能是BV号或av号）
                    val detail = getVideoDetail(aid)
                    MediaParseResult(
                        aid = aid,
                        cid = detail.first,
                        title = detail.second,
                        type = MediaType.Video
                    )
                } else if (result.type == MediaType.Bangumi) {
                    // 番剧API，优先用ep_id
                    val bangumiDetail = getBangumiDetail(result.id)
                    MediaParseResult(
                        aid = bangumiDetail.aid,
                        cid = bangumiDetail.cid,
                        title = bangumiDetail.title,
                        type = MediaType.Bangumi,
                        epId = bangumiDetail.epId,
                        seasonId = bangumiDetail.seasonId
                    )
                } else {
                    throw Exception("Unsupported type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析媒体链接失败", e)
                throw e
            }
        }
    }
    // 解析ID类型（视频/番剧/短链），支持完整URL和纯ID
    private fun parseIdType(raw: String): IdTypeResult? {
        val input = raw.trim()
        Log.d(TAG, "开始解析输入: $input")
        
        // 优先匹配完整URL格式
        val urlPatterns = listOf(
            // 普通视频URL
            "bilibili\\.com/video/av(\\d+)".toRegex() to MediaType.Video,
            "bilibili\\.com/video/(BV[a-zA-Z0-9]{10})".toRegex() to MediaType.Video,
            // 番剧URL
            "bilibili\\.com/bangumi/play/ss(\\d+)".toRegex() to MediaType.Bangumi,
            "bilibili\\.com/bangumi/play/ep(\\d+)".toRegex() to MediaType.Bangumi,
            "bilibili\\.com/bangumi/play/md(\\d+)".toRegex() to MediaType.Bangumi,
            // 移动端URL
            "m\\.bilibili\\.com/video/av(\\d+)".toRegex() to MediaType.Video,
            "m\\.bilibili\\.com/video/(BV[a-zA-Z0-9]{10})".toRegex() to MediaType.Video,
            "m\\.bilibili\\.com/bangumi/play/ss(\\d+)".toRegex() to MediaType.Bangumi,
            "m\\.bilibili\\.com/bangumi/play/ep(\\d+)".toRegex() to MediaType.Bangumi
        )
        
        for ((regex, type) in urlPatterns) {
            val match = regex.find(input)
            if (match != null) {
                val id = match.groupValues[1]
                val fullId = if (type == MediaType.Video && !id.startsWith("BV") && !id.startsWith("av")) {
                    "av$id"
                } else if (type == MediaType.Bangumi && !id.matches("(ss|ep|md)\\d+".toRegex())) {
                    // 番剧ID需要带前缀
                    when {
                        input.contains("/ss") -> "ss$id"
                        input.contains("/ep") -> "ep$id"
                        input.contains("/md") -> "md$id"
                        else -> id
                    }
                } else {
                    id
                }
                Log.d(TAG, "URL匹配成功: $fullId, 类型: $type")
                return IdTypeResult(fullId, type)
            }
        }
        
        // 匹配纯ID格式
        val idPatterns = listOf(
            "^av(\\d+)$".toRegex() to MediaType.Video,
            "^(BV[a-zA-Z0-9]{10})$".toRegex() to MediaType.Video,
            "^ep(\\d+)$".toRegex() to MediaType.Bangumi,
            "^ss(\\d+)$".toRegex() to MediaType.Bangumi,
            "^md(\\d+)$".toRegex() to MediaType.Bangumi,
            // 纯数字当作av号
            "^(\\d+)$".toRegex() to MediaType.Video
        )
        
        for ((regex, type) in idPatterns) {
            val match = regex.find(input)
            if (match != null) {
                val id = match.groupValues[1]
                val fullId = if (type == MediaType.Video && !id.startsWith("BV") && !id.startsWith("av")) {
                    "av$id"
                } else {
                    match.value
                }
                Log.d(TAG, "ID匹配成功: $fullId, 类型: $type")
                return IdTypeResult(fullId, type)
            }
        }
        
        // b23.tv短链 - 需要解析真实URL
        if (input.contains("b23.tv/", ignoreCase = true)) {
            Log.d(TAG, "检测到短链，开始解析")
            try {
                val realUrl = resolveShortUrl(input)
                if (realUrl != null) {
                    Log.d(TAG, "短链解析成功: $realUrl")
                    // 递归解析真实URL
                    return parseIdType(realUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "短链解析失败", e)
            }
        }
        
        Log.d(TAG, "无法识别输入格式")
        return null
    }
    // 获取番剧详情（ep_id/ss_id/md_id）
    private suspend fun getBangumiDetail(id: String): BangumiDetailResult {
        // 先判断类型
        val epMatch = "ep(\\d+)".toRegex().find(id)
        val ssMatch = "ss(\\d+)".toRegex().find(id)
        val mdMatch = "md(\\d+)".toRegex().find(id)
        val seasonId = ssMatch?.groupValues?.get(1) ?: mdMatch?.groupValues?.get(1)
        val epId = epMatch?.groupValues?.get(1)
        val url = if (seasonId != null) {
            "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
        } else if (epId != null) {
            "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
        } else {
            throw Exception("Unable to recognize anime ID")
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0")
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("Cookie", CookieManager.getBilibiliCookie(context))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Anime details request failed")
        val json = JSONObject(body)
        if (json.optInt("code", -1) != 0) throw Exception("Anime API error: ${json.optString("message")}")
        val data = json.optJSONObject("result") ?: json.optJSONObject("data") ?: throw Exception("Anime data is empty")
        val episodes = data.optJSONArray("episodes") ?: throw Exception("Anime episode list is empty")
        val firstEp = episodes.optJSONObject(0) ?: throw Exception("Anime episode parsing failed")
        return BangumiDetailResult(
            aid = firstEp.optString("aid"),
            cid = firstEp.optString("cid"),
            title = firstEp.optString("long_title", firstEp.optString("title", "Unknown Bangumi")),
            epId = firstEp.optString("id"),
            seasonId = data.optString("season_id")
        )
    }

    // 获取番剧所有集数列表
    suspend fun getBangumiEpisodes(id: String): Result<List<EpisodeInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                // 支持多种输入格式：ep103924, ss12345, md12345, 或纯数字
                val epMatch = "ep(\\d+)".toRegex().find(id)
                val ssMatch = "ss(\\d+)".toRegex().find(id)
                val mdMatch = "md(\\d+)".toRegex().find(id)
                
                val seasonId = ssMatch?.groupValues?.get(1) ?: mdMatch?.groupValues?.get(1)
                val epId = epMatch?.groupValues?.get(1)
                
                val url = when {
                    seasonId != null -> "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
                    epId != null -> "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
                    id.matches("\\d+".toRegex()) -> "https://api.bilibili.com/pgc/view/web/season?season_id=$id"
                    else -> throw Exception("Unable to recognize anime ID: $id")
                }
                
                Log.d(TAG, "获取番剧集数列表，URL: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://www.bilibili.com")
                    .addHeader("Cookie", CookieManager.getBilibiliCookie(context))
                    .build()
                    
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Anime details request failed")
                
                Log.d(TAG, "番剧API响应: ${body.take(200)}")
                
                val json = JSONObject(body)
                val code = json.optInt("code", -1)
                
                if (code != 0) {
                    val message = json.optString("message", "Unknown error")
                    Log.e(TAG, "番剧API错误: code=$code, message=$message")
                    throw Exception("Anime API error: $message")
                }
                
                val data = json.optJSONObject("result") ?: throw Exception("Anime data is empty")
                val episodes = data.optJSONArray("episodes") ?: throw Exception("Anime episode list is empty")
                val seasonIdFromApi = data.optString("season_id", "")
                
                Log.d(TAG, "获取到${episodes.length()}集，season_id: $seasonIdFromApi")
                
                val episodeList = mutableListOf<EpisodeInfo>()
                for (i in 0 until episodes.length()) {
                    val ep = episodes.getJSONObject(i)
                    episodeList.add(
                        EpisodeInfo(
                            episodeId = ep.optString("id"),
                            aid = ep.optString("aid"),
                            cid = ep.optString("cid"),
                            title = ep.optString("title", "Episode ${i+1}"),
                            longTitle = ep.optString("long_title", ""),
                            index = i + 1,
                            badge = ep.optString("badge", ""),
                            badgeType = ep.optInt("badge_type", 0)
                        )
                    )
                }
                
                Result.success(episodeList)
            } catch (e: Exception) {
                Log.e(TAG, "获取番剧集数列表失败", e)
                Result.failure(e)
            }
        }
    }

    // 解析b23.tv短链，获取真实URL
    private fun resolveShortUrl(shortUrl: String): String? {
        return try {
            val url = if (!shortUrl.startsWith("http")) {
                "https://$shortUrl"
            } else {
                shortUrl
            }
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            
            // 不自动跟随重定向，手动获取Location
            val clientNoRedirect = client.newBuilder()
                .followRedirects(false)
                .build()
            
            val response = clientNoRedirect.newCall(request).execute()
            val location = response.header("Location")
            
            Log.d(TAG, "短链重定向到: $location")
            location
        } catch (e: Exception) {
            Log.e(TAG, "解析短链失败", e)
            null
        }
    }

    // 从URL中提取视频ID
    private fun extractVideoId(url: String): String {
        // 支持多种URL格式
        val patterns = listOf(
            "video/(av\\d+)".toRegex(),
            "video/(BV[a-zA-Z0-9]+)".toRegex(),
            "av(\\d+)".toRegex(),
            "BV([a-zA-Z0-9]+)".toRegex(),
            "b23\\.tv/([a-zA-Z0-9]+)".toRegex(),
            "bilibili\\.com/([a-zA-Z0-9]+)".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                val id = match.groupValues[1]
                Log.d(TAG, "从URL $url 提取到ID: $id")
                return id
            }
        }

        // 如果是纯数字，可能是AV号
        if (url.matches("\\d+".toRegex())) {
            Log.d(TAG, "纯数字ID: $url")
            return "av$url"
        }

        Log.d(TAG, "无法从URL提取ID: $url")
        return ""
    }

    // BV号转AV号
    private fun bv2av(bv: String): String {
        if (!bv.startsWith("BV")) return bv

        try {
            val bvCode = bv.substring(2)
            if (bvCode.length != 10) {
                Log.e(TAG, "BV号长度错误: $bv")
                return bv
            }

            // B站官方算法
            val table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
            val tr = mutableMapOf<Char, Int>()
            for (i in table.indices) {
                tr[table[i]] = i
            }

            val s = intArrayOf(11, 10, 3, 8, 4, 6)
            val xor = 177451812L
            val add = 8728348608L

            var r = 0L
            for (i in 0..5) {
                r += tr[bvCode[s[i]]]!! * Math.pow(58.0, i.toDouble()).toLong()
            }

            val av = (r - add) xor xor
            Log.d(TAG, "BV: $bv -> AV: $av")
            return av.toString()
        } catch (e: Exception) {
            Log.e(TAG, "BV转AV失败: $bv", e)
            return bv
        }
    }

    // 获取视频详情
    private suspend fun getVideoDetail(aid: String): Pair<String, String> {
        // 判断是BV号还是AV号
        val isBV = aid.startsWith("BV")
        val cleanId = if (isBV) aid else aid.removePrefix("av")
        
        Log.d(TAG, "获取视频详情: 原始=$aid, 清理后=$cleanId, isBV=$isBV")
        
        // 先尝试无cookie请求
        try {
            val url = if (isBV) {
                "https://api.bilibili.com/x/web-interface/view?bvid=$cleanId"
            } else {
                "https://api.bilibili.com/x/web-interface/view?aid=$cleanId"
            }
            
            val simpleRequest = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36")
                .build()

            Log.d(TAG, "尝试无cookie请求: $url")
            val simpleResponse = client.newCall(simpleRequest).execute()
            val simpleBody = simpleResponse.body?.string()

            if (simpleResponse.isSuccessful && simpleBody != null) {
                val json = JSONObject(simpleBody)
                if (json.getInt("code") == 0) {
                    val data = json.getJSONObject("data")
                    val title = data.getString("title")
                    val pages = data.getJSONArray("pages")
                    val cid = pages.getJSONObject(0).getString("cid")
                    Log.d(TAG, "无cookie成功: $title, $cid")
                    return Pair(cid, title)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "无cookie请求失败", e)
        }

        // 如果无cookie请求失败，则使用带cookie的请求
        val url = if (isBV) {
            "https://api.bilibili.com/x/web-interface/view?bvid=$cleanId"
        } else {
            "https://api.bilibili.com/x/web-interface/view?aid=$cleanId"
        }
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", CookieManager.getBilibiliCookie(context))
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Origin", "https://www.bilibili.com")
            .build()

        Log.d(TAG, "请求URL: $url")
        val cookie = CookieManager.getBilibiliCookie(context)
        Log.d(TAG, "Cookie长度: ${cookie.length}, 内容: ${cookie.take(100)}...")

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        Log.d(TAG, "响应码: ${response.code}")
        Log.d(TAG, "响应头: ${response.headers}")
        Log.d(TAG, "响应体: $responseBody")

        if (response.isSuccessful && responseBody != null) {
            val json = JSONObject(responseBody)
            val code = json.getInt("code")
            Log.d(TAG, "API响应码: $code")

            if (code == 0) {
                val data = json.getJSONObject("data")
                val title = data.getString("title")
                val pages = data.getJSONArray("pages")
                val cid = pages.getJSONObject(0).getString("cid")
                Log.d(TAG, "标题: $title, CID: $cid")
                return Pair(cid, title)
            } else {
                val message = json.optString("message", "Unknown error")
                throw Exception("Failed to get video details: $message (code: $code)")
            }
        } else {
            throw Exception("Network request failed: ${response.code} - ${response.message}")
        }
    }
}

data class VideoInfo(
    val aid: String,
    val cid: String,
    val fragments: List<DownloadFragment>
)

data class DownloadFragment(
    val url: String,
    val size: Long,
    val type: String // "video" or "audio"
)

enum class MediaType { Video, Bangumi }

data class IdTypeResult(val id: String, val type: MediaType)

data class MediaParseResult(
    val aid: String,
    val cid: String,
    val title: String,
    val type: MediaType,
    val epId: String? = null,
    val seasonId: String? = null
)

data class BangumiDetailResult(
    val aid: String,
    val cid: String,
    val title: String,
    val epId: String?,
    val seasonId: String?
)

data class UserStatus(
    val isLogin: Boolean,
    val isVip: Boolean,
    val vipType: Int, // 0:无 1:月度 2:年度
    val uname: String
)