package com.fam4k007.videoplayer.player

/**
 * 视频画面比例模式
 */
enum class VideoAspect(val displayName: String) {
    FIT("Fit to Screen"),      // 原始比例，完整显示
    STRETCH("Stretch"),     // 拉伸填充屏幕
    CROP("Crop");        // 裁剪填充屏幕
    
    /**
     * 获取下一个模式（循环切换）
     */
    fun next(): VideoAspect {
        return when (this) {
            FIT -> STRETCH
            STRETCH -> CROP
            CROP -> FIT
        }
    }
}
