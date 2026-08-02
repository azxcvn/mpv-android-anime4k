package com.fam4k007.videoplayer.repository

import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.bilibili.model.*
import com.fam4k007.videoplayer.dandanplay.DanDanPlayApi
import com.fam4k007.videoplayer.dandanplay.DanmakuResponse
import com.fam4k007.videoplayer.dandanplay.SearchAnimeResponse
import com.fam4k007.videoplayer.utils.Logger

/**
 * B站数据仓库
 * 封装B站API调用、认证、弹幕下载等数据访问逻辑
 * 
 * 职责：
 * - B站用户认证（二维码登录、Cookie管理）
 * - B站番剧信息获取
 * - B站视频播放地址解析
 * - 弹幕搜索和下载（通过DanDanPlay）
 */
class BilibiliRepository(
    private val authManager: BiliBiliAuthManager,
    private val danDanPlayApi: DanDanPlayApi
) {
    
    companion object {
        private const val TAG = "BilibiliRepository"
    }
    
    // ==================== 认证相关 ====================
    
    /**
     * 生成登录二维码
     */
    suspend fun generateQRCode(): Result<QRCodeInfo> {
        return try {
            authManager.generateQRCode()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to generate QR code: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 轮询二维码登录状态
     */
    suspend fun pollQRCodeStatus(qrcodeKey: String): LoginResult {
        return try {
            authManager.pollQRCodeStatus(qrcodeKey)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to poll QR code status: ${e.message}", e)
            LoginResult.Failed("Polling failed: ${e.message}")
        }
    }
    
    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return authManager.isLoggedIn()
    }
    
    /**
     * 获取当前用户信息
     */
    fun getUserInfo(): UserInfo? {
        return authManager.getUserInfo()
    }
    
    /**
     * 登出
     */
    fun logout() {
        authManager.logout()
        Logger.d(TAG, "User logged out")
    }
    
    /**
     * 获取Cookies（用于视频播放）
     */
    fun getCookies(): Map<String, String> {
        return authManager.getCookies()
    }
    
    /**
     * 刷新登录状态（刷新token）
     * 注意：BiliBiliAuthManager暂不支持token刷新，使用Cookie有效性检查代替
     * 后续可通过B站refresh_token API实现真正的刷新
     */
    suspend fun refreshLogin(): Result<Boolean> {
        return try {
            // 通过检查登录状态间接判断Cookie是否有效
            val isValid = authManager.isLoggedIn() && authManager.getUserInfo() != null
            Logger.d(TAG, "Login status check: $isValid")
            Result.success(isValid)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to refresh login: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== 番剧相关（预留接口，实际实现在BiliBiliAuthManager中）====================
    
    /**
     * 获取番剧列表（追番/收藏）
     * 注意：需要B站追番API（/x/space/bangumi/follow/list），待BiliBiliAuthManager扩展后实现
     * 当前返回空列表，不影响其他功能使用
     */
    suspend fun getBangumiList(): List<BangumiItem> {
        return try {
            Logger.d(TAG, "getBangumiList: pending B站追番API implementation")
            emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get bangumi list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 获取番剧详情
     * 注意：需要B站番剧详情API（/pgc/view/web/season），待实现
     */
    suspend fun getBangumiDetail(seasonId: String): BangumiDetail {
        return try {
            Logger.d(TAG, "getBangumiDetail: pending API implementation for seasonId=$seasonId")
            BangumiDetail(
                seasonId = seasonId,
                title = "",
                cover = "",
                description = "",
                episodes = emptyList()
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get bangumi detail: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 搜索番剧
     * 注意：需要B站搜索API（/x/web-interface/search/type），待实现
     */
    suspend fun searchBangumi(keyword: String): List<BangumiItem> {
        return try {
            Logger.d(TAG, "searchBangumi: pending API implementation for keyword=$keyword")
            emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to search bangumi: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 获取用户追番列表
     * 注意：需要B站追番API（/x/space/bangumi/follow/list），待BiliBiliAuthManager扩展后实现
     * 当前返回空列表，不影响其他功能使用
     */
    suspend fun getUserFollowedBangumi(): Result<List<BangumiItem>> {
        return try {
            Logger.d(TAG, "getUserFollowedBangumi: pending B站追番API implementation")
            Result.success(emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get user followed bangumi: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== 弹幕相关（DanDanPlay）====================
    
    /**
     * 搜索动漫（用于弹幕匹配）- 返回Anime列表
     * @param keyword 搜索关键词（通常是视频文件名）
     */
    suspend fun searchAnime(keyword: String): List<com.fam4k007.videoplayer.dandanplay.Anime> {
        return try {
            Logger.d(TAG, "Searching anime with keyword: $keyword")
            val result = danDanPlayApi.searchAnime(keyword)
            result.getOrNull()?.animes ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to search anime: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 搜索动漫（用于弹幕匹配）- 返回Result
     * @param keyword 搜索关键词（通常是视频文件名）
     */
    suspend fun searchAnimeResult(keyword: String): Result<SearchAnimeResponse> {
        return try {
            Logger.d(TAG, "Searching anime with keyword: $keyword")
            danDanPlayApi.searchAnime(keyword)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to search anime: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取弹幕
     * @param episodeId 剧集ID
     */
    suspend fun getDanmaku(episodeId: Int): Result<DanmakuResponse> {
        return try {
            Logger.d(TAG, "Getting danmaku for episode: $episodeId")
            danDanPlayApi.getDanmaku(episodeId)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get danmaku: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 下载弹幕到本地文件
     * @param episodeId 剧集ID
     * @param savePath 保存路径
     */
    suspend fun downloadDanmaku(episodeId: String, savePath: String) {
        try {
            val result = danDanPlayApi.getDanmaku(episodeId.toInt())
            result.onSuccess { response ->
                // 将弹幕数据写入文件
                val file = java.io.File(savePath)
                file.parentFile?.mkdirs()
                
                // 这里需要将DanmakuResponse转换为XML格式
                // 暂时简单处理
                val xml = buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    append("<i>\n")
                    response.comments.forEach { comment ->
                        append("<d p=\"${comment.p}\">${comment.m}</d>\n")
                    }
                    append("</i>")
                }
                file.writeText(xml)
                Logger.d(TAG, "Downloaded danmaku to: $savePath")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to download danmaku: ${e.message}", e)
            throw e
        }
    }
}
