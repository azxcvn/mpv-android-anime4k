package com.fam4k007.videoplayer.domain.media

import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.NaturalOrderComparator

/**
 * 视频浏览器管理器
 *
 * 负责视频排序、搜索过滤、排序设置的持久化。
 * 将原本杂糅在 LibraryViewModel 中的排序/搜索逻辑提炼到此 Manager。
 *
 * 设计原则（遵循项目 Manager 模式）：
 * - 无状态工具类，不持有可变状态
 * - 排序字符串常量与 PreferencesManager 对齐
 */
class VideoBrowserManager(
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "VideoBrowserManager"
    }

    // ==================== 排序 ====================

    /**
     * 对视频列表进行排序
     * @param videos 待排序的视频列表
     * @param sortType 0=名称(自然排序), 1=日期, 2=大小
     * @param sortOrder 0=升序, 1=降序
     */
    fun sort(videos: List<VideoFileParcelable>, sortType: Int, sortOrder: Int): List<VideoFileParcelable> {
        return when (sortType) {
            0 -> {
                val sorted = videos.sortedWith(NaturalOrderComparator.comparator { it.name })
                if (sortOrder == 0) sorted else sorted.reversed()
            }
            1 -> if (sortOrder == 0) videos.sortedBy { it.dateAdded } else videos.sortedByDescending { it.dateAdded }
            2 -> if (sortOrder == 0) videos.sortedBy { it.size } else videos.sortedByDescending { it.size }
            else -> videos
        }
    }

    // ==================== 搜索过滤 ====================

    /**
     * 客户端内存过滤：按名称（不区分大小写）匹配
     */
    fun filter(videos: List<VideoFileParcelable>, query: String): List<VideoFileParcelable> {
        if (query.isBlank()) return videos
        return videos.filter { it.name.contains(query, ignoreCase = true) }
    }

    // ==================== 排序设置持久化 ====================

    /**
     * 加载保存的视频排序设置
     * @return Pair(sortType, sortOrder)
     */
    fun loadSavedSortSettings(): Pair<Int, Int> {
        val sortType = when (preferencesManager.getVideoSortType()) {
            "NAME" -> 0
            "DATE" -> 1
            "SIZE" -> 2
            else -> 0
        }
        val sortOrder = when (preferencesManager.getVideoSortOrder()) {
            "ASCENDING" -> 0
            "DESCENDING" -> 1
            else -> 0
        }
        Logger.d(TAG, "Loaded video sort settings: type=$sortType, order=$sortOrder")
        return sortType to sortOrder
    }

    /**
     * 保存视频排序设置
     */
    fun saveSortSettings(sortType: Int, sortOrder: Int) {
        val typeStr = when (sortType) {
            0 -> "NAME"
            1 -> "DATE"
            2 -> "SIZE"
            else -> "NAME"
        }
        val orderStr = when (sortOrder) {
            0 -> "ASCENDING"
            1 -> "DESCENDING"
            else -> "ASCENDING"
        }
        preferencesManager.setVideoSortType(typeStr)
        preferencesManager.setVideoSortOrder(orderStr)
        Logger.d(TAG, "Saved video sort settings: type=$typeStr, order=$orderStr")
    }
}
