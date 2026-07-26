package com.fam4k007.videoplayer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 视频缓存 DAO
 */
@Dao
interface VideoCacheDao {
    
    @Query("SELECT * FROM video_cache ORDER BY folderName, name")
    fun getAllVideos(): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY name")
    fun getVideosByFolder(folderPath: String): List<VideoCacheEntity>
    
    /**
     * 获取指定文件夹的所有视频（不分页），支持动态排序
     * 使用nameSortKey实现自然排序
     */
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY nameSortKey ASC")
    fun getVideosByFolderSortedByNameAsc(folderPath: String): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY nameSortKey DESC")
    fun getVideosByFolderSortedByNameDesc(folderPath: String): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY dateAdded ASC")
    fun getVideosByFolderSortedByDateAsc(folderPath: String): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY dateAdded DESC")
    fun getVideosByFolderSortedByDateDesc(folderPath: String): List<VideoCacheEntity>
    
    /**
     * 分页查询指定文件夹的视频（支持Paging3）
     * 使用nameSortKey实现自然排序
     */
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY nameSortKey ASC LIMIT :limit OFFSET :offset")
    fun getVideosByFolderPagedByNameAsc(
        folderPath: String,
        limit: Int,
        offset: Int
    ): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY nameSortKey DESC LIMIT :limit OFFSET :offset")
    fun getVideosByFolderPagedByNameDesc(
        folderPath: String,
        limit: Int,
        offset: Int
    ): List<VideoCacheEntity>
    
    /**
     * 按日期排序
     */
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY dateAdded ASC LIMIT :limit OFFSET :offset")
    fun getVideosByFolderPagedByDateAsc(
        folderPath: String,
        limit: Int,
        offset: Int
    ): List<VideoCacheEntity>
    
    @Query("SELECT * FROM video_cache WHERE folderPath = :folderPath ORDER BY dateAdded DESC LIMIT :limit OFFSET :offset")
    fun getVideosByFolderPagedByDateDesc(
        folderPath: String,
        limit: Int,
        offset: Int
    ): List<VideoCacheEntity>
    
    /**
     * 获取指定文件夹视频总数（Paging3需要）
     */
    @Query("SELECT COUNT(*) FROM video_cache WHERE folderPath = :folderPath")
    fun getVideoCountByFolder(folderPath: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVideos(videos: List<VideoCacheEntity>)
    
    @Query("DELETE FROM video_cache WHERE lastScanned < :timestamp")
    fun deleteOldEntries(timestamp: Long): Int
    
    @Query("DELETE FROM video_cache")
    fun clearAll(): Int
    
    @Query("SELECT COUNT(*) FROM video_cache")
    fun getVideoCount(): Int

    /** 获取所有缓存路径及修改时间，用于增量扫描对比 */
    @Query("SELECT path, dateModified FROM video_cache")
    fun getCachedPathModTimeMap(): List<CachedPathModTime>
}

/** 缓存路径与修改时间的简单映射，用于增量扫描 */
data class CachedPathModTime(
    val path: String,
    val dateModified: Long
)
