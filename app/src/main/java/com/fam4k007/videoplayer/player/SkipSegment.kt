package com.fam4k007.videoplayer.player

import androidx.compose.ui.graphics.Color

/**
 * OP/ED skip segment type
 */
enum class SkipSegmentType(
    val label: String,
    val accentColor: Color,
) {
    INTRO("Skip Intro", Color(0xFFFF7A00)),
    RECAP("Skip Recap", Color(0xFF2F80FF)),
    OUTRO("Skip Outro", Color(0xFFE05666)),
    CREDITS("Skip Credits", Color(0xFFA64DFF)),
    COLD_OPEN("Skip Opening Segment", Color(0xFFFFB300)),
    PREVIEW("Skip Next Preview", Color(0xFF00D4C7)),
}

/**
 * Skip segment data
 *
 * @param type segment type (OP/ED etc.)
 * @param startSeconds start time (seconds)
 * @param endSeconds end time (seconds)
 * @param source source identifier ("chapter" means detected from chapters)
 */
data class SkipSegment(
    val type: SkipSegmentType,
    val startSeconds: Double,
    val endSeconds: Double,
    val source: String = "chapter",
) {
    val isValid: Boolean get() = endSeconds > startSeconds + 1.0
}
