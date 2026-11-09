package com.rainplayer.core

/**
 * 内置的第三方服务凭据。
 *
 * TMDB 的 v3 API Key 是免费申请的，这里内置一把公共 Key 让用户开箱即用，
 * 免得每个人都去 themoviedb.org 注册。设置页仍然可以替换成自己的 Key，
 * 清空则关闭 TMDB 评分。
 *
 * 注意：公开仓库里的 Key 有被滥用的风险，一旦配额被打满或 Key 被吊销，
 * 换成任意一把新的 v3 Key 即可，不需要改别的代码。
 */
object Keys {

    /** TMDB v3 API Key（用于 /search/movie 与 /search/tv）。 */
    const val TMDB_API_KEY = "6f7320d3e5cae417f9a271146c538251"
}
