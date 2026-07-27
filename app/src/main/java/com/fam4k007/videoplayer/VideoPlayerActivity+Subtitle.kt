package com.fam4k007.videoplayer

import android.net.Uri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.utils.Logger
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

private const val TAG = "VideoPlayerActivity"

/**
 * 字幕优先级列表：ass > srt > ssa > vtt > sub > sbv > json
 */
private val SUBTITLE_PRIORITY = listOf("ass", "srt", "ssa", "vtt", "sub", "sbv", "json")

/**
 * 自动加载同文件夹下的同名字幕文件
 */
internal fun VideoPlayerActivity.autoLoadSubtitleIfExists(videoUri: android.net.Uri) {
    try {
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== Auto-load subtitle start =====")
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video URI: $videoUri")

        // 检查PreferencesManager中是否已有字幕路径，如果有则跳过自动加载
        val savedSubtitlePath = preferencesManager.getExternalSubtitle(videoUri.toString())
        if (savedSubtitlePath != null && File(savedSubtitlePath).exists()) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Subtitle already exists in preferences: $savedSubtitlePath, skipping auto-load")
            return
        }

        // 获取视频文件真实路径
        val videoPath = getRealPathFromUri(videoUri)
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Real video path: $videoPath")

        if (videoPath == null) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Cannot get real path from URI")
            return
        }

        val videoFile = File(videoPath)
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video file exists: ${videoFile.exists()}, isFile: ${videoFile.isFile}")

        if (!videoFile.exists() || !videoFile.isFile) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video file not accessible")
            return
        }

        // 获取视频文件名（不含扩展名）
        val videoNameWithoutExt = videoFile.nameWithoutExtension
        val videoDir = videoFile.parentFile

        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video name without ext: $videoNameWithoutExt")
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video directory: ${videoDir?.absolutePath}")

        if (videoDir == null) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Video directory is null")
            return
        }

        // 模糊匹配：查找所有以视频文件名开头的字幕文件
        val allSubtitles = videoDir.listFiles()?.filter { file ->
            if (!file.isFile) return@filter false
            val fileName = file.name.lowercase()
            val videoName = videoNameWithoutExt.lowercase()

            // 文件名必须以视频名开头
            if (!fileName.startsWith(videoName)) return@filter false

            // 扩展名必须在支持列表中
            val ext = file.extension.lowercase()
            SUBTITLE_PRIORITY.contains(ext)
        } ?: emptyList()

        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Found ${allSubtitles.size} potential subtitle files")

        // 如果没有找到任何字幕，直接返回
        if (allSubtitles.isEmpty()) {
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "No matching subtitle file found")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== Auto-load subtitle end =====")
            return
        }

        // 使用公用排序函数选择最佳字幕
        val systemLanguage = resources.configuration.locales[0].toString().lowercase()
        val sortedSubtitles = allSubtitles.sortedWith(
            subtitleComparator(videoNameWithoutExt, systemLanguage)
        )

        // 打印所有找到的字幕（调试用）
        sortedSubtitles.forEachIndexed { index, file ->
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Subtitle [$index]: ${file.name}")
        }

        // 选择优先级最高的字幕文件
        val foundSubtitle = sortedSubtitles.first()

        val subtitlePath = foundSubtitle.absolutePath
        com.fam4k007.videoplayer.utils.Logger.d(TAG, "Auto-loading best match subtitle: $subtitlePath")

        try {
            MPVLib.command("sub-add", subtitlePath, "select")
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Successfully auto-loaded subtitle: ${foundSubtitle.name}")

            // 保存字幕路径到记忆中，下次进入视频时不会重复自动加载
            preferencesManager.setExternalSubtitle(videoUri.toString(), subtitlePath)
            com.fam4k007.videoplayer.utils.Logger.d(TAG, "Saved auto-loaded subtitle path to preferences")

            // 设置标志，防止 restoreSubtitlePreferences 重复加载
            viewModel.setHasAutoLoadedSubtitle(true)

            DialogUtils.showToastShort(this, "Auto-loaded subtitle: ${foundSubtitle.name}")
        } catch (e: Exception) {
            com.fam4k007.videoplayer.utils.Logger.w(TAG, "Failed to auto-load subtitle", e)
            DialogUtils.showToastShort(this, "Subtitle loading failed: ${e.message}")
        }

        com.fam4k007.videoplayer.utils.Logger.d(TAG, "===== Auto-load subtitle end =====")
    } catch (e: Exception) {
        Log.e(TAG, "Error in autoLoadSubtitleIfExists", e)
    }
}

