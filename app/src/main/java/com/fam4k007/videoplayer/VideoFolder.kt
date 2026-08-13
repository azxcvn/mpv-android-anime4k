package com.fam4k007.videoplayer

data class VideoFolder(
    val folderPath: String,
    val folderName: String,
    val videoCount: Int,
    val videos: List<VideoFile>,
    val totalSize: Long = 0L,
    val dateModified: Long = 0L
)

data class VideoFile(
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long
)
