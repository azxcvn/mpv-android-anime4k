package com.fam4k007.videoplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * FAM4K007 应用主题
 * 参考 mpvEx 设计，支持多主题、暗色模式、动态颜色
 * 
 * @param appTheme 应用主题
 * @param darkMode 暗色模式设置
 * @param amoledMode 是否启用 AMOLED 纯黑模式（仅暗色模式有效）
 * @param content 内容 Composable
 */
@Composable
fun VideoPlayerTheme(
    appTheme: AppTheme = AppTheme.Lilac,
    darkMode: DarkMode = DarkMode.System,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDarkTheme = isSystemInDarkTheme()

    // 确定是否使用暗色主题
    val useDarkTheme = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.Amoled -> true
        DarkMode.System -> systemInDarkTheme
    }

    // 选择配色方案
    val colorScheme: ColorScheme = when {
        // 动态颜色（Android 12+）
        appTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when {
                useDarkTheme && amoledMode -> {
                    // 动态暗色 + AMOLED 纯黑
                    dynamicDarkColorScheme(context).copy(
                        background = android.graphics.Color.BLACK.toComposeColor(),
                        surface = android.graphics.Color.BLACK.toComposeColor(),
                    )
                }
                useDarkTheme -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }
        // AMOLED 纯黑模式
        useDarkTheme && amoledMode -> appTheme.getAmoledColorScheme()
        // 常规暗色模式
        useDarkTheme -> appTheme.getDarkColorScheme()
        // 亮色模式
        else -> appTheme.getLightColorScheme()
    }

    // 提供 Spacing 和 MaterialTheme
    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Int 颜色转 Compose Color 的扩展函数
 */
private fun Int.toComposeColor(): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(this)
}
