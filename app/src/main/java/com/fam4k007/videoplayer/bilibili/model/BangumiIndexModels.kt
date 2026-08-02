package com.fam4k007.videoplayer.bilibili.model

import com.google.gson.annotations.SerializedName

/**
 * 番剧索引筛选条件响应
 */
data class PgcIndexConditionResponse(
    val code: Int,
    val message: String?,
    val data: PgcIndexConditionData?
)

data class PgcIndexConditionData(
    val order: List<OrderItem>?,
    val filter: List<FilterGroup>?
)

data class OrderItem(
    val field: Int,
    val name: String
)

data class FilterGroup(
    val field: String,
    val name: String,
    val values: List<FilterValue>?
)

data class FilterValue(
    val keyword: String,
    val name: String
)

/**
 * 番剧索引结果响应
 */
data class PgcIndexResultResponse(
    val code: Int,
    val message: String?,
    val data: PgcIndexResultData?
)

data class PgcIndexResultData(
    val has_next: Int,
    val list: List<PgcIndexItem>?
)

data class PgcIndexItem(
    val season_id: Int,
    val title: String,
    val cover: String,
    val badge: String?,
    val index_show: String?,
    val order: String?
)

/**
 * 番剧详情响应
 */
data class PgcInfoResponse(
    val code: Int,
    val message: String?,
    val result: PgcInfoResult?
)

data class PgcInfoResult(
    val season_id: Int,
    val title: String,
    val cover: String,
    val evaluate: String?,
    val actors: String?,
    val type: Int,
    val areas: List<Area>?,
    val rating: Rating?,
    val stat: PgcStat?,
    val publish: Publish?,
    val new_ep: NewEp?,
    val episodes: List<PgcEpisode>?,
    val user_status: UserStatus?
)

data class Area(
    val id: Int,
    val name: String
)

data class Rating(
    val score: Double,
    val count: Int
)

data class PgcStat(
    @SerializedName("views") val views: Long = 0,
    @SerializedName("danmakus") val danmakus: Long = 0,
    @SerializedName("favorites") val favorites: Long = 0,
    @SerializedName("coins") val coins: Long = 0
)

data class Publish(
    @SerializedName("pub_time") val pubTime: String? = null,
    @SerializedName("pub_date") val pubDate: String? = null,
    @SerializedName("pub_date_show") val pubDateShow: String? = null,
    @SerializedName("pub_index") val pubIndex: String? = null,
    @SerializedName("is_started") val isStarted: Int? = null,
    @SerializedName("is_finish") val isFinish: Int? = null,
    @SerializedName("weekday") val weekday: Int? = null,
    @SerializedName("time_from_show") val timeFromShow: String? = null
)

data class NewEp(
    val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("index_show") val indexShow: String?,
    @SerializedName("long_title") val longTitle: String?,
    val cover: String?
)

data class PgcEpisode(
    val ep_id: Int,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    @SerializedName("long_title") val longTitle: String?,
    val cover: String?,
    val duration: Long?,
    val badge: String?,
    @SerializedName("show_title") val showTitle: String?,
    @SerializedName("share_url") val shareUrl: String?
)

data class UserStatus(
    val favored: Int,
    val follow: Int
)

/**
 * 播放地址响应（带包装）
 */
data class BiliPlayUrlResponse(
    val code: Int,
    val message: String?,
    val result: PlayUrlResult?
)

data class PlayUrlResult(
    val quality: Int,
    val format: String?,
    val timelength: Long?,
    val durl: List<PlayUrlDurl>?,
    val dash: PlayUrlDash?,
    val clip_info_list: List<ClipInfo>? = null
)

/**
 * 番剧剪辑信息（bilibili playurl 响应的 clip_info_list）
 * 官方维护的片头/片尾时间段，start/end 单位秒
 * clipType: CLIP_TYPE_OP = 片头, CLIP_TYPE_ED = 片尾
 */
data class ClipInfo(
    val start: Double,
    val end: Double,
    val clipType: String?
)

data class PlayUrlDurl(
    val order: Int,
    val length: Long,
    val size: Long,
    val url: String?,
    val backup_url: List<String>?
)

data class PlayUrlDash(
    val video: List<DashVideo>?,
    val audio: List<DashAudio>?
)

data class DashVideo(
    val id: Int,
    val baseUrl: String?,
    val backupUrl: List<String>?,
    val bandwidth: Int,
    val codecs: String?
)

data class DashAudio(
    val id: Int,
    val baseUrl: String?,
    val backupUrl: List<String>?,
    val bandwidth: Int,
    val codecs: String?
)