package com.fam4k007.videoplayer.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.fam4k007.videoplayer.AppConstants
import com.fam4k007.videoplayer.VideoFileParcelable
import com.fam4k007.videoplayer.VideoFolder
import com.fam4k007.videoplayer.database.VideoDatabase
import com.fam4k007.videoplayer.database.VideoCacheEntity
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 视频数据仓库
 * 封装视频扫描、缓存、数据库操作等数据访问逻辑
 */
class VideoRepository(
    private val context: Context,
    private val database: VideoDatabase
) {
    
    companion object {
        private const val TAG = "VideoRepository"
        private const val MAX_SUPPLEMENTARY_FILES = 1000
        private const val MAX_SCAN_FILES = 2000
    }
    
    private val videoCacheDao = database.videoCacheDao()

    /** 内存缓存：全量扫描结果，避免每次进场都重新扫 */
    private var scanResultCache: List<VideoFolder>? = null
    /** 缓存对应的扫描选项标签，选项变更时自动失效 */
    private var cacheOptionsTag: String? = null

    /**
     * 获取缓存的全量扫描结果（若存在）
     */
    fun getCachedScanResult(): List<VideoFolder>? = scanResultCache

    /**
     * 清除内存缓存
     */
    fun clearScanCache() {
        scanResultCache = null
        cacheOptionsTag = null
    }

    /**
     * 强制刷新全量扫描（清除缓存）
     */
    suspend fun forceRefreshScan(): List<VideoFolder> = withContext(Dispatchers.IO) {
        scanResultCache = null
        scanAllVideoFolders()
    }

    /**
     * 扫描指定文件夹的所有视频文件（通过MediaStore）
     * @param folderPath 文件夹路径
     * @param sortOrder 排序方式（NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC）
     * @return 视频列表
     */
    suspend fun scanVideosInFolder(
        folderPath: String,
        sortOrder: VideoSortOrder = VideoSortOrder.NAME_ASC
    ): List<VideoFileParcelable> = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED
            )
            
            val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$folderPath/%")
            val orderBy = when (sortOrder) {
                VideoSortOrder.NAME_ASC -> "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                VideoSortOrder.NAME_DESC -> "${MediaStore.Video.Media.DISPLAY_NAME} DESC"
                VideoSortOrder.DATE_ASC -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
                VideoSortOrder.DATE_DESC -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            }
            
            val videos = mutableListOf<VideoFileParcelable>()
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                orderBy
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    // 确保文件真的在该文件夹下（排除子文件夹）
                    val file = File(path)
                    // 检查文件是否真实存在（MediaStore可能有过期数据）
                    if (!file.exists()) continue
                    if (file.parent == folderPath) {
                        val uri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        videos.add(
                            VideoFileParcelable(
                                uri = uri.toString(),
                                name = name,
                                path = path,
                                size = size,
                                duration = duration,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
            
            // 补充扫描：当 .nomedia 关闭或隐藏文件夹开启时，MediaStore 可能遗漏文件
            val prefs = com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context)
            val needSupplementary = prefs.getIncludeNoMediaFolders() || prefs.isScanHiddenFoldersEnabled()
            if (needSupplementary) {
                val folderFile = File(folderPath)
                if (folderFile.exists() && folderFile.isDirectory) {
                    val knownPaths = videos.map { it.path }.toMutableSet()
                    folderFile.listFiles()?.forEach { file ->
                        if (!file.isFile || !file.exists()) return@forEach
                        val path = file.absolutePath
                        if (path in knownPaths) return@forEach
                        if (ScanFilter.shouldSkipFile(context, path)) return@forEach

                        val extension = file.extension.lowercase()
                        if (extension in com.fam4k007.videoplayer.AppConstants.Files.SUPPORTED_VIDEO_EXTENSIONS) {
                            val msDuration = com.fam4k007.videoplayer.utils.ScanFilter.queryDuration(context, path)
                            videos.add(
                                VideoFileParcelable(
                                    uri = android.net.Uri.fromFile(file).toString(),
                                    name = file.name,
                                    path = path,
                                    size = file.length(),
                                    duration = msDuration,
                                    dateAdded = file.lastModified() / 1000
                                )
                            )
                            knownPaths.add(path)
                        }
                    }
                }
            }

            Logger.d(TAG, "Scanned ${videos.size} videos in folder: $folderPath")
            videos
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan videos in folder: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 从缓存获取视频列表（支持排序）
     * @param folderPath 文件夹路径
     * @param sortOrder 排序方式
     * @return 视频缓存列表
     */
    suspend fun getVideosFromCache(
        folderPath: String,
        sortOrder: VideoSortOrder = VideoSortOrder.NAME_ASC
    ): List<VideoCacheEntity> = withContext(Dispatchers.IO) {
        try {
            when (sortOrder) {
                VideoSortOrder.NAME_ASC -> videoCacheDao.getVideosByFolderSortedByNameAsc(folderPath)
                VideoSortOrder.NAME_DESC -> videoCacheDao.getVideosByFolderSortedByNameDesc(folderPath)
                VideoSortOrder.DATE_ASC -> videoCacheDao.getVideosByFolderSortedByDateAsc(folderPath)
                VideoSortOrder.DATE_DESC -> videoCacheDao.getVideosByFolderSortedByDateDesc(folderPath)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get videos from cache: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 分页获取视频列表（支持大量视频）
     * @param folderPath 文件夹路径
     * @param sortOrder 排序方式
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 视频缓存列表
     */
    suspend fun getVideosPaged(
        folderPath: String,
        sortOrder: VideoSortOrder,
        limit: Int,
        offset: Int
    ): List<VideoCacheEntity> = withContext(Dispatchers.IO) {
        try {
            when (sortOrder) {
                VideoSortOrder.NAME_ASC -> videoCacheDao.getVideosByFolderPagedByNameAsc(folderPath, limit, offset)
                VideoSortOrder.NAME_DESC -> videoCacheDao.getVideosByFolderPagedByNameDesc(folderPath, limit, offset)
                VideoSortOrder.DATE_ASC -> videoCacheDao.getVideosByFolderPagedByDateAsc(folderPath, limit, offset)
                VideoSortOrder.DATE_DESC -> videoCacheDao.getVideosByFolderPagedByDateDesc(folderPath, limit, offset)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get videos paged: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 获取文件夹中的视频总数
     */
    suspend fun getVideoCount(folderPath: String): Int = withContext(Dispatchers.IO) {
        try {
            videoCacheDao.getVideoCountByFolder(folderPath)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get video count: ${e.message}", e)
            0
        }
    }
    
    /**
     * 缓存视频列表到数据库
     * @param videos 视频列表
     */
    suspend fun cacheVideos(videos: List<VideoCacheEntity>) = withContext(Dispatchers.IO) {
        try {
            videoCacheDao.insertVideos(videos)
            Logger.d(TAG, "Cached ${videos.size} videos to database")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cache videos: ${e.message}", e)
        }
    }
    
    /**
     * 删除过期的缓存（清理扫描时间早于指定时间戳的记录）
     * @param timestamp 时间戳
     * @return 删除的记录数
     */
    suspend fun deleteOldCache(timestamp: Long): Int = withContext(Dispatchers.IO) {
        try {
            val count = videoCacheDao.deleteOldEntries(timestamp)
            Logger.d(TAG, "Deleted $count old cache entries")
            count
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete old cache: ${e.message}", e)
            0
        }
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clearAllCache(): Int = withContext(Dispatchers.IO) {
        try {
            val count = videoCacheDao.clearAll()
            Logger.d(TAG, "Cleared all cache entries: $count")
            count
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear cache: ${e.message}", e)
            0
        }
    }
    
    /**
     * 获取缓存总数
     */
    suspend fun getCacheCount(): Int = withContext(Dispatchers.IO) {
        try {
            videoCacheDao.getVideoCount()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get cache count: ${e.message}", e)
            0
        }
    }
    
    /**
     * 扫描所有包含视频的文件夹
     * @return 视频文件夹列表
     */
    suspend fun scanAllVideoFolders(): List<com.fam4k007.videoplayer.VideoFolder> = withContext(Dispatchers.IO) {
        val prefs = com.fam4k007.videoplayer.preferences.PreferencesManager.getInstance(context)
        val includeNoMedia = prefs.getIncludeNoMediaFolders()
        val includeHidden = prefs.isScanHiddenFoldersEnabled()
        val currentTag = "nomedia=$includeNoMedia,hidden=$includeHidden"

        // 内存缓存命中且选项未变更 → 直接返回
        if (scanResultCache != null && cacheOptionsTag == currentTag) {
            Logger.d(TAG, "返回内存缓存 ${scanResultCache!!.size} 个文件夹")
            return@withContext scanResultCache!!
        }

        // 选项变了 → 之前的缓存失效
        scanResultCache = null
        cacheOptionsTag = currentTag

        try {
            val folderMap = mutableMapOf<String, MutableList<com.fam4k007.videoplayer.VideoFile>>()

            if (includeNoMedia || includeHidden) {
                // ──────────────────────────────────────────────
                // 方案 A：包含 .nomedia 或隐藏文件夹
                // 用 File API 直接从存储根目录扫描（不走 MediaStore）
                // 因为 MediaStore 内建过滤，无法拿到 .nomedia 中的文件
                // ──────────────────────────────────────────────
                ScanFilter.clearCache()
                scanFromStorageRoots(folderMap, context, prefs)
                Logger.d(TAG, "File API 扫描完成，共 ${folderMap.size} 个文件夹")
            } else {
                // ──────────────────────────────────────────────
                // 方案 B：默认路径 — 使用 MediaStore（快）
                // ──────────────────────────────────────────────
                scanFromMediaStore(folderMap)
                Logger.d(TAG, "MediaStore 扫描完成，共 ${folderMap.size} 个文件夹")
            }

            val folders = folderMap.map { (folderPath, videos) ->
                val folderName = File(folderPath).name
                com.fam4k007.videoplayer.VideoFolder(
                    folderPath = folderPath,
                    folderName = folderName,
                    videoCount = videos.size,
                    videos = videos
                )
            }.sortedByDescending { it.videoCount }
            
            // 写入内存缓存（不缓存空结果，避免授权前空扫描污染缓存）
            if (folders.isNotEmpty()) {
                scanResultCache = folders
                cacheOptionsTag = currentTag
            }

            Logger.d(TAG, "Scanned ${folders.size} video folders")
            folders

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan video folders: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 搜索视频
     * @param query 搜索关键词
     * @return 匹配的视频列表
     */
    suspend fun searchVideos(query: String): List<VideoFileParcelable> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext emptyList()
            }
            
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )
            
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            val videos = mutableListOf<VideoFileParcelable>()
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    
                    val file = File(path)
                    val folderPath = file.parent ?: ""
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    videos.add(
                        VideoFileParcelable(
                            uri = uri.toString(),
                            name = name,
                            path = path,
                            size = size,
                            duration = duration,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
            
            Logger.d(TAG, "Search results for '$query': ${videos.size} videos")
            videos
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to search videos: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 从缓存获取视频列表
     * @param folderPath 文件夹路径
     * @return 缓存的视频列表
     */
    suspend fun getCachedVideos(folderPath: String): List<VideoFileParcelable> = withContext(Dispatchers.IO) {
        try {
            val cachedVideos = videoCacheDao.getVideosByFolder(folderPath)
            val result = cachedVideos.map { entity ->
                VideoFileParcelable(
                    uri = entity.uri,
                    name = entity.name,
                    path = entity.path,
                    size = entity.size,
                    duration = entity.duration,
                    dateAdded = entity.dateAdded
                )
            }
            Logger.d(TAG, "Loaded ${result.size} cached videos from folder: $folderPath")
            result
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get cached videos: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 清除视频缓存（别名方法）
     */
    suspend fun clearVideoCache() = clearAllCache()
    
    /**
     * 隐藏文件夹（添加.nomedia文件）
     * @param folderPath 文件夹路径
     */
    suspend fun hideFolder(folderPath: String) = withContext(Dispatchers.IO) {
        try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                val nomediaFile = File(folder, ".nomedia")
                if (!nomediaFile.exists()) {
                    nomediaFile.createNewFile()
                    Logger.d(TAG, "Hidden folder: $folderPath")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hide folder: ${e.message}", e)
        }
    }
    
    /**
     * 显示文件夹（删除.nomedia文件）
     * @param folderPath 文件夹路径
     */
    suspend fun showFolder(folderPath: String) = withContext(Dispatchers.IO) {
        try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                val nomediaFile = File(folder, ".nomedia")
                if (nomediaFile.exists()) {
                    nomediaFile.delete()
                    Logger.d(TAG, "Shown folder: $folderPath")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show folder: ${e.message}", e)
        }
    }
    
    /**
     * 从URI获取文件路径
     */
    fun getPathFromUri(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    cursor.getString(dataColumn)
                } else null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get path from URI: ${e.message}", e)
            null
        }
    }

    /**
     * 从 MediaStore 扫描所有视频文件（快速路径）
     */
    private fun scanFromMediaStore(
        folderMap: MutableMap<String, MutableList<com.fam4k007.videoplayer.VideoFile>>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                val name = cursor.getString(nameColumn)
                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)
                val dateAdded = cursor.getLong(dateColumn)

                val file = File(path)
                if (!file.exists()) continue
                val folderPath = file.parent ?: continue

                val uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()

                val videoFile = com.fam4k007.videoplayer.VideoFile(
                    uri = uri,
                    name = name,
                    path = path,
                    size = size,
                    duration = duration,
                    dateAdded = dateAdded
                )

                folderMap.getOrPut(folderPath) { mutableListOf() }.add(videoFile)
            }
        }
    }

    /**
     * 扫描单个文件夹中的视频文件（不递归子目录，仅扫描直接子文件）
     */
    /**
     * 从存储根目录直接用 File API 扫描所有视频文件
     * 不走 MediaStore，用于需要包含 .nomedia/隐藏文件夹的场景
     */
    private fun scanFromStorageRoots(
        folderMap: MutableMap<String, MutableList<com.fam4k007.videoplayer.VideoFile>>,
        context: android.content.Context,
        prefs: com.fam4k007.videoplayer.preferences.PreferencesManager
    ) {
        val knownPaths = mutableSetOf<String>()
        val visitedDirs = mutableSetOf<String>()  // 防止符号链接循环
        val roots = mutableListOf<File>()

        // 主存储
        val primaryRoot = android.os.Environment.getExternalStorageDirectory()
        if (primaryRoot.exists() && primaryRoot.isDirectory) {
            roots.add(primaryRoot)
        }

        // 扫描根目录下的一级子目录（Android/data、Android/media 不扫）
        for (root in roots) {
            root.listFiles()?.forEach { subDir ->
                if (!subDir.isDirectory || !subDir.canRead()) return@forEach
                val name = subDir.name
                // 跳过系统关键目录
                if (name.equals("Android", ignoreCase = true)) return@forEach
                if (name.equals("obb", ignoreCase = true)) return@forEach
                scanDirectoryRecursive(subDir, knownPaths, visitedDirs, folderMap, context, prefs)
            }
        }

        Logger.d(TAG, "File API 扫描完成，发现 ${folderMap.size} 个文件夹")
    }

    /**
     * 递归扫描目录（visitedDirs 防止符号链接循环）
     */
    private fun scanDirectoryRecursive(
        dir: File,
        knownPaths: MutableSet<String>,
        visitedDirs: MutableSet<String>,
        folderMap: MutableMap<String, MutableList<com.fam4k007.videoplayer.VideoFile>>,
        context: android.content.Context,
        prefs: com.fam4k007.videoplayer.preferences.PreferencesManager
    ) {
        if (knownPaths.size >= MAX_SCAN_FILES) return
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return

        // 防止符号链接导致的无限循环
        val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { return }
        if (!visitedDirs.add(canonicalPath)) return  // 已访问过，跳过

        if (ScanFilter.shouldSkipFolder(context, dir.absolutePath)) return

        val entries = dir.listFiles() ?: return

        // 先扫当前目录的视频文件
        for (entry in entries) {
            if (!entry.isFile || !entry.exists()) continue
            val path = entry.absolutePath
            if (path in knownPaths) continue
            if (knownPaths.size >= MAX_SCAN_FILES) return
            if (ScanFilter.shouldSkipFile(context, path)) continue

            val extension = entry.extension.lowercase()
            if (extension in com.fam4k007.videoplayer.AppConstants.Files.SUPPORTED_VIDEO_EXTENSIONS) {
                val folderPath = path.substringBeforeLast("/")
                val msDuration = com.fam4k007.videoplayer.utils.ScanFilter.queryDuration(context, path)
                val videoFile = com.fam4k007.videoplayer.VideoFile(
                    uri = android.net.Uri.fromFile(entry).toString(),
                    name = entry.name,
                    path = path,
                    size = entry.length(),
                    duration = msDuration,
                    dateAdded = entry.lastModified() / 1000
                )
                folderMap.getOrPut(folderPath) { mutableListOf() }.add(videoFile)
                knownPaths.add(path)
            }
        }

        // 递归子目录（不限深度，用 visitedDirs 防循环）
        for (entry in entries) {
            if (!entry.isDirectory || !entry.canRead()) continue
            if (entry.name.startsWith(".") && !prefs.isScanHiddenFoldersEnabled()) continue
            scanDirectoryRecursive(entry, knownPaths, visitedDirs, folderMap, context, prefs)
        }
    }

    private fun scanSingleFolder(
        dir: File,
        knownPaths: MutableSet<String>,
        folderMap: MutableMap<String, MutableList<com.fam4k007.videoplayer.VideoFile>>,
        context: android.content.Context
    ) {
        try {
            if (knownPaths.size >= MAX_SUPPLEMENTARY_FILES) return
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return
            if (ScanFilter.shouldSkipFolder(context, dir.absolutePath)) return

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (!file.isFile || !file.exists()) continue
                val path = file.absolutePath
                if (path in knownPaths) continue
                if (knownPaths.size >= MAX_SUPPLEMENTARY_FILES) return
                if (ScanFilter.shouldSkipFile(context, path)) continue

                val extension = file.extension.lowercase()
                if (extension in com.fam4k007.videoplayer.AppConstants.Files.SUPPORTED_VIDEO_EXTENSIONS) {
                    val folderPath = path.substringBeforeLast("/")
                    val msDuration = com.fam4k007.videoplayer.utils.ScanFilter.queryDuration(context, path)
                    val videoFile = com.fam4k007.videoplayer.VideoFile(
                        uri = android.net.Uri.fromFile(file).toString(),
                        name = file.name,
                        path = path,
                        size = file.length(),
                        duration = msDuration,
                        dateAdded = file.lastModified() / 1000
                    )
                    folderMap.getOrPut(folderPath) { mutableListOf() }.add(videoFile)
                    knownPaths.add(path)
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "扫描文件夹失败: ${dir.absolutePath}", e)
        }
    }

    // ==================== 文件夹列表缓存 ====================

    /**
     * 将扫描结果缓存到 SharedPreferences（JSON 格式）
     * 下次打开 App 时先显示缓存数据，再后台刷新
     */
    suspend fun saveFolderCache(folders: List<VideoFolder>) = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesManager.getInstance(context)
            val includeNoMedia = prefs.getIncludeNoMediaFolders()
            val includeHidden = prefs.isScanHiddenFoldersEnabled()
            val tag = "nomedia=$includeNoMedia,hidden=$includeHidden"

            val jsonArray = JSONArray()
            for (folder in folders) {
                val obj = JSONObject()
                obj.put("path", folder.folderPath)
                obj.put("name", folder.folderName)
                obj.put("count", folder.videoCount)
                jsonArray.put(obj)
            }
            prefs.sharedPreferences.edit()
                .putString(AppConstants.Preferences.FOLDER_CACHE, jsonArray.toString())
                .putString(AppConstants.Preferences.FOLDER_CACHE_TAG, tag)
                .putLong(AppConstants.Preferences.FOLDER_CACHE_TIME, System.currentTimeMillis())
                .apply()
            Logger.d(TAG, "已缓存 ${folders.size} 个文件夹 (tag=$tag)")
        } catch (e: Exception) {
            Logger.w(TAG, "保存文件夹缓存失败", e)
        }
    }

    /**
     * 从缓存加载文件夹列表
     * @return 缓存的文件夹列表，没有缓存或选项不匹配则返回 null
     */
    suspend fun loadFolderCache(): List<VideoFolder>? = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesManager.getInstance(context)
            val json = prefs.sharedPreferences.getString(AppConstants.Preferences.FOLDER_CACHE, null)
            if (json.isNullOrBlank()) return@withContext null

            // 检查缓存选项标签是否与当前设置匹配
            val includeNoMedia = prefs.getIncludeNoMediaFolders()
            val includeHidden = prefs.isScanHiddenFoldersEnabled()
            val currentTag = "nomedia=$includeNoMedia,hidden=$includeHidden"
            val cachedTag = prefs.sharedPreferences.getString(AppConstants.Preferences.FOLDER_CACHE_TAG, null)
            if (cachedTag != currentTag) {
                Logger.d(TAG, "缓存标签不匹配: 缓存=$cachedTag, 当前=$currentTag, 忽略缓存")
                return@withContext null
            }

            val cacheTime = prefs.sharedPreferences.getLong(AppConstants.Preferences.FOLDER_CACHE_TIME, 0)
            val now = System.currentTimeMillis()
            // 缓存超过 1 小时视为过期，但依然返回数据以便秒开（后台会刷新）
            val isExpired = (now - cacheTime) > 3_600_000

            val jsonArray = JSONArray(json)
            val folders = mutableListOf<VideoFolder>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                folders.add(
                    VideoFolder(
                        folderPath = obj.getString("path"),
                        folderName = obj.getString("name"),
                        videoCount = obj.getInt("count"),
                        videos = emptyList()
                    )
                )
            }
            Logger.d(TAG, "从缓存加载了 ${folders.size} 个文件夹 (过期=$isExpired)")
            folders
        } catch (e: Exception) {
            Logger.w(TAG, "加载文件夹缓存失败", e)
            null
        }
    }

    /**
     * 清除文件夹缓存
     */
    suspend fun clearFolderCache() = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager.getInstance(context)
        prefs.sharedPreferences.edit()
            .remove(AppConstants.Preferences.FOLDER_CACHE)
            .remove(AppConstants.Preferences.FOLDER_CACHE_TIME)
            .apply()
    }
}

/**
 * 视频排序方式
 */
enum class VideoSortOrder {
    NAME_ASC,      // 名称升序（自然排序）
    NAME_DESC,     // 名称降序
    DATE_ASC,      // 日期升序（旧→新）
    DATE_DESC      // 日期降序（新→旧）
}
