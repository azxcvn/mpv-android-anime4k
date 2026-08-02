package com.fam4k007.videoplayer.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 日志查看ViewModel
 * 管理错误日志的读取和清除
 */
class LogViewerViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "LogViewerViewModel"
    }
    
    // ==================== UI State ====================
    
    data class UiState(
        val logFiles: List<LogFile> = emptyList(),
        val selectedLog: String? = null,
        val isLoading: Boolean = false,
        val message: String? = null
    )
    
    data class LogFile(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long
    ) {
        fun getFormattedDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(lastModified)
        }
        
        fun getFormattedSize(): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
                else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            }
        }
    }
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // ==================== 初始化 ====================
    
    init {
        loadLogFiles()
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 加载日志文件列表
     */
    fun loadLogFiles() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val logs = withContext(Dispatchers.IO) {
                    val logDir = File(context.getExternalFilesDir(null), "crash_logs")
                    if (!logDir.exists() || !logDir.isDirectory) {
                        return@withContext emptyList<LogFile>()
                    }
                    
                    logDir.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".txt") }
                        ?.map { file ->
                            LogFile(
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        }
                        ?.sortedByDescending { it.lastModified }
                        ?: emptyList()
                }
                
                _uiState.value = _uiState.value.copy(
                    logFiles = logs,
                    isLoading = false
                )
                
                Logger.d(TAG, "Loaded ${logs.size} log files")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load log files", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to load logs: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 读取日志文件内容
     */
    fun readLogFile(filePath: String) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    File(filePath).readText()
                }
                
                _uiState.value = _uiState.value.copy(selectedLog = content)
                Logger.d(TAG, "Read log file: $filePath")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to read log file", e)
                _uiState.value = _uiState.value.copy(
                    message = "Failed to read logs: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 清除选中的日志（返回列表）
     */
    fun clearSelectedLog() {
        _uiState.value = _uiState.value.copy(selectedLog = null)
    }
    
    /**
     * 清除所有日志
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    val logDir = File(context.getExternalFilesDir(null), "crash_logs")
                    if (!logDir.exists()) return@withContext 0
                    
                    var count = 0
                    logDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.delete()) {
                            count++
                        }
                    }
                    count
                }
                
                _uiState.value = _uiState.value.copy(
                    logFiles = emptyList(),
                    selectedLog = null,
                    message = "Cleared $deletedCount log files"
                )
                
                Logger.d(TAG, "Cleared $deletedCount log files")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear logs", e)
                _uiState.value = _uiState.value.copy(
                    message = "Failed to clear logs: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
