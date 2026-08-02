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
    val bilibiliCid: Long = 0,  // B站视频cid，用于原生弹幕加载
    val opEdClips: List<OpEdClip>? = null  // 番剧官方 OP/ED 时间段（来自 playurl 的 clip_info_list）
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
 * 番剧官方片头/片尾时间段
 *
 * @param type "OP" 或 "ED"
 * @param startSeconds 开始秒
 * @param endSeconds 结束秒
 */
@Parcelize
data class OpEdClip(
    val type: String,
    val startSeconds: Double,
    val endSeconds: Double
) : Parcelable
