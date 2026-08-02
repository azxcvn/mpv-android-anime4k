package com.fam4k007.videoplayer.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fam4k007.videoplayer.data.model.WebDavAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * WebDAV 账户数据源
 * Data Layer - 负责账户数据的持久化存储
 */
class WebDavAccountDataSource(context: Context) {
    
    private val prefs: SharedPreferences
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "webdav_accounts"
        private const val KEY_ACCOUNTS = "accounts"
    }
    
    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * 获取所有账户
     * 修复：Gson 反序列化可能将 null 赋给 Kotlin 非空字段，
     * 导致后续 UI 层使用时 NPE 崩溃。此处对所有字段做防御性空安全处理。
     */
    fun getAllAccounts(): List<WebDavAccount> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WebDavAccount>>() {}.type
            val accounts: List<WebDavAccount> = gson.fromJson(json, type)
            accounts.map { account ->
                account.copy(
                    id = account.id ?: java.util.UUID.randomUUID().toString(),
                    displayName = account.displayName ?: "Unnamed Account",
                    serverUrl = account.serverUrl ?: "",
                    account = account.account ?: "",
                    password = account.password ?: "",
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 保存所有账户
     */
    fun saveAllAccounts(accounts: List<WebDavAccount>) {
        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()
    }
    
    /**
     * 根据 ID 获取账户
     */
    fun getAccountById(accountId: String): WebDavAccount? {
        return getAllAccounts().find { it.id == accountId }
    }
    
    /**
     * 清除所有账户
     */
    fun clearAllAccounts() {
        prefs.edit().clear().apply()
    }
}
