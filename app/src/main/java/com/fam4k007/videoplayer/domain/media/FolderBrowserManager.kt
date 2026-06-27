package com.fam4k007.videoplayer.domain.media

import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.NaturalOrderComparator

/**
 * 文件夹浏览器管理器
 *
 * 负责文件夹排序、黑名单过滤、排序设置的持久化。
 * 将原本杂糅在 LibraryViewModel 中的排序/过滤逻辑提炼到此 Manager。
 *
 * 设计原则（遵循项目 Manager 模式）：
 * - 无状态工具类，不持有可变状态
 * - 排序字符串常量与 PreferencesManager 对齐
 */
class FolderBrowserManager(
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "FolderBrowserManager"
    }

    // ==================== 排序 ====================

    /**
     * 对文件夹列表进行排序
     * @param folders 待排序的文件夹列表
     * @param sortType 0=名称(自然排序), 2=视频数量
     * @param sortOrder 0=升序, 1=降序
     */
    fun sort(folders: List<VideoFolder>, sortType: Int, sortOrder: Int): List<VideoFolder> {
        return when (sortType) {
            0 -> {
                val sorted = folders.sortedWith(NaturalOrderComparator.comparator { it.folderName })
                if (sortOrder == 0) sorted else sorted.reversed()
            }
            2 -> if (sortOrder == 0) folders.sortedBy { it.videoCount } else folders.sortedByDescending { it.videoCount }
            else -> folders
        }
    }

    // ==================== 黑名单过滤 ====================

    /**
     * 过滤掉黑名单中的文件夹
     */
    fun filterBlacklisted(folders: List<VideoFolder>): List<VideoFolder> {
        val blacklisted = preferencesManager.getBlacklistedFolders()
        if (blacklisted.isEmpty()) return folders
        return folders.filter { it.folderPath !in blacklisted }
    }

    // ==================== 排序设置持久化 ====================

    /**
     * 加载保存的文件夹排序设置
     * @return Pair(sortType, sortOrder)
     */
    fun loadSavedSortSettings(): Pair<Int, Int> {
        val sortType = when (preferencesManager.getFolderSortType()) {
            "NAME" -> 0
            "VIDEO_COUNT" -> 2
            else -> 2
        }
        val sortOrder = when (preferencesManager.getFolderSortOrder()) {
            "ASCENDING" -> 0
            "DESCENDING" -> 1
            else -> 1
        }
        Logger.d(TAG, "Loaded folder sort settings: type=$sortType, order=$sortOrder")
        return sortType to sortOrder
    }

    /**
     * 保存文件夹排序设置
     */
    fun saveSortSettings(sortType: Int, sortOrder: Int) {
        val typeStr = when (sortType) {
            0 -> "NAME"
            2 -> "VIDEO_COUNT"
            else -> "NAME"
        }
        val orderStr = when (sortOrder) {
            0 -> "ASCENDING"
            1 -> "DESCENDING"
            else -> "DESCENDING"
        }
        preferencesManager.setFolderSortType(typeStr)
        preferencesManager.setFolderSortOrder(orderStr)
        Logger.d(TAG, "Saved folder sort settings: type=$typeStr, order=$orderStr")
    }
}
