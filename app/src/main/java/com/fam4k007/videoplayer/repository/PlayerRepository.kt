package com.fam4k007.videoplayer.repository

import android.content.Context
import android.net.Uri
import com.fam4k007.videoplayer.AppConstants
import com.fam4k007.videoplayer.database.VideoDatabase
import com.fam4k007.videoplayer.database.PlaybackHistoryEntity
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放器数据仓库
 * 封装播放历史、播放器设置等数据访问逻辑
 * 
 * 职责：
 * - 播放历史管理（增删改查）
 * - 播放器设置读写（快进时长、倍速、记忆功能等）
 * - 弹幕和字幕信息更新
 */
class PlayerRepository(
    private val context: Context,
    private val database: VideoDatabase,
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        private const val TAG = "PlayerRepository"
    }
    
    private val historyDao = database.playbackHistoryDao()
    
    // ==================== 播放历史管理 ====================
    
    /**
     * 添加或更新播放历史
     */
    suspend fun savePlaybackHistory(
        uri: Uri,
        fileName: String,
        position: Long,
        duration: Long,
        folderName: String,
        danmuPath: String? = null,
        danmuVisible: Boolean = true,
        danmuOffsetTime: Long = 0L,
        thumbnailPath: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // 计算是否已观看：保留旧标记，或当前进度 >= 阈值
            val threshold = preferencesManager.getWatchedThreshold()
            val oldState = historyDao.getHistoryByUri(uri.toString())
            val isWatched = oldState?.hasBeenWatched == true ||
                    (duration > 0 && (position.toFloat() / duration * 100) >= threshold)
            
            val entity = PlaybackHistoryEntity(
                uri = uri.toString(),
                fileName = fileName,
                position = position,
                duration = duration,
                lastPlayed = System.currentTimeMillis(),
                folderName = folderName,
                danmuPath = danmuPath,
                danmuVisible = danmuVisible,
                danmuOffsetTime = danmuOffsetTime,
                thumbnailPath = thumbnailPath,
                hasBeenWatched = isWatched
            )
            
            historyDao.insertOrUpdate(entity)
            
            // 限制历史记录数量
            val count = historyDao.getCount()
            if (count > AppConstants.Defaults.MAX_HISTORY_SIZE) {
                historyDao.deleteOldRecords(AppConstants.Defaults.MAX_HISTORY_SIZE)
            }
            
            Logger.d(TAG, "Playback history saved: $fileName")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save playback history: ${e.message}", e)
        }
    }
    
    /**
     * 获取所有播放历史（按播放时间倒序）
     */
    suspend fun getAllHistory(): List<PlaybackHistoryEntity> = withContext(Dispatchers.IO) {
        try {
            historyDao.getAllHistory()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get all history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 根据URI获取播放历史
     */
    suspend fun getHistoryByUri(uri: Uri): PlaybackHistoryEntity? = withContext(Dispatchers.IO) {
        try {
            historyDao.getHistoryByUri(uri.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get history by URI: ${e.message}", e)
            null
        }
    }
    
    /**
     * 获取最后播放的本地视频
     */
    suspend fun getLastPlayedLocalVideo(): PlaybackHistoryEntity? = withContext(Dispatchers.IO) {
        try {
            historyDao.getLastPlayedLocalVideo()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get last played local video: ${e.message}", e)
            null
        }
    }
    
    /**
     * 删除指定URI的播放历史
     */
    suspend fun deleteHistory(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            historyDao.deleteByUri(uri.toString())
            Logger.d(TAG, "History deleted: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete history: ${e.message}", e)
        }
    }
    
    /**
     * 清空所有播放历史
     */
    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        try {
            historyDao.clearAll()
            Logger.d(TAG, "All history cleared")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear history: ${e.message}", e)
        }
    }
    
    /**
     * 获取播放历史总数
     */
    suspend fun getHistoryCount(): Int = withContext(Dispatchers.IO) {
        try {
            historyDao.getCount()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get history count: ${e.message}", e)
            0
        }
    }
    
    /**
     * 更新弹幕信息
     */
    suspend fun updateDanmu(
        uri: Uri,
        danmuPath: String?,
        danmuVisible: Boolean,
        danmuOffsetTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            historyDao.updateDanmu(uri.toString(), danmuPath, danmuVisible, danmuOffsetTime)
            Logger.d(TAG, "Danmu info updated for: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update danmu info: ${e.message}", e)
        }
    }
    
    /**
     * 更新缩略图路径
     */
    suspend fun updateThumbnail(uri: Uri, thumbnailPath: String?) = withContext(Dispatchers.IO) {
        try {
            historyDao.updateThumbnail(uri.toString(), thumbnailPath)
            Logger.d(TAG, "Thumbnail updated for: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update thumbnail: ${e.message}", e)
        }
    }
    
    // ==================== 观看进度 ====================
    
    /**
     * 获取已观看阈值
     */
    fun getWatchedThreshold(): Int {
        return preferencesManager.getWatchedThreshold()
    }
    
    /**
     * 设置已观看阈值
     */
    fun setWatchedThreshold(threshold: Int) {
        preferencesManager.setWatchedThreshold(threshold)
    }
    
    /**
     * 获取是否显示视频列表进度条
     */
    fun isShowVideoProgressBarEnabled(): Boolean {
        return preferencesManager.isShowVideoProgressBarEnabled()
    }
    
    /**
     * 设置是否显示视频列表进度条
     */
    fun setShowVideoProgressBarEnabled(enabled: Boolean) {
        preferencesManager.setShowVideoProgressBarEnabled(enabled)
    }

    fun isControlsAnimationEnabled(): Boolean {
        return preferencesManager.isControlsAnimationEnabled()
    }

    fun setControlsAnimationEnabled(enabled: Boolean) {
        preferencesManager.setControlsAnimationEnabled(enabled)
    }

    fun isDrawerAnimationEnabled(): Boolean {
        return preferencesManager.isDrawerAnimationEnabled()
    }

    fun setDrawerAnimationEnabled(enabled: Boolean) {
        preferencesManager.setDrawerAnimationEnabled(enabled)
    }

    fun getVideoAspect(): String {
        return preferencesManager.getVideoAspect()
    }

    fun setVideoAspect(aspect: String) {
        preferencesManager.setVideoAspect(aspect)
    }

    /**
     * 获取所有播放状态（用于视频列表快速查询进度）
     */
    suspend fun getAllPlaybackStates(): Map<String, com.fam4k007.videoplayer.database.PlaybackState> = withContext(Dispatchers.IO) {
        try {
            historyDao.getAllPlaybackStates().associateBy { it.uri }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get all playback states: ${e.message}", e)
            emptyMap()
        }
    }
    
    // ==================== 播放器设置 ====================
    
    // ==================== 播放器设置 ====================
    
    /**
     * 获取快进/快退时长（秒）
     */
    fun getSeekTime(): Int {
        return preferencesManager.getSeekTime()
    }
    
    /**
     * 获取快进/快退时长（秒）- 别名方法
     */
    fun getSeekTimeSeconds(): Int = getSeekTime()
    
    /**
     * 设置快进/快退时长（秒）
     */
    fun setSeekTime(seconds: Int) {
        preferencesManager.setSeekTime(seconds)
    }
    
    /**
     * 设置快进/快退时长（秒）- 别名方法
     */
    fun setSeekTimeSeconds(seconds: Int) = setSeekTime(seconds)
    
    /**
     * 获取播放历史 - 别名方法
     */
    suspend fun getPlaybackHistory(uri: Uri) = getHistoryByUri(uri)
    
    /**
     * 删除播放历史 - 别名方法
     */
    suspend fun deletePlaybackHistory(uri: Uri) = deleteHistory(uri)
    
    /**
     * 清除所有播放历史 - 别名方法
     */
    suspend fun clearAllPlaybackHistory() = clearAllHistory()
    
    /**
     * 是否启用记忆播放位置
     */
    fun isRememberPositionEnabled(): Boolean {
        return preferencesManager.isRememberPositionEnabled()
    }
    
    /**
     * 设置记忆播放位置开关
     */
    fun setRememberPositionEnabled(enabled: Boolean) {
        preferencesManager.setRememberPositionEnabled(enabled)
    }
    
    /**
     * 是否启用记忆亮度
     */
    fun isRememberBrightnessEnabled(): Boolean {
        return preferencesManager.isRememberBrightnessEnabled()
    }
    
    /**
     * 设置记忆亮度开关
     */
    fun setRememberBrightnessEnabled(enabled: Boolean) {
        preferencesManager.setRememberBrightnessEnabled(enabled)
    }
    
    /**
     * 是否启用自动加载弹幕
     */
    fun isAutoLoadDanmakuEnabled(): Boolean {
        return preferencesManager.isAutoLoadDanmakuEnabled()
    }
    
    /**
     * 设置自动加载弹幕开关
     */
    fun setAutoLoadDanmakuEnabled(enabled: Boolean) {
        preferencesManager.setAutoLoadDanmakuEnabled(enabled)
    }
    
    /**
     * 是否启用硬件解码
     */
    fun isHardwareDecodingEnabled(): Boolean {
        return preferencesManager.isHardwareDecodingEnabled()
    }
    
    /**
     * 设置硬件解码开关
     */
    fun setHardwareDecodingEnabled(enabled: Boolean) {
        preferencesManager.setHardwareDecodingEnabled(enabled)
    }
    
    /**
     * 是否启用手势控制
     */
    fun isGestureControlEnabled(): Boolean {
        return preferencesManager.isGestureControlEnabled()
    }
    
    /**
     * 设置手势控制开关
     */
    fun setGestureControlEnabled(enabled: Boolean) {
        preferencesManager.setGestureControlEnabled(enabled)
    }
    
    /**
     * 是否启用Anime4K
     */
    fun isAnime4KEnabled(): Boolean {
        return preferencesManager.isAnime4KEnabled()
    }
    
    /**
     * 设置Anime4K开关
     */
    fun setAnime4KEnabled(enabled: Boolean) {
        preferencesManager.setAnime4KEnabled(enabled)
    }
    
    /**
     * 获取Anime4K模式
     */
    fun getAnime4KMode(): String {
        return preferencesManager.getAnime4KMode()
    }
    
    /**
     * 设置Anime4K模式
     */
    fun setAnime4KMode(mode: String) {
        preferencesManager.setAnime4KMode(mode)
    }
    
    /**
     * 获取Anime4K强度
     */
    fun getAnime4KStrength(): Float {
        return preferencesManager.getAnime4KStrength()
    }
    
    /**
     * 设置Anime4K强度
     */
    fun setAnime4KStrength(strength: Float) {
        preferencesManager.setAnime4KStrength(strength)
    }
    
    /**
     * 获取弹幕速度（转换为 0.0-1.0 的 Float）
     */
    fun getDanmakuSpeed(): Float {
        return preferencesManager.getDanmakuSpeed().toFloat() / 100f
    }
    
    /**
     * 设置弹幕速度（从 0.0-1.0 的 Float 转换为 0-100 的 Int）
     */
    fun setDanmakuSpeed(speed: Float) {
        preferencesManager.setDanmakuSpeed((speed * 100).toInt())
    }
    
    /**
     * 获取弹幕字体大小
     */
    fun getDanmakuFontSize(): Float {
        return preferencesManager.getDanmakuFontSize()
    }
    
    /**
     * 设置弹幕字体大小
     */
    fun setDanmakuFontSize(size: Float) {
        preferencesManager.setDanmakuFontSize(size)
    }
    
    /**
     * 获取弹幕透明度（转换为 0.0-1.0 的 Float）
     */
    fun getDanmakuAlpha(): Float {
        return preferencesManager.getDanmakuAlpha().toFloat() / 100f
    }
    
    /**
     * 设置弹幕透明度（从 0.0-1.0 的 Float 转换为 0-100 的 Int）
     */
    fun setDanmakuAlpha(alpha: Float) {
        preferencesManager.setDanmakuAlpha((alpha * 100).toInt())
    }
    
    /**
     * 是否启用弹幕描边
     */
    fun isDanmakuStrokeEnabled(): Boolean {
        return preferencesManager.isDanmakuStrokeEnabled()
    }
    
    /**
     * 设置弹幕描边开关
     */
    fun setDanmakuStrokeEnabled(enabled: Boolean) {
        preferencesManager.setDanmakuStrokeEnabled(enabled)
    }
    
    /**
     * 获取弹幕最大行数
     */
    fun getDanmakuMaxLines(): Int {
        return preferencesManager.getDanmakuMaxLines()
    }
    
    /**
     * 获取是否启用章节进度条
     */
    fun isChapterBarEnabled(): Boolean {
        return preferencesManager.isChapterBarEnabled()
    }

    /**
     * 设置章节进度条开关
     */
    fun setChapterBarEnabled(enabled: Boolean) {
        preferencesManager.setChapterBarEnabled(enabled)
    }

    fun isSeekbarThumbnailEnabled(): Boolean {
        return preferencesManager.isSeekbarThumbnailEnabled()
    }

    fun setSeekbarThumbnailEnabled(enabled: Boolean) {
        preferencesManager.setSeekbarThumbnailEnabled(enabled)
    }

    fun isChapterSkipDetectionEnabled(): Boolean {
        return preferencesManager.isChapterSkipDetectionEnabled()
    }

    fun setChapterSkipDetectionEnabled(enabled: Boolean) {
        preferencesManager.setChapterSkipDetectionEnabled(enabled)
    }

    /**
     * 获取是否显示剩余时间
     */
    fun isShowRemainingTimeEnabled(): Boolean {
        return preferencesManager.getShowRemainingTime()
    }

    /**
     * 设置剩余时间显示开关
     */
    fun setShowRemainingTimeEnabled(enabled: Boolean) {
        preferencesManager.setShowRemainingTime(enabled)
    }

    /**
     * 设置弹幕最大行数
     */
    fun setDanmakuMaxLines(maxLines: Int) {
        preferencesManager.setDanmakuMaxLines(maxLines)
    }
    
    /**
     * 获取字幕字体大小
     */
    fun getSubtitleFontSize(): Float {
        return preferencesManager.getSubtitleFontSize()
    }
    
    /**
     * 设置字幕字体大小
     */
    fun setSubtitleFontSize(size: Float) {
        preferencesManager.setSubtitleFontSize(size)
    }
    
    /**
     * 获取字幕位置
     */
    fun getSubtitlePosition(): Float {
        return preferencesManager.getSubtitlePosition()
    }
    
    /**
     * 设置字幕位置
     */
    fun setSubtitlePosition(position: Float) {
        preferencesManager.setSubtitlePosition(position)
    }
    
    /**
     * 是否启用自动旋转
     */
    fun isAutoRotateEnabled(): Boolean {
        return preferencesManager.isAutoRotateEnabled()
    }
    
    /**
     * 设置自动旋转开关
     */
    fun setAutoRotateEnabled(enabled: Boolean) {
        preferencesManager.setAutoRotateEnabled(enabled)
    }
    
    /**
     * 是否启用竖屏UI
     */
    fun isPortraitUIEnabled(): Boolean {
        return preferencesManager.isPortraitUIEnabled()
    }
    
    /**
     * 设置竖屏UI开关
     */
    fun setPortraitUIEnabled(enabled: Boolean) {
        preferencesManager.setPortraitUIEnabled(enabled)
    }
    
    /**
     * 获取长按倍速
     */
    fun getLongPressSpeed(): Float {
        return preferencesManager.getLongPressSpeed()
    }
    
    /**
     * 设置长按倍速
     */
    fun setLongPressSpeed(speed: Float) {
        preferencesManager.setLongPressSpeed(speed)
    }

    fun getBrightnessSensitivity(): Float {
        return preferencesManager.getBrightnessSensitivity()
    }

    fun setBrightnessSensitivity(value: Float) {
        preferencesManager.setBrightnessSensitivity(value)
    }

    fun getVolumeSensitivity(): Float {
        return preferencesManager.getVolumeSensitivity()
    }

    fun setVolumeSensitivity(value: Float) {
        preferencesManager.setVolumeSensitivity(value)
    }

    /**
     * 获取进度条样式
     */
    fun getSeekbarStyle(): String {
        return preferencesManager.getSeekbarStyle()
    }

    /**
     * 设置进度条样式
     */
    fun setSeekbarStyle(style: String) {
        preferencesManager.setSeekbarStyle(style)
    }

    // ==================== MPV 解码器预设 ====================

    /**
     * 获取 MPV 解码器预设
     */
    fun getMpvProfile(): String {
        return preferencesManager.getMpvProfile()
    }

    /**
     * 设置 MPV 解码器预设
     */
    fun setMpvProfile(profile: String) {
        preferencesManager.setMpvProfile(profile)
    }

    /**
     * 获取是否启用 GPU Next 渲染
     */
    fun getGpuNext(): Boolean {
        return preferencesManager.getGpuNext()
    }

    /**
     * 设置是否启用 GPU Next 渲染
     */
    fun setGpuNext(enabled: Boolean) {
        preferencesManager.setGpuNext(enabled)
    }

    /**
     * 获取是否启用 Vulkan 渲染上下文
     */
    fun getUseVulkan(): Boolean {
        return preferencesManager.getUseVulkan()
    }

    /**
     * 设置是否启用 Vulkan 渲染上下文
     */
    fun setUseVulkan(enabled: Boolean) {
        preferencesManager.setUseVulkan(enabled)
    }

    /**
     * 是否启用播放倍速记忆
     */
    fun isRememberSpeedEnabled(): Boolean {
        return preferencesManager.isRememberSpeedEnabled()
    }
    
    /**
     * 设置播放倍速记忆开关
     */
    fun setRememberSpeedEnabled(enabled: Boolean) {
        preferencesManager.setRememberSpeedEnabled(enabled)
    }
    
    /**
     * 获取上次播放倍速
     */
    fun getLastPlaybackSpeed(): Float {
        return preferencesManager.getLastPlaybackSpeed()
    }
    
    /**
     * 保存上次播放倍速
     */
    fun setLastPlaybackSpeed(speed: Float) {
        preferencesManager.setLastPlaybackSpeed(speed)
    }
    
    // ==================== 精确进度定位 ====================
    
    /**
     * 是否启用精确进度定位
     */
    fun isPreciseSeekingEnabled(): Boolean {
        return preferencesManager.isPreciseSeekingEnabled()
    }
    
    /**
     * 设置精确进度定位开关
     */
    fun setPreciseSeekingEnabled(enabled: Boolean) {
        preferencesManager.setPreciseSeekingEnabled(enabled)
    }
    
    // ==================== 音量增强 ====================
    
    // ==================== 控制系统音量 ====================
    
    fun isControlSystemVolume(): Boolean {
        return preferencesManager.isControlSystemVolume()
    }
    
    fun setControlSystemVolume(enabled: Boolean) {
        preferencesManager.setControlSystemVolume(enabled)
    }
    
    // ==================== 音量增强 ====================
    
    /**
     * 是否启用音量增强
     */
    fun isVolumeBoostEnabled(): Boolean {
        return preferencesManager.isVolumeBoostEnabled()
    }
    
    /**
     * 设置音量增强开关
     */
    fun setVolumeBoostEnabled(enabled: Boolean) {
        preferencesManager.setVolumeBoostEnabled(enabled)
    }
    
    // ==================== Anime4K 模式记忆 ====================
    
    /**
     * 是否启用Anime4K模式记忆
     */
    fun isAnime4KMemoryEnabled(): Boolean {
        return preferencesManager.isAnime4KMemoryEnabled()
    }
    
    /**
     * 设置Anime4K模式记忆开关
     */
    fun setAnime4KMemoryEnabled(enabled: Boolean) {
        preferencesManager.setAnime4KMemoryEnabled(enabled)
    }
    
    /**
     * 获取上次使用的Anime4K模式
     */
    fun getLastAnime4KMode(): String {
        return preferencesManager.getLastAnime4KMode()
    }
    
    /**
     * 设置上次使用的Anime4K模式
     */
    fun setLastAnime4KMode(mode: String) {
        preferencesManager.setLastAnime4KMode(mode)
    }
    
    // ==================== Anime4K 超分质量 ====================
    
    /**
     * 获取超分质量设置
     */
    fun getAnime4KQuality(): String {
        return preferencesManager.getAnime4KQuality()
    }
    
    /**
     * 保存超分质量设置
     */
    fun setAnime4KQuality(quality: String) {
        preferencesManager.setAnime4KQuality(quality)
    }
    
    // ==================== 双击手势设置 ====================
    
    /**
     * 获取双击手势模式
     * @return 0=暂停/播放, 1=快进/快退
     */
    fun getDoubleTapMode(): Int {
        return preferencesManager.getDoubleTapMode()
    }
    
    /**
     * 设置双击手势模式
     */
    fun setDoubleTapMode(mode: Int) {
        preferencesManager.setDoubleTapMode(mode)
    }
    
    /**
     * 获取双击跳转秒数
     */
    fun getDoubleTapSeekSeconds(): Int {
        return preferencesManager.getDoubleTapSeekSeconds()
    }
    
    /**
     * 设置双击跳转秒数
     */
    fun setDoubleTapSeekSeconds(seconds: Int) {
        preferencesManager.setDoubleTapSeekSeconds(seconds)
    }
    
    // ==================== 自定义倍速选项 ====================
    
    /**
     * 获取自定义倍速选项
     */
    fun getCustomSpeedPresets(): Set<String> {
        return preferencesManager.getCustomSpeedPresets()
    }
    
    /**
     * 设置自定义倍速选项
     */
    fun setCustomSpeedPresets(presets: Set<String>) {
        preferencesManager.setCustomSpeedPresets(presets)
    }

    // ==================== 自动连播（百分百复用 mpvEx）====================

    fun isAutoPlayNextEnabled(): Boolean {
        return preferencesManager.isAutoPlayNextEnabled()
    }

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        preferencesManager.setAutoPlayNextEnabled(enabled)
    }

    fun isCloseAfterEndOfVideo(): Boolean {
        return preferencesManager.isCloseAfterEndOfVideo()
    }

    fun setCloseAfterEndOfVideo(enabled: Boolean) {
        preferencesManager.setCloseAfterEndOfVideo(enabled)
    }
}
