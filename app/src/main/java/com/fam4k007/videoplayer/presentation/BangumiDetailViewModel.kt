package com.fam4k007.videoplayer.presentation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.bilibili.auth.BiliBiliAuthManager
import com.fam4k007.videoplayer.bilibili.model.PgcEpisode
import com.fam4k007.videoplayer.bilibili.model.PgcInfoResult
import com.fam4k007.videoplayer.bilibili.repository.BangumiRepository
import com.fam4k007.videoplayer.remote.RemotePlaybackHeaders
import com.fam4k007.videoplayer.remote.RemotePlaybackLauncher
import com.fam4k007.videoplayer.remote.RemotePlaybackRequest
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 番剧详情页ViewModel
 * 管理番剧详情数据和播放地址获取
 */
class BangumiDetailViewModel(
    private val bangumiRepository: BangumiRepository,
    private val authManager: BiliBiliAuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "BangumiDetailViewModel"
    }

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow<BangumiDetailUiState>(BangumiDetailUiState.Loading)
    val uiState: StateFlow<BangumiDetailUiState> = _uiState.asStateFlow()

    private val _seasonInfo = MutableStateFlow<PgcInfoResult?>(null)
    val seasonInfo: StateFlow<PgcInfoResult?> = _seasonInfo.asStateFlow()

    private val _episodes = MutableStateFlow<List<PgcEpisode>>(emptyList())
    val episodes: StateFlow<List<PgcEpisode>> = _episodes.asStateFlow()

    // 当前季度ID
    private var currentSeasonId: Int = 0

    // ==================== 公开方法 ====================

    /**
     * 加载番剧详情
     * @param id 季度ID或集ID
     * @param isEpId 是否为集ID（ep链接需要传true）
     */
    fun loadSeasonInfo(id: Int, isEpId: Boolean = false) {
        currentSeasonId = id
        
        viewModelScope.launch {
            _uiState.value = BangumiDetailUiState.Loading
            
            bangumiRepository.getSeasonInfo(id, isEpId).fold(
                onSuccess = { result ->
                    _seasonInfo.value = result
                    _episodes.value = result.episodes ?: emptyList()
                    _uiState.value = BangumiDetailUiState.Success
                    
                    Logger.d(TAG, "Loaded season info: ${result.title}, episodes: ${_episodes.value.size}")
                },
                onFailure = { error ->
                    Logger.e(TAG, "Failed to load season info", error)
                    _uiState.value = BangumiDetailUiState.Error(error.message ?: "Failed to load anime details")
                }
            )
        }
    }

    /**
     * 播放指定集数
     * @param context 上下文
     * @param episode 集数信息
     */
    fun playEpisode(context: Context, episode: PgcEpisode) {
        viewModelScope.launch {
            Toast.makeText(context, "Fetching playback URL...", Toast.LENGTH_SHORT).show()
            
            bangumiRepository.getPlayUrl(
                avid = episode.aid,
                bvid = episode.bvid,
                cid = episode.cid,
                epId = episode.ep_id,
                seasonId = currentSeasonId
            ).fold(
                onSuccess = { playUrlResult ->
                    val videoUrl = bangumiRepository.extractVideoUrl(playUrlResult)
                    val audioUrl = bangumiRepository.extractAudioUrl(playUrlResult)
                    
                    if (videoUrl.isNullOrEmpty()) {
                        Toast.makeText(context, "Failed to get playback URL", Toast.LENGTH_SHORT).show()
                        return@fold
                    }
                    
                    // 获取实际画质信息（优先从DASH视频流中取真实画质ID，而非API响应的qn字段）
                    val actualQuality = playUrlResult.dash?.video?.firstOrNull()?.id
                        ?: playUrlResult.quality
                    val qualityName = when (actualQuality) {
                        127 -> "8K Ultra HD"
                        126 -> "Dolby Vision"
                        125 -> "HDR True Color"
                        120 -> "4K Ultra Clear"
                        116 -> "1080P 60fps"
                        112 -> "1080P High Bitrate"
                        80 -> "1080P HD"
                        64 -> "720P HD"
                        32 -> "480P Clear"
                        16 -> "360P Smooth"
                        else -> "${actualQuality}P"
                    }
                    Toast.makeText(context, "Quality: $qualityName", Toast.LENGTH_SHORT).show()
                    
                    // 启动播放器
                    val title = "${_seasonInfo.value?.title ?: ""} ${episode.title}"
                    val requestHeaders = RemotePlaybackHeaders.normalize(
                        linkedMapOf(
                            "Cookie" to authManager.getCookieString(),
                            "Referer" to "https://www.bilibili.com"
                        )
                    )
                    
                    val request = RemotePlaybackRequest(
                        url = videoUrl,
                        title = title,
                        headers = requestHeaders,
                        sourcePageUrl = "https://www.bilibili.com",
                        source = RemotePlaybackRequest.Source.BILIBILI,
                        audioUrl = audioUrl
                    )
                    RemotePlaybackLauncher.start(context, request)
                },
                onFailure = { error ->
                    Logger.e(TAG, "Failed to get play url", error)
                    Toast.makeText(context, "Failed to get playback URL: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

// ==================== UI State ====================

sealed class BangumiDetailUiState {
    data object Loading : BangumiDetailUiState()
    data object Success : BangumiDetailUiState()
    data class Error(val message: String) : BangumiDetailUiState()
}