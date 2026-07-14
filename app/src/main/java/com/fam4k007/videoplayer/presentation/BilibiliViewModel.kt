package com.fam4k007.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.bilibili.model.BangumiDetail
import com.fam4k007.videoplayer.bilibili.model.BangumiItem
import com.fam4k007.videoplayer.bilibili.model.LoginResult
import com.fam4k007.videoplayer.bilibili.model.QRCodeInfo
import com.fam4k007.videoplayer.bilibili.model.UserInfo
import com.fam4k007.videoplayer.dandanplay.Anime
import com.fam4k007.videoplayer.repository.BilibiliRepository
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * B站功能ViewModel
 * 管理B站登录、番剧浏览、弹幕下载等UI状态
 */
class BilibiliViewModel(
    private val bilibiliRepository: BilibiliRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "BilibiliViewModel"
        private const val QR_CODE_POLL_INTERVAL = 2000L // 2秒轮询一次
    }
    
    // ==================== UI State ====================
    
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.NotLoggedIn)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()
    
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()
    
    private val _bangumiState = MutableStateFlow<BangumiUiState>(BangumiUiState.Loading)
    val bangumiState: StateFlow<BangumiUiState> = _bangumiState.asStateFlow()
    
    private val _bangumiList = MutableStateFlow<List<BangumiItem>>(emptyList())
    val bangumiList: StateFlow<List<BangumiItem>> = _bangumiList.asStateFlow()
    
    private val _bangumiDetail = MutableStateFlow<BangumiDetail?>(null)
    val bangumiDetail: StateFlow<BangumiDetail?> = _bangumiDetail.asStateFlow()
    
    private val _danmakuSearchState = MutableStateFlow<DanmakuSearchState>(DanmakuSearchState.Idle)
    val danmakuSearchState: StateFlow<DanmakuSearchState> = _danmakuSearchState.asStateFlow()
    
    private var qrCodePollingJob: Job? = null
    
    // ==================== 登录管理 ====================
    
    /**
     * 检查登录状态
     */
    fun checkLoginStatus() {
        viewModelScope.launch {
            try {
                val isLoggedIn = bilibiliRepository.isLoggedIn()
                if (isLoggedIn) {
                    val userInfo = bilibiliRepository.getUserInfo()
                    if (userInfo != null) {
                        _userInfo.value = userInfo
                        _loginState.value = LoginUiState.LoggedIn(userInfo)
                    } else {
                        _loginState.value = LoginUiState.NotLoggedIn
                    }
                } else {
                    _loginState.value = LoginUiState.NotLoggedIn
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check login status", e)
                _loginState.value = LoginUiState.NotLoggedIn
            }
        }
    }
    
    /**
     * 生成二维码登录
     */
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                _loginState.value = LoginUiState.GeneratingQRCode
                val result = bilibiliRepository.generateQRCode()
                
                result.onSuccess { qrCodeInfo ->
                    _loginState.value = LoginUiState.QRCodeGenerated(qrCodeInfo)
                    // 开始轮询二维码状态
                    startQRCodePolling(qrCodeInfo.qrcodeKey)
                }.onFailure { error ->
                    Logger.e(TAG, "Failed to generate QR code", error)
                    _loginState.value = LoginUiState.Error(error.message ?: "Failed to generate QR code")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception while generating QR code", e)
                _loginState.value = LoginUiState.Error(e.message ?: "QR code generation exception")
            }
        }
    }
    
    /**
     * 开始轮询二维码状态
     */
    private fun startQRCodePolling(qrcodeKey: String) {
        qrCodePollingJob?.cancel()
        qrCodePollingJob = viewModelScope.launch {
            while (true) {
                delay(QR_CODE_POLL_INTERVAL)
                
                try {
                    val result = bilibiliRepository.pollQRCodeStatus(qrcodeKey)
                    when (result) {
                        LoginResult.Success -> {
                            // 登录成功，重新获取用户信息
                            val userInfo = bilibiliRepository.getUserInfo()
                            if (userInfo != null) {
                                _userInfo.value = userInfo
                                _loginState.value = LoginUiState.LoggedIn(userInfo)
                                Logger.d(TAG, "Login successful: ${userInfo.uname}")
                            } else {
                                _loginState.value = LoginUiState.Error("Login successful but unable to get user info")
                            }
                            break // 停止轮询
                        }
                        LoginResult.WaitingScan -> {
                            Logger.d(TAG, "Waiting for QR code scan...")
                        }
                        LoginResult.WaitingConfirm -> {
                            _loginState.value = LoginUiState.QRCodeScanned
                            Logger.d(TAG, "QR code scanned, waiting for confirmation...")
                        }
                        LoginResult.Expired -> {
                            _loginState.value = LoginUiState.Error("QR code expired, please regenerate")
                            Logger.w(TAG, "QR code expired")
                            break
                        }
                        is LoginResult.Failed -> {
                            _loginState.value = LoginUiState.Error(result.message)
                            Logger.e(TAG, "Login failed: ${result.message}")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error polling QR code status", e)
                    _loginState.value = LoginUiState.Error(e.message ?: "Failed to poll login status")
                    break
                }
            }
        }
    }
    
    /**
     * 停止二维码轮询
     */
    fun stopQRCodePolling() {
        qrCodePollingJob?.cancel()
        qrCodePollingJob = null
    }
    
    /**
     * 登出
     */
    fun logout() {
        viewModelScope.launch {
            try {
                bilibiliRepository.logout()
                _userInfo.value = null
                _loginState.value = LoginUiState.NotLoggedIn
                Logger.d(TAG, "Logged out successfully")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to logout", e)
            }
        }
    }
    
    // ==================== 番剧管理 ====================
    
    /**
     * 加载番剧列表
     */
    fun loadBangumiList() {
        viewModelScope.launch {
            try {
                _bangumiState.value = BangumiUiState.Loading
                val bangumiList = bilibiliRepository.getBangumiList()
                _bangumiList.value = bangumiList
                _bangumiState.value = if (bangumiList.isEmpty()) {
                    BangumiUiState.Empty
                } else {
                    BangumiUiState.Success
                }
                Logger.d(TAG, "Loaded ${bangumiList.size} bangumi items")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load bangumi list", e)
                _bangumiState.value = BangumiUiState.Error(e.message ?: "Failed to load anime list")
            }
        }
    }
    
    /**
     * 加载番剧详情
     */
    fun loadBangumiDetail(seasonId: String) {
        viewModelScope.launch {
            try {
                _bangumiState.value = BangumiUiState.Loading
                val detail = bilibiliRepository.getBangumiDetail(seasonId)
                _bangumiDetail.value = detail
                _bangumiState.value = BangumiUiState.Success
                Logger.d(TAG, "Loaded bangumi detail: ${detail.title}")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load bangumi detail", e)
                _bangumiState.value = BangumiUiState.Error(e.message ?: "Failed to load anime details")
            }
        }
    }
    
    /**
     * 搜索番剧
     */
    fun searchBangumi(keyword: String) {
        viewModelScope.launch {
            try {
                _bangumiState.value = BangumiUiState.Loading
                val searchResults = bilibiliRepository.searchBangumi(keyword)
                _bangumiList.value = searchResults
                _bangumiState.value = if (searchResults.isEmpty()) {
                    BangumiUiState.Empty
                } else {
                    BangumiUiState.Success
                }
                Logger.d(TAG, "Search results for '$keyword': ${searchResults.size} items")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to search bangumi", e)
                _bangumiState.value = BangumiUiState.Error(e.message ?: "Search failed")
            }
        }
    }
    
    // ==================== 弹幕搜索 ====================
    
    /**
     * 搜索弹幕（通过DanDanPlay）
     */
    fun searchDanmaku(fileName: String) {
        viewModelScope.launch {
            try {
                _danmakuSearchState.value = DanmakuSearchState.Loading
                val searchResults = bilibiliRepository.searchAnime(fileName)
                _danmakuSearchState.value = if (searchResults.isEmpty()) {
                    DanmakuSearchState.Empty
                } else {
                    DanmakuSearchState.Success(searchResults)
                }
                Logger.d(TAG, "Danmaku search results: ${searchResults.size} items")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to search danmaku", e)
                _danmakuSearchState.value = DanmakuSearchState.Error(e.message ?: "Failed to search danmaku")
            }
        }
    }
    
    /**
     * 下载弹幕
     */
    fun downloadDanmaku(episodeId: String, savePath: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                bilibiliRepository.downloadDanmaku(episodeId, savePath)
                Logger.d(TAG, "Downloaded danmaku to: $savePath")
                onSuccess()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to download danmaku", e)
            }
        }
    }
    
    /**
     * 清空弹幕搜索结果
     */
    fun clearDanmakuSearch() {
        _danmakuSearchState.value = DanmakuSearchState.Idle
    }
    
    // ==================== 生命周期管理 ====================
    
    override fun onCleared() {
        super.onCleared()
        stopQRCodePolling()
    }
}

/**
 * 登录UI状态
 */
sealed class LoginUiState {
    object NotLoggedIn : LoginUiState()
    object GeneratingQRCode : LoginUiState()
    data class QRCodeGenerated(val qrCodeInfo: QRCodeInfo) : LoginUiState()
    object QRCodeScanned : LoginUiState()
    data class LoggedIn(val userInfo: UserInfo) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/**
 * 番剧UI状态
 */
sealed class BangumiUiState {
    object Loading : BangumiUiState()
    object Success : BangumiUiState()
    object Empty : BangumiUiState()
    data class Error(val message: String) : BangumiUiState()
}

/**
 * 弹幕搜索状态
 */
sealed class DanmakuSearchState {
    object Idle : DanmakuSearchState()
    object Loading : DanmakuSearchState()
    data class Success(val results: List<Anime>) : DanmakuSearchState()
    object Empty : DanmakuSearchState()
    data class Error(val message: String) : DanmakuSearchState()
}
