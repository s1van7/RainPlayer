package com.rain.player.player

/**
 * 播放队列：让「自动连播」在多集之间连续生效。
 *
 * 剧集详情页把整季的集 id 列表塞进来，播放器每播完一集就取下一个；
 * 电影/单集则只放一个元素，播完即停。
 */
object PlayQueue {

    private var ids: List<String> = emptyList()
    private var index: Int = -1

    fun set(list: List<String>, startId: String) {
        ids = list
        index = list.indexOf(startId).takeIf { it >= 0 } ?: -1
    }

    fun mark(id: String) {
        val i = ids.indexOf(id)
        if (i >= 0) index = i
    }

    fun peekNext(): String? = ids.getOrNull(index + 1)

    fun advance(): String? {
        val next = ids.getOrNull(index + 1) ?: return null
        index += 1
        return next
    }

    fun clear() {
        ids = emptyList()
        index = -1
    }
}
