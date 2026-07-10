package com.fam4k007.videoplayer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.fam4k007.videoplayer.R

/**
 * 应用主题枚举
 * 参考 mpvEx 设计，每个主题都有独特的配色方案
 * 支持亮色和暗色模式
 */
enum class AppTheme(
    @StringRes val titleRes: Int,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val tertiaryLight: Color,
    val tertiaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color,
    val isDynamic: Boolean = false,
) {
    /**
     * 动态主题 - 基于系统壁纸（Android 12+）【默认】
     */
    Dynamic(
        titleRes = R.string.theme_dynamic,
        primaryLight = Color(0xFF6750A4),
        primaryDark = Color(0xFFD0BCFF),
        secondaryLight = Color(0xFF625B71),
        secondaryDark = Color(0xFFCCC2DC),
        tertiaryLight = Color(0xFF7D5260),
        tertiaryDark = Color(0xFFEFB8C8),
        backgroundLight = Color(0xFFFFFBFF),
        backgroundDark = Color(0xFF1C1B1F),
        isDynamic = true,
    ),

    /**
     * 丁香紫 - 柔和紫色系（原默认主题）
     */
    Lilac(
        titleRes = R.string.theme_lilac,
        primaryLight = Color(0xFF794F81),
        primaryDark = Color(0xFFE8B5EF),
        secondaryLight = Color(0xFF6A596C),
        secondaryDark = Color(0xFFD6C0D6),
        tertiaryLight = Color(0xFF82524D),
        tertiaryDark = Color(0xFFF5B7B0),
        backgroundLight = Color(0xFFFFF7FB),
        backgroundDark = Color(0xFF161217),
    ),

    /**
     * Cloudflare - 橙色系
     */
    Cloudflare(
        titleRes = R.string.theme_cloudflare,
        primaryLight = Color(0xFFF6821F),
        primaryDark = Color(0xFFFFB77C),
        secondaryLight = Color(0xFF6B5E4C),
        secondaryDark = Color(0xFFD6C5AC),
        tertiaryLight = Color(0xFF855316),
        tertiaryDark = Color(0xFFFABD71),
        backgroundLight = Color(0xFFFFFBF7),
        backgroundDark = Color(0xFF1A1612),
    ),

    /**
     * CottonCandy - 棉花糖粉蓝色系
     */
    CottonCandy(
        titleRes = R.string.theme_cotton_candy,
        primaryLight = Color(0xFFE993C1),
        primaryDark = Color(0xFFFFB1D5),
        secondaryLight = Color(0xFF70A2C2),
        secondaryDark = Color(0xFF9ED0EF),
        tertiaryLight = Color(0xFF9C68AC),
        tertiaryDark = Color(0xFFDEB0E9),
        backgroundLight = Color(0xFFFFF8FA),
        backgroundDark = Color(0xFF1A1418),
    ),

    /**
     * Doom - 红色系
     */
    Doom(
        titleRes = R.string.theme_doom,
        primaryLight = Color(0xFFBB2929),
        primaryDark = Color(0xFFFF6B6B),
        secondaryLight = Color(0xFF6B5353),
        secondaryDark = Color(0xFFD6BABA),
        tertiaryLight = Color(0xFF8C4A4A),
        tertiaryDark = Color(0xFFFFB4AB),
        backgroundLight = Color(0xFFFFF8F7),
        backgroundDark = Color(0xFF1A1010),
    ),

    /**
     * GreenApple - 青苹果绿色系
     */
    GreenApple(
        titleRes = R.string.theme_green_apple,
        primaryLight = Color(0xFF2E7D32),
        primaryDark = Color(0xFF81C784),
        secondaryLight = Color(0xFF4A6349),
        secondaryDark = Color(0xFFB0CFB1),
        tertiaryLight = Color(0xFF3D7B5F),
        tertiaryDark = Color(0xFF8FD5B7),
        backgroundLight = Color(0xFFF6FFF6),
        backgroundDark = Color(0xFF0F1A0F),
    ),

    /**
     * Gruvbox - 复古暖色调
     */
    Gruvbox(
        titleRes = R.string.theme_gruvbox,
        primaryLight = Color(0xFF9D5B3F),
        primaryDark = Color(0xFFD89B6A),
        secondaryLight = Color(0xFF7A7556),
        secondaryDark = Color(0xFFB0AE8A),
        tertiaryLight = Color(0xFF4A7B7C),
        tertiaryDark = Color(0xFF8AAFA8),
        backgroundLight = Color(0xFFFBF1C7),
        backgroundDark = Color(0xFF282828),
    ),

    /**
     * Kanagawa - 日式水墨风
     */
    Kanagawa(
        titleRes = R.string.theme_kanagawa,
        primaryLight = Color(0xFF5A7785),
        primaryDark = Color(0xFF7E9CD8),
        secondaryLight = Color(0xFF8A7A6E),
        secondaryDark = Color(0xFFDCA561),
        tertiaryLight = Color(0xFF6A8E7F),
        tertiaryDark = Color(0xFF98BB6C),
        backgroundLight = Color(0xFFF2ECBC),
        backgroundDark = Color(0xFF1F1F28),
    ),

    /**
     * Lavender - 薰衣草紫色系
     */
    Lavender(
        titleRes = R.string.theme_lavender,
        primaryLight = Color(0xFF7C5AB8),
        primaryDark = Color(0xFFCFBCFF),
        secondaryLight = Color(0xFF635B70),
        secondaryDark = Color(0xFFCBC3DA),
        tertiaryLight = Color(0xFF7E525A),
        tertiaryDark = Color(0xFFF2B8C1),
        backgroundLight = Color(0xFFFCF8FF),
        backgroundDark = Color(0xFF16121A),
    ),

    /**
     * Mocha 主题 - 摩卡棕色系
     */
    Mocha(
        titleRes = R.string.theme_mocha,
        primaryLight = Color(0xFF795548),
        primaryDark = Color(0xFFBCAAA4),
        secondaryLight = Color(0xFF5D4037),
        secondaryDark = Color(0xFFA1887F),
        tertiaryLight = Color(0xFF6D4C41),
        tertiaryDark = Color(0xFFD7CCC8),
        backgroundLight = Color(0xFFFFF9F5),
        backgroundDark = Color(0xFF1A1512),
    ),

    /**
     * Strawberry - 草莓粉色系
     */
    Strawberry(
        titleRes = R.string.theme_strawberry,
        primaryLight = Color(0xFFD81B60),
        primaryDark = Color(0xFFF48FB1),
        secondaryLight = Color(0xFF6B4958),
        secondaryDark = Color(0xFFD6B0C1),
        tertiaryLight = Color(0xFFC2185B),
        tertiaryDark = Color(0xFFF8BBD9),
        backgroundLight = Color(0xFFFFF5F8),
        backgroundDark = Color(0xFF1A1015),
    ),

    /**
     * Tidal - 青绿色系
     */
    Tidal(
        titleRes = R.string.theme_tidal,
        primaryLight = Color(0xFF00796B),
        primaryDark = Color(0xFF80CBC4),
        secondaryLight = Color(0xFF4A635E),
        secondaryDark = Color(0xFFB0CFC9),
        tertiaryLight = Color(0xFF00897B),
        tertiaryDark = Color(0xFF4DB6AC),
        backgroundLight = Color(0xFFF2FFFD),
        backgroundDark = Color(0xFF0F1A18),
    ),

    /**
     * Nord - 北欧蓝色系
     */
    Nord(
        titleRes = R.string.theme_nord,
        primaryLight = Color(0xFF5E81AC),
        primaryDark = Color(0xFF88C0D0),
        secondaryLight = Color(0xFF4C566A),
        secondaryDark = Color(0xFFD8DEE9),
        tertiaryLight = Color(0xFFB48EAD),
        tertiaryDark = Color(0xFFD8A9C4),
        backgroundLight = Color(0xFFECEFF4),
        backgroundDark = Color(0xFF2E3440),
    ),

    /**
     * RosePine - 玫瑰松木色系
     */
    RosePine(
        titleRes = R.string.theme_rose_pine,
        primaryLight = Color(0xFF907AA9),
        primaryDark = Color(0xFFC4A7E7),
        secondaryLight = Color(0xFFB4637A),
        secondaryDark = Color(0xFFEBBCBA),
        tertiaryLight = Color(0xFF7A9A8A),
        tertiaryDark = Color(0xFF9CCFD8),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark = Color(0xFF232136),
    ),

    /**
     * TakoGreen - 清新绿色系
     */
    TakoGreen(
        titleRes = R.string.theme_tako_green,
        primaryLight = Color(0xFF66BB6A),
        primaryDark = Color(0xFFA5D6A7),
        secondaryLight = Color(0xFF546E7A),
        secondaryDark = Color(0xFF90A4AE),
        tertiaryLight = Color(0xFF43A047),
        tertiaryDark = Color(0xFF81C784),
        backgroundLight = Color(0xFFF5FFF5),
        backgroundDark = Color(0xFF121A12),
    ),

    /**
     * TokyoNight - 东京夜景蓝色系
     */
    TokyoNight(
        titleRes = R.string.theme_tokyo_night,
        primaryLight = Color(0xFF3D5A80),
        primaryDark = Color(0xFF7D9BC1),
        secondaryLight = Color(0xFF6B5B95),
        secondaryDark = Color(0xFFA89DC9),
        tertiaryLight = Color(0xFF4A6B5C),
        tertiaryDark = Color(0xFF8AB4A3),
        backgroundLight = Color(0xFFF0F1F5),
        backgroundDark = Color(0xFF1A1B26),
    ),

    /**
     * YinYang - 黑白极简风格
     */
    YinYang(
        titleRes = R.string.theme_yin_yang,
        primaryLight = Color(0xFF424242),
        primaryDark = Color(0xFFBDBDBD),
        secondaryLight = Color(0xFF616161),
        secondaryDark = Color(0xFFE0E0E0),
        tertiaryLight = Color(0xFF757575),
        tertiaryDark = Color(0xFFEEEEEE),
        backgroundLight = Color(0xFFFAFAFA),
        backgroundDark = Color(0xFF121212),
    ),

    /**
     * Yotsuba - 橙色暖系
     */
    Yotsuba(
        titleRes = R.string.theme_yotsuba,
        primaryLight = Color(0xFFFF8A65),
        primaryDark = Color(0xFFFFAB91),
        secondaryLight = Color(0xFF6D5D5B),
        secondaryDark = Color(0xFFD6C4C2),
        tertiaryLight = Color(0xFFFF7043),
        tertiaryDark = Color(0xFFFFCCBC),
        backgroundLight = Color(0xFFFFF8F5),
        backgroundDark = Color(0xFF1A1412),
    ),

    /**
     * Sapphire - 蓝宝石色系
     */
    Sapphire(
        titleRes = R.string.theme_sapphire,
        primaryLight = Color(0xFF1E88E5),
        primaryDark = Color(0xFF64B5F6),
        secondaryLight = Color(0xFF5C6BC0),
        secondaryDark = Color(0xFF9FA8DA),
        tertiaryLight = Color(0xFF0288D1),
        tertiaryDark = Color(0xFF4FC3F7),
        backgroundLight = Color(0xFFF3F8FF),
        backgroundDark = Color(0xFF0D1620),
    ),

    /**
     * Ocean 主题 - 海洋蓝色系
     */
    Ocean(
        titleRes = R.string.theme_ocean,
        primaryLight = Color(0xFF006064),
        primaryDark = Color(0xFF4DD0E1),
        secondaryLight = Color(0xFF00838F),
        secondaryDark = Color(0xFF80DEEA),
        tertiaryLight = Color(0xFF0097A7),
        tertiaryDark = Color(0xFF26C6DA),
        backgroundLight = Color(0xFFF0FFFF),
        backgroundDark = Color(0xFF0A1A1C),
    ),

    /**
     * Forest 主题 - 森林绿色系
     */
    Forest(
        titleRes = R.string.theme_forest,
        primaryLight = Color(0xFF1B5E20),
        primaryDark = Color(0xFF66BB6A),
        secondaryLight = Color(0xFF33691E),
        secondaryDark = Color(0xFF9CCC65),
        tertiaryLight = Color(0xFF2E7D32),
        tertiaryDark = Color(0xFFA5D6A7),
        backgroundLight = Color(0xFFF1F8E9),
        backgroundDark = Color(0xFF0D1A0D),
    ),

    /**
     * Sunset 主题 - 日落橙色系
     */
    Sunset(
        titleRes = R.string.theme_sunset,
        primaryLight = Color(0xFFE65100),
        primaryDark = Color(0xFFFF9E80),
        secondaryLight = Color(0xFFEF6C00),
        secondaryDark = Color(0xFFFFCC80),
        tertiaryLight = Color(0xFFF4511E),
        tertiaryDark = Color(0xFFFF8A65),
        backgroundLight = Color(0xFFFFF5F0),
        backgroundDark = Color(0xFF1A120D),
    ),

    /**
     * RoseGold 主题 - 玫瑰金色系
     */
    RoseGold(
        titleRes = R.string.theme_rose_gold,
        primaryLight = Color(0xFFB76E79),
        primaryDark = Color(0xFFE8A9B0),
        secondaryLight = Color(0xFFAD8075),
        secondaryDark = Color(0xFFDDBFB8),
        tertiaryLight = Color(0xFFD4A5A5),
        tertiaryDark = Color(0xFFF5D5D5),
        backgroundLight = Color(0xFFFFF5F5),
        backgroundDark = Color(0xFF1A1212),
    ),

    /**
     * Midnight 主题 - 午夜蓝色系
     */
    Midnight(
        titleRes = R.string.theme_midnight,
        primaryLight = Color(0xFF0D47A1),
        primaryDark = Color(0xFF90CAF9),
        secondaryLight = Color(0xFF455A64),
        secondaryDark = Color(0xFFB0BEC5),
        tertiaryLight = Color(0xFF1565C0),
        tertiaryDark = Color(0xFF64B5F6),
        backgroundLight = Color(0xFFF5F9FF),
        backgroundDark = Color(0xFF0D1117),
    ),

    /**
     * Violet - 紫罗兰色系
     */
    Violet(
        titleRes = R.string.theme_violet,
        primaryLight = Color(0xFF6A1B9A),
        primaryDark = Color(0xFFCE93D8),
        secondaryLight = Color(0xFF7B1FA2),
        secondaryDark = Color(0xFFE1BEE7),
        tertiaryLight = Color(0xFF8E24AA),
        tertiaryDark = Color(0xFFBA68C8),
        backgroundLight = Color(0xFFFCF5FF),
        backgroundDark = Color(0xFF150D1A),
    ),

    /**
     * Amber - 琥珀黄色系
     */
    Amber(
        titleRes = R.string.theme_amber,
        primaryLight = Color(0xFFFF8F00),
        primaryDark = Color(0xFFFFCA28),
        secondaryLight = Color(0xFFFFA000),
        secondaryDark = Color(0xFFFFD54F),
        tertiaryLight = Color(0xFFFFB300),
        tertiaryDark = Color(0xFFFFE082),
        backgroundLight = Color(0xFFFFFBF0),
        backgroundDark = Color(0xFF1A1508),
    ),

    /**
     * Coral - 珊瑚红色系
     */
    Coral(
        titleRes = R.string.theme_coral,
        primaryLight = Color(0xFFFF5252),
        primaryDark = Color(0xFFFF8A80),
        secondaryLight = Color(0xFFFF6E40),
        secondaryDark = Color(0xFFFFAB91),
        tertiaryLight = Color(0xFFFF7043),
        tertiaryDark = Color(0xFFFFCCBC),
        backgroundLight = Color(0xFFFFF5F5),
        backgroundDark = Color(0xFF1A1010),
    ),

    /**
     * Slate - 石板灰色系
     */
    Slate(
        titleRes = R.string.theme_slate,
        primaryLight = Color(0xFF455A64),
        primaryDark = Color(0xFF90A4AE),
        secondaryLight = Color(0xFF546E7A),
        secondaryDark = Color(0xFFB0BEC5),
        tertiaryLight = Color(0xFF607D8B),
        tertiaryDark = Color(0xFFCFD8DC),
        backgroundLight = Color(0xFFF5F7F8),
        backgroundDark = Color(0xFF151A1C),
    ),

    /**
     * Dracula - 德古拉紫色系
     */
    Dracula(
        titleRes = R.string.theme_dracula,
        primaryLight = Color(0xFF6272A4),
        primaryDark = Color(0xFFBD93F9),
        secondaryLight = Color(0xFF44475A),
        secondaryDark = Color(0xFFFF79C6),
        tertiaryLight = Color(0xFF50FA7B),
        tertiaryDark = Color(0xFF8BE9FD),
        backgroundLight = Color(0xFFF8F8F2),
        backgroundDark = Color(0xFF282A36),
    ),

    /**
     * Monochrome - 单色灰色系
     */
    Monochrome(
        titleRes = R.string.theme_monochrome,
        primaryLight = Color(0xFF212121),
        primaryDark = Color(0xFFE0E0E0),
        secondaryLight = Color(0xFF424242),
        secondaryDark = Color(0xFFBDBDBD),
        tertiaryLight = Color(0xFF616161),
        tertiaryDark = Color(0xFF9E9E9E),
        backgroundLight = Color(0xFFFFFFFF),
        backgroundDark = Color(0xFF0A0A0A),
    );

    /**
     * 获取亮色配色方案
     */
    open fun getLightColorScheme(): ColorScheme {
        return lightColorScheme(
            primary = primaryLight,
            secondary = secondaryLight,
            tertiary = tertiaryLight,
            background = backgroundLight,
            surface = backgroundLight,
            surfaceVariant = backgroundLight.copy(alpha = 0.9f),
        )
    }

    /**
     * 获取暗色配色方案
     */
    open fun getDarkColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = primaryDark,
            secondary = secondaryDark,
            tertiary = tertiaryDark,
            background = backgroundDark,
            surface = backgroundDark,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF1F1F1F),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424),
            surfaceVariant = backgroundDark.copy(alpha = 0.9f),
        )
    }

    /**
     * 获取 AMOLED 纯黑配色方案（为深色模式优化，省电）
     */
    fun getAmoledColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = primaryDark,
            secondary = secondaryDark,
            tertiary = tertiaryDark,
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0A0A0A),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF1F1F1F),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424),
        )
    }

    companion object {
        /**
         * 从名称获取主题
         */
        fun fromName(name: String): AppTheme {
            // 兼容旧设置：旧版 "Default" 映射到新名称 "Lilac"
            val resolved = if (name == "Default") "Lilac" else name
            return entries.find { it.name == resolved } ?: Dynamic
        }

        /**
         * 从资源 ID 获取主题
         */
        fun fromTitleRes(@StringRes titleRes: Int): AppTheme {
            return entries.find { it.titleRes == titleRes } ?: Dynamic
        }
    }
}

/**
 * 暗色模式枚举
 */
enum class DarkMode {
    Light,   // 始终亮色
    Dark,    // 始终暗色
    Amoled,  // AMOLED 纯黑模式
    System;  // 跟随系统

    companion object {
        fun fromName(name: String): DarkMode {
            return entries.find { it.name == name } ?: System
        }
    }
}
