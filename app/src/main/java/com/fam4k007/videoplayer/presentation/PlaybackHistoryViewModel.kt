package com.fam4k007.videoplayer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.database.PlaybackHistoryEntity
import com.fam4k007.videoplayer.repository.PlayerRepository
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放历史ViewModel
 * 管理播放历史列表的状态和业务逻辑
 */
class PlaybackHistoryViewModel(
    private val playerRepository: PlayerRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "PlaybackHistoryViewModel"
    }
    
    // ==================== UI State ====================
    
    data class UiState(
        val historyList: List<PlaybackHistoryEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // ==================== 初始化 ====================
    
    init {
        loadHistory()
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 加载播放历史
     */
    fun loadHistory() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val history = playerRepository.getAllHistory()
                
                _uiState.value = _uiState.value.copy(
                    historyList = history,
                    isLoading = false
                )
                
                Logger.d(TAG, "Loaded ${history.size} history items")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load history", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load history: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 删除单条历史记录
     */
    fun deleteHistory(uri: String) {
        viewModelScope.launch {
            try {
                playerRepository.deleteHistory(Uri.parse(uri))
                
                // 更新UI状态
                val updatedList = _uiState.value.historyList.filter { it.uri != uri }
                _uiState.value = _uiState.value.copy(historyList = updatedList)
                
                Logger.d(TAG, "Deleted history: $uri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete history", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 清空所有历史记录
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                playerRepository.clearAllHistory()
                
                // 更新UI状态
                _uiState.value = _uiState.value.copy(historyList = emptyList())
                
                Logger.d(TAG, "Cleared all history")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear history", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 清除错误状态
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
