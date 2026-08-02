package com.fam4k007.videoplayer.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.domain.sniffer.VideoSelector
import com.fam4k007.videoplayer.remote.RemotePlaybackLauncher
import com.fam4k007.videoplayer.sniffer.DetectedVideo
import com.fam4k007.videoplayer.sniffer.VideoSnifferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TV浏览器 ViewModel
 * 管理WebView浏览、视频嗅探等UI状态
 */
class TVBrowserViewModel : ViewModel() {

    companion object {
        private const val TAG = "TVBrowserViewModel"
    }

    // ==================== UI State ====================

    data class BrowserState(
        val currentUrl: String = "",
        val currentTitle: String = "TV Browser",
        val isLoading: Boolean = false,
        val urlInput: String = "",
        val showUrlBar: Boolean = false,
        val urlToLoad: String = "",
        val detectedVideos: List<DetectedVideo> = emptyList()
    )

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    // ==================== 页面信息（供 WebView 回调使用） ====================

    private var currentPageUrl: String = ""
    private var currentPageTitle: String = "TV Browser"

    init {
        VideoSnifferManager.clear()
        Log.d(TAG, "Cleared previous detected videos on init")
        
        // 收集嗅探结果
        viewModelScope.launch {
            VideoSnifferManager.detectedVideos.collect { videos ->
                _state.update { it.copy(detectedVideos = videos) }
            }
        }
    }

    /**
     * 初始化入口URL
     */
    fun initUrl(url: String) {
        val showBar = url.isEmpty()
        _state.update {
            it.copy(
                urlInput = url,
                showUrlBar = showBar,
                urlToLoad = url
            )
        }
        currentPageUrl = url
    }

    // ==================== 用户操作 ====================

    fun onUrlChanged(url: String) {
        _state.update { it.copy(currentUrl = url, urlInput = url) }
    }

    fun onTitleChanged(title: String) {
        _state.update { it.copy(currentTitle = title) }
    }

    fun onLoadingChanged(loading: Boolean) {
        _state.update { it.copy(isLoading = loading) }
    }

    fun onPageUrlChanged(pageUrl: String, pageTitle: String) {
        currentPageUrl = pageUrl
        currentPageTitle = pageTitle
        VideoSnifferManager.startNewPage()
    }

    fun getCurrentPageUrl(): String = currentPageUrl
    fun getCurrentPageTitle(): String = currentPageTitle

    fun updateUrlInput(input: String) {
        _state.update { it.copy(urlInput = input) }
    }

    fun toggleUrlBar() {
        _state.update { it.copy(showUrlBar = !it.showUrlBar) }
    }

    fun clearUrlInput() {
        _state.update { it.copy(urlInput = "") }
    }

    fun navigateToUrl() {
        val input = _state.value.urlInput.trim()
        if (input.isEmpty()) return

        var url = input
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        _state.update { it.copy(urlToLoad = url, showUrlBar = false) }
    }

    fun playBestVideo(context: Context) {
        val videos = _state.value.detectedVideos
        if (videos.isEmpty()) return

        val bestVideo = VideoSelector.selectBest(videos)
        Log.d(TAG, "Playing best video: ${bestVideo.url}")

        val request = bestVideo.toRemotePlaybackRequest().copy(
            title = bestVideo.title.ifEmpty { "Online Video" }
        )
        RemotePlaybackLauncher.start(context, request)
    }
}

// ==================== MutableStateFlow 扩展 ====================

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
