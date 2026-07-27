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
    val audioUrl: String? = null,  // DASH格式音频流URL
    val bilibiliCid: Long = 0  // B站视频cid，用于原生弹幕加载
) : Parcelable {

    enum class Source {
        DIRECT_INPUT,
        WEB_SNIFFER,
        WEBDAV,
        BILIBILI,
        UNKNOWN
    }
}
