package com.fam4k007.videoplayer.ui.player

/**
 * Seekbar style enum
 * Designed with reference to mpvEx / mpvRx, provides four seekbar styles
 */
enum class SeekbarStyle(val displayName: String) {
    Standard("Standard"),
    Wavy("Wave"),
    Thick("Thick"),
    Slim("Slim");

    companion object {
        fun fromName(name: String): SeekbarStyle {
            return entries.find { it.name == name } ?: Standard
        }
    }
}
