package com.fam4k007.videoplayer.domain.media

import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.repository.VideoRepository
import com.fam4k007.videoplayer.repository.VideoSortOrder
import com.fam4k007.videoplayer.utils.Logger

/**
 * 媒体扫描管理器
 *
 * 封装 VideoRepository 的扫描调用，提供统一的扫描入口。
 * 负责：
 * - 全量文件夹扫描（含内存缓存协调）
 * - 单文件夹视频扫描
 * - 缓存保存/加载策略协调
 *
 * 设计原则（遵循项目 Manager 模式）：
 * - 无内部可变状态，所有缓存委托给 VideoRepository
 * - 作为 ViewModel 与 Repository 之间的协调层
 */
class MediaScanManager(
    private val videoRepository: VideoRepository
) {

    companion object {
        private const val TAG = "MediaScanManager"
    }

    // ==================== 全量扫描 ====================

    /**
     * 扫描所有包含视频的文件夹
     * 如果内存缓存有效则直接返回缓存
     */
    suspend fun scanAllFolders(): List<VideoFolder> {
        return videoRepository.scanAllVideoFolders()
    }

    /**
     * 强制刷新全量扫描（清除内存缓存后重新扫描）
     */
    suspend fun forceRefreshAllFolders(): List<VideoFolder> {
        return videoRepository.forceRefreshScan()
    }

    /**
     * 获取缓存的扫描结果（不触发重新扫描）
     */
    fun getCachedFolders(): List<VideoFolder>? {
        return videoRepository.getCachedScanResult()
    }

    // ==================== 单文件夹扫描 ====================

    /**
     * 扫描指定文件夹中的视频
     */
    suspend fun scanVideosInFolder(folderPath: String): List<VideoFileParcelable> {
        return videoRepository.scanVideosInFolder(folderPath, VideoSortOrder.NAME_ASC)
    }

    // ==================== 缓存管理 ====================

    /**
     * 保存文件夹列表缓存到 SharedPreferences
     */
    suspend fun saveFolderCache(folders: List<VideoFolder>) {
        videoRepository.saveFolderCache(folders)
    }

    /**
     * 从 SharedPreferences 加载文件夹缓存
     */
    suspend fun loadFolderCache(): List<VideoFolder>? {
        return videoRepository.loadFolderCache()
    }

    /**
     * 清除所有缓存（内存缓存 + SharedPreferences 缓存）
     */
    suspend fun clearAllCache() {
        videoRepository.clearScanCache()
        videoRepository.clearFolderCache()
    }

    /**
     * 清除内存扫描缓存
     */
    fun clearMemoryCache() {
        videoRepository.clearScanCache()
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否有可用的内存缓存
     */
    fun hasMemoryCache(): Boolean {
        return videoRepository.getCachedScanResult() != null
    }

    /**
     * 从缓存获取视频列表
     */
    suspend fun getCachedVideos(folderPath: String): List<VideoFileParcelable> {
        return videoRepository.getCachedVideos(folderPath)
    }
}
