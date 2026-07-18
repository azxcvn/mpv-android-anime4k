package com.fam4k007.videoplayer.domain.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fam4k007.videoplayer.player.CustomMPVView
import com.fam4k007.videoplayer.player.VideoAspect
import com.fam4k007.videoplayer.remote.RemotePlaybackHeaders
import com.fam4k007.videoplayer.remote.RemotePlaybackRequest
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import android.util.Base64
import java.lang.ref.WeakReference
import java.net.URI

// 架构说明：PlaybackEngine依赖CustomMPVView和VideoAspect（player层）
// 原因：MPV播放器是原生View，必须通过CustomMPVView操作
// 这是合理的依赖关系，PlaybackEngine封装了MPV的所有播放控制逻辑

/**
 * 播放引擎管理器
 * 负责 MPV 播放器的播放控制、属性管理
 * 使用 WeakReference 防止内存泄漏
 * 
 * 注意：MPV初始化和Surface管理已移到CustomMPVView中
 */
class PlaybackEngine(
    private val mpvView: CustomMPVView,
    private val contextRef: WeakReference<android.content.Context>,
    private val eventCallback: PlaybackEventCallback
) : MPVLib.EventObserver {

    companion object {
        private const val TAG = "PlaybackEngine"
    }

    interface PlaybackEventCallback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressUpdate(position: Double, duration: Double)
        fun onFileLoaded()
        fun onEndOfFile()
        fun onError(message: String)
        fun onSurfaceReady()  // 新增：Surface准备完成回调
        fun onBufferingStateChanged(isBuffering: Boolean)  // 新增：缓冲状态回调
    }

    // 播放状态
    var isPlaying: Boolean = false
        private set
    var currentPosition: Double = 0.0
        private set
    var duration: Double = 0.0
        private set
    var currentSpeed: Double = 1.0
        private set
    var isHardwareDecoding: Boolean = true
        private set
    
    // 缓冲状态
    var isBuffering: Boolean = false
        private set
    var isSeeking: Boolean = false
        private set
    
    // 保存当前文件路径
    private var currentFilePath: String? = null

    // 进度更新
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private var isInitialized = false

    /** 文件结束检测标志，防止重复触发 EOF */
    private var eofHandled = false

    /**
     * 初始化 MPV 播放器
     * 注意：MPV的create和init已由CustomMPVView处理
     * 这里只注册事件观察者
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.w(TAG, "PlaybackEngine already initialized")
            return true
        }

        return try {
            // 注册事件观察者
            MPVLib.addObserver(this)

            isInitialized = true
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "PlaybackEngine initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "MPV initialization failed", e)
            eventCallback.onError("Player initialization failed: ${e.message}")
            false
        }
    }

    /**
     * 重置文件结束检测标志（每次加载新视频前调用）
     */
    fun resetEofDetection() {
        eofHandled = false
        Log.d(TAG, "EOF detection reset")
    }

    /**
     * 加载视频文件
     */
    fun loadVideo(videoUri: android.net.Uri, startPosition: Double = 0.0) {
        if (!isInitialized) {
            Log.e(TAG, "PlaybackEngine not initialized")
            return
        }

        try {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Loading video: $videoUri")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Start position: $startPosition seconds")
            
            // 对于网络URL，直接使用原始字符串；对于本地文件，使用URI
            val filePath = when (videoUri.scheme) {
                "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "ftp", "sftp" -> {
                    // 网络URL：直接使用toString()
                    videoUri.toString()
                }
                else -> {
                    // 本地文件：使用toString()（保持原有逻辑）
                    videoUri.toString()
                }
            }
            
            // 保存文件路径
            currentFilePath = filePath
            
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "MPV loading file path: $filePath")
            MPVLib.command("loadfile", filePath)
            
            // 确保视频加载后开始播放
            handler.postDelayed({
                try {
                    MPVLib.setPropertyBoolean("pause", false)
                    isPlaying = true
                    
                    // 字幕将在 FILE_LOADED 事件中自动启用，不在这里处理
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video auto-play started")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start auto-play: ${e.message}")
                }
            }, 100)
            
            // 如果有起始位置,在文件加载后立即跳转
            if (startPosition > 0.1) {
                // 使用延迟确保文件已开始加载
                handler.postDelayed({
                    try {
                        seekTo(startPosition.toInt())
                        Log.d(TAG, "Restored position: $startPosition")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore position: ${e.message}")
                    }
                }, 200)  // 减少延迟到200ms
            }
            
            // 异步记录视频信息(不阻塞播放)
            handler.postDelayed({
                try {
                    val videoCodec = MPVLib.getPropertyString("video-codec")
                    val audioCodec = MPVLib.getPropertyString("audio-codec")
                    val videoFormat = MPVLib.getPropertyString("video-format")
                    val hwdec = MPVLib.getPropertyString("hwdec-current")
                    
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video codec: $videoCodec")
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Audio codec: $audioCodec")
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video format: $videoFormat")
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Hardware decoding: $hwdec")
                    
                    // 检查视频流是否存在
                    if (videoCodec == null || videoCodec == "null") {
                        Log.w(TAG, "⚠️ Video codec is null - this file may be audio-only or corrupted")
                        handler.post {
                            eventCallback.onError("Invalid video stream or corrupted file")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get video info: ${e.message}")
                }
            }, 800)

            // 开始进度更新 - 本地视频可以立即开始
            handler.post(updateProgressRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video", e)
            eventCallback.onError("Failed to load video: ${e.message}")
        }
    }

    /**
     * 从URL字符串加载视频（用于在线视频）
     * 支持格式：url;{Cookie@xxx&&User-Agent@yyy&&Referer@zzz}
     */
    fun loadVideoFromUrl(urlString: String, startPosition: Double = 0.0) {
        if (!isInitialized) {
            Log.e(TAG, "PlaybackEngine not initialized")
            return
        }

        try {
            Log.d(TAG, "Loading video from URL: $urlString")
            Log.d(TAG, "Start position: $startPosition seconds")
            
            // 解析URL和HTTP头（支持海阔视界格式）
            val (actualUrl, httpHeaders) = parseUrlWithHeaders(urlString)
            loadRemoteInternal(actualUrl, httpHeaders, startPosition)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video from URL", e)
            eventCallback.onError("Failed to load video: ${e.message}")
        }
    }

    fun loadRemote(request: RemotePlaybackRequest, startPosition: Double = 0.0) {
        if (!isInitialized) {
            Log.e(TAG, "PlaybackEngine not initialized")
            return
        }

        try {
            val normalizedHeaders = RemotePlaybackHeaders.enrich(
                headers = request.headers,
                sourcePageUrl = request.sourcePageUrl
            )
            Log.d(TAG, "Loading remote request: ${request.url}")
            Log.d(TAG, "Remote request source: ${request.source}")
            Log.d(TAG, "Remote request headers: ${RemotePlaybackHeaders.describeForLog(normalizedHeaders)}")
            loadRemoteInternal(request.url, normalizedHeaders, startPosition)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load remote request", e)
            eventCallback.onError("Failed to load video: ${e.message}")
        }
    }
    
    /**
     * 解析URL和HTTP头信息
     * 支持格式：url;{Cookie@xxx&&User-Agent@yyy&&Referer@zzz}
     * @return Pair<实际URL, HTTP头列表>
     */
    private fun parseUrlWithHeaders(input: String): Pair<String, Map<String, String>> {
        if (!input.contains(";{") || !input.contains("}")) {
            // 普通URL，没有头信息
            return Pair(input, emptyMap())
        }
        
        try {
            val parts = input.split(";{", "}")
            if (parts.size < 2) {
                return Pair(input, emptyMap())
            }
            
            val url = parts[0].trim()
            val headersStr = parts[1]
            
            // 解析HTTP头：Cookie@xxx&&User-Agent@yyy
            val headers = linkedMapOf<String, String>()
            headersStr.split("&&").forEach { header ->
                val keyValue = header.split("@", limit = 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    // 将全角分号转换为半角分号（海阔视界的格式）
                    val value = keyValue[1].replace("；；", "; ").trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        headers[key] = value
                    }
                }
            }
            
            Log.d(TAG, "Parsed URL: $url")
            Log.d(TAG, "Parsed ${headers.size} HTTP headers")
            
            return Pair(url, RemotePlaybackHeaders.normalize(headers))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse URL with headers: ${e.message}")
            return Pair(input, emptyMap())
        }
    }

    private fun loadRemoteInternal(
        actualUrl: String,
        headers: Map<String, String>,
        startPosition: Double
    ) {
        // 从 URL 中提取认证信息并转为 Authorization header
        val (cleanUrl, urlAuthHeader) = extractUrlCredentials(actualUrl)
        val mergedHeaders = if (urlAuthHeader != null) {
            val mutable = LinkedHashMap(headers)
            if (mutable["Authorization"].isNullOrBlank()) {
                mutable["Authorization"] = urlAuthHeader
            }
            mutable
        } else {
            headers
        }

        currentFilePath = cleanUrl

        resetRemotePlaybackOptions()
        applyRemoteHeaders(mergedHeaders)
        applyStreamingOptions()

        Log.d(TAG, "MPV loading URL: $cleanUrl")
        MPVLib.command("loadfile", cleanUrl)

        scheduleAutoPlay()
        scheduleSeekRestore(startPosition)
        scheduleRemoteMediaInfoLog()

        // 延迟开始进度更新,避免在视频未就绪时查询属性
        handler.postDelayed({
            handler.post(updateProgressRunnable)
        }, 2000)
    }

    /**
     * 从 URL 中提取 user:pass@host 格式的认证信息
     * @return Pair(清理后的URL, Authorization header值)
     */
    private fun extractUrlCredentials(url: String): Pair<String, String?> {
        try {
            val uri = URI(url)
            val userInfo = uri.userInfo
            if (userInfo.isNullOrBlank()) {
                return Pair(url, null)
            }

            val colonIndex = userInfo.indexOf(':')
            if (colonIndex <= 0) {
                return Pair(url, null)
            }

            val username = userInfo.substring(0, colonIndex)
            val password = userInfo.substring(colonIndex + 1)
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // 构建不含认证信息的 URL
            val cleanUri = URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment)
            Log.d(TAG, "Extracted URL credentials, clean URL: ${cleanUri}")
            return Pair(cleanUri.toString(), "Basic $encoded")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract URL credentials: ${e.message}")
            return Pair(url, null)
        }
    }

    private fun resetRemotePlaybackOptions() {
        MPVLib.setOptionString("user-agent", RemotePlaybackHeaders.DEFAULT_USER_AGENT)
        MPVLib.setOptionString("referrer", "")
        MPVLib.setOptionString("http-header-fields", "Accept: */*")
    }

    private fun applyRemoteHeaders(headers: Map<String, String>) {
        val normalizedHeaders = RemotePlaybackHeaders.enrich(headers)
        val userAgent = RemotePlaybackHeaders.get(normalizedHeaders, "User-Agent").orEmpty()
            .ifBlank { RemotePlaybackHeaders.DEFAULT_USER_AGENT }
        val referer = RemotePlaybackHeaders.get(normalizedHeaders, "Referer").orEmpty().trim()

        MPVLib.setOptionString("user-agent", userAgent)
        MPVLib.setOptionString("referrer", referer)

        val headerString = RemotePlaybackHeaders.toMpvHeaderFields(
            headers = normalizedHeaders,
            excludeNames = setOf("User-Agent", "Referer")
        )
        MPVLib.setOptionString("http-header-fields", headerString)
        Log.d(TAG, "Set HTTP headers: ${RemotePlaybackHeaders.describeForLog(normalizedHeaders)}")
    }

    private fun applyStreamingOptions() {
        // 这些都是内存缓存，不占用持久化存储
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "120")
        MPVLib.setOptionString("demuxer-max-bytes", "150M")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("stream-buffer-size", "5M")
        MPVLib.setOptionString("http-allow-redirect", "yes")
        MPVLib.setOptionString("hls-bitrate", "max")
    }

    private fun scheduleAutoPlay() {
        handler.postDelayed({
            try {
                MPVLib.setPropertyBoolean("pause", false)
                isPlaying = true
                Log.d(TAG, "Video auto-play started")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start auto-play: ${e.message}")
            }
        }, 100)
    }

    private fun scheduleSeekRestore(startPosition: Double) {
        if (startPosition <= 0.1) {
            return
        }

        handler.postDelayed({
            try {
                seekTo(startPosition.toInt())
                Log.d(TAG, "Restored position: $startPosition")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore position: ${e.message}")
            }
        }, 200)
    }

    private fun scheduleRemoteMediaInfoLog() {
        handler.postDelayed({
            try {
                val videoCodec = MPVLib.getPropertyString("video-codec")
                val audioCodec = MPVLib.getPropertyString("audio-codec")
                val videoFormat = MPVLib.getPropertyString("video-format")
                val hwdec = MPVLib.getPropertyString("hwdec-current")

                Log.d(TAG, "Video codec: $videoCodec")
                Log.d(TAG, "Audio codec: $audioCodec")
                Log.d(TAG, "Video format: $videoFormat")
                Log.d(TAG, "Hardware decoding: $hwdec")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get video info: ${e.message}")
            }
        }, 3000)
    }

    /**
     * 播放/暂停切换
     */
    fun togglePlayPause() {
        try {
            isPlaying = !isPlaying
            MPVLib.setPropertyBoolean("pause", !isPlaying)
            eventCallback.onPlaybackStateChanged(isPlaying)
            Log.d(TAG, "Playback state toggled to: $isPlaying")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle playback state", e)
            isPlaying = !isPlaying // 恢复状态
            eventCallback.onError("Failed to toggle playback: ${e.message}")
        }
    }

    /**
     * 设置播放状态
     */
    fun setPlaying(playing: Boolean) {
        try {
            if (isPlaying != playing) {
                isPlaying = playing
                MPVLib.setPropertyBoolean("pause", !isPlaying)
                eventCallback.onPlaybackStateChanged(isPlaying)
                Log.d(TAG, "Playback state set to: $isPlaying")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback state", e)
            // 不反转状态，保留当前状态
            eventCallback.onError("Failed to set playback state: ${e.message}")
        }
    }

    /**
     * 跳转到指定位置（绝对位置）
     * @param precise true=精确定位(慢), false=关键帧定位(快)
     */
    fun seekTo(position: Int, precise: Boolean = false) {
        try {
            // 位置校验：防止超越
            val safePosition = when {
                position < 0 -> {
                    Log.w(TAG, "Seek position is negative: $position, clamping to 0")
                    0
                }
                duration > 0 && position > duration.toInt() -> {
                    Log.w(TAG, "Seek position exceeds duration: $position > ${duration.toInt()}, clamping to duration")
                    duration.toInt()
                }
                else -> position
            }
            
            // 根据精确度选择不同的定位模式
            val seekMode = if (precise) "absolute" else "absolute+keyframes"
            MPVLib.command("seek", safePosition.toString(), seekMode)
            
            Log.d(TAG, "Seek to: $safePosition (mode: $seekMode, requested: $position)")
        } catch (e: Exception) {
            Log.e(TAG, "Seek failed", e)
            eventCallback.onError("Seek failed: ${e.message}")
        }
    }

    /**
     * 相对跳转
     */
    fun seekBy(seconds: Int) {
        try {
            val currentPos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
            val targetPos = currentPos + seconds
            
            // 位置校验：防止超越
            val safeTargetPos = when {
                targetPos < 0 -> {
                    Log.w(TAG, "Seek target is negative: $targetPos, clamping to 0")
                    0.0
                }
                duration > 0 && targetPos > duration -> {
                    Log.w(TAG, "Seek target exceeds duration: $targetPos > $duration, clamping to duration")
                    duration
                }
                else -> targetPos
            }
            
            // 用绝对定位代替相对定位
            MPVLib.command("seek", safeTargetPos.toString(), "absolute")
            Log.d(TAG, "Seek by: $seconds, position before: $currentPos, target: $targetPos, safe target: $safeTargetPos")
            
            // 使用已有handler异步检查seek后的位置
            handler.postDelayed({
                try {
                    val newPos = MPVLib.getPropertyDouble("time-pos") ?: currentPos
                    val actualDiff = newPos - currentPos
                    Log.d("SEEK_DEBUG", "PlaybackEngine.seekBy RESULT: requested=$seconds, actualDiff=$actualDiff, posAfter=$newPos")
                } catch (e: Exception) {
                    Log.e("SEEK_DEBUG", "Failed to check position after seek", e)
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Seek by failed", e)
            eventCallback.onError("Seek failed: ${e.message}")
        }
    }

    /**
     * 设置播放速度
     */
    fun setSpeed(speed: Double) {
        currentSpeed = speed
        MPVLib.setPropertyDouble("speed", speed)
        Log.d(TAG, "Speed set to: $speed")
    }

    /**
     * 切换解码器
     */
    fun setHardwareDecoding(enabled: Boolean) {
        isHardwareDecoding = enabled
        val hwdec = if (enabled) "auto" else "no"
        MPVLib.setPropertyString("hwdec", hwdec)
        Log.d(TAG, "Hardware decoding: $enabled")
    }

    /**
     * 设置音轨
     */
    fun setAudioTrack(trackId: Int) {
        MPVLib.setPropertyInt("aid", trackId)
        Log.d(TAG, "Audio track set to: $trackId")
    }
    
    /**
     * 设置字幕轨道
     */
    fun setSubtitleTrack(trackId: Int) {
        if (trackId == -1) {
            // 关闭字幕需要使用"no"
            MPVLib.setPropertyString("sid", "no")
            Log.d(TAG, "Subtitle track disabled")
        } else {
            MPVLib.setPropertyInt("sid", trackId)
            Log.d(TAG, "Subtitle track set to: $trackId")
        }
    }
    
    /**
     * 获取当前字幕轨道
     */
    fun getCurrentSubtitleTrack(): Int {
        return try {
            MPVLib.getPropertyInt("sid") ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current subtitle track", e)
            0
        }
    }
    
    /**
     * 设置字幕位置（垂直方向）
     */
    fun setSubtitlePosition(position: Float) {
        setSubtitleVerticalPosition(position)
    }

    /**
     * 获取章节列表
     */
    fun getChapters(): List<Pair<String, Double>> {
        val chapters = mutableListOf<Pair<String, Double>>()
        try {
            val chapterCount = MPVLib.getPropertyInt("chapter-list/count") ?: 0
            for (i in 0 until chapterCount) {
                val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
                val time = MPVLib.getPropertyDouble("chapter-list/$i/time") ?: 0.0
                chapters.add(Pair(title, time))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chapters", e)
        }
        return chapters
    }

    /**
     * 应用 Anime4K 着色器
     */
    fun applyShaders(shaderChain: String) {
        try {
            MPVLib.setOptionString("glsl-shaders", shaderChain)
            Log.d(TAG, "Applied shaders: $shaderChain")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply shaders", e)
            eventCallback.onError("Failed to apply shader: ${e.message}")
        }
    }

    /**
     * 更新播放进度
     */
    private fun updateProgress() {
        try {
            // 安全获取属性,避免在视频未就绪时查询导致错误
            val pos = try {
                MPVLib.getPropertyDouble("time-pos") ?: 0.0
            } catch (e: Exception) {
                0.0
            }
            
            val dur = try {
                MPVLib.getPropertyDouble("duration") ?: 0.0
            } catch (e: Exception) {
                0.0
            }
            
            currentPosition = pos
            duration = dur

            if (duration > 0) {
                eventCallback.onProgressUpdate(currentPosition, duration)
            }

            // 通过进度检测文件结束（keep-open=yes 时 MPV_EVENT_END_FILE 不会触发）
            // 当位置接近末尾且正在播放时，触发结束处理
            // 注意：dur 必须 > 1.0，避免新视频加载时 duration=0 导致误触发
            if (dur > 1.0 && pos >= dur - 0.5 && isPlaying && !eofHandled) {
                eofHandled = true
                Log.d(TAG, "End of file detected by progress (pos=$pos, dur=$dur), triggering onEndOfFile")
                handler.post {
                    eventCallback.onEndOfFile()
                }
            }
            
            // 检查缓冲状态
            checkBufferingState()
        } catch (e: Exception) {
            // 只记录警告,不阻塞播放
            Log.w(TAG, "Failed to update progress: ${e.message}")
        }
    }
    
    /**
     * 检查缓冲状态
     */
    private fun checkBufferingState() {
        try {
            // 检查是否因缓冲而暂停
            val pausedForCache = try {
                MPVLib.getPropertyBoolean("paused-for-cache") ?: false
            } catch (e: Exception) {
                false
            }
            
            // 检查是否在快进
            val seeking = try {
                MPVLib.getPropertyBoolean("seeking") ?: false
            } catch (e: Exception) {
                false
            }
            
            // 检查缓存时长（针对流媒体）
            val cacheDuration = try {
                MPVLib.getPropertyDouble("demuxer-cache-duration") ?: 0.0
            } catch (e: Exception) {
                0.0
            }
            
            // 如果缓存时长小于10秒，认为是缓冲状态
            val lowCache = cacheDuration < 10.0
            
            val newBufferingState = pausedForCache  // 移除seeking，只基于缓存暂停状态 || lowCache
            
            // 只在状态改变时通知
            if (newBufferingState != isBuffering) {
                isBuffering = newBufferingState
                isSeeking = seeking
                Log.d(TAG, "Buffering state changed: buffering=$isBuffering, seeking=$isSeeking, cacheDuration=$cacheDuration")
                handler.post {
                    eventCallback.onBufferingStateChanged(isBuffering)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check buffering state: ${e.message}")
        }
    }

    /**
     * 销毁播放引擎
     * 按正确顺序清理所有资源
     */
    fun destroy() {
        Log.d(TAG, "========== Destroying PlaybackEngine ==========")
        
        if (!isInitialized) {
            Log.d(TAG, "Already destroyed, skipping")
            return
        }
        isInitialized = false
        
        // 立即停止进度更新，防止内存泄漏
        handler.removeCallbacks(updateProgressRunnable)
        Log.d(TAG, "✓ 停止进度更新")
        
        try {
            // 1. 停止播放
            try {
                MPVLib.command("stop")
                Log.d(TAG, "✓ 停止播放")
            } catch (e: Exception) {
                Log.w(TAG, "停止播放失败: ${e.message}")
            }
            
            // 2. 移除观察者
            MPVLib.removeObserver(this)
            Log.d(TAG, "✓ 移除事件观察者")
            
            // 3. 销毁MPV
            try {
                MPVLib.destroy()
                Log.d(TAG, "✓ MPVLib已销毁")
            } catch (e: Exception) {
                Log.w(TAG, "销毁MPVLib失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying MPV", e)
        }

        // 清理引用
        currentFilePath = null
        
        Log.d(TAG, "✓ PlaybackEngine已完全销毁")
        Log.d(TAG, "========================================")
    }

    // ========== MPVLib.EventObserver ==========

    override fun eventProperty(property: String, value: MPVNode) {
        // 处理复杂类型的属性变化（如 track-list 等）
        Log.d(TAG, "Event property (MPVNode): $property = $value")
    }

    override fun eventProperty(property: String) {
        Log.d(TAG, "Event property: $property")
        
        // 监听轨道列表变化（参考 mpvKt 实现）
        if (property == "track-list") {
            Log.d(TAG, "轨道列表已更新，刷新字幕和音轨信息")
            handler.post {
                // 通知UI更新轨道列表（如果需要）
                // 这里可以添加回调通知UI刷新
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        Log.d(TAG, "Event property: $property = $value")
    }

    override fun eventProperty(property: String, value: Double) {
        Log.d(TAG, "Event property: $property = $value")
    }

    override fun eventProperty(property: String, value: Boolean) {
        Log.d(TAG, "Event property: $property = $value")

        // 监听暂停状态变化
        if (property == "pause") {
            isPlaying = !value
            handler.post {
                eventCallback.onPlaybackStateChanged(isPlaying)
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        Log.d(TAG, "Event property: $property = $value")
        
        // 监听字幕轨道变化（参考 mpvKt）
        when (property) {
            "sid" -> {
                val trackId = when (value) {
                    "auto" -> null
                    "no", "false" -> -1
                    else -> value.toIntOrNull()
                }
                if (trackId != null) {
                    Log.d(TAG, "当前字幕轨道已变更为: $trackId")
                }
            }
            "aid" -> {
                val trackId = when (value) {
                    "auto" -> null
                    "no", "false" -> -1
                    else -> value.toIntOrNull()
                }
                if (trackId != null) {
                    Log.d(TAG, "当前音轨已变更为: $trackId")
                }
            }
        }
    }

    override fun event(eventId: Int, data: MPVNode) {
        Log.d(TAG, "Event: $eventId")
        when (eventId) {
            6 -> { // MPV_EVENT_FILE_LOADED
                Log.d(TAG, "File loaded successfully")
                handler.post {
                    // 确保播放状态和MPV同步
                    try {
                        val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
                        isPlaying = !isPaused
                        Log.d(TAG, "File loaded, playing state: $isPlaying")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get pause state: ${e.message}")
                        isPlaying = true  // 默认为播放状态
                    }
                    eventCallback.onFileLoaded()
                }
            }
            7 -> { // MPV_EVENT_END_FILE
                // keep-open=yes 时此事件只会在切换视频时触发（卸载旧视频），不会在自然结束时触发
                // 自然结束检测已由 updateProgress() 中的进度检测处理
                Log.d(TAG, "End of file (ignored, progress-based detection handles EOF)")
                handler.post {
                    isPlaying = false
                }
            }
        }
    }

    /**
     * 获取音轨列表
     * @return List<Triple<轨道ID, 轨道名称, 是否当前轨道>>
     */
    fun getAudioTracks(): List<Triple<Int, String, Boolean>> {
        return try {
            val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
            val currentTrackId = MPVLib.getPropertyInt("aid") ?: -1
            val tracks = mutableListOf<Triple<Int, String, Boolean>>()

            for (i in 0 until trackCount) {
                val type = MPVLib.getPropertyString("track-list/$i/type")
                if (type == "audio") {
                    val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                    val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: "unknown"
                    val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                    val name = if (title.isNotEmpty()) "$lang - $title" else lang
                    val isCurrent = (id == currentTrackId)
                    tracks.add(Triple(id, name, isCurrent))
                }
            }
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio tracks", e)
            emptyList()
        }
    }

    /**
     * 选择音轨
     */
    fun selectAudioTrack(trackId: Int) {
        try {
            MPVLib.setPropertyInt("aid", trackId)
            Log.d(TAG, "Selected audio track: $trackId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select audio track", e)
        }
    }

    /**
     * 获取字幕轨道列表
     * @return List<Triple<轨道ID, 轨道名称, 是否当前轨道>>
     */
    /**
     * 获取字幕轨道列表
     * @return List<Triple<轨道ID, 轨道名称, 是否当前轨道>>
     */
    fun getSubtitleTracks(): List<Triple<Int, String, Boolean>> {
        return try {
            val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
            val currentTrackIdStr = MPVLib.getPropertyString("sid") ?: "no"
            val currentTrackId = when (currentTrackIdStr) {
                "no", "false" -> -1
                "auto" -> null
                else -> currentTrackIdStr.toIntOrNull() ?: -1
            }
            
            val tracks = mutableListOf<Triple<Int, String, Boolean>>()
            tracks.add(Triple(-1, "Disable Subtitles", currentTrackId == -1))

            for (i in 0 until trackCount) {
                val type = MPVLib.getPropertyString("track-list/$i/type")
                if (type != "sub") continue
                
                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                
                val name = when {
                    title.isNotEmpty() && lang.isNotEmpty() -> "$title ($lang)"
                    title.isNotEmpty() -> title
                    lang.isNotEmpty() -> lang
                    else -> "Subtitle Track"
                }
                
                tracks.add(Triple(id, name, id == currentTrackId))
            }
            
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subtitle tracks", e)
            emptyList()
        }
    }

    /**
     * 选择字幕轨道
     */
    fun selectSubtitleTrack(trackId: Int) {
        try {
            if (trackId == -1) {
                MPVLib.setPropertyString("sid", "no")
                Log.d(TAG, "Subtitle disabled")
            } else {
                MPVLib.setPropertyInt("sid", trackId)
                MPVLib.setPropertyBoolean("sub-visibility", true)
                Log.d(TAG, "Selected subtitle track: $trackId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to select subtitle track", e)
        }
    }

    /**
     * 设置字幕缩放比例（通用方法，支持所有字幕类型）
     * @param scale 缩放比例（默认1.0），范围 0.5-3.0
     */
    fun setSubtitleScale(scale: Double) {
        try {
            MPVLib.setPropertyDouble("sub-scale", scale)
            Log.d(TAG, "Subtitle scale set to: $scale")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle scale", e)
        }
    }

    /**
     * 获取当前字幕缩放比例
     */
    fun getSubtitleScale(): Double {
        return try {
            MPVLib.getPropertyDouble("sub-scale") ?: 1.0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subtitle scale", e)
            1.0
        }
    }

    /**
     * 设置字幕垂直位置
     * @param position 0-100，0=顶部，100=底部（默认100）
     */
    fun setSubtitleVerticalPosition(position: Float) {
        try {
            MPVLib.setPropertyDouble("sub-pos", position.toDouble())
            Log.d(TAG, "Subtitle vertical position set to: $position")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle vertical position", e)
        }
    }

    /**
     * 获取字幕垂直位置
     */
    fun getSubtitleVerticalPosition(): Int {
        return try {
            MPVLib.getPropertyInt("sub-pos") ?: 100
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subtitle vertical position", e)
            100
        }
    }

    /**
     * 设置字幕延迟
     * @param delay 延迟时间（秒），正数延迟，负数提前
     */
    fun setSubtitleDelay(delay: Double) {
        try {
            MPVLib.setPropertyDouble("sub-delay", delay)
            Log.d(TAG, "Subtitle delay set to: $delay seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle delay", e)
        }
    }

    /**
     * 获取字幕延迟
     */
    fun getSubtitleDelay(): Double {
        return try {
            MPVLib.getPropertyDouble("sub-delay") ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subtitle delay", e)
            0.0
        }
    }
    
    /**
     * 启用或禁用 ASS/SSA 字幕样式覆盖
     * 参考 mpvKt 实现：使用 "force" 和 "scale"
     * @param enable true=完全覆盖ASS样式；false=默认模式（保持动画）
     */
    fun setAssOverride(enable: Boolean) {
        try {
            // "force" - 强制覆盖所有 ASS 样式（用于后续样式自定义）
            // "scale" - 默认模式，保持 ASS 动画和特效
            val value = if (enable) "force" else "scale"
            MPVLib.setPropertyString("sub-ass-override", value)
            Log.d(TAG, "ASS override set to: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ASS override", e)
        }
    }
    
    /**
     * 获取当前ASS覆盖设置
     */
    fun getAssOverride(): Boolean {
        return try {
            val value = MPVLib.getPropertyString("sub-ass-override") ?: "scale"
            value == "force"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ASS override", e)
            false
        }
    }
    
    /**
     * 设置字幕文本颜色
     * @param color 颜色值，格式如 "#FFFFFF" 或 "#FFFFFFFF"（ARGB）
     */
    fun setSubtitleTextColor(color: String) {
        try {
            MPVLib.setPropertyString("sub-color", color)
            Log.d(TAG, "Subtitle text color set to: $color")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle text color", e)
        }
    }
    
    /**
     * 设置字幕描边粗细
     * @param size 粗细值，范围 0-100
     */
    fun setSubtitleBorderSize(size: Int) {
        try {
            // 使用 setPropertyString 确保跨平台兼容性（mpv 的 sub-border-size 为浮点属性）
            MPVLib.setPropertyString("sub-border-size", size.toString())
            Log.d(TAG, "Subtitle border size set to: $size")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle border size", e)
        }
    }
    
    /**
     * 设置字幕描边颜色
     * @param color 颜色值，格式如 "#000000" 或 "#FF000000"（ARGB）
     */
    fun setSubtitleBorderColor(color: String) {
        try {
            MPVLib.setPropertyString("sub-border-color", color)
            Log.d(TAG, "Subtitle border color set to: $color")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle border color", e)
        }
    }
    
    /**
     * 设置字幕背景颜色
     * @param color 颜色值，格式如 "#00000000"（ARGB，支持透明）
     */
    fun setSubtitleBackColor(color: String) {
        try {
            MPVLib.setPropertyString("sub-back-color", color)
            Log.d(TAG, "Subtitle back color set to: $color")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle back color", e)
        }
    }
    
    /**
     * 设置字幕描边样式
     * @param style 样式值: "outline-and-shadow", "opaque-box", "background-box"
     */
    fun setSubtitleBorderStyle(style: String) {
        try {
            MPVLib.setPropertyString("sub-border-style", style)
            Log.d(TAG, "Subtitle border style set to: $style")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle border style", e)
        }
    }

    /**
     * 检查是否有章节
     */
    fun hasChapters(): Boolean {
        return try {
            val chapterCount = MPVLib.getPropertyInt("chapters") ?: 0
            chapterCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check chapters", e)
            false
        }
    }
    
    // ==================== 音频均衡器 ====================
    
    /**
     * 设置音频均衡器
     * 使用 mpv 的 af (audio filter) 属性实现 5 频段均衡器
     * 频段: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz
     *
     * @param enabled 是否启用均衡器
     * @param bands 5 个频段的增益值（dB），范围 -15 到 +15
     */
    fun setEqualizer(enabled: Boolean, bands: List<Float>) {
        try {
            if (!enabled || bands.all { it == 0f }) {
                // 移除均衡器滤镜
                MPVLib.command("af", "remove", "@eq")
                Log.d(TAG, "Equalizer disabled")
            } else {
                // 构建 superequalizer 滤镜参数
                // mpv superequalizer 使用 18 个频段，我们映射 5 个频段到对应位置
                // 60Hz → band1, 230Hz → band3, 910Hz → band7, 3600Hz → band11, 14000Hz → band15
                val b1 = bands.getOrElse(0) { 0f }
                val b2 = bands.getOrElse(1) { 0f }
                val b3 = bands.getOrElse(2) { 0f }
                val b4 = bands.getOrElse(3) { 0f }
                val b5 = bands.getOrElse(4) { 0f }
                
                // 使用 lavfi equalizer 链（多个 equalizer 滤镜串联）
                val filterStr = "lavfi=[" +
                    "equalizer=f=60:t=o:w=2:g=$b1," +
                    "equalizer=f=230:t=o:w=2:g=$b2," +
                    "equalizer=f=910:t=o:w=2:g=$b3," +
                    "equalizer=f=3600:t=o:w=2:g=$b4," +
                    "equalizer=f=14000:t=o:w=2:g=$b5]"
                
                MPVLib.command("af", "add", "@eq:$filterStr")
                Log.d(TAG, "Equalizer set: bands=$bands")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set equalizer", e)
        }
    }
    
    /**
     * 设置低音增强
     * @param value 增强强度，0-100
     */
    fun setBassBoost(value: Int) {
        try {
            if (value <= 0) {
                MPVLib.command("af", "remove", "@bass")
                Log.d(TAG, "Bass boost disabled")
            } else {
                // 使用 lowshelf 滤镜增强低频
                val gain = value * 0.2  // 0-100 → 0-20 dB
                val filterStr = "lavfi=[lowshelf=f=250:t=s:g=$gain]"
                MPVLib.command("af", "add", "@bass:$filterStr")
                Log.d(TAG, "Bass boost set to: $value ($gain dB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost", e)
        }
    }
    
    /**
     * 设置虚拟环绕声
     * @param value 虚拟环绕强度，0-100
     */
    fun setVirtualizer(value: Int) {
        try {
            if (value <= 0) {
                MPVLib.command("af", "remove", "@virt")
                Log.d(TAG, "Virtualizer disabled")
            } else {
                // 使用 extrastereo 滤镜增强立体声效果
                val strength = value / 50.0  // 0-100 → 0.0-2.0
                val filterStr = "lavfi=[extrastereo=m=$strength]"
                MPVLib.command("af", "add", "@virt:$filterStr")
                Log.d(TAG, "Virtualizer set to: $value (strength=$strength)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set virtualizer", e)
        }
    }

    /**
     * 设置着色器列表（用于Anime4K等）
     * 启用着色器时自动开启OpenGL渲染优化（PBO、直接渲染），提升性能
     */
    fun setShaderList(shaders: List<String>) {
        try {
            if (shaders.isEmpty()) {
                MPVLib.setPropertyString("glsl-shaders", "")
                // 关闭OpenGL优化（恢复默认）
                setGlslOptimizations(false)
                Log.d(TAG, "Cleared shader list")
            } else {
                val shaderString = shaders.joinToString(":")
                MPVLib.setPropertyString("glsl-shaders", shaderString)
                // 开启OpenGL渲染优化：PBO加速+直接渲染
                setGlslOptimizations(true)
                Log.d(TAG, "Set shaders: $shaderString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set shader list", e)
        }
    }

    /**
     * OpenGL渲染优化开关
     * - opengl-pbo=yes: 像素缓冲区对象，加速CPU→GPU纹理上传
     * - opengl-early-flush=no: 延迟OpenGL刷新，减少同步开销
     * - vd-lavc-dr=yes: 直接渲染，解码器直接写入GPU内存，减少内存拷贝
     */
    private fun setGlslOptimizations(enabled: Boolean) {
        try {
            if (enabled) {
                MPVLib.setOptionString("opengl-pbo", "yes")
                MPVLib.setOptionString("opengl-early-flush", "no")
                MPVLib.setOptionString("vd-lavc-dr", "yes")
                Log.d(TAG, "OpenGL optimizations enabled (PBO+DR)")
            } else {
                MPVLib.setOptionString("opengl-pbo", "no")
                MPVLib.setOptionString("opengl-early-flush", "yes")
                MPVLib.setOptionString("vd-lavc-dr", "no")
                Log.d(TAG, "OpenGL optimizations disabled")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set OpenGL optimizations", e)
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        try {
            MPVLib.setPropertyBoolean("pause", true)
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause", e)
        }
    }
    
    /**
     * 开始播放
     */
    fun play() {
        try {
            MPVLib.setPropertyBoolean("pause", false)
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play", e)
        }
    }
    
    /**
     * 改变视频画面比例
     * 参考mpvKt的实现
     */
    fun changeVideoAspect(aspect: VideoAspect) {
        try {
            val context = contextRef.get() ?: return

            // 先重置 zoom 和 unscaled，仅特定模式设置
            MPVLib.setPropertyDouble("video-zoom", 0.0)
            MPVLib.setPropertyString("video-unscaled", "no")

            when (aspect) {
                VideoAspect.FIT -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                }
                VideoAspect.STRETCH -> {
                    val dm = context.resources.displayMetrics
                    val ratio = dm.widthPixels / dm.heightPixels.toDouble()
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", ratio)
                }
                VideoAspect.CROP -> {
                    MPVLib.setPropertyDouble("panscan", 1.0)
                    MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                }
                VideoAspect.EQUAL_WIDTH -> {
                    val w = MPVLib.getPropertyInt("video-params/w") ?: return
                    val dm = context.resources.displayMetrics
                    val zoom = dm.widthPixels.toDouble() / w
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                    MPVLib.setPropertyDouble("video-zoom", zoom)
                }
                VideoAspect.EQUAL_HEIGHT -> {
                    val h = MPVLib.getPropertyInt("video-params/h") ?: return
                    val dm = context.resources.displayMetrics
                    val zoom = dm.heightPixels.toDouble() / h
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                    MPVLib.setPropertyDouble("video-zoom", zoom)
                }
                VideoAspect.ORIGINAL -> {
                    val vw = MPVLib.getPropertyInt("video-params/w") ?: return
                    val vh = MPVLib.getPropertyInt("video-params/h") ?: return
                    if (vw <= 0 || vh <= 0) return
                    val dm = context.resources.displayMetrics
                    val sw = dm.widthPixels.toDouble()
                    val sh = dm.heightPixels.toDouble()
                    val videoAspect = vw.toDouble() / vh
                    val screenAspect = sw / sh
                    // mpv 将视频 scale 到填满屏幕的较大维度，反算 1:1 像素映射所需 zoom
                    val fitScale = if (videoAspect > screenAspect) sw / vw else sh / vh
                    val zoom = Math.log(1.0 / fitScale) / Math.log(2.0)
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                    MPVLib.setPropertyDouble("video-zoom", zoom)
                }
                VideoAspect.RATIO_4_3 -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", 4.0 / 3.0)
                }
                VideoAspect.RATIO_16_9 -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyDouble("video-aspect-override", 16.0 / 9.0)
                }
            }

            Log.d(TAG, "Video aspect changed to: ${aspect.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change video aspect", e)
        }
    }
}
