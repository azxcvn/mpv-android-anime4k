package com.fam4k007.videoplayer.navigation

import kotlinx.serialization.Serializable

/**
 * 应用导航路由定义
 * 使用 @Serializable 实现类型安全的导航
 */
sealed interface AppScreen {

    /** 主页 */
    @Serializable
    data object Home : AppScreen

    /** 设置页 */
    @Serializable
    data object Settings : AppScreen

    /** 播放设置页 */
    @Serializable
    data object PlaybackSettings : AppScreen

    /** 播放历史页 */
    @Serializable
    data object PlaybackHistory : AppScreen

    /** 文件夹黑名单页 */
    @Serializable
    data object FolderBlacklist : AppScreen

    /** 文件夹白名单页 */
    @Serializable
    data object FolderWhitelist : AppScreen

    /** 其他媒体设置页 */
    @Serializable
    data object MediaSettings : AppScreen

    /** 关于页 */
    @Serializable
    data object About : AppScreen

    /** 设备信息页 */
    @Serializable
    data object DeviceInfo : AppScreen

    /** 错误日志页 */
    @Serializable
    data object LogViewer : AppScreen

    /** 缓存管理页 */
    @Serializable
    data object CacheManagement : AppScreen

        /** 开源许可页 */
        @Serializable
        data object License : AppScreen

    /** 用户协议页 */
    @Serializable
    data object UserAgreement : AppScreen

    /** WebDAV 账户列表页 */
    @Serializable
    data object WebDavAccounts : AppScreen

    /** WebDAV 文件浏览页 */
    @Serializable
    data class WebDavBrowser(val accountId: String) : AppScreen

    /** B站弹幕下载页 */
    @Serializable
    data object BiliBiliDanmaku : AppScreen

    /** 弹幕服务器管理页 */
    @Serializable
    data object DanmakuServerManagement : AppScreen

    /** 视频浏览页 */
    @Serializable
    data object VideoBrowser : AppScreen

    /** 视频下载页（B站） */
    @Serializable
    data object Download : AppScreen

    /** 下载管理器页 */
    @Serializable
    data object DownloadManager : AppScreen

    /** 字幕搜索页 */
    @Serializable
    data object SubtitleSearch : AppScreen

    /** B站登录页 */
    @Serializable
    data object BiliBiliLogin : AppScreen

    /** 番剧索引页 */
    @Serializable
    data object BangumiIndex : AppScreen

    /** 番剧详情页 */
    @Serializable
    data class BangumiDetail(val seasonId: Int, val isEpId: Boolean = false) : AppScreen

    /** TV浏览器页 */
    @Serializable
    data class TVBrowser(val initialUrl: String = "") : AppScreen

    /** 媒体信息页 */
    @Serializable
    data class MediaInfo(val videoUri: String, val videoName: String) : AppScreen

    /** 视频列表页 */
    @Serializable
    data class VideoList(val folderName: String, val folderPath: String) : AppScreen
}
