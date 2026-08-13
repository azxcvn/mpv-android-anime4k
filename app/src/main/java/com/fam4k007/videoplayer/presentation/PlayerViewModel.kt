package com.fam4k007.videoplayer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.database.PlaybackHistoryEntity
import com.fam4k007.videoplayer.repository.PlayerRepository
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.player.VideoAspect
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import com.fam4k007.videoplayer.VideoFileParcelable
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 重复模式（百分百复用 mpvEx RepeatMode）
 */
enum class RepeatMode {
    OFF,      // 不重复
    ONE,      // 重复当前文件
    ALL       // 重复全部（播放列表）
}

/**
 * Sheet类型枚举（对话框/底部菜单）
 */
enum class Sheets {
    None,
    Speed,           // 播放速度
    AspectRatio,     // 画面比例
    Subtitle,        // 字幕轨道
    AudioTrack,      // 音频轨道
    Danmaku,         // 弹幕设置
    Anime4K,         // Anime4K设置
    Decoder,         // 解码器选择
    More             // 更多设置
}

/**
 * Panel类型枚举（侧边栏面板）
 */
enum class Panels {
    None,
    Playlist,        // 播放列表
    Series,          // 剧集列表
    Settings         // 设置面板
}

/**
 * 播放器更新提示（速度、亮度、音量变化等）
 */
sealed class PlayerUpdates {
    data object None : PlayerUpdates()
    data class Speed(val speed: Double) : PlayerUpdates()
    data class Brightness(val brightness: Float) : PlayerUpdates()
    data class Volume(val volume: Int) : PlayerUpdates()
    data class Seek(val position: Int, val duration: Int) : PlayerUpdates()
}

/**
 * 播放器ViewModel
 * 管理播放器相关的UI状态，调用Repository和Domain层
 * 
 * 阶段1.1: MPV状态直接监听（学习mpvEx）
 * - 使用MPVLib观察者模式监听属性变化
 * - 将MPV状态封装为StateFlow，自动触发UI更新
 * 
 * 阶段1.2: UI状态集中管理
 * - 将所有UI状态变量移到ViewModel
 * - 所有状态使用StateFlow封装
 * - Activity只负责UI渲染和系统集成
 * 
 * 阶段1.3: Manager集成（保留现有架构）
 * - 通过Koin注入无View依赖的Manager（Anime4KManager、SeriesManager）
 * - 需要View的Manager通过initializeManagers()延迟初始化
 * - Manager回调更新ViewModel StateFlow
 */
