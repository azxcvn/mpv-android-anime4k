package com.fam4k007.videoplayer.database

import androidx.room.ColumnInfo

/**
 * 播放状态精简数据类
 * 用于视频列表快速查询进度，只包含必要字段
 */
data class PlaybackState(
    val uri: String,
    val position: Long,
    val duration: Long,
    @ColumnInfo(name = "hasBeenWatched")
    val hasBeenWatched: Boolean
)
