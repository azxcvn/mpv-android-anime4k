package com.fam4k007.videoplayer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import com.fam4k007.videoplayer.player.CustomMPVView
import com.fam4k007.videoplayer.player.VideoAspect
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.domain.subtitle.SubtitleManager
import com.fam4k007.videoplayer.remote.RemotePlaybackHeaders
import com.fam4k007.videoplayer.remote.RemotePlaybackLauncher
import com.fam4k007.videoplayer.remote.RemotePlaybackRequest
import com.fam4k007.videoplayer.remote.RemotePlaybackResolver
import com.fam4k007.videoplayer.remote.RemoteUrlParser
import com.fam4k007.videoplayer.domain.player.GestureHandler
import com.fam4k007.videoplayer.domain.player.PlaybackEngine
import com.fam4k007.videoplayer.domain.player.PlayerControlsManager
import com.fam4k007.videoplayer.domain.player.PlayerDialogManager
import com.fam4k007.videoplayer.domain.player.FilePickerManager
import com.fam4k007.videoplayer.domain.player.SubtitleDialogCallback
import com.fam4k007.videoplayer.domain.player.DanmakuDialogCallback
import com.fam4k007.videoplayer.domain.player.MoreOptionsCallback
import com.fam4k007.videoplayer.domain.player.AudioOptionsCallback
import com.fam4k007.videoplayer.domain.player.VideoAspectCallback
import com.fam4k007.videoplayer.service.BackgroundPlaybackService
import com.fam4k007.videoplayer.domain.player.VideoUriProvider
import com.fam4k007.videoplayer.utils.FormatUtils
import com.fam4k007.videoplayer.utils.UriUtils.resolveUri
import com.fam4k007.videoplayer.utils.UriUtils.getFolderName
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.presentation.PlayerViewModel
import com.fam4k007.videoplayer.presentation.PlayerStateHolder
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.fam4k007.videoplayer.ui.player.PlayerControls
import com.fam4k007.videoplayer.ui.theme.ThemeController
import com.fam4k007.videoplayer.ui.theme.VideoPlayerTheme

/**
 * 视频播放器 Activity (重构版)
 * 使用管理器模式进行职责分离，防止内存泄漏
 */
