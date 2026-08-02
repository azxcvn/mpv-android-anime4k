package com.fam4k007.videoplayer.download

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class BilibiliDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = BilibiliDownloadManager(application)
    val downloadItems: StateFlow<List<DownloadItem>> = DownloadTaskStore.downloadItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // SharedPreferences用于持久化存储路径
    private val prefs = application.getSharedPreferences("bilibili_download", android.content.Context.MODE_PRIVATE)
    
    // 存储路径（无默认值，用户必须手动设置）
    private val _downloadPath = MutableStateFlow(
        prefs.getString("download_path", "") ?: ""
    )
    val downloadPath: StateFlow<String> = _downloadPath
    
    private val _downloadPathDisplay = MutableStateFlow(
        prefs.getString("download_path_display", "") ?: ""
    )
    val downloadPathDisplay: StateFlow<String> = _downloadPathDisplay

    // 解析相关状态
    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError

    private val _parseResult = MutableStateFlow<MediaParseResult?>(null)
    val parseResult: StateFlow<MediaParseResult?> = _parseResult

    private val _episodeList = MutableStateFlow<List<EpisodeInfo>>(emptyList())
    val episodeList: StateFlow<List<EpisodeInfo>> = _episodeList

    private val _selectedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val selectedEpisodes: StateFlow<Set<String>> = _selectedEpisodes

    private val _isEpisodeListExpanded = MutableStateFlow(true)
    val isEpisodeListExpanded: StateFlow<Boolean> = _isEpisodeListExpanded

    private val TAG = "BilibiliDownloadVM"

    init {
        // 注册恢复下载回调，供 DownloadManagerViewModel 使用
        DownloadTaskStore.onResumeDownload = { item ->
            resumeDownload(item)
        }
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: 从数据库加载下载项
            _isLoading.value = false
        }
    }

    // 获取下载目录
    private fun getDownloadDir(): File {
        val path = _downloadPath.value
        if (path.isEmpty()) {
            throw IllegalStateException("Download path not set")
        }
        // 如果是content URI，使用默认路径
        val dir = if (path.startsWith("content://")) {
            File(getApplication<Application>().getExternalFilesDir(null), "downloads")
        } else {
            File(path)
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // 设置存储路径（支持DocumentTree URI）
    fun setDownloadPath(uriString: String, displayName: String = "") {
        _downloadPath.value = uriString
        _downloadPathDisplay.value = displayName.ifEmpty { uriString.substringAfterLast('/') }
        
        // 持久化保存
        prefs.edit().apply {
            putString("download_path", uriString)
            putString("download_path_display", _downloadPathDisplay.value)
            apply()
        }
        
        Log.d(TAG, "存储路径已更改并保存: $uriString, 显示: ${_downloadPathDisplay.value}")
    }

    // 创建下载文件（支持普通路径和content URI）
    private fun createDownloadFile(fileName: String): File {
        val path = _downloadPath.value
        return if (path.startsWith("content://")) {
            // 如果是content URI，需要使用DocumentFile API
            // 这里我们将文件先下载到临时目录，完成后再移动到用户选择的位置
            val tempDir = File(getApplication<Application>().cacheDir, "download_temp")
            tempDir.mkdirs()
            File(tempDir, fileName)
        } else {
            // 普通文件路径
            val dir = File(path)
            dir.mkdirs()
            File(dir, fileName)
        }
    }
    
    // 将临时文件移动到最终目录（处理DocumentFile）
    private fun moveToFinalLocation(tempFile: File, fileName: String): Boolean {
        val path = _downloadPath.value
        return try {
            if (path.startsWith("content://")) {
                // 使用DocumentFile API
                val uri = android.net.Uri.parse(path)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    getApplication(), uri
                )
                
                if (docFile != null && docFile.exists()) {
                    val mimeType = when {
                        fileName.endsWith(".mp4") -> "video/mp4"
                        fileName.endsWith(".m4s") -> "video/mp4"
                        else -> "application/octet-stream"
                    }
                    
                    val newFile = docFile.createFile(mimeType, fileName)
                    if (newFile != null) {
                        getApplication<android.app.Application>().contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                        Log.d(TAG, "文件已移动到DocumentTree: ${newFile.uri}")
                        true
                    } else {
                        Log.e(TAG, "无法在DocumentTree中创建文件")
                        false
                    }
                } else {
                    Log.e(TAG, "DocumentFile不存在")
                    false
                }
            } else {
                // 普通路径，文件已经在正确位置
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "移动文件失败", e)
            false
        }
    }

    // 选择存储路径（已废弃，使用UI层的文件选择器）
    @Deprecated("使用UI层的rememberLauncherForActivityResult")
    fun selectDownloadPath() {
        // 该方法已由UI层的文件选择器替代
    }

    // 清除已完成的下载
    fun clearCompletedDownloads() {
        DownloadTaskStore.clearCompleted(deleteFiles = false)
        Log.d(TAG, "已清除完成的下载记录")
    }

    // 清除所有下载（可选是否删除文件）
    fun clearAllDownloads(deleteFiles: Boolean = false) {
        DownloadTaskStore.clearAll(deleteFiles = deleteFiles)
        Log.d(TAG, "已清除所有下载记录, 删除文件=$deleteFiles")
    }

    // 删除单个下载任务
    fun deleteDownload(item: DownloadItem, deleteFile: Boolean = false) {
        // 先取消任务
        DownloadTaskStore.downloadJobs[item.id]?.cancel()
        DownloadTaskStore.downloadJobs.remove(item.id)
        // 删除本地文件
        if (deleteFile && item.filePath != null) {
            try {
                val file = java.io.File(item.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "删除文件失败", e)
            }
        }
        DownloadTaskStore.removeItem(item.id)
        Log.d(TAG, "已删除下载: ${item.title}, 删除文件=$deleteFile")
    }

    /**
     * 解析视频URL
     */
    fun parseVideoUrl(inputUrl: String) {
        if (inputUrl.isBlank()) {
            _parseError.value = "Please enter video link"
            return
        }

        // 提取URL（支持带文本的链接）
        val extractedUrl = inputUrl.trim().let { text ->
            // 尝试匹配 b23.tv 短链
            val shortUrlRegex = """https?://b23\.tv/\w+""".toRegex()
            val shortUrlMatch = shortUrlRegex.find(text)
            if (shortUrlMatch != null) {
                return@let shortUrlMatch.value
            }
            
            // 尝试匹配 bilibili.com 链接
            val biliUrlRegex = """https?://(?:www\.)?bilibili\.com/[^\s\u3011\]\)]+""".toRegex()
            val biliUrlMatch = biliUrlRegex.find(text)
            if (biliUrlMatch != null) {
                return@let biliUrlMatch.value
            }
            
            // 如果没有匹配到URL，返回原文本
            text
        }

        _isParsing.value = true
        _parseError.value = null
        _parseResult.value = null
        _episodeList.value = emptyList()
        _selectedEpisodes.value = emptySet()

        viewModelScope.launch {
            try {
                val result = parseMediaUrlSync(extractedUrl)
                _parseResult.value = result

                // 如果是番剧，获取所有集数
                if (result.type == MediaType.Bangumi) {
                    val queryId = result.seasonId ?: result.epId
                    if (queryId != null) {
                        Log.d(TAG, "获取番剧集数，ID: $queryId")
                        val episodesResult = getBangumiEpisodesSync(queryId)
                        if (episodesResult.isSuccess) {
                            _episodeList.value = episodesResult.getOrNull() ?: emptyList()
                            Log.d(TAG, "获取到${_episodeList.value.size}集")
                        } else {
                            _parseError.value = "Failed to get episodes: ${episodesResult.exceptionOrNull()?.message}"
                            Log.e(TAG, "获取集数失败", episodesResult.exceptionOrNull())
                        }
                    } else {
                        _parseError.value = "Anime ID parsing failed"
                    }
                }
            } catch (e: Exception) {
                _parseError.value = e.message ?: "Parsing failed"
                Log.e(TAG, "解析失败", e)
            } finally {
                _isParsing.value = false
            }
        }
    }

    /**
     * 切换剧集选择
     */
    fun toggleEpisodeSelection(episodeId: String) {
        _selectedEpisodes.value = if (_selectedEpisodes.value.contains(episodeId)) {
            _selectedEpisodes.value - episodeId
        } else {
            _selectedEpisodes.value + episodeId
        }
    }

    /**
     * 全选/取消全选剧集
     */
    fun toggleAllEpisodes() {
        _selectedEpisodes.value = if (_selectedEpisodes.value.size == _episodeList.value.size) {
            emptySet()
        } else {
            _episodeList.value.map { it.episodeId }.toSet()
        }
    }

    /**
     * 切换剧集列表展开状态
     */
    fun toggleEpisodeListExpanded() {
        _isEpisodeListExpanded.value = !_isEpisodeListExpanded.value
    }

    /**
     * 开始下载选中的剧集或视频
     */
    fun startSelectedDownloads() {
        val result = _parseResult.value ?: return

        // 检查是否已设置下载路径
        if (_downloadPath.value.isEmpty()) {
            _parseError.value = "Please set save path first"
            return
        }

        if (_episodeList.value.isNotEmpty() && _selectedEpisodes.value.isNotEmpty()) {
            // 下载选中的集数
            _episodeList.value.filter { _selectedEpisodes.value.contains(it.episodeId) }
                .forEach { episode ->
                    addDownloadByEpisode(episode, result.seasonId ?: "")
                }
            // 清空选择
            _selectedEpisodes.value = emptySet()
        } else if (_episodeList.value.isEmpty()) {
            // 普通视频，直接下载
            addDownloadByMediaParse(result)
        }
    }

    /**
     * 清除解析结果
     */
    fun clearParseResult() {
        _parseResult.value = null
        _episodeList.value = emptyList()
        _selectedEpisodes.value = emptySet()
        _parseError.value = null
    }

    fun addDownload(aid: String, cid: String, title: String) {
        val newItem = DownloadItem(
            id = DownloadTaskStore.nextId(),
            title = title,
            url = "bilibili://$aid/$cid",
            status = "pending"
        )
        DownloadTaskStore.addItem(newItem)
        startDownload(newItem, aid, cid)
    }

    private fun startDownload(item: DownloadItem, aid: String, cid: String) {
        viewModelScope.launch {
            DownloadTaskStore.updateItemStatus(item.id, "downloading")

            try {
                val videoInfoResult = downloadManager.getMediaInfo(aid, cid)
                if (videoInfoResult.isSuccess) {
                    val videoInfo = videoInfoResult.getOrThrow()
                    val downloadDir = File(_downloadPath.value)
                    downloadDir.mkdirs()

                    // 下载视频片段
                    for (fragment in videoInfo.fragments) {
                        val fileName = "${item.title}_${fragment.type}_${System.currentTimeMillis()}.mp4"
                        val file = File(downloadDir, fileName)

                        val downloadResult = downloadManager.downloadFile(fragment.url, file, item.id) { bytesRead, totalBytes ->
                            val progress = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt() else 0
                            DownloadTaskStore.updateItemProgress(item.id, progress)
                        }

                        if (downloadResult.isFailure) {
                            DownloadTaskStore.updateItemStatus(item.id, "failed", downloadResult.exceptionOrNull()?.message)
                            return@launch
                        }
                    }

                    DownloadTaskStore.updateItemStatus(item.id, "completed")
                } else {
                    DownloadTaskStore.updateItemStatus(item.id, "failed", videoInfoResult.exceptionOrNull()?.message)
                }
            } catch (e: CancellationException) {
                // 暂停/取消导致的取消异常，不设为失败
                throw e
            } catch (e: Exception) {
                DownloadTaskStore.updateItemStatus(item.id, "failed", e.message)
            }
        }
    }

    // 新增：解析链接，返回可选集数（番剧）或视频信息
    suspend fun parseMediaUrlSync(url: String): MediaParseResult {
        return downloadManager.parseMediaUrl(url)
    }

    // 新增：获取番剧所有集数列表
    suspend fun getBangumiEpisodesSync(id: String): Result<List<EpisodeInfo>> {
        return downloadManager.getBangumiEpisodes(id)
    }

    // 新增：下载选定集数（支持视频/番剧）
    fun addDownloadByMediaParse(parse: MediaParseResult) {
        val newItem = DownloadItem(
            id = DownloadTaskStore.nextId(),
            title = parse.title,
            url = "bilibili://${parse.aid}/${parse.cid}",
            status = "pending"
        )
        DownloadTaskStore.addItem(newItem)
        startDownloadByMediaParse(newItem, parse)
    }

    private fun startDownloadByMediaParse(item: DownloadItem, parse: MediaParseResult) {
        val job = viewModelScope.launch {
            // 并发限制：如果已有下载任务在进行，设置为等待中
            if (!DownloadTaskStore.tryAcquireDownloadSlot()) {
                DownloadTaskStore.updateItemStatus(item.id, "pending")
                // 轮询等待直到获得下载许可
                while (!DownloadTaskStore.tryAcquireDownloadSlot()) {
                    delay(1000)
                    ensureActive()
                }
            }
            DownloadTaskStore.updateItemStatus(item.id, "downloading")
            try {
                val result = downloadManager.getMediaInfo(
                    id = parse.aid,
                    cid = parse.cid,
                    isBangumi = (parse.type == MediaType.Bangumi),
                    epId = parse.epId,
                    seasonId = parse.seasonId
                )
                if (result.isSuccess) {
                    val videoInfo = result.getOrThrow()
                    val downloadDir = getDownloadDir()
                    
                    val downloadedFiles = mutableListOf<File>()
                    val fragmentCount = videoInfo.fragments.size
                    
                    for ((index, fragment) in videoInfo.fragments.withIndex()) {
                        val fileName = "${item.title}_${fragment.type}.m4s"
                        val file = createDownloadFile(fileName)
                        
                        Log.d(TAG, "开始下载片段 ${index + 1}/$fragmentCount: ${fragment.type}")
                        
                        val downloadResult = downloadManager.downloadFile(fragment.url, file, item.id) { bytesRead, totalBytes ->
                            val fragmentProgress = if (totalBytes > 0) {
                                (bytesRead.toFloat() / totalBytes * 100).toInt()
                            } else 0
                            
                            val completedWeight = index * 100
                            val overallProgress = (completedWeight + fragmentProgress) / fragmentCount
                            
                            DownloadTaskStore.updateItemProgress(item.id, overallProgress)
                        }
                        
                        if (downloadResult.isFailure) {
                            DownloadTaskStore.updateItemStatus(item.id, "failed", downloadResult.exceptionOrNull()?.message)
                            downloadedFiles.forEach { it.delete() }
                            return@launch
                        }
                        
                        downloadedFiles.add(file)
                        Log.d(TAG, "片段下载完成: ${fragment.type}, 文件大小: ${file.length() / 1024 / 1024}MB")
                    }
                    
                    // 合并音视频
                    if (downloadedFiles.size == 2) {
                        DownloadTaskStore.updateItemStatus(item.id, "merging")
                        val videoFile = downloadedFiles.find { it.name.contains("video") }
                        val audioFile = downloadedFiles.find { it.name.contains("audio") }
                        
                        if (videoFile != null && audioFile != null) {
                            val outputFileName = "${item.title}.mp4"
                            val outputFile = createDownloadFile(outputFileName)
                            
                            // 在IO线程执行合并，避免UI卡顿
                            val mergeResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                mergeVideoAudio(videoFile, audioFile, outputFile)
                            }
                            
                            if (mergeResult) {
                                videoFile.delete()
                                audioFile.delete()
                                
                                // 如果是DocumentTree，移动到最终位置
                                if (_downloadPath.value.startsWith("content://")) {
                                    if (moveToFinalLocation(outputFile, outputFileName)) {
                                        DownloadTaskStore.updateItemStatus(item.id, "completed")
                                    } else {
                                        DownloadTaskStore.updateItemStatus(item.id, "failed", "Unable to save to the selected folder")
                                    }
                                } else {
                                    DownloadTaskStore.updateItemStatus(item.id, "completed")
                                }
                            } else {
                                DownloadTaskStore.updateItemStatus(item.id, "failed", "Audio/video merge failed")
                            }
                        }
                    } else {
                        DownloadTaskStore.updateItemStatus(item.id, "completed")
                    }
                } else {
                    DownloadTaskStore.updateItemStatus(item.id, "failed", result.exceptionOrNull()?.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "下载异常", e)
                DownloadTaskStore.updateItemStatus(item.id, "failed", e.message)
            } finally {
                DownloadTaskStore.downloadJobs.remove(item.id)
                // 暂停时不释放下载槽，防止下一个任务自动开始
                val currentStatus = DownloadTaskStore.getItem(item.id)?.status
                if (currentStatus != "paused") {
                    DownloadTaskStore.releaseDownloadSlot()
                }
            }
        }
        
        DownloadTaskStore.downloadJobs[item.id] = job
    }

    // 新增：根据EpisodeInfo下载番剧集数
    fun addDownloadByEpisode(episode: EpisodeInfo, seasonId: String) {
        val newItem = DownloadItem(
            id = DownloadTaskStore.nextId(),
            title = episode.longTitle.ifEmpty { episode.title },
            url = "bilibili://${episode.aid}/${episode.cid}",
            status = "pending",
            mediaType = MediaType.Bangumi
        )
        DownloadTaskStore.addItem(newItem)
        
        val job = viewModelScope.launch {
            // 并发限制
            if (!DownloadTaskStore.tryAcquireDownloadSlot()) {
                DownloadTaskStore.updateItemStatus(newItem.id, "pending")
                while (!DownloadTaskStore.tryAcquireDownloadSlot()) {
                    delay(1000)
                    ensureActive()
                }
            }
            DownloadTaskStore.updateItemStatus(newItem.id, "downloading")
            try {
                val result = downloadManager.getMediaInfo(
                    id = episode.aid,
                    cid = episode.cid,
                    isBangumi = true,
                    epId = episode.episodeId,
                    seasonId = seasonId
                )
                if (result.isSuccess) {
                    val videoInfo = result.getOrThrow()
                    val downloadDir = getDownloadDir()
                    
                    val downloadedFiles = mutableListOf<File>()
                    val fragmentCount = videoInfo.fragments.size
                    
                    // 下载所有片段
                    for ((index, fragment) in videoInfo.fragments.withIndex()) {
                        val fileName = "${newItem.title}_${fragment.type}.m4s"
                        val file = createDownloadFile(fileName)
                        
                        Log.d(TAG, "开始下载片段 ${index + 1}/$fragmentCount: ${fragment.type}, URL: ${fragment.url}")
                        
                        val downloadResult = downloadManager.downloadFile(fragment.url, file, newItem.id) { bytesRead, totalBytes ->
                            // 计算当前片段的进度
                            val fragmentProgress = if (totalBytes > 0) {
                                (bytesRead.toFloat() / totalBytes * 100).toInt()
                            } else 0
                            
                            // 计算总体进度：已完成片段 + 当前片段进度
                            val completedWeight = index * 100
                            val overallProgress = (completedWeight + fragmentProgress) / fragmentCount
                            
                            DownloadTaskStore.updateItemProgress(newItem.id, overallProgress)
                        }
                        
                        if (downloadResult.isFailure) {
                            Log.e(TAG, "下载片段失败: ${fragment.type}", downloadResult.exceptionOrNull())
                            DownloadTaskStore.updateItemStatus(newItem.id, "failed", downloadResult.exceptionOrNull()?.message)
                            downloadedFiles.forEach { it.delete() } // 清理已下载的文件
                            return@launch
                        }
                        
                        downloadedFiles.add(file)
                        Log.d(TAG, "片段下载完成: ${fragment.type}, 文件大小: ${file.length() / 1024 / 1024}MB")
                    }
                    
                    // 合并音视频
                    if (downloadedFiles.size == 2) {
                        DownloadTaskStore.updateItemStatus(newItem.id, "merging")
                        Log.d(TAG, "开始合并音视频")
                        
                        val videoFile = downloadedFiles.find { it.name.contains("video") }
                        val audioFile = downloadedFiles.find { it.name.contains("audio") }
                        
                        if (videoFile != null && audioFile != null) {
                            val outputFileName = "${newItem.title}.mp4"
                            val outputFile = createDownloadFile(outputFileName)
                            
                            // 在IO线程执行合并，避免UI卡顿
                            val mergeResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                mergeVideoAudio(videoFile, audioFile, outputFile)
                            }
                            
                            if (mergeResult) {
                                Log.d(TAG, "合并成功")
                                videoFile.delete()
                                audioFile.delete()
                                
                                // 如果是DocumentTree，移动到最终位置
                                if (_downloadPath.value.startsWith("content://")) {
                                    if (moveToFinalLocation(outputFile, outputFileName)) {
                                        Log.d(TAG, "文件已移动到用户选择的文件夹")
                                        DownloadTaskStore.updateItemStatus(newItem.id, "completed")
                                    } else {
                                        Log.e(TAG, "移动文件失败")
                                        DownloadTaskStore.updateItemStatus(newItem.id, "failed", "Unable to save to the selected folder")
                                    }
                                } else {
                                    DownloadTaskStore.updateItemStatus(newItem.id, "completed")
                                }
                            } else {
                                Log.e(TAG, "合并失败")
                                DownloadTaskStore.updateItemStatus(newItem.id, "failed", "Audio/video merge failed")
                            }
                        } else {
                            Log.e(TAG, "找不到音视频文件")
                            DownloadTaskStore.updateItemStatus(newItem.id, "failed", "Audio/video files not found")
                        }
                    } else {
                        Log.d(TAG, "只有一个片段，无需合并")
                        DownloadTaskStore.updateItemStatus(newItem.id, "completed")
                    }
                    
                } else {
                    DownloadTaskStore.updateItemStatus(newItem.id, "failed", result.exceptionOrNull()?.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "下载异常", e)
                DownloadTaskStore.updateItemStatus(newItem.id, "failed", e.message)
            } finally {
                DownloadTaskStore.downloadJobs.remove(newItem.id)
                val currentStatus = DownloadTaskStore.getItem(newItem.id)?.status
                if (currentStatus != "paused") {
                    DownloadTaskStore.releaseDownloadSlot()
                }
            }
        }
        
        DownloadTaskStore.downloadJobs[newItem.id] = job
    }
    
    // 合并音视频（使用MediaMuxer）
    private fun mergeVideoAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        return try {
            Log.d(TAG, "开始合并: video=${videoFile.length()}bytes, audio=${audioFile.length()}bytes")
            
            // 使用Android MediaMuxer合并
            val muxer = android.media.MediaMuxer(
                outputFile.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            
            // 提取video track
            val videoExtractor = android.media.MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: android.media.MediaFormat? = null
            
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    videoExtractor.selectTrack(i)
                    Log.d(TAG, "找到视频轨道: $mime")
                    break
                }
            }
            
            // 提取audio track
            val audioExtractor = android.media.MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    audioExtractor.selectTrack(i)
                    Log.d(TAG, "找到音频轨道: $mime")
                    break
                }
            }
            
            if (videoFormat == null || audioFormat == null) {
                Log.e(TAG, "无法找到音视频轨道")
                videoExtractor.release()
                audioExtractor.release()
                return false
            }
            
            // 添加轨道到muxer
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            
            // 复制视频数据
            val videoBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val videoBufferInfo = android.media.MediaCodec.BufferInfo()
            
            while (true) {
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                if (videoBufferInfo.size < 0) break
                
                videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                videoBufferInfo.flags = videoExtractor.sampleFlags
                
                muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                videoExtractor.advance()
            }
            Log.d(TAG, "视频轨道复制完成")
            
            // 复制音频数据
            val audioBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val audioBufferInfo = android.media.MediaCodec.BufferInfo()
            
            while (true) {
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
                if (audioBufferInfo.size < 0) break
                
                audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                audioBufferInfo.flags = audioExtractor.sampleFlags
                
                muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                audioExtractor.advance()
            }
            Log.d(TAG, "音频轨道复制完成")
            
            // 释放资源
            videoExtractor.release()
            audioExtractor.release()
            muxer.stop()
            muxer.release()
            
            Log.d(TAG, "合并完成，输出文件大小: ${outputFile.length() / 1024 / 1024}MB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "合并失败", e)
            false
        }
    }

    fun pauseDownload(item: DownloadItem) {
        DownloadTaskStore.pauseDownload(item.id)
        Log.d(TAG, "暂停下载: ${item.title}")
    }

    fun resumeDownload(item: DownloadItem) {
        Log.d(TAG, "恢复下载: ${item.title}")
        
        val downloadItem = DownloadTaskStore.getItem(item.id) ?: return
        
        if (downloadItem.url.startsWith("bilibili://")) {
            val parts = downloadItem.url.removePrefix("bilibili://").split("/")
            if (parts.size >= 2) {
                val aid = if (parts[0].startsWith("ep")) "" else parts[0]
                val cid = parts.getOrElse(1) { "" }
                
                val job = viewModelScope.launch {
                    // 恢复已暂停的任务：槽位已经被自己占着，不需要重新获取
                    DownloadTaskStore.updateItemStatus(item.id, "downloading")
                    try {
                        val videoInfoResult = downloadManager.getMediaInfo(aid, cid)
                        if (videoInfoResult.isSuccess) {
                            val videoInfo = videoInfoResult.getOrThrow()
                            val downloadDir = getDownloadDir()
                            
                            val downloadedFiles = mutableListOf<java.io.File>()
                            val fragmentCount = videoInfo.fragments.size
                            
                            for ((index, fragment) in videoInfo.fragments.withIndex()) {
                                val fileName = "${downloadItem.title}_${fragment.type}.m4s"
                                val file = createDownloadFile(fileName)
                                
                                val downloadResult = downloadManager.downloadFile(fragment.url, file, item.id) { bytesRead, totalBytes ->
                                    val fragmentProgress = if (totalBytes > 0) {
                                        (bytesRead.toFloat() / totalBytes * 100).toInt()
                                    } else 0
                                    val completedWeight = index * 100
                                    val overallProgress = (completedWeight + fragmentProgress) / fragmentCount
                                    DownloadTaskStore.updateItemProgress(item.id, overallProgress)
                                }
                                
                                if (downloadResult.isFailure) {
                                    DownloadTaskStore.updateItemStatus(item.id, "failed", downloadResult.exceptionOrNull()?.message)
                                    downloadedFiles.forEach { it.delete() }
                                    return@launch
                                }
                                downloadedFiles.add(file)
                            }
                            
                            if (downloadedFiles.size == 2) {
                                DownloadTaskStore.updateItemStatus(item.id, "merging")
                                val videoFile = downloadedFiles.find { it.name.contains("video") }
                                val audioFile = downloadedFiles.find { it.name.contains("audio") }
                                
                                if (videoFile != null && audioFile != null) {
                                    val outputFileName = "${downloadItem.title}.mp4"
                                    val outputFile = createDownloadFile(outputFileName)
                                    
                                    val mergeResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        mergeVideoAudio(videoFile, audioFile, outputFile)
                                    }
                                    
                                    if (mergeResult) {
                                        videoFile.delete()
                                        audioFile.delete()
                                        DownloadTaskStore.updateItemStatus(item.id, "completed")
                                    } else {
                                        DownloadTaskStore.updateItemStatus(item.id, "failed", "Audio/video merge failed")
                                    }
                                }
                            } else {
                                DownloadTaskStore.updateItemStatus(item.id, "completed")
                            }
                        } else {
                            DownloadTaskStore.updateItemStatus(item.id, "failed", videoInfoResult.exceptionOrNull()?.message)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DownloadTaskStore.updateItemStatus(item.id, "failed", e.message)
                    } finally {
                        DownloadTaskStore.downloadJobs.remove(item.id)
                        val currentStatus = DownloadTaskStore.getItem(item.id)?.status
                        if (currentStatus != "paused") {
                            DownloadTaskStore.releaseDownloadSlot()
                        }
                    }
                }
                DownloadTaskStore.downloadJobs[item.id] = job
            }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        DownloadTaskStore.downloadJobs[item.id]?.cancel()
        DownloadTaskStore.downloadJobs.remove(item.id)
        DownloadTaskStore.removeItem(item.id)
        Log.d(TAG, "取消下载: ${item.title}")
    }
}