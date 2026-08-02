package com.fam4k007.videoplayer.domain.danmaku

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fam4k007.videoplayer.danmaku.DanmakuConfig
import com.fam4k007.videoplayer.danmaku.DanmakuPlayerView
import com.fam4k007.videoplayer.domain.player.PlaybackEngine
import com.fam4k007.videoplayer.utils.DialogUtils
import java.io.File

/**
 * 弹幕管理器
 * 负责弹幕的加载、控制、样式管理
 * 使用状态机模式自动管理弹幕生命周期：
 * - 当弹幕准备完成 (isPrepared) 且播放引擎正在播放 (isEnginePlaying) 时自动 start()
 * - 当播放引擎暂停时自动 pause()
 * 无需外部手动管理播放/暂停状态
 */
class DanmakuManager(
    private val context: Context,
    private val danmakuView: DanmakuPlayerView
) {
    companion object {
        private const val TAG = "DanmakuManager"
    }

    private var isInitialized = false
    
    // 记录当前加载的弹幕文件路径（参考 DanDanPlay 的 mAddedTrack）
    private var currentDanmakuPath: String? = null
    
    // 播放引擎引用（用于提供播放位置）
    private var playbackEngine: PlaybackEngine? = null
    
    // ==================== 状态机 ====================
    // 引擎播放状态（由 onPlaybackStateChanged 更新）
    private var isEnginePlaying = false
    // 弹幕是否已准备完成（由 onPreparedListener 回调更新）
    private var isDanmakuPrepared = false

    /**
     * 初始化弹幕配置
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "DanmakuManager already initialized")
            return
        }

        try {
            DanmakuConfig.init(context)
            isInitialized = true
            
            // 重新应用所有样式设置（因为 DanmakuPlayerView 在 DanmakuConfig.init() 之前就被创建了）
            danmakuView.updateDanmakuSize()
            danmakuView.updateDanmakuSpeed()
            danmakuView.updateDanmakuAlpha()
            danmakuView.updateDanmakuStroke()
            danmakuView.updateScrollDanmakuState()
            danmakuView.updateTopDanmakuState()
            danmakuView.updateBottomDanmakuState()
            danmakuView.updateMaxLine()
            danmakuView.updateMaxScreenNum()
            
            Log.d(TAG, "DanmakuManager initialized successfully with saved settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DanmakuManager", e)
        }
    }
    
    /**
     * 连接播放引擎（参考 DanDanPlay 的 ControlWrapper 设计）
     * 自动建立弹幕与播放引擎的绑定关系：
     * 1. 设置 PlaybackPositionProvider 用于进度同步
     * 2. 注册 onPreparedListener 用于状态机驱动
     */
    fun connectToEngine(engine: PlaybackEngine) {
        playbackEngine = engine
        
        // 创建 PlaybackPositionProvider 并设置到 DanmakuView
        val provider = object : DanmakuPlayerView.PlaybackPositionProvider {
            override fun getCurrentPosition(): Long {
                return (engine.currentPosition * 1000).toLong()
            }
            
            override fun isPlaying(): Boolean {
                return engine.isPlaying
            }
        }
        danmakuView.setPlaybackPositionProvider(provider)
        
        // 注册弹幕准备完成回调（状态机驱动）
        danmakuView.onPreparedListener = { onDanmakuPrepared() }
        
        Log.d(TAG, "connectToEngine: PlaybackEngine connected, prepared listener registered")
    }
    
    /**
     * 播放状态变化回调（由 ViewModel 状态监听器调用）
     * 状态机核心：当播放状态变化时，自动同步弹幕的播放/暂停
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        isEnginePlaying = isPlaying
        syncDanmakuState()
    }
    
    /**
     * 弹幕准备完成回调（由 DanmakuPlayerView.onPreparedListener 调用）
     * 状态机核心：当弹幕准备完成时，检查播放状态并自动启动
     */
    private fun onDanmakuPrepared() {
        isDanmakuPrepared = true
        Log.d(TAG, "onDanmakuPrepared: isEnginePlaying=$isEnginePlaying, trackSelected=${danmakuView.getTrackSelected()}")
        syncDanmakuState()
    }
    
    /**
     * 状态机同步：检查弹幕和引擎的状态，自动控制弹幕播放/暂停
     * 解决了弹幕启动的时序难题——无论 prepared 和 isPlaying 谁先到达，都能正确启动
     *
     * 关键设计：
     * - 如果弹幕处于暂停状态（isPaused），用 resume() 恢复渲染
     * - 否则用 start() 首次启动（DanmakuFlameMaster 的 start() 在已启动时会内部调用 resume()）
     */
    private fun syncDanmakuState() {
        if (!isDanmakuPrepared) {
            Log.d(TAG, "syncDanmakuState: danmaku not prepared yet, skipping")
            return
        }
        if (!danmakuView.getTrackSelected()) {
            Log.d(TAG, "syncDanmakuState: track not selected, skipping")
            return
        }
        
        if (isEnginePlaying) {
            if (danmakuView.isPaused) {
                // 之前被暂停了，用 resume 恢复
                danmakuView.resumeDanmaku()
                Log.d(TAG, "syncDanmakuState: auto-resumed (was paused, engine now playing)")
            } else {
                // 首次启动或已经播放中，start() 是安全的
                danmakuView.startDanmaku()
                Log.d(TAG, "syncDanmakuState: auto-started (engine playing)")
            }
        } else {
            danmakuView.pauseDanmaku()
            Log.d(TAG, "syncDanmakuState: auto-paused (engine paused)")
        }
    }

    /**
     * 加载弹幕文件
     * 自动根据视频文件路径查找同名的 .xml 弹幕文件
     * 启动时序由状态机自动管理，无需外部传入 isPlaying
     */
    fun loadDanmakuForVideo(videoPath: String, autoShow: Boolean = true): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "DanmakuManager not initialized")
            return false
        }

        // 即使弹幕功能被禁用，也要继续加载（只是不显示）
        // 这样可以保证弹幕文件被记录，用户开启弹幕功能后可以直接使用

        // 查找同名弹幕文件
        val danmakuFile = findDanmakuFile(videoPath)
        if (danmakuFile == null) {
            Log.d(TAG, "No danmaku file found for video: $videoPath")
            return false
        }

        // 重置状态机，准备加载新弹幕
        isDanmakuPrepared = false

        // 加载弹幕
        val loaded = danmakuView.loadDanmaku(danmakuFile.absolutePath)
        if (loaded) {
            currentDanmakuPath = danmakuFile.absolutePath
            // 参考 DanDanPlay: addTrack 成功后自动调用 selectTrack
            danmakuView.setTrackSelected(autoShow)
            Log.d(TAG, "Danmaku loaded: ${danmakuFile.absolutePath}, autoShow=$autoShow (state machine will handle start)")
        }
        return loaded
    }

    /**
     * 加载指定的弹幕文件（用户手动选择或历史恢复）
     * 参考 DanDanPlay 的 addTrack 方法
     * 启动时序由状态机自动管理（无需延迟重试）
     */
    fun loadDanmakuFile(danmakuPath: String, autoShow: Boolean = true): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "DanmakuManager not initialized")
            return false
        }

        // 重置状态机，准备加载新弹幕
        isDanmakuPrepared = false

        val loaded = danmakuView.loadDanmaku(danmakuPath)
        
        if (loaded) {
            currentDanmakuPath = danmakuPath
            danmakuView.setTrackSelected(autoShow)
            Log.d(TAG, "Danmaku loaded and track selected: $danmakuPath, autoShow=$autoShow (state machine will handle start)")
        }
        
        return loaded
    }

    /**
     * 导入弹幕文件（从 URI 复制到内部存储）
     * @param context Android Context
     * @param uri 弹幕文件的 URI
     * @return 复制后的弹幕文件路径，失败返回 null
     */
    fun importDanmakuFile(context: Context, uri: Uri): String? {
        return try {
            val danmakuDir = File(context.filesDir, "danmaku")
            if (!danmakuDir.exists()) {
                danmakuDir.mkdirs()
            }
            
            // 获取文件名
            var fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            } ?: "danmaku_${System.currentTimeMillis()}.xml"
            
            // 确保文件有.xml后缀（兼容弹弹play缓存的无后缀文件）
            if (!fileName.endsWith(".xml", ignoreCase = true)) {
                fileName = "$fileName.xml"
                Log.d(TAG, "Added .xml extension to danmaku file: $fileName")
            }
            
            val danmakuFile = File(danmakuDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                danmakuFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (danmakuFile.exists()) {
                Log.d(TAG, "Danmaku file imported: ${danmakuFile.absolutePath}")
                // 自动加载导入的弹幕
                val loaded = loadDanmakuFile(danmakuFile.absolutePath, autoShow = true)
                if (loaded) {
                    danmakuFile.absolutePath
                } else {
                    null
                }
            } else {
                Log.e(TAG, "Failed to import danmaku file")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import danmaku file", e)
            null
        }
    }

    /**
     * 查找弹幕文件
     * 规则：视频文件同目录下，同名的弹幕文件
     * 支持多种弹幕格式和命名规则
     */
    private fun findDanmakuFile(videoPath: String): File? {
        try {
            Log.d(TAG, "===== Finding danmaku file =====")
            Log.d(TAG, "Video path: $videoPath")
            
            val videoFile = File(videoPath)
            val videoDir = videoFile.parentFile
            
            if (videoDir == null) {
                Log.d(TAG, "✗ Parent directory is null")
                return null
            }
            
            if (!videoDir.exists() || !videoDir.isDirectory) {
                Log.d(TAG, "✗ Parent directory does not exist or is not a directory: ${videoDir.absolutePath}")
                return null
            }
            
            val videoNameWithoutExt = videoFile.nameWithoutExtension
            Log.d(TAG, "Video name without ext: $videoNameWithoutExt")
            Log.d(TAG, "Searching in directory: ${videoDir.absolutePath}")

            // 优先级1：查找标准同名 .xml 文件
            val danmakuFile = File(videoDir, "$videoNameWithoutExt.xml")
            if (danmakuFile.exists() && danmakuFile.isFile) {
                Log.d(TAG, "✓ Found danmaku file: ${danmakuFile.absolutePath}")
                return danmakuFile
            }

            // 优先级2：查找其他常见命名格式
            val alternativeNames = listOf(
                // B站格式
                "${videoNameWithoutExt}.danmaku.xml",
                "${videoNameWithoutExt}_danmaku.xml",
                // 弹弹play格式
                "${videoNameWithoutExt}.dandan.xml",
                "${videoNameWithoutExt}_dandan.xml",
                // AcFun格式
                "${videoNameWithoutExt}.acfun.xml",
                // 通用格式
                "danmaku.xml",
                "danmaku.xml",
                // 无扩展名（兼容弹弹play缓存）
                videoNameWithoutExt
            )

            Log.d(TAG, "Trying alternative names...")
            for (name in alternativeNames) {
                val file = File(videoDir, name)
                if (file.exists() && file.isFile) {
                    Log.d(TAG, "✓ Found alternative danmaku file: ${file.absolutePath}")
                    return file
                }
            }

            Log.d(TAG, "✗ No danmaku file found for: $videoNameWithoutExt")
            Log.d(TAG, "Directory contents:")
            videoDir.listFiles()?.take(10)?.forEach { file ->
                Log.d(TAG, "  - ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding danmaku file", e)
        }

        return null
    }

    /**
     * 开始播放弹幕
     */
    fun start() {
        danmakuView.startDanmaku()
    }

    /**
     * 暂停弹幕
     */
    fun pause() {
        danmakuView.pauseDanmaku()
    }

    /**
     * 恢复弹幕
     */
    fun resume() {
        danmakuView.resumeDanmaku()
    }

    /**
     * 同步进度
     * @param timeMs 时间（毫秒）
     * @param isPlaying 是否正在播放（默认 true）
     */
    fun seekTo(timeMs: Long, isPlaying: Boolean = true) {
        danmakuView.seekDanmaku(timeMs, isPlaying)
    }

    /**
     * 切换弹幕显示/隐藏
     */
    fun toggleVisibility() {
        danmakuView.toggleDanmakuVisibility()
        Log.d(TAG, "Danmaku visibility toggled to: ${danmakuView.isShown}")
    }

    /**
     * 设置弹幕轨道选中状态（参考 DanDanPlay 的 setTrackSelected）
     * @param selected true=选中轨道并显示弹幕，false=取消选中并隐藏弹幕
     */
    fun setTrackSelected(selected: Boolean) {
        danmakuView.setTrackSelected(selected)
        Log.d(TAG, "Danmaku track selected: $selected")
    }
    
    /**
     * 获取弹幕轨道选中状态
     */
    fun getTrackSelected(): Boolean {
        return danmakuView.getTrackSelected()
    }

    /**
     * 设置弹幕显示状态
     */
    fun setVisibility(visible: Boolean) {
        if (visible) {
            danmakuView.show()
        } else {
            danmakuView.hide()
        }
        Log.d(TAG, "Danmaku visibility set to: $visible")
    }

    /**
     * 获取弹幕当前显示状态
     */
    fun isVisible(): Boolean {
        return danmakuView.isShown
    }

    /**
     * 获取当前加载的弹幕文件路径（参考 DanDanPlay 的 getAddedTrack）
     */
    fun getCurrentDanmakuPath(): String? {
        return currentDanmakuPath
    }
    
    /**
     * 弹幕是否已准备好
     */
    fun isPrepared(): Boolean {
        return danmakuView.isDanmakuPrepared()
    }

    /**
     * 释放资源
     */
    fun release() {
        currentDanmakuPath = null
        isDanmakuPrepared = false
        isEnginePlaying = false
        danmakuView.releaseDanmaku()
    }

    /**
     * 设置播放倍速
     */
    fun setSpeed(speed: Float) {
        danmakuView.setPlaybackSpeed(speed)
    }

    // ==================== 样式设置方法 ====================

    fun updateSize() {
        danmakuView.updateDanmakuSize()
    }

    fun updateSpeed() {
        danmakuView.updateDanmakuSpeed()
    }

    fun updateAlpha() {
        danmakuView.updateDanmakuAlpha()
    }

    fun updateStroke() {
        danmakuView.updateDanmakuStroke()
    }

    fun updateScrollDanmaku() {
        danmakuView.updateScrollDanmakuState()
    }

    fun updateTopDanmaku() {
        danmakuView.updateTopDanmakuState()
    }

    fun updateBottomDanmaku() {
        danmakuView.updateBottomDanmakuState()
    }

    fun updateMaxLine() {
        danmakuView.updateMaxLine()
    }

    fun updateMaxScreenNum() {
        danmakuView.updateMaxScreenNum()
    }

    fun updateOffsetTime() {
        danmakuView.updateOffsetTime()
    }

    /**
     * 应用所有样式设置
     */
    fun applyAllSettings() {
        updateSize()
        updateSpeed()
        updateAlpha()
        updateStroke()
        updateScrollDanmaku()
        updateTopDanmaku()
        updateBottomDanmaku()
        updateMaxLine()
        updateMaxScreenNum()
    }
}
