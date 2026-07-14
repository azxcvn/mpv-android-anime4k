package com.fam4k007.videoplayer.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fam4k007.videoplayer.preferences.PreferencesManager

/**
 * 主题管理器 - 负责管理主题状态和持久化
 * 集成 PreferencesManager，提供 Compose 友好的 API
 */
class ThemeController(private val preferencesManager: PreferencesManager) {
    
    /**
     * 获取当前应用主题
     */
    fun getCurrentTheme(): AppTheme {
        val themeName = preferencesManager.getAppTheme()
        return AppTheme.fromName(themeName)
    }
    
    /**
     * 设置应用主题
     */
    fun setTheme(theme: AppTheme) {
        preferencesManager.setAppTheme(theme.name)
    }
    
    /**
     * 获取当前暗色模式
     */
    fun getDarkMode(): DarkMode {
        val modeStr = preferencesManager.getThemeMode()
        return when (modeStr) {
            "dark" -> DarkMode.Dark
            "light" -> DarkMode.Light
            "amoled" -> DarkMode.Amoled
            "system" -> DarkMode.System
            else -> DarkMode.System
        }
    }
    
    /**
     * 设置暗色模式
     */
    fun setDarkMode(mode: DarkMode) {
        val modeStr = when (mode) {
            DarkMode.Dark -> "dark"
            DarkMode.Light -> "light"
            DarkMode.Amoled -> "amoled"
            DarkMode.System -> "system"
        }
        preferencesManager.setThemeMode(modeStr)
        
        // 同步设置 AMOLED 模式
        preferencesManager.setAmoledMode(mode == DarkMode.Amoled)
        
        // 立即应用夜间模式，确保 recreate() 后 Activity 使用正确的配置
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                DarkMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                DarkMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                DarkMode.Amoled -> AppCompatDelegate.MODE_NIGHT_YES
                DarkMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
    
    /**
     * 获取是否启用 AMOLED 模式
     */
    fun getAmoledMode(): Boolean {
        return preferencesManager.getAmoledMode()
    }
    
    /**
     * 设置 AMOLED 模式
     */
    fun setAmoledMode(enabled: Boolean) {
        preferencesManager.setAmoledMode(enabled)
    }
    
    companion object {
        /**
         * 从 Context 创建 ThemeController
         */
        fun from(context: Context): ThemeController {
            val preferencesManager = PreferencesManager.getInstance(context)
            return ThemeController(preferencesManager)
        }
    }
}

/**
 * Composable 函数：记住主题控制器
 */
@Composable
fun rememberThemeController(context: Context): ThemeController {
    return remember { ThemeController.from(context) }
}

/**
 * 可观察的主题状态
 * 用于在 Compose 中管理主题切换
 */
class ThemeState(
    initialTheme: AppTheme = AppTheme.Lilac,
    initialDarkMode: DarkMode = DarkMode.System,
    initialAmoledMode: Boolean = false
) {
    var currentTheme by mutableStateOf(initialTheme)
        private set
    
    var darkMode by mutableStateOf(initialDarkMode)
        private set
    
    var amoledMode by mutableStateOf(initialAmoledMode)
        private set
    
    fun updateTheme(theme: AppTheme) {
        currentTheme = theme
    }
    
    fun updateDarkMode(mode: DarkMode) {
        darkMode = mode
    }
    
    fun updateAmoledMode(enabled: Boolean) {
        amoledMode = enabled
    }
}

/**
 * Composable 函数：记住主题状态
 */
@Composable
fun rememberThemeState(
    context: Context
): ThemeState {
    val controller = rememberThemeController(context)
    return remember {
        ThemeState(
            initialTheme = controller.getCurrentTheme(),
            initialDarkMode = controller.getDarkMode(),
            initialAmoledMode = controller.getAmoledMode()
        )
    }
}
