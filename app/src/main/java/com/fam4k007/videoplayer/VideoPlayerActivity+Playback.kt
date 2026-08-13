package com.fam4k007.videoplayer

import android.net.Uri
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import `is`.xyz.mpv.MPVLib
import com.fam4k007.videoplayer.remote.RemotePlaybackHeaders
import com.fam4k007.videoplayer.remote.RemotePlaybackRequest
import com.fam4k007.videoplayer.remote.RemotePlaybackResolver
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.UriUtils.getFolderName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VideoPlayerActivity"
private val REMOTE_URI_SCHEMES = setOf("http", "https", "rtsp", "rtmp", "rtmps")

/**
 * 加载视频
 */
internal fun VideoPlayerActivity.loadVideo() {
    videoUri?.let { uri ->
        // 重置自动加载字幕标志（新视频开始播放）
        viewModel.setHasAutoLoadedSubtitle(false)

        val position = if (duration > 0 && duration < 30) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Short video detected, starting from 0")
            0.0
        } else {
            savedPosition
        }

        if (position > 5.0) {
            viewModel.showResumeToast()
        }

        // 对于在线视频,直接使用URI字符串;对于本地文件,使用URI对象
        if (isOnlineVideo) {
            val request = remotePlaybackRequest ?: buildLegacyRemotePlaybackRequest(uri)
            remotePlaybackRequest = request
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Loading online video with request: ${request.url}")
            loadResolvedRemoteVideo(request, position)
        } else {
            // 本地视频:使用URI对象
            playbackEngine?.loadVideo(uri, position)
            applyRememberedSpeed()
        }

        loadDanmakuForVideo(uri)

        // 只在position > 0时同步弹幕(延迟更长,等待在线视频加载)
        if (position > 0) {
            lifecycleScope.launch {
                // 在线视频需要更长延迟
                val delayTime = if (isOnlineVideo) 3000L else 800L
                delay(delayTime)
                danmakuManager.seekTo((position * 1000).toLong())
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Synced danmaku to position: $position seconds")
            }
        }

        // 使用协程延迟恢复字幕设置和Anime4K效果
        lifecycleScope.launch {
            delay(500)

            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Post-load coroutine started, isOnlineVideo=$isOnlineVideo")

            // 先尝试自动加载同名字幕
            if (!isOnlineVideo) {
                // 本地视频（包括 content://）
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Calling autoLoadSubtitleIfExists")
                autoLoadSubtitleIfExists(uri)
            } else if (intent.getBooleanExtra("is_webdav", false)) {
                // WebDAV 远程视频 — 通过 WebDAV 客户端查找同名字幕
                val accountId = intent.getStringExtra("webdav_account_id")
                val filePath = intent.getStringExtra("webdav_file_path")
                if (accountId != null && filePath != null) {
                    try {
                        val repository: com.fam4k007.videoplayer.repository.WebDavRepository =
                            org.koin.core.context.GlobalContext.get().get()
                        val account = repository.getAccountById(accountId)
                        if (account != null) {
                            val client = repository.createClient(account)
                            // 文件在根目录时 path 无 '/'，此时父目录为空字符串
                            val parentDir = filePath.substringBeforeLast('/', "")
                            autoLoadWebDavSubtitle(uri, parentDir, client)
                        }
                    } catch (e: Exception) {
                        com.fam4k007.videoplayer.utils.Logger.e(TAG, "Failed to auto-load WebDAV subtitle", e)
                    }
                }
            } else {
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Skipping subtitle auto-load for other online video")
            }

            // 再恢复用户的字幕偏好设置（会覆盖自动加载的）
            restoreSubtitlePreferences(uri)

            // 恢复均衡器设置
            restoreEqualizerSettings()

            // 如果记忆的Anime4K模式已启用，在视频加载后应用shader
            if (viewModel.anime4KEnabled.value && anime4KMode != com.fam4k007.videoplayer.domain.player.Anime4KManager.Mode.OFF) {
                delay(200) // 额外延迟确保MPV完全初始化
                applyAnime4K()
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Applied remembered Anime4K mode: $anime4KMode")
            }
        }
    }
}

/**
 * 应用记忆的播放倍速
 */
