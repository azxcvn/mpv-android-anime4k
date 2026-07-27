package com.fam4k007.videoplayer.bilibili.auth

import android.content.Context
import android.content.SharedPreferences
import com.fam4k007.videoplayer.bilibili.model.*
import com.fam4k007.videoplayer.utils.SecureStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/**
 * B站认证管理器 - 处理登录、Cookie管理
 * 
 * 安全性说明：
 * 1. 使用 EncryptedSharedPreferences 加密存储 Cookie 和用户信息
 * 2. 数据存储在 Android KeyStore，硬件级别保护
 * 3. 加密算法：AES256-GCM
 * 4. 即使设备被 Root，数据也难以被读取
 */
class BiliBiliAuthManager(private val context: Context) {
    
    // 使用加密的 SharedPreferences
    private val prefs: SharedPreferences = SecureStorage.getEncryptedPreferences(context)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .cookieJar(BiliCookieJar(prefs))
        .followRedirects(true)  // 允许跟随重定向以获取Cookie
        .build()
    
    companion object {
        private const val BASE_URL = "https://api.bilibili.com"
        private const val PASSPORT_URL = "https://passport.bilibili.com"
        
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER_INFO = "user_info"
        
        @Volatile
        private var instance: BiliBiliAuthManager? = null
        
        fun getInstance(context: Context): BiliBiliAuthManager {
            return instance ?: synchronized(this) {
                instance ?: BiliBiliAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 生成登录二维码
     */
    suspend fun generateQRCode(): Result<QRCodeInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$PASSPORT_URL/x/passport-login/web/qrcode/generate")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(Exception("请求失败: ${response.code}"))
            }
            
            val apiResponse = gson.fromJson(body, object : com.google.gson.reflect.TypeToken<BiliApiResponse<QRCodeResponse>>() {}.type) as BiliApiResponse<QRCodeResponse>
            
            if (apiResponse.code != 0 || apiResponse.data == null) {
                return@withContext Result.failure(Exception(apiResponse.message ?: "生成二维码失败"))
            }
            
            val qrData = apiResponse.data
            Result.success(QRCodeInfo(qrData.url, qrData.qrcodeKey))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 轮询二维码登录状态
     */
    suspend fun pollQRCodeStatus(qrcodeKey: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val url = "$PASSPORT_URL/x/passport-login/web/qrcode/poll".toHttpUrl().newBuilder()
                .addQueryParameter("qrcode_key", qrcodeKey)
                .build()
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Poll response code: ${response.code}")
            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Poll response body: $body")
            
            if (!response.isSuccessful || body == null) {
                return@withContext LoginResult.Failed("网络请求失败")
            }
            
            val apiResponse = gson.fromJson(body, object : com.google.gson.reflect.TypeToken<BiliApiResponse<QRLoginPollResponse>>() {}.type) as BiliApiResponse<QRLoginPollResponse>
            
            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Poll API code: ${apiResponse.code}, data.code: ${apiResponse.data?.code}, message: ${apiResponse.data?.message}")
            
            when (apiResponse.data?.code) {
                0 -> {
                    com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Login success! URL: ${apiResponse.data.url}")
                    
                    // 登录成功，先访问返回的URL来获取Cookie
                    apiResponse.data.url?.let { loginUrl ->
                        try {
                            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Accessing login URL to get cookies...")
                            val followRequest = Request.Builder()
                                .url(loginUrl)
                                .get()
                                .build()
                            val followResponse = client.newCall(followRequest).execute()
                            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Follow response code: ${followResponse.code}")
                            
                            // 重要：从跳转响应中保存Cookie
                            saveCookiesFromResponse(followResponse)
                            com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Cookies saved from follow response")
                            
                            followResponse.close()
                        } catch (e: Exception) {
                            android.util.Log.e("BiliAuth", "Failed to follow URL: ${e.message}")
                        }
                    }
                    
                    // 保存刷新令牌
                    apiResponse.data.refreshToken?.let { token ->
                        prefs.edit().putString("refresh_token", token).apply()
                    }
                    
                    // 等待Cookie完全保存
                    delay(500)
                    // 敏感信息：使用Logger.sensitive保护Cookie
                    com.fam4k007.videoplayer.utils.Logger.sensitive("BiliAuth", "Cookies after login", getCookies().toString())
                    
                    // 获取用户信息（必须等待完成）
                    fetchAndSaveUserInfo()
                    
                    // 给一点时间让SharedPreferences写入完成
                    delay(200)
                    
                    // 敏感信息：使用Logger.sensitive保护Cookie
                    com.fam4k007.videoplayer.utils.Logger.sensitive("BiliAuth", "Final cookies", getCookies().toString())
                    com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "User info after fetch: ${getUserInfo()?.uname}")
                    LoginResult.Success
                }
                86101 -> {
                    com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Waiting for scan")
                    LoginResult.WaitingScan          // 未扫码
                }
                86090 -> {
                    com.fam4k007.videoplayer.utils.Logger.d("BiliAuth", "Scanned, waiting confirm")
                    LoginResult.WaitingConfirm       // 已扫码未确认
                }
                86038 -> {
                    android.util.Log.d("BiliAuth", "QR code expired")
                    LoginResult.Expired              // 二维码过期
                }
                else -> {
                    android.util.Log.e("BiliAuth", "Unknown code: ${apiResponse.data?.code}")
                    LoginResult.Failed(apiResponse.data?.message ?: "登录失败")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("BiliAuth", "Poll error: ${e.message}", e)
            LoginResult.Failed(e.message ?: "未知错误")
        }
    }
    
    /**
     * 获取并保存用户信息
     */
    private suspend fun fetchAndSaveUserInfo() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BiliAuth", "Starting to fetch user info...")
            val request = Request.Builder()
                .url("$BASE_URL/x/web-interface/nav")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext
            
            android.util.Log.d("BiliAuth", "User info response: $body")
            
            val apiResponse = gson.fromJson(body, object : com.google.gson.reflect.TypeToken<BiliApiResponse<UserInfoResponse>>() {}.type) as BiliApiResponse<UserInfoResponse>
            
            if (apiResponse.code == 0 && apiResponse.data != null) {
                val userData = apiResponse.data
                val userInfo = UserInfo(
                    mid = userData.mid,
                    uname = userData.uname,
                    face = userData.face,
                    vipStatus = userData.vipInfo?.status ?: 0,
                    vipType = userData.vipInfo?.type ?: 0
                )
                android.util.Log.d("BiliAuth", "Saving user info: ${userInfo.uname}")
                saveUserInfo(userInfo)
                android.util.Log.d("BiliAuth", "User info saved successfully")
            } else {
                android.util.Log.e("BiliAuth", "Failed to get user info, code: ${apiResponse.code}")
            }
        } catch (e: Exception) {
            android.util.Log.e("BiliAuth", "Error fetching user info", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 保存Cookie
     */
    private fun saveCookiesFromResponse(response: Response) {
        val cookies = response.headers("Set-Cookie")
        if (cookies.isNotEmpty()) {
            val cookieMap = mutableMapOf<String, String>()
            cookies.forEach { cookie ->
                val parts = cookie.split(";")[0].split("=", limit = 2)
                if (parts.size == 2) {
                    cookieMap[parts[0]] = parts[1]
                }
            }
            prefs.edit().putString(KEY_COOKIES, gson.toJson(cookieMap)).apply()
        }
    }
    
    /**
     * 保存用户信息
     */
    private fun saveUserInfo(userInfo: UserInfo) {
        prefs.edit().putString(KEY_USER_INFO, gson.toJson(userInfo)).apply()
    }
    
    /**
     * 获取当前用户信息
     */
    fun getUserInfo(): UserInfo? {
        val json = prefs.getString(KEY_USER_INFO, null) ?: return null
        return try {
            gson.fromJson(json, UserInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getUserInfo() != null && getCookies().isNotEmpty()
    }
    
    /**
     * 获取所有Cookies
     */
    fun getCookies(): Map<String, String> {
        val json = prefs.getString(KEY_COOKIES, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 获取Cookie字符串
     */
    fun getCookieString(): String {
        return getCookies().entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
    
    /**
     * 退出登录
     */
    fun logout() {
        prefs.edit().clear().apply()
        instance = null
    }
    
    /**
     * 获取OkHttp客户端（带Cookie）
     */
    fun getClient(): OkHttpClient = client
}

/**
 * Cookie存储器
 */
private class BiliCookieJar(private val prefs: SharedPreferences) : CookieJar {
    private val gson = Gson()
    
    // 内存中的Cookie存储
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            android.util.Log.d("BiliCookieJar", "No cookies to save from: $url")
            return
        }
        
        android.util.Log.d("BiliCookieJar", "Saving ${cookies.size} cookies from: $url")
        
        // 保存到内存
        val host = url.host
        cookieStore[host] = cookies.toMutableList()
        
        // 同时保存到SharedPreferences（简化版，只保存name=value）
        val cookieMap = mutableMapOf<String, String>()
        val existing = prefs.getString("cookies", null)
        if (existing != null) {
            try {
                val existingMap: Map<String, String> = gson.fromJson(existing, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
                cookieMap.putAll(existingMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        cookies.forEach { cookie ->
            android.util.Log.d("BiliCookieJar", "Saving cookie: ${cookie.name} = ${cookie.value.take(20)}... domain=${cookie.domain}")
            cookieMap[cookie.name] = cookie.value
        }
        
        prefs.edit().putString("cookies", gson.toJson(cookieMap)).apply()
        android.util.Log.d("BiliCookieJar", "Total cookies saved: ${cookieMap.size}")
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        android.util.Log.d("BiliCookieJar", "Loading cookies for: $url")
        
        // 先尝试从内存加载
        val host = url.host
        val memoryCookies = cookieStore[host]?.filter { !it.expiresAt.let { time -> time < System.currentTimeMillis() } }
        if (!memoryCookies.isNullOrEmpty()) {
            android.util.Log.d("BiliCookieJar", "Loaded ${memoryCookies.size} cookies from memory")
            return memoryCookies
        }
        
        // 从SharedPreferences加载
        val json = prefs.getString("cookies", null) ?: return emptyList()
        return try {
            val cookieMap: Map<String, String> = gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
            val cookies = cookieMap.map { (name, value) ->
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)  // 使用请求的host
                    .path("/")
                    .build()
            }
            android.util.Log.d("BiliCookieJar", "Loaded ${cookies.size} cookies from storage")
            cookies
        } catch (e: Exception) {
            android.util.Log.e("BiliCookieJar", "Error loading cookies", e)
            emptyList()
        }
    }
}
