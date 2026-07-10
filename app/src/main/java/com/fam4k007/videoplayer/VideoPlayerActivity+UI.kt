package com.fam4k007.videoplayer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import com.fam4k007.videoplayer.domain.player.Anime4KManager
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.utils.Logger

private const val TAG = "VideoPlayerActivity"

/**
 * 加载用户设置
 */
internal fun VideoPlayerActivity.loadUserSettings() {
    val seekTime = preferencesManager.getSeekTime()
    viewModel.setSeekTimeSeconds(seekTime)
    Log.d(TAG, "loadUserSettings: seekTimeSeconds loaded = $seekTime seconds")

    // 恢复超分质量设置
    val savedQuality = preferencesManager.getAnime4KQuality()
    try {
        val quality = Anime4KManager.Quality.valueOf(savedQuality)
        viewModel.setAnime4KQuality(quality)
        Logger.d(TAG, "Anime4K quality restored: $savedQuality")
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Invalid Anime4K quality in preferences: $savedQuality", e)
    }

    // 如果启用了Anime4K记忆功能，恢复上次使用的模式
    if (preferencesManager.isAnime4KMemoryEnabled()) {
        val lastMode = preferencesManager.getLastAnime4KMode()
        try {
            anime4KMode = Anime4KManager.Mode.valueOf(lastMode)
            // 只有非OFF模式才启用Anime4K
            val enabled = (anime4KMode != Anime4KManager.Mode.OFF)
            viewModel.setAnime4K(enabled, anime4KMode, viewModel.anime4KQuality.value)
            Logger.d(TAG, "Anime4K mode restored from memory: $lastMode, enabled=$enabled")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Anime4K mode in preferences: $lastMode", e)
            anime4KMode = Anime4KManager.Mode.OFF
            viewModel.setAnime4K(false, Anime4KManager.Mode.OFF, viewModel.anime4KQuality.value)
        }
    } else {
        anime4KMode = Anime4KManager.Mode.OFF
        viewModel.setAnime4K(false, Anime4KManager.Mode.OFF, viewModel.anime4KQuality.value)
    }
}

/**
 * 显示速度变化提示
 */
internal fun VideoPlayerActivity.showSpeedChangeHint(speed: Float) {
    speedHintText?.text = String.format("%.1f倍速", speed)
    speedHint?.apply {
        visibility = View.VISIBLE
        alpha = 0f

        // 入场动画
        animate()
            .alpha(1.0f)
            .setDuration(200)
            .withEndAction {
                // 2秒后自动隐藏
                postDelayed({
                    animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { visibility = View.GONE }
                        .start()
                }, 2000)
            }
            .start()
    }
}

internal fun VideoPlayerActivity.applyAnime4K() {
    if (viewModel.anime4KEnabled.value) {
        val shaderString = anime4KManager.getShaderChain(anime4KMode, viewModel.anime4KQuality.value)
        val shaders = if (shaderString.isNotEmpty()) {
            shaderString.split(":")
        } else {
            emptyList()
        }
        playbackEngine.setShaderList(shaders)
        Logger.d(TAG, "Anime4K applied: mode=$anime4KMode, quality=${viewModel.anime4KQuality.value}")
    } else {
        playbackEngine.setShaderList(emptyList())
        Logger.d(TAG, "Anime4K disabled")
    }
}

/**
 * 显示视频列表抽屉
 */
internal fun VideoPlayerActivity.showVideoListDrawer() {
    val videoList = viewModel.videoList.value
    // 如果没有视频列表，提示用户
    if (videoList.isEmpty()) {
        DialogUtils.showToastShort(this, "当前没有可用的视频列表")
        return
    }

    videoUri?.let { uri ->
        composeOverlayManager.showVideoListDrawer(
            videoList = videoList,
            currentVideoUri = uri,
            onVideoSelected = { video, index ->
                // 先保存当前视频的播放进度，再切换到新视频
                savePlaybackState()
                val selectedUri = android.net.Uri.parse(video.uri)
                Logger.d(TAG, "Video selected from list: ${video.name}, index: $index")
                playVideo(selectedUri)
            }
        )
    }
}

/**
 * 处理返回导航
 */
internal fun VideoPlayerActivity.handleBackNavigation() {
    gestureHandler?.restoreOriginalSettings()

    // 重置屏幕方向为用户设置（跟随设备旋转锁定状态），避免在某些设备上强制横屏
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

    if (viewModel.isFromHomeContinue.value) {
        // 从主页继续播放进入，直接返回到主页（MainActivity）
        Logger.d(TAG, "Returning to MainActivity from continue play")

        val intent = Intent(this, MainActivity::class.java).apply {
            // 清除任务栈，确保回到主页
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    } else {
        // 正常返回
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
