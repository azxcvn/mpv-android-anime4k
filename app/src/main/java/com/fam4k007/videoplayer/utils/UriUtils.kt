package com.fam4k007.videoplayer.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * URI工具类
 * 参考 mpvKt-0.1.6 实现
 * 用于处理 content:// 协议的URI，转换为MPV可识别的文件描述符路径
 */
object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * 将 content:// URI 转换为文件描述符路径
     * MPV无法直接访问Android的content URI，需要转换为 fd://
     * 
     * @param context Android上下文
     * @return 文件描述符路径 (fd://xxx) 或真实路径，失败返回null
     */
    fun Uri.openContentFd(context: Context): String? {
        return try {
            context.contentResolver.openFileDescriptor(this, "r")?.use { pfd ->
                val fd = pfd.detachFd()
                // 尝试找到真实路径
                val realPath = findRealPath(fd)
                if (realPath != null) {
                    // 如果能找到真实路径，关闭文件描述符并返回路径
                    ParcelFileDescriptor.adoptFd(fd).close()
                    realPath
                } else {
                    // 否则返回文件描述符路径
                    "fd://$fd"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open content URI: $this", e)
            null
        }
    }

    /**
     * 解析URI为MPV可用的路径
     * 
     * @param context Android上下文
     * @return MPV可识别的路径字符串
     */
    fun Uri.resolveUri(context: Context): String? {
        val filepath = when (scheme) {
            "file" -> path
            "content" -> openContentFd(context)
            "http", "https", "rtsp", "rtmp", "rtmps" -> toString()
            else -> {
                Log.e(TAG, "Unknown URI scheme: $scheme")
                null
            }
        }
        return filepath
    }

    /**
     * 尝试从文件描述符获取真实路径
     * 
     * @param fd 文件描述符
     * @return 真实路径或null
     */
    private fun findRealPath(fd: Int): String? {
        return try {
            val path = "/proc/self/fd/$fd"
            val file = java.io.File(path)
            if (file.exists()) {
                file.canonicalPath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find real path for fd: $fd", e)
            null
        }
    }
    
    /**
     * 从 URI 获取文件夹名称
     */
    fun Uri.getFolderName(): String {
        return try {
            val path = this.path ?: return "Unknown folder"
            val segments = path.split("/")
            val folderName = if (segments.size > 1) {
                segments[segments.size - 2]
            } else {
                "Unknown folder"
            }
            folderName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get folder name", e)
            "Unknown folder"
        }
    }
}
