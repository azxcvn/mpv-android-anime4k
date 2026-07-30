package com.fam4k007.videoplayer

/**
 * 应用全局常量定义
 * 统一管理所有配置常量、URL、默认值等
 */
object AppConstants {

    /**
     * SharedPreferences 配置
     */
    object Preferences {
        // 播放相关配置存储位置
        const val VIDEO_PLAYBACK = "VideoPlayback"
        const val PLAYER_PREFS = "player_preferences"
        const val PLAYBACK_HISTORY = "playback_history"

        // 播放设置 Key
        const val PRECISE_SEEKING = "precise_seeking"
        const val VOLUME_BOOST_ENABLED = "volume_boost_enabled"
        const val CONTROL_SYSTEM_VOLUME = "control_system_volume"
        const val SEEK_TIME = "seek_time"
        const val LONG_PRESS_SPEED = "long_press_speed"
        const val BRIGHTNESS_SENSITIVITY = "brightness_sensitivity"
        const val VOLUME_SENSITIVITY = "volume_sensitivity"
        const val ANIME4K_MEMORY_ENABLED = "anime4k_memory_enabled"
        const val ANIME4K_LAST_MODE = "anime4k_last_mode"
        const val ANIME4K_QUALITY = "anime4k_quality"
        
        // 倍速记忆设置 Key
        const val REMEMBER_PLAYBACK_SPEED = "remember_playback_speed"
        const val LAST_PLAYBACK_SPEED = "last_playback_speed"
        const val CUSTOM_SPEED_PRESETS = "custom_speed_presets"
        
        // 视频显示模式 Key
        const val VIDEO_DISPLAY_MODE = "video_display_mode"
        
        // 首次播放 Key
        const val IS_FIRST_PLAY = "is_first_play"
        // 杜比视界提示 Key
        const val DONT_SHOW_DV_WARNING = "dont_show_dv_warning"

        // 文件夹黑名单 Key
        const val FOLDER_BLACKLIST = "folder_blacklist"

        // 文件夹白名单 Key
        const val FOLDER_WHITELIST = "folder_whitelist"

        // 白名单模式开关 Key
        const val WHITELIST_MODE_ENABLED = "whitelist_mode_enabled"

        // 记录播放历史开关 Key
        const val HISTORY_RECORDING_ENABLED = "history_recording_enabled"
        
        // 双击手势设置 Key
        const val DOUBLE_TAP_MODE = "double_tap_mode"  // 0=暂停, 1=快进快退
        const val DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds"  // 双击跳转秒数

        // 剩余时间显示 Key
        const val SHOW_REMAINING_TIME = "show_remaining_time"

        // 章节进度条 Key
        const val CHAPTER_BAR_ENABLED = "chapter_bar_enabled"

        // 进度条缩略图预览 Key
        const val SEEKBAR_THUMBNAIL_ENABLED = "seekbar_thumbnail_enabled"

        // 章节 OP/ED 检测开关
        const val CHAPTER_SKIP_DETECTION_ENABLED = "chapter_skip_detection_enabled"

        // 播放界面动画开关
        const val CONTROLS_ANIMATION_ENABLED = "controls_animation_enabled"

        // 抽屉界面动画开关
        const val DRAWER_ANIMATION_ENABLED = "drawer_animation_enabled"

        // MPV 解码器预设
        const val MPV_PROFILE = "mpv_profile"
        const val GPU_NEXT = "gpu_next"
        const val USE_VULKAN = "use_vulkan"

        // 音频均衡器 Key
        const val EQ_ENABLED = "eq_enabled"
        const val EQ_BAND_PREFIX = "eq_band_"  // eq_band_0 ~ eq_band_4
        const val EQ_BASS_BOOST = "eq_bass_boost"
        const val EQ_VIRTUALIZER = "eq_virtualizer"

        // 文件扫描 Key
        const val NOMEDIA_ENABLED = "nomedia_enabled"
        const val INCLUDE_NOMEDIA_FOLDERS = "include_nomedia_folders"
        const val SCAN_HIDDEN_FOLDERS = "scan_hidden_folders"
        const val DONT_SHOW_NOMEDIA_WARNING = "dont_show_nomedia_warning"