internal fun VideoPlayerActivity.applyRememberedSpeed() {
    if (preferencesManager.isRememberSpeedEnabled()) {
        val lastSpeed = preferencesManager.getLastPlaybackSpeed()
        if (lastSpeed != 1.0f) {
            viewModel.setSpeed(lastSpeed.toDouble())
            playbackEngine.setSpeed(lastSpeed.toDouble())
            danmakuManager.setSpeed(lastSpeed)
            Logger.d(TAG, "Restored remembered playback speed: ${lastSpeed}x")
        }
    }
}

internal fun VideoPlayerActivity.loadResolvedRemoteVideo(
    request: RemotePlaybackRequest,
    position: Double
) {
    remoteResolveJob?.cancel()
    val sequence = ++remoteResolveSequence
    remoteResolveJob = lifecycleScope.launch {
        val result = RemotePlaybackResolver.resolve(request)
        val debugSummary = RemotePlaybackResolver.buildDebugSummary(result)
        preferencesManager.setLastRemoteDebugSummary(debugSummary)
        Logger.d(TAG, "Remote debug summary:\n$debugSummary")
        if (sequence != remoteResolveSequence) {
            Logger.d(TAG, "Discarding stale remote resolve result for: ${request.url}")
            return@launch
        }

        // Bilibili 源：通过本地 OkHttp 代理播放，绕过 mpv 内置 mbedTLS
        val finalRequest: RemotePlaybackRequest
        if (request.source == RemotePlaybackRequest.Source.BILIBILI) {
            val headers = RemotePlaybackHeaders.enrich(request.headers, request.sourcePageUrl)
            val normalizedHeaders = RemotePlaybackHeaders.normalize(headers)
            val (localVideo, localAudio) = com.fam4k007.videoplayer.remote.StreamProxy.registerStreams(
                videoUrl = request.url,
                audioUrl = request.audioUrl,
                headers = normalizedHeaders
            )
            if (localVideo != null) {
                proxyAudioUrl = localAudio
                finalRequest = request.copy(url = localVideo)
                Logger.d(TAG, "Bilibili proxy: $localVideo audio=$localAudio")
            } else {
                Logger.w(TAG, "StreamProxy failed to start, falling back to direct playback")
                finalRequest = when (result) {
                    is RemotePlaybackResolver.ResolveResult.Success -> result.request
                    is RemotePlaybackResolver.ResolveResult.Failed -> result.request
                }
            }
        } else {
            finalRequest = when (result) {
                is RemotePlaybackResolver.ResolveResult.Success -> result.request
                is RemotePlaybackResolver.ResolveResult.Failed -> result.request
            }
        }

        when (result) {
            is RemotePlaybackResolver.ResolveResult.Success -> {
                remotePlaybackRequest = finalRequest
                videoUri = Uri.parse(finalRequest.url)
                Logger.d(
                    TAG,
                    "Remote request resolved via ${result.probeMethod}: ${request.url} -> ${finalRequest.url}"
                )
                playbackEngine.loadRemote(finalRequest, position)
            }
            is RemotePlaybackResolver.ResolveResult.Failed -> {
                remotePlaybackRequest = finalRequest
                Logger.w(TAG, "Remote resolve fallback: reason=${result.reason}, message=${result.message}", result.cause)
                if (request.source != RemotePlaybackRequest.Source.BILIBILI) {
                    val suggestion = RemotePlaybackResolver.buildFailureSuggestion(result.reason)
                    DialogUtils.showToastLong(
                        this@loadResolvedRemoteVideo,
                        "${result.message}, trying direct playback\n$suggestion"
                    )
                }
                playbackEngine.loadRemote(finalRequest, position)
            }
        }

        // DASH 音频轨：解析完成后添加（Bilibili 代理已在上面就绪）
        val audioToAdd = proxyAudioUrl ?: finalRequest.audioUrl
        if (!audioToAdd.isNullOrEmpty()) {
            mpvView.postDelayed({
                try {
                    MPVLib.command("audio-add", audioToAdd)
                    MPVLib.setPropertyString("vid", "auto")
                    Logger.d(TAG, "Added external audio: $audioToAdd")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add audio: ${e.message}")
                }
            }, 300)
        }

        applyRememberedSpeed()
    }
}

/**
 * 处理从列表传入的视频数据
 */
internal fun VideoPlayerActivity.handleVideoListIntent() {
    val lastPosition = intent.getLongExtra("lastPosition", -1L)
    if (lastPosition > 0) {
        viewModel.setLastPlaybackPosition(lastPosition)
    }
}

/**
 * 播放上一集（百分百复用 mpvEx 算法 - 委托给 Activity.playPrevious）
 */
