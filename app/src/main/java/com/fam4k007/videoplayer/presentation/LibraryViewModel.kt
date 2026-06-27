package com.fam4k007.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.domain.media.FolderBrowserManager
import com.fam4k007.videoplayer.domain.media.MediaScanManager
import com.fam4k007.videoplayer.domain.media.TreeNavigationManager
import com.fam4k007.videoplayer.domain.media.VideoBrowserManager
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 视频库ViewModel
 *
 * 职责：UI 状态聚合与协调。
 * 排序/过滤/搜索/树状导航逻辑已提炼到对应的 Manager 中。
 *
 * 依赖的 Manager：
 * - FolderBrowserManager  — 文件夹排序、黑名单过滤、排序设置持久化
 * - VideoBrowserManager    — 视频排序、搜索过滤、排序设置持久化
 * - TreeNavigationManager  — 树状视图导航栈、面包屑、子文件夹标记
 * - MediaScanManager       — 媒体扫描协调、缓存管理
 */
class LibraryViewModel(
    private val mediaScanManager: MediaScanManager,
    private val folderBrowserManager: FolderBrowserManager,
    private val videoBrowserManager: VideoBrowserManager,
    private val treeNavigationManager: TreeNavigationManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    // ==================== 文件夹列表状态 ====================

    data class FolderListState(
        val folders: List<VideoFolder> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val sortType: Int = 0,
        val sortOrder: Int = 0
    )

    private val _folderListState = MutableStateFlow(FolderListState())
    val folderListState: StateFlow<FolderListState> = _folderListState.asStateFlow()

    // ==================== 视频列表状态 ====================

    data class VideoListState(
        val videos: List<VideoFileParcelable> = emptyList(),
        val filteredVideos: List<VideoFileParcelable> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val sortType: Int = 0,
        val sortOrder: Int = 0,
        val searchQuery: String = ""
    )

    private val _videoListState = MutableStateFlow(VideoListState())
    val videoListState: StateFlow<VideoListState> = _videoListState.asStateFlow()

    // ==================== 树状/视图状态（委托给 TreeNavigationManager） ====================

    val breadcrumbs: StateFlow<List<Pair<String, String>>> = treeNavigationManager.breadcrumbs
    val treeHasSubfolders: StateFlow<Set<String>> = treeNavigationManager.hasSubfolders

    private val _viewMode = MutableStateFlow(preferencesManager.getFolderViewMode())
    val viewMode: StateFlow<String> = _viewMode.asStateFlow()

    // 全量文件夹数据缓存（扫描一次，树构建器复用）
    private var allFoldersCache: List<VideoFolder>? = null

    // 搜索防抖 Job
    private var searchJob: Job? = null

    init {
        // 恢复保存的排序设置
        val (folderSortType, folderSortOrder) = folderBrowserManager.loadSavedSortSettings()
        _folderListState.value = _folderListState.value.copy(sortType = folderSortType, sortOrder = folderSortOrder)

        val (videoSortType, videoSortOrder) = videoBrowserManager.loadSavedSortSettings()
        _videoListState.value = _videoListState.value.copy(sortType = videoSortType, sortOrder = videoSortOrder)

        // 先显示缓存（秒开），再后台刷新
        viewModelScope.launch {
            val cached = mediaScanManager.loadFolderCache()
            if (cached != null && cached.isNotEmpty()) {
                val filtered = folderBrowserManager.filterBlacklisted(cached)
                if (_viewMode.value == "TREE_VIEW") {
                    val treeFolders = treeNavigationManager.loadRoot(filtered)
                    val sorted = folderBrowserManager.sort(treeFolders, folderSortType, folderSortOrder)
                    _folderListState.value = _folderListState.value.copy(folders = sorted)
                } else {
                    val sorted = folderBrowserManager.sort(filtered, folderSortType, folderSortOrder)
                    _folderListState.value = _folderListState.value.copy(folders = sorted)
                }
                Logger.d(TAG, "显示缓存的 ${filtered.size} 个文件夹")
            }
            // 后台静默刷新
            if (_viewMode.value == "TREE_VIEW") {
                loadTreeRoot(silent = true)
            } else {
                scanVideoFolders(silent = true)
            }
        }

        // 监听媒体库变化事件，自动刷新
        viewModelScope.launch {
            MediaLibraryEvents.changes.collect {
                Logger.d(TAG, "收到媒体库变化通知，延迟 2 秒后自动刷新")
                delay(2000)
                if (_viewMode.value == "TREE_VIEW") {
                    refreshTreeView()
                } else {
                    scanVideoFolders(silent = true)
                }
            }
        }
    }

    // ==================== 文件夹扫描 ====================

    fun scanVideoFolders(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!silent) {
                    _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)
                }
                val allFolders = mediaScanManager.scanAllFolders()
                val filtered = folderBrowserManager.filterBlacklisted(allFolders)
                val sorted = folderBrowserManager.sort(
                    filtered, _folderListState.value.sortType, _folderListState.value.sortOrder
                )
                _folderListState.value = _folderListState.value.copy(folders = sorted, isLoading = false)
                mediaScanManager.saveFolderCache(sorted)
                Logger.d(TAG, "扫描完成：${allFolders.size} 个文件夹，过滤后 ${filtered.size}")
            } catch (e: Exception) {
                Logger.e(TAG, "扫描文件夹失败", e)
                if (!silent) {
                    _folderListState.value = _folderListState.value.copy(
                        isLoading = false, error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            try {
                _folderListState.value = _folderListState.value.copy(isRefreshing = true, error = null)
                val allFolders = mediaScanManager.forceRefreshAllFolders()
                val filtered = folderBrowserManager.filterBlacklisted(allFolders)
                val sorted = folderBrowserManager.sort(
                    filtered, _folderListState.value.sortType, _folderListState.value.sortOrder
                )
                _folderListState.value = _folderListState.value.copy(folders = sorted, isRefreshing = false)
            } catch (e: Exception) {
                Logger.e(TAG, "刷新文件夹失败", e)
                _folderListState.value = _folderListState.value.copy(
                    isRefreshing = false, error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun sortFolders(sortType: Int, sortOrder: Int) {
        val sorted = folderBrowserManager.sort(_folderListState.value.folders, sortType, sortOrder)
        _folderListState.value = _folderListState.value.copy(folders = sorted, sortType = sortType, sortOrder = sortOrder)
        folderBrowserManager.saveSortSettings(sortType, sortOrder)
    }

    // ==================== 视频扫描 ====================

    fun scanVideosInFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                _videoListState.value = _videoListState.value.copy(isLoading = true, error = null)
                val videos = mediaScanManager.scanVideosInFolder(folderPath)
                val sorted = videoBrowserManager.sort(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)
                _videoListState.value = _videoListState.value.copy(
                    videos = sorted,
                    filteredVideos = videoBrowserManager.filter(sorted, _videoListState.value.searchQuery),
                    isLoading = false
                )
            } catch (e: Exception) {
                Logger.e(TAG, "扫描视频失败", e)
                _videoListState.value = _videoListState.value.copy(
                    isLoading = false, error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun setVideos(videos: List<VideoFileParcelable>) {
        val sorted = videoBrowserManager.sort(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)
        _videoListState.value = _videoListState.value.copy(
            videos = sorted,
            filteredVideos = videoBrowserManager.filter(sorted, _videoListState.value.searchQuery),
            isLoading = false
        )
    }

    fun refreshVideos(folderPath: String) {
        viewModelScope.launch {
            try {
                _videoListState.value = _videoListState.value.copy(isRefreshing = true, error = null)
                val videos = mediaScanManager.scanVideosInFolder(folderPath)
                val sorted = videoBrowserManager.sort(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)
                _videoListState.value = _videoListState.value.copy(
                    videos = sorted,
                    filteredVideos = videoBrowserManager.filter(sorted, _videoListState.value.searchQuery),
                    isRefreshing = false
                )
            } catch (e: Exception) {
                Logger.e(TAG, "刷新视频失败", e)
                _videoListState.value = _videoListState.value.copy(
                    isRefreshing = false, error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun sortVideos(sortType: Int, sortOrder: Int) {
        val sorted = videoBrowserManager.sort(_videoListState.value.videos, sortType, sortOrder)
        _videoListState.value = _videoListState.value.copy(
            videos = sorted,
            filteredVideos = videoBrowserManager.filter(sorted, _videoListState.value.searchQuery),
            sortType = sortType,
            sortOrder = sortOrder
        )
        videoBrowserManager.saveSortSettings(sortType, sortOrder)
    }

    fun searchVideos(query: String) {
        // 立即更新 searchQuery（同步），防止 Compose TextField 光标重置
        _videoListState.value = _videoListState.value.copy(searchQuery = query)
        // 过滤操作保持 300ms 防抖，避免频繁排序
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val filtered = videoBrowserManager.filter(_videoListState.value.videos, query)
            _videoListState.value = _videoListState.value.copy(filteredVideos = filtered)
        }
    }

    fun clearSearch() {
        _videoListState.value = _videoListState.value.copy(
            searchQuery = "", filteredVideos = _videoListState.value.videos
        )
    }

    // ==================== 视图模式 ====================

    fun setViewMode(mode: String) {
        _viewMode.value = mode
        preferencesManager.setFolderViewMode(mode)
        _folderListState.value = _folderListState.value.copy(folders = emptyList())
        if (mode == "TREE_VIEW") {
            loadTreeRoot()
        } else {
            scanVideoFolders()
        }
    }

    // ==================== 树状视图 ====================

    fun loadTreeRoot(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!silent) {
                    _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)
                }
                val allFolders = getOrRefreshAllFolders()
                mediaScanManager.saveFolderCache(allFolders)
                val rawFolders = treeNavigationManager.loadRoot(allFolders)
                val sorted = folderBrowserManager.sort(
                    rawFolders, _folderListState.value.sortType, _folderListState.value.sortOrder
                )
                _folderListState.value = _folderListState.value.copy(folders = sorted, isLoading = false)
            } catch (e: Exception) {
                Logger.e(TAG, "加载树根失败", e)
                if (!silent) {
                    _folderListState.value = _folderListState.value.copy(
                        isLoading = false, error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun navigateToTreeFolder(path: String, name: String) {
        viewModelScope.launch {
            try {
                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)
                val allFolders = getOrRefreshAllFolders()
                val isRefresh = treeNavigationManager.currentParentPath() == path
                val folders = treeNavigationManager.navigateTo(path, name, allFolders, isRefresh)
                val sorted = folderBrowserManager.sort(
                    folders, _folderListState.value.sortType, _folderListState.value.sortOrder
                )
                _folderListState.value = _folderListState.value.copy(folders = sorted, isLoading = false)
            } catch (e: Exception) {
                Logger.e(TAG, "导航树文件夹失败", e)
                _folderListState.value = _folderListState.value.copy(
                    isLoading = false, error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        viewModelScope.launch {
            try {
                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)
                val allFolders = getOrRefreshAllFolders()
                val folders = treeNavigationManager.navigateToBreadcrumb(index, allFolders)
                _folderListState.value = _folderListState.value.copy(folders = folders, isLoading = false)
            } catch (e: Exception) {
                Logger.e(TAG, "面包屑导航失败", e)
                _folderListState.value = _folderListState.value.copy(
                    isLoading = false, error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun treeNavigateBack(): Boolean {
        viewModelScope.launch {
            try {
                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)
                val allFolders = getOrRefreshAllFolders()
                val (_, folders) = treeNavigationManager.navigateBack(allFolders)
                val sorted = folderBrowserManager.sort(
                    folders, _folderListState.value.sortType, _folderListState.value.sortOrder
                )
                _folderListState.value = _folderListState.value.copy(folders = sorted, isLoading = false)
            } catch (e: Exception) {
                Logger.e(TAG, "树导航返回失败", e)
                _folderListState.value = _folderListState.value.copy(
                    isLoading = false, error = e.message ?: "Unknown error"
                )
            }
        }
        return !treeNavigationManager.isAtRoot()
    }

    fun refreshTreeView() {
        allFoldersCache = null
        mediaScanManager.clearMemoryCache()
        if (treeNavigationManager.isAtRoot()) {
            loadTreeRoot()
        } else {
            val path = treeNavigationManager.currentParentPath()
            if (path != null) {
                navigateToTreeFolder(path, "")
            } else {
                loadTreeRoot()
            }
        }
    }

    // ==================== 内部辅助 ====================

    private suspend fun getOrRefreshAllFolders(): List<VideoFolder> {
        allFoldersCache?.let { return it }
        val folders = mediaScanManager.scanAllFolders()
        val filtered = folderBrowserManager.filterBlacklisted(folders)
        if (filtered.isNotEmpty()) {
            allFoldersCache = filtered
        }
        return filtered
    }
}