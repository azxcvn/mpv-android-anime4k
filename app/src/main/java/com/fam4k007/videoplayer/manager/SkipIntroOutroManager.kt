package com.fam4k007.videoplayer.manager

import android.content.Context
import android.util.Log
import com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.DialogUtils

/**
 * 片头片尾跳过管理器
 * 负责处理视频播放时的片头片尾自动跳过逻辑
 */
class SkipIntroOutroManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val composeOverlayManager: ComposeOverlayManager
) {
    companion object {
        private const val TAG = "SkipIntroOutroManager"
    }
    
    // 标记是否已跳过片头
    private var hasSkippedIntro = false
    
    // 标记是否已显示片尾提示
    private var hasShownOutroWarning = false
    
    // 标记视频是否已真正开始播放（用于延迟片头跳过，等待加载完成）
    private var isVideoReady = false
    
    /**
     * 重置标记（切换视频时调用）
     */
    fun resetFlags() {
        hasSkippedIntro = false
        hasShownOutroWarning = false
        isVideoReady = false
    }
    
    /**
     * 标记视频已准备好（在视频真正开始播放后调用）
     */
    fun markVideoReady() {
        isVideoReady = true
    }
    
    /**
     * 显示片头片尾设置抽屉
     */
    fun showSkipSettingsDrawer(folderPath: String?) {
        if (folderPath == null) return

        val skipIntro = preferencesManager.getSkipIntroSeconds(folderPath)
        val skipOutro = preferencesManager.getSkipOutroSeconds(folderPath)

        composeOverlayManager.showSkipSettingsDrawer(
            currentSkipIntro = skipIntro,
            currentSkipOutro = skipOutro,
            onSkipIntroChange = { seconds ->
                preferencesManager.setSkipIntroSeconds(folderPath, seconds)
            },
            onSkipOutroChange = { seconds ->
                preferencesManager.setSkipOutroSeconds(folderPath, seconds)
            }
        )
    }
    
    /**
     * 处理片头片尾跳过逻辑
     * 
     * @param folderPath 当前视频所在文件夹路径
     * @param position 当前播放位置（秒）
     * @param duration 视频总时长（秒）
     * @param getChapters 获取视频章节列表的回调
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
        // 手动时间跳过已禁用，改用章节关键词自动检测 OP/ED
        // 见 PlayerViewModel.refreshChapterDerivedSegments()
        return
    }
}
