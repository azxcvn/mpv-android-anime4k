package com.fam4k007.videoplayer.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 统一格式化工具类
 * 提供时间、日期、文件大小等格式化方法
 */
object FormatUtils {

    /**
     * 格式化时间（秒 → HH:MM:SS）
     * @param seconds 秒数
     * @return 格式化后的时间字符串，例如 "01:23:45"
     */
    fun formatTime(seconds: Double): String {
        if (seconds.isNaN() || seconds.isInfinite()) {
            return "00:00:00"
        }
        
        val totalSeconds = seconds.toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * 格式化进度条时间（仅显示 MM:SS，不显示小时）
     * @param seconds 秒数
     * @return 格式化后的时间字符串，例如 "23:45"
     */
    fun formatProgressTime(seconds: Double): String {
        if (seconds.isNaN() || seconds.isInfinite()) {
            return "00:00"
        }
        
        val totalSeconds = seconds.toLong()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    /**
     * 格式化文件大小（字节 → MB/GB）
     * @param bytes 字节数
     * @return 格式化后的文件大小字符串，例如 "1.5 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 格式化时长（毫秒 → HH:MM:SS 或 MM:SS）
     * @param milliseconds 毫秒数
     * @return 格式化后的时长字符串，例如 "01:23:45" 或 "23:45"
     */
    fun formatDuration(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 格式化时间戳为日期（毫秒 → YYYY-MM-DD HH:MM:SS）
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的日期时间字符串
     */
    fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 格式化时间戳为简短日期（毫秒 → YYYY-MM-DD HH:MM）
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的简短日期时间字符串
     */
    fun formatDateShort(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 生成时间戳后缀（用于截图文件名）
     * 格式：yyyyMMdd_HHmmss
     * @return 时间戳字符串，例如 "20231225_143022"
     */
    fun generateTimestamp(): String {
        return try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Exception) {
            System.currentTimeMillis().toString()
        }
    }
}
