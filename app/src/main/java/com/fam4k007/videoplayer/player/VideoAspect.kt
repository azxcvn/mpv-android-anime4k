package com.fam4k007.videoplayer.player

/**
 * 视频画面比例模式
 */
enum class VideoAspect(val displayName: String) {
    FIT("自动"),
    STRETCH("拉伸"),
    CROP("裁剪"),
    EQUAL_WIDTH("等宽"),
    EQUAL_HEIGHT("等高"),
    ORIGINAL("原始"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9");
}
