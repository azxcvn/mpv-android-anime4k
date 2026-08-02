package com.fam4k007.videoplayer.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.danmaku.BiliBiliDanmakuDownloadManager
import com.fam4k007.videoplayer.ui.screens.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * B站弹幕下载 ViewModel
 * 负责管理弹幕下载的状态和业务逻辑
 */
class BiliBiliDanmakuViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = BiliBiliDanmakuDownloadManager(application)
    private val prefs = application.getSharedPreferences("bilibili_danmaku", android.content.Context.MODE_PRIVATE)

    // 保存的文件夹URI
    private val _savedFolderUri = MutableStateFlow<Uri?>(null)
    val savedFolderUri: StateFlow<Uri?> = _savedFolderUri.asStateFlow()

    // 下载进度
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    // 是否正在下载
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 成功消息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // 下载模式：true=整季下载，false=单集下载
    private val _downloadWholeSeason = MutableStateFlow(true)
    val downloadWholeSeason: StateFlow<Boolean> = _downloadWholeSeason.asStateFlow()

    companion object {
        private const val TAG = "BiliBiliDanmakuVM"
    }

    init {
        loadSavedFolderUri()
        loadDownloadMode()
    }

    /**
     * 加载已保存的下载模式
     */
    private fun loadDownloadMode() {
        _downloadWholeSeason.value = prefs.getBoolean("download_whole_season", true)
    }

    /**
     * 设置下载模式并持久化
     */
    fun setDownloadMode(wholeSeason: Boolean) {
        _downloadWholeSeason.value = wholeSeason
        prefs.edit().putBoolean("download_whole_season", wholeSeason).apply()
    }

    /**
     * 加载已保存的文件夹URI
     */
    private fun loadSavedFolderUri() {
        val savedUriString = prefs.getString("save_directory_uri", null)
        _savedFolderUri.value = savedUriString?.let { Uri.parse(it) }
        Log.d(TAG, "加载已保存的文件夹URI: $savedUriString")
    }

    /**
     * 设置保存文件夹
     */
    fun setFolderUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                // 获取持久化权限
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)

                // 保存到SharedPreferences
                prefs.edit()
                    .putString("save_directory_uri", uri.toString())
                    .apply()

                _savedFolderUri.value = uri
                _successMessage.value = "Folder setting saved"
                Log.d(TAG, "文件夹URI已保存: $uri")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save settings: ${e.message}"
                Log.e(TAG, "保存文件夹URI失败", e)
            }
        }
    }

    /**
     * 开始下载弹幕
     */
    fun startDownload(url: String, downloadWholeSeason: Boolean) {
        if (!downloadManager.isValidBilibiliUrl(url)) {
            _errorMessage.value = "Please enter a valid Bilibili video/bangumi link"
            return
        }

        val folderUri = _savedFolderUri.value
        if (folderUri == null) {
            _errorMessage.value = "Please set the save folder first"
            return
        }

        _isDownloading.value = true
        _downloadProgress.value = DownloadProgress()
        _errorMessage.value = null
        _successMessage.value = null

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    downloadManager.downloadDanmaku(url, folderUri, downloadWholeSeason) { current, total, epTitle, success, fail ->
                        // 更新下载进度
                        _downloadProgress.value = DownloadProgress(
                            current = current,
                            total = total,
                            currentTitle = epTitle,
                            successCount = success,
                            failedCount = fail
                        )
                    }
                }

                _isDownloading.value = false

                when (result) {
                    is BiliBiliDanmakuDownloadManager.DownloadResult.Success -> {
                        _successMessage.value = "Download successful"
                        Log.d(TAG, "弹幕下载成功")
                    }
                    is BiliBiliDanmakuDownloadManager.DownloadResult.Error -> {
                        _errorMessage.value = "Download failed: ${result.message}"
                        Log.e(TAG, "弹幕下载失败: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _isDownloading.value = false
                _errorMessage.value = "Download failed: ${e.message}"
                Log.e(TAG, "下载过程出错", e)
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
