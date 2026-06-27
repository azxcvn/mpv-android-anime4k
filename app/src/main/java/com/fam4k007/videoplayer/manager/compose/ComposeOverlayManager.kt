package com.fam4k007.videoplayer.manager.compose

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner

/**
 * 通用Compose覆盖层管理器
 * 负责管理Activity中的ComposeView容器,提供统一的Compose内容展示接口
 */
class ComposeOverlayManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val rootView: ViewGroup
) {
    private var composeView: ComposeView? = null
    
    // 弹窗显示状态回调
    var onPopupVisibilityChanged: ((Boolean) -> Unit)? = null

    // 是否禁用动画（超分开启时由外部设置为 true，消除 GPU 额外负载）
    var disableAnimations: Boolean = false

    companion object {
        /**
         * 全局动画开关，供所有抽屉面板读取。
         * 由 PlayerDialogManager 在超分模式变化时同步更新。
         */
        @Volatile
        var globalDisableAnimations: Boolean = false
    }

    /**
     * 设置Compose内容
     * @param content Composable内容
     */
    fun setContent(content: @Composable () -> Unit) {
        if (composeView == null) {
            composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setContent(content)
            }
            rootView.addView(composeView)
            // 通知popup状态变化
            onPopupVisibilityChanged?.invoke(true)
        } else {
            composeView?.setContent(content)
        }
    }

    /**
     * 清空内容并移除视图
     */
    fun clearContent() {
        composeView?.let {
            rootView.removeView(it)
        }
        composeView = null
        // 通知popup状态变化
        onPopupVisibilityChanged?.invoke(false)
    }

    /**
     * 检查是否有内容正在显示
     */
    fun hasContent(): Boolean = composeView != null

    /**
     * 释放资源
     */
    fun release() {
        clearContent()
    }

    // ===== 字幕对话框相关 =====

    /**
     * 显示字幕设置抽屉（统一入口）
     */
    fun showSubtitleSettingsDrawer(
        currentDelay: Double,
        currentScale: Float,
        currentPosition: Int,
        currentBorderSize: Int,
        currentTextColor: String,
        currentBorderColor: String,
        currentBackColor: String,
        currentBorderStyle: String,
        onDelayChange: (Double) -> Unit,
        onScaleChange: (Float) -> Unit,
        onPositionChange: (Int) -> Unit,
        onBorderSizeChange: (Int) -> Unit,
        onTextColorChange: (String) -> Unit,
        onBorderColorChange: (String) -> Unit,
        onBackColorChange: (String) -> Unit,
        onBorderStyleChange: (String) -> Unit
    ) {
        setContent {
            SubtitleSettingsDrawer(
                currentDelay = currentDelay,
                currentScale = currentScale,
                currentPosition = currentPosition,
                currentBorderSize = currentBorderSize,
                currentTextColor = currentTextColor,
                currentBorderColor = currentBorderColor,
                currentBackColor = currentBackColor,
                currentBorderStyle = currentBorderStyle,
                onDelayChange = onDelayChange,
                onScaleChange = onScaleChange,
                onPositionChange = onPositionChange,
                onBorderSizeChange = onBorderSizeChange,
                onTextColorChange = onTextColorChange,
                onBorderColorChange = onBorderColorChange,
                onBackColorChange = onBackColorChange,
                onBorderStyleChange = onBorderStyleChange,
                composeOverlayManager = this,
                onDismiss = { clearContent() }
            )
        }
    }
    
    // ===== 弹幕对话框相关 =====

    /**
     * 显示弹幕设置抽屉
     */
    // ===== 章节对话框相关 =====

    /**
     * 显示章节列表面板（右侧抽屉式）
     */
    fun showChapterDrawer(
        chapters: List<Pair<String, Double>>,
        currentChapter: Int,
        onChapterClick: (Int) -> Unit
    ) {
        val chapterItems = chapters.mapIndexed { _, (title, timeSeconds) ->
            val timeText = formatChapterTime(timeSeconds)
            com.fam4k007.videoplayer.manager.compose.ChapterItem(title, timeText)
        }
        setContent {
            com.fam4k007.videoplayer.manager.compose.ChapterDrawer(
                chapters = chapterItems,
                currentChapterIndex = currentChapter,
                onChapterClick = onChapterClick,
                composeOverlayManager = this,
                onDismiss = { clearContent() }
            )
        }
    }

    /**
     * 格式化章节时间
     */
    private fun formatChapterTime(seconds: Double): String {
        val totalSecs = seconds.toInt()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    fun showDanmakuSettingsDrawer(
        hasDanmakuLoaded: Boolean,
        currentSize: Int,
        currentSpeed: Int,
        currentAlpha: Int,
        currentStroke: Int,
        currentShowScroll: Boolean,
        currentShowTop: Boolean,
        currentShowBottom: Boolean,
        currentDisplayArea: Int,
        currentMaxScreenNum: Int,
        onSizeChange: (Int) -> Unit,
        onSpeedChange: (Int) -> Unit,
        onAlphaChange: (Int) -> Unit,
        onStrokeChange: (Int) -> Unit,
        onShowScrollChange: (Boolean) -> Unit,
        onShowTopChange: (Boolean) -> Unit,
        onShowBottomChange: (Boolean) -> Unit,
        onDisplayAreaChange: (Int) -> Unit,
        onMaxScreenNumChange: (Int) -> Unit
    ) {
        setContent {
            DanmakuSettingsDrawer(
                hasDanmakuLoaded = hasDanmakuLoaded,
                currentSize = currentSize,
                currentSpeed = currentSpeed,
                currentAlpha = currentAlpha,
                currentStroke = currentStroke,
                currentShowScroll = currentShowScroll,
                currentShowTop = currentShowTop,
                currentShowBottom = currentShowBottom,
                currentDisplayArea = currentDisplayArea,
                currentMaxScreenNum = currentMaxScreenNum,
                onSizeChange = onSizeChange,
                onSpeedChange = onSpeedChange,
                onAlphaChange = onAlphaChange,
                onStrokeChange = onStrokeChange,
                onShowScrollChange = onShowScrollChange,
                onShowTopChange = onShowTopChange,
                onShowBottomChange = onShowBottomChange,
                onDisplayAreaChange = onDisplayAreaChange,
                onMaxScreenNumChange = onMaxScreenNumChange,
                onDismiss = { clearContent() }
            )
        }
    }
    
    /**
     * 显示DanDanPlay网络弹幕搜索对话框
     */
    fun showDanDanPlaySearchDialog(
        onEpisodeSelected: (episodeId: Int, animeTitle: String, episodeTitle: String) -> Unit
    ) {
        setContent {
            com.fam4k007.videoplayer.ui.components.DanDanPlaySearchDialog(
                onDismiss = { clearContent() },
                onEpisodeSelected = onEpisodeSelected
            )
        }
    }
    
    /**
     * 显示弹幕文件选择器
     */
    fun showDanmakuFilePicker(
        initialPath: String?,
        onFileSelected: (String) -> Unit
    ) {
        setContent {
            DanmakuFilePickerDialog(
                initialPath = initialPath,
                onFileSelected = onFileSelected,
                onDismiss = { clearContent() }
            )
        }
    }
    
    /**
     * 显示字幕文件选择器
     */
    fun showSubtitleFilePicker(
        initialPath: String?,
        onFileSelected: (String) -> Unit
    ) {
        setContent {
            SubtitleFilePickerDialog(
                initialPath = initialPath,
                onFileSelected = onFileSelected,
                onDismiss = { clearContent() }
            )
        }
    }
    
    /**
     * 显示字幕延迟对话框（已废弃，保留兼容）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleDelayDialog(
        currentDelay: Double,
        onDelayChange: (Double) -> Unit
    ) {
        setContent {
            SubtitleDelayDialog(
                currentDelay = currentDelay,
                onDelayChange = onDelayChange,
                onDismiss = { clearContent() }
            )
        }
    }
    
    // ===== 音频均衡器 =====
    
    /**
     * 显示音频均衡器抽屉
     */
    fun showEqualizerDrawer(
        state: EqualizerState,
        onEnabledChange: (Boolean) -> Unit,
        onBandChange: (Int, Float) -> Unit,
        onBassBoostChange: (Int) -> Unit,
        onVirtualizerChange: (Int) -> Unit
    ) {
        setContent {
            EqualizerDrawer(
                state = state,
                onEnabledChange = onEnabledChange,
                onBandChange = onBandChange,
                onBassBoostChange = onBassBoostChange,
                onVirtualizerChange = onVirtualizerChange,
                onDismiss = { clearContent() }
            )
        }
    }
    
    // ===== 播放速度 =====

    /**
     * 显示播放速度抽屉
     */
    fun showSpeedDrawer(
        currentSpeed: Double,
        speedPresets: Set<String>,
        onSpeedChanged: (Double) -> Unit,
        onPresetsChanged: (Set<String>) -> Unit
    ) {
        setContent {
            SpeedDrawer(
                currentSpeed = currentSpeed,
                speedPresets = speedPresets,
                onSpeedChanged = onSpeedChanged,
                onPresetsChanged = onPresetsChanged,
                onDismiss = { clearContent() }
            )
        }
    }

    // ===== 片头片尾跳过设置 =====
    
    /**
     * 显示片头片尾跳过设置抽屉
     */
    fun showSkipSettingsDrawer(
        currentSkipIntro: Int,
        currentSkipOutro: Int,
        currentAutoSkipChapter: Boolean,
        currentSkipToChapterIndex: Int,
        onSkipIntroChange: (Int) -> Unit,
        onSkipOutroChange: (Int) -> Unit,
        onAutoSkipChapterChange: (Boolean) -> Unit,
        onSkipToChapterIndexChange: (Int) -> Unit
    ) {
        setContent {
            SkipSettingsDrawer(
                currentSkipIntro = currentSkipIntro,
                currentSkipOutro = currentSkipOutro,
                currentAutoSkipChapter = currentAutoSkipChapter,
                currentSkipToChapterIndex = currentSkipToChapterIndex,
                onSkipIntroChange = onSkipIntroChange,
                onSkipOutroChange = onSkipOutroChange,
                onAutoSkipChapterChange = onAutoSkipChapterChange,
                onSkipToChapterIndexChange = onSkipToChapterIndexChange,
                onDismiss = { clearContent() }
            )
        }
    }

    /**
     * 显示字幕杂项对话框（已废弃，保留兼容）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleMiscDialog(
        currentScale: Float,
        currentPosition: Int,
        onScaleChange: (Float) -> Unit,
        onPositionChange: (Int) -> Unit
    ) {
        setContent {
            SubtitleMiscDialog(
                currentScale = currentScale,
                currentPosition = currentPosition,
                onScaleChange = onScaleChange,
                onPositionChange = onPositionChange,
                onDismiss = { clearContent() }
            )
        }
    }

    /**
     * 显示字幕样式对话框（已废弃，保留兼容）
     */
    @Deprecated("使用 showSubtitleSettingsDrawer 替代")
    fun showSubtitleStyleDialog(
        currentBorderSize: Int,
        onBorderSizeChange: (Int) -> Unit
    ) {
        setContent {
            SubtitleStyleDialog(
                currentBorderSize = currentBorderSize,
                onBorderSizeChange = onBorderSizeChange,
                onDismiss = { clearContent() }
            )
        }
    }
    
    // ===== 视频列表相关 =====
    
    /**
     * 显示视频列表抽屉
     */
    fun showVideoListDrawer(
        videoList: List<com.fam4k007.videoplayer.VideoFileParcelable>,
        currentVideoUri: android.net.Uri,
        onVideoSelected: (com.fam4k007.videoplayer.VideoFileParcelable, Int) -> Unit
    ) {
        setContent {
            VideoListDrawer(
                videoList = videoList,
                currentVideoUri = currentVideoUri,
                onVideoSelected = onVideoSelected,
                onDismiss = { clearContent() }
            )
        }
    }

    /**
     * 显示杜比视界提示对话框
     * 当检测到视频为杜比视界编码且未开启 GPU Next 时弹出
     */
    fun showDolbyVisionDialog(
        onDontShowAgain: () -> Unit
    ) {
        setContent {
            DolbyVisionHintDialog(
                onDismiss = { clearContent() },
                onDontShowAgain = {
                    onDontShowAgain()
                    clearContent()
                }
            )
        }
    }
}