class VideoPlayerActivity : AppCompatActivity(),
    SubtitleDialogCallback,
    DanmakuDialogCallback,
    MoreOptionsCallback,
    AudioOptionsCallback,
    VideoAspectCallback,
    VideoUriProvider {

    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val SEEK_DEBUG = "SEEK_DEBUG"  // 快进调试专用日志标签
        private val REMOTE_URI_SCHEMES = setOf("http", "https", "rtsp", "rtmp", "rtmps")
        private const val EXTRA_PORTRAIT_UI = "portrait_ui"
        private const val EXTRA_AUTO_ROTATE = "auto_rotate"
    }

    internal lateinit var playbackEngine: PlaybackEngine
    internal lateinit var controlsManager: PlayerControlsManager
    internal val isControlsManagerInitialized get() = ::controlsManager.isInitialized
    internal lateinit var gestureHandler: GestureHandler
    internal lateinit var anime4KManager: Anime4KManager
    internal lateinit var danmakuManager: com.fam4k007.videoplayer.domain.danmaku.DanmakuManager
    internal lateinit var dialogManager: PlayerDialogManager
    internal lateinit var filePickerManager: FilePickerManager
    internal lateinit var composeOverlayManager: com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager
    internal lateinit var screenshotManager: com.fam4k007.videoplayer.manager.ScreenshotManager
    internal lateinit var skipIntroOutroManager: com.fam4k007.videoplayer.manager.SkipIntroOutroManager
    internal var thumbnailManager: com.fam4k007.videoplayer.manager.VideoThumbnailManager? = null

    internal lateinit var mpvView: CustomMPVView
    internal lateinit var danmakuView: com.fam4k007.videoplayer.danmaku.DanmakuPlayerView
    internal lateinit var clickArea: View
    // loadingIndicator 已迁移至 Compose LoadingOverlay
    
    @Deprecated("Use viewModel.currentVideoUri instead", ReplaceWith("viewModel.currentVideoUri.value"))
    internal var videoUri: Uri? = null
    
    internal var remotePlaybackRequest: RemotePlaybackRequest? = null
    internal var remoteResolveJob: Job? = null
    internal var remoteResolveSequence = 0L
    internal var themeRevision by mutableIntStateOf(0)
    internal val preferencesManager: PreferencesManager by inject()
    internal val historyManager: PlaybackHistoryManager by inject()
    internal val viewModel: PlayerViewModel by inject()
    internal val subtitleManager = SubtitleManager()
    
    @Deprecated("Use viewModel.savedPosition instead", ReplaceWith("viewModel.savedPosition.value"))
    internal var savedPosition = 0.0
    
    @Deprecated("Use viewModel.lastPlaybackPosition instead", ReplaceWith("viewModel.lastPlaybackPosition.value"))
    internal var lastPlaybackPosition = 0L  // 从列表传入的播放位置
    
    @Deprecated("Use viewModel.precisePosition instead", ReplaceWith("viewModel.precisePosition.value"))
    internal var currentPosition = 0.0
    
    @Deprecated("Use viewModel.duration instead", ReplaceWith("viewModel.duration.value"))
    internal var duration = 0.0
    
    // 【阶段1.2迁移】以下变量已迁移到ViewModel，Activity保留用于临时兼容
    @Deprecated("Use viewModel.paused instead", ReplaceWith("viewModel.paused.value == false"))
    internal var isPlaying = false
    
    @Deprecated("Use viewModel.isHardwareDecoding instead", ReplaceWith("viewModel.isHardwareDecoding.value"))
    internal var isHardwareDecoding = true
    
    internal var pendingSeekPosition: Int? = null  // 待处理的seek位置，用于解决连续双击问题
    
    @Deprecated("Use viewModel.videoAspect instead", ReplaceWith("viewModel.videoAspect.value"))
    internal var currentVideoAspect = VideoAspect.FIT  // 当前画面比例模式
    
    // 缓冲检测相关变量
    @Deprecated("Use viewModel.lastPositionForBuffering instead", ReplaceWith("viewModel.lastPositionForBuffering.value"))
    internal var lastPositionForBuffering = 0.0
    
    @Deprecated("Use viewModel.lastPositionUpdateTime instead", ReplaceWith("viewModel.lastPositionUpdateTime.value"))
    internal var lastPositionUpdateTime = 0L
    
    @Deprecated("Use viewModel.isStalledBuffering instead", ReplaceWith("viewModel.isStalledBuffering.value"))
    internal var isStalledBuffering = false
    
    // ==================== 播放列表管理（百分百复用 mpvEx 算法）====================
    
    /** 播放列表（URI 列表）*/
    internal var playlist: List<Uri> = emptyList()
    
    /** 当前播放索引 */
    internal var playlistIndex: Int = 0
    
    /** 随机播放索引列表 */
    private var shuffledIndices: List<Int> = emptyList()
    
    /** 随机播放当前位置 */
    private var shuffledPosition: Int = 0
    
    /** 播放列表数据库ID（来自自定义播放列表） */
    private var playlistId: Int? = null
    
    /** 窗口加载偏移（用于大播放列表） */
    private var playlistWindowOffset: Int = 0
    
    /** 完整播放列表的总项目数（窗口加载时使用） */
    var playlistTotalCount: Int = -1
        private set
    
    /** 当前是否为 M3U 播放列表 */
    private var isM3uPlaylist: Boolean = false

    /** 防止 handleEndOfFile 重入（切换视频时 MPV 会对旧视频发出 END_FILE 事件） */
    internal var isSwitchingVideo = false

    /** 是否已经在处理 END_FILE（防重入保护） */
    private var isHandlingEndOfFile = false

    internal var anime4KDialog: android.app.Dialog? = null
    
    @Deprecated("Use viewModel.anime4KMode instead", ReplaceWith("viewModel.anime4KMode.value"))
    internal var anime4KMode = Anime4KManager.Mode.OFF
    
    // 是否为在线视频
    @Deprecated("Use viewModel.isOnlineVideo instead", ReplaceWith("viewModel.isOnlineVideo.value"))
    internal var isOnlineVideo = false
    
    internal var seekHint: TextView? = null
    internal var speedHint: LinearLayout? = null
    internal var speedHintText: TextView? = null
    
    internal lateinit var subtitlePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    
    internal lateinit var danmakuPickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    
    internal lateinit var audioPickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    
    internal var wasPlayingBeforeSubtitlePicker = false
    
    internal var wasPlayingBeforeDanmakuPicker = false

    // ==================== 后台播放（听视频）====================
    
    /** 用户手动触发了后台播放 */
    private var isManualBackgroundPlayback = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val portraitUi = intent.getBooleanExtra(EXTRA_PORTRAIT_UI, false)
        if (intent.getBooleanExtra(EXTRA_AUTO_ROTATE, false)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        // Always use the same layout to avoid tearing down / re-initializing MPV when toggling portrait UI.
        // Portrait mode is applied by updating RelativeLayout rules at runtime.
        setContentView(R.layout.activity_video_player)
        
        // 确保内容不受系统栏影响，视频画面完全居中
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 处理刘海屏/挖孔屏，让视频延伸到刘海区域，确保横屏时完全居中
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // 立即隐藏系统栏，避免初始显示时的闪烁和挤压
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Screen keep-on enabled")
        
        // 注册外部音频文件选择器
        audioPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleAddAudioTrack(uri)
            }
        }
        
        loadUserSettings()
        remotePlaybackRequest = intent.getParcelableExtra(RemotePlaybackLauncher.EXTRA_REMOTE_REQUEST)

        // 处理视频URI - 支持本地文件和在线URL
        try {
            videoUri = when {
                remotePlaybackRequest != null -> {
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Remote playback request from intent")
                    Uri.parse(remotePlaybackRequest!!.url)
                }
                intent.action == android.content.Intent.ACTION_VIEW -> {
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "ACTION_VIEW intent")
                    intent.data
                }
                intent.action == android.content.Intent.ACTION_SEND -> {
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "ACTION_SEND intent")
                    if (intent.type?.startsWith("video/") == true || intent.type?.startsWith("audio/") == true) {
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                    } else {
                        val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                        val parsedSharedInput = sharedText?.let { RemoteUrlParser.parsePlaybackInput(it) }
                        if (parsedSharedInput != null) {
                            val sourcePageUrl = RemotePlaybackHeaders.deriveSourcePageUrl(
                                headers = parsedSharedInput.headers
                            )
                            remotePlaybackRequest = RemotePlaybackRequest(
                                url = parsedSharedInput.url,
                                title = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT).orEmpty(),
                                sourcePageUrl = sourcePageUrl,
                                headers = parsedSharedInput.headers,
                                source = RemotePlaybackRequest.Source.DIRECT_INPUT
                            )
                            Logger.d(
                                TAG,
                                "Parsed shared remote text: url=${parsedSharedInput.url}, headers=${RemotePlaybackHeaders.describeForLog(parsedSharedInput.headers)}"
                            )
                            Uri.parse(parsedSharedInput.url)
                        } else {
                            intent.data
                        }
                    }
                }
                // 支持从MainActivity传递的URL
                intent.hasExtra("uri") -> {
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Has 'uri' extra")
                    val uriString = intent.getStringExtra("uri")
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "URI string from extra: $uriString")
                    if (uriString != null) {
                        Uri.parse(uriString)
                    } else {
                        intent.data
                    }
                }
                else -> {
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Using intent.data")
                    intent.data
                }
            }
            
            // 同步到ViewModel + 重置上次残留的播放状态
            viewModel.resetPlaybackState()
            viewModel.setCurrentVideoUri(videoUri)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video URI", e)
            DialogUtils.showToastShort(this, "Failed to parse video URL: ${e.message}")
            finish()
            return
        }
        
        if (videoUri == null) {
            Log.e(TAG, "Video URI is null")
            DialogUtils.showToastShort(this, "Invalid video path")
            finish()
            return
        }

        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video URI: $videoUri")
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "URI scheme: ${videoUri?.scheme}")
        
        // 判断是否为在线视频
        val isOnline = remotePlaybackRequest != null ||
            intent.getBooleanExtra("is_online", false) ||
            intent.getBooleanExtra("is_online_video", false) ||
            isRemotePlaybackUri(videoUri)
        viewModel.setIsOnlineVideo(isOnline)

        if (isOnline && remotePlaybackRequest == null) {
            remotePlaybackRequest = buildLegacyRemotePlaybackRequest(videoUri!!)
        }
        
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Is online video: $isOnlineVideo")
        
        // 检查是否从主页继续播放进入（有 folder_path 参数）
        val fromHomeContinue = intent.hasExtra("folder_path")
        viewModel.setIsFromHomeContinue(fromHomeContinue)
        if (fromHomeContinue) {
            val folderPath = intent.getStringExtra("folder_path")
            viewModel.setCurrentFolderPath(folderPath)
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "From home continue play, folder: $folderPath")
        } else if (isOnlineVideo) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Playing online video")
            // 在线视频不需要获取文件夹路径
            viewModel.setCurrentFolderPath(null)
        } else {
            // 获取当前视频所在文件夹路径
            videoUri?.let { uri ->
                val folderPath = uri.getFolderName()
                viewModel.setCurrentFolderPath(folderPath)
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Folder path: $folderPath")
            }
        }

        val savedPos = preferencesManager.getPlaybackPosition(videoUri.toString())
        viewModel.setSavedPosition(savedPos)
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Saved position: $savedPos seconds")

        mpvView = findViewById(R.id.surfaceView)
        danmakuView = findViewById(R.id.danmakuView)
        clickArea = findViewById(R.id.clickArea)
        // loadingIndicator 已迁移至 Compose LoadingOverlay
        
        // 根据是否为在线视频设置加载动画的初始状态
        viewModel.setLoading(isOnlineVideo)
        
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Initializing MPV in Activity...")
        try {
            // 总是调用 initialize，CustomMPVView 内部会处理重复初始化的保护
            mpvView.initialize(filesDir.path, cacheDir.path)
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "MPV View initialized")
            
            // 注册ViewModel的MPV观察者（阶段1.1）
            viewModel.registerMPVObservers()
            
            mpvView.postDelayed({
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Loading video after MPV init")
                loadVideo()
                // DASH格式：视频加载后动态添加外部音频轨
                val audioUrl = intent.getStringExtra("audio_url")
                if (!audioUrl.isNullOrEmpty()) {
                    mpvView.postDelayed({
                        try {
                            MPVLib.command("audio-add", audioUrl)
                            MPVLib.setPropertyString("vid", "auto")
                            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Added external audio: $audioUrl")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to add audio: ${e.message}")
                        }
                    }, 500) // 视频加载后尽快添加音频轨
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            DialogUtils.showToastLong(this, "Player initialization failed: ${e.message}\n\nPlease restart the app if the issue persists")
            finish()
            return
        }
        
        danmakuManager = com.fam4k007.videoplayer.domain.danmaku.DanmakuManager(this, danmakuView)
        danmakuManager.initialize()
        
        initializeManagers()
        
        // 连接播放引擎：弹幕状态机自动管理播放/暂停/seek（参考 DanDanPlay 的 ControlWrapper）
        danmakuManager.connectToEngine(playbackEngine)
        
        handleVideoListIntent()

        // Apply portrait UI last: by this point all views are inflated and bound.
        applyPortraitUiEnabled(portraitUi)
        
        // 【阶段1.1】订阅ViewModel StateFlow，自动更新UI（方案A：零视觉影响）
        setupViewModelObservers()
        
        // ========== 在 setupComposeTestLayer (调用 gestureHandler.initialize) 之前读取系统音量和亮度 ==========
        // 因为 gestureHandler.initialize() 会将系统音量设为 MAX，必须在它之前读取原始值
        
        // 读取原始系统音量百分比
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val sysVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maxSysVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val originalVolumePercent = ((sysVol.toFloat() / maxSysVol) * 100).toInt()
        viewModel.setInitialVolume(originalVolumePercent)
        Logger.d(TAG, "Original system volume: $sysVol/$maxSysVol = $originalVolumePercent%")
        
        // 读取系统亮度（0-255）并归一化到 0-1，参考 mpvEx 做法
        val sysBrightness = runCatching {
            android.provider.Settings.System.getFloat(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            ).let { raw ->
                (raw / 255f).coerceIn(0f, 1f)
            }
        }.getOrElse {
            Logger.w(TAG, "Failed to read system brightness, falling back to 0f: $it")
            0f
        }
        viewModel.setInitialBrightness(sysBrightness)
        // 将亮度应用到当前窗口，使指示器数值与实际亮度一致（参考 mpvEx）
        window.attributes = window.attributes.apply {
            screenBrightness = sysBrightness
        }
        Logger.d(TAG, "System brightness: ${(sysBrightness * 100).toInt()}%")
        
        // 读取音量增强状态
        
        // 读取音量增强状态
        val volumeBoost = preferencesManager.isVolumeBoostEnabled()
        viewModel.setVolumeBoostEnabled(volumeBoost)
        
        // 创建后台播放通知渠道
        BackgroundPlaybackService.createNotificationChannel(this)
        
        // 【阶段2.1】添加Compose测试层（不影响现有XML布局）
        setupComposeTestLayer()
        
    }

    override fun onTogglePortraitUi() {
        val autoRotateEnabled = intent.getBooleanExtra(EXTRA_AUTO_ROTATE, false)
        
        val currentPortrait = intent.getBooleanExtra(EXTRA_PORTRAIT_UI, false)
        val nextPortrait = !currentPortrait
        intent.putExtra(EXTRA_PORTRAIT_UI, nextPortrait)
        applyPortraitUiEnabled(nextPortrait)
        
        if (!autoRotateEnabled) {
            // 自动旋转关闭时，锁定到对应的方向
            requestedOrientation = if (nextPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            // 手动旋转按钮会关闭自动旋转，锁定到当前方向
            // 这样"更多"菜单中的自动旋转状态会同步更新为"关"
            intent.putExtra(EXTRA_AUTO_ROTATE, false)
            requestedOrientation = if (nextPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        refreshVideoLayoutAfterOrientationToggle()
    }

    override fun onToggleAutoRotate() {
        val enableAutoRotate = !intent.getBooleanExtra(EXTRA_AUTO_ROTATE, false)
        intent.putExtra(EXTRA_AUTO_ROTATE, enableAutoRotate)

        if (enableAutoRotate) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            syncPortraitUiWithConfiguration(resources.configuration)
        } else {
            val currentPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            intent.putExtra(EXTRA_PORTRAIT_UI, currentPortrait)
            applyPortraitUiEnabled(currentPortrait)
            requestedOrientation = if (currentPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        refreshVideoLayoutAfterOrientationToggle()
    }

    override fun isAutoRotateEnabled(): Boolean {
        return intent.getBooleanExtra(EXTRA_AUTO_ROTATE, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (intent.getBooleanExtra(EXTRA_AUTO_ROTATE, false)) {
            syncPortraitUiWithConfiguration(newConfig)
            refreshVideoLayoutAfterOrientationToggle()
        }
    }

    override fun onBackPressed() {
        handleBackNavigation()
    }

    /**
     * VideoAspectCallback 实现
     */
    override fun onVideoAspectChanged(aspect: VideoAspect) {
        viewModel.setVideoAspect(aspect)
        Logger.d(TAG, "Video aspect changed to: ${aspect.displayName}")
    }

    override fun onPause() {
        super.onPause()
        savePlaybackState()
    }
    
    override fun onStop() {
        super.onStop()
        
        // 后台播放模式：不暂停视频，让 Service 继续控制，但仍保存播放进度
        if (isManualBackgroundPlayback) {
            Log.d(TAG, "Background playback active, keeping video playing in background")
            savePlaybackState()
            return
        }
        
        // 当Activity完全不可见时（Home键、锁屏等），自动暂停视频
        // 不会影响文件选择器等操作，因为那些只触发onPause不触发onStop
        if (::playbackEngine.isInitialized && isPlaying) {
            playbackEngine.pause()
            
            // 手动同步播放状态(因为playbackEngine.pause()不会触发onPlaybackStateChanged)
            isPlaying = false
            
            // 更新UI状态
            // (播放按钮状态由Compose通过ViewModel自动更新)
            
            // 同步暂停弹幕(关键!修复问题1)
            if (::danmakuManager.isInitialized && danmakuManager.isPrepared()) {
                danmakuManager.pause()
            }
            
            // 暂停指示器会由状态监听器自动显示，不需要手动调用
            
            Logger.d(TAG, "Video and danmaku paused due to app going to background")
        }
        
        savePlaybackState()
    }
    
    override fun onStart() {
        super.onStart()
        // 从后台返回时重置标记并停止 Service
        if (isManualBackgroundPlayback) {
            isManualBackgroundPlayback = false
            endBackgroundPlayback()
            Logger.d(TAG, "Returned from background playback, service stopped")
        }
    }

    override fun onResume() {
        super.onResume()
        themeRevision++
        // 重新同步音量增强状态（用户可能在设置页面修改了）
        val currentBoost = preferencesManager.isVolumeBoostEnabled()
        viewModel.setVolumeBoostEnabled(currentBoost)
        // 重新同步动画开关设置（用户可能在设置页面修改了）
        viewModel.syncAnimationSettings()
        Logger.d(TAG, "Activity resumed, volume boost synced: $currentBoost")
        // 不自动恢复播放，让用户手动控制
        Logger.d(TAG, "Activity resumed")
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 当窗口重新获得焦点时(例如对话框关闭后),重新隐藏系统UI
        // 这样可以确保在Android 12及以下版本中,系统栏不会一直显示
        if (hasFocus && ::controlsManager.isInitialized) {
            controlsManager.hideSystemUI()
        }
    }
    
    override fun onDestroy() {
        // 必须在 super.onDestroy() 之前恢复音量和亮度，避免 context 失效
        if (::gestureHandler.isInitialized) {
            gestureHandler.restoreOriginalSettings()
        }
        
        super.onDestroy()
        Logger.d(TAG, "Activity destroyed")

        remoteResolveJob?.cancel()
        
        savePlaybackState()
        
        // 后台播放模式：不解绑 Service，不销毁 MPV，让 Service 继续运行
        if (isManualBackgroundPlayback) {
            Log.d(TAG, "Background playback active, keeping service alive")
            // 仍然清理 UI 相关资源
            anime4KDialog?.dismiss()
            anime4KDialog = null
            if (::dialogManager.isInitialized) {
                dialogManager.cleanup()
            }
            controlsManager?.cleanup()
            gestureHandler?.cleanup()
            filePickerManager?.cleanup()
            // 注意：不调用 playbackEngine?.destroy()，保持 MPV 运行
            // 不调用 endBackgroundPlayback()，保持 Service 运行
            return
        }
        
        // 正常销毁流程
        endBackgroundPlayback()

        // 清除自动旋转设置
        intent.removeExtra(EXTRA_AUTO_ROTATE)
        intent.removeExtra(EXTRA_PORTRAIT_UI)
        
        // 重置屏幕方向为用户设置，防止影响其他Activity
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 释放弹幕资源
        if (::danmakuManager.isInitialized) {
            danmakuManager.release()
        }
        
        // 清理Dialog（防止内存泄漏）
        anime4KDialog?.dismiss()
        anime4KDialog = null
        
        // 清理对话框管理器
        if (::dialogManager.isInitialized) {
            dialogManager.cleanup()
        }
        
        // 先停止 ViewModel 协程（防止 mpv 销毁后协程调用 mpv）
        viewModel.cleanup()

        // 销毁播放引擎
        playbackEngine?.destroy()
        controlsManager?.cleanup()
        gestureHandler?.cleanup()
        filePickerManager?.cleanup()
        thumbnailManager?.release()
        thumbnailManager = null
    }

    override fun onImportSubtitle() {
        filePickerManager.importSubtitle(isPlaying)
    }
    
    override fun onImportDanmaku() {
        filePickerManager.importDanmaku(isPlaying)
    }
    
    override fun onSearchNetworkDanmaku() {
        composeOverlayManager.showDanDanPlaySearchDialog(
            onEpisodeSelected = { episodeId, animeTitle, episodeTitle, animeId, serverUrl, episodes ->
                saveDanmakuAutoMatchCache(animeId, animeTitle, serverUrl, episodes)
                loadNetworkDanmaku(episodeId, animeTitle, episodeTitle, serverUrl)
            }
        )
    }

    override fun onDanmakuVisibilityChanged(visible: Boolean) {
        // 更新历史记录中的弹幕可见性状态
        videoUri?.let { uri ->
            historyManager.updateDanmu(
                uri = uri,
                danmuPath = danmakuManager.getCurrentDanmakuPath(),
                danmuVisible = visible,
                danmuOffsetTime = 0L
            )
            Logger.d(TAG, "Danmaku visibility updated in history: $visible")
        }
    }
    
    override fun onMatchDanmaku() {
        // 自动匹配弹幕
        lifecycleScope.launch {
            try {
                // 获取当前视频文件路径
                val uri = videoUri
                if (uri == null) {
                    DialogUtils.showToastShort(this@VideoPlayerActivity, "Failed to get video URI")
                    return@launch
                }
                
                // 获取真实文件路径
                val videoPath = when (uri.scheme) {
                    "file" -> uri.path
                    "content" -> {
                        // ContentResolver 方式获取路径
                        try {
                            val cursor = contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.DATA), null, null, null)
                            cursor?.use {
                                if (it.moveToFirst()) {
                                    val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                                    it.getString(columnIndex)
                                } else null
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to get path from ContentResolver", e)
                            null
                        }
                    }
                    else -> uri.path
                }
                
                if (videoPath.isNullOrEmpty()) {
                    DialogUtils.showToastShort(this@VideoPlayerActivity, "Failed to get video file path")
                    return@launch
                }
                
                val videoFile = File(videoPath)
                if (!videoFile.exists()) {
                    DialogUtils.showToastShort(this@VideoPlayerActivity, "Video file not found: $videoPath")
                    return@launch
                }
                
                // 显示匹配提示
                DialogUtils.showToastShort(this@VideoPlayerActivity, "Matching danmaku, please wait...")
                
                val enabledServers = preferencesManager.getEnabledDanmakuServers()
                if (enabledServers.isEmpty()) {
                    DialogUtils.showToastLong(this@VideoPlayerActivity, "没有启用的弹幕服务器")
                    return@launch
                }
                
                // 获取完整文件名
                val fileName = videoFile.name
                val fileSize = videoFile.length()
                
                Logger.d(TAG, "File info - name: $fileName, size: $fileSize, path: $videoPath")
                
                // 计算文件哈希（本地计算，只需一次）
                Logger.d(TAG, "Calculating file hash...")
                val hashApi = com.fam4k007.videoplayer.dandanplay.DanDanPlayApi(null)
                val fileHash = hashApi.calculateFileHash(videoPath)
                Logger.d(TAG, "File hash calculated: $fileHash")
                
                // 遍历所有启用的服务器进行匹配
                val allMatchResults = mutableListOf<com.fam4k007.videoplayer.dandanplay.ServerMatchResult>()
                for (server in enabledServers) {
                    try {
                        val api = com.fam4k007.videoplayer.dandanplay.DanDanPlayApi(
                            if (server.isDefault) null else server.url
                        )
                        val matchResponse = api.matchDanmaku(
                            fileName = fileName,
                            fileHash = fileHash,
                            fileSize = fileSize
                        )
                        if (matchResponse.isMatched && !matchResponse.matches.isNullOrEmpty()) {
                            matchResponse.matches.forEach { match ->
                                allMatchResults.add(
                                    com.fam4k007.videoplayer.dandanplay.ServerMatchResult(
                                        matchInfo = match,
                                        serverName = server.name,
                                        serverUrl = if (server.isDefault) null else server.url
                                    )
                                )
                            }
                        }
                        Logger.d(TAG, "Server '${server.name}' returned ${matchResponse.matches?.size ?: 0} matches")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Server '${server.name}' match failed", e)
                    }
                }
                
                if (allMatchResults.isEmpty()) {
                    DialogUtils.showToastLong(this@VideoPlayerActivity, "未找到匹配的弹幕")
                    return@launch
                }
                
                // 根据结果数量决定是否弹窗
                if (allMatchResults.size == 1) {
                    val result = allMatchResults[0]
                    loadNetworkDanmaku(result.matchInfo.episodeId, result.matchInfo.animeTitle, result.matchInfo.episodeTitle, result.serverUrl)
                    // 匹配弹幕没有集列表，需异步获取后缓存
                    lifecycleScope.launch {
                        val api = com.fam4k007.videoplayer.dandanplay.DanDanPlayApi(result.serverUrl)
                        api.searchAnime(result.matchInfo.animeTitle).fold(
                            onSuccess = { response ->
                                response.animes.firstOrNull { it.animeId == result.matchInfo.animeId }?.let {
                                    saveDanmakuAutoMatchCache(result.matchInfo.animeId, result.matchInfo.animeTitle, result.serverUrl, it.episodes)
                                }
                            },
                            onFailure = { Logger.w(TAG, "Match cache: search failed ${it.message}") }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showMatchSelectionDialog(allMatchResults)
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to match danmaku", e)
                DialogUtils.showToastLong(this@VideoPlayerActivity, "Matching failed: ${e.message}")
            }
        }
    }

    override fun onScreenshot() {
        screenshotManager.takeScreenshot()
    }
    
    // ==================== AudioOptionsCallback ====================
    
    override fun onAddAudioTrack() {
        Logger.d(TAG, "onAddAudioTrack: launching audio picker")
        try {
            audioPickerLauncher.launch(arrayOf("audio/*", "*/*"))
            Logger.d(TAG, "onAddAudioTrack: picker launched")
        } catch (e: Exception) {
            Logger.e(TAG, "onAddAudioTrack: failed to launch picker", e)
            DialogUtils.showToastShort(this, "无法打开文件选择器")
        }
    }
    
    /**
     * 处理外部音频文件添加
     * 参考 mpvRx-CN-main: MPVLib.command("audio-add", path, "cached")
     */
    private fun handleAddAudioTrack(uri: Uri) {
        Logger.d(TAG, "handleAddAudioTrack: uri=$uri, scheme=${uri.scheme}")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // content:// URI：resolveUri 可能返回已关闭FD的 /proc 路径，直接拷贝到临时文件
                val finalPath: String? = if (uri.scheme == "content") {
                    val tempFile = java.io.File(cacheDir, "audio_import_${System.currentTimeMillis()}.tmp")
                    try {
                        this@VideoPlayerActivity.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Logger.d(TAG, "handleAddAudioTrack: copied content URI to ${tempFile.absolutePath}, size=${tempFile.length()}")
                        tempFile.absolutePath
                    } catch (e: Exception) {
                        Logger.e(TAG, "handleAddAudioTrack: copy failed", e)
                        null
                    }
                } else {
                    uri.resolveUri(this@VideoPlayerActivity)
                }
                
                if (finalPath == null) {
                    withContext(Dispatchers.Main) {
                        DialogUtils.showToastShort(this@VideoPlayerActivity, "无法处理音频文件")
                    }
                    return@launch
                }
                
                val file = java.io.File(finalPath)
                Logger.d(TAG, "handleAddAudioTrack: finalPath=$finalPath, exists=${file.exists()}, size=${file.length()}")
                
                withContext(Dispatchers.Main) {
                    try {
                        Logger.d(TAG, "handleAddAudioTrack: calling MPVLib.command(audio-add, $finalPath, cached)")
                        MPVLib.command("audio-add", finalPath, "cached")
                        Logger.d(TAG, "handleAddAudioTrack: MPVLib.command returned OK")
                        
                        val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                        Logger.d(TAG, "handleAddAudioTrack: track-list/count=$trackCount")
                        for (i in 0 until trackCount) {
                            Logger.d(TAG, "handleAddAudioTrack: track[$i] type=${MPVLib.getPropertyString("track-list/$i/type")}, id=${MPVLib.getPropertyInt("track-list/$i/id")}, title=${MPVLib.getPropertyString("track-list/$i/title")}")
                        }
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            viewModel.refreshTrackList()
                        }, 500)
                        DialogUtils.showToastShort(this@VideoPlayerActivity, "音频轨道已添加")
                    } catch (e: Exception) {
                        Logger.e(TAG, "handleAddAudioTrack: MPVLib.command failed", e)
                        DialogUtils.showToastShort(this@VideoPlayerActivity, "添加音频轨道失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "handleAddAudioTrack: error", e)
                withContext(Dispatchers.Main) {
                    DialogUtils.showToastShort(this@VideoPlayerActivity, "添加音频失败: ${e.message}")
                }
            }
        }
    }
    
    override fun onShowSkipSettings() {
        skipIntroOutroManager.showSkipSettingsDrawer(
            folderPath = viewModel.currentFolderPath.value,
            getCurrentPosition = { viewModel.position.value.toDouble() },
            getDuration = { viewModel.duration.value.toDouble() }
        )
    }

    override fun onShowEqualizer() {
        val state = com.fam4k007.videoplayer.manager.compose.EqualizerState(
            enabled = preferencesManager.isEqualizerEnabled(),
            bands = preferencesManager.getEqualizerBands(),
            bassBoost = preferencesManager.getEqualizerBassBoost(),
            virtualizer = preferencesManager.getEqualizerVirtualizer()
        )
        composeOverlayManager.showEqualizerDrawer(
            state = state,
            onEnabledChange = { enabled ->
                preferencesManager.setEqualizerEnabled(enabled)
                if (enabled) {
                    playbackEngine?.setEqualizer(true, preferencesManager.getEqualizerBands())
                    playbackEngine?.setBassBoost(preferencesManager.getEqualizerBassBoost())
                    playbackEngine?.setVirtualizer(preferencesManager.getEqualizerVirtualizer())
                } else {
                    playbackEngine?.setEqualizer(false, emptyList())
                    playbackEngine?.setBassBoost(0)
                    playbackEngine?.setVirtualizer(0)
                }
            },
            onBandChange = { index, value ->
                preferencesManager.setEqualizerBand(index, value)
                if (preferencesManager.isEqualizerEnabled()) {
                    playbackEngine?.setEqualizer(true, preferencesManager.getEqualizerBands())
                }
            },
            onBassBoostChange = { value ->
                preferencesManager.setEqualizerBassBoost(value)
                if (preferencesManager.isEqualizerEnabled()) {
                    playbackEngine?.setBassBoost(value)
                }
            },
            onVirtualizerChange = { value ->
                preferencesManager.setEqualizerVirtualizer(value)
                if (preferencesManager.isEqualizerEnabled()) {
                    playbackEngine?.setVirtualizer(value)
                }
            }
        )
    }

    /**
     * 显示播放速度抽屉
     */
    fun showSpeedDrawer() {
        composeOverlayManager.showSpeedDrawer(
            currentSpeed = viewModel.speed.value.toDouble(),
            speedPresets = preferencesManager.getCustomSpeedPresets(),
            onSpeedChanged = { speed ->
                viewModel.setSpeed(speed)
                playbackEngine.setSpeed(speed)
                danmakuManager.setSpeed(speed.toFloat())
                preferencesManager.setLastPlaybackSpeed(speed.toFloat())
            },
            onPresetsChanged = { presets ->
                preferencesManager.setCustomSpeedPresets(presets)
            }
        )
    }

    /**
     * 恢复均衡器设置
     */
    internal fun restoreEqualizerSettings() {
        if (preferencesManager.isEqualizerEnabled()) {
            val bands = preferencesManager.getEqualizerBands()
            playbackEngine?.setEqualizer(true, bands)
            playbackEngine?.setBassBoost(preferencesManager.getEqualizerBassBoost())
            playbackEngine?.setVirtualizer(preferencesManager.getEqualizerVirtualizer())
        }
    }

    override fun onBackgroundPlayback() {
        if (!::playbackEngine.isInitialized || viewModel.paused.value == true) {
            DialogUtils.showToastShort(this, "Please start video playback first")
            return
        }
        
        isManualBackgroundPlayback = true
        
        // 暂停弹幕（画面不可见）
        if (::danmakuManager.isInitialized && danmakuManager.isPrepared()) {
            danmakuManager.pause()
        }
        
        // 同步状态到共享容器 + 启动前台 Service + 听视频界面
        val stateHolder: PlayerStateHolder by inject()
        stateHolder.syncFromViewModel(viewModel)
        startBackgroundPlayback()
        startActivity(Intent(this, AudioPlayerActivity::class.java))
    }

    // ==================== 后台播放管理 ====================

    /**
     * 启动后台播放 Service
     */
    private fun startBackgroundPlayback() {
        Log.d(TAG, "Starting background playback service")
        BackgroundPlaybackService.createNotificationChannel(this)
        try {
            startForegroundService(Intent(this, BackgroundPlaybackService::class.java).apply {
                putExtra("media_title", viewModel.videoTitle.value.ifBlank { "Audio Only" })
            })
            Log.d(TAG, "Service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service", e)
            isManualBackgroundPlayback = false
        }
    }
    
    /**
     * 停止后台播放 Service
     */
    private fun endBackgroundPlayback() {
        Log.d(TAG, "Ending background playback service")
        try {
            stopService(Intent(this, BackgroundPlaybackService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }

    override fun getVideoUri(): Uri? = videoUri

    // ==================== 播放列表管理（百分百复用 mpvEx 算法）====================

    /**
     * 检查是否有下一个视频
     */
    fun hasNext(): Boolean {
        if (playlist.isEmpty()) return false

        // repeat ALL 模式下始终有"下一个"
        if (viewModel.shouldRepeatPlaylist()) return true

        val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size

        return if (viewModel.shuffleEnabled.value) {
            shuffledPosition < shuffledIndices.size - 1
        } else {
            playlistIndex < effectiveSize - 1
        }
    }

    /**
     * 检查是否有上一个视频
     */
    fun hasPrevious(): Boolean {
        if (playlist.isEmpty()) return false

        // repeat ALL 模式下始终有"上一个"
        if (viewModel.shouldRepeatPlaylist()) return true

        return if (viewModel.shuffleEnabled.value) {
            shuffledPosition > 0
        } else {
            playlistIndex > 0
        }
    }

    /**
     * 生成随机播放索引
     */
    private fun generateShuffledIndices() {
        if (playlist.isEmpty()) return

        val indices = playlist.indices.filter { it != playlistIndex }.toMutableList()
        indices.shuffle()

        shuffledIndices = listOf(playlistIndex) + indices
        shuffledPosition = 0
    }

    /**
     * 当随机播放切换时调用
     */
    fun onShuffleToggled(enabled: Boolean) {
        if (enabled && playlist.isNotEmpty()) {
            generateShuffledIndices()
        } else {
            shuffledIndices = emptyList()
            shuffledPosition = 0
        }
    }

    /**
     * 播放下一集
     */
    fun playNext() {
        if (playlist.isEmpty()) {
            Log.d(TAG, "playNext: playlist is empty, cannot play next")
            return
        }

        val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size
        Log.d(TAG, "playNext: playlist.size=${playlist.size}, effectiveSize=$effectiveSize, playlistIndex=$playlistIndex, isShuffle=${viewModel.shuffleEnabled.value}")

        if (viewModel.shuffleEnabled.value) {
            if (shuffledIndices.isEmpty()) {
                generateShuffledIndices()
            }

            if (shuffledPosition < shuffledIndices.size - 1) {
                shuffledPosition++
                playlistIndex = shuffledIndices[shuffledPosition]
                Log.d(TAG, "playNext(shuffle): shuffledPosition=$shuffledPosition, playlistIndex=$playlistIndex")
                loadPlaylistItem(playlistIndex)
            } else if (viewModel.shouldRepeatPlaylist()) {
                generateShuffledIndices()
                shuffledPosition = 0
                playlistIndex = shuffledIndices[0]
                Log.d(TAG, "playNext(shuffle,loop): restart from ${shuffledIndices[0]}")
                loadPlaylistItem(playlistIndex)
            } else {
                Log.d(TAG, "playNext(shuffle): no more items in shuffle list")
            }
        } else {
            if (playlistIndex < effectiveSize - 1) {
                playlistIndex++
                Log.d(TAG, "playNext: advancing to index $playlistIndex")
                loadPlaylistItem(playlistIndex)
            } else if (viewModel.shouldRepeatPlaylist()) {
                playlistIndex = 0
                Log.d(TAG, "playNext: at end, repeat playlist -> back to index 0")
                loadPlaylistItem(0)
            } else {
                Log.d(TAG, "playNext: at end of playlist, no repeat")
            }
        }
    }

    /**
     * 播放上一集
     */
    fun playPrevious() {
        if (playlist.isEmpty()) return

        val effectiveSize = if (playlistTotalCount > 0) playlistTotalCount else playlist.size

        if (viewModel.shuffleEnabled.value) {
            if (shuffledIndices.isEmpty()) {
                generateShuffledIndices()
            }

            if (shuffledPosition > 0) {
                shuffledPosition--
                playlistIndex = shuffledIndices[shuffledPosition]
                loadPlaylistItem(playlistIndex)
            } else if (viewModel.shouldRepeatPlaylist()) {
                shuffledPosition = shuffledIndices.size - 1
                playlistIndex = shuffledIndices[shuffledPosition]
                loadPlaylistItem(playlistIndex)
            }
        } else {
            if (playlistIndex > 0) {
                playlistIndex--
                loadPlaylistItem(playlistIndex)
            } else if (viewModel.shouldRepeatPlaylist()) {
                playlistIndex = effectiveSize - 1
                loadPlaylistItem(playlistIndex)
            }
        }
    }

    /**
     * 播放指定索引的播放列表项
     */
    internal fun playPlaylistItem(index: Int) {
        if (index in playlist.indices) {
            loadPlaylistItem(index)
        }
    }

    /**
     * 加载播放列表项
     */
    private fun loadPlaylistItem(index: Int) {
        if (index < 0 || index >= playlist.size) {
            Log.e(TAG, "Invalid playlist index: $index (playlist size: ${playlist.size})")
            return
        }
        loadPlaylistItemInternal(index)
    }

    /**
     * 内部：加载播放列表项
     */
    private fun loadPlaylistItemInternal(index: Int) {
        if (index < 0 || index >= playlist.size) {
            Log.e(TAG, "loadPlaylistItemInternal: Invalid index $index (playlist.size=${playlist.size})")
            return
        }

        Log.d(TAG, "loadPlaylistItemInternal: loading index=$index, uri=${playlist[index]}")

        // 设置切换标志，防止 MPV 对旧视频发出的 END_FILE 事件触发级联切换
        isSwitchingVideo = true
        Log.d(TAG, "loadPlaylistItemInternal: isSwitchingVideo set to true")

        // 保存当前视频的播放状态
        videoUri?.let { uri ->
            preferencesManager.clearPlaybackPosition(uri.toString())
        }

        val uri = playlist[index]
        playlistIndex = index

        // 更新 ViewModel 的索引
        viewModel.syncPlaylistIndex(index)

        // 获取文件名
        val fileName = getFileNameFromUri(uri)
        viewModel.setVideoTitle(fileName)

        // 设置新的 videoUri
        videoUri = uri
        viewModel.setCurrentVideoUri(uri)

        // 更新在线视频标志
        val isOnline = isRemotePlaybackUri(uri)
        viewModel.setIsOnlineVideo(isOnline)
        remotePlaybackRequest = if (isOnline) {
            RemotePlaybackRequest(
                url = uri.toString(),
                title = fileName,
                source = RemotePlaybackRequest.Source.UNKNOWN
            )
        } else {
            null
        }

        // 显示加载动画（仅在线视频）
        viewModel.setLoading(isOnline)

        // 获取保存的播放位置
        val position = preferencesManager.getPlaybackPosition(uri.toString())

        // 重置自动加载字幕标志
        viewModel.setHasAutoLoadedSubtitle(false)

        // 释放旧弹幕
        danmakuManager.release()

        Log.d(TAG, "loadPlaylistItemInternal: calling playbackEngine.loadVideo, position=$position")

        // 加载视频
        if (isOnline) {
            loadResolvedRemoteVideo(remotePlaybackRequest!!, position)
        } else {
            playbackEngine?.resetEofDetection()
            playbackEngine?.loadVideo(uri, position)
            applyRememberedSpeed()
        }
        Log.d(TAG, "loadPlaylistItemInternal: loadVideo called, waiting for MPV_EVENT_FILE_LOADED to reset isSwitchingVideo")

        // 设置当前视频 URI 给文件选择器
        filePickerManager.setCurrentVideoUri(uri)

        // 加载弹幕
        loadDanmakuForVideo(uri)

        // 自动加载字幕和恢复设置
        lifecycleScope.launch {
            delay(500)

            if (!isOnline) {
                autoLoadSubtitleIfExists(uri)
            }

            restoreSubtitlePreferences(uri)

            // 恢复均衡器设置
            restoreEqualizerSettings()

            if (position > 0) {
                delay(300)
                danmakuManager.seekTo((position * 1000).toLong())
            }
        }

        Logger.d(TAG, "Loaded playlist item: index=$index, uri=$uri")
    }

    /**
     * 处理视频播放结束事件
     * 百分百复用 mpvEx 的 handleEndOfFile 算法
     * 加入防重入保护：MPV 加载新视频时会对旧视频发出 END_FILE，必须忽略
     */
    fun handleEndOfFile() {
        // 防重入保护：如果正在切换视频或已在处理中，忽略此次 END_FILE
        if (isSwitchingVideo || isHandlingEndOfFile) {
            Log.d(TAG, "handleEndOfFile ignored: isSwitchingVideo=$isSwitchingVideo, isHandlingEndOfFile=$isHandlingEndOfFile")
            return
        }

        isHandlingEndOfFile = true
        try {
            Log.d(TAG, "handleEndOfFile: playlist.size=${playlist.size}, playlistIndex=$playlistIndex, isShuffle=${viewModel.shuffleEnabled.value}")
            Log.d(TAG, "handleEndOfFile: autoPlayNext=${preferencesManager.isAutoPlayNextEnabled()}, closeAfterEOF=${preferencesManager.isCloseAfterEndOfVideo()}, repeatCurrent=${viewModel.shouldRepeatCurrentFile()}, repeatPlaylist=${viewModel.shouldRepeatPlaylist()}")

            // 检查是否重复当前文件
            if (viewModel.shouldRepeatCurrentFile()) {
                Log.d(TAG, "handleEndOfFile: repeating current file")
                MPVLib.command("seek", "0", "absolute")
                MPVLib.setPropertyBoolean("pause", false)
                return
            }

            // 处理播放列表播放
            if (playlist.isNotEmpty()) {
                val hasNextItem = if (viewModel.shuffleEnabled.value) {
                    shuffledPosition < shuffledIndices.size - 1
                } else {
                    playlistIndex < playlist.size - 1
                }
                Log.d(TAG, "handleEndOfFile: hasNextItem=$hasNextItem")

                // 检查是否启用自动连播
                val autoplayEnabled = preferencesManager.isAutoPlayNextEnabled()

                if (hasNextItem && autoplayEnabled) {
                    Log.d(TAG, "handleEndOfFile: has next AND autoplay enabled -> playNext()")
                    isSwitchingVideo = true
                    playNext()
                } else if (viewModel.shouldRepeatPlaylist() && autoplayEnabled) {
                    Log.d(TAG, "handleEndOfFile: no next but repeat playlist AND autoplay -> loop from start")
                    isSwitchingVideo = true
                    if (viewModel.shuffleEnabled.value) {
                        generateShuffledIndices()
                        shuffledPosition = 0
                        playlistIndex = shuffledIndices[0]
                        loadPlaylistItem(playlistIndex)
                    } else {
                        playlistIndex = 0
                        loadPlaylistItem(0)
                    }
                } else if (preferencesManager.isCloseAfterEndOfVideo()) {
                    Log.d(TAG, "handleEndOfFile: no autoplay -> finishAndRemoveTask")
                    finishAndRemoveTask()
                } else {
                    Log.d(TAG, "handleEndOfFile: no autoplay and no close -> stay on current")
                }
            } else {
                Log.d(TAG, "handleEndOfFile: single video, closeAfterEOF=${preferencesManager.isCloseAfterEndOfVideo()}")
                if (preferencesManager.isCloseAfterEndOfVideo()) {
                    finishAndRemoveTask()
                }
            }
        } finally {
            isHandlingEndOfFile = false
        }
    }

    /**
     * 从文件夹生成播放列表
     * 百分百复用 mpvEx 的 generatePlaylistFromFolder 算法
     */
    fun generatePlaylistFromFolder(currentPath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val currentFile = java.io.File(currentPath)
                if (!currentFile.exists()) return@runCatching

                val parentFolder = currentFile.parentFile ?: return@runCatching

                // 视频扩展名
                val videoExtensions = setOf(
                    "mp4", "mkv", "avi", "mov", "flv", "wmv",
                    "webm", "m4v", "3gp", "ts", "m2ts", "mts",
                    "ogv", "ogm", "rmvb", "rm", "asf", "vob",
                    "divx", "xvid", "f4v", "mpeg", "mpg"
                )

                // 列出文件夹中的视频文件（百分百复用 mpvEx 逻辑：过滤视频扩展名 + 排除隐藏文件）
                val files = parentFolder.listFiles { file ->
                    file.isFile &&
                        file.extension.lowercase() in videoExtensions &&
                        !file.name.startsWith(".")
                } ?: return@runCatching

                // 按文件名自然排序
                val siblingFiles = files.sortedWith { f1, f2 ->
                    compareNaturalFileNames(f1.name, f2.name)
                }

                if (siblingFiles.size <= 1) return@runCatching

                val newPlaylist = siblingFiles.map { Uri.fromFile(it) }

                val newIndex = siblingFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }

                if (newIndex != -1) {
                    withContext(Dispatchers.Main) {
                        playlist = newPlaylist
                        playlistIndex = newIndex
                        // 同步 ViewModel
                        viewModel.syncPlaylistFromActivity(newPlaylist, newIndex)
                        Log.d(TAG, "Auto-playlist generated: ${playlist.size} videos")
                        if (viewModel.shuffleEnabled.value) {
                            onShuffleToggled(true)
                        }
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to auto-generate playlist", e)
            }
        }
    }

    /**
     * 自然排序文件名比较器（百分百复用 mpvEx 的 NaturalOrderComparator 逻辑）
     */
    private fun compareNaturalFileNames(name1: String, name2: String): Int {
        val s1 = name1.lowercase()
        val s2 = name2.lowercase()
        var i1 = 0
        var i2 = 0
        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]
            if (c1.isDigit() && c2.isDigit()) {
                var num1 = 0L
                var num2 = 0L
                while (i1 < s1.length && s1[i1].isDigit()) {
                    num1 = num1 * 10 + (s1[i1] - '0')
                    i1++
                }
                while (i2 < s2.length && s2[i2].isDigit()) {
                    num2 = num2 * 10 + (s2[i2] - '0')
                    i2++
                }
                val cmp = num1.compareTo(num2)
                if (cmp != 0) return cmp
            } else {
                if (c1 != c2) return c1.compareTo(c2)
                i1++
                i2++
            }
        }
        return (s1.length - i1).compareTo(s2.length - i2)
    }

    /**
     * 检查当前播放列表是否为 M3U 播放列表
     */
    fun isCurrentPlaylistM3U(): Boolean = isM3uPlaylist
}

fun Int.dpToPx(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
