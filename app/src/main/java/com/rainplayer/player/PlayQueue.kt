package com.rainplayer.player

/** 播放队列里的一集。 */
data class QueueEntry(
    val id: String,
    val title: String,
    /** 例：S01E05 */
    val label: String = "",
)

/**
 * 播放队列：让「自动连播」在多集之间连续生效，同时给播放页提供选集列表。
 *
 * 剧集详情页把整季的集塞进来，播放器每播完一集就取下一个；
 * 电影/单集则只放一个元素，播完即停。
 */
object PlayQueue {

    private var entries: List<QueueEntry> = emptyList()
    private var index: Int = -1

    fun set(list: List<QueueEntry>, startId: String) {
        entries = list
        index = list.indexOfFirst { it.id == startId }
    }

    /** 只有 id 的场景：标题留空，选集列表里退化成序号。 */
    fun setIds(ids: List<String>, startId: String) {
        set(ids.map { QueueEntry(it, "") }, startId)
    }

    fun mark(id: String) {
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) index = i
    }

    fun peekNext(): String? = entries.getOrNull(index + 1)?.id

    fun advance(): String? {
        val next = entries.getOrNull(index + 1) ?: return null
        index += 1
        return next.id
    }

    /** 直接跳到某一集（选集面板用）。 */
    fun jumpTo(id: String): Boolean {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        index = i
        return true
    }

    fun all(): List<QueueEntry> = entries

    fun currentId(): String? = entries.getOrNull(index)?.id

    fun size(): Int = entries.size

    fun clear() {
        entries = emptyList()
        index = -1
    }
}
