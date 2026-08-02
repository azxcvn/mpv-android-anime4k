package com.fam4k007.videoplayer.domain.subtitle

import android.content.Context
import android.net.Uri
import android.util.Log
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * 外挂字幕管理器
 * 负责加载和管理外部字幕文件
 */
class SubtitleManager {
    
    companion object {
        private const val TAG = "SubtitleManager"
    }
    
    // 保存最后添加的字幕文件路径
    private var lastAddedSubtitlePath: String? = null

    /**
     * 添加外挂字幕
     * @param context Android Context
     * @param subtitleUri 字幕文件的 Uri
     * @return 是否添加成功
     */
    fun addExternalSubtitle(
        context: Context, 
        subtitleUri: Uri
    ): Boolean {
        return try {
            val path = getSubtitlePath(context, subtitleUri)
            if (path != null) {
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== Adding external subtitle =====")
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Path: $path")
                
                // 保存路径供外部使用
                lastAddedSubtitlePath = path
                
                // 执行 sub-add 命令，使用 "select" 标志自动选中字幕
                // sub-add <path> [select|insert-next|append] [title]
                // "select" 标志会自动加载并显示该字幕
                MPVLib.command("sub-add", path, "select")
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "sub-add command executed with 'select' flag")
                
                // 立即查询 track-list 状态
                val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                val currentSid = MPVLib.getPropertyString("sid") ?: "no"
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Immediately after sub-add: track-list/count = $trackCount, current sid = $currentSid")
                
                for (i in 0 until trackCount) {
                    val type = MPVLib.getPropertyString("track-list/$i/type")
                    val id = MPVLib.getPropertyInt("track-list/$i/id")
                    val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: "unknown"
                    val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "  Track[$i]: type=$type, id=$id, lang=$lang, title=$title")
                }
                
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Successfully added external subtitle: $path")
                
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== End adding external subtitle =====")
                true
            } else {
                Log.w(TAG, "Failed to get subtitle path from URI: $subtitleUri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add external subtitle", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 从文件路径直接添加外挂字幕（新方法）
     * @param filePath 字幕文件的绝对路径
     * @return 是否添加成功
     */
    fun addExternalSubtitleFromPath(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "Subtitle file does not exist: $filePath")
                return false
            }
            
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== Adding external subtitle from path =====")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Path: $filePath")
            
            // 保存路径供外部使用
            lastAddedSubtitlePath = filePath
            
            // 执行 sub-add 命令，使用 "select" 标志自动选中字幕
            MPVLib.command("sub-add", filePath, "select")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "sub-add command executed with 'select' flag")
            
            // 立即查询 track-list 状态
            val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
            val currentSid = MPVLib.getPropertyString("sid") ?: "no"
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Immediately after sub-add: track-list/count = $trackCount, current sid = $currentSid")
            
            for (i in 0 until trackCount) {
                val type = MPVLib.getPropertyString("track-list/$i/type")
                val id = MPVLib.getPropertyInt("track-list/$i/id")
                val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: "unknown"
                val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "  Track[$i]: type=$type, id=$id, lang=$lang, title=$title")
            }
            
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Successfully added external subtitle: $filePath")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== End adding external subtitle =====")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add external subtitle from path", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取字幕文件的实际路径
     * 支持 content:// 和 file:// URI
     * 对于 content:// URI，复制到临时目录后返回真实路径
     */
    private fun getSubtitlePath(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // file:// URI 直接使用路径
                    uri.path?.let { path ->
                        if (File(path).exists()) {
                            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Using file:// path directly: $path")
                            path
                        } else {
                            com.fam4k007.videoplayer.utils.Logger.w(TAG, "File does not exist: $path")
                            null
                        }
                    }
                }
                "content" -> {
                    // content:// URI 需要复制到实际文件路径，MPV 无法直接处理 content:// URI
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "content:// URI detected, need to copy to cache")
                    copyContentUriToFile(context, uri)
                }
                else -> {
                    // 尝试直接使用 URI
                    com.fam4k007.videoplayer.utils.Logger.d(TAG, "Unknown scheme: ${uri.scheme}, trying direct URI")
                    uri.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subtitle path", e)
            null
        }
    }

    /**
     * 将 content:// URI 的文件复制到应用缓存目录
     * 返回缓存文件的实际路径供 MPV 使用
     * 修改：使用 filesDir 而不是 cacheDir，确保文件持久化
     */
    private fun copyContentUriToFile(context: Context, uri: Uri): String? {
        return try {
            val displayName = getSubtitleDisplayName(context, uri)
            // 使用 filesDir/subtitles 目录存储字幕，确保持久化
            val subtitleDir = File(context.filesDir, "subtitles")
            if (!subtitleDir.exists()) {
                subtitleDir.mkdirs()
            }
            
            // 使用 URI 的哈希值作为文件名的一部分，避免冲突
            val fileName = "${uri.hashCode()}_$displayName"
            val subtitleFile = File(subtitleDir, fileName)
            
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Copying content URI to persistent storage: ${subtitleFile.absolutePath}")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                subtitleFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (subtitleFile.exists()) {
                com.fam4k007.videoplayer.utils.Logger.d(TAG, "Subtitle file created successfully: ${subtitleFile.absolutePath} (${subtitleFile.length()} bytes)")
                subtitleFile.absolutePath
            } else {
                Log.e(TAG, "Failed to create subtitle file")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy content URI to file", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取字幕文件的显示名称
     */
    fun getSubtitleDisplayName(context: Context, uri: Uri): String {
        return when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: "")
                file.name
            }
            "content" -> {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            if (index >= 0) {
                                return it.getString(index)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get display name from content URI", e)
                }
                "External subtitle"
            }
            else -> {
                "Subtitle file"
            }
        }
    }

    /**
     * 检查文件是否是支持的字幕格式
     */
    fun isSupportedSubtitleFormat(filename: String): Boolean {
        val supportedExtensions = setOf(
            "srt", "ass", "ssa", "sub", "vtt", "lrc", 
            "sbv", "smi", "pjs", "psb", "rt", "vtt",
            "aqt", "mpl2", "txt", "dvd", "idx", "sup"
        )
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension in supportedExtensions
    }
    
    /**
     * 获取最后添加的字幕文件路径
     */
    fun getLastAddedSubtitlePath(): String? {
        return lastAddedSubtitlePath
    }
}
