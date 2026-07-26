package com.fam4k007.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.repository.PlayerRepository
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置ViewModel
 * 管理应用设置相关的UI状态
 */
class SettingsViewModel(
    private val playerRepository: PlayerRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
    }
    
    // ==================== UI State ====================
    
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()
    
    // ==================== 初始化 ====================
    
    init {
        loadAllSettings()
    }
    
    /**
     * 加载所有设置
     */
    fun loadAllSettings() {
        viewModelScope.launch {
            try {
                val settings = SettingsState(
                    // 播放器设置
                    seekTimeSeconds = playerRepository.getSeekTimeSeconds(),
                    rememberPosition = playerRepository.isRememberPositionEnabled(),
                    rememberSpeed = playerRepository.isRememberSpeedEnabled(),
                    rememberBrightness = playerRepository.isRememberBrightnessEnabled(),
                    autoLoadDanmaku = playerRepository.isAutoLoadDanmakuEnabled(),
                    danmakuAutoMatch = playerRepository.isDanmakuAutoMatchEnabled(),
                    hardwareDecoding = playerRepository.isHardwareDecodingEnabled(),
                    gestureControlEnabled = playerRepository.isGestureControlEnabled(),
                    
                    // Anime4K设置
                    anime4kEnabled = playerRepository.isAnime4KEnabled(),
                    anime4kMode = playerRepository.getAnime4KMode(),
                    anime4kStrength = playerRepository.getAnime4KStrength(),
                    
                    // 弹幕设置
                    danmakuSpeed = playerRepository.getDanmakuSpeed(),
                    danmakuFontSize = playerRepository.getDanmakuFontSize(),
                    danmakuAlpha = playerRepository.getDanmakuAlpha(),
                    danmakuStroke = playerRepository.isDanmakuStrokeEnabled(),
                    danmakuMaxLines = playerRepository.getDanmakuMaxLines(),
                    
                    // 字幕设置
                    subtitleFontSize = playerRepository.getSubtitleFontSize(),
                    subtitlePosition = playerRepository.getSubtitlePosition(),
                    
                    // 其他设置
                    autoRotate = playerRepository.isAutoRotateEnabled(),
                    portraitUI = playerRepository.isPortraitUIEnabled()
                )
                _settingsState.value = settings
                Logger.d(TAG, "Loaded all settings")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load settings", e)
            }
        }
    }
    
    // ==================== 播放器设置 ====================
    
    /**
     * 设置快进/快退时长
     */
    fun setSeekTimeSeconds(seconds: Int) {
        viewModelScope.launch {
            try {
                playerRepository.setSeekTimeSeconds(seconds)
                _settingsState.value = _settingsState.value.copy(seekTimeSeconds = seconds)
                Logger.d(TAG, "Set seek time: $seconds seconds")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set seek time", e)
            }
        }
    }
    
    /**
     * 设置记忆播放位置
     */
    fun setRememberPosition(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setRememberPositionEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(rememberPosition = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set remember position", e)
            }
        }
    }
    
    /**
     * 设置记忆播放速度
     */
    fun setRememberSpeed(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setRememberSpeedEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(rememberSpeed = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set remember speed", e)
            }
        }
    }
    
    /**
     * 设置记忆亮度
     */
    fun setRememberBrightness(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setRememberBrightnessEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(rememberBrightness = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set remember brightness", e)
            }
        }
    }
    
    /**
     * 设置自动加载弹幕
     */
    fun setAutoLoadDanmaku(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setAutoLoadDanmakuEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(autoLoadDanmaku = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set auto load danmaku", e)
            }
        }
    }

    fun setDanmakuAutoMatch(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuAutoMatchEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(danmakuAutoMatch = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku auto match", e)
            }
        }
    }
    
    /**
     * 设置硬件解码
     */
    fun setHardwareDecoding(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setHardwareDecodingEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(hardwareDecoding = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set hardware decoding", e)
            }
        }
    }
    
    /**
     * 设置手势控制
     */
    fun setGestureControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setGestureControlEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(gestureControlEnabled = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set gesture control", e)
            }
        }
    }
    
    // ==================== Anime4K设置 ====================
    
    /**
     * 设置Anime4K开关
     */
    fun setAnime4KEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setAnime4KEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(anime4kEnabled = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set Anime4K enabled", e)
            }
        }
    }
    
    /**
     * 设置Anime4K模式
     */
    fun setAnime4KMode(mode: String) {
        viewModelScope.launch {
            try {
                playerRepository.setAnime4KMode(mode)
                _settingsState.value = _settingsState.value.copy(anime4kMode = mode)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set Anime4K mode", e)
            }
        }
    }
    
    /**
     * 设置Anime4K强度
     */
    fun setAnime4KStrength(strength: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setAnime4KStrength(strength)
                _settingsState.value = _settingsState.value.copy(anime4kStrength = strength)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set Anime4K strength", e)
            }
        }
    }
    
    // ==================== 弹幕设置 ====================
    
    /**
     * 设置弹幕速度
     */
    fun setDanmakuSpeed(speed: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuSpeed(speed)
                _settingsState.value = _settingsState.value.copy(danmakuSpeed = speed)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku speed", e)
            }
        }
    }
    
    /**
     * 设置弹幕字体大小
     */
    fun setDanmakuFontSize(size: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuFontSize(size)
                _settingsState.value = _settingsState.value.copy(danmakuFontSize = size)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku font size", e)
            }
        }
    }
    
    /**
     * 设置弹幕透明度
     */
    fun setDanmakuAlpha(alpha: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuAlpha(alpha)
                _settingsState.value = _settingsState.value.copy(danmakuAlpha = alpha)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku alpha", e)
            }
        }
    }
    
    /**
     * 设置弹幕描边
     */
    fun setDanmakuStroke(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuStrokeEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(danmakuStroke = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku stroke", e)
            }
        }
    }
    
    /**
     * 设置弹幕最大行数
     */
    fun setDanmakuMaxLines(maxLines: Int) {
        viewModelScope.launch {
            try {
                playerRepository.setDanmakuMaxLines(maxLines)
                _settingsState.value = _settingsState.value.copy(danmakuMaxLines = maxLines)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set danmaku max lines", e)
            }
        }
    }
    
    // ==================== 字幕设置 ====================
    
    /**
     * 设置字幕字体大小
     */
    fun setSubtitleFontSize(size: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setSubtitleFontSize(size)
                _settingsState.value = _settingsState.value.copy(subtitleFontSize = size)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set subtitle font size", e)
            }
        }
    }
    
    /**
     * 设置字幕位置
     */
    fun setSubtitlePosition(position: Float) {
        viewModelScope.launch {
            try {
                playerRepository.setSubtitlePosition(position)
                _settingsState.value = _settingsState.value.copy(subtitlePosition = position)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set subtitle position", e)
            }
        }
    }
    
    // ==================== 其他设置 ====================
    
    /**
     * 设置自动旋转
     */
    fun setAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setAutoRotateEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(autoRotate = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set auto rotate", e)
            }
        }
    }
    
    /**
     * 设置竖屏UI
     */
    fun setPortraitUI(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.setPortraitUIEnabled(enabled)
                _settingsState.value = _settingsState.value.copy(portraitUI = enabled)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to set portrait UI", e)
            }
        }
    }
}

/**
 * 设置状态
 */
data class SettingsState(
    // 播放器设置
    val seekTimeSeconds: Int = 5,
    val rememberPosition: Boolean = true,
    val rememberSpeed: Boolean = true,
    val rememberBrightness: Boolean = true,
    val autoLoadDanmaku: Boolean = true,
    val danmakuAutoMatch: Boolean = false,  // 切集自动匹配弹幕（需自建服务器）
    val hardwareDecoding: Boolean = true,
    val gestureControlEnabled: Boolean = true,
    
    // Anime4K设置
    val anime4kEnabled: Boolean = false,
    val anime4kMode: String = "A",
    val anime4kStrength: Float = 1.0f,
    
    // 弹幕设置
    val danmakuSpeed: Float = 1.0f,
    val danmakuFontSize: Float = 16f,
    val danmakuAlpha: Float = 1.0f,
    val danmakuStroke: Boolean = true,
    val danmakuMaxLines: Int = 5,
    
    // 字幕设置
    val subtitleFontSize: Float = 16f,
    val subtitlePosition: Float = 0f,
    
    // 其他设置
    val autoRotate: Boolean = false,
    val portraitUI: Boolean = false
)
