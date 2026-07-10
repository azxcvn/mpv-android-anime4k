package com.fam4k007.videoplayer.player

import androidx.compose.ui.graphics.Color

/**
 * OP/ED 跳过片段类型
 */
enum class SkipSegmentType(
    val label: String,
    val accentColor: Color,
) {
    INTRO("跳过片头", Color(0xFFFF7A00)),
    RECAP("跳过前情提要", Color(0xFF2F80FF)),
    OUTRO("跳过片尾", Color(0xFFE05666)),
    CREDITS("跳过制作人员", Color(0xFFA64DFF)),
    PREVIEW("跳过下集预告", Color(0xFF00D4C7)),
}

/**
 * 跳过片段数据
 *
 * @param type 片段类型（OP/ED 等）
 * @param startSeconds 起始时间（秒）
 * @param endSeconds 结束时间（秒）
 * @param source 来源标识（"chapter" 表示从章节检测）
 */
data class SkipSegment(
    val type: SkipSegmentType,
    val startSeconds: Double,
    val endSeconds: Double,
    val source: String = "chapter",
) {
    val isValid: Boolean get() = endSeconds > startSeconds + 1.0
}
