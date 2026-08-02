package com.fam4k007.videoplayer.domain.player

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fam4k007.videoplayer.PlaybackHistoryManager
import com.fam4k007.videoplayer.domain.danmaku.DanmakuManager
import com.fam4k007.videoplayer.domain.player.PlaybackEngine
import com.fam4k007.videoplayer.preferences.PreferencesManager
import com.fam4k007.videoplayer.domain.subtitle.SubtitleManager
import com.fam4k007.videoplayer.utils.DialogUtils
import com.fam4k007.videoplayer.manager.compose.ComposeOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

/**
 * 文件选择器管理器
 * 负责管理字幕和弹幕文件的导入
 */
class FilePickerManager(
    private val activityRef: WeakReference<AppCompatActivity>,
    private val subtitleManager: SubtitleManager,
    private val danmakuManager: DanmakuManager,
    private val historyManager: PlaybackHistoryManager,
    private val playbackEngineRef: WeakReference<PlaybackEngine>,
    private val preferencesManager: PreferencesManager,
    private val subtitleSystemPickerLauncher: ActivityResultLauncher<Array<String>>? = null,
    private val danmakuSystemPickerLauncher: ActivityResultLauncher<Array<String>>? = null
) {
    companion object {
        private const val TAG = "FilePickerManager"
        
        /**
         * Android 10 (API 29) 及以下使用系统文件选择器（SAF），
         * 因为在这些版本上自定义 Compose 文件选择器无法正确识别外挂字幕/弹幕文件。
         * Android 11 (API 30) 及以上使用内置 Compose 文件选择器。
         */
        val isAndroid10OrBelow: Boolean
            get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
    }

    // 移除系统字幕和弹幕文件选择器，全面改用Compose UI选择器
    // private var subtitlePickerLauncher: ActivityResultLauncher<Array<String>>? = null
    // private var danmakuPickerLauncher: ActivityResultLauncher<Array<String>>? = null
    
    private var wasPlayingBeforeSubtitlePicker = false
    private var wasPlayingBeforeDanmakuPicker = false
    
    private var currentVideoUri: Uri? = null
    
    // Compose overlay管理器（用于显示自定义文件选择器）
    private var composeOverlayManager: ComposeOverlayManager? = null

    /**
     * 初始化文件选择器
     */
    fun initialize() {
        // 字幕和弹幕文件选择器全部改用Compose UI，不再注册系统选择器
        Log.d(TAG, "File pickers initialized (using Compose UI)")
    }
    
    /**
     * 设置Compose overlay管理器
     */
    fun setComposeOverlayManager(manager: ComposeOverlayManager) {
        composeOverlayManager = manager
    }

    /**
     * 设置当前播放的视频 URI（用于历史记录更新）
     */
    fun setCurrentVideoUri(uri: Uri?) {
        currentVideoUri = uri
    }

    /**
     * 导入字幕文件
     * - Android 10 及以下：使用系统 SAF 文件选择器
     * - Android 11 及以上：使用内置 Compose 文件选择器
     */
    fun importSubtitle(isPlaying: Boolean) {
        if (isAndroid10OrBelow) {
            // Android 10 及以下：使用系统文件选择器
            val launcher = subtitleSystemPickerLauncher
            if (launcher == null) {
                Log.e(TAG, "importSubtitle: system picker launcher is null on Android 10-")
                return
            }
            val mimeTypes = arrayOf(
                "text/*",           // .srt, .ass, .ssa, .vtt, .sbv, .json
                "application/*",    // 某些设备上的 .ass/.ssa
                "*/*"               // 兜底
            )
            try {
                launcher.launch(mimeTypes)
                Log.d(TAG, "importSubtitle: system file picker launched (Android 10-)")
            } catch (e: Exception) {
                Log.e(TAG, "importSubtitle: failed to launch system picker", e)
                activityRef.get()?.let { DialogUtils.showToastShort(it, "无法打开文件选择器") }
            }
        } else {
            // Android 11 及以上：使用内置 Compose 文件选择器
            val lastPath = preferencesManager.getLastSubtitlePickerPath()
            composeOverlayManager?.showSubtitleFilePicker(
                initialPath = lastPath,
                onFileSelected = { filePath ->
                    val parentPath = File(filePath).parent
                    if (parentPath != null) {
                        preferencesManager.setLastSubtitlePickerPath(parentPath)
                    }
                    handleSubtitleFileSelected(filePath)
                }
            )
        }
    }

    /**
     * 导入弹幕文件
     * - Android 10 及以下：使用系统 SAF 文件选择器
     * - Android 11 及以上：使用内置 Compose 文件选择器
     */
    fun importDanmaku(isPlaying: Boolean) {
        if (isAndroid10OrBelow) {
            // Android 10 及以下：使用系统文件选择器
            val launcher = danmakuSystemPickerLauncher
            if (launcher == null) {
                Log.e(TAG, "importDanmaku: system picker launcher is null on Android 10-")
                return
            }
            val mimeTypes = arrayOf(
                "text/xml",         // .xml
                "application/xml",  // .xml (部分设备)
                "text/*",
                "*/*"               // 兜底
            )
            try {
                launcher.launch(mimeTypes)
                Log.d(TAG, "importDanmaku: system file picker launched (Android 10-)")
            } catch (e: Exception) {
                Log.e(TAG, "importDanmaku: failed to launch system picker", e)
                activityRef.get()?.let { DialogUtils.showToastShort(it, "无法打开文件选择器") }
            }
        } else {
            // Android 11 及以上：使用内置 Compose 文件选择器
            val lastPath = preferencesManager.getLastDanmakuPickerPath()
            composeOverlayManager?.showDanmakuFilePicker(
                initialPath = lastPath,
                onFileSelected = { filePath ->
                    val parentPath = File(filePath).parent
                    if (parentPath != null) {
                        preferencesManager.setLastDanmakuPickerPath(parentPath)
                    }
                    handleDanmakuFileSelected(filePath)
                }
            )
        }
    }

    /**
     * 处理选中的字幕文件（新方法，接收文件路径）
     */
    private fun handleSubtitleFileSelected(filePath: String) {
        val activity = activityRef.get() ?: return
        
        Log.d(TAG, "Subtitle file selected: $filePath")
        
        // 使用协程在后台线程处理字幕导入
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 直接加载字幕文件
                val success = subtitleManager.addExternalSubtitleFromPath(filePath)
                
                if (success) {
                    DialogUtils.showToastShort(activity, "字幕导入成功")
                    
                    // 保存外挂字幕路径到历史记录
                    currentVideoUri?.let { videoUri ->
                        preferencesManager.setExternalSubtitle(videoUri.toString(), filePath)
                        Log.d(TAG, "Saved external subtitle path: $filePath")
                    }
                } else {
                    DialogUtils.showToastLong(activity, "字幕导入失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import subtitle", e)
                DialogUtils.showToastLong(activity, "字幕导入失败: ${e.message}")
            }
        }
    }
    
    /**
     * 处理选中的字幕文件（旧方法，接收URI，保留兼容性）
     */
    @Deprecated("使用handleSubtitleFileSelected(filePath)替代")
    private fun handleSubtitleSelected(uri: Uri?) {
        val activity = activityRef.get() ?: return
        
        if (uri != null) {
            Log.d(TAG, "Subtitle file selected: $uri")
            val success = subtitleManager.addExternalSubtitle(activity, uri)
            if (success) {
                DialogUtils.showToastShort(activity, "字幕导入成功")
                
                // 保存外挂字幕路径
                currentVideoUri?.let { videoUri ->
                    val subtitlePath = subtitleManager.getLastAddedSubtitlePath()
                    if (subtitlePath != null) {
                        preferencesManager.setExternalSubtitle(videoUri.toString(), subtitlePath)
                        Log.d(TAG, "Saved external subtitle path: $subtitlePath")
                    }
                }
            } else {
                DialogUtils.showToastLong(activity, "字幕导入失败")
            }
        } else {
            Log.d(TAG, "Subtitle picker cancelled")
        }
        
        // 恢复播放状态
        if (wasPlayingBeforeSubtitlePicker) {
            playbackEngineRef.get()?.play()
        }
    }

    /**
     * 处理选中的弹幕文件（新方法，接收文件路径）
     */
    private fun handleDanmakuFileSelected(filePath: String) {
        val activity = activityRef.get() ?: return
        val playbackEngine = playbackEngineRef.get() ?: return
        
        Log.d(TAG, "Danmaku file selected: $filePath")
        
        // 使用协程在后台线程处理弹幕导入
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 直接加载弹幕文件
                withContext(Dispatchers.Main) {
                    DialogUtils.showToastShort(activity, "弹幕导入成功")
                    
                    // 加载弹幕文件
                    val loaded = danmakuManager.loadDanmakuFile(filePath, autoShow = true)
                    if (loaded) {
                        // 同步弹幕到当前播放位置
                        val currentPosition = (playbackEngine.currentPosition * 1000).toLong()
                        danmakuManager.seekTo(currentPosition)
                        
                        Log.d(TAG, "Danmaku loaded and synced to position: $currentPosition")
                    }
                    
                    // 更新历史记录
                    currentVideoUri?.let { videoUri ->
                        historyManager.updateDanmu(
                            uri = videoUri,
                            danmuPath = filePath,
                            danmuVisible = danmakuManager.isVisible(),
                            danmuOffsetTime = 0L
                        )
                        Log.d(TAG, "Danmu info updated in history")
                    }
                    
                    // 不再恢复播放状态，保持用户原有的播放状态
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import danmaku", e)
                withContext(Dispatchers.Main) {
                    DialogUtils.showToastLong(activity, "弹幕导入失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 处理选中的弹幕文件（旧方法，接收URI，已废弃但保留兼容）
     */
    @Deprecated("使用handleDanmakuFileSelected(filePath)替代")
    private fun handleDanmakuSelected(uri: Uri?) {
        val activity = activityRef.get() ?: return
        val playbackEngine = playbackEngineRef.get() ?: return
        
        if (uri != null) {
            Log.d(TAG, "Danmaku file selected: $uri")
            
            // 使用协程在后台线程处理弹幕导入，避免卡死主线程
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 使用 DanmakuManager 导入弹幕文件（IO操作）
                    val danmakuPath = danmakuManager.importDanmakuFile(activity, uri)
                    
                    // 切回主线程处理UI和播放器操作
                    withContext(Dispatchers.Main) {
                        if (danmakuPath != null) {
                            DialogUtils.showToastShort(activity, "弹幕导入成功")
                            
                            // 加载弹幕文件（autoShow=true 会设置track为选中状态）
                            val loaded = danmakuManager.loadDanmakuFile(danmakuPath, autoShow = true)
                            if (loaded) {
                                // 同步弹幕到当前播放位置
                                val currentPosition = (playbackEngine.currentPosition * 1000).toLong()
                                danmakuManager.seekTo(currentPosition)
                                
                                Log.d(TAG, "Danmaku loaded and synced to position: $currentPosition")
                                
                                // 按照DanDanPlay逻辑：弹幕的播放控制由onPlaybackStateChanged统一管理
                                // 不需要在这里手动调用start或resume
                            }
                            
                            // 更新历史记录中的弹幕信息（使用实际的可见性状态）
                            currentVideoUri?.let { videoUri ->
                                historyManager.updateDanmu(
                                    uri = videoUri,
                                    danmuPath = danmakuPath,
                                    danmuVisible = danmakuManager.isVisible(),
                                    danmuOffsetTime = 0L
                                )
                                Log.d(TAG, "Danmu info updated in history, visible=${danmakuManager.isVisible()}")
                            }
                        } else {
                            DialogUtils.showToastLong(activity, "弹幕导入失败")
                        }
                        
                        // 恢复播放状态（必须在主线程）
                        if (wasPlayingBeforeDanmakuPicker) {
                            playbackEngineRef.get()?.play()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import danmaku", e)
                    withContext(Dispatchers.Main) {
                        DialogUtils.showToastLong(activity, "弹幕导入失败: ${e.message}")
                        
                        // 发生异常也要恢复播放状态
                        if (wasPlayingBeforeDanmakuPicker) {
                            playbackEngineRef.get()?.play()
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Danmaku picker cancelled")
            
            // 取消选择也要恢复播放状态
            if (wasPlayingBeforeDanmakuPicker) {
                playbackEngineRef.get()?.play()
            }
        }
    }

    /**
     * 处理系统文件选择器返回的字幕 URI（Android 10 及以下使用）
     * 由 VideoPlayerActivity 中的 ActivityResultLauncher 回调调用
     */
    fun handleSubtitleFromSystemPicker(uri: Uri?) {
        val activity = activityRef.get() ?: return

        if (uri != null) {
            Log.d(TAG, "System subtitle picker returned: $uri")
            val success = subtitleManager.addExternalSubtitle(activity, uri)
            if (success) {
                DialogUtils.showToastShort(activity, "字幕导入成功")

                // 保存外挂字幕路径到历史记录
                currentVideoUri?.let { videoUri ->
                    val subtitlePath = subtitleManager.getLastAddedSubtitlePath()
                    if (subtitlePath != null) {
                        preferencesManager.setExternalSubtitle(videoUri.toString(), subtitlePath)
                        Log.d(TAG, "Saved external subtitle path: $subtitlePath")
                    }
                }
            } else {
                DialogUtils.showToastLong(activity, "字幕导入失败")
            }
        } else {
            Log.d(TAG, "System subtitle picker cancelled")
        }
    }

    /**
     * 处理系统文件选择器返回的弹幕 URI（Android 10 及以下使用）
     * 由 VideoPlayerActivity 中的 ActivityResultLauncher 回调调用
     */
    fun handleDanmakuFromSystemPicker(uri: Uri?) {
        val activity = activityRef.get() ?: return
        val playbackEngine = playbackEngineRef.get() ?: return

        if (uri != null) {
            Log.d(TAG, "System danmaku picker returned: $uri")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val danmakuPath = danmakuManager.importDanmakuFile(activity, uri)

                    withContext(Dispatchers.Main) {
                        if (danmakuPath != null) {
                            DialogUtils.showToastShort(activity, "弹幕导入成功")
                            val currentPosition = (playbackEngine.currentPosition * 1000).toLong()
                            danmakuManager.seekTo(currentPosition)
                            Log.d(TAG, "Danmaku loaded and synced to position: $currentPosition")

                            currentVideoUri?.let { videoUri ->
                                historyManager.updateDanmu(
                                    uri = videoUri,
                                    danmuPath = danmakuPath,
                                    danmuVisible = danmakuManager.isVisible(),
                                    danmuOffsetTime = 0L
                                )
                                Log.d(TAG, "Danmu info updated in history")
                            }
                        } else {
                            DialogUtils.showToastLong(activity, "弹幕导入失败")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import danmaku from system picker", e)
                    withContext(Dispatchers.Main) {
                        DialogUtils.showToastLong(activity, "弹幕导入失败: ${e.message}")
                    }
                }
            }
        } else {
            Log.d(TAG, "System danmaku picker cancelled")
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        // 已移除系统文件选择器，不再需要清理 subtitlePickerLauncher 和 danmakuPickerLauncher
        currentVideoUri = null
        composeOverlayManager = null
        Log.d(TAG, "FilePickerManager cleaned up")
    }
}
