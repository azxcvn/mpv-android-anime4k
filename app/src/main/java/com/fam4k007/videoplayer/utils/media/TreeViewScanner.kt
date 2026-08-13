package com.fam4k007.videoplayer.utils.media

import com.fam4k007.videoplayer.VideoFile
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.utils.Logger
import java.io.File
import java.util.Locale

/**
 * 树状视图构建器
 * 
 * 纯粹的树结构构建工具，不包含扫描逻辑。
 * 接收 VideoRepository 扫描出的文件夹列表，构建层级树结构。
 * 
 * 设计原则：扫描逻辑统一在 VideoRepository 中，这里只负责数据转换。
 */
object TreeViewScanner {
    private const val TAG = "TreeViewScanner"

    /**
     * 树状文件夹节点
     */
    data class TreeNode(
        val path: String,
        val name: String,
        val videoCount: Int,
        val hasSubfolders: Boolean,
        val videos: List<VideoFile> = emptyList(),
        val totalSize: Long = 0L,
        val latestDate: Long = 0L
    )

    /**
     * 根据已有的文件夹列表，获取指定路径下的直接子文件夹（树状视图）
     * 
     * @param allFolders VideoRepository 扫描出的所有包含视频的文件夹
     * @param parentPath 父级路径，null 表示根级别
     * @return 直接子文件夹节点列表
     */
    fun getChildren(
        allFolders: List<VideoFolder>,
        parentPath: String?
    ): List<TreeNode> {
        if (allFolders.isEmpty()) return emptyList()

        // 将 VideoFolder 列表转换为 path -> videos 的映射
        val folderMap = allFolders.associate { it.folderPath to it.videos }

        if (parentPath == null) {
            // 根级别：找到所有文件夹的最近公共祖先，然后返回其直接子目录
            val commonAncestor = findCommonAncestor(folderMap.keys)
            Logger.d(TAG, "Common ancestor: $commonAncestor")
            return getDirectChildren(commonAncestor, folderMap)
        } else {
            return getDirectChildren(parentPath, folderMap)
        }
    }

    /**
     * 找到所有路径的最近公共祖先目录
     */
    private fun findCommonAncestor(paths: Set<String>): String {
        if (paths.isEmpty()) return "/"
        if (paths.size == 1) return File(paths.first()).parent ?: "/"

        val splitPaths = paths.map { it.split("/").filter { s -> s.isNotEmpty() } }
        val minLength = splitPaths.minOf { it.size }
        val commonParts = mutableListOf<String>()

        for (i in 0 until minLength) {
            val part = splitPaths[0][i]
            if (splitPaths.all { it[i] == part }) {
                commonParts.add(part)
            } else {
                break
            }
        }

        return if (commonParts.isEmpty()) "/" else "/${commonParts.joinToString("/")}"
    }

    /**
     * 获取指定目录下的直接子文件夹（只包含有视频后代的）
     */
    private fun getDirectChildren(
        parentPath: String,
        allFolders: Map<String, List<VideoFile>>
    ): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        val normalizedParent = parentPath.trimEnd('/')
        val directChildren = mutableMapOf<String, ChildInfo>()

        for ((folderPath, videos) in allFolders) {
            val normalizedFolder = folderPath.trimEnd('/')

            // 判断这个文件夹是否在 parentPath 下
            if (!normalizedFolder.startsWith("$normalizedParent/")) continue

            // 计算相对于 parentPath 的路径
            val relativePath = normalizedFolder.removePrefix("$normalizedParent/")

            // 获取直接子文件夹名（第一级）
            val directChildName = relativePath.substringBefore("/")
            val directChildPath = "$normalizedParent/$directChildName"

            val childInfo = directChildren.getOrPut(directChildPath) {
                ChildInfo(directChildName)
            }

            // 累加视频计数、大小与最近日期
            childInfo.videoCount += videos.size
            childInfo.totalSize += videos.sumOf { it.size }
            childInfo.latestDate = maxOf(
                childInfo.latestDate,
                videos.maxOfOrNull { it.dateAdded } ?: 0L
            )

            // 如果不是直接子文件夹，说明有更深层的子目录
            if (relativePath.contains("/")) {
                childInfo.hasSubfolders = true
            }
        }

        // 检查是否有更深层的子文件夹
        for ((childPath, info) in directChildren) {
            if (!info.hasSubfolders) {
                info.hasSubfolders = hasDeeperSubfolders(childPath, allFolders)
            }
        }

        // 父文件夹自身的视频（直接位于当前层级、不属于任何子文件夹的视频）
        val parentVideos = allFolders[normalizedParent]
        if (parentVideos != null && parentVideos.isNotEmpty()) {
            val parentName = File(normalizedParent).name.ifEmpty { normalizedParent }
            result.add(
                TreeNode(
                    path = normalizedParent,
                    name = parentName,
                    videoCount = parentVideos.size,
                    hasSubfolders = false,
                    videos = parentVideos,
                    totalSize = parentVideos.sumOf { it.size },
                    latestDate = parentVideos.maxOfOrNull { it.dateAdded } ?: 0L
                )
            )
        }

        // 转换为 TreeNode 并排序
        for ((childPath, info) in directChildren) {
            result.add(
                TreeNode(
                    path = childPath,
                    name = info.name,
                    videoCount = info.videoCount,
                    hasSubfolders = info.hasSubfolders,
                    totalSize = info.totalSize,
                    latestDate = info.latestDate
                )
            )
        }

        return result.sortedWith(compareBy { it.name.lowercase(Locale.getDefault()) })
    }

    /**
     * 检查是否有更深层的子文件夹
     */
    private fun hasDeeperSubfolders(
        folderPath: String,
        allFolders: Map<String, List<VideoFile>>
    ): Boolean {
        val normalizedFolder = folderPath.trimEnd('/')
        for (otherPath in allFolders.keys) {
            val normalizedOther = otherPath.trimEnd('/')
            if (normalizedOther.startsWith("$normalizedFolder/") &&
                normalizedOther != normalizedFolder
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 辅助数据类
     */
    private data class ChildInfo(
        val name: String,
        var videoCount: Int = 0,
        var totalSize: Long = 0L,
        var latestDate: Long = 0L,
        var hasSubfolders: Boolean = false
    )
}
