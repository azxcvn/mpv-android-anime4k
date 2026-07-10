package com.fam4k007.videoplayer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新检查管理器
 */
object UpdateManager {
    
    private const val TAG = "UpdateManager"
    
    // GitHub Releases API 地址
    private const val GITHUB_API_URL = "https://api.github.com/repos/azxcvn/mpv-android-anime4k/releases/latest"
    
    // 自定义下载地址
    private const val CUSTOM_DOWNLOAD_URL = "https://b23.tv/0xk3O0m"
    // 备用下载地址
    private const val SECONDARY_DOWNLOAD_URL = "https://docs.qq.com/aio/p/scektroglatideb?p=pJTx0TLuJpKfJrmfpzYPBA&nlc=1"
    
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val secondaryDownloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String
    )
    
    /**
     * 检查是否有新版本
     * @return UpdateInfo 如果有更新，null 如果没有更新或检查失败
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = getAppVersionCode(context)
            Log.d(TAG, "当前版本号: $currentVersionCode")
            Log.d(TAG, "请求地址: $GITHUB_API_URL")
            
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "响应代码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "响应内容: $response")
                val jsonObject = JSONObject(response)
                
                // 解析版本信息
                val tagName = jsonObject.optString("tag_name", "")
                val versionName = tagName.removePrefix("v") // 移除 v 前缀，如 v1.1.4 -> 1.1.4
                val body = jsonObject.optString("body", "")
                val publishedAt = jsonObject.optString("published_at", "")
                
                Log.d(TAG, "GitHub 版本标签: $tagName")
                Log.d(TAG, "解析后版本名: $versionName")
                
                // 从版本名提取版本号 - 改进逻辑
                // 优先从 body 中查找 versionCode，格式如: versionCode:14 或 [14]
                var latestVersionCode = extractVersionCodeFromBody(body)
                if (latestVersionCode <= 0) {
                    // 如果没找到，则从版本名转换
                    latestVersionCode = versionNameToCode(versionName)
                }
                
                Log.d(TAG, "最新版本: $versionName (code: $latestVersionCode)")
                Log.d(TAG, "当前版本: ${getAppVersionName(context)} (code: $currentVersionCode)")
                Log.d(TAG, "比较结果: $latestVersionCode > $currentVersionCode = ${latestVersionCode > currentVersionCode}")
                
                // 获取下载链接 - 优先使用自定义网盘地址
                var downloadUrl = CUSTOM_DOWNLOAD_URL
                Log.d(TAG, "使用自定义下载地址（夸克网盘）: $downloadUrl")
                
                // 备用：从 GitHub Assets 获取（如果需要的话可以注释掉上面两行）
                /*
                val assets = jsonObject.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    // 查找 APK 文件
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                
                // 如果没有找到 APK，使用 html_url
                if (downloadUrl.isEmpty()) {
                    downloadUrl = jsonObject.optString("html_url", "")
                }
                */
                
                // 比较版本
                if (latestVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        versionName = versionName,
                        versionCode = latestVersionCode,
                        downloadUrl = downloadUrl,
                        secondaryDownloadUrl = SECONDARY_DOWNLOAD_URL,
                        releaseNotes = body,
                        publishedAt = publishedAt
                    )
                } else {
                    Log.d(TAG, "已是最新版本")
                    return@withContext null
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.e(TAG, "仓库未找到或没有发布任何 Release")
                throw Exception("仓库未找到 Release，请先在 GitHub 发布版本")
            } else {
                Log.e(TAG, "请求失败: $responseCode")
                throw Exception("网络请求失败: HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            throw e
        }
    }
    
    /**
     * 获取当前应用版本号
     */
    private fun getAppVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionCode
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取当前应用版本名称
     */
    fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }
    
    /**
     * 版本名转版本号（如 1.1.3 -> 13）
     * 规则：主版本号*10 + 修订号（忽略次版本号）
     * 例如：1.1.2 -> 12, 1.1.3 -> 13, 2.0.1 -> 21
     * 注意：考虑到 build.gradle 中的 ABI 分包版本号计算（versionCode * 10 + 2）
     */
    private fun versionNameToCode(versionName: String): Int {
        return try {
            val parts = versionName.split(".")
            Log.d(TAG, "解析版本名: $versionName, parts: $parts")
            
            var code = 0
            if (parts.isNotEmpty()) {
                val major = parts[0].toInt()
                code += major * 10  // 主版本 * 10
                Log.d(TAG, "主版本: $major, code = $code")
            }
            // 忽略次版本号 parts[1]
            if (parts.size > 2) {
                val patch = parts[2].toInt()
                code += patch  // 直接加修订号
                Log.d(TAG, "修订号: $patch, code = $code")
            }
            
            // 考虑 ABI 分包：实际版本号 = code * 10 + 2 (arm64-v8a)
            val finalCode = code * 10 + 2
            Log.d(TAG, "基础 versionCode: $code, 最终: $finalCode")
            finalCode
        } catch (e: Exception) {
            Log.e(TAG, "版本号转换失败: ${e.message}")
            0
        }
    }
    
    /**
     * 从 Release body 中提取 versionCode
     * 支持格式: "versionCode: 14" 或 "versionCode:14" 或 "[14]"
     */
    private fun extractVersionCodeFromBody(body: String): Int {
        return try {
            // 尝试匹配 versionCode: 14 格式
            var regex = Regex("""versionCode\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
            var match = regex.find(body)
            if (match != null) {
                return match.groupValues[1].toInt()
            }
            
            // 尝试匹配 [14] 格式（在开头）
            regex = Regex("""^\[(\d+)\]""")
            match = regex.find(body.trim())
            if (match != null) {
                return match.groupValues[1].toInt()
            }
            
            0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 打开浏览器下载更新
     */
    fun openDownloadPage(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开下载页面失败", e)
        }
    }
}