/**
 * 恢复视频的字幕偏好设置
 */
internal fun VideoPlayerActivity.restoreSubtitlePreferences(videoUri: android.net.Uri) {
    try {
        val uriString = videoUri.toString()
        Logger.d(TAG, "Restoring subtitle preferences for: $uriString")

        playbackEngine?.let { engine ->
            val assOverride = preferencesManager.isAssOverrideEnabled()
            if (assOverride) {
                lifecycleScope.launch {
                    delay(300)
                    engine.setAssOverride(assOverride)
                    Logger.d(TAG, "Restored ASS override: $assOverride")
                }
            }

            val savedScale = preferencesManager.getSubtitleScale(uriString)
            if (savedScale != 1.0) {
                engine.setSubtitleScale(savedScale)
                Logger.d(TAG, "Restored subtitle scale: $savedScale")
            }

            val savedPosition = preferencesManager.getSubtitlePosition(uriString)
            if (savedPosition != 100f) {
                engine.setSubtitleVerticalPosition(savedPosition)
                Logger.d(TAG, "Restored subtitle position: $savedPosition")
            }

            val savedDelay = preferencesManager.getSubtitleDelay(uriString)
            if (savedDelay != 0.0) {
                engine.setSubtitleDelay(savedDelay)
                Logger.d(TAG, "Restored subtitle delay: $savedDelay")
            }

            val savedSubtitlePath = preferencesManager.getExternalSubtitle(uriString)
            if (savedSubtitlePath != null) {
                // 如果已经自动加载过这个字幕，就跳过（防止重复加载）
                if (viewModel.hasAutoLoadedSubtitle.value) {
                    Logger.d(TAG, "Subtitle already auto-loaded this session, skipping restore")
                } else if (File(savedSubtitlePath).exists() || savedSubtitlePath.startsWith("http")) {
                    try {
                        MPVLib.command("sub-add", savedSubtitlePath, "select")
                        Logger.d(TAG, "Restored external subtitle from path: $savedSubtitlePath")
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to restore external subtitle", e)
                    }
                } else {
                    Logger.w(TAG, "Saved subtitle file not found: $savedSubtitlePath")
                }
            }

            val savedTrackId = preferencesManager.getSubtitleTrackId(uriString)
            if (savedTrackId != -1) {
                lifecycleScope.launch {
                    delay(600)
                    try {
                        engine.selectSubtitleTrack(savedTrackId)
                        Logger.d(TAG, "Restored subtitle track: $savedTrackId")
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to restore subtitle track", e)
                    }
                }
            }

            val savedTextColor = preferencesManager.getSubtitleTextColor(uriString)
            if (savedTextColor != "#FFFFFF") {
                engine.setSubtitleTextColor(savedTextColor)
                Logger.d(TAG, "Restored subtitle text color: $savedTextColor")
            }

            val savedBorderSize = preferencesManager.getSubtitleBorderSize(uriString)
            // 总是应用描边粗细，确保全局设置也能生效
            engine.setSubtitleBorderSize(savedBorderSize)
            Logger.d(TAG, "Restored subtitle border size: $savedBorderSize")

            val savedBorderColor = preferencesManager.getSubtitleBorderColor(uriString)
            if (savedBorderColor != "#000000") {
                engine.setSubtitleBorderColor(savedBorderColor)
                Logger.d(TAG, "Restored subtitle border color: $savedBorderColor")
            }

            val savedBackColor = preferencesManager.getSubtitleBackColor(uriString)
            if (savedBackColor != "#00000000") {
                engine.setSubtitleBackColor(savedBackColor)
                Logger.d(TAG, "Restored subtitle back color: $savedBackColor")
            }

            // 总是应用描边样式，即使是默认值，确保MPV状态正确
            val savedBorderStyle = preferencesManager.getSubtitleBorderStyle(uriString)
            engine.setSubtitleBorderStyle(savedBorderStyle)
            Logger.d(TAG, "Restored subtitle border style: $savedBorderStyle")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error restoring subtitle preferences", e)
        // 恢复字幕设置失败不影响播放，不提示用户
    }
}

/**
 * 从content:// URI获取真实文件路径
 */
internal fun VideoPlayerActivity.getRealPathFromUri(uri: android.net.Uri): String? {
    return when (uri.scheme) {
        "file" -> uri.path
        "content" -> {
            try {
                val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                        cursor.getString(columnIndex)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                com.fam4k007.videoplayer.utils.Logger.w(TAG, "Failed to get real path from content URI", e)
                null
            }
        }
        else -> null
    }
}

/**
 * 根据文件路径查找对应的content:// URI（从MediaStore）
 */
internal fun VideoPlayerActivity.getContentUriForFile(file: File): android.net.Uri? {
    return try {
        val projection = arrayOf(android.provider.MediaStore.Files.FileColumns._ID)
        val selection = "${android.provider.MediaStore.Files.FileColumns.DATA}=?"
        val selectionArgs = arrayOf(file.absolutePath)

        contentResolver.query(
            android.provider.MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID))
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    id
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        com.fam4k007.videoplayer.utils.Logger.w(TAG, "Failed to get content URI for file: ${file.absolutePath}", e)
        null
    }
}

