package com.fam4k007.videoplayer.presentation



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.fam4k007.videoplayer.VideoFileParcelable

import com.fam4k007.videoplayer.VideoFolder

import com.fam4k007.videoplayer.preferences.PreferencesManager

import com.fam4k007.videoplayer.repository.VideoRepository

import com.fam4k007.videoplayer.repository.VideoSortOrder

import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.NaturalOrderComparator

import com.fam4k007.videoplayer.utils.media.MediaLibraryEvents

import com.fam4k007.videoplayer.utils.media.TreeViewScanner

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch



/**

 * 视频库ViewModel

 * 管理本地视频扫描、文件夹管理、视频列表等UI状态

 */

class LibraryViewModel(

    private val videoRepository: VideoRepository,

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

        val sortType: Int = 0,  // 0: 名称升序, 1: 名称降序, 2: 视频数量

        val sortOrder: Int = 0   // 0: 升序, 1: 降序

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

        val sortType: Int = 0,  // 0: 名称, 1: 时间, 2: 大小

        val sortOrder: Int = 0,  // 0: 升序, 1: 降序

        val searchQuery: String = ""

    )

    

    private val _videoListState = MutableStateFlow(VideoListState())

    val videoListState: StateFlow<VideoListState> = _videoListState.asStateFlow()

    

    // ==================== 树状视图状态 ====================



    // 全量文件夹数据缓存（扫描一次，树构建器复用）

    private var allFoldersCache: List<VideoFolder>? = null



    // 树状视图导航栈：每个元素是 (path, name)

    private val treeBackStack = mutableListOf<Pair<String?, String>>()



    // 树状视图当前层级的文件夹列表（直接输出到 folderListState）

    private val _treeCurrentFolders = MutableStateFlow<List<VideoFolder>>(emptyList())



    // 面包屑（用于 UI 展示）

    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String>>>(listOf("" to "根目录"))

    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _breadcrumbs.asStateFlow()



    // 树状视图中标记了有子文件夹的路径（用于 UI 判断是否显示子文件夹箭头）

    private val _treeHasSubfolders = MutableStateFlow<Set<String>>(emptySet())

    val treeHasSubfolders: StateFlow<Set<String>> = _treeHasSubfolders.asStateFlow()



    // 当前视图模式

    private val _viewMode = MutableStateFlow(preferencesManager.getFolderViewMode())

    val viewMode: StateFlow<String> = _viewMode.asStateFlow()



    init {

        // 从 PreferencesManager 恢复保存的排序设置

        loadSavedSortSettings()



        // 先显示缓存（秒开），再后台刷新

        // 树状视图/文件夹视图都走这个路径

        viewModelScope.launch {

            val cached = videoRepository.loadFolderCache()

            if (cached != null && cached.isNotEmpty()) {

                val sorted = filterBlacklistedFolders(cached)

                if (_viewMode.value == "TREE_VIEW") {

                    // 用缓存数据构建树状视图根级别

                    val nodes = TreeViewScanner.getChildren(allFolders = sorted, parentPath = null)

                    val rawFolders = nodes.map { node ->

                        VideoFolder(

                            folderPath = node.path,

                            folderName = node.name,

                            videoCount = node.videoCount,

                            videos = node.videos

                        )

                    }

                    val sortedFolders = sortFolders(rawFolders, _folderListState.value.sortType, _folderListState.value.sortOrder)

                    _folderListState.value = _folderListState.value.copy(folders = sortedFolders)

                    _treeHasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()

                    Logger.d(TAG, "树状视图：显示缓存的 ${sortedFolders.size} 个根节点")

                } else {

                    _folderListState.value = _folderListState.value.copy(folders = sorted)

                    Logger.d(TAG, "显示缓存的 ${sorted.size} 个文件夹")

                }

            }

            // 后台静默刷新（不显示加载动画）

            if (_viewMode.value == "TREE_VIEW") {

                loadTreeRoot(silent = true)

            } else {

                scanVideoFolders(silent = true)

            }

        }



        // 2. 监听媒体库变化事件，自动刷新（根据当前视图模式选择刷新方式）

        viewModelScope.launch {

            MediaLibraryEvents.changes.collect {

                Logger.d(TAG, "收到媒体库变化通知，延迟 2 秒后自动刷新")

                delay(2000) // 等 MediaScanner 完成索引更新

                if (_viewMode.value == "TREE_VIEW") {

                    refreshTreeView()

                } else {

                    scanVideoFolders(silent = true)

                }

            }

        }

    }

    

    /**

     * 加载保存的排序设置

     */

    private fun loadSavedSortSettings() {

        // 恢复文件夹排序设置

        val folderSortType = when (preferencesManager.getFolderSortType()) {

            "NAME" -> 0  // 名称

            "VIDEO_COUNT" -> 2  // 视频数量

            else -> 2  // 默认按视频数量

        }

        val folderSortOrder = when (preferencesManager.getFolderSortOrder()) {

            "ASCENDING" -> 0

            "DESCENDING" -> 1

            else -> 1  // 默认降序

        }

        _folderListState.value = _folderListState.value.copy(

            sortType = folderSortType,

            sortOrder = folderSortOrder

        )

        

        // 恢复视频列表排序设置

        val videoSortType = when (preferencesManager.getVideoSortType()) {

            "NAME" -> 0

            "DATE" -> 1

            "SIZE" -> 2

            else -> 0  // 默认按名称

        }

        val videoSortOrder = when (preferencesManager.getVideoSortOrder()) {

            "ASCENDING" -> 0

            "DESCENDING" -> 1

            else -> 0  // 默认升序

        }

        _videoListState.value = _videoListState.value.copy(

            sortType = videoSortType,

            sortOrder = videoSortOrder

        )

        

        Logger.d(TAG, "Loaded sort settings - Folder: type=$folderSortType, order=$folderSortOrder; Video: type=$videoSortType, order=$videoSortOrder")

    }

    

    // ==================== 文件夹扫描 ====================

    

    /**

     * 扫描所有本地视频文件夹

     * @param silent true=不显示加载动画，用于后台静默刷新

     */

    fun scanVideoFolders(silent: Boolean = false) {

        viewModelScope.launch {

            try {

                if (!silent) {

                    _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)

                }

                val allFolders = videoRepository.scanAllVideoFolders()

                val folders = filterBlacklistedFolders(allFolders)

                val sortedFolders = sortFolders(folders, _folderListState.value.sortType, _folderListState.value.sortOrder)

                _folderListState.value = _folderListState.value.copy(

                    folders = sortedFolders,

                    isLoading = false

                )

                // 缓存扫描结果到 SharedPreferences，下次秒开

                videoRepository.saveFolderCache(sortedFolders)

                Logger.d(TAG, "Scanned ${allFolders.size} video folders, filtered ${allFolders.size - folders.size} blacklisted")

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to scan video folders", e)

                if (!silent) {

                    _folderListState.value = _folderListState.value.copy(

                        isLoading = false,

                        error = e.message ?: "Unknown error"

                    )

                }

            }

        }

    }

    

    /**

     * 过滤掉黑名单中的文件夹

     */

    private fun filterBlacklistedFolders(folders: List<VideoFolder>): List<VideoFolder> {

        val blacklisted = preferencesManager.getBlacklistedFolders()

        if (blacklisted.isEmpty()) return folders

        return folders.filter { it.folderPath !in blacklisted }

    }



    /**

     * 刷新文件夹列表（触发 MediaStore 重新扫描 + 直接文件扫描）

     */

    fun refreshFolders() {

        viewModelScope.launch {

            try {

                _folderListState.value = _folderListState.value.copy(isRefreshing = true, error = null)



                val allFolders = videoRepository.forceRefreshScan()

                val folders = filterBlacklistedFolders(allFolders)

                val sortedFolders = sortFolders(folders, _folderListState.value.sortType, _folderListState.value.sortOrder)

                _folderListState.value = _folderListState.value.copy(

                    folders = sortedFolders,

                    isRefreshing = false

                )

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to refresh folders", e)

                _folderListState.value = _folderListState.value.copy(

                    isRefreshing = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

    }

    

    /**

     * 文件夹排序

     */

    fun sortFolders(sortType: Int, sortOrder: Int) {

        val sorted = sortFolders(_folderListState.value.folders, sortType, sortOrder)

        _folderListState.value = _folderListState.value.copy(

            folders = sorted,

            sortType = sortType,

            sortOrder = sortOrder

        )

        

        // 保存排序设置

        val sortTypeStr = when (sortType) {

            0 -> "NAME"

            2 -> "VIDEO_COUNT"

            else -> "NAME"

        }

        val sortOrderStr = when (sortOrder) {

            0 -> "ASCENDING"

            1 -> "DESCENDING"

            else -> "DESCENDING"

        }

        preferencesManager.setFolderSortType(sortTypeStr)

        preferencesManager.setFolderSortOrder(sortOrderStr)

        Logger.d(TAG, "Saved folder sort settings: type=$sortTypeStr, order=$sortOrderStr")

    }

    

    private fun sortFolders(folders: List<VideoFolder>, sortType: Int, sortOrder: Int): List<VideoFolder> {

        return when (sortType) {

            0 -> {
                // 使用自然排序：数字按数值大小比较，而非字典序
                val sorted = folders.sortedWith(NaturalOrderComparator.comparator { it.folderName })
                if (sortOrder == 0) sorted else sorted.reversed()
            }

            2 -> if (sortOrder == 0) folders.sortedBy { it.videoCount } else folders.sortedByDescending { it.videoCount }

            else -> folders  // 默认不排序

        }

    }

    

    // ==================== 视频扫描 ====================

    

    /**

     * 扫描指定文件夹的视频

     */

    fun scanVideosInFolder(folderPath: String) {

        viewModelScope.launch {

            try {

                _videoListState.value = _videoListState.value.copy(isLoading = true, error = null)

                val videos = videoRepository.scanVideosInFolder(folderPath, VideoSortOrder.NAME_ASC)

                val sortedVideos = sortVideos(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)

                _videoListState.value = _videoListState.value.copy(

                    videos = sortedVideos,

                    filteredVideos = filterVideos(sortedVideos, _videoListState.value.searchQuery),

                    isLoading = false

                )

                Logger.d(TAG, "Scanned ${videos.size} videos in folder: $folderPath")

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to scan videos in folder", e)

                _videoListState.value = _videoListState.value.copy(

                    isLoading = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

    }

    

    /**

     * 直接设置视频列表（用于预加载的视频列表）

     */

    fun setVideos(videos: List<VideoFileParcelable>) {

        val sortedVideos = sortVideos(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)

        _videoListState.value = _videoListState.value.copy(

            videos = sortedVideos,

            filteredVideos = filterVideos(sortedVideos, _videoListState.value.searchQuery),

            isLoading = false

        )

        Logger.d(TAG, "Set ${videos.size} preloaded videos")

    }

    

    /**

     * 刷新当前文件夹的视频列表

     */

    fun refreshVideos(folderPath: String) {

        viewModelScope.launch {

            try {

                _videoListState.value = _videoListState.value.copy(isRefreshing = true, error = null)

                val videos = videoRepository.scanVideosInFolder(folderPath, VideoSortOrder.NAME_ASC)

                val sortedVideos = sortVideos(videos, _videoListState.value.sortType, _videoListState.value.sortOrder)

                _videoListState.value = _videoListState.value.copy(

                    videos = sortedVideos,

                    filteredVideos = filterVideos(sortedVideos, _videoListState.value.searchQuery),

                    isRefreshing = false

                )

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to refresh videos", e)

                _videoListState.value = _videoListState.value.copy(

                    isRefreshing = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

    }

    

    // ==================== 视频排序 ====================

    

    /**

     * 视频排序

     */

    fun sortVideos(sortType: Int, sortOrder: Int) {

        val sorted = sortVideos(_videoListState.value.videos, sortType, sortOrder)

        _videoListState.value = _videoListState.value.copy(

            videos = sorted,

            filteredVideos = filterVideos(sorted, _videoListState.value.searchQuery),

            sortType = sortType,

            sortOrder = sortOrder

        )

        

        // 保存排序设置

        val sortTypeStr = when (sortType) {

            0 -> "NAME"

            1 -> "DATE"

            2 -> "SIZE"

            else -> "NAME"

        }

        val sortOrderStr = when (sortOrder) {

            0 -> "ASCENDING"

            1 -> "DESCENDING"

            else -> "ASCENDING"

        }

        preferencesManager.setVideoSortType(sortTypeStr)

        preferencesManager.setVideoSortOrder(sortOrderStr)

        Logger.d(TAG, "Saved video sort settings: type=$sortTypeStr, order=$sortOrderStr")

    }

    

    private fun sortVideos(videos: List<VideoFileParcelable>, sortType: Int, sortOrder: Int): List<VideoFileParcelable> {

        return when (sortType) {

            0 -> {
                // 使用自然排序：数字按数值大小比较，而非字典序
                val sorted = videos.sortedWith(NaturalOrderComparator.comparator { it.name })
                if (sortOrder == 0) sorted else sorted.reversed()
            }

            1 -> if (sortOrder == 0) videos.sortedBy { it.dateAdded } else videos.sortedByDescending { it.dateAdded }

            2 -> if (sortOrder == 0) videos.sortedBy { it.size } else videos.sortedByDescending { it.size }

            else -> videos

        }

    }

    

    // ==================== 视频搜索 ====================

    

    private var searchJob: kotlinx.coroutines.Job? = null



    /**

     * 搜索视频（带 300ms 防抖）

     */

    fun searchVideos(query: String) {

        // 取消上一次搜索

        searchJob?.cancel()

        searchJob = viewModelScope.launch {

            delay(300)  // 防抖：用户停止输入 300ms 后再执行搜索

            val filtered = filterVideos(_videoListState.value.videos, query)

            _videoListState.value = _videoListState.value.copy(

                searchQuery = query,

                filteredVideos = filtered

            )

        }

    }

    

    private fun filterVideos(videos: List<VideoFileParcelable>, query: String): List<VideoFileParcelable> {

        if (query.isBlank()) return videos

        return videos.filter { it.name.contains(query, ignoreCase = true) }

    }

    

    /**

     * 清空搜索

     */

    fun clearSearch() {

        _videoListState.value = _videoListState.value.copy(

            searchQuery = "",

            filteredVideos = _videoListState.value.videos

        )

    }



    // ==================== 视图模式切换 ====================



    /**

     * 切换视图模式

     */

    fun setViewMode(mode: String) {

        _viewMode.value = mode

        preferencesManager.setFolderViewMode(mode)



        // 清空当前文件夹数据，避免旧模式的数据残留

        _folderListState.value = _folderListState.value.copy(folders = emptyList())



        // 加载对应模式的根级别数据

        if (mode == "TREE_VIEW") {

            loadTreeRoot()

        } else {

            scanVideoFolders()

        }

    }



    // ==================== 树状视图操作 ====================



    /**

     * 加载树状视图根级别

     * 将 TreeViewScanner 的节点转为 VideoFolder，输出到 folderListState

     */

    fun loadTreeRoot(silent: Boolean = false) {

        viewModelScope.launch {

            try {

                if (!silent) {

                    _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)

                }

                val allFolders = getOrRefreshAllFolders()

                // 缓存全量文件夹数据到 SharedPreferences，下次 App 启动秒开

                videoRepository.saveFolderCache(allFolders)

                val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = null)

                val rawFolders = nodes.map { node ->

                    VideoFolder(

                        folderPath = node.path,

                        folderName = node.name,

                        videoCount = node.videoCount,

                        videos = node.videos

                    )

                }

                val sortedFolders = sortFolders(rawFolders, _folderListState.value.sortType, _folderListState.value.sortOrder)

                treeBackStack.clear()

                _breadcrumbs.value = listOf("" to "根目录")

                _treeHasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()



                _folderListState.value = _folderListState.value.copy(

                    folders = sortedFolders,

                    isLoading = false

                )

                Logger.d(TAG, "Loaded ${nodes.size} tree root nodes")

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to load tree root", e)

                if (!silent) {

                    _folderListState.value = _folderListState.value.copy(

                        isLoading = false,

                        error = e.message ?: "Unknown error"

                    )

                }

            }

        }

    }



    /**

     * 导航到树状视图的子文件夹

     */

    fun navigateToTreeFolder(path: String, name: String) {

        viewModelScope.launch {

            try {

                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)

                val allFolders = getOrRefreshAllFolders()

                val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = path)

                val rawFolders = nodes.map { node ->

                    VideoFolder(

                        folderPath = node.path,

                        folderName = node.name,

                        videoCount = node.videoCount,

                        videos = node.videos

                    )

                }

                val sortedFolders = sortFolders(rawFolders, _folderListState.value.sortType, _folderListState.value.sortOrder)



                // 避免刷新时重复添加：如果栈顶已是同一路径，说明是刷新当前层级，不追加
                val isRefresh = treeBackStack.isNotEmpty() && treeBackStack.last().first == path
                if (!isRefresh) {
                    treeBackStack.add(path to name)
                }

                _breadcrumbs.value = listOf("" to "根目录") + treeBackStack.map { (p, n) -> (p ?: "") to n }

                _treeHasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()



                _folderListState.value = _folderListState.value.copy(

                    folders = sortedFolders,

                    isLoading = false

                )

                Logger.d(TAG, "Navigated to tree folder: $path, ${nodes.size} children")

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to navigate to tree folder", e)

                _folderListState.value = _folderListState.value.copy(

                    isLoading = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

    }



    /**

     * 导航到面包屑指定的层级

     */

    fun navigateToBreadcrumb(index: Int) {

        if (index < 0 || index >= _breadcrumbs.value.size) return



        viewModelScope.launch {

            try {

                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)

                val allFolders = getOrRefreshAllFolders()



                while (treeBackStack.size > index) {

                    treeBackStack.removeLast()

                }



                val targetPath = if (index == 0) null else treeBackStack.lastOrNull()?.first

                _breadcrumbs.value = listOf("" to "根目录") + treeBackStack.map { (p, n) -> (p ?: "") to n }



                val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = targetPath)

                val folders = nodes.map { node ->

                    VideoFolder(

                        folderPath = node.path,

                        folderName = node.name,

                        videoCount = node.videoCount,

                        videos = node.videos

                    )

                }

                _treeHasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()



                _folderListState.value = _folderListState.value.copy(

                    folders = folders,

                    isLoading = false

                )

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to navigate to breadcrumb", e)

                _folderListState.value = _folderListState.value.copy(

                    isLoading = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

    }



    /**

     * 树状视图返回上一级

     * @return true 如果有上级可以返回，false 已经在根级别

     */

    fun treeNavigateBack(): Boolean {

        if (treeBackStack.isEmpty()) return false



        viewModelScope.launch {

            try {

                _folderListState.value = _folderListState.value.copy(isLoading = true, error = null)

                val allFolders = getOrRefreshAllFolders()



                treeBackStack.removeLast()

                val targetPath = if (treeBackStack.isEmpty()) null else treeBackStack.last().first

                _breadcrumbs.value = listOf("" to "根目录") + treeBackStack.map { (p, n) -> (p ?: "") to n }



                val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = targetPath)

                val rawFolders = nodes.map { node ->

                    VideoFolder(

                        folderPath = node.path,

                        folderName = node.name,

                        videoCount = node.videoCount,

                        videos = node.videos

                    )

                }

                val sortedFolders = sortFolders(rawFolders, _folderListState.value.sortType, _folderListState.value.sortOrder)

                _treeHasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()



                _folderListState.value = _folderListState.value.copy(

                    folders = sortedFolders,

                    isLoading = false

                )

            } catch (e: Exception) {

                Logger.e(TAG, "Failed to navigate back", e)

                _folderListState.value = _folderListState.value.copy(

                    isLoading = false,

                    error = e.message ?: "Unknown error"

                )

            }

        }

        return true

    }



    /**

     * 刷新树状视图

     */

    fun refreshTreeView() {

        allFoldersCache = null

        videoRepository.clearScanCache()

        if (treeBackStack.isEmpty()) {

            loadTreeRoot()

        } else {

            val (targetPath, targetName) = treeBackStack.last()

            if (targetPath != null) {

                navigateToTreeFolder(targetPath, targetName)

            } else {

                loadTreeRoot()

            }

        }

    }



    /**

     * 获取或刷新全量文件夹数据（复用 VideoRepository）

     */

    private suspend fun getOrRefreshAllFolders(): List<VideoFolder> {

        allFoldersCache?.let { return it }

        val folders = videoRepository.scanAllVideoFolders()

        val filtered = filterBlacklistedFolders(folders)

        // 不缓存空结果，避免无权限时空扫描污染缓存

        if (filtered.isNotEmpty()) {

            allFoldersCache = filtered

        }

        return filtered

    }

}

