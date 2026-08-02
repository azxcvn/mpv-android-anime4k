package com.fam4k007.videoplayer.presentation

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fam4k007.videoplayer.utils.DeviceInfoDetector
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import `is`.xyz.mpv.Utils

/**
 * 设备信息页 ViewModel
 * 负责收集并管理设备硬件信息状态，与 UI 层职责分离。
 */
class DeviceInfoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DeviceInfoViewModel"
    }

    // ==================== UI State ====================

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    // ==================== 初始化 ====================

    init {
        loadDeviceInfo()
    }

    /**
     * 加载所有设备信息
     */
    private fun loadDeviceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val context = getApplication<Application>()
                val deviceCodecInfo = DeviceInfoDetector.getDeviceCodecInfo(context)
                val basicInfo = collectBasicDeviceInfo(context)
                val isTvDevice = isTvDevice(context)

                _uiState.value = DeviceInfoUiState(
                    isLoading = false,
                    basicInfo = basicInfo,
                    deviceCodecInfo = deviceCodecInfo,
                    isTvDevice = isTvDevice,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "加载设备信息失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load device info: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * 重新加载
     */
    fun refresh() {
        // 清除缓存
        loadDeviceInfo()
    }

    // ==================== 数据收集 ====================

    /**
     * 基础设备信息
     */
    data class BasicDeviceInfo(
        val appVersion: String,
        val androidVersion: String,
        val sdkLevel: Int,
        val deviceBrand: String,
        val deviceManufacturer: String,
        val deviceModel: String,
        val deviceName: String,
        val mpvVersion: String = "N/A",
        val ffmpegVersion: String = "N/A",
        val libplaceboVersion: String = "N/A",
    )

    private fun collectBasicDeviceInfo(context: Application): BasicDeviceInfo {
        var appVersion = "N/A"
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = pkgInfo.versionName ?: "N/A"
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.w(TAG, "获取应用版本失败", e)
        }

        // 获取 mpv/ffmpeg/libplacebo 版本
        val versions = Utils.VERSIONS

        return BasicDeviceInfo(
            appVersion = appVersion,
            androidVersion = Build.VERSION.RELEASE,
            sdkLevel = Build.VERSION.SDK_INT,
            deviceBrand = Build.BRAND,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceName = Build.DEVICE,
            mpvVersion = versions.mpv,
            ffmpegVersion = versions.ffmpeg,
            libplaceboVersion = versions.libPlacebo,
        )
    }

    private fun isTvDevice(context: Application): Boolean {
        val uiModeManager = context.getSystemService(Application.UI_MODE_SERVICE)
            as? android.app.UiModeManager ?: return false
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * 设备信息 UI 状态
 */
data class DeviceInfoUiState(
    val isLoading: Boolean = false,
    val basicInfo: DeviceInfoViewModel.BasicDeviceInfo? = null,
    val deviceCodecInfo: DeviceInfoDetector.DeviceCodecInfo? = null,
    val isTvDevice: Boolean = false,
    val errorMessage: String? = null,
)
