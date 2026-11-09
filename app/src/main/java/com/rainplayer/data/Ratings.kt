package com.rainplayer.data

import com.rainplayer.core.Http
import com.rainplayer.core.J
import com.rainplayer.core.arr
import com.rainplayer.core.asArr
import com.rainplayer.core.asObj
import com.rainplayer.core.dbl
import com.rainplayer.core.int
import com.rainplayer.core.obj
import com.rainplayer.core.str
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/**
 * 外部评分聚合结果。
 *
 * 所有字段都可空：任何一个源挂了只影响自己那一项，UI 按「有就显示，没有就不占位」处理。
 */
data class ExternalRatings(
    val tmdb: Double? = null,
    val tmdbVotes: Int? = null,
    val douban: Double? = null,
    val doubanVotes: Int? = null,
    val tmdbId: Int? = null,
    val doubanId: String? = null,
) {
    val isEmpty: Boolean get() = tmdb == null && douban == null
}

/**
 * 评分聚合。
 *
 * - **TMDB**：官方 API。内置了一把公共 v3 Key（见 [com.rainplayer.core.Keys]），
 *   开箱即用；用户可以在设置里替换成自己的，清空则关闭这一项。
 * - **豆瓣**：豆瓣官方 API 早已关闭，这里走公开的第三方聚合接口（wmdb），
 *   拿不到就静默返回空——绝不因为评分把详情页卡住或报错。
 *
 * 结果做进程内 LRU 缓存（同一部片反复进出详情页不会重复请求）。
 */
object RatingsRepo {

    private const val TMDB_BASE = "https://api.themoviedb.org/3"
    private const val WMDB_SEARCH = "https://api.wmdb.tv/api/v1/movie/search"

    private const val CACHE_MAX = 200
    private val cache = object : LinkedHashMap<String, ExternalRatings>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExternalRatings>?): Boolean =
            size > CACHE_MAX
    }

    /**
     * 查询一部影片的外部评分。
     *
     * @param isTv 剧集走 TMDB 的 tv 端点，电影走 movie 端点
     * @param tmdbKey 用户配置的 TMDB v3 Key，空串表示不查 TMDB
     * @param doubanEnabled 是否查豆瓣
     */
    suspend fun fetch(
        title: String,
        originalTitle: String? = null,
        year: Int? = null,
        isTv: Boolean = false,
        tmdbKey: String = "",
        doubanEnabled: Boolean = true,
    ): ExternalRatings {
        if (title.isBlank()) return ExternalRatings()
        val key = "${title.trim()}|${year ?: 0}|$isTv"
        synchronized(cache) { cache[key] }?.let { return it }

        val tmdb = if (tmdbKey.isNotBlank()) {
            runCatching { queryTmdb(title, originalTitle, year, isTv, tmdbKey.trim()) }.getOrNull()
        } else {
            null
        }
        val douban = if (doubanEnabled) {
            runCatching { queryDouban(title, originalTitle, year) }.getOrNull()
        } else {
            null
        }

        val merged = ExternalRatings(
            tmdb = tmdb?.tmdb,
            tmdbVotes = tmdb?.tmdbVotes,
            tmdbId = tmdb?.tmdbId,
            douban = douban?.douban,
            doubanVotes = douban?.doubanVotes,
            doubanId = douban?.doubanId,
        )
        // 两个源都空时不缓存，避免用户刚填好 Key 还要等缓存过期
        if (!merged.isEmpty) {
            synchronized(cache) { cache[key] = merged }
        }
        return merged
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    // ---------------------------------------------------------------- TMDB

    private suspend fun queryTmdb(
        title: String,
        originalTitle: String?,
        year: Int?,
        isTv: Boolean,
        apiKey: String,
    ): ExternalRatings {
        val kind = if (isTv) "tv" else "movie"
        val yearParam = when {
            year == null -> ""
            isTv -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        // 中文名搜不到时用原名再试一次
        val queries = listOfNotNull(title, originalTitle?.takeIf { it != title }).distinct()
        for (q in queries) {
            val url = "$TMDB_BASE/search/$kind?api_key=$apiKey&language=zh-CN&include_adult=false" +
                "&query=${URLEncoder.encode(q, "UTF-8")}$yearParam"
            val root = J.obj(Http.getText(url)) ?: continue
            val first = root.arr("results")?.firstOrNull()?.asObj() ?: continue
            val vote = first.dbl("vote_average")?.takeIf { it > 0 }
            val votes = first.int("vote_count")
            val id = first.int("id")
            if (vote == null) continue
            return ExternalRatings(tmdb = round1(vote), tmdbVotes = votes, tmdbId = id)
        }
        return ExternalRatings()
    }

    // -------------------------------------------------------------- 豆瓣聚合

    /**
     * wmdb 是个公开的影视元数据聚合站，返回结构大致是：
     * ```
     * [ { "doubanId":"1292052", "doubanRating":"9.7", "doubanVotes":2800000,
     *     "name":"肖申克的救赎", "year":"1994", "data":[{...}] } ]
     * ```
     * 字段名在不同版本里略有差异，这里全部做兼容读取。
     */
    private suspend fun queryDouban(
        title: String,
        originalTitle: String?,
        year: Int?,
    ): ExternalRatings {
        val queries = listOfNotNull(title, originalTitle?.takeIf { it != title }).distinct()
        for (q in queries) {
            val url = "$WMDB_SEARCH?q=${URLEncoder.encode(q, "UTF-8")}&lang=Cn&limit=8"
            val list = J.parse(Http.getText(url))?.asArr() ?: continue
            val candidates = list.mapNotNull { it.asObj() }
            // 优先取年份匹配的那条，其次取评分最高的
            val matched = candidates.firstOrNull { c ->
                val y = c.str("year")?.take(4)?.toIntOrNull()
                y != null && year != null && y == year
            } ?: candidates.maxByOrNull { it.ratingValue() ?: 0.0 }
            val rating = matched?.ratingValue() ?: continue
            return ExternalRatings(
                douban = round1(rating),
                doubanVotes = matched.votesValue(),
                doubanId = matched.str("doubanId") ?: matched.str("douban_id"),
            )
        }
        return ExternalRatings()
    }

    private fun JsonObject.ratingValue(): Double? {
        val raw = str("doubanRating")
            ?: str("douban_rating")
            ?: str("rating")
            ?: obj("rating")?.str("value")
        return raw?.toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun JsonObject.votesValue(): Int? {
        int("doubanVotes")?.let { return it }
        int("douban_votes")?.let { return it }
        val raw = str("doubanVotes") ?: str("douban_votes")
        return raw?.toDoubleOrNull()?.toInt()
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
