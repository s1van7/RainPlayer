package com.rainplayer.data

import com.rainplayer.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 跨服务器聚合搜索。
 *
 * 对所有已配置的服务器（各自使用当前线路 + 当前账号）并发搜索，把结果合并成一条列表。
 * 每个结果都会带上来源服务器信息，UI 上打一个小标签；点开时先切到对应服务器再进详情。
 *
 * 单台服务器失败不影响其它服务器的结果——聚合搜索的价值就在于「总有一台能搜到」。
 */
object AggregateSearch {

    data class Outcome(
        val cards: List<MediaCard>,
        /** 失败的服务器名 -> 错误信息，用于在结果底部给一句「N 台服务器未响应」。 */
        val failures: List<Pair<String, String>>,
    )

    suspend fun search(query: String, targets: List<ServerConfig>): Outcome = coroutineScope {
        val keyword = query.trim()
        if (keyword.isEmpty() || targets.isEmpty()) return@coroutineScope Outcome(emptyList(), emptyList())

        val jobs = targets.map { cfg ->
            async(Dispatchers.IO) {
                val outcome: Pair<List<MediaCard>, Pair<String, String>?> = runCatching {
                    BackendFactory.create(cfg).search(keyword)
                }.fold(
                    onSuccess = { list ->
                        list.map { card ->
                            card.copy(
                                serverId = cfg.id,
                                accountId = cfg.activeUserId,
                                serverName = cfg.name,
                            )
                        } to null
                    },
                    onFailure = { e -> emptyList<MediaCard>() to (cfg.name to (e.message ?: "请求失败")) },
                )
                outcome
            }
        }

        val results = jobs.awaitAll()
        val cards = results.flatMap { it.first }
        val failures = results.mapNotNull { it.second }

        // 聚合搜索会临时实例化别的服务器的后端，它们改写了全局图片鉴权头。
        // 这里把状态还原回当前活动服务器，否则封面图会带着错误的 token 去请求。
        runCatching { Graph.backend()?.refreshAuth() }

        Outcome(cards, failures)
    }
}
