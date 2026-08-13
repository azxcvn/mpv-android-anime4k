package com.fam4k007.videoplayer.danmaku

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer.DANMAKU_STYLE_STROKEN
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import java.io.File
import kotlin.math.max

/**
 * 弹幕视图 - 参考 DanDanPlay 实现
 */
class DanmakuPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DanmakuView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DanmakuPlayerView"
        private const val DANMU_MAX_TEXT_SIZE = 2f
        private const val DANMU_MAX_TEXT_ALPHA = 1f
        private const val DANMU_MAX_TEXT_SPEED = 2.5f
        private const val DANMU_MAX_TEXT_STROKE = 20f
        private const val INVALID_POSITION = -1L  // 使用常量代替魔法数字
    }
    
    /**
     * 播放位置提供接口（参考 DanDanPlay 的 ControlWrapper）
     */
    interface PlaybackPositionProvider {
        fun getCurrentPosition(): Long  // 返回毫秒
        fun isPlaying(): Boolean
    }

    private val danmakuContext = DanmakuContext.create()
    private val danmakuLoader = BiliDanmakuLoader.instance()

    // 当前加载的弹幕文件路径
    private var currentDanmakuPath: String? = null
    
    // 弹幕是否加载完成（prepared回调触发后为true）
    private var danmakuLoaded = false
    
    // 待应用的seek位置（在prepared之前调用seekTo会保存在这里）
    private var pendingSeekPosition: Long = INVALID_POSITION
    
    // 弹幕轨道是否被选中（参考 DanDanPlay 的 mTrackSelected）
    private var trackSelected = false
    
    // 播放位置提供器（用于 prepared 回调中自动同步）
    private var positionProvider: PlaybackPositionProvider? = null
    
    // 弹幕准备完成回调（由 DanmakuManager 注册，用于状态机驱动）
    var onPreparedListener: (() -> Unit)? = null

    init {
        // 显示 FPS（调试用）
        showFPS(DanmakuConfig.isDebug)

        initDanmakuContext()

        setCallback(object : DrawHandler.Callback {
            override fun drawingFinished() {
            }

            override fun danmakuShown(danmaku: BaseDanmaku?) {
            }

            override fun prepared() {
                // 弹幕准备完成
                danmakuLoaded = true
                Log.d(TAG, "Danmaku prepared, trackSelected=$trackSelected, isShown=$isShown")
                
                // 应用待处理的 seek 位置（优先级最高）
                if (pendingSeekPosition != INVALID_POSITION) {
                    seekTo(pendingSeekPosition)
                    pendingSeekPosition = INVALID_POSITION
                    Log.d(TAG, "Applied pending seek position")
                } else {
                    // 没有 pending seek，同步到当前播放位置
                    positionProvider?.let { provider ->
                        if (trackSelected) {
                            val currentPosition = provider.getCurrentPosition()
                            seekTo(currentPosition + DanmakuConfig.offsetTime)
                            Log.d(TAG, "Synced to current position: $currentPosition ms")
                        }
                    }
                }
                
                // 设置可见性
                setDanmuVisible(trackSelected)
                
                // 通知 DanmakuManager 弹幕已准备完成（状态机会决定是否 start）
                onPreparedListener?.invoke()
            }

            override fun updateTimer(timer: DanmakuTimer?) {
                // 定时器更新回调
            }
        })
    }

    /**
     * 初始化弹幕上下文
     */
    private fun initDanmakuContext() {
        // 设置禁止重叠 - 与 DanDanPlay 完全一致
        val overlappingPair: MutableMap<Int, Boolean> = HashMap()
        overlappingPair[BaseDanmaku.TYPE_SCROLL_LR] = true
        overlappingPair[BaseDanmaku.TYPE_SCROLL_RL] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_TOP] = true
        overlappingPair[BaseDanmaku.TYPE_FIX_BOTTOM] = true

        // 弹幕更新方式：0=Choreographer（高刷适配）, 1=new Thread, 2=DrawHandler（稳定）
        val danmuUpdateMethod: Byte = if (DanmakuConfig.updateInChoreographer) 0 else 2

        danmakuContext.apply {
            // 合并重复弹幕
            isDuplicateMergingEnabled = true
            // 弹幕view开启绘制缓存
            enableDanmakuDrawingCache(true)
            // 设置禁止重叠
            preventOverlapping(overlappingPair)
            // 设置更新方式
            updateMethod = danmuUpdateMethod
        }

        // 应用所有样式设置
        updateDanmakuSize()
        updateDanmakuSpeed()
        updateDanmakuAlpha()
        updateDanmakuStroke()
        updateScrollDanmakuState()
        updateTopDanmakuState()
        updateBottomDanmakuState()
        updateMaxLine()
        updateMaxScreenNum()
    }

    /**
     * 加载弹幕文件
     */
    fun loadDanmaku(filePath: String): Boolean {
        try {
            Log.d(TAG, "Loading danmaku file: $filePath")
            
            val danmuFile = File(filePath)
            if (!danmuFile.exists()) {
                Log.e(TAG, "Danmaku file not exists: $filePath")
                Toast.makeText(context, "弹幕文件不存在", Toast.LENGTH_SHORT).show()
                return false
            }

            // 释放之前的弹幕
            releaseDanmaku()

            // 加载弹幕文件
            danmakuLoader.load(filePath)
            val dataSource = danmakuLoader.dataSource
            if (dataSource == null) {
                Log.e(TAG, "Failed to load danmaku data source")
                Toast.makeText(context, "弹幕加载失败", Toast.LENGTH_SHORT).show()
                return false
            }

            currentDanmakuPath = filePath

            // 设置随机渐变色开关（在解析前设置，解析时自动覆盖颜色）
            BiliDanmakuParser.setRandomColorEnabled(DanmakuConfig.randomGradientColor)
            
            // 创建解析器并准备
            val danmuParser = BiliDanmakuParser().apply {
                load(dataSource)
            }
            prepare(danmuParser, danmakuContext)

            Log.d(TAG, "Danmaku file loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading danmaku", e)
            Toast.makeText(context, "弹幕加载异常: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * 开始播放弹幕
     */
    fun startDanmaku() {
        if (isPrepared && trackSelected) {
            start()
            Log.d(TAG, "Danmaku started")
        } else {
            Log.d(TAG, "Danmaku not started: isPrepared=$isPrepared, trackSelected=$trackSelected")
        }
    }

    /**
     * 暂停弹幕
     */
    fun pauseDanmaku() {
        if (isPrepared) {
            pause()
            Log.d(TAG, "Danmaku paused")
        }
    }

    /**
     * 恢复弹幕
     */
    fun resumeDanmaku() {
        if (isPrepared && trackSelected) {
            resume()
            Log.d(TAG, "Danmaku resumed")
        } else {
            Log.d(TAG, "Danmaku not resumed: isPrepared=$isPrepared, trackSelected=$trackSelected")
        }
    }

    /**
     * 同步弹幕进度（参考 DanDanPlay 的设计，添加 isPlaying 参数）
     * @param timeMs 目标时间（毫秒）
     * @param isPlaying 是否正在播放（默认 true 保持向后兼容）
     */
    fun seekDanmaku(timeMs: Long, isPlaying: Boolean = true) {
        if (isPlaying && isPrepared && danmakuLoaded) {
            // 已加载且正在播放：立即 seek
            val adjustedTime = timeMs + DanmakuConfig.offsetTime
            seekTo(adjustedTime)
            Log.d(TAG, "Danmaku seeked to: $adjustedTime ms")
        } else {
            // 未加载或暂停：保存为 pending
            pendingSeekPosition = timeMs + DanmakuConfig.offsetTime
            Log.d(TAG, "Danmaku not ready, pending seek to: $pendingSeekPosition ms")
        }
    }

    /**
     * 释放弹幕资源
     */
    fun releaseDanmaku() {
        currentDanmakuPath = null
        danmakuLoaded = false
        pendingSeekPosition = INVALID_POSITION
        // 注意：不清除 positionProvider，它由外部 DanmakuManager.setPlaybackEngine() 设置
        // 清除后会导致 prepared() 回调中无法调用 start()，弹幕不会自动启动
        hide()
        clear()
        clearDanmakusOnScreen()
        release()
        Log.d(TAG, "Danmaku released")
    }
    
    /**
     * 设置播放位置提供器（用于 prepared 回调中自动同步）
     */
    fun setPlaybackPositionProvider(provider: PlaybackPositionProvider?) {
        positionProvider = provider
        Log.d(TAG, "PlaybackPositionProvider set: ${provider != null}")
    }

    /**
     * 显示/隐藏弹幕
     */
    fun toggleDanmakuVisibility() {
        if (trackSelected.not()) {
            Log.d(TAG, "Danmaku track not selected, cannot toggle visibility")
            return
        }
        
        if (isShown) {
            hide()
            Log.d(TAG, "Danmaku hidden")
        } else {
            show()
            // 显示弹幕时，如果已经prepared且不是暂停状态，立即启动
            if (isPrepared && !isPaused) {
                resume()
                Log.d(TAG, "Danmaku shown and resumed")
            } else {
                Log.d(TAG, "Danmaku shown (paused state)")
            }
        }
    }
    
    /**
     * 设置弹幕轨道选中状态（参考 DanDanPlay 的 setTrackSelected）
     */
    fun setTrackSelected(selected: Boolean) {
        trackSelected = selected
        Log.d(TAG, "Danmaku track selected: $selected, isPrepared=$isPrepared")
        
        // 只在已经 prepared 的情况下立即应用可见性
        if (isPrepared) {
            setDanmuVisible(selected)
        }
        // 否则等待 prepared 回调来处理
    }
    
    /**
     * 获取弹幕轨道选中状态
     */
    fun getTrackSelected(): Boolean {
        return trackSelected
    }
    
    /**
     * 设置弹幕可见性（参考 DanDanPlay 的 setDanmuVisible）
     */
    private fun setDanmuVisible(visible: Boolean) {
        if (visible) {
            show()
        } else {
            hide()
        }
    }

    /**
     * 获取当前弹幕文件路径
     */
    fun getCurrentDanmakuPath(): String? = currentDanmakuPath
    
    /**
     * 弹幕是否已准备
     */
    fun isDanmakuPrepared(): Boolean = danmakuLoaded && isPrepared

    // ==================== 样式更新方法 ====================

    fun updateDanmakuSize() {
        val progress = DanmakuConfig.size / 100f
        val size = progress * DANMU_MAX_TEXT_SIZE
        danmakuContext.setScaleTextSize(size)
        Log.d(TAG, "Danmaku size updated: $size")
    }

    fun updateDanmakuSpeed() {
        val progress = DanmakuConfig.speed / 100f
        var speed = DANMU_MAX_TEXT_SPEED * (1 - progress)
        speed = max(0.1f, speed)
        danmakuContext.setScrollSpeedFactor(speed)
        Log.d(TAG, "Danmaku speed updated: $speed")
    }

    fun updateDanmakuAlpha() {
        val progress = DanmakuConfig.alpha / 100f
        val alpha = progress * DANMU_MAX_TEXT_ALPHA
        danmakuContext.setDanmakuTransparency(alpha)
        Log.d(TAG, "Danmaku alpha updated: $alpha")
    }

    fun updateDanmakuStroke() {
        val progress = DanmakuConfig.stroke / 100f
        val stroke = progress * DANMU_MAX_TEXT_STROKE
        danmakuContext.setDanmakuStyle(DANMAKU_STYLE_STROKEN, stroke)
        Log.d(TAG, "Danmaku stroke updated: $stroke")
    }

    fun updateScrollDanmakuState() {
        danmakuContext.r2LDanmakuVisibility = DanmakuConfig.showScrollDanmaku
        Log.d(TAG, "Scroll danmaku visibility: ${DanmakuConfig.showScrollDanmaku}")
    }

    fun updateTopDanmakuState() {
        danmakuContext.ftDanmakuVisibility = DanmakuConfig.showTopDanmaku
        Log.d(TAG, "Top danmaku visibility: ${DanmakuConfig.showTopDanmaku}")
    }

    fun updateBottomDanmakuState() {
        danmakuContext.fbDanmakuVisibility = DanmakuConfig.showBottomDanmaku
        Log.d(TAG, "Bottom danmaku visibility: ${DanmakuConfig.showBottomDanmaku}")
    }

    fun updateMaxLine() {
        val danmuMaxLineMap: MutableMap<Int, Int?> = mutableMapOf()

        val scrollLine = DanmakuConfig.maxScrollLine
        val topLine = DanmakuConfig.maxTopLine
        val bottomLine = DanmakuConfig.maxBottomLine
        
        danmuMaxLineMap[BaseDanmaku.TYPE_SCROLL_LR] = if (scrollLine > 0) scrollLine else null
        danmuMaxLineMap[BaseDanmaku.TYPE_SCROLL_RL] = if (scrollLine > 0) scrollLine else null
        danmuMaxLineMap[BaseDanmaku.TYPE_FIX_TOP] = if (topLine > 0) topLine else null
        danmuMaxLineMap[BaseDanmaku.TYPE_FIX_BOTTOM] = if (bottomLine > 0) bottomLine else null
        
        danmakuContext.setMaximumLines(danmuMaxLineMap)
        Log.d(TAG, "Max lines updated - scroll:$scrollLine, top:$topLine, bottom:$bottomLine")
    }

    fun updateMaxScreenNum() {
        val maxNum = DanmakuConfig.maxScreenNum
        danmakuContext.setMaximumVisibleSizeInScreen(maxNum)
        Log.d(TAG, "Max screen number updated: $maxNum")
    }

    fun updateOffsetTime() {
        if (isPrepared) {
            // 使用视频实际播放位置（未含偏移）作为基准，再叠加新的偏移，避免重复叠加
            val basePosition = positionProvider?.getCurrentPosition() ?: return
            seekTo(basePosition + DanmakuConfig.offsetTime)
        }
    }

    /**
     * 设置播放倍速（用于倍速播放时同步弹幕）
     * 使用 DanmakuFlameMaster 的 setSpeed() 加速/减速时间轴，
     * 使弹幕的出现时机和滚动速度都与视频倍速同步。
     * 参考 DanDanPlay 的 DanmuController.setSpeed() 实现方式。
     * 注意：不调整 setScrollSpeedFactor()，避免与用户设置的弹幕基础速度冲突。
     */
    fun setPlaybackSpeed(speed: Float) {
        danmakuContext.setSpeed(speed)
        Log.d(TAG, "Playback speed set to: $speed (timeline accelerated via setSpeed)")
    }
}