        // 文件夹视图模式 Key
        const val FOLDER_VIEW_MODE = "folder_view_mode"
        const val FOLDER_CACHE = "folder_cache"          // 文件夹列表缓存（JSON）
        const val FOLDER_CACHE_TAG = "folder_cache_tag"  // 缓存对应的扫描选项标签
        const val FOLDER_CACHE_TIME = "folder_cache_time" // 缓存时间戳

        // 播放历史记录 Key
        const val HISTORY_LIST = "history_list"

        // 更新相关 Key
        const val AUTO_CHECK_UPDATE = "auto_check_update"
        const val IGNORED_UPDATE_VERSION = "ignored_update_version"
    }

    /**
     * URL 配置
     */
    object URLs {
        const val GITHUB_URL = "https://github.com/azxcvn/mpv-android-anime4k"
        const val CONTACT_URL = "https://github.com/azxcvn"
        const val HOME_PAGE = "https://github.com/azxcvn/mpv-android-anime4k"
        const val GITHUB_ISSUES_URL = "https://github.com/azxcvn/mpv-android-anime4k/issues"
    }

    /**
     * 默认值配置
     */
    object Defaults {
        // 快进/快退时长（秒）
        const val DEFAULT_SEEK_TIME = 5
        const val MIN_SEEK_TIME = 3
        const val MAX_SEEK_TIME = 30

        // 长按倍速
        const val DEFAULT_LONG_PRESS_SPEED = 2.0f
        const val MIN_LONG_PRESS_SPEED = 1.5f
        const val MAX_LONG_PRESS_SPEED = 3.5f
        const val DEFAULT_BRIGHTNESS_SENSITIVITY = 2.0f
        const val DEFAULT_VOLUME_SENSITIVITY = 150f
        
        // 双击手势默认配置
        const val DEFAULT_DOUBLE_TAP_MODE = 0  // 默认为暂停/播放模式
        const val DEFAULT_DOUBLE_TAP_SEEK_SECONDS = 10  // 默认10秒
        const val MIN_DOUBLE_TAP_SEEK_SECONDS = 5
        const val MAX_DOUBLE_TAP_SEEK_SECONDS = 30

        // 播放历史最大记录数
        const val MAX_HISTORY_SIZE = 50
        
        // 默认播放倍速
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
        
        // 默认自定义倍速预设列表
        val DEFAULT_SPEED_PRESETS = setOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0")
        
        // 视频显示模式
        const val DEFAULT_VIDEO_DISPLAY_MODE = "folder"
        
        // 已观看阈值（百分比，默认95%）
        const val DEFAULT_WATCHED_THRESHOLD = 95
        const val MIN_WATCHED_THRESHOLD = 50
        const val MAX_WATCHED_THRESHOLD = 100
    }

    /**
     * UI 时间配置（毫秒）
     */
    object Timings {
        // 控制条自动隐藏时间
        const val CONTROL_AUTO_HIDE_TIME = 5000L

        // Toast 显示时长
        const val TOAST_DURATION = 2000L

        // 进度更新间隔
        const val PROGRESS_UPDATE_INTERVAL = 500L
    }

    /**
     * 文件相关常量
     */
    object Files {
        // 截图保存目录相对路径
        const val SCREENSHOT_DIR_NAME = "Screenshots"

        // 支持的视频文件扩展名
        val SUPPORTED_VIDEO_EXTENSIONS = arrayOf(
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "m3u8", "mpd", "ts",
            "3gp", "3g2", "mxf", "ogv", "m2ts", "mts",
            "rmvb", "rm", "m4v", "f4v", "mpg", "mpeg", "vob", "divx"
        )

        // 支持的字幕文件扩展名
        val SUPPORTED_SUBTITLE_EXTENSIONS = arrayOf(
            "srt", "ass", "ssa", "sub", "vtt", "sbv", "json"
        )
    }

    /**
     * 日志标签前缀
     */
    object Logging {
        const val TAG_PREFIX = "FAM4K007"
        const val TAG_PLAYBACK = "Playback"
        const val TAG_PLAYER = "Player"
        const val TAG_GESTURE = "Gesture"
        const val TAG_CONTROLS = "Controls"
    }
}