internal fun VideoPlayerActivity.playPreviousVideo() {
    Logger.d(TAG, "playPreviousVideo called")
    playPrevious()
}

/**
 * 播放下一集（百分百复用 mpvEx 算法 - 委托给 Activity.playNext）
 */
internal fun VideoPlayerActivity.playNextVideo() {
    Logger.d(TAG, "playNextVideo called")
    playNext()
}

/**
 * 播放指定视频（百分百复用 mpvEx loadPlaylistItem 算法）
 */
internal fun VideoPlayerActivity.playVideo(uri: Uri) {
    videoUri = uri

    // 设置切换标志，防止级联切换
    isSwitchingVideo = true

    // 重置缩略图状态（新视频需要重新初始化）
    viewModel.resetThumbnailState()

    // 更新播放列表索引
    val newIndex = playlist.indexOfFirst { it.toString() == uri.toString() }
    if (newIndex >= 0) {
        playlistIndex = newIndex
        viewModel.syncPlaylistIndex(newIndex)
    }

    // 重置自动加载字幕标志（新视频开始播放）
    viewModel.setHasAutoLoadedSubtitle(false)

    // 更新在线视频标志
    val isOnline = isRemotePlaybackUri(uri)
    viewModel.setIsOnlineVideo(isOnline)
    remotePlaybackRequest = if (isOnline) {
        RemotePlaybackRequest(
            url = uri.toString(),
            title = resolveVideoTitle(uri),
            source = RemotePlaybackRequest.Source.UNKNOWN
        )
    } else {
        null
    }

    // 显示加载动画（仅在线视频）
    viewModel.setLoading(isOnlineVideo)

    val fileName = getFileNameFromUri(uri)
    viewModel.setVideoTitle(fileName)

    val position = preferencesManager.getPlaybackPosition(uri.toString())

    // 清除旧弹幕
    Logger.d(TAG, "Releasing old danmaku before playing new video")
    danmakuManager.release()

    if (isOnlineVideo) {
        loadResolvedRemoteVideo(remotePlaybackRequest!!, position)
    } else {
        playbackEngine?.loadVideo(uri, position)
        applyRememberedSpeed()
    }

    // 设置当前视频 URI 给文件选择器管理器
    filePickerManager.setCurrentVideoUri(uri)

    // 自动加载字幕和弹幕
    lifecycleScope.launch {
        delay(500)

        if (!isOnlineVideo) {
            Logger.d(TAG, "Auto-loading subtitle for next video")
            autoLoadSubtitleIfExists(uri)
        }

        restoreSubtitlePreferences(uri)

        Logger.d(TAG, "Loading danmaku for new video: $uri")
        loadDanmakuForVideo(uri)

        if (position > 0) {
            delay(300)
            danmakuManager.seekTo((position * 1000).toLong())
            Logger.d(TAG, "Synced danmaku to position: $position seconds")
        }
    }
}

/**
 * 从 URI 解析文件路径（用于 generatePlaylistFromFolder）
 */
internal fun VideoPlayerActivity.parsePathFromUri(uri: Uri): String? {
    return when (uri.scheme) {
        "file" -> uri.path
        "content" -> {
            try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.Video.Media.DATA),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                        it.getString(columnIndex)
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse path from content URI", e)
                null
            }
        }
        else -> null
    }
}

internal fun VideoPlayerActivity.buildLegacyRemotePlaybackRequest(uri: Uri): RemotePlaybackRequest {
    val headers = linkedMapOf<String, String>()
    intent.getStringExtra("cookies")
        ?.takeIf { it.isNotBlank() }
        ?.let { headers["Cookie"] = it }
    intent.getStringExtra("referer")
        ?.takeIf { it.isNotBlank() }
        ?.let { headers["Referer"] = it }
    intent.getStringExtra("user_agent")
        ?.takeIf { it.isNotBlank() }
        ?.let { headers["User-Agent"] = it }

    val urlString = uri.toString()
    if (urlString.contains("bilivideo.com") && headers["Referer"].isNullOrBlank()) {
        headers["Referer"] = "https://www.bilibili.com"
    }

    val source = when {
        intent.hasExtra("cookies") -> RemotePlaybackRequest.Source.BILIBILI
        intent.getBooleanExtra("is_webdav", false) -> RemotePlaybackRequest.Source.WEBDAV
        else -> RemotePlaybackRequest.Source.UNKNOWN
    }

    return RemotePlaybackRequest(
        url = urlString,
        title = resolveVideoTitle(uri),
        sourcePageUrl = RemotePlaybackHeaders.deriveSourcePageUrl(headers),
        headers = RemotePlaybackHeaders.normalize(headers),
        source = source
    )
}

