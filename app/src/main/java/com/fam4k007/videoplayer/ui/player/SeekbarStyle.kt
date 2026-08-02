package com.fam4k007.videoplayer.ui.player

/**
 * 进度条样式枚举
 * 参考 mpvEx / mpvRx 设计，提供四种进度条样式
 */
enum class SeekbarStyle(val displayName: String) {
    Standard("标准"),
    Wavy("波浪"),
    Thick("粗条"),
    Slim("纤细");

    companion object {
        fun fromName(name: String): SeekbarStyle {
            return entries.find { it.name == name } ?: Standard
        }
    }
}
