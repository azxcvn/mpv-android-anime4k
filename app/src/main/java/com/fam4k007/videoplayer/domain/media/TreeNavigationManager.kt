package com.fam4k007.videoplayer.domain.media

import com.fam4k007.videoplayer.VideoFile
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.media.TreeViewScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 树状导航管理器
 *
 * 负责树状视图的导航栈管理、面包屑状态、子文件夹标记。
 * 将原本杂糅在 LibraryViewModel 中的树状导航逻辑提炼到此 Manager。
 *
 * 设计原则（遵循项目 Manager 模式）：
 * - 持有导航相关的可变状态（backStack, breadcrumbs, hasSubfolders）
 * - 所有导航操作通过此 Manager 统一管理
 */
class TreeNavigationManager {

    companion object {
        private const val TAG = "TreeNavigationManager"
    }

    // 导航栈：每个元素是 (path, name)，path 为 null 表示根级别
    private val backStack = mutableListOf<Pair<String?, String>>()

    // 面包屑状态
    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String>>>(listOf("" to "根目录"))
    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _breadcrumbs.asStateFlow()

    // 当前层级中有子文件夹的节点路径集合（用于 UI 判断是否显示子文件夹箭头）
    private val _hasSubfolders = MutableStateFlow<Set<String>>(emptySet())
    val hasSubfolders: StateFlow<Set<String>> = _hasSubfolders.asStateFlow()

    // ==================== 导航操作 ====================

    /**
     * 获取当前层级的父路径（null=根级别）
     */
    fun currentParentPath(): String? {
        return backStack.lastOrNull()?.first
    }

    /**
     * 是否在根级别
     */
    fun isAtRoot(): Boolean = backStack.isEmpty()

    /**
     * 导航到子文件夹
     * @param path 目标文件夹路径
     * @param name 目标文件夹名称
     * @param allFolders 全量文件夹数据
     * @param isRefresh 是否是刷新当前层级（不追加到导航栈）
     * @return 当前层级的文件夹列表
     */
    fun navigateTo(
        path: String,
        name: String,
        allFolders: List<VideoFolder>,
        isRefresh: Boolean = false
    ): List<VideoFolder> {
        if (!isRefresh) {
            backStack.add(path to name)
        }
        updateBreadcrumbs()
        val result = buildTreeNodeFolders(allFolders, path)
        Logger.d(TAG, "Navigated to: $path, ${result.size} children, stack depth=${backStack.size}")
        return result
    }

    /**
     * 导航到面包屑指定的层级
     * @param index 面包屑索引，0=根级别
     * @param allFolders 全量文件夹数据
     * @return 当前层级的文件夹列表
     */
    fun navigateToBreadcrumb(index: Int, allFolders: List<VideoFolder>): List<VideoFolder> {
        while (backStack.size > index) {
            backStack.removeLast()
        }
        val targetPath = if (index == 0) null else backStack.lastOrNull()?.first
        updateBreadcrumbs()
        val result = loadLevel(allFolders, targetPath)
        Logger.d(TAG, "Navigated to breadcrumb index=$index, targetPath=$targetPath")
        return result
    }

    /**
     * 返回上一级
     * @param allFolders 全量文件夹数据
     * @return Pair(是否成功返回, 当前层级文件夹列表)
     */
    fun navigateBack(allFolders: List<VideoFolder>): Pair<Boolean, List<VideoFolder>> {
        if (backStack.isEmpty()) return false to emptyList()
        backStack.removeLast()
        val targetPath = if (backStack.isEmpty()) null else backStack.last().first
        updateBreadcrumbs()
        val result = loadLevel(allFolders, targetPath)
        Logger.d(TAG, "Navigated back, remaining stack depth=${backStack.size}")
        return true to result
    }

    /**
     * 加载根级别
     * @param allFolders 全量文件夹数据
     * @return 根级别的文件夹列表
     */
    fun loadRoot(allFolders: List<VideoFolder>): List<VideoFolder> {
        backStack.clear()
        updateBreadcrumbs()
        val result = loadLevel(allFolders, null)
        Logger.d(TAG, "Loaded tree root, ${result.size} nodes")
        return result
    }

    // ==================== 内部方法 ====================

    /**
     * 加载指定级别的文件夹列表
     */
    private fun loadLevel(allFolders: List<VideoFolder>, parentPath: String?): List<VideoFolder> {
        val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = parentPath)
        _hasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()
        return buildTreeNodeFolders(nodes)
    }

    /**
     * 根据 parentPath 构建 TreeNode 并转为 VideoFolder 列表
     */
    private fun buildTreeNodeFolders(allFolders: List<VideoFolder>, parentPath: String): List<VideoFolder> {
        val nodes = TreeViewScanner.getChildren(allFolders = allFolders, parentPath = parentPath)
        _hasSubfolders.value = nodes.filter { it.hasSubfolders }.map { it.path }.toSet()
        return buildTreeNodeFolders(nodes)
    }

    /**
     * 将 TreeNode 列表转为 VideoFolder 列表
     */
    private fun buildTreeNodeFolders(nodes: List<TreeViewScanner.TreeNode>): List<VideoFolder> {
        return nodes.map { node ->
            VideoFolder(
                folderPath = node.path,
                folderName = node.name,
                videoCount = node.videoCount,
                videos = node.videos
            )
        }
    }

    /**
     * 更新面包屑状态
     */
    private fun updateBreadcrumbs() {
        _breadcrumbs.value = listOf("" to "根目录") + backStack.map { (p, n) -> (p ?: "") to n }
    }
}