internal fun VideoPlayerActivity.isRemotePlaybackUri(uri: Uri?): Boolean {
    val scheme = uri?.scheme?.lowercase().orEmpty()
    return scheme in REMOTE_URI_SCHEMES
}

internal fun VideoPlayerActivity.resolveVideoTitle(uri: Uri): String {
    remotePlaybackRequest?.title
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    intent.getStringExtra("video_title")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    if (isRemotePlaybackUri(uri)) {
        val remoteName = uri.lastPathSegment
            ?.substringBefore("?")
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.decode(it) }
        return remoteName ?: uri.host ?: "Online Video"
    }

    return try {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    it.getString(nameIndex)
                } else {
                    uri.lastPathSegment ?: "Unknown File"
                }
            } else {
                uri.lastPathSegment ?: "Unknown File"
            }
        } ?: uri.lastPathSegment ?: "Unknown File"
    } catch (e: Exception) {
        Logger.w(TAG, "Failed to resolve file name from uri: $uri", e)
        uri.lastPathSegment ?: "Unknown File"
    }
}

internal fun VideoPlayerActivity.getFileNameFromUri(uri: Uri): String {
    return resolveVideoTitle(uri)
}

internal fun VideoPlayerActivity.savePlaybackState() {
    val uri = videoUri ?: return

    try {
        // 1. 保存播放进度到 PreferencesManager
        if (duration > 0 && currentPosition > 0) {
            preferencesManager.setPlaybackPosition(uri.toString(), currentPosition)
            Logger.d(TAG, "Playback position saved: $currentPosition / $duration")
        }

        // 2. 添加到历史记录 - 只记录本地视频，不记录在线视频
        if (duration > 0 && !isOnlineVideo && preferencesManager.isHistoryRecordingEnabled()) {  // 添加 !isOnlineVideo 判断
            val fileName = getFileNameFromUri(uri)
            val folderName = uri.getFolderName()

            // 获取弹幕信息，一起写入历史记录（避免 addHistory 和 updateDanmu 的竞态条件）
            val danmakuPath = danmakuManager.getCurrentDanmakuPath()
            // 使用 ViewModel 的弹幕状态作为数据源（它是用户实际的开关状态）
            // 不使用 danmakuManager.isVisible() 因为 isShown 可能在停顿时不准确
            val danmuVisible = danmakuPath != null && viewModel.danmakuVisible.value

            historyManager.addHistory(
                uri = uri,
                fileName = fileName,
                position = (currentPosition * 1000).toLong(),
                duration = (duration * 1000).toLong(),
                folderName = folderName,
                danmuPath = danmakuPath,
                danmuVisible = danmuVisible
            )
            Logger.d(TAG, "History saved: $fileName, danmuPath=$danmakuPath, danmuVisible=$danmuVisible")
        } else if (isOnlineVideo) {
            Logger.d(TAG, "Skipping history for online video: $uri")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save playback state", e)
    }
}

/**
 * 视频自然播放完成时保存：将进度写入末尾并清除续播位置，确保当前集被标记为已观看。
 * 修复自动连播开启时，上一集自然播完切到下一集后未被标记完成的问题（issue #94）。
 */
internal fun VideoPlayerActivity.savePlaybackStateAsCompleted() {
    val uri = videoUri ?: return

    try {
        if (duration > 0 && !isOnlineVideo && preferencesManager.isHistoryRecordingEnabled()) {
            val fileName = getFileNameFromUri(uri)
            val folderName = uri.getFolderName()
            val danmakuPath = danmakuManager.getCurrentDanmakuPath()
            val danmuVisible = danmakuPath != null && viewModel.danmakuVisible.value

            historyManager.addHistory(
                uri = uri,
                fileName = fileName,
                position = (duration * 1000).toLong(),
                duration = (duration * 1000).toLong(),
                folderName = folderName,
                danmuPath = danmakuPath,
                danmuVisible = danmuVisible
            )
            Logger.d(TAG, "Playback completed saved: $fileName, position=${duration}s")
        }

        // 已看完，清除续播位置，避免下次从接近末尾的位置恢复
        preferencesManager.clearPlaybackPosition(uri.toString())
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save completed playback state", e)
    }
}