// ==================== 字幕匹配工具函数（本地和 WebDAV 共用）====================

/**
 * 从文件名列表中，找出与视频名最佳匹配的字幕文件名
 * 使用与本地字幕相同的排序算法
 */
fun findBestSubtitleFileName(
    videoNameWithoutExt: String,
    availableFiles: List<String>,
    systemLanguage: String = "zh_cn"
): String? {
    val matching = availableFiles.filter { fileName ->
        val name = fileName.lowercase()
        val videoName = videoNameWithoutExt.lowercase()
        name.startsWith(videoName) && SUBTITLE_PRIORITY.contains(fileName.substringAfterLast('.', "").lowercase())
    }
    if (matching.isEmpty()) return null

    return matching.sortedWith(subtitleFileNameComparator(videoNameWithoutExt, systemLanguage)).first()
}

/**
 * File 版本的字幕比较器（本地文件使用）
 */
private fun subtitleComparator(videoNameWithoutExt: String, systemLanguage: String): Comparator<File> {
    val isSC = systemLanguage.contains("zh_cn")
    val isTC = systemLanguage.contains("zh_tw") || systemLanguage.contains("zh_hk")
    return compareBy<File>(
        { file -> SUBTITLE_PRIORITY.indexOf(file.extension.lowercase()).let { if (it == -1) 999 else it } },
        { file -> if (file.nameWithoutExtension.equals(videoNameWithoutExt, ignoreCase = true)) 0 else 1 },
        { file ->
            val after = file.nameWithoutExtension.lowercase().removePrefix(videoNameWithoutExt.lowercase())
            langPriority(after, isSC, isTC)
        },
        { file -> file.nameWithoutExtension.length },
        { file -> file.name.lowercase() }
    )
}

/**
 * 文件名 String 版本的字幕比较器（WebDAV 使用）
 */
private fun subtitleFileNameComparator(videoNameWithoutExt: String, systemLanguage: String): Comparator<String> {
    val isSC = systemLanguage.contains("zh_cn")
    val isTC = systemLanguage.contains("zh_tw") || systemLanguage.contains("zh_hk")
    return compareBy<String>(
        { name -> SUBTITLE_PRIORITY.indexOf(name.substringAfterLast('.').lowercase()).let { if (it == -1) 999 else it } },
        { name -> if (name.substringBeforeLast('.').equals(videoNameWithoutExt, ignoreCase = true)) 0 else 1 },
        { name ->
            val after = name.substringBeforeLast('.').lowercase().removePrefix(videoNameWithoutExt.lowercase())
            langPriority(after, isSC, isTC)
        },
        { name -> name.substringBeforeLast('.').length },
        { name -> name.lowercase() }
    )
}

