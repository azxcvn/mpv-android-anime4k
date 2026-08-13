package com.fam4k007.videoplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoFileParcelable(
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val width: Int = 0,
    val height: Int = 0
) : Parcelable
