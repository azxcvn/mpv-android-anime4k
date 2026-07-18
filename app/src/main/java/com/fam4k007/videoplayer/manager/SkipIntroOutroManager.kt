package com.fam4k007.videoplayer.manager

import android.content.Context
import com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger

/**
 * 片头片尾跳过管理器
 * 负责处理视频播放时的片头片尾自动跳过逻辑
 * 支持两种跳过方式：
 * 1. 手动时间跳过（用户设置固定秒数）
 * 2. 章节关键词自动检测 OP/ED（PlayerViewModel.refreshChapterDerivedSegments）
 */
class SkipIntroOutroManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val composeOverlayManager: ComposeOverlayManager
) {
    companion object {
        private const val TAG = "SkipIntroOutroManager"
    }

    private var hasSkippedIntro = false
    private var hasShownOutroWarning = false
    private var isVideoReady = false

    fun resetFlags() {
        hasSkippedIntro = false
        hasShownOutroWarning = false
        isVideoReady = false
    }

    fun markVideoReady() {
        isVideoReady = true
    }

    /**
     * 显示片头片尾设置抽屉
     */
    fun showSkipSettingsDrawer(
        folderPath: String?,
        getCurrentPosition: () -> Double = { 0.0 },
        getDuration: () -> Double = { 0.0 }
    ) {
        if (folderPath == null) return

        val skipIntro = preferencesManager.getSkipIntroSeconds(folderPath)
        val skipOutro = preferencesManager.getSkipOutroSeconds(folderPath)
        val introRange = preferencesManager.getSkipIntroRangeSeconds(folderPath)
        val outroRange = preferencesManager.getSkipOutroRangeSeconds(folderPath)
        val enabled = preferencesManager.isSkipIntroOutroEnabled()

        composeOverlayManager.showSkipSettingsDrawer(
            enabled = enabled,
            currentSkipIntro = skipIntro,
            currentSkipOutro = skipOutro,
            currentIntroRange = introRange,
            currentOutroRange = outroRange,
            getCurrentPosition = getCurrentPosition,
            getDuration = getDuration,
            onEnabledChange = { preferencesManager.setSkipIntroOutroEnabled(it) },
            onIntroRangeChange = { preferencesManager.setSkipIntroRangeSeconds(folderPath, it) },
            onOutroRangeChange = { preferencesManager.setSkipOutroRangeSeconds(folderPath, it) },
            onSkipIntroChange = { seconds ->
                preferencesManager.setSkipIntroSeconds(folderPath, seconds)
            },
            onSkipOutroChange = { seconds ->
                preferencesManager.setSkipOutroSeconds(folderPath, seconds)
            },
            onReset = {
                preferencesManager.setSkipIntroSeconds(folderPath, 0)
                preferencesManager.setSkipOutroSeconds(folderPath, 0)
                preferencesManager.setSkipIntroRangeSeconds(folderPath, 180)
                preferencesManager.setSkipOutroRangeSeconds(folderPath, 180)
            }
        )
    }

    /**
     * 处理片头片尾跳过逻辑（手动时间跳过模式）
     *
     * 与章节关键词自动检测（PlayerViewModel.refreshChapterDerivedSegments）并存。
     * 受全局开关 preferencesManager.isSkipIntroOutroEnabled() 控制。
     *
     * @param folderPath 当前视频所在文件夹路径
     * @param position 当前播放位置（秒）
     * @param duration 视频总时长（秒）
     * @param getChapters 获取视频章节列表的回调（保留兼容）
     * @param seekTo 跳转到指定位置的回调
     * @param onOutroReached 到达片尾时的回调（返回是否有下一集）
     */
    fun handleSkipIntroOutro(
        folderPath: String?,
        position: Double,
        duration: Double,
        getChapters: () -> List<Pair<String, Double>>,
        seekTo: (Int) -> Unit,
        onOutroReached: () -> Boolean
    ) {
        if (folderPath == null) return
        if (!isVideoReady) return
        if (duration <= 0) return
        if (!preferencesManager.isSkipIntroOutroEnabled()) return

        val skipIntro = preferencesManager.getSkipIntroSeconds(folderPath)
        val skipOutro = preferencesManager.getSkipOutroSeconds(folderPath)

        // 跳过片头：位置还在片头范围内时自动 seek
        if (skipIntro > 0 && !hasSkippedIntro && position < skipIntro) {
            hasSkippedIntro = true
            Logger.d(TAG, "Auto-skip intro: seek from ${position.toInt()}s to ${skipIntro}s")
            seekTo(skipIntro)
            return
        }

        // 标记片头已过（防止短于片头时间的视频误判）
        if (skipIntro > 0 && position >= skipIntro) {
            hasSkippedIntro = true
        }

        // 片尾跳转下一集
        if (skipOutro > 0 && !hasShownOutroWarning &&
            position >= (duration - skipOutro) && duration > skipOutro
        ) {
            hasShownOutroWarning = true
            Logger.d(TAG, "Outro reached: position=${position.toInt()}s, duration=${duration.toInt()}s, skipOutro=${skipOutro}s")
            val hasNext = onOutroReached()
            if (!hasNext) {
                Logger.d(TAG, "No next video available, outro skip aborted")
            }
        }
    }
}
