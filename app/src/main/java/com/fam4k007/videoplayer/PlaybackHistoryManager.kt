package com.fam4k007.videoplayer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.fam4k007.videoplayer.database.PlaybackHistoryEntity
import com.fam4k007.videoplayer.database.VideoDatabase
import com.fam4k007.videoplayer.utils.FormatUtils
import com.fam4k007.videoplayer.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 播放历史记录管理器
 * 已迁移到Room数据库，提供更好的性能和类型安全
 */
class PlaybackHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackHistoryManager"
        private const val MIGRATION_FLAG = "history_migrated_to_room"
    }

    private val database = VideoDatabase.getDatabase(context)
    private val historyDao = database.playbackHistoryDao()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(AppConstants.Preferences.PLAYBACK_HISTORY, Context.MODE_PRIVATE)
    
    // 使用协程作用域管理后台任务
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // 首次启动时自动迁移数据
        migrateFromSharedPreferences()
    }

    /**
     * 历史记录数据类（保持向后兼容）
     */
    data class HistoryItem(
        val uri: String,
        val fileName: String,
        val position: Long,
        val duration: Long,
        val lastPlayed: Long,
        val folderName: String,
        val danmuPath: String? = null,
        val danmuVisible: Boolean = true,
        val danmuOffsetTime: Long = 0L,
        val thumbnailPath: String? = null
    ) {
        fun getFormattedDate(): String {
            return FormatUtils.formatDateShort(lastPlayed)
        }

        fun getProgressPercentage(): Int {
            return if (duration > 0) {
                ((position.toDouble() / duration) * 100).toInt()
            } else {
                0
            }
        }
        
        /**
         * 转换为Room实体
         */
        fun toEntity(): PlaybackHistoryEntity {
            return PlaybackHistoryEntity(
                uri = uri,
                fileName = fileName,
                position = position,
                duration = duration,
                lastPlayed = lastPlayed,
                folderName = folderName,
                danmuPath = danmuPath,
                danmuVisible = danmuVisible,
                danmuOffsetTime = danmuOffsetTime,
                thumbnailPath = thumbnailPath,
                hasBeenWatched = false
            )
        }
        
        companion object {
            /**
             * 从Room实体转换
             */
            fun fromEntity(entity: PlaybackHistoryEntity): HistoryItem {
                return HistoryItem(
                    uri = entity.uri,
                    fileName = entity.fileName,
                    position = entity.position,
                    duration = entity.duration,
                    lastPlayed = entity.lastPlayed,
                    folderName = entity.folderName,
                    danmuPath = entity.danmuPath,
                    danmuVisible = entity.danmuVisible,
                    danmuOffsetTime = entity.danmuOffsetTime,
                    thumbnailPath = entity.thumbnailPath
                )
            }
        }
    }

    /**
     * 添加或更新历史记录
     */
    fun addHistory(
        uri: Uri,
        fileName: String,
        position: Long,
        duration: Long,
        folderName: String,
        danmuPath: String? = null,
        danmuVisible: Boolean = true
    ) {
        scope.launch {
            try {
                val oldEntity = historyDao.getHistoryByUri(uri.toString())
                val entity = PlaybackHistoryEntity(
                    uri = uri.toString(),
                    fileName = fileName,
                    position = position,
                    duration = duration,
                    lastPlayed = System.currentTimeMillis(),
                    folderName = folderName,
                    danmuPath = danmuPath,
                    danmuVisible = danmuVisible,
                    hasBeenWatched = oldEntity?.hasBeenWatched == true
                )
                
                historyDao.insertOrUpdate(entity)
                
                // 限制历史记录数量
                val count = historyDao.getCount()
                if (count > AppConstants.Defaults.MAX_HISTORY_SIZE) {
                    historyDao.deleteOldRecords(AppConstants.Defaults.MAX_HISTORY_SIZE)
                }
                
                Logger.d(TAG, "History added successfully: $fileName")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to add history: ${e.message}", e)
            }
        }
    }

    /**
     * 获取所有历史记录
     * 同步方法，保持API兼容性
     */
    fun getHistory(): List<HistoryItem> {
        return try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    historyDao.getAllHistory().map { HistoryItem.fromEntity(it) }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get history: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 删除单条历史记录
     */
    fun removeHistory(uri: String) {
        runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    historyDao.deleteByUri(uri)
                }
                Logger.d(TAG, "History removed successfully: $uri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to remove history: ${e.message}", e)
            }
        }
    }

    /**
     * 清空所有历史记录
     */
    fun clearHistory() {
        runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    historyDao.clearAll()
                }
                Logger.d(TAG, "History cleared successfully")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to clear history: ${e.message}", e)
            }
        }
    }

    /**
     * 更新弹幕信息
     */
    fun updateDanmu(
        uri: Uri,
        danmuPath: String?,
        danmuVisible: Boolean = true,
        danmuOffsetTime: Long = 0L
    ) {
        scope.launch {
            try {
                historyDao.updateDanmu(uri.toString(), danmuPath, danmuVisible, danmuOffsetTime)
                Logger.d(TAG, "Danmu info updated for: $uri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update danmu info: ${e.message}", e)
            }
        }
    }

    /**
     * 更新视频缩略图路径
     */
    fun updateThumbnail(uri: Uri, thumbnailPath: String?) {
        scope.launch {
            try {
                historyDao.updateThumbnail(uri.toString(), thumbnailPath)
                Logger.d(TAG, "Thumbnail updated for: $uri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update thumbnail: ${e.message}", e)
            }
        }
    }

    /**
     * 获取指定 URI 的历史记录
     */
    fun getHistoryForUri(uri: Uri): HistoryItem? {
        return try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    historyDao.getHistoryByUri(uri.toString())?.let { HistoryItem.fromEntity(it) }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get history for URI: ${e.message}", e)
            null
        }
    }

    /**
     * 获取最后播放的本地视频记录
     */
    fun getLastPlayedLocalVideo(): HistoryItem? {
        return try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    historyDao.getLastPlayedLocalVideo()?.let { HistoryItem.fromEntity(it) }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get last played local video: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从SharedPreferences迁移数据到Room数据库
     * 只在首次启动时执行一次
     */
    private fun migrateFromSharedPreferences() {
        scope.launch {
            try {
                // 检查是否已经迁移过
                val migrated = sharedPreferences.getBoolean(MIGRATION_FLAG, false)
                if (migrated) {
                    Logger.d(TAG, "Data already migrated to Room")
                    return@launch
                }
                
                // 读取旧的JSON数据
                val jsonString = sharedPreferences.getString(AppConstants.Preferences.HISTORY_LIST, null)
                if (jsonString.isNullOrEmpty()) {
                    Logger.d(TAG, "No data to migrate")
                    sharedPreferences.edit().putBoolean(MIGRATION_FLAG, true).apply()
                    return@launch
                }
                
                // 解析JSON并转换为实体
                val entities = mutableListOf<PlaybackHistoryEntity>()
                val jsonArray = JSONArray(jsonString)
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val entity = PlaybackHistoryEntity(
                            uri = jsonObject.getString("uri"),
                            fileName = jsonObject.getString("fileName"),
                            position = jsonObject.getLong("position"),
                            duration = jsonObject.getLong("duration"),
                            lastPlayed = jsonObject.getLong("lastPlayed"),
                            folderName = jsonObject.optString("folderName", "Unknown Folder"),
                            danmuPath = jsonObject.optString("danmuPath", null).takeIf { it?.isNotEmpty() == true },
                            danmuVisible = jsonObject.optBoolean("danmuVisible", true),
                            danmuOffsetTime = jsonObject.optLong("danmuOffsetTime", 0L),
                            thumbnailPath = jsonObject.optString("thumbnailPath", null).takeIf { it?.isNotEmpty() == true }
                        )
                        entities.add(entity)
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to parse history item at index $i during migration: ${e.message}")
                    }
                }
                
                // 批量插入到数据库
                if (entities.isNotEmpty()) {
                    historyDao.insertAll(entities)
                    Logger.d(TAG, "Successfully migrated ${entities.size} history items to Room")
                }
                
                // 标记迁移完成（保留原数据作为备份，不删除）
                sharedPreferences.edit().putBoolean(MIGRATION_FLAG, true).apply()
                Logger.d(TAG, "Migration completed, original data preserved as backup")
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to migrate data from SharedPreferences: ${e.message}", e)
            }
        }
    }
}
