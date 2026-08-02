package com.fam4k007.videoplayer.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RemotePlaybackRequest(
    val url: String,
    val title: String = "",
    val sourcePageUrl: String = "",
    val headers: LinkedHashMap<String, String> = linkedMapOf(),
    val detectedContentType: String? = null,
    val isStream: Boolean = false,
    val source: Source = Source.UNKNOWN,
    val audioUrl: String? = null,  // DASH audio stream URL
    val bilibiliCid: Long = 0,  // Bilibili video cid, used for native danmaku loading
    val opEdClips: List<OpEdClip>? = null  // Official OP/ED segments for bangumi (from playurl clip_info_list)
) : Parcelable {

    enum class Source {
        DIRECT_INPUT,
        WEB_SNIFFER,
        WEBDAV,
        BILIBILI,
        UNKNOWN
    }
}

/**
 * Official bangumi opening/ending segment
 *
 * @param type "OP" or "ED"
 * @param startSeconds start seconds
 * @param endSeconds end seconds
 */
@Parcelize
data class OpEdClip(
    val type: String,
    val startSeconds: Double,
    val endSeconds: Double
) : Parcelable
