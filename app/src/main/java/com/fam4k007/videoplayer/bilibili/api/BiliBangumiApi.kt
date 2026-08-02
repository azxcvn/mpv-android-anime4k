package com.fam4k007.videoplayer.bilibili.api

import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.bilibili.model.*
import com.fam4k007.videoplayer.utils.Logger
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * B站番剧API封装
 * 使用OkHttp调用B站Web端API
 */
class BiliBangumiApi(
    private val authManager: BiliBiliAuthManager
) {
    companion object {
        private const val TAG = "BiliBangumiApi"
        private const val BASE_URL = "https://api.bilibili.com"
    }

    private val client = authManager.getClient()
    private val gson = Gson()

    /**
     * 获取索引筛选条件
     * @param seasonType 1=番剧, 2=电影, 3=纪录片, 4=国创
     */
    suspend fun getIndexCondition(seasonType: Int = 1): Result<PgcIndexConditionData> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/pgc/season/index/condition".toHttpUrl().newBuilder()
                    .addQueryParameter("season_type", seasonType.toString())
                    .addQueryParameter("type", "0")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Network error"))

                val apiResponse = gson.fromJson(body, PgcIndexConditionResponse::class.java)
                if (apiResponse.code != 0) {
                    return@withContext Result.failure(Exception("API error: ${apiResponse.message}"))
                }

                Result.success(apiResponse.data ?: PgcIndexConditionData(null, null))
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get index condition", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取索引结果
     * @param params 筛选参数
     * @param page 页码
     * @param pageSize 每页数量
     */
    suspend fun getIndexResult(
        params: Map<String, String>,
        page: Int,
        pageSize: Int = 21
    ): Result<PgcIndexResultData> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$BASE_URL/pgc/season/index/result".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("pagesize", pageSize.toString())
                    .addQueryParameter("season_type", "1")
                    .addQueryParameter("type", "0")

                // 添加筛选参数
                params.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Network error"))

                val apiResponse = gson.fromJson(body, PgcIndexResultResponse::class.java)
                if (apiResponse.code != 0) {
                    return@withContext Result.failure(Exception("API error: ${apiResponse.message}"))
                }

                Result.success(apiResponse.data ?: PgcIndexResultData(0, null))
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get index result", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取番剧详情（支持 season_id 或 ep_id）
     * @param id 季度ID或集ID
     * @param isEpId 是否为集ID
     */
    suspend fun getSeasonInfo(id: Int, isEpId: Boolean = false): Result<PgcInfoResult> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/pgc/view/web/season".toHttpUrl().newBuilder()
                    .addQueryParameter(if (isEpId) "ep_id" else "season_id", id.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://www.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Network error"))
                Logger.d(TAG, "getSeasonInfo: code=${response.code}, body=${body.take(300)}")

                val apiResponse = gson.fromJson(body, PgcInfoResponse::class.java)
                if (apiResponse.code != 0) {
                    return@withContext Result.failure(Exception("API error: ${apiResponse.message}"))
                }

                val result = apiResponse.result ?: return@withContext Result.failure(Exception("Empty data"))
                Result.success(result)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get season info", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取播放地址
     * @param avid 稿件avid
     * @param bvid 稿件bvid
     * @param cid 视频cid
     * @param epId 集ID
     * @param seasonId 季度ID
     * @param qn 画质ID
     * @param fnval 格式位掩码
     */
    suspend fun getPlayUrl(
        avid: Long,
        bvid: String,
        cid: Long,
        epId: Int,
        seasonId: Int,
        qn: Int = 80,
        fnval: Int = 4048
    ): Result<PlayUrlResult> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/pgc/player/web/playurl".toHttpUrl().newBuilder()
                    .addQueryParameter("avid", avid.toString())
                    .addQueryParameter("bvid", bvid)
                    .addQueryParameter("cid", cid.toString())
                    .addQueryParameter("ep_id", epId.toString())
                    .addQueryParameter("season_id", seasonId.toString())
                    .addQueryParameter("qn", qn.toString())
                    .addQueryParameter("fnval", fnval.toString())
                    .addQueryParameter("fnver", "0")
                    .addQueryParameter("fourk", "1")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://www.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Network error"))
                Logger.d(TAG, "getPlayUrl: code=${response.code}, body=${body.take(300)}")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                // 尝试解析包装格式 { code, message, result }
                try {
                    val apiResponse = gson.fromJson(body, BiliPlayUrlResponse::class.java)
                    if (apiResponse.code == 0 && apiResponse.result != null) {
                        return@withContext Result.success(apiResponse.result)
                    }
                } catch (_: Exception) {}

                // 降级：直接解析 result 字段
                val jsonObj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                val code = jsonObj.get("code")?.asInt
                if (code != null && code != 0) {
                    val msg = jsonObj.get("message")?.asString ?: "Unknown error"
                    return@withContext Result.failure(Exception("Failed to get play URL ($code): $msg"))
                }
                
                val resultObj = jsonObj.getAsJsonObject("result")
                    ?: jsonObj  // 如果没有 result 包装，直接就是 result
                val playResult = gson.fromJson(resultObj, PlayUrlResult::class.java)
                Result.success(playResult)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to get play url", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 搜索番剧
     * @param keyword 搜索关键词
     */
    suspend fun searchBangumi(keyword: String): Result<List<SearchBangumiItem>> {
        return try {
            val url = "$BASE_URL/x/web-interface/wbi/search/type".toHttpUrl().newBuilder()
                .addQueryParameter("search_type", "7")
                .addQueryParameter("keyword", keyword)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.bilibili.com")
                .header("User-Agent", "Mozilla/5.0")
                .header("Origin", "https://www.bilibili.com")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = withContext(Dispatchers.IO) { response.body?.string() } ?: return Result.failure(Exception("Network error"))
            Logger.d(TAG, "Search response: code=${response.code}, body=${body.take(300)}")

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonObj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val code = jsonObj.get("code")?.asInt ?: -1
            if (code != 0) {
                val msg = jsonObj.get("message")?.asString ?: "Unknown error"
                return Result.failure(Exception("Search failed ($code): $msg"))
            }

            val data = jsonObj.get("data")
            if (data == null || data.isJsonNull) return Result.success(emptyList())

            val dataObj = data.asJsonObject
            val resultElement = dataObj.get("result")
            if (resultElement == null || resultElement.isJsonNull) return Result.success(emptyList())

            val resultArray = resultElement.asJsonArray
            val items = resultArray.map { gson.fromJson(it, SearchBangumiItem::class.java) }
            Result.success(items)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to search bangumi", e)
            Result.failure(e)
        }
    }
}