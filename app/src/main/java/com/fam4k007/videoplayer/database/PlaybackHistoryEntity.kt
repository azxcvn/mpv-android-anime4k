package com.fam4k007.videoplayer.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fam4k007.videoplayer.utils.FormatUtils

/**
 * 播放历史记录实体
 * 从SharedPreferences+JSON迁移到Room数据库
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey
    val uri: String,
    val fileName: String,
    val position: Long,          // 播放位置（毫秒）
    val duration: Long,          // 总时长（毫秒）
    val lastPlayed: Long,        // 最后播放时间戳
    val folderName: String,      // 所属文件夹
    val danmuPath: String? = null,        // 弹幕文件路径
    val danmuVisible: Boolean = true,     // 弹幕显示状态
    val danmuOffsetTime: Long = 0L,       // 弹幕时间偏移（毫秒）
    val thumbnailPath: String? = null,    // 视频缩略图路径
    val hasBeenWatched: Boolean = false   // 是否已观看（进度超过阈值）
) {
    /**
     * 格式化播放时间
     */
    fun getFormattedDate(): String {
        return FormatUtils.formatDateShort(lastPlayed)
    }

    /**
     * 获取播放进度百分比
     */
    fun getProgressPercentage(): Int {
        return if (duration > 0) {
            ((position.toDouble() / duration) * 100).toInt()
        } else {
            0
        }
    }
}
