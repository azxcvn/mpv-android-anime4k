package com.fam4k007.videoplayer.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.utils.Logger
import com.fam4k007.videoplayer.utils.ThumbnailCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缓存管理ViewModel
 * 管理应用缓存的查看和清理
 */
class CacheManagementViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "CacheManagementViewModel"
    }
    
    // ==================== UI State ====================
    
    data class UiState(
        val cacheSize: String = "Calculating...",
        val thumbnailCacheSize: String = "Calculating...",
        val otherCacheSize: String = "Calculating...",
        val isLoading: Boolean = false,
        val message: String? = null
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // ==================== 初始化 ====================
    
    init {
        updateCacheSize()
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                withContext(Dispatchers.IO) {
                    val cacheManager = ThumbnailCacheManager.getInstance(context)
                    cacheManager.clearCache()
                    
                    // 同时清除应用缓存目录
                    context.cacheDir.deleteRecursively()
                    context.externalCacheDir?.deleteRecursively()
                }
                
                updateCacheSize()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Cache cleared"
                )
                
                Logger.d(TAG, "All cache cleared successfully")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear all cache", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to clear cache: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 清除缩略图缓存
     */
    fun clearThumbnailCache() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                withContext(Dispatchers.IO) {
                    val cacheManager = ThumbnailCacheManager.getInstance(context)
                    cacheManager.clearCache()
                }
                
                updateCacheSize()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Thumbnail cache cleared"
                )
                
                Logger.d(TAG, "Thumbnail cache cleared successfully")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear thumbnail cache", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to clear thumbnail cache: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 更新缓存大小
     */
    fun updateCacheSize() {
        viewModelScope.launch {
            try {
                val (totalSize, thumbnailSize, otherSize) = withContext(Dispatchers.IO) {
                    val thumbnailDir = context.externalCacheDir?.resolve("video_thumbnails")
                    val thumbnailCacheSize = thumbnailDir?.dirSize() ?: 0L
                    
                    val totalCacheSize = (context.cacheDir.dirSize() + 
                                         (context.externalCacheDir?.dirSize() ?: 0L))
                    val otherCacheSize = totalCacheSize - thumbnailCacheSize
                    
                    Triple(
                        formatSize(totalCacheSize),
                        formatSize(thumbnailCacheSize),
                        formatSize(otherCacheSize)
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    cacheSize = totalSize,
                    thumbnailCacheSize = thumbnailSize,
                    otherCacheSize = otherSize
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to calculate cache size", e)
                _uiState.value = _uiState.value.copy(
                    cacheSize = "Unknown",
                    thumbnailCacheSize = "Unknown",
                    otherCacheSize = "Unknown"
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
    
    // ==================== 私有方法 ====================
    
    private fun File.dirSize(): Long {
        return walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