class PlayerViewModel(
    private val playerRepository: PlayerRepository,
    private val anime4KManager: Anime4KManager
) : ViewModel(), MPVLib.EventObserver {
    
    companion object {
        private const val TAG = "PlayerViewModel"
    }
    
    // ==================== 音量变更事件 ====================
    
    data class VolumeChange(
        val volume: Int,          // 目标音量值 (0-100 或 0-300)
        val volumeBoostEnabled: Boolean  // 是否启用音量增强
    )
    
    // 音量变更事件流（用于通知Activity设置MPV音量）
    private val _volumeChangeEvent = MutableSharedFlow<VolumeChange>(extraBufferCapacity = 1)
    val volumeChangeEvent: SharedFlow<VolumeChange> = _volumeChangeEvent.asSharedFlow()
    
    // ==================== 亮度变更事件 ====================
    
    // 亮度变更事件流（用于通知Activity设置窗口亮度）
    private val _brightnessChangeEvent = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    val brightnessChangeEvent: SharedFlow<Float> = _brightnessChangeEvent.asSharedFlow()
    
    // ==================== Seek事件（用于弹幕同步）====================
    
    // Seek事件流（用于通知Activity同步弹幕进度）
    private val _seekEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val seekEvent: SharedFlow<Int> = _seekEvent.asSharedFlow()
    
    // ==================== 视频切换事件 ====================
    
    // 视频切换事件流（用于通知Activity播放新视频）
    private val _switchVideoEvent = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val switchVideoEvent: SharedFlow<Uri> = _switchVideoEvent.asSharedFlow()
    
    // ==================== MPV 属性状态（阶段1.1）====================
    
    // 播放状态（直接从MPV监听）
    private val _paused = MutableStateFlow<Boolean?>(null)
    val paused: StateFlow<Boolean?> = _paused.asStateFlow()
    
    // 播放位置（秒，整数）
    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()
    
    // 视频时长（秒，整数）
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    // 播放速度
    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    
    // 高精度进度（用于流畅的进度条，约24fps更新）
    private val _precisePosition = MutableStateFlow(0.0)
    val precisePosition: StateFlow<Double> = _precisePosition.asStateFlow()
    
    // 高精度时长
    private val _preciseDuration = MutableStateFlow(0.0)
    val preciseDuration: StateFlow<Double> = _preciseDuration.asStateFlow()
    
    // 字幕轨道列表（TODO: 从track-list解析）
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()
    
    // 音频轨道列表（TODO: 从track-list解析）
    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()
    
    // ==================== UI控制状态（阶段1.2）====================
    
    // 控制面板显示状态
    private val _controlsShown = MutableStateFlow(true)  // 初始显示控制面板
    val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()
    
    // 进度条显示状态
    private val _seekBarShown = MutableStateFlow(false)
    val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()
    
    // 控制栏锁定状态
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()
    
    // 对话框/Sheet状态
    private val _sheetShown = MutableStateFlow(Sheets.None)
    val sheetShown: StateFlow<Sheets> = _sheetShown.asStateFlow()
    
    // 面板状态
    private val _panelShown = MutableStateFlow(Panels.None)
    val panelShown: StateFlow<Panels> = _panelShown.asStateFlow()
    
    // 播放器更新提示（速度、亮度、音量变化）
    private val _playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val playerUpdate: StateFlow<PlayerUpdates> = _playerUpdate.asStateFlow()
    
    // 视频比例模式（从持久化存储读取）
    private val _videoAspect = MutableStateFlow(
        try { VideoAspect.valueOf(playerRepository.getVideoAspect()) } catch (_: Exception) { VideoAspect.FIT }
    )
    val videoAspect: StateFlow<VideoAspect> = _videoAspect.asStateFlow()
    
    // Anime4K 状态
    private val _anime4KEnabled = MutableStateFlow(false)
    val anime4KEnabled: StateFlow<Boolean> = _anime4KEnabled.asStateFlow()
    
    private val _anime4KMode = MutableStateFlow(Anime4KManager.Mode.OFF)
    val anime4KMode: StateFlow<Anime4KManager.Mode> = _anime4KMode.asStateFlow()
    
    private val _anime4KQuality = MutableStateFlow(Anime4KManager.Quality.BALANCED)
    val anime4KQuality: StateFlow<Anime4KManager.Quality> = _anime4KQuality.asStateFlow()
    
    // 弹幕显示状态
    private val _danmakuVisible = MutableStateFlow(true)
    val danmakuVisible: StateFlow<Boolean> = _danmakuVisible.asStateFlow()
    
    // Seek提示文本
    private val _seekText = MutableStateFlow<String?>(null)
    val seekText: StateFlow<String?> = _seekText.asStateFlow()
    
    // Seek指示器显示状态
    private val _seekIndicatorShown = MutableStateFlow(false)
    val seekIndicatorShown: StateFlow<Boolean> = _seekIndicatorShown.asStateFlow()
    
    // Seek偏移量（用于显示+10s/-10s）
    private val _seekOffset = MutableStateFlow(0)
    val seekOffset: StateFlow<Int> = _seekOffset.asStateFlow()
    
    // Seek指示器是否在顶部显示（false=双击手势，在左侧/右侧显示）
    private val _seekIndicatorAtTop = MutableStateFlow(true)
    val seekIndicatorAtTop: StateFlow<Boolean> = _seekIndicatorAtTop.asStateFlow()
    
    // 手势相关状态
    private val _isBrightnessSliderShown = MutableStateFlow(false)
    val isBrightnessSliderShown: StateFlow<Boolean> = _isBrightnessSliderShown.asStateFlow()
    
    private val _isVolumeSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown: StateFlow<Boolean> = _isVolumeSliderShown.asStateFlow()
    
    // 指示器自动隐藏 Job（每次手势时取消上一个，确保停止操作后才隐藏）
    private var brightnessHideJob: Job? = null
    private var volumeHideJob: Job? = null
    private val indicatorHideDelay = 2000L  // 停止手势后 2 秒隐藏
    // 当前亮度（0.0 - 1.0）
    private val _currentBrightness = MutableStateFlow(0.5f)
    val currentBrightness: StateFlow<Float> = _currentBrightness.asStateFlow()
    
    // 当前音量（0 - 100，音量增强开启时 0 - 300）
    private val _currentVolume = MutableStateFlow(50)
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()
    
    // 音量增强状态
    private val _volumeBoostEnabled = MutableStateFlow(false)
    val volumeBoostEnabled: StateFlow<Boolean> = _volumeBoostEnabled.asStateFlow()
    
    // 重复模式（百分百复用 mpvEx RepeatMode）
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // 随机播放状态
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    
    // 自动连播开关（百分百复用 mpvEx autoplayNextVideo）
    private val _autoPlayNextEnabled = MutableStateFlow(true)
    val autoPlayNextEnabled: StateFlow<Boolean> = _autoPlayNextEnabled.asStateFlow()

    // 系列播放状态（百分百复用 mpvEx 算法）
    private val _videoList = MutableStateFlow<List<VideoFileParcelable>>(emptyList())
    val videoList: StateFlow<List<VideoFileParcelable>> = _videoList.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // 播放列表 URI 列表（百分百复用 mpvEx playlist 逻辑）
    private val _playlistUris = MutableStateFlow<List<Uri>>(emptyList())
    val playlistUris: StateFlow<List<Uri>> = _playlistUris.asStateFlow()
    
    // 文件加载完成事件（用于 Activity 重置切换标志）
    private val _fileLoadCompleteEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fileLoadCompleteEvent: SharedFlow<Unit> = _fileLoadCompleteEvent.asSharedFlow()

    // 上下集按钮状态（百分百复用 mpvEx hasNext/hasPrevious 算法）
    // hasNext: list not empty AND (repeat ALL OR index < size - 1)
    val hasPrevious: StateFlow<Boolean> = combine(_currentIndex, _playlistUris, _repeatMode) { idx, list, repeatMode ->
        list.isNotEmpty() && (repeatMode == RepeatMode.ALL || idx > 0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasNext: StateFlow<Boolean> = combine(_currentIndex, _playlistUris, _repeatMode) { idx, list, repeatMode ->
        list.isNotEmpty() && (repeatMode == RepeatMode.ALL || idx < list.size - 1)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 是否应该重复播放列表（百分百复用 mpvEx shouldRepeatPlaylist）
     */
    fun shouldRepeatPlaylist(): Boolean = _repeatMode.value == RepeatMode.ALL

    /**
     * 是否应该重复当前文件（百分百复用 mpvEx shouldRepeatCurrentFile）
     */
    fun shouldRepeatCurrentFile(): Boolean = _repeatMode.value == RepeatMode.ONE
    
    // 硬件解码状态
    private val _isHardwareDecoding = MutableStateFlow(true)
    val isHardwareDecoding: StateFlow<Boolean> = _isHardwareDecoding.asStateFlow()
    
    // 当前播放视频URI
    private val _currentVideoUri = MutableStateFlow<Uri?>(null)
    val currentVideoUri: StateFlow<Uri?> = _currentVideoUri.asStateFlow()

    // 视频标题（顶部面板显示）
    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()
    
    // 快进/快退秒数配置（从 PreferencesManager 加载）
    private val _seekTimeSeconds = MutableStateFlow(5)
    val seekTimeSeconds: StateFlow<Int> = _seekTimeSeconds.asStateFlow()

    // 双击快进/快退秒数（从 PreferencesManager 加载）
    private val _doubleTapSeekSeconds = MutableStateFlow(10)
    val doubleTapSeekSeconds: StateFlow<Int> = _doubleTapSeekSeconds.asStateFlow()

    // 双击模式：0=播放/暂停，1=左右快退/快进（从 PreferencesManager 加载）
    private val _doubleTapMode = MutableStateFlow(0)
    val doubleTapMode: StateFlow<Int> = _doubleTapMode.asStateFlow()

    // 长按倍速（从 PreferencesManager 加载）
    private val _longPressSpeed = MutableStateFlow(2.0f)
    val longPressSpeed: StateFlow<Float> = _longPressSpeed.asStateFlow()

    // 亮度/音量灵敏度（从 PreferencesManager 加载）
    private val _brightnessSensitivity = MutableStateFlow(2.0f)
    val brightnessSensitivity: StateFlow<Float> = _brightnessSensitivity.asStateFlow()
    private val _volumeSensitivity = MutableStateFlow(150f)
    val volumeSensitivity: StateFlow<Float> = _volumeSensitivity.asStateFlow()

    // 进度条样式（从 PreferencesManager 加载）
    private val _seekbarStyle = MutableStateFlow("Standard")
    val seekbarStyle: StateFlow<String> = _seekbarStyle.asStateFlow()

    // 是否正在长按倍速播放（用于显示速度提示浮层）
    private val _isLongPressing = MutableStateFlow(false)
    val isLongPressing: StateFlow<Boolean> = _isLongPressing.asStateFlow()

    // 长按动态调速状态（true = 长按期间正在左右滑动调速）
    private val _isDynamicSpeedActive = MutableStateFlow(false)
    val isDynamicSpeedActive: StateFlow<Boolean> = _isDynamicSpeedActive.asStateFlow()

    // 长按动态调速的速度档位预设
    val dynamicSpeedPresets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

    // 长按倍速提示开关（从 PreferencesManager 加载）
    private val _longPressSpeedHintEnabled = MutableStateFlow(true)
    val longPressSpeedHintEnabled: StateFlow<Boolean> = _longPressSpeedHintEnabled.asStateFlow()

    // 档位列表条可见性（动态调速后自动隐藏，文字提示不受此控制）
    private val _speedPresetBarVisible = MutableStateFlow(false)
    val speedPresetBarVisible: StateFlow<Boolean> = _speedPresetBarVisible.asStateFlow()
    private var speedPresetBarJob: Job? = null

    // 滑动 Seek 预览（null=未在滑动，有值=正在滑动中）
    data class SwipeSeekPreview(val targetSeconds: Int, val deltaSeconds: Int)
    private val _swipeSeekPreview = MutableStateFlow<SwipeSeekPreview?>(null)
    val swipeSeekPreview: StateFlow<SwipeSeekPreview?> = _swipeSeekPreview.asStateFlow()

    // 自定义倍速预设（从 PreferencesManager 加载，排序后的列表）
    private val _customSpeedPresets = MutableStateFlow<List<Float>>(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f))
    val customSpeedPresets: StateFlow<List<Float>> = _customSpeedPresets.asStateFlow()

    // Slider 正在被拖动（拖动期间暂停自动隐藏定时器）
    private val _isSliderDragging = MutableStateFlow(false)
    val isSliderDragging: StateFlow<Boolean> = _isSliderDragging.asStateFlow()

    // ==================== 缩略图预览 ====================

    /** 缩略图专用调度器：单线程顺序处理 */
    private val thumbnailDispatcher = Dispatchers.Default.limitedParallelism(1)

    private var _thumbnailManager: com.fam4k007.videoplayer.manager.VideoThumbnailManager? = null
    private var thumbnailInitialized = false

    private val _thumbnailBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val thumbnailBitmap: StateFlow<android.graphics.Bitmap?> = _thumbnailBitmap.asStateFlow()

    private val _thumbnailTimeSec = MutableStateFlow(-1L)
    val thumbnailTimeSec: StateFlow<Long> = _thumbnailTimeSec.asStateFlow()

    private val _thumbnailLoading = MutableStateFlow(false)
    val thumbnailLoading: StateFlow<Boolean> = _thumbnailLoading.asStateFlow()

    private val _thumbnailFraction = MutableStateFlow(0f)
    val thumbnailFraction: StateFlow<Float> = _thumbnailFraction.asStateFlow()

    // ── Worker 模式 ──
    private var thumbnailRequestId = 0L
    private var thumbnailWorkerJob: Job? = null
    private val thumbnailRequestLock = Any()
    private var pendingThumbnailPosition: Float? = null
    private var pendingThumbnailDuration: Float? = null
    private var lastQueuedThumbnailKey: String? = null
    private var warmedThumbnailSource: String? = null
    private val thumbnailBucketsPerSec = 2f
    private val thumbnailPrefetchRadius = 5

    fun setThumbnailManager(manager: com.fam4k007.videoplayer.manager.VideoThumbnailManager) {
        _thumbnailManager = manager
        // 重置初始化标志，确保新 manager 在 Activity 重建后能被正确初始化
        // PlayerViewModel 是 Koin 单例，Activity 销毁重建时复用同一实例
        thumbnailInitialized = false
        warmedThumbnailSource = null
    }

    fun initializeThumbnail(uri: Uri, durationMs: Long, isWebDav: Boolean = false) {
        if (thumbnailInitialized) return
        _thumbnailManager?.initializeVideo(uri, durationMs, isWebDav)
        thumbnailInitialized = true
    }

    fun onSeekbarDragPosition(positionSec: Float, durationSec: Float) {
        if (!_seekbarThumbnailEnabled.value) return
        val manager = _thumbnailManager ?: return
        if (!manager.isThumbnailSupported()) return
        val sec = positionSec.toLong()
        _thumbnailTimeSec.value = sec
        _thumbnailFraction.value = if (durationSec > 0f) (positionSec / durationSec).coerceIn(0f, 1f) else 0f

        val exactCached = manager.getThumbnailAt(sec)
        if (exactCached != null) { _thumbnailBitmap.value = exactCached; _thumbnailLoading.value = false; return }

        var nearby: android.graphics.Bitmap? = null
        for (offset in 1..3) {
            nearby = manager.getThumbnailAt(sec + offset) ?: manager.getThumbnailAt(sec - offset)
            if (nearby != null) break
        }
        if (nearby != null) { _thumbnailBitmap.value = nearby; _thumbnailLoading.value = false }
        else if (_thumbnailBitmap.value == null) _thumbnailLoading.value = true

        val queueKey = "${(positionSec * thumbnailBucketsPerSec).roundToInt().coerceAtLeast(0)}"
        if (queueKey == lastQueuedThumbnailKey) return
        lastQueuedThumbnailKey = queueKey

        synchronized(thumbnailRequestLock) {
            pendingThumbnailPosition = positionSec
            pendingThumbnailDuration = durationSec
        }
        ensureThumbnailWorker()
    }

    fun onSeekbarDragStart(positionSec: Float) {
        _thumbnailManager?.preloadAroundPosition(positionSec.toLong())
    }

    fun onSeekbarDragEnd() {
        thumbnailRequestId++
        lastQueuedThumbnailKey = null
        synchronized(thumbnailRequestLock) { pendingThumbnailPosition = null; pendingThumbnailDuration = null }
        _thumbnailBitmap.value = null
        _thumbnailLoading.value = false
    }

    fun resetThumbnailState() {
        thumbnailInitialized = false
        _thumbnailManager?.resetForNewVideo()
        thumbnailRequestId++
        lastQueuedThumbnailKey = null
        warmedThumbnailSource = null
        synchronized(thumbnailRequestLock) { pendingThumbnailPosition = null; pendingThumbnailDuration = null }
        _thumbnailBitmap.value = null
        _thumbnailTimeSec.value = -1L
        _thumbnailFraction.value = 0f
        _thumbnailLoading.value = false
    }

    fun warmSeekThumbnailer() {
        if (!_seekbarThumbnailEnabled.value) return
        val manager = _thumbnailManager ?: return
        if (!manager.isThumbnailSupported()) return
        val currentSource = runCatching { MPVLib.getPropertyString("path") }.getOrNull()
        if (currentSource == warmedThumbnailSource) return
        warmedThumbnailSource = currentSource
        val currentPos = precisePosition.value
        if (currentPos > 0f) { manager.warmThumbnail(currentPos.toLong()); Logger.d(TAG, "缩略图预热 @ ${currentPos}s") }
    }

    // ── Worker ──
    private fun ensureThumbnailWorker() {
        if (thumbnailWorkerJob?.isActive == true) return
        thumbnailWorkerJob = viewModelScope.launch(thumbnailDispatcher) {
            while (isActive) {
                val targetSec: Float; val durationSec: Float; val requestId: Long
                synchronized(thumbnailRequestLock) {
                    val p = pendingThumbnailPosition; val d = pendingThumbnailDuration
                    if (p == null || d == null) break
                    pendingThumbnailPosition = null; pendingThumbnailDuration = null
                    targetSec = p; durationSec = d; requestId = ++thumbnailRequestId
                }
                val manager = _thumbnailManager ?: break
                if (!manager.isThumbnailSupported()) break
                val bitmap = manager.extractThumbnailRealtime(targetSec.toLong())
                if (requestId == thumbnailRequestId) {
                    if (bitmap != null) {
                        if (_thumbnailBitmap.value != null || _thumbnailLoading.value) {
                            _thumbnailBitmap.value = bitmap; _thumbnailLoading.value = false
                        }
                    } else _thumbnailLoading.value = false
                }
                if (synchronized(thumbnailRequestLock) { pendingThumbnailPosition == null }) {
                    prefetchNearbyThumbnails(targetSec, durationSec)
                }
            }
        }
    }

    private suspend fun prefetchNearbyThumbnails(centerSec: Float, durationSec: Float) {
        val manager = _thumbnailManager ?: return
        if (!manager.isThumbnailSupported()) return
        val centerBucket = (centerSec * thumbnailBucketsPerSec).roundToInt().coerceAtLeast(0)
        val maxBucket = if (durationSec > 0f) (durationSec * thumbnailBucketsPerSec).roundToInt() else Int.MAX_VALUE
        for (distance in 1..thumbnailPrefetchRadius) {
            synchronized(thumbnailRequestLock) { if (pendingThumbnailPosition != null) return }
            val next = centerBucket + distance
            if (next <= maxBucket) manager.extractThumbnailRealtime((next / thumbnailBucketsPerSec).toLong())
            val prev = centerBucket - distance
            if (prev >= 0) manager.extractThumbnailRealtime((prev / thumbnailBucketsPerSec).toLong())
        }
    }
    
    // 是否为在线视频
    private val _isOnlineVideo = MutableStateFlow(false)
    val isOnlineVideo: StateFlow<Boolean> = _isOnlineVideo.asStateFlow()

    // 实时下载网速（KB/s），仅在线播放时有效
    private val _downloadSpeedKbps = MutableStateFlow(0)
    val downloadSpeedKbps: StateFlow<Int> = _downloadSpeedKbps.asStateFlow()
    
    // 加载动画显示状态（在线视频缓冲/加载时显示）
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 剩余时间显示切换状态（点击进度条右侧时间文本切换为剩余时间/总时长）
    private val _showRemainingTime = MutableStateFlow(false)
    val showRemainingTime: StateFlow<Boolean> = _showRemainingTime.asStateFlow()
    
    /**
     * 切换剩余时间/总时长显示
     */
    fun toggleRemainingTimeDisplay() {
        val newValue = !_showRemainingTime.value
        _showRemainingTime.value = newValue
        playerRepository.setShowRemainingTimeEnabled(newValue)
    }

    // ==================== 章节管理 ====================

    // 章节进度条开关（从 PreferencesManager 加载）
    // GPU Next 状态
    private val _gpuNext = MutableStateFlow(false)
    val gpuNext: StateFlow<Boolean> = _gpuNext.asStateFlow()

    private val _controlsAnimationEnabled = MutableStateFlow(false)
    val controlsAnimationEnabled: StateFlow<Boolean> = _controlsAnimationEnabled.asStateFlow()

    private val _drawerAnimationEnabled = MutableStateFlow(false)
    val drawerAnimationEnabled: StateFlow<Boolean> = _drawerAnimationEnabled.asStateFlow()

    /**
     * 同步动画设置（从 SharedPreferences 重新读取）
     * 在 onResume 中调用，确保用户从设置页返回后动画开关立即生效
     */
    fun syncAnimationSettings() {
        _controlsAnimationEnabled.value = playerRepository.isControlsAnimationEnabled()
        _drawerAnimationEnabled.value = playerRepository.isDrawerAnimationEnabled()
        _longPressSpeedHintEnabled.value = playerRepository.isLongPressSpeedHintEnabled()
        updateGlobalAnimationFlag()
    }

    /**
     * 同步进度条样式（从 SharedPreferences 重新读取）
     * 在 onResume 中调用，确保用户从设置页返回后进度条样式立即生效，无需重启
     */
    fun syncSeekbarStyle() {
        _seekbarStyle.value = playerRepository.getSeekbarStyle()
    }

    private val _chapterBarEnabled = MutableStateFlow(true)
    val chapterBarEnabled: StateFlow<Boolean> = _chapterBarEnabled.asStateFlow()

    private val _seekbarThumbnailEnabled = MutableStateFlow(false)
    val seekbarThumbnailEnabled: StateFlow<Boolean> = _seekbarThumbnailEnabled.asStateFlow()

    /**
     * 章节信息
     */
    data class Chapter(
        val title: String,
        val timeSeconds: Double
    )

    // 章节列表
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    // 当前所在章节索引
    private val _currentChapterIndex = MutableStateFlow(-1)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    // 当前章节名称（无章节时为 null）
    val currentChapterName: StateFlow<String?> = combine(_chapters, _currentChapterIndex) { chapters, index ->
        if (index in chapters.indices) chapters[index].title else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 是否有章节信息
    val hasChapters: StateFlow<Boolean> = _chapters.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 更新章节列表（在文件加载时调用）
     */
    fun updateChapters(chapters: List<Pair<String, Double>>) {
        _chapters.value = chapters.map { (title, time) -> Chapter(title, time) }
        _currentChapterIndex.value = -1
        refreshChapterDerivedSegments()
    }

    /**
     * 根据当前播放位置更新当前章节索引
     */
    fun updateCurrentChapter(positionSeconds: Double) {
        val chapters = _chapters.value
        if (chapters.isEmpty() || positionSeconds < 0) {
            _currentChapterIndex.value = -1
            return
        }
        // 从后往前找，找到最后一个 startTime <= positionSeconds 的章节
        var index = -1
        for (i in chapters.indices) {
            if (chapters[i].timeSeconds <= positionSeconds) {
                index = i
            } else {
                break
            }
        }
        _currentChapterIndex.value = index
    }

    /**
     * 跳转到指定章节
     */
    fun seekToChapter(index: Int) {
        val chapters = _chapters.value
        if (index in chapters.indices) {
            seekTo(chapters[index].timeSeconds.toInt())
        }
    }

    // ==================== OP/ED 片段检测 ====================

    /** 内置关键词（多语言，借鉴 mpvRx） */
    private val introKeywords = listOf(
        "intro", "opening", "op", "op1", "op2", "op3",
        "オープニング", "主題歌", "主题曲", "片头",
        "开幕", "片头曲", "序章", "prologue", "avance",
        "a partire", "开场", "头曲",
    )
    private val outroKeywords = listOf(
        "outro", "ending", "ed", "ed1", "ed2", "ed3",
        "エンディング", "片尾", "结尾", "片尾曲", "尾曲",
        "闭幕", "epilogue", "finale", "credits", "closing",
        "fin", "fine",
    )
    private val recapKeywords = listOf(
        "recap", "summary", "振り返り", "前回", "回顾",
        "复习", "总集", "résumé", "riepilogo", "resumen",
        "前情提要", "上集回顾", "前回まで", "これまで",
    )
    private val creditsKeywords = listOf(
        "credits", "end credits", "staff", "cast",
        "制作", "スタッフ", "出演", "声の出演",
        "creditless", "クレジット",
    )
    /** 片头前段（avant / アバンタイトル）关键词：正片开始、OP 之前的一小段正片内容，不是下集预告 */
    private val coldOpenKeywords = listOf(
        "avant", "アバン", "アバンタイトル", "冷开场", "正片前段", "片头前段",
    )
    private val previewKeywords = listOf(
        "preview", "next episode", "次回予告", "予告",
        "预告", "下集", "次回", "次巻", "preview",
        "次回预告", "先行", "trailer",
    )

    /** 检测到的跳过片段列表 */
    private val _skipSegments = MutableStateFlow<List<com.fam4k007.videoplayer.player.SkipSegment>>(emptyList())
    val skipSegments: StateFlow<List<com.fam4k007.videoplayer.player.SkipSegment>> = _skipSegments.asStateFlow()

    /** 当前播放位置所在的可跳过片段 */
    private val _currentSkippableSegment = MutableStateFlow<com.fam4k007.videoplayer.player.SkipSegment?>(null)
    val currentSkippableSegment: StateFlow<com.fam4k007.videoplayer.player.SkipSegment?> = _currentSkippableSegment.asStateFlow()

    /** 是否自动显示 skip 按钮 */
    private val _showSkipChip = MutableStateFlow(false)
    val showSkipChip: StateFlow<Boolean> = _showSkipChip.asStateFlow()

    /** 已跳过的片段类型（防止重复跳过） */
    private val skippedSegmentTypes = mutableSetOf<com.fam4k007.videoplayer.player.SkipSegmentType>()

    /** 章节 OP/ED 检测开关 */
    private val _chapterSkipDetectionEnabled = MutableStateFlow(true)
    val chapterSkipDetectionEnabled: StateFlow<Boolean> = _chapterSkipDetectionEnabled.asStateFlow()

    /** 番剧官方 OP/ED 片段（来自 bilibili playurl 的 clip_info_list，精确时间戳，优先级高于章节关键词检测） */
    private var officialSkipSegments: List<com.fam4k007.videoplayer.player.SkipSegment> = emptyList()

    /**
     * 注入番剧官方 OP/ED 跳过片段（bilibili clip_info_list）
     * 在线番剧播放时由播放器在视频加载后调用；传空列表表示清除（例如切回本地视频）
     */
    fun setOfficialSkipSegments(segments: List<com.fam4k007.videoplayer.player.SkipSegment>) {
        officialSkipSegments = segments
        refreshSkipSegments()
        Logger.d(TAG, "Official OP/ED clips set: ${segments.size} segments")
    }

    /**
     * 重新计算当前生效的跳过片段
     * 番剧官方 OP/ED 优先，其次章节关键词检测
     */
    private fun refreshSkipSegments() {
        if (officialSkipSegments.isNotEmpty()) {
            _skipSegments.value = officialSkipSegments
            skippedSegmentTypes.clear()
        } else {
            refreshChapterDerivedSegments()
        }
    }

    fun toggleChapterSkipDetection() {
        _chapterSkipDetectionEnabled.value = !_chapterSkipDetectionEnabled.value
        if (!_chapterSkipDetectionEnabled.value) {
            _skipSegments.value = emptyList()
            _currentSkippableSegment.value = null
            _showSkipChip.value = false
            skippedSegmentTypes.clear()
        } else {
            refreshChapterDerivedSegments()
        }
    }

    /** 当章节列表更新后重新检测 OP/ED */
    fun refreshChapterDerivedSegments() {
        if (!_chapterSkipDetectionEnabled.value) return
        // 番剧官方 OP/ED 片段优先，存在时不使用章节关键词检测覆盖
        if (officialSkipSegments.isNotEmpty()) return
        val chapterList = _chapters.value
        val dur = _duration.value.toDouble()
        if (dur <= 0.0 || chapterList.isEmpty()) {
            _skipSegments.value = emptyList()
            return
        }
        val derived = chapterList.mapIndexedNotNull { index, chapter ->
            val type = chapterTitleToType(chapter.title) ?: return@mapIndexedNotNull null
            val start = chapter.timeSeconds
            val end = chapterList.getOrNull(index + 1)?.timeSeconds ?: dur
            val normalizedEnd = end.coerceAtMost(dur)
            if (normalizedEnd - start < 5.0) return@mapIndexedNotNull null
            val fraction = start / dur
            when (type) {
                com.fam4k007.videoplayer.player.SkipSegmentType.INTRO ->
                    if (fraction > 0.5) return@mapIndexedNotNull null
                com.fam4k007.videoplayer.player.SkipSegmentType.OUTRO ->
                    if (fraction < 0.4) return@mapIndexedNotNull null
                else -> {}
            }
            com.fam4k007.videoplayer.player.SkipSegment(type, start, normalizedEnd, "chapter")
        }
        _skipSegments.value = derived.distinctBy { Triple(it.type, it.startSeconds.toInt(), it.endSeconds.toInt()) }
        skippedSegmentTypes.clear()
    }

    /** 关键词匹配章节标题 → 片段类型 */
    private fun chapterTitleToType(title: String): com.fam4k007.videoplayer.player.SkipSegmentType? {
        val lowered = title.trim().lowercase()
        if (lowered.isBlank()) return null
        val normalizedLatin = lowered.replace(Regex("""[^a-z0-9]+"""), " ").trim()
        val compactLatin = normalizedLatin.replace(" ", "")
        val compactRaw = lowered.replace(Regex("""[\s\p{Punct}・_]+"""), "")

        fun hasKeyword(keywords: List<String>): Boolean = keywords.any { kw ->
            val k = kw.lowercase()
            when {
                k.matches(Regex("""[a-z0-9]+""")) ->
                    normalizedLatin.contains(Regex("""(?:^|\s)${Regex.escape(k)}(?:\s|$)""")) ||
                        (k.length >= 4 && compactLatin.contains(k))
                else -> compactRaw.contains(k.replace(" ", ""))
            }
        }

        val introOk = hasKeyword(introKeywords)
        val outroOk = hasKeyword(outroKeywords)

        return when {
            hasKeyword(recapKeywords) -> com.fam4k007.videoplayer.player.SkipSegmentType.RECAP
            hasKeyword(coldOpenKeywords) && !introOk && !outroOk ->
                com.fam4k007.videoplayer.player.SkipSegmentType.COLD_OPEN
            hasKeyword(creditsKeywords) && !introOk && !outroOk ->
                com.fam4k007.videoplayer.player.SkipSegmentType.CREDITS
            hasKeyword(previewKeywords) && !introOk && !outroOk ->
                com.fam4k007.videoplayer.player.SkipSegmentType.PREVIEW
            outroOk && !introOk -> com.fam4k007.videoplayer.player.SkipSegmentType.OUTRO
            introOk -> com.fam4k007.videoplayer.player.SkipSegmentType.INTRO
            else -> null
        }
    }

    /** 跳过当前所在片段 */
    fun skipActiveSegment() {
        val segment = _currentSkippableSegment.value ?: return
        skippedSegmentTypes.add(segment.type)
        seekTo(segment.endSeconds.toInt())
        _currentSkippableSegment.value = null
        _showSkipChip.value = false
        Logger.d(TAG, "跳过${segment.type.label} @ ${segment.startSeconds}-${segment.endSeconds}s")
    }

    /** 轮询中检查是否需要显示 skip 按钮 */
    private fun updateSkipChipState(positionSeconds: Double) {
        val segments = _skipSegments.value
        if (segments.isEmpty()) { _currentSkippableSegment.value = null; _showSkipChip.value = false; return }

        // 如果用户回拖到已跳过片段之前，清除标记允许重新跳过
        skippedSegmentTypes.toList().forEach { type ->
            val seg = segments.firstOrNull { it.type == type } ?: return@forEach
            if (positionSeconds < seg.startSeconds) {
                skippedSegmentTypes.remove(type)
            }
        }

        val active = segments.firstOrNull { pos ->
            pos.isValid && pos.type !in skippedSegmentTypes &&
                positionSeconds in pos.startSeconds..pos.endSeconds &&
                (pos.endSeconds - positionSeconds) >= 1.0
        }
        _currentSkippableSegment.value = active
        _showSkipChip.value = active != null  // 只要在片段内就持续显示
    }

    // 保存的播放位置（用于恢复播放）
    private val _savedPosition = MutableStateFlow(0.0)
    val savedPosition: StateFlow<Double> = _savedPosition.asStateFlow()

    // 恢复进度 Toast 显示状态
    private val _resumeToastVisible = MutableStateFlow(false)
    val resumeToastVisible: StateFlow<Boolean> = _resumeToastVisible.asStateFlow()
    
    // 长按前的速度（用于松开后恢复）
    private val _speedBeforeLongPress = MutableStateFlow(1.0)
    val speedBeforeLongPress: StateFlow<Double> = _speedBeforeLongPress.asStateFlow()
    
    // 是否已自动加载字幕
    private val _hasAutoLoadedSubtitle = MutableStateFlow(false)
    val hasAutoLoadedSubtitle: StateFlow<Boolean> = _hasAutoLoadedSubtitle.asStateFlow()
    
    // 当前视频所在文件夹路径
    private val _currentFolderPath = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderPath.asStateFlow()
    
    // 是否从主页继续播放进入
    private val _isFromHomeContinue = MutableStateFlow(false)
    val isFromHomeContinue: StateFlow<Boolean> = _isFromHomeContinue.asStateFlow()
    
    // 从列表传入的播放位置
    private val _lastPlaybackPosition = MutableStateFlow(0L)
    val lastPlaybackPosition: StateFlow<Long> = _lastPlaybackPosition.asStateFlow()
    
    // 位置恢复相关标志
    private val _hasRestoredPosition = MutableStateFlow(false)
    val hasRestoredPosition: StateFlow<Boolean> = _hasRestoredPosition.asStateFlow()
    
    private val _hasShownPrompt = MutableStateFlow(false)
    val hasShownPrompt: StateFlow<Boolean> = _hasShownPrompt.asStateFlow()
    
    // 缓冲检测相关
    private val _lastPositionForBuffering = MutableStateFlow(0.0)
    val lastPositionForBuffering: StateFlow<Double> = _lastPositionForBuffering.asStateFlow()
    
    private val _lastPositionUpdateTime = MutableStateFlow(0L)
    val lastPositionUpdateTime: StateFlow<Long> = _lastPositionUpdateTime.asStateFlow()
    
    private val _isStalledBuffering = MutableStateFlow(false)
    val isStalledBuffering: StateFlow<Boolean> = _isStalledBuffering.asStateFlow()
    
    // 播放状态跟踪
    private val _previousIsPlaying = MutableStateFlow(false)
    val previousIsPlaying: StateFlow<Boolean> = _previousIsPlaying.asStateFlow()
    
    // 系列播放相关
    private val _currentSeries = MutableStateFlow<List<Uri>>(emptyList())
    val currentSeries: StateFlow<List<Uri>> = _currentSeries.asStateFlow()
    
    // 手势相关状态
    private val _pendingSeekPosition = MutableStateFlow<Int?>(null)
    val pendingSeekPosition: StateFlow<Int?> = _pendingSeekPosition.asStateFlow()
    
    private val _gestureStartPosition = MutableStateFlow(0)
    val gestureStartPosition: StateFlow<Int> = _gestureStartPosition.asStateFlow()
    
    // ==================== 内部状态 ====================
    
    // MPV观察者是否已注册
    private var mpvObserverRegistered = false
    
    // MPV初始化标志
    private var mpvInitialized = false
    
    init {
        // 从 PreferencesManager 加载播放器设置
        _seekTimeSeconds.value = playerRepository.getSeekTimeSeconds()
        _doubleTapSeekSeconds.value = playerRepository.getDoubleTapSeekSeconds()
        _doubleTapMode.value = playerRepository.getDoubleTapMode()
        _longPressSpeed.value = playerRepository.getLongPressSpeed()
        _brightnessSensitivity.value = playerRepository.getBrightnessSensitivity()
        _volumeSensitivity.value = playerRepository.getVolumeSensitivity()
        _seekbarStyle.value = playerRepository.getSeekbarStyle()
        val rawPresets = playerRepository.getCustomSpeedPresets()
        val parsedPresets = rawPresets
            .mapNotNull { it.toFloatOrNull() }
            .sorted()
            .takeIf { it.isNotEmpty() }
            ?: listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        _customSpeedPresets.value = parsedPresets
        // 加载剩余时间显示偏好
        _showRemainingTime.value = playerRepository.isShowRemainingTimeEnabled()
        // 加载章节进度条开关
        _chapterBarEnabled.value = playerRepository.isChapterBarEnabled()
        // 加载缩略图预览开关
        _seekbarThumbnailEnabled.value = playerRepository.isSeekbarThumbnailEnabled()
        // 加载章节 OP/ED 检测开关
        _chapterSkipDetectionEnabled.value = playerRepository.isChapterSkipDetectionEnabled()
        // 加载 GPU Next 状态
        _gpuNext.value = playerRepository.getGpuNext()
        // 加载播放界面动画开关
        _controlsAnimationEnabled.value = playerRepository.isControlsAnimationEnabled()
        // 加载抽屉界面动画开关
        _drawerAnimationEnabled.value = playerRepository.isDrawerAnimationEnabled()
        // 加载长按倍速提示开关
        _longPressSpeedHintEnabled.value = playerRepository.isLongPressSpeedHintEnabled()
    }
    
    // 轮询协程的Job
    private var positionPollingJob: Job? = null
    private var durationPollingJob: Job? = null
    
    /**
     * 启动MPV轮询协程
     * 应在MPV初始化后调用
     */
    private fun startMPVPolling() {
        if (positionPollingJob?.isActive == true) {
            Logger.w(TAG, "Position polling already started")
            return
        }
        
        // 轮询更新高精度位置（约24fps）
        positionPollingJob = viewModelScope.launch {
            while (isActive && mpvInitialized) {
                try {
                    MPVLib.getPropertyDouble("time-pos")?.let {
                        _precisePosition.value = it
                        updateCurrentChapter(it)
                        updateSkipChipState(it)
                    }
                } catch (e: Exception) {
                    // 忽略错误，可能MPV还未初始化
                }
                delay(42) // ~24fps
            }
        }
        
        // 轮询更新网速（仅在线视频）
        viewModelScope.launch {
            while (isActive && mpvInitialized) {
                if (_isOnlineVideo.value) {
                    try {
                        // cache-speed 返回的是字节/秒，除以 1024 转 KB/s
                        val speedBytes = MPVLib.getPropertyInt("cache-speed") ?: 0
                        _downloadSpeedKbps.value = speedBytes / 1024
                    } catch (_: Exception) {
                        _downloadSpeedKbps.value = 0
                    }
                } else {
                    if (_downloadSpeedKbps.value != 0) _downloadSpeedKbps.value = 0
                }
                delay(1000)
            }
        }

        // 轮询更新高精度时长
        durationPollingJob = viewModelScope.launch {
            var lastDuration = 0
            while (isActive && mpvInitialized) {
                try {
                    val intDuration = MPVLib.getPropertyInt("duration") ?: 0
                    if (intDuration != lastDuration) {
                        lastDuration = intDuration
                        // 同步更新 _duration（修复某些设备 observeProperty 不触发导致 duration=0）
                        if (intDuration > 0 && intDuration != _duration.value) {
                            _duration.value = intDuration
                        }
                        MPVLib.getPropertyDouble("duration")?.let { dur ->
                            if (dur > 0) {
                                _preciseDuration.value = dur
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
                delay(1000) // 每秒检查一次
            }
        }
        
        Logger.d(TAG, "MPV polling started")
    }
    
    /**
     * 注册MPV观察者
     * 应在MPV初始化后调用
     */
    fun registerMPVObservers() {
        if (mpvObserverRegistered) {
            Logger.w(TAG, "MPV observers already registered")
            return
        }
        
        // 标记MPV已初始化
        mpvInitialized = true
        
        // 启动轮询协程
        startMPVPolling()
        
        try {
            // 注册事件观察者
            MPVLib.addObserver(this)
            
            // 观察属性变化
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            
            // 观察track-list用于获取字幕/音频轨道
            MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NODE)
            
            mpvObserverRegistered = true
            Logger.d(TAG, "MPV observers registered successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register MPV observers", e)
        }
    }
    
    /**
     * 注销MPV观察者
     * 应在ViewModel清理时调用
     */
    fun unregisterMPVObservers() {
        if (!mpvObserverRegistered) return
        
        // 取消轮询协程
        positionPollingJob?.cancel()
        durationPollingJob?.cancel()
        positionPollingJob = null
        durationPollingJob = null
        
        // 标记MPV未初始化
        mpvInitialized = false
        
        try {
            MPVLib.removeObserver(this)
            mpvObserverRegistered = false
            Logger.d(TAG, "MPV observers unregistered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister MPV observers", e)
        }
    }
    
    fun cleanup() {
        positionPollingJob?.cancel()
        durationPollingJob?.cancel()
        positionPollingJob = null
        durationPollingJob = null
        mpvInitialized = false
        mpvObserverRegistered = false
    }

    fun resetPlaybackState() {
        _position.value = 0
        _duration.value = 0
        _paused.value = false
    }

    // ==================== MPVLib.EventObserver 实现 ====================

    override fun eventProperty(property: String) {
        Logger.v(TAG, "MPV property changed: $property")
    }
    
    override fun eventProperty(property: String, value: Long) {
        // 整数属性变化
        Logger.v(TAG, "MPV property $property = $value")
        when (property) {
            "time-pos" -> _position.value = value.toInt()
            "duration" -> _duration.value = value.toInt()
        }
    }
    
    override fun eventProperty(property: String, value: Boolean) {
        // 布尔属性变化
        Logger.v(TAG, "MPV property $property = $value")
        when (property) {
            "pause" -> _paused.value = value
        }
    }
    
    override fun eventProperty(property: String, value: String) {
        // 字符串属性变化
        Logger.v(TAG, "MPV property $property = $value")
    }
    
    override fun eventProperty(property: String, value: Double) {
        // 浮点数属性变化
        Logger.v(TAG, "MPV property $property = $value")
        when (property) {
            "speed" -> _speed.value = value.toFloat()
        }
    }
    
    override fun eventProperty(property: String, value: MPVNode) {
        // 复杂类型属性变化（如track-list）
        Logger.v(TAG, "MPV property $property = MPVNode")
        when (property) {
            "track-list" -> parseTrackList(value)
        }
    }
    
    /**
     * 解析MPV的track-list节点
     * track-list是一个数组，每个元素是一个map包含轨道信息
     */
    private fun parseTrackList(node: MPVNode) {
        try {
            // track-list是ArrayNode类型
            if (node !is MPVNode.ArrayNode) {
                Logger.w(TAG, "track-list is not ArrayNode: ${node::class.simpleName}")
                return
            }
            
            val subtitles = mutableListOf<SubtitleTrack>()
            val audios = mutableListOf<AudioTrack>()
            
            // 遍历每个轨道
            for (trackNode in node.value) {
                if (trackNode !is MPVNode.MapNode) continue
                
                val trackMap = trackNode.value
                
                // 提取轨道信息
                val id = (trackMap["id"] as? MPVNode.IntNode)?.value?.toInt() ?: continue
                val type = (trackMap["type"] as? MPVNode.StringNode)?.value ?: continue
                val lang = (trackMap["lang"] as? MPVNode.StringNode)?.value
                val title = (trackMap["title"] as? MPVNode.StringNode)?.value
                val selected = (trackMap["selected"] as? MPVNode.BooleanNode)?.value ?: false
                val external = (trackMap["external"] as? MPVNode.BooleanNode)?.value ?: false
                
                // 根据类型分类
                when (type) {
                    "sub" -> {
                        subtitles.add(
                            SubtitleTrack(
                                id = id,
                                lang = lang,
                                title = title,
                                type = type,
                                selected = selected,
                                external = external
                            )
                        )
                    }
                    "audio" -> {
                        val codec = (trackMap["codec"] as? MPVNode.StringNode)?.value
                        audios.add(
                            AudioTrack(
                                id = id,
                                lang = lang,
                                title = title,
                                type = type,
                                selected = selected,
                                codec = codec
                            )
                        )
                    }
                }
            }
            
            // 更新StateFlow
            _subtitleTracks.value = subtitles
            _audioTracks.value = audios
            
            Logger.d(TAG, "Parsed track-list: ${subtitles.size} subtitle tracks, ${audios.size} audio tracks")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse track-list", e)
        }
    }
    
    /**
     * 手动刷新音频/字幕轨道列表
     * 用于外部添加音轨后立即更新UI
     */
    fun refreshTrackList() {
        try {
            val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
            val currentAudioId = MPVLib.getPropertyInt("aid") ?: -1
            val currentSubId = MPVLib.getPropertyInt("sid") ?: -1
            
            val subtitles = mutableListOf<SubtitleTrack>()
            val audios = mutableListOf<AudioTrack>()
            
            for (i in 0 until trackCount) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                val lang = MPVLib.getPropertyString("track-list/$i/lang")
                val title = MPVLib.getPropertyString("track-list/$i/title")
                val codec = MPVLib.getPropertyString("track-list/$i/codec")
                val external = MPVLib.getPropertyString("track-list/$i/external")?.toBooleanStrictOrNull() ?: false
                
                when (type) {
                    "sub" -> {
                        subtitles.add(SubtitleTrack(
                            id = id,
                            lang = lang,
                            title = title,
                            type = type,
                            selected = id == currentSubId,
                            external = external
                        ))
                    }
                    "audio" -> {
                        audios.add(AudioTrack(
                            id = id,
                            lang = lang,
                            title = title,
                            type = type,
                            selected = id == currentAudioId,
                            codec = codec
                        ))
                    }
                }
            }
            
            _subtitleTracks.value = subtitles
            _audioTracks.value = audios
            
            Logger.d(TAG, "Refreshed track-list: ${subtitles.size} subtitle tracks, ${audios.size} audio tracks")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to refresh track-list", e)
        }
    }
    
    override fun event(eventId: Int, data: MPVNode) {
        // MPV事件（文件加载、播放结束等）
        when (eventId) {
            21 -> { // MPV_EVENT_PLAYBACK_RESTART
                Logger.d(TAG, "MPV: Playback restarted")
            }
            6 -> { // MPV_EVENT_FILE_LOADED
                Logger.d(TAG, "MPV: File loaded")
                _fileLoadCompleteEvent.tryEmit(Unit)
            }
            7 -> { // MPV_EVENT_END_FILE
                Logger.d(TAG, "MPV: End of file")
            }
        }
    }
    
    // ==================== UI状态操作方法（阶段1.2）====================
    
    // 自动隐藏定时器
    private var hideControlsJob: Job? = null
    
    /**
     * 切换控制栏显示/隐藏
     */
    fun toggleControls() {
        if (_controlsShown.value) {
            hideControls()
        } else {
            showControls()
        }
    }
    
    /**
     * 显示控制栏
     */
    fun showControls() {
        _controlsShown.value = true
        Logger.v(TAG, "Controls shown")
        resetAutoHideTimer()
    }
    
    /**
     * 隐藏控制栏
     */
    fun hideControls() {
        _controlsShown.value = false
        Logger.v(TAG, "Controls hidden")
        cancelAutoHideTimer()
    }
    
    /**
     * 重置自动隐藏定时器（5秒后自动隐藏）
     * Slider 拖动期间不启动定时器
     */
    fun resetAutoHideTimer() {
        cancelAutoHideTimer()
        // 只在播放中、控制面板可见、且 Slider 未被拖动时启动定时器
        if (_controlsShown.value && paused.value != true && !_isSliderDragging.value) {
            hideControlsJob = viewModelScope.launch {
                delay(5000) // 5秒后自动隐藏
                if (_controlsShown.value && !_isSliderDragging.value) {
                    hideControls()
                }
            }
        }
    }

    /**
     * 设置 Slider 拖动状态
     * 拖动时停止自动隐藏定时器，拖动结束后重新启动
     */
    fun setSliderDragging(isDragging: Boolean) {
        _isSliderDragging.value = isDragging
        if (isDragging) {
            cancelAutoHideTimer()
        } else {
            resetAutoHideTimer()
        }
    }
    
    /**
     * 取消自动隐藏定时器
     */
    private fun cancelAutoHideTimer() {
        hideControlsJob?.cancel()
        hideControlsJob = null
    }
    
    /**
     * 切换控制栏锁定状态
     */
    fun toggleLock() {
        val newLocked = !_areControlsLocked.value
        _areControlsLocked.value = newLocked
        if (newLocked) {
            // 锁定时自动显示解锁按钮
            showUnlockButtons()
        } else {
            // 解锁时隐藏解锁按钮并取消自动隐藏
            _unlockButtonsVisible.value = false
        }
        Logger.v(TAG, "Controls ${if (newLocked) "locked" else "unlocked"}")
    }
    
    // 解锁按钮显示状态
    private val _unlockButtonsVisible = MutableStateFlow(false)
    val unlockButtonsVisible: StateFlow<Boolean> = _unlockButtonsVisible.asStateFlow()
    
    // 解锁按钮自动隐藏 Job
    private var unlockButtonsHideJob: Job? = null
    
    /**
     * 触发解锁按钮显示/隐藏（锁定状态下单击屏幕时调用）
     * 点击时切换显示状态；显示时自动 3 秒后隐藏
     */
    fun triggerUnlockButtons() {
        if (_unlockButtonsVisible.value) {
            // 当前可见 → 隐藏
            _unlockButtonsVisible.value = false
            unlockButtonsHideJob?.cancel()
        } else {
            // 当前隐藏 → 显示并启动自动隐藏
            showUnlockButtons()
        }
    }
    
    private fun showUnlockButtons() {
        _unlockButtonsVisible.value = true
        unlockButtonsHideJob?.cancel()
        unlockButtonsHideJob = viewModelScope.launch {
            delay(3_000L)
            _unlockButtonsVisible.value = false
        }
    }
    
    /**
     * 设置控制栏锁定状态
     */
    fun setControlsLocked(locked: Boolean) {
        _areControlsLocked.value = locked
    }
    
    /**
     * 显示Sheet对话框
     */
    fun showSheet(sheet: Sheets) {
        _sheetShown.value = sheet
        Logger.v(TAG, "Show sheet: $sheet")
    }
    
    /**
     * 关闭Sheet对话框
     */
    fun dismissSheet() {
        _sheetShown.value = Sheets.None
    }
    
    /**
     * 显示Panel面板
     */
    fun showPanel(panel: Panels) {
        _panelShown.value = panel
        Logger.v(TAG, "Show panel: $panel")
    }
    
    /**
     * 关闭Panel面板
     */
    fun dismissPanel() {
        _panelShown.value = Panels.None
    }
    
    /**
     * 显示播放器更新提示
     */
    fun showPlayerUpdate(update: PlayerUpdates) {
        _playerUpdate.value = update
        // 自动清除提示（2秒后）
        viewModelScope.launch {
            delay(2000)
            if (_playerUpdate.value == update) {
                _playerUpdate.value = PlayerUpdates.None
            }
        }
    }
    
    /**
     * 设置视频比例
     */
    fun setVideoAspect(aspect: VideoAspect) {
        _videoAspect.value = aspect
        playerRepository.setVideoAspect(aspect.name)
        Logger.d(TAG, "Video aspect changed to: ${aspect.displayName}")
    }
    
    /**
     * 切换弹幕显示/隐藏
     */
    fun toggleDanmaku() {
        _danmakuVisible.value = !_danmakuVisible.value
        Logger.v(TAG, "Danmaku ${if (_danmakuVisible.value) "visible" else "hidden"}")
    }
    
    // 亮度浮点累加器（小幅度调节时累积到 0.01 再更新）
    private var brightnessAccumulator = 0f
    private var lastBrightnessEmitValue = -1f

    /**
     * 调节亮度
     * @param delta 亮度变化量（浮点累加，避免小幅度丢失）
     * 注意：只调节当前窗口亮度，不修改系统亮度设置。退出播放器自动恢复。
     */
    fun adjustBrightness(delta: Float) {
        brightnessAccumulator += delta
        val intDelta = (brightnessAccumulator * 100f).toInt()
        if (intDelta != 0) {
            brightnessAccumulator -= intDelta / 100f
            val newBrightness = (_currentBrightness.value + intDelta / 100f).coerceIn(0f, 1f)
            _currentBrightness.value = newBrightness
            _isBrightnessSliderShown.value = true
            
            // 避免重复发送相同值
            if (kotlin.math.abs(newBrightness - lastBrightnessEmitValue) > 0.005f) {
                lastBrightnessEmitValue = newBrightness
                _brightnessChangeEvent.tryEmit(newBrightness)
                Logger.d(TAG, "Brightness adjusted to: $newBrightness")
            }
        }
        _isBrightnessSliderShown.value = true
        
        // 取消上一个隐藏任务，重新计时 — 停止操作后 2 秒才隐藏
        brightnessHideJob?.cancel()
        brightnessHideJob = viewModelScope.launch {
            delay(indicatorHideDelay)
            _isBrightnessSliderShown.value = false
        }
    }
    
    /**
     * 设置初始亮度（从系统获取）
     */
    fun setInitialBrightness(brightness: Float) {
        _currentBrightness.value = brightness.coerceIn(0f, 1f)
        Logger.d(TAG, "Initial brightness set to: $brightness")
    }
    
    /**
     * 设置音量增强开关状态
     */
    fun setVolumeBoostEnabled(enabled: Boolean) {
        _volumeBoostEnabled.value = enabled
        Logger.d(TAG, "Volume boost enabled: $enabled")
    }
    
    /**
     * 设置初始音量（从系统获取）
     */
    fun setInitialVolume(volume: Int) {
        val maxVol = if (_volumeBoostEnabled.value) 300 else 100
        _currentVolume.value = volume.coerceIn(0, maxVol)
        Logger.d(TAG, "Initial volume set to: ${_currentVolume.value}")
    }
    
    // 音量浮点累加器（解决 toInt() 截断导致小幅度调节不响应的问题）
    private var volumeAccumulator = 0f

    /**
     * 调节音量
     * @param delta 音量变化量（浮点累加，避免 toInt() 截断丢失小幅度调节）
     * 注意：只调节MPV音量，不修改系统音量。退出播放器时由domain GestureHandler自动恢复。
     */
    fun adjustVolume(delta: Float) {
        volumeAccumulator += delta
        val intDelta = volumeAccumulator.toInt()
        if (intDelta != 0) {
            volumeAccumulator -= intDelta
            val maxVol = if (_volumeBoostEnabled.value) 300 else 100
            val newVolume = (_currentVolume.value + intDelta).coerceIn(0, maxVol)
            if (newVolume != _currentVolume.value) {
                _currentVolume.value = newVolume
                _volumeChangeEvent.tryEmit(VolumeChange(newVolume, _volumeBoostEnabled.value))
                Logger.d(TAG, "Volume adjusted to: $newVolume (boost: ${_volumeBoostEnabled.value})")
            }
        }
        _isVolumeSliderShown.value = true
        
        // 取消上一个隐藏任务，重新计时 — 停止操作后 2 秒才隐藏
        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(indicatorHideDelay)
            _isVolumeSliderShown.value = false
        }
    }
    
    /**
     * 设置弹幕显示状态
     */
    fun setDanmakuVisible(visible: Boolean) {
        _danmakuVisible.value = visible
    }
    
    /**
     * 设置Anime4K状态
     */
    fun setAnime4K(enabled: Boolean, mode: Anime4KManager.Mode, quality: Anime4KManager.Quality) {
        _anime4KEnabled.value = enabled
        _anime4KMode.value = mode
        _anime4KQuality.value = quality
        // 超分开启或抽屉动画关闭时全局禁用动画
        updateGlobalAnimationFlag()
        Logger.d(TAG, "Anime4K: enabled=$enabled, mode=$mode, quality=$quality")
    }

    private fun updateGlobalAnimationFlag() {
        val anime4KActive = _anime4KEnabled.value && _anime4KMode.value != Anime4KManager.Mode.OFF
        val drawerAnimEnabled = _drawerAnimationEnabled.value
        com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager.globalDisableAnimations =
            anime4KActive || !drawerAnimEnabled
    }
    
    /**
     * 设置超分质量等级
     */
    fun setAnime4KQuality(quality: Anime4KManager.Quality) {
        _anime4KQuality.value = quality
        Logger.d(TAG, "Anime4K quality set: $quality")
    }

    /**
     * 切换Anime4K开关
     */
    fun toggleAnime4K() {
        _anime4KEnabled.value = !_anime4KEnabled.value
        Logger.v(TAG, "Anime4K ${if (_anime4KEnabled.value) "enabled" else "disabled"}")
    }
    
    /**
     * 设置Seek提示文本
     */
    fun setSeekText(text: String?) {
        _seekText.value = text
    }
    
    /**
     * 显示亮度滑块
     */
    fun showBrightnessSlider(show: Boolean) {
        _isBrightnessSliderShown.value = show
    }
    
    /**
     * 显示音量滑块
     */
    fun showVolumeSlider(show: Boolean) {
        _isVolumeSliderShown.value = show
    }
    
    /**
     * 设置视频列表（百分百复用 mpvEx 算法 - 从 VideoFileParcelable 列表设置）
     */
    fun setVideoList(list: List<VideoFileParcelable>, currentIndex: Int = 0) {
        _videoList.value = list
        _currentIndex.value = currentIndex
        _playlistUris.value = list.map { Uri.parse(it.uri) }
        Logger.d(TAG, "Video list set: ${list.size} videos, current index: $currentIndex")
    }

    /**
     * 同步 Activity 的播放列表到 ViewModel（百分百复用 mpvEx 算法）
     */
    fun syncPlaylistFromActivity(uriList: List<Uri>, currentIndex: Int) {
        _playlistUris.value = uriList
        _currentIndex.value = currentIndex
        // 同步 VideoFileParcelable 列表
        _videoList.value = uriList.map { uri ->
            VideoFileParcelable(uri.toString(), uri.lastPathSegment ?: uri.toString(), "", 0L, 0L, 0L)
        }
        Logger.d(TAG, "syncPlaylistFromActivity: ${uriList.size} videos, index=$currentIndex")
    }

    /**
     * 同步 Activity 的播放列表索引到 ViewModel（百分百复用 mpvEx 算法）
     */
    fun syncPlaylistIndex(index: Int) {
        _currentIndex.value = index
        Logger.d(TAG, "syncPlaylistIndex: $index")
    }
    
    /**
     * 切换到下一个视频（百分百复用 mpvEx playNext 算法）
     * 委托给 Activity 执行实际的播放切换和索引更新
     */
    fun nextVideo() {
        val uris = _playlistUris.value
        val idx = _currentIndex.value
        Logger.d(TAG, "nextVideo - idx=$idx, size=${uris.size}")

        val effectiveSize = uris.size

        if (_shuffleEnabled.value) {
            // 随机播放模式
            if (_shuffleIndices.value.isEmpty() && uris.isNotEmpty()) {
                generateShuffleIndicesInternal()
            }
            val shuffled = _shuffleIndices.value
            val shuffledPos = _shuffledPosition.value
            if (shuffledPos < shuffled.size - 1) {
                val newPos = shuffledPos + 1
                _shuffledPosition.value = newPos
                val newIndex = shuffled[newPos]
                _currentIndex.value = newIndex
                val uri = uris[newIndex]
                Logger.d(TAG, "Shuffle next: pos=$newPos, index=$newIndex, uri=$uri")
                viewModelScope.launch { _switchVideoEvent.emit(uri) }
            } else if (shouldRepeatPlaylist()) {
                generateShuffleIndicesInternal()
                _shuffledPosition.value = 0
                val newIndex = _shuffleIndices.value[0]
                _currentIndex.value = newIndex
                viewModelScope.launch { _switchVideoEvent.emit(uris[newIndex]) }
            }
        } else {
            if (uris.isNotEmpty() && idx < effectiveSize - 1) {
                val newIndex = idx + 1
                _currentIndex.value = newIndex
                val uri = uris[newIndex]
                Logger.d(TAG, "Switching to next video: $uri, index: $newIndex")
                viewModelScope.launch { _switchVideoEvent.emit(uri) }
            } else if (uris.isNotEmpty() && shouldRepeatPlaylist()) {
                _currentIndex.value = 0
                viewModelScope.launch { _switchVideoEvent.emit(uris[0]) }
            } else {
                Logger.d(TAG, "Already at last video")
            }
        }
    }

    /**
     * 切换到上一个视频（百分百复用 mpvEx playPrevious 算法）
     */
    fun previousVideo() {
        val uris = _playlistUris.value
        val idx = _currentIndex.value
        Logger.d(TAG, "previousVideo - idx=$idx, size=${uris.size}")

        val effectiveSize = uris.size

        if (_shuffleEnabled.value) {
            if (_shuffleIndices.value.isEmpty() && uris.isNotEmpty()) {
                generateShuffleIndicesInternal()
            }
            val shuffled = _shuffleIndices.value
            val shuffledPos = _shuffledPosition.value
            if (shuffledPos > 0) {
                val newPos = shuffledPos - 1
                _shuffledPosition.value = newPos
                val newIndex = shuffled[newPos]
                _currentIndex.value = newIndex
                viewModelScope.launch { _switchVideoEvent.emit(uris[newIndex]) }
            } else if (shouldRepeatPlaylist()) {
                _shuffledPosition.value = shuffled.size - 1
                val newIndex = shuffled[shuffled.size - 1]
                _currentIndex.value = newIndex
                viewModelScope.launch { _switchVideoEvent.emit(uris[newIndex]) }
            }
        } else {
            if (uris.isNotEmpty() && idx > 0) {
                val newIndex = idx - 1
                _currentIndex.value = newIndex
                val uri = uris[newIndex]
                Logger.d(TAG, "Switching to previous video: $uri, index: $newIndex")
                viewModelScope.launch { _switchVideoEvent.emit(uri) }
            } else if (uris.isNotEmpty() && shouldRepeatPlaylist()) {
                _currentIndex.value = effectiveSize - 1
                viewModelScope.launch { _switchVideoEvent.emit(uris[effectiveSize - 1]) }
            } else {
                Logger.d(TAG, "Already at first video")
            }
        }
    }

    fun playVideoAtIndex(index: Int) {
        val uris = _playlistUris.value
        if (index in uris.indices) {
            _currentIndex.value = index
            _currentVideoUri.value = uris[index]
            viewModelScope.launch { _switchVideoEvent.emit(uris[index]) }
        }
    }

    // 随机播放内部索引（百分百复用 mpvEx generateShuffledIndices 算法）
    private val _shuffleIndices = MutableStateFlow<List<Int>>(emptyList())
    private val _shuffledPosition = MutableStateFlow(0)

    private fun generateShuffleIndicesInternal() {
        val uris = _playlistUris.value
        if (uris.isEmpty()) return
        val currentIdx = _currentIndex.value
        val indices = uris.indices.filter { it != currentIdx }.toMutableList()
        indices.shuffle()
        _shuffleIndices.value = listOf(currentIdx) + indices
        _shuffledPosition.value = 0
        Logger.d(TAG, "Shuffle indices generated: ${_shuffleIndices.value.size} items")
    }

    /**
     * 设置随机播放状态（百分百复用 mpvEx onShuffleToggled 算法）
     */
    fun setShuffleEnabled(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        if (enabled && _playlistUris.value.isNotEmpty()) {
            generateShuffleIndicesInternal()
        } else {
            _shuffleIndices.value = emptyList()
            _shuffledPosition.value = 0
        }
        Logger.d(TAG, "Shuffle ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * 循环切换重复模式（百分百复用 mpvEx cycleRepeatMode 算法）
     */
    fun cycleRepeatMode() {
        val hasPlaylist = _playlistUris.value.isNotEmpty()
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> if (hasPlaylist) RepeatMode.ALL else RepeatMode.OFF
            RepeatMode.ALL -> RepeatMode.OFF
        }
        Logger.d(TAG, "Repeat mode cycled to: ${_repeatMode.value}")
    }

    /**
     * 直接设置重复模式
     */
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        Logger.d(TAG, "Repeat mode set to: $mode")
    }

    /**
     * 设置自动连播开关（百分百复用 mpvEx autoplayNextVideo 算法）
     */
    fun setAutoPlayNextEnabled(enabled: Boolean) {
        _autoPlayNextEnabled.value = enabled
        Logger.d(TAG, "Auto play next ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * 从 URI 列表初始化系列（向后兼容 - 百分百复用 mpvEx 算法）
     */
    fun initSeriesFromUriList(uriList: List<Uri>, currentUri: Uri) {
        val idx = uriList.indexOfFirst { it.toString() == currentUri.toString() }.takeIf { it >= 0 } ?: 0
        _playlistUris.value = uriList
        _currentIndex.value = idx
        val list = uriList.map { uri ->
            VideoFileParcelable(uri.toString(), uri.lastPathSegment ?: uri.toString(), "", 0L, 0L, 0L)
        }
        _videoList.value = list
        Logger.d(TAG, "initSeriesFromUriList: ${uriList.size} videos, idx=$idx")
    }
    
    /**
     * 设置硬件解码状态
     */
    fun setHardwareDecoding(enabled: Boolean) {
        _isHardwareDecoding.value = enabled
        Logger.d(TAG, "Hardware decoding: $enabled")
    }
    
    /**
     * 设置当前视频URI
     */
    fun setCurrentVideoUri(uri: Uri?) {
        _currentVideoUri.value = uri
    }

    fun setVideoTitle(title: String) {
        _videoTitle.value = title
    }
    
    /**
     * 设置快进/快退秒数
     */
    fun setSeekTimeSeconds(seconds: Int) {
        _seekTimeSeconds.value = seconds
        Logger.d(TAG, "Seek time set to: ${seconds}s")
    }
    
    /**
     * 设置是否为在线视频
     */
    fun setIsOnlineVideo(isOnline: Boolean) {
        _isOnlineVideo.value = isOnline
        Logger.d(TAG, "Is online video: $isOnline")
    }
    
    /**
     * 设置加载动画显示状态（在线视频缓冲/加载时显示）
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
        Logger.v(TAG, "Loading indicator: $loading")
    }
    
    /**
     * 设置保存的播放位置
     */
    fun setSavedPosition(position: Double) {
        _savedPosition.value = position
        Logger.d(TAG, "Saved position: ${position}s")
    }

    fun showResumeToast() {
        _resumeToastVisible.value = true
    }

    fun hideResumeToast() {
        _resumeToastVisible.value = false
    }
    
    /**
     * 保存长按前的速度
     */
    fun saveSpeedBeforeLongPress(speed: Double) {
        _speedBeforeLongPress.value = speed
        Logger.d(TAG, "Speed before long press saved: $speed")
    }
    
    /**
     * 恢复长按前的速度
     */
    fun restoreSpeedAfterLongPress() {
        val savedSpeed = _speedBeforeLongPress.value
        setSpeed(savedSpeed)
        Logger.d(TAG, "Speed restored to: $savedSpeed")
    }

    /**
     * 开始长按倍速（Compose 层调用）：保存当前速度并切换到长按速度
     */
    fun startLongPressSpeed() {
        saveSpeedBeforeLongPress(_speed.value.toDouble())
        setSpeed(_longPressSpeed.value.toDouble())
        _isLongPressing.value = true
        _isDynamicSpeedActive.value = false
        _speedPresetBarVisible.value = false
        cancelSpeedPresetBarJob()
        cancelAutoHideTimer()
    }

    /**
     * 长按期间动态调速（左右滑动切换速度档位）
     */
    fun updateDynamicSpeed(newSpeed: Float) {
        _isDynamicSpeedActive.value = true
        setSpeed(newSpeed.toDouble())
        // 档位条显示后 2.5 秒自动隐藏（文字提示不消失，一直长按一直显示）
        _speedPresetBarVisible.value = true
        cancelSpeedPresetBarJob()
        speedPresetBarJob = viewModelScope.launch {
            delay(2500L)
            _speedPresetBarVisible.value = false
        }
    }

    /**
     * 结束长按倍速（Compose 层调用）：恢复原速度
     */
    fun endLongPressSpeed() {
        restoreSpeedAfterLongPress()
        _isLongPressing.value = false
        _isDynamicSpeedActive.value = false
        _speedPresetBarVisible.value = false
        cancelSpeedPresetBarJob()
        resetAutoHideTimer()
    }

    private fun cancelSpeedPresetBarJob() {
        speedPresetBarJob?.cancel()
        speedPresetBarJob = null
    }

    /**
     * 更新滑动 Seek 预览（实时 seek + 显示预览文字）
     */
    fun updateSwipeSeek(targetSeconds: Int, deltaSeconds: Int) {
        val clampedTarget = targetSeconds.coerceIn(0, _duration.value)
        _swipeSeekPreview.value = SwipeSeekPreview(clampedTarget, deltaSeconds)
        seekTo(clampedTarget)
    }

    /**
     * 结束滑动 Seek（清除预览）
     */
    fun endSwipeSeek() {
        _swipeSeekPreview.value = null
    }

    
    /**
     * 设置是否已自动加载字幕
     */
    fun setHasAutoLoadedSubtitle(loaded: Boolean) {
        _hasAutoLoadedSubtitle.value = loaded
        Logger.d(TAG, "Has auto-loaded subtitle: $loaded")
    }
    
    /**
     * 设置当前文件夹路径
     */
    fun setCurrentFolderPath(path: String?) {
        _currentFolderPath.value = path
        Logger.d(TAG, "Current folder path: $path")
    }
    
    /**
     * 设置是否从主页继续播放进入
     */
    fun setIsFromHomeContinue(fromHome: Boolean) {
        _isFromHomeContinue.value = fromHome
        Logger.d(TAG, "Is from home continue: $fromHome")
    }
    
    /**
     * 设置从列表传入的播放位置
     */
    fun setLastPlaybackPosition(position: Long) {
        _lastPlaybackPosition.value = position
        Logger.d(TAG, "Last playback position: ${position}ms")
    }
    
    /**
     * 标记已恢复播放位置
     */
    fun setHasRestoredPosition(restored: Boolean) {
        _hasRestoredPosition.value = restored
    }
    
    /**
     * 标记已显示提示
     */
    fun setHasShownPrompt(shown: Boolean) {
        _hasShownPrompt.value = shown
    }
    
    /**
     * 更新缓冲检测位置
     */
    fun updateBufferingPosition(position: Double) {
        _lastPositionForBuffering.value = position
    }
    
    /**
     * 更新缓冲检测时间
     */
    fun updateBufferingTime(time: Long) {
        _lastPositionUpdateTime.value = time
    }
    
    /**
     * 设置缓冲停滞状态
     */
    fun setIsStalledBuffering(isStalled: Boolean) {
        _isStalledBuffering.value = isStalled
    }
    
    /**
     * 更新之前的播放状态
     */
    fun updatePreviousIsPlaying(wasPlaying: Boolean) {
        _previousIsPlaying.value = wasPlaying
    }
    
    /**
     * 设置系列播放列表
     */
    fun setCurrentSeries(series: List<Uri>) {
        _currentSeries.value = series
        Logger.d(TAG, "Current series: ${series.size} videos")
    }
    
    /**
     * 设置待处理的seek位置
     */
    fun setPendingSeekPosition(position: Int?) {
        _pendingSeekPosition.value = position
    }
    
    /**
     * 设置手势开始位置
     */
    fun setGestureStartPosition(position: Int) {
        _gestureStartPosition.value = position
    }
    
    // ==================== MPV播放控制方法（阶段1.2）====================
    
    /**
     * 切换播放/暂停
     */
    fun togglePlayPause() {
        val currentPaused = paused.value ?: true
        MPVLib.setPropertyBoolean("pause", !currentPaused)
        Logger.d(TAG, "Toggle play/pause: ${!currentPaused}")
    }
    
    /**
     * 播放
     */
    fun play() {
        MPVLib.setPropertyBoolean("pause", false)
    }
    
    /**
     * 暂停
     */
    fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
    }
    
    /**
     * 设置播放速度
     */
    fun setSpeed(speed: Double) {
        MPVLib.setPropertyDouble("speed", speed)
        // 显示速度变化提示
        if (speed != 1.0) {
            showPlayerUpdate(PlayerUpdates.Speed(speed))
        }
        Logger.d(TAG, "Speed changed to: ${speed}x")
    }
    
    // ==================== Seek 防抖合并（参考 mpvEx coalesceSeek）====================
    private var seekCoalesceJob: Job? = null
    private var pendingSeekOffset = 0
    private val seekCoalesceDelayMs = 60L  // 60ms 防抖窗口，期间累积多次点击的偏移量

    /**
     * 防抖合并的 seekBy（参考 mpvEx）
     * 快速连续点击时，将多次偏移量累加后只执行一次 seek，避免 MPV 过载
     */
    private fun coalesceSeek(offset: Int) {
        pendingSeekOffset += offset
        seekCoalesceJob?.cancel()
        seekCoalesceJob = viewModelScope.launch {
            delay(seekCoalesceDelayMs)
            val toApply = pendingSeekOffset
            pendingSeekOffset = 0
            if (toApply != 0) {
                // 临时启用精确跳转（参考 mpvEx 动态 hr-seek 管理）
                val hrSeekEnabled = playerRepository.isPreciseSeekingEnabled()
                if (!hrSeekEnabled) {
                    MPVLib.setPropertyString("hr-seek", "yes")
                }
                // 使用 mpv 原生相对跳转 "relative+exact"（参考 mpvKt/mpvEx）
                // 避免在 Kotlin 层做位置计算，由 MPV 内部处理偏移
                MPVLib.command("seek", toApply.toString(), "relative+exact")
                if (!hrSeekEnabled) {
                    MPVLib.setPropertyString("hr-seek", "no")
                }
                Logger.d(TAG, "Seek coalesced: $toApply (relative+exact, hr-seek=$hrSeekEnabled)")
            }
        }
    }

    /**
     * Seek到指定位置（绝对位置跳转，用于进度条、滑动手势）
     */
    fun seekTo(position: Int) {
        val safePosition = position.coerceAtLeast(0)
        val hrSeekEnabled = playerRepository.isPreciseSeekingEnabled()
        if (!hrSeekEnabled) {
            MPVLib.setPropertyString("hr-seek", "yes")
        }
        MPVLib.command("seek", safePosition.toString(), "absolute+exact")
        if (!hrSeekEnabled) {
            MPVLib.setPropertyString("hr-seek", "no")
        }
        // 发射Seek事件，通知Activity同步弹幕进度
        _seekEvent.tryEmit(safePosition)
        Logger.d(TAG, "Seek to: $safePosition (absolute+exact, hr-seek=$hrSeekEnabled)")
    }
    
    /**
     * 相对Seek（前进/后退按钮）
     * 使用 mpv 原生相对跳转 "relative+exact" + 防抖合并
     * @param seconds 偏移秒数
     * @param atTop 指示器是否显示在顶部
     */
    fun seekRelative(seconds: Int, atTop: Boolean = true) {
        // 使用防抖合并的 seek（参考 mpvEx coalesceSeek 算法）
        coalesceSeek(seconds)
        
        // 显示seek指示器
        _seekOffset.value = seconds
        _seekIndicatorShown.value = true
        _seekIndicatorAtTop.value = atTop
        
        // 发射Seek事件用于弹幕同步
        _seekEvent.tryEmit(position.value + seconds)
        
        // 1秒后自动隐藏指示器
        viewModelScope.launch {
            delay(1000)
            _seekIndicatorShown.value = false
        }
    }
    
    // ==================== 原有方法 ====================
    // ==================== UI State（原有代码）====================
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _playbackHistory = MutableStateFlow<PlaybackHistoryEntity?>(null)
    val playbackHistory: StateFlow<PlaybackHistoryEntity?> = _playbackHistory.asStateFlow()
    
    private val _playerSettings = MutableStateFlow(PlayerSettings())
    val playerSettings: StateFlow<PlayerSettings> = _playerSettings.asStateFlow()
    
    // ==================== 播放状态更新 ====================
    
    fun updatePlaybackPosition(position: Long, duration: Long) {
        _playbackState.value = _playbackState.value.copy(
            position = position,
            duration = duration
        )
    }
    
    fun updatePlayingState(isPlaying: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
    }
    
    fun updateSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(speed = speed)
    }
    
    fun updateBufferingState(isBuffering: Boolean) {
        _playbackState.value = _playbackState.value.copy(isBuffering = isBuffering)
    }
    
    fun updateHardwareDecoding(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(isHardwareDecoding = enabled)
    }
    
    // ==================== 播放历史管理 ====================
    
    /**
     * 加载指定视频的播放历史
     */
    fun loadPlaybackHistory(uri: Uri) {
        viewModelScope.launch {
            try {
                val history = playerRepository.getPlaybackHistory(uri)
                _playbackHistory.value = history
                Logger.d(TAG, "Loaded playback history for $uri: position=${history?.position}")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load playback history", e)
                _playbackHistory.value = null
            }
        }
    }
    
    /**
     * 保存播放历史
     */
    fun savePlaybackHistory(
        uri: Uri,
        fileName: String,
        position: Long,
        duration: Long,
        folderName: String,
        danmuPath: String? = null,
        danmuVisible: Boolean = true,
        danmuOffsetTime: Long = 0L,
        thumbnailPath: String? = null
    ) {
        viewModelScope.launch {
            try {
                playerRepository.savePlaybackHistory(
                    uri = uri,
                    fileName = fileName,
                    position = position,
                    duration = duration,
                    folderName = folderName,
                    danmuPath = danmuPath,
                    danmuVisible = danmuVisible,
                    danmuOffsetTime = danmuOffsetTime,
                    thumbnailPath = thumbnailPath
                )
                Logger.d(TAG, "Saved playback history: $fileName at $position")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save playback history", e)
            }
        }
    }
    
    /**
     * 删除播放历史
     */
    fun deletePlaybackHistory(uri: Uri) {
        viewModelScope.launch {
            try {
                playerRepository.deletePlaybackHistory(uri)
                _playbackHistory.value = null
                Logger.d(TAG, "Deleted playback history for $uri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete playback history", e)
            }
        }
    }
    
    /**
     * 清除所有播放历史
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                playerRepository.clearAllPlaybackHistory()
                _playbackHistory.value = null
                Logger.d(TAG, "Cleared all playback history")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear all history", e)
            }
        }
    }
    
    // ==================== 播放器设置管理 ====================
    
    /**
     * 加载播放器设置
     */
    fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = PlayerSettings(
                    seekTimeSeconds = playerRepository.getSeekTimeSeconds(),
                    rememberPosition = playerRepository.isRememberPositionEnabled(),
                    rememberSpeed = playerRepository.isRememberSpeedEnabled(),
                    rememberBrightness = playerRepository.isRememberBrightnessEnabled(),
                    autoLoadDanmaku = playerRepository.isAutoLoadDanmakuEnabled(),
                    hardwareDecoding = playerRepository.isHardwareDecodingEnabled(),
                    gestureControlEnabled = playerRepository.isGestureControlEnabled()
                )
                _playerSettings.value = settings
                Logger.d(TAG, "Loaded playback settings")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load settings", e)
            }
        }
    }
    
    /**
     * 更新快进/快退时长
     */
    fun updateSeekTimeSeconds(seconds: Int) {
        viewModelScope.launch {
            try {
                playerRepository.setSeekTimeSeconds(seconds)
                _playerSettings.value = _playerSettings.value.copy(seekTimeSeconds = seconds)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update seek time", e)
            }
        }
    }
    
    /**
     * 更新记忆播放位置开关
     */
    fun updateRememberPosition(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setRememberPositionEnabled(enabled)
                _playerSettings.value = _playerSettings.value.copy(rememberPosition = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update remember position", e)
            }
        }
    }
    
    /**
     * 更新记忆播放速度开关
     */
    fun updateRememberSpeed(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setRememberSpeedEnabled(enabled)
                _playerSettings.value = _playerSettings.value.copy(rememberSpeed = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update remember speed", e)
            }
        }
    }
    
    /**
     * 更新硬件解码开关
     */
    fun updateHardwareDecodingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setHardwareDecodingEnabled(enabled)
                _playerSettings.value = _playerSettings.value.copy(hardwareDecoding = enabled)
                _playbackState.value = _playbackState.value.copy(isHardwareDecoding = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update hardware decoding", e)
            }
        }
    }
    
    /**
     * 更新手势控制开关
     */
    fun updateGestureControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setGestureControlEnabled(enabled)
                _playerSettings.value = _playerSettings.value.copy(gestureControlEnabled = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update gesture control", e)
            }
        }
    }
    
    // ==================== ViewModel 生命周期 ====================
    
    /**
     * ViewModel清理时注销MPV观察者
     */
    override fun onCleared() {
        super.onCleared()
        unregisterMPVObservers()
        Logger.d(TAG, "PlayerViewModel cleared")
    }
}

/**
 * 播放状态
 */
data class PlaybackState(
    val position: Long = 0L,
    val duration: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val isHardwareDecoding: Boolean = true
)

/**
 * 播放器设置（核心播放器配置）
 */
data class PlayerSettings(
    val seekTimeSeconds: Int = 5,
    val rememberPosition: Boolean = true,
    val rememberSpeed: Boolean = true,
    val rememberBrightness: Boolean = true,
    val autoLoadDanmaku: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val gestureControlEnabled: Boolean = true
)

/**
 * 字幕轨道信息（从MPV track-list解析）
 */
data class SubtitleTrack(
    val id: Int,
    val lang: String?,
    val title: String?,
    val type: String,
    val selected: Boolean = false,
    val external: Boolean = false
)

/**
 * 音频轨道信息（从MPV track-list解析）
 */
data class AudioTrack(
    val id: Int,
    val lang: String?,
    val title: String?,
    val type: String,
    val selected: Boolean = false,
    val codec: String?
)
