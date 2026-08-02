package com.fam4k007.videoplayer.dandanplay

import com.fam4k007.videoplayer.preferences.PreferencesManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 弹幕自动匹配缓存
 * 用户手动匹配某集后缓存番剧信息，切集时自动加载对应集弹幕
 */
data class DanmakuAutoMatchCache(
    val animeId: Int,
    val animeTitle: String,
    val serverUrl: String?,        // null=默认服务器
    val episodes: List<CachedEpisode>
) {
    data class CachedEpisode(
        val episodeId: Int,
        val episodeTitle: String
    )

    companion object {
        private const val KEY = "danmaku_auto_match_cache"

        fun save(prefs: PreferencesManager, cache: DanmakuAutoMatchCache) {
            val json = JSONObject().apply {
                put("animeId", cache.animeId)
                put("animeTitle", cache.animeTitle)
                put("serverUrl", cache.serverUrl ?: "")
                put("episodes", JSONArray().apply {
                    cache.episodes.forEach { ep ->
                        put(JSONObject().apply {
                            put("episodeId", ep.episodeId)
                            put("episodeTitle", ep.episodeTitle)
                        })
                    }
                })
            }
            prefs.sharedPreferences.edit().putString(KEY, json.toString()).commit()
        }

        fun load(prefs: PreferencesManager): DanmakuAutoMatchCache? {
            val raw = prefs.sharedPreferences.getString(KEY, null) ?: return null
            return try {
                val json = JSONObject(raw)
                val episodes = mutableListOf<CachedEpisode>()
                val arr = json.getJSONArray("episodes")
                for (i in 0 until arr.length()) {
                    val ep = arr.getJSONObject(i)
                    episodes.add(CachedEpisode(ep.getInt("episodeId"), ep.getString("episodeTitle")))
                }
                DanmakuAutoMatchCache(
                    animeId = json.getInt("animeId"),
                    animeTitle = json.getString("animeTitle"),
                    serverUrl = json.getString("serverUrl").ifEmpty { null },
                    episodes = episodes
                )
            } catch (_: Exception) {
                null
            }
        }

        fun clear(prefs: PreferencesManager) {
            prefs.sharedPreferences.edit().remove(KEY).apply()
        }
    }
}

/**
 * 从文件名提取集数
 * 支持格式: "01.mkv", "第01话", "S01E01", "[Group] Anime - 01 [1080p].mkv", "Anime_12.5.mkv"
 */
fun extractEpisodeNumber(fileName: String): Double? {
    val name = fileName.substringBeforeLast(".")

    // S01E01 格式 (季 集)
    Regex("""[Ss](\d+)[Ee](\d+)""").find(name)?.let {
        return it.groupValues[2].toDoubleOrNull()
    }

    // 第01话 / 第01集 格式
    Regex("""第\s*(\d+(?:\.\d+)?)\s*[话集回話]""").find(name)?.let {
        return it.groupValues[1].toDoubleOrNull()
    }

    // EP01 / ep01 格式
    Regex("""[Ee][Pp]\s*(\d+(?:\.\d+)?)""").find(name)?.let {
        return it.groupValues[1].toDoubleOrNull()
    }

    // 末尾数字 (01, 01v2 等)
    Regex("""[\[【\s\-_]#](\d+(?:\.\d+)?)(?:v\d+)?(?:\s*[\]】])?$""").find(name)?.let {
        return it.groupValues[1].toDoubleOrNull()
    }

    // 开头数字 (01 - Title 格式)
    Regex("""^(\d+(?:\.\d+)?)\s*[-–—_\s]""").find(name)?.let {
        return it.groupValues[1].toDoubleOrNull()
    }

    // 纯数字文件名
    Regex("""^(\d+(?:\.\d+)?)$""").find(name)?.let {
        return it.groupValues[1].toDoubleOrNull()
    }

    return null
}

/**
 * 从集标题提取集数
 * DanDanPlay 返回的标题如 "第01话"、"01"、"Episode 01"
 */
fun extractEpisodeNumberFromTitle(episodeTitle: String): Double? {
    return extractEpisodeNumber(episodeTitle)
        ?: Regex("""(\d+(?:\.\d+)?)""").find(episodeTitle)?.groupValues?.get(1)?.toDoubleOrNull()
}

/**
 * 根据集号在缓存中查找匹配的剧集
 */
fun findMatchingEpisode(cache: DanmakuAutoMatchCache, episodeNumber: Double): DanmakuAutoMatchCache.CachedEpisode? {
    // 精确匹配
    cache.episodes.forEach { ep ->
        val epNum = extractEpisodeNumberFromTitle(ep.episodeTitle)
        if (epNum != null && kotlin.math.abs(epNum - episodeNumber) < 0.001) {
            return ep
        }
    }
    // 按索引匹配（集号取整后对应数组索引+1）
    val intNum = episodeNumber.toInt()
    val index = intNum - 1
    if (index >= 0 && index < cache.episodes.size) {
        return cache.episodes[index]
    }
    return null
}
