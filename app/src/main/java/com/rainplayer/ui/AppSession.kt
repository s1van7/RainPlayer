package com.rainplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rainplayer.Graph
import com.rainplayer.data.MediaBackend
import com.rainplayer.data.ServerConfig

/**
 * 会话状态：当前活动服务器 + 已保存服务器列表。
 * 所有界面共用同一个实例，切换服务器后自动重建后端并刷新。
 */
class AppSession {

    var backend by mutableStateOf<MediaBackend?>(Graph.backend())
        private set

    var servers by mutableStateOf(Graph.prefs.servers)
        private set

    var error by mutableStateOf<String?>(null)

    fun activate(config: ServerConfig) {
        Graph.saveServer(config)
        Graph.prefs.activeServerId = config.id
        Graph.invalidateBackend()
        backend = Graph.backend()
        servers = Graph.prefs.servers
        error = null
    }

    fun remove(config: ServerConfig) {
        val remaining = Graph.prefs.servers.filterNot { it.id == config.id }
        Graph.prefs.servers = remaining
        if (Graph.prefs.activeServerId == config.id) {
            Graph.prefs.activeServerId = remaining.firstOrNull()?.id
            Graph.invalidateBackend()
            backend = Graph.backend()
        }
        servers = remaining
    }

    fun logout() {
        Graph.prefs.activeServerId = null
        Graph.invalidateBackend()
        backend = null
    }

    fun reload() {
        servers = Graph.prefs.servers
        backend = Graph.backend()
    }
}
