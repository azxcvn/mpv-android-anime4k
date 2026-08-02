package com.fam4k007.videoplayer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.repository.MediaInfoRepository
import com.fam4k007.videoplayer.utils.MediaInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 媒体信息ViewModel
 * 管理媒体信息页面的状态和业务逻辑
 */
class MediaInfoViewModel(
    private val mediaInfoRepository: MediaInfoRepository
) : ViewModel() {
    
    // UI状态
    private val _uiState = MutableStateFlow<MediaInfoUiState>(MediaInfoUiState.Loading)
    val uiState: StateFlow<MediaInfoUiState> = _uiState.asStateFlow()
    
    /**
     * 加载媒体信息
     */
    fun loadMediaInfo(videoUri: String, videoName: String) {
        if (videoUri.isEmpty()) {
            _uiState.value = MediaInfoUiState.Error("Invalid video address")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = MediaInfoUiState.Loading
            
            try {
                val uri = Uri.parse(videoUri)
                
                // 并行加载详细信息和文本输出
                val mediaInfoResult = mediaInfoRepository.getMediaInfo(uri, videoName)
                val textOutputResult = mediaInfoRepository.generateTextOutput(uri, videoName)
                
                mediaInfoResult.onSuccess { mediaInfo ->
                    textOutputResult.onSuccess { textOutput ->
                        _uiState.value = MediaInfoUiState.Success(
                            mediaInfo = mediaInfo,
                            fullTextContent = textOutput
                        )
                    }.onFailure { e ->
                        // 即使文本输出失败，也显示媒体信息
                        _uiState.value = MediaInfoUiState.Success(
                            mediaInfo = mediaInfo,
                            fullTextContent = null
                        )
                    }
                }.onFailure { e ->
                    _uiState.value = MediaInfoUiState.Error(e.message ?: "Failed to load media info")
                }
            } catch (e: Exception) {
                _uiState.value = MediaInfoUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

/**
 * 媒体信息UI状态
 */
sealed class MediaInfoUiState {
    /**
     * 加载中
     */
    data object Loading : MediaInfoUiState()
    
    /**
     * 加载成功
     */
    data class Success(
        val mediaInfo: MediaInfoHelper.MediaInfoData,
        val fullTextContent: String?
    ) : MediaInfoUiState()
    
    /**
     * 加载失败
     */
    data class Error(val message: String) : MediaInfoUiState()
}
