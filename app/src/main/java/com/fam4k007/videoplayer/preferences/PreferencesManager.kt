package com.fam4k007.videoplayer.preferences

import android.content.Context
import android.content.SharedPreferences
import com.fam4k007.videoplayer.AppConstants
import com.fam4k007.videoplayer.dandanplay.DanmakuServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 统一的设置管理器（单例模式）
 * 集中所有 SharedPreferences 操作，避免重复创建和散落在各处
 */
class PreferencesManager private constructor(context: Context) {
    
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        AppConstants.Preferences.PLAYER_PREFS,
        Context.MODE_PRIVATE
    )
    
    companion object {
        @Volatile
        private var instance: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // ==================== 快进时长 ====================
    
    /**
     * 获取快进/快退时长（秒）
     */
    fun getSeekTime(): Int {
        return sharedPreferences.getInt(
            AppConstants.Preferences.SEEK_TIME,
            AppConstants.Defaults.DEFAULT_SEEK_TIME
        )
    }
    
    /**
     * 保存快进/快退时长
     */
    fun setSeekTime(seconds: Int) {
        sharedPreferences.edit().putInt(AppConstants.Preferences.SEEK_TIME, seconds).apply()
    }
    
    // ==================== 长按倍速 ====================
    
    /**
     * 获取长按倍速
     */
    fun getLongPressSpeed(): Float {
        return sharedPreferences.getFloat(
            AppConstants.Preferences.LONG_PRESS_SPEED,
            AppConstants.Defaults.DEFAULT_LONG_PRESS_SPEED
        )
    }
    
    /**
     * 保存长按倍速
     */
    fun setLongPressSpeed(speed: Float) {
        sharedPreferences.edit().putFloat(AppConstants.Preferences.LONG_PRESS_SPEED, speed).apply()
    }

    fun getBrightnessSensitivity(): Float {
        return sharedPreferences.getFloat(AppConstants.Preferences.BRIGHTNESS_SENSITIVITY, AppConstants.Defaults.DEFAULT_BRIGHTNESS_SENSITIVITY)
    }

    fun setBrightnessSensitivity(value: Float) {
        sharedPreferences.edit().putFloat(AppConstants.Preferences.BRIGHTNESS_SENSITIVITY, value).apply()
    }

    fun getVolumeSensitivity(): Float {
        return sharedPreferences.getFloat(AppConstants.Preferences.VOLUME_SENSITIVITY, AppConstants.Defaults.DEFAULT_VOLUME_SENSITIVITY)
    }

    fun setVolumeSensitivity(value: Float) {
        sharedPreferences.edit().putFloat(AppConstants.Preferences.VOLUME_SENSITIVITY, value).apply()
    }
    
    /**
     * 用户是否已使用过长按动态调速（左右滑动）
     */
    fun hasDynamicSpeedBeenUsed(): Boolean {
        return sharedPreferences.getBoolean("dynamic_speed_used", false)
    }
    
    fun setDynamicSpeedUsed() {
        sharedPreferences.edit().putBoolean("dynamic_speed_used", true).apply()
    }
    
    // ==================== 播放倍速记忆 ====================
    
    /**
     * 获取是否启用播放倍速记忆
     */
    fun isRememberSpeedEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.REMEMBER_PLAYBACK_SPEED,
            false
        )
    }
    
    /**
     * 保存播放倍速记忆开关
     */
    fun setRememberSpeedEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.REMEMBER_PLAYBACK_SPEED, enabled).apply()
    }
    
    /**
     * 获取上次播放倍速
     */
    fun getLastPlaybackSpeed(): Float {
        return sharedPreferences.getFloat(
            AppConstants.Preferences.LAST_PLAYBACK_SPEED,
            AppConstants.Defaults.DEFAULT_PLAYBACK_SPEED
        )
    }
    
    /**
     * 保存上次播放倍速
     */
    fun setLastPlaybackSpeed(speed: Float) {
        sharedPreferences.edit().putFloat(AppConstants.Preferences.LAST_PLAYBACK_SPEED, speed).apply()
    }
    
    /**
     * 获取自定义倍速预设列表
     */
    fun getCustomSpeedPresets(): Set<String> {
        return sharedPreferences.getStringSet(
            AppConstants.Preferences.CUSTOM_SPEED_PRESETS,
            AppConstants.Defaults.DEFAULT_SPEED_PRESETS
        ) ?: AppConstants.Defaults.DEFAULT_SPEED_PRESETS
    }
    
    /**
     * 保存自定义倍速预设列表
     */
    fun setCustomSpeedPresets(presets: Set<String>) {
        sharedPreferences.edit().putStringSet(AppConstants.Preferences.CUSTOM_SPEED_PRESETS, presets).apply()
    }
    
    // ==================== 视频显示模式 ====================
    
    /**
     * 获取视频显示模式
     * @return "folder" 显示文件夹列表 | "flat" 直接显示视频
     */
    fun getVideoDisplayMode(): String {
        return sharedPreferences.getString(
            AppConstants.Preferences.VIDEO_DISPLAY_MODE,
            AppConstants.Defaults.DEFAULT_VIDEO_DISPLAY_MODE
        ) ?: AppConstants.Defaults.DEFAULT_VIDEO_DISPLAY_MODE
    }
    
    /**
     * 保存视频显示模式
     */
    fun setVideoDisplayMode(mode: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.VIDEO_DISPLAY_MODE, mode).apply()
    }
    
    // ==================== 首次播放 ====================

    /**
     * 获取是否首次播放（新安装第一次播放时标记为 false）
     */
    fun isFirstPlay(): Boolean {
        return sharedPreferences.getBoolean(AppConstants.Preferences.IS_FIRST_PLAY, true)
    }

    /**
     * 标记已首次播放
     */
    fun setFirstPlayDone() {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.IS_FIRST_PLAY, false).apply()
    }

    // ==================== 杜比视界提示 ====================

    /**
     * 获取是否不再提示杜比视界
     */
    fun getDontShowDvWarning(): Boolean {
        return sharedPreferences.getBoolean(AppConstants.Preferences.DONT_SHOW_DV_WARNING, false)
    }

    /**
     * 设置是否不再提示杜比视界
     */
    fun setDontShowDvWarning(dontShow: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.DONT_SHOW_DV_WARNING, dontShow).apply()
    }

    // ==================== 文件夹黑名单 ====================
    
    /**
     * 获取黑名单文件夹路径集合
     * @return 被屏蔽的文件夹路径 Set
     */
    fun getBlacklistedFolders(): Set<String> {
        return sharedPreferences.getStringSet(AppConstants.Preferences.FOLDER_BLACKLIST, emptySet())
            ?: emptySet()
    }
    
    /**
     * 保存黑名单文件夹路径集合
     */
    fun setBlacklistedFolders(folders: Set<String>) {
        sharedPreferences.edit().putStringSet(AppConstants.Preferences.FOLDER_BLACKLIST, folders).apply()
    }
    
    /**
     * 检查路径是否被黑名单屏蔽
     */
    fun isFolderBlacklisted(folderPath: String): Boolean {
        return getBlacklistedFolders().contains(folderPath)
    }
    
    // ==================== 精确进度定位 ====================
    
    /**
     * 获取是否启用精确进度定位
     */
    fun isPreciseSeekingEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.PRECISE_SEEKING,
            false
        )
    }
    
    /**
     * 保存精确进度定位设置
     */
    fun setPreciseSeekingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.PRECISE_SEEKING, enabled).apply()
    }
    
    // ==================== 音量增强 ====================
    
    /**
     * 获取是否启用音量增强(允许音量超过100%)
     */
    fun isVolumeBoostEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.VOLUME_BOOST_ENABLED,
            false  // 默认关闭
        )
    }
    
    /**
     * 保存音量增强设置
     */
    fun setVolumeBoostEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.VOLUME_BOOST_ENABLED, enabled).apply()
    }
    
    // ==================== 控制系统音量 ====================
    
    /**
     * 获取是否控制系统音量（开启后退出播放不恢复进入前的音量）
     */
    fun isControlSystemVolume(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.CONTROL_SYSTEM_VOLUME,
            false  // 默认关闭（退出时恢复音量）
        )
    }
    
    /**
     * 保存控制系统音量设置
     */
    fun setControlSystemVolume(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.CONTROL_SYSTEM_VOLUME, enabled).apply()
    }
    
    // ==================== Anime4K 超分模式记忆 ====================
    
    /**
     * 获取是否启用Anime4K模式记忆
     */
    fun isAnime4KMemoryEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.ANIME4K_MEMORY_ENABLED,
            false  // 默认关闭
        )
    }
    
    /**
     * 保存Anime4K模式记忆设置
     */
    fun setAnime4KMemoryEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.ANIME4K_MEMORY_ENABLED, enabled).apply()
    }
    
    /**
     * 获取上次使用的Anime4K模式
     * @return 模式名称（OFF, A, B, C, A_PLUS, B_PLUS, C_PLUS）
     */
    fun getLastAnime4KMode(): String {
        return sharedPreferences.getString(
            AppConstants.Preferences.ANIME4K_LAST_MODE,
            "OFF"  // 默认为OFF
        ) ?: "OFF"
    }
    
    /**
     * 保存当前使用的Anime4K模式
     * @param mode 模式名称
     */
    fun setLastAnime4KMode(mode: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.ANIME4K_LAST_MODE, mode).apply()
    }
    
    // ==================== Anime4K 超分质量 ====================
    
    /**
     * 获取超分质量设置
     * @return Quality 枚举名称（FAST, BALANCED, HIGH）
     */
    fun getAnime4KQuality(): String {
        return sharedPreferences.getString(
            AppConstants.Preferences.ANIME4K_QUALITY,
            "BALANCED"  // 默认平衡档
        ) ?: "BALANCED"
    }
    
    /**
     * 保存超分质量设置
     * @param quality Quality 枚举名称
     */
    fun setAnime4KQuality(quality: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.ANIME4K_QUALITY, quality).apply()
    }
    
    // ==================== 更新相关 ====================
    
    /**
     * 获取是否启用自动检查更新
     */
    fun isAutoCheckUpdateEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.AUTO_CHECK_UPDATE,
            true  // 默认开启
        )
    }
    
    /**
     * 设置自动检查更新开关
     */
    fun setAutoCheckUpdateEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.AUTO_CHECK_UPDATE, enabled).apply()
    }
    
    /**
     * 获取用户忽略的版本号
     * @return 被忽略的 versionName，空字符串表示没有忽略任何版本
     */
    fun getIgnoredUpdateVersion(): String {
        return sharedPreferences.getString(
            AppConstants.Preferences.IGNORED_UPDATE_VERSION,
            ""
        ) ?: ""
    }
    
    /**
     * 设置忽略的版本号
     * @param version 要忽略的版本名，空字符串清除忽略
     */
    fun setIgnoredUpdateVersion(version: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.IGNORED_UPDATE_VERSION, version).apply()
    }
    
    // ==================== 双击手势设置 ====================
    
    /**
     * 获取双击手势模式
     * @return 0=暂停/播放, 1=快进/快退
     */
    fun getDoubleTapMode(): Int {
        return sharedPreferences.getInt(
            AppConstants.Preferences.DOUBLE_TAP_MODE,
            AppConstants.Defaults.DEFAULT_DOUBLE_TAP_MODE
        )
    }
    
    /**
     * 设置双击手势模式
     * @param mode 0=暂停/播放, 1=快进/快退
     */
    fun setDoubleTapMode(mode: Int) {
        sharedPreferences.edit().putInt(AppConstants.Preferences.DOUBLE_TAP_MODE, mode).apply()
    }
    
    /**
     * 获取双击跳转秒数（仅在快进/快退模式下有效）
     */
    fun getDoubleTapSeekSeconds(): Int {
        return sharedPreferences.getInt(
            AppConstants.Preferences.DOUBLE_TAP_SEEK_SECONDS,
            AppConstants.Defaults.DEFAULT_DOUBLE_TAP_SEEK_SECONDS
        )
    }
    
    /**
     * 设置双击跳转秒数
     */
    fun setDoubleTapSeekSeconds(seconds: Int) {
        sharedPreferences.edit().putInt(AppConstants.Preferences.DOUBLE_TAP_SEEK_SECONDS, seconds).apply()
    }
    
    // ==================== 播放位置（用于记忆播放进度）====================
    
    /**
     * 获取视频的保存播放位置
     * @param videoUri 视频URI（使用 uri.toString() 作为键）
     */
    fun getPlaybackPosition(videoUri: String): Double {
        return sharedPreferences.getFloat(videoUri, 0f).toDouble()
    }
    
    /**
     * 保存视频播放位置
     */
    fun setPlaybackPosition(videoUri: String, position: Double) {
        sharedPreferences.edit().putFloat(videoUri, position.toFloat()).apply()
    }
    
    /**
     * 清除视频播放位置记录
     */
    fun clearPlaybackPosition(videoUri: String) {
        sharedPreferences.edit().remove(videoUri).apply()
    }
    
    // ==================== 字幕偏好设置（针对每个视频）====================
    
    /**
     * 获取视频的外部字幕路径
     */
    fun getExternalSubtitle(videoUri: String): String? {
        return sharedPreferences.getString("${videoUri}_subtitle", null)
    }
    
    /**
     * 保存视频的外部字幕路径
     */
    fun setExternalSubtitle(videoUri: String, subtitlePath: String) {
        sharedPreferences.edit().putString("${videoUri}_subtitle", subtitlePath).apply()
    }
    
    /**
     * 获取视频的字幕大小（优先 per-video，回退 global）
     */
    fun getSubtitleScale(videoUri: String): Double {
        if (sharedPreferences.contains("${videoUri}_sub_scale")) {
            return sharedPreferences.getFloat("${videoUri}_sub_scale", 1.0f).toDouble()
        }
        return sharedPreferences.getFloat("global_sub_scale", 1.0f).toDouble()
    }
    
    /**
     * 保存视频的字幕大小（同时保存 per-video 和 global）
     */
    fun setSubtitleScale(videoUri: String, scale: Double) {
        sharedPreferences.edit()
            .putFloat("${videoUri}_sub_scale", scale.toFloat())
            .putFloat("global_sub_scale", scale.toFloat())
            .apply()
    }
    
    /**
     * 获取视频的字幕位置（优先 per-video，回退 global）
     */
    fun getSubtitlePosition(videoUri: String): Int {
        if (sharedPreferences.contains("${videoUri}_sub_pos")) {
            return sharedPreferences.getInt("${videoUri}_sub_pos", 100)
        }
        return sharedPreferences.getInt("global_sub_pos", 100)
    }
    
    /**
     * 保存视频的字幕位置（同时保存 per-video 和 global）
     */
    fun setSubtitlePosition(videoUri: String, position: Int) {
        sharedPreferences.edit()
            .putInt("${videoUri}_sub_pos", position)
            .putInt("global_sub_pos", position)
            .apply()
    }
    
    /**
     * 获取视频的字幕延迟
     */
    fun getSubtitleDelay(videoUri: String): Double {
        return sharedPreferences.getFloat("${videoUri}_sub_delay", 0f).toDouble()
    }
    
    /**
     * 保存视频的字幕延迟
     */
    fun setSubtitleDelay(videoUri: String, delay: Double) {
        sharedPreferences.edit().putFloat("${videoUri}_sub_delay", delay.toFloat()).apply()
    }
    
    /**
     * 获取是否启用ASS字幕样式覆盖
     */
    fun isAssOverrideEnabled(videoUri: String): Boolean {
        return sharedPreferences.getBoolean("${videoUri}_ass_override", false)
    }
    
    /**
     * 保存ASS字幕样式覆盖设置
     */
    fun setAssOverrideEnabled(videoUri: String, enabled: Boolean) {
        sharedPreferences.edit().putBoolean("${videoUri}_ass_override", enabled).apply()
    }
    
    /**
     * 获取视频的字幕轨道ID
     */
    fun getSubtitleTrackId(videoUri: String): Int {
        return sharedPreferences.getInt("${videoUri}_sub_track_id", -1)
    }
    
    /**
     * 保存视频的字幕轨道ID
     */
    fun setSubtitleTrackId(videoUri: String, trackId: Int) {
        sharedPreferences.edit().putInt("${videoUri}_sub_track_id", trackId).apply()
    }
    
    // ==================== 字幕样式设置 ====================
    
    /**
     * 获取字幕文本颜色（ARGB格式）
     * 优先返回视频专属设置，若无则回退到全局设置
     */
    fun getSubtitleTextColor(videoUri: String): String {
        val perVideo = sharedPreferences.getString("${videoUri}_sub_text_color", null)
        if (perVideo != null) return perVideo
        return sharedPreferences.getString("global_sub_text_color", "#FFFFFF") ?: "#FFFFFF"
    }
    
    /**
     * 保存字幕文本颜色（同时保存全局）
     */
    fun setSubtitleTextColor(videoUri: String, color: String) {
        sharedPreferences.edit()
            .putString("${videoUri}_sub_text_color", color)
            .putString("global_sub_text_color", color)
            .apply()
    }
    
    /**
     * 获取字幕描边粗细
     * 优先返回视频专属设置，若无则回退到全局设置
     */
    fun getSubtitleBorderSize(videoUri: String): Int {
        if (sharedPreferences.contains("${videoUri}_sub_border_size")) {
            return sharedPreferences.getInt("${videoUri}_sub_border_size", 3)
        }
        return sharedPreferences.getInt("global_sub_border_size", 3)
    }
    
    /**
     * 保存字幕描边粗细（同时保存全局）
     */
    fun setSubtitleBorderSize(videoUri: String, size: Int) {
        sharedPreferences.edit()
            .putInt("${videoUri}_sub_border_size", size)
            .putInt("global_sub_border_size", size)
            .apply()
    }
    
    /**
     * 获取字幕描边颜色（ARGB格式）
     * 优先返回视频专属设置，若无则回退到全局设置
     */
    fun getSubtitleBorderColor(videoUri: String): String {
        val perVideo = sharedPreferences.getString("${videoUri}_sub_border_color", null)
        if (perVideo != null) return perVideo
        return sharedPreferences.getString("global_sub_border_color", "#000000") ?: "#000000"
    }
    
    /**
     * 保存字幕描边颜色（同时保存全局）
     */
    fun setSubtitleBorderColor(videoUri: String, color: String) {
        sharedPreferences.edit()
            .putString("${videoUri}_sub_border_color", color)
            .putString("global_sub_border_color", color)
            .apply()
    }
    
    /**
     * 获取字幕背景颜色（ARGB格式）
     * 优先返回视频专属设置，若无则回退到全局设置
     */
    fun getSubtitleBackColor(videoUri: String): String {
        val perVideo = sharedPreferences.getString("${videoUri}_sub_back_color", null)
        if (perVideo != null) return perVideo
        return sharedPreferences.getString("global_sub_back_color", "#00000000") ?: "#00000000"
    }
    
    /**
     * 保存字幕背景颜色（同时保存全局）
     */
    fun setSubtitleBackColor(videoUri: String, color: String) {
        sharedPreferences.edit()
            .putString("${videoUri}_sub_back_color", color)
            .putString("global_sub_back_color", color)
            .apply()
    }
    
    /**
     * 获取字幕描边样式
     * 优先返回视频专属设置，若无则回退到全局设置
     */
    fun getSubtitleBorderStyle(videoUri: String): String {
        val perVideo = sharedPreferences.getString("${videoUri}_sub_border_style", null)
        if (perVideo != null) return perVideo
        return sharedPreferences.getString("global_sub_border_style", "outline-and-shadow") ?: "outline-and-shadow"
    }
    
    /**
     * 保存字幕描边样式（同时保存全局）
     */
    fun setSubtitleBorderStyle(videoUri: String, style: String) {
        sharedPreferences.edit()
            .putString("${videoUri}_sub_border_style", style)
            .putString("global_sub_border_style", style)
            .apply()
    }
    
    // ==================== 主题设置 ====================
    
    /**
     * 获取主题模式
     * @return "light" 亮色 | "dark" 深色 | "system" 跟随系统
     */
    fun getThemeMode(): String {
        return sharedPreferences.getString(
            "theme_mode",
            "light"  // 默认亮色
        ) ?: "light"
    }
    
    /**
     * 保存主题模式
     */
    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString("theme_mode", mode).apply()
    }
    
    /**
     * 获取应用主题名称
     * @return 主题名称，默认 "Default"
     */
    fun getAppTheme(): String {
        return sharedPreferences.getString(
            "app_theme",
            "Default"  // 默认主题
        ) ?: "Default"
    }
    
    /**
     * 保存应用主题
     */
    fun setAppTheme(themeName: String) {
        sharedPreferences.edit().putString("app_theme", themeName).apply()
    }
    
    /**
     * 获取是否启用 AMOLED 纯黑模式
     * @return true = 启用，false = 不启用
     */
    fun getAmoledMode(): Boolean {
        return sharedPreferences.getBoolean("amoled_mode", false)
    }
    
    /**
     * 保存 AMOLED 纯黑模式设置
     */
    fun setAmoledMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("amoled_mode", enabled).apply()
    }
    
    /**
     * 获取进度条样式
     * @return 样式名称，默认 "Standard"
     */
    fun getSeekbarStyle(): String {
        return sharedPreferences.getString(
            "seekbar_style",
            "Standard"
        ) ?: "Standard"
    }
    
    /**
     * 保存进度条样式
     */
    fun setSeekbarStyle(style: String) {
        sharedPreferences.edit().putString("seekbar_style", style).apply()
    }
    
    // ==================== 弹幕设置 ====================
    
    fun getDanmakuEnabled(): Boolean {
        return sharedPreferences.getBoolean("danmaku_enabled", true)
    }
    
    fun setDanmakuEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_enabled", enabled).apply()
    }
    
    fun getDanmakuSize(): Int {
        return sharedPreferences.getInt("danmaku_size", 50)
    }
    
    fun setDanmakuSize(size: Int) {
        sharedPreferences.edit().putInt("danmaku_size", size).apply()
    }
    
    fun getDanmakuSpeed(): Int {
        return sharedPreferences.getInt("danmaku_speed", 50)
    }
    
    fun setDanmakuSpeed(speed: Int) {
        sharedPreferences.edit().putInt("danmaku_speed", speed).apply()
    }
    
    fun getDanmakuAlpha(): Int {
        return sharedPreferences.getInt("danmaku_alpha", 100)
    }
    
    fun setDanmakuAlpha(alpha: Int) {
        sharedPreferences.edit().putInt("danmaku_alpha", alpha).apply()
    }
    
    fun getDanmakuStroke(): Int {
        return sharedPreferences.getInt("danmaku_stroke", 50)
    }
    
    fun setDanmakuStroke(stroke: Int) {
        sharedPreferences.edit().putInt("danmaku_stroke", stroke).apply()
    }
    
    fun getDanmakuOffsetTime(): Long {
        return sharedPreferences.getLong("danmaku_offset_time", 0L)
    }
    
    fun setDanmakuOffsetTime(time: Long) {
        sharedPreferences.edit().putLong("danmaku_offset_time", time).apply()
    }
    
    fun getDanmakuShowScroll(): Boolean {
        return sharedPreferences.getBoolean("danmaku_show_scroll", true)
    }
    
    fun setDanmakuShowScroll(show: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_show_scroll", show).apply()
    }
    
    fun getDanmakuShowTop(): Boolean {
        return sharedPreferences.getBoolean("danmaku_show_top", true)
    }
    
    fun setDanmakuShowTop(show: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_show_top", show).apply()
    }
    
    fun getDanmakuShowBottom(): Boolean {
        return sharedPreferences.getBoolean("danmaku_show_bottom", true)
    }
    
    fun setDanmakuShowBottom(show: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_show_bottom", show).apply()
    }
    
    fun getDanmakuMaxScrollLine(): Int {
        return sharedPreferences.getInt("danmaku_max_scroll_line", 0)
    }
    
    fun setDanmakuMaxScrollLine(line: Int) {
        sharedPreferences.edit().putInt("danmaku_max_scroll_line", line).apply()
    }
    
    fun getDanmakuMaxTopLine(): Int {
        return sharedPreferences.getInt("danmaku_max_top_line", 0)
    }
    
    fun setDanmakuMaxTopLine(line: Int) {
        sharedPreferences.edit().putInt("danmaku_max_top_line", line).apply()
    }
    
    fun getDanmakuMaxBottomLine(): Int {
        return sharedPreferences.getInt("danmaku_max_bottom_line", 0)
    }
    
    fun setDanmakuMaxBottomLine(line: Int) {
        sharedPreferences.edit().putInt("danmaku_max_bottom_line", line).apply()
    }

    fun getDanmakuDisplayArea(): Int {
        return sharedPreferences.getInt("danmaku_display_area", 100)
    }

    fun setDanmakuDisplayArea(percent: Int) {
        sharedPreferences.edit().putInt("danmaku_display_area", percent).apply()
    }
    
    fun getDanmakuMaxScreenNum(): Int {
        return sharedPreferences.getInt("danmaku_max_screen_num", 0)
    }
    
    fun setDanmakuMaxScreenNum(num: Int) {
        sharedPreferences.edit().putInt("danmaku_max_screen_num", num).apply()
    }
    
    fun getDanmakuUseChoreographer(): Boolean {
        return sharedPreferences.getBoolean("danmaku_use_choreographer", true)
    }
    
    fun setDanmakuUseChoreographer(use: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_use_choreographer", use).apply()
    }
    
    fun getDanmakuDebug(): Boolean {
        return sharedPreferences.getBoolean("danmaku_debug", false)
    }
    
    fun setDanmakuDebug(debug: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_debug", debug).apply()
    }
    
    // ==================== 视频列表排序设置 ====================
    
    /**
     * 获取视频列表排序类型 (NAME, DATE)
     */
    fun getVideoSortType(): String {
        return sharedPreferences.getString("video_sort_type", "NAME") ?: "NAME"
    }
    
    /**
     * 保存视频列表排序类型
     */
    fun setVideoSortType(type: String) {
        sharedPreferences.edit().putString("video_sort_type", type).apply()
    }
    
    /**
     * 获取视频列表排序顺序 (ASCENDING, DESCENDING)
     */
    fun getVideoSortOrder(): String {
        return sharedPreferences.getString("video_sort_order", "ASCENDING") ?: "ASCENDING"
    }
    
    /**
     * 保存视频列表排序顺序
     */
    fun setVideoSortOrder(order: String) {
        sharedPreferences.edit().putString("video_sort_order", order).apply()
    }
    
    /**
     * 获取文件夹列表排序类型 (NAME, VIDEO_COUNT)
     */
    fun getFolderSortType(): String {
        return sharedPreferences.getString("folder_sort_type", "VIDEO_COUNT") ?: "VIDEO_COUNT"
    }
    
    /**
     * 保存文件夹列表排序类型
     */
    fun setFolderSortType(type: String) {
        sharedPreferences.edit().putString("folder_sort_type", type).apply()
    }
    
    /**
     * 获取文件夹列表排序顺序 (ASCENDING, DESCENDING)
     */
    fun getFolderSortOrder(): String {
        return sharedPreferences.getString("folder_sort_order", "DESCENDING") ?: "DESCENDING"
    }
    
    /**
     * 保存文件夹列表排序顺序
     */
    fun setFolderSortOrder(order: String) {
        sharedPreferences.edit().putString("folder_sort_order", order).apply()
    }
    
    // ==================== 硬件解码 ====================
    
    /**
     * 获取硬件解码设置
     */
    fun getHardwareDecoder(): Boolean {
        return sharedPreferences.getBoolean("hardware_decoder", true)
    }
    
    /**
     * 保存硬件解码设置
     */
    fun setHardwareDecoder(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("hardware_decoder", enabled).apply()
    }
    
    // ==================== 片头片尾跳过 ====================
    
    /**
     * 获取片头跳过秒数（按文件夹存储）
     */
    fun getSkipIntroSeconds(folderPath: String): Int {
        val key = "skip_intro_folder_${folderPath.hashCode()}"
        return sharedPreferences.getInt(key, 0)
    }
    
    /**
     * 保存片头跳过秒数（按文件夹存储）
     */
    fun setSkipIntroSeconds(folderPath: String, seconds: Int) {
        val key = "skip_intro_folder_${folderPath.hashCode()}"
        sharedPreferences.edit().putInt(key, seconds).apply()
    }
    
    /**
     * 获取片尾跳过秒数（按文件夹存储）
     */
    fun getSkipOutroSeconds(folderPath: String): Int {
        val key = "skip_outro_folder_${folderPath.hashCode()}"
        return sharedPreferences.getInt(key, 0)
    }
    
    /**
     * 保存片尾跳过秒数（按文件夹存储）
     */
    fun setSkipOutroSeconds(folderPath: String, seconds: Int) {
        val key = "skip_outro_folder_${folderPath.hashCode()}"
        sharedPreferences.edit().putInt(key, seconds).apply()
    }
    
    /**
     * 获取是否自动跳过章节（按文件夹存储）
     */
    fun getAutoSkipChapter(folderPath: String): Boolean {
        val key = "auto_skip_chapter_folder_${folderPath.hashCode()}"
        return sharedPreferences.getBoolean(key, false)
    }
    
    /**
     * 保存是否自动跳过章节（按文件夹存储）
     */
    fun setAutoSkipChapter(folderPath: String, enabled: Boolean) {
        val key = "auto_skip_chapter_folder_${folderPath.hashCode()}"
        sharedPreferences.edit().putBoolean(key, enabled).apply()
    }
    
    /**
     * 获取章节跳过的目标章节索引（按文件夹存储）
     * 默认为1（第二个章节，通常OP结束后的正片）
     */
    fun getSkipToChapterIndex(folderPath: String): Int {
        val key = "skip_to_chapter_index_folder_${folderPath.hashCode()}"
        return sharedPreferences.getInt(key, 1)
    }
    
    /**
     * 保存章节跳过的目标章节索引（按文件夹存储）
     */
    fun setSkipToChapterIndex(folderPath: String, chapterIndex: Int) {
        val key = "skip_to_chapter_index_folder_${folderPath.hashCode()}"
        sharedPreferences.edit().putInt(key, chapterIndex).apply()
    }
    
    // ==================== 弹幕文件选择器路径记忆 ====================
    
    /**
     * 获取上次选择弹幕文件的路径
     */
    fun getLastDanmakuPickerPath(): String? {
        return sharedPreferences.getString("last_danmaku_picker_path", null)
    }
    
    /**
     * 保存弹幕文件选择器路径
     */
    fun setLastDanmakuPickerPath(path: String) {
        sharedPreferences.edit().putString("last_danmaku_picker_path", path).apply()
    }
    
    // ==================== 字幕文件选择器路径记忆 ====================
    
    /**
     * 获取上次选择字幕文件的路径
     */
    fun getLastSubtitlePickerPath(): String? {
        return sharedPreferences.getString("last_subtitle_picker_path", null)
    }
    
    /**
     * 保存字幕文件选择器路径
     */
    fun setLastSubtitlePickerPath(path: String) {
        sharedPreferences.edit().putString("last_subtitle_picker_path", path).apply()
    }
    
    // ==================== 视频列表排序设置 ====================
    
    /**
     * 获取视频列表排序方式
     * @return "NAME", "SIZE", "DURATION", "DATE"
     */
    fun getVideoListSortBy(): String {
        return sharedPreferences.getString("video_list_sort_by", "NAME") ?: "NAME"
    }
    
    /**
     * 保存视频列表排序方式
     */
    fun setVideoListSortBy(sortBy: String) {
        sharedPreferences.edit().putString("video_list_sort_by", sortBy).apply()
    }
    
    /**
     * 获取视频列表排序顺序
     * @return "ASCENDING" 或 "DESCENDING"
     */
    fun getVideoListSortOrder(): String {
        return sharedPreferences.getString("video_list_sort_order", "ASCENDING") ?: "ASCENDING"
    }
    
    /**
     * 保存视频列表排序顺序
     */
    fun setVideoListSortOrder(sortOrder: String) {
        sharedPreferences.edit().putString("video_list_sort_order", sortOrder).apply()
    }
    
    // ==================== 弹幕文件选择器排序设置 ====================
    
    /**
     * 获取弹幕文件选择器排序方式
     * @return "NAME" 或 "DATE"
     */
    fun getDanmakuFileSortBy(): String {
        return sharedPreferences.getString("danmaku_file_sort_by", "NAME") ?: "NAME"
    }
    
    /**
     * 保存弹幕文件选择器排序方式
     */
    fun setDanmakuFileSortBy(sortBy: String) {
        sharedPreferences.edit().putString("danmaku_file_sort_by", sortBy).apply()
    }
    
    /**
     * 获取弹幕文件选择器排序顺序
     * @return "ASCENDING" 或 "DESCENDING"
     */
    fun getDanmakuFileSortOrder(): String {
        return sharedPreferences.getString("danmaku_file_sort_order", "ASCENDING") ?: "ASCENDING"
    }
    
    /**
     * 保存弹幕文件选择器排序顺序
     */
    fun setDanmakuFileSortOrder(sortOrder: String) {
        sharedPreferences.edit().putString("danmaku_file_sort_order", sortOrder).apply()
    }
    
    // ==================== 字幕文件选择器排序设置 ====================
    
    /**
     * 获取字幕文件选择器排序方式
     * @return "NAME" 或 "DATE"
     */
    fun getSubtitleFileSortBy(): String {
        return sharedPreferences.getString("subtitle_file_sort_by", "NAME") ?: "NAME"
    }
    
    /**
     * 保存字幕文件选择器排序方式
     */
    fun setSubtitleFileSortBy(sortBy: String) {
        sharedPreferences.edit().putString("subtitle_file_sort_by", sortBy).apply()
    }
    
    /**
     * 获取字幕文件选择器排序顺序
     * @return "ASCENDING" 或 "DESCENDING"
     */
    fun getSubtitleFileSortOrder(): String {
        return sharedPreferences.getString("subtitle_file_sort_order", "ASCENDING") ?: "ASCENDING"
    }
    
    /**
     * 保存字幕文件选择器排序顺序
     */
    fun setSubtitleFileSortOrder(sortOrder: String) {
        sharedPreferences.edit().putString("subtitle_file_sort_order", sortOrder).apply()
    }
    
    // ==================== 字幕字体设置（全局） ====================
    
    /**
     * 获取用户选择的字体名称（字体族名）
     */
    fun getSubtitleFontName(): String {
        return sharedPreferences.getString("subtitle_font_name", "") ?: ""
    }
    
    /**
     * 保存用户选择的字体名称（字体族名）
     */
    fun setSubtitleFontName(fontName: String) {
        sharedPreferences.edit().putString("subtitle_font_name", fontName).apply()
    }
    
    /**
     * 获取字体目录 URI
     */
    fun getFontDirectoryUri(): String {
        return sharedPreferences.getString("font_directory_uri", "") ?: ""
    }
    
    /**
     * 保存字体目录 URI
     */
    fun setFontDirectoryUri(uri: String) {
        sharedPreferences.edit().putString("font_directory_uri", uri).apply()
    }
    
    /**
     * 获取全局ASS样式覆盖开关
     */
    fun isAssOverrideEnabled(): Boolean {
        return sharedPreferences.getBoolean("ass_override_global", false)
    }
    
    /**
     * 保存全局ASS样式覆盖开关
     */
    fun setAssOverrideEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("ass_override_global", enabled).apply()
    }
    
    // ==================== 批量操作 ====================
    
    /**
     * 清除所有设置（谨慎使用）
     */
    fun getLastRemoteInputUrl(): String {
        return sharedPreferences.getString("remote_input_url", "") ?: ""
    }

    fun setLastRemoteInputUrl(value: String) {
        sharedPreferences.edit().putString("remote_input_url", value).apply()
    }

    fun getLastRemoteInputTitle(): String {
        return sharedPreferences.getString("remote_input_title", "") ?: ""
    }

    fun setLastRemoteInputTitle(value: String) {
        sharedPreferences.edit().putString("remote_input_title", value).apply()
    }

    fun getLastRemoteInputSourcePageUrl(): String {
        return sharedPreferences.getString("remote_input_source_page_url", "") ?: ""
    }

    fun setLastRemoteInputSourcePageUrl(value: String) {
        sharedPreferences.edit().putString("remote_input_source_page_url", value).apply()
    }

    fun getLastRemoteInputReferer(): String {
        return sharedPreferences.getString("remote_input_referer", "") ?: ""
    }

    fun setLastRemoteInputReferer(value: String) {
        sharedPreferences.edit().putString("remote_input_referer", value).apply()
    }

    fun getLastRemoteInputOrigin(): String {
        return sharedPreferences.getString("remote_input_origin", "") ?: ""
    }

    fun setLastRemoteInputOrigin(value: String) {
        sharedPreferences.edit().putString("remote_input_origin", value).apply()
    }

    fun getLastRemoteInputCookie(): String {
        return sharedPreferences.getString("remote_input_cookie", "") ?: ""
    }

    fun setLastRemoteInputCookie(value: String) {
        sharedPreferences.edit().putString("remote_input_cookie", value).apply()
    }

    fun getLastRemoteInputAuthorization(): String {
        return sharedPreferences.getString("remote_input_authorization", "") ?: ""
    }

    fun setLastRemoteInputAuthorization(value: String) {
        sharedPreferences.edit().putString("remote_input_authorization", value).apply()
    }

    fun getLastRemoteInputUserAgent(): String {
        return sharedPreferences.getString("remote_input_user_agent", "") ?: ""
    }

    fun setLastRemoteInputUserAgent(value: String) {
        sharedPreferences.edit().putString("remote_input_user_agent", value).apply()
    }

    fun clearLastRemoteInputDraft() {
        sharedPreferences.edit()
            .remove("remote_input_url")
            .remove("remote_input_title")
            .remove("remote_input_source_page_url")
            .remove("remote_input_referer")
            .remove("remote_input_origin")
            .remove("remote_input_cookie")
            .remove("remote_input_authorization")
            .remove("remote_input_user_agent")
            .apply()
    }

    fun getLastRemoteDebugSummary(): String {
        return sharedPreferences.getString("remote_debug_summary", "") ?: ""
    }

    fun setLastRemoteDebugSummary(value: String) {
        sharedPreferences.edit().putString("remote_debug_summary", value).apply()
    }

    fun clearLastRemoteDebugSummary() {
        sharedPreferences.edit().remove("remote_debug_summary").apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * 获取所有设置
     */
    fun getAll(): Map<String, *> {
        return sharedPreferences.all ?: emptyMap<String, Any>()
    }
    
    // ==================== PlayerRepository 兼容方法 ====================
    
    /**
     * 是否启用记忆播放位置
     */
    fun isRememberPositionEnabled(): Boolean {
        return sharedPreferences.getBoolean("remember_position_enabled", true)
    }
    
    fun setRememberPositionEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("remember_position_enabled", enabled).apply()
    }
    
    /**
     * 是否启用记忆亮度
     */
    fun isRememberBrightnessEnabled(): Boolean {
        return sharedPreferences.getBoolean("remember_brightness_enabled", false)
    }
    
    fun setRememberBrightnessEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("remember_brightness_enabled", enabled).apply()
    }
    
    /**
     * 是否启用自动加载弹幕
     */
    fun isAutoLoadDanmakuEnabled(): Boolean {
        return sharedPreferences.getBoolean("auto_load_danmaku_enabled", true)
    }
    
    fun setAutoLoadDanmakuEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_load_danmaku_enabled", enabled).apply()
    }
    
    /**
     * 自定义弹幕服务器地址（旧接口，保留兼容）
     * 为空时使用默认弹弹Play服务器
     */
    fun getDanmakuServerUrl(): String {
        return sharedPreferences.getString("dandanplay_server_url", "") ?: ""
    }
    
    fun setDanmakuServerUrl(url: String) {
        sharedPreferences.edit().putString("dandanplay_server_url", url.trim()).apply()
    }
    
    // ==================== 弹幕服务器列表管理 ====================
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 获取弹幕服务器列表
     * 如果列表为空，自动初始化默认服务器
     * 同时迁移旧的单服务器设置
     */
    fun getDanmakuServers(): List<DanmakuServer> {
        val jsonStr = sharedPreferences.getString("dandanplay_servers", null)
        if (jsonStr != null) {
            return try {
                json.decodeFromString<List<DanmakuServer>>(jsonStr)
            } catch (e: Exception) {
                listOf(DanmakuServer.createDefault())
            }
        }
        
        // 首次使用：初始化并迁移旧设置
        val servers = mutableListOf(DanmakuServer.createDefault())
        val oldUrl = getDanmakuServerUrl()
        if (oldUrl.isNotBlank()) {
            servers.add(
                DanmakuServer(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "自定义服务器",
                    url = oldUrl,
                    isEnabled = true,
                    isDefault = false
                )
            )
        }
        saveDanmakuServers(servers)
        return servers
    }
    
    /**
     * 保存弹幕服务器列表
     */
    fun saveDanmakuServers(servers: List<DanmakuServer>) {
        val jsonStr = json.encodeToString(servers)
        sharedPreferences.edit().putString("dandanplay_servers", jsonStr).apply()
    }
    
    /**
     * 获取所有已启用的弹幕服务器
     */
    fun getEnabledDanmakuServers(): List<DanmakuServer> {
        return getDanmakuServers().filter { it.isEnabled }
    }
    
    /**
     * 添加弹幕服务器
     */
    fun addDanmakuServer(name: String, url: String) {
        val servers = getDanmakuServers().toMutableList()
        servers.add(
            DanmakuServer(
                id = java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                url = url.trim().trimEnd('/'),
                isEnabled = true,
                isDefault = false
            )
        )
        saveDanmakuServers(servers)
    }
    
    /**
     * 删除弹幕服务器（不允许删除默认服务器）
     */
    fun removeDanmakuServer(id: String) {
        val servers = getDanmakuServers().filter { it.id != id || it.isDefault }
        saveDanmakuServers(servers)
    }
    
    /**
     * 切换弹幕服务器启用状态
     */
    fun toggleDanmakuServer(id: String, enabled: Boolean) {
        val servers = getDanmakuServers().map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveDanmakuServers(servers)
    }
    
    /**
     * 是否启用硬件解码（兼容方法）
     */
    fun isHardwareDecodingEnabled(): Boolean {
        return getHardwareDecoder()
    }
    
    fun setHardwareDecodingEnabled(enabled: Boolean) {
        setHardwareDecoder(enabled)
    }
    
    /**
     * 是否启用手势控制
     */
    fun isGestureControlEnabled(): Boolean {
        return sharedPreferences.getBoolean("gesture_control_enabled", true)
    }
    
    fun setGestureControlEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("gesture_control_enabled", enabled).apply()
    }
    
    /**
     * 是否启用Anime4K
     */
    fun isAnime4KEnabled(): Boolean {
        return sharedPreferences.getBoolean("anime4k_enabled", false)
    }
    
    fun setAnime4KEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("anime4k_enabled", enabled).apply()
    }
    
    /**
     * 获取Anime4K模式（兼容方法，使用已有的LastAnime4KMode）
     */
    fun getAnime4KMode(): String {
        return getLastAnime4KMode()
    }
    
    fun setAnime4KMode(mode: String) {
        setLastAnime4KMode(mode)
    }
    
    /**
     * 获取Anime4K强度
     */
    fun getAnime4KStrength(): Float {
        return sharedPreferences.getFloat("anime4k_strength", 1.0f)
    }
    
    fun setAnime4KStrength(strength: Float) {
        sharedPreferences.edit().putFloat("anime4k_strength", strength).apply()
    }
    
    /**
     * 获取弹幕字体大小（Float版本）
     */
    fun getDanmakuFontSize(): Float {
        return getDanmakuSize().toFloat()
    }
    
    fun setDanmakuFontSize(size: Float) {
        setDanmakuSize(size.toInt())
    }
    
    /**
     * 是否启用弹幕描边
     */
    fun isDanmakuStrokeEnabled(): Boolean {
        return sharedPreferences.getBoolean("danmaku_stroke_enabled", true)
    }
    
    fun setDanmakuStrokeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("danmaku_stroke_enabled", enabled).apply()
    }
    
    /**
     * 获取弹幕最大行数
     */
    fun getDanmakuMaxLines(): Int {
        return sharedPreferences.getInt("danmaku_max_lines", 5)
    }
    
    fun setDanmakuMaxLines(maxLines: Int) {
        sharedPreferences.edit().putInt("danmaku_max_lines", maxLines).apply()
    }
    
    /**
     * 获取字幕字体大小（全局设置，Float版本）
     */
    fun getSubtitleFontSize(): Float {
        return sharedPreferences.getFloat("subtitle_font_size", 20f)
    }
    
    fun setSubtitleFontSize(size: Float) {
        sharedPreferences.edit().putFloat("subtitle_font_size", size).apply()
    }
    
    /**
     * 获取字幕位置（全局设置）
     */
    fun getSubtitlePosition(): Int {
        return sharedPreferences.getInt("subtitle_position", 100)
    }
    
    fun setSubtitlePosition(position: Int) {
        sharedPreferences.edit().putInt("subtitle_position", position).apply()
    }
    
    /**
     * 是否启用自动旋转
     */
    fun isAutoRotateEnabled(): Boolean {
        return sharedPreferences.getBoolean("auto_rotate_enabled", false)
    }
    
    fun setAutoRotateEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_rotate_enabled", enabled).apply()
    }
    
    /**
     * 是否启用竖屏UI
     */
    fun isPortraitUIEnabled(): Boolean {
        return sharedPreferences.getBoolean("portrait_ui_enabled", false)
    }
    
    fun setPortraitUIEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("portrait_ui_enabled", enabled).apply()
    }

    /**
     * 是否启用自动连播（百分百复用 mpvEx autoplayNextVideo 算法）
     */
    fun isAutoPlayNextEnabled(): Boolean {
        return sharedPreferences.getBoolean("autoplay_next_video", true)
    }

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("autoplay_next_video", enabled).apply()
    }

    /**
     * 是否在视频结束后关闭播放器（百分百复用 mpvEx closeAfterReachingEndOfVideo 算法）
     */
    fun isCloseAfterEndOfVideo(): Boolean {
        return sharedPreferences.getBoolean("close_after_eof", true)
    }

    fun setCloseAfterEndOfVideo(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("close_after_eof", enabled).apply()
    }

    // ==================== 文件扫描 ====================

    /**
     * 获取是否启用 .nomedia 规则（旧，保持兼容）
     */
    fun isNomediaEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.NOMEDIA_ENABLED,
            true  // 默认启用（与系统行为一致）
        )
    }

    /**
     * 设置 .nomedia 规则开关（旧，保持兼容）
     */
    fun setNomediaEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.NOMEDIA_ENABLED, enabled).apply()
    }

    /**
     * 获取是否扫描包含 .nomedia 文件的文件夹
     * 与旧版 isNomediaEnabled() 逻辑相反
     */
    fun getIncludeNoMediaFolders(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.INCLUDE_NOMEDIA_FOLDERS,
            false  // 默认不扫描（与系统行为一致）
        )
    }

    /**
     * 设置是否扫描包含 .nomedia 文件的文件夹
     */
    fun setIncludeNoMediaFolders(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.INCLUDE_NOMEDIA_FOLDERS, enabled).apply()
        // 同步旧键值，保持兼容
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.NOMEDIA_ENABLED, !enabled).apply()
    }

    /**
     * 获取是否扫描隐藏文件夹（以点号开头）
     */
    fun isScanHiddenFoldersEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.SCAN_HIDDEN_FOLDERS,
            false  // 默认不扫描隐藏文件夹
        )
    }

    /**
     * 设置扫描隐藏文件夹开关
     */
    fun setScanHiddenFoldersEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.SCAN_HIDDEN_FOLDERS, enabled).apply()
    }

    // ==================== 逆向扫描警告 ====================

    /**
     * 获取是否不再显示逆向扫描警告
     */
    fun getDontShowNomediaWarning(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.DONT_SHOW_NOMEDIA_WARNING,
            false  // 默认显示警告
        )
    }

    /**
     * 设置不再显示逆向扫描警告
     */
    fun setDontShowNomediaWarning(dontShow: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.DONT_SHOW_NOMEDIA_WARNING, dontShow).apply()
    }

    // ==================== 章节进度条 ====================

    /**
     * 获取是否启用章节进度条
     */
    fun isChapterBarEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.CHAPTER_BAR_ENABLED,
            true  // 默认启用
        )
    }

    /**
     * 设置章节进度条开关
     */
    fun setChapterBarEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.CHAPTER_BAR_ENABLED, enabled).apply()
    }

    // ==================== 进度条缩略图预览 ====================

    fun isSeekbarThumbnailEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.SEEKBAR_THUMBNAIL_ENABLED,
            false  // 默认关闭
        )
    }

    fun setSeekbarThumbnailEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.SEEKBAR_THUMBNAIL_ENABLED, enabled).apply()
    }

    // ==================== MPV 解码器预设 ====================

    /**
     * 获取 MPV 解码器预设
     * @return "fast", "default", "high-quality", "gpu-hq", "low-latency", "sw-fast"
     */
    fun getMpvProfile(): String {
        return sharedPreferences.getString(AppConstants.Preferences.MPV_PROFILE, "fast") ?: "fast"
    }

    /**
     * 设置 MPV 解码器预设
     */
    fun setMpvProfile(profile: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.MPV_PROFILE, profile).apply()
    }

    // ==================== 音频均衡器 ====================

    fun isEqualizerEnabled(): Boolean {
        return sharedPreferences.getBoolean(AppConstants.Preferences.EQ_ENABLED, false)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.EQ_ENABLED, enabled).apply()
    }

    fun getEqualizerBand(index: Int): Float {
        return sharedPreferences.getFloat("${AppConstants.Preferences.EQ_BAND_PREFIX}$index", 0f)
    }

    fun setEqualizerBand(index: Int, value: Float) {
        sharedPreferences.edit().putFloat("${AppConstants.Preferences.EQ_BAND_PREFIX}$index", value).apply()
    }

    fun getEqualizerBands(): List<Float> {
        return (0..4).map { getEqualizerBand(it) }
    }

    fun getEqualizerBassBoost(): Int {
        return sharedPreferences.getInt(AppConstants.Preferences.EQ_BASS_BOOST, 0)
    }

    fun setEqualizerBassBoost(value: Int) {
        sharedPreferences.edit().putInt(AppConstants.Preferences.EQ_BASS_BOOST, value).apply()
    }

    fun getEqualizerVirtualizer(): Int {
        return sharedPreferences.getInt(AppConstants.Preferences.EQ_VIRTUALIZER, 0)
    }

    fun setEqualizerVirtualizer(value: Int) {
        sharedPreferences.edit().putInt(AppConstants.Preferences.EQ_VIRTUALIZER, value).apply()
    }

    // ==================== GPU Next 渲染 ====================

    /**
     * 获取是否启用 GPU Next 渲染
     */
    fun getGpuNext(): Boolean {
        return sharedPreferences.getBoolean(AppConstants.Preferences.GPU_NEXT, false)
    }

    /**
     * 设置是否启用 GPU Next 渲染
     */
    fun setGpuNext(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.GPU_NEXT, enabled).apply()
    }

    /**
     * 获取是否启用 Vulkan 渲染上下文
     */
    fun getUseVulkan(): Boolean {
        return sharedPreferences.getBoolean(AppConstants.Preferences.USE_VULKAN, false)
    }

    /**
     * 设置是否启用 Vulkan 渲染上下文
     */
    fun setUseVulkan(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.USE_VULKAN, enabled).apply()
    }

    // ==================== 剩余时间显示 ====================

    /**
     * 获取是否显示剩余时间
     */
    fun getShowRemainingTime(): Boolean {
        return sharedPreferences.getBoolean(
            AppConstants.Preferences.SHOW_REMAINING_TIME,
            false
        )
    }

    /**
     * 保存剩余时间显示设置
     */
    fun setShowRemainingTime(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(AppConstants.Preferences.SHOW_REMAINING_TIME, enabled).apply()
    }

    // ==================== 文件夹视图模式 ====================

    /**
     * 获取文件夹视图模式 (FOLDER_VIEW, TREE_VIEW)
     */
    fun getFolderViewMode(): String {
        return sharedPreferences.getString(AppConstants.Preferences.FOLDER_VIEW_MODE, "FOLDER_VIEW") ?: "FOLDER_VIEW"
    }

    /**
     * 保存文件夹视图模式
     */
    fun setFolderViewMode(mode: String) {
        sharedPreferences.edit().putString(AppConstants.Preferences.FOLDER_VIEW_MODE, mode).apply()
    }
}
