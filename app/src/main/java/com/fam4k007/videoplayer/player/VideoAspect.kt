package com.fam4k007.videoplayer.player

/**
 * 视频画面比例模式
 */
enum class VideoAspect(val displayName: String) {
    FIT("Auto"),
    STRETCH("Stretch"),
    CROP("Crop"),
    EQUAL_WIDTH("Equal Width"),
    EQUAL_HEIGHT("Equal Height"),
    ORIGINAL("Original"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9");
}
