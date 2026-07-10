package com.fam4k007.videoplayer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 播放历史记录 DAO
 * 提供高效的数据库操作，替代SharedPreferences+JSON方案
 */
@Dao
interface PlaybackHistoryDao {
    
    /**
     * 获取所有历史记录（按播放时间倒序）
     */
    @Query("SELECT * FROM playback_history ORDER BY lastPlayed DESC")
    fun getAllHistory(): List<PlaybackHistoryEntity>
    
    /**
     * 根据URI获取历史记录
     */
    @Query("SELECT * FROM playback_history WHERE uri = :uri LIMIT 1")
    fun getHistoryByUri(uri: String): PlaybackHistoryEntity?
    
    /**
     * 获取最后播放的本地视频（非http/https）
     */
    @Query("SELECT * FROM playback_history WHERE uri NOT LIKE 'http://%' AND uri NOT LIKE 'https://%' ORDER BY lastPlayed DESC LIMIT 1")
    fun getLastPlayedLocalVideo(): PlaybackHistoryEntity?
    
    /**
     * 插入或更新历史记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(history: PlaybackHistoryEntity)
    
    /**
     * 批量插入历史记录（用于数据迁移）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(histories: List<PlaybackHistoryEntity>)
    
    /**
     * 删除指定URI的历史记录
     */
    @Query("DELETE FROM playback_history WHERE uri = :uri")
    fun deleteByUri(uri: String)
    
    /**
     * 清空所有历史记录
     */
    @Query("DELETE FROM playback_history")
    fun clearAll()
    
    /**
     * 获取历史记录总数
     */
    @Query("SELECT COUNT(*) FROM playback_history")
    fun getCount(): Int
    
    /**
     * 删除超过指定数量的旧记录
     * @param limit 保留的最大记录数
     */
    @Query("DELETE FROM playback_history WHERE uri IN (SELECT uri FROM playback_history ORDER BY lastPlayed DESC LIMIT -1 OFFSET :limit)")
    fun deleteOldRecords(limit: Int)
    
    /**
     * 更新弹幕信息
     */
    @Query("UPDATE playback_history SET danmuPath = :danmuPath, danmuVisible = :danmuVisible, danmuOffsetTime = :danmuOffsetTime WHERE uri = :uri")
    fun updateDanmu(uri: String, danmuPath: String?, danmuVisible: Boolean, danmuOffsetTime: Long)
    
    /**
     * 更新缩略图路径
     */
    @Query("UPDATE playback_history SET thumbnailPath = :thumbnailPath WHERE uri = :uri")
    fun updateThumbnail(uri: String, thumbnailPath: String?)
    
    /**
     * 获取所有播放状态的精简列表（仅返回 URI、position、duration、hasBeenWatched）
     * 用于视频列表快速查询进度，避免加载全部字段
     */
    @Query("SELECT uri, position, duration, hasBeenWatched FROM playback_history")
    fun getAllPlaybackStates(): List<PlaybackState>
    
    /**
     * 更新已观看标记
     */
    @Query("UPDATE playback_history SET hasBeenWatched = :watched WHERE uri = :uri")
    fun updateWatchedStatus(uri: String, watched: Boolean)
}