private fun langPriority(afterVideoName: String, isSC: Boolean, isTC: Boolean): Int {
    return when {
        isSC && (afterVideoName.contains("sc") || afterVideoName.contains("chs") ||
                 afterVideoName.contains("简") || afterVideoName.contains("zh-cn")) -> 0
        isTC && (afterVideoName.contains("tc") || afterVideoName.contains("cht") ||
                 afterVideoName.contains("繁") || afterVideoName.contains("zh-tw")) -> 0
        else -> 1
    }
}

// ==================== WebDAV 同名字幕加载 ====================

/**
 * 自动加载 WebDAV 视频的同名字幕
 * 通过 WebDAV 客户端列目录，找到同名字幕 URL 后传给 MPV 加载
 */
internal suspend fun VideoPlayerActivity.autoLoadWebDavSubtitle(
    videoUri: android.net.Uri,
    parentDirPath: String,
    client: com.fam4k007.videoplayer.domain.webdav.WebDavClient
) {
    try {
        Logger.d(TAG, "===== Auto-load WebDAV subtitle start =====")
        Logger.d(TAG, "Video URI: $videoUri, parent dir: $parentDirPath")

        // 从 URL 中提取视频文件名（不含扩展名），需 URL 解码
        val videoUrl = videoUri.toString()
        val fileName = videoUrl.substringAfterLast('/').substringBefore('?')
        val rawName = fileName.substringBeforeLast('.')
        val videoNameWithoutExt = URLDecoder.decode(rawName, "UTF-8")

        Logger.d(TAG, "Video file name: $fileName, raw name: $rawName, decoded base name: $videoNameWithoutExt")

        // 获取系统语言偏好（简体系统优先 sc，繁体优先 tc）
        val systemLanguage = resources.configuration.locales[0].toString().lowercase()
        Logger.d(TAG, "System language: $systemLanguage")

        // 用 WebDAV 客户端列出目录（网络请求，需要在 IO 线程执行）
        val files = withContext(Dispatchers.IO) {
            client.listFiles(parentDirPath)
        }
        val subtitleCandidates = files.filter { !it.isDirectory }

        if (subtitleCandidates.isEmpty()) {
            Logger.d(TAG, "No files found in WebDAV directory")
            return
        }

        val availableNames = subtitleCandidates.map { it.name }
        val bestName = findBestSubtitleFileName(videoNameWithoutExt, availableNames, systemLanguage)

        if (bestName == null) {
            Logger.d(TAG, "No matching subtitle found in WebDAV directory")
            return
        }

        Logger.d(TAG, "Best matching subtitle: $bestName")

        // 通过本地代理加载字幕（与视频使用相同的代理架构）
        val bestFile = subtitleCandidates.first { it.name == bestName }
        val config = client.config
        val proxy = com.fam4k007.videoplayer.domain.webdav.WebDavStreamingProxy.getInstance()
        val subtitleStreamId = "subtitle_${System.currentTimeMillis()}_${bestFile.path.hashCode()}"
        val subtitleUrl = proxy.registerStream(
            streamId = subtitleStreamId,
            config = config,
            filePath = bestFile.path,
            fileSize = bestFile.size,
            mimeType = "text/plain"
        )

        Logger.d(TAG, "Subtitle proxy URL: $subtitleUrl")

        // 通过 MPV 加载代理 URL 字幕
        MPVLib.command("sub-add", subtitleUrl, "select")
        Logger.d(TAG, "Successfully loaded WebDAV subtitle: $bestName")

        // 保存字幕路径，避免重复加载
        preferencesManager.setExternalSubtitle(videoUri.toString(), subtitleUrl)
        viewModel.setHasAutoLoadedSubtitle(true)

        DialogUtils.showToastShort(this, "Auto-loaded subtitle: $bestName")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to auto-load WebDAV subtitle", e)
    }
}
