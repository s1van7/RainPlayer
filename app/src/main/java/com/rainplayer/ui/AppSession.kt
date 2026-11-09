package com.rainplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rainplayer.Graph
import com.rainplayer.data.BackendFactory
import com.rainplayer.data.MediaBackend
import com.rainplayer.data.ServerConfig
import com.rainplayer.data.ServerLine
import com.rainplayer.data.ServerUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 会话状态：服务器列表 + 当前活动服务器（含其当前线路 / 当前账号）。
 *
 * 所有界面共用同一个实例，切换后自动重建后端并触发重组。
 */
class AppSession {

    var backend by mutableStateOf<MediaBackend?>(Graph.backend())
        private set

    var servers by mutableStateOf(Graph.servers())
        private set

    var activeId by mutableStateOf(Graph.prefs.activeServerId)
        private set

    var error by mutableStateOf<String?>(null)

    val active: ServerConfig?
        get() = servers.firstOrNull { it.id == activeId }

    // ------------------------------------------------------------ 服务器切换

    fun activate(config: ServerConfig) {
        Graph.saveServer(config)
        Graph.prefs.activeServerId = config.id
        Graph.invalidateBackend()
        backend = Graph.backend()
        servers = Graph.servers()
        activeId = config.id
        error = null
    }

    /** 就地更新一台服务器（改线路/账号/图标/媒体库开关等），不动活动状态。 */
    fun update(config: ServerConfig) {
        val isActive = config.id == activeId
        Graph.saveServer(config)
        servers = Graph.servers()
        if (isActive) {
            backend = Graph.backend()
        }
    }

    fun remove(config: ServerConfig) {
        Graph.deleteServer(config.id)
        servers = Graph.servers()
        activeId = Graph.prefs.activeServerId
        backend = Graph.backend()
    }

    fun logout() {
        Graph.prefs.activeServerId = null
        Graph.invalidateBackend()
        backend = null
        activeId = null
    }

    fun reload() {
        servers = Graph.servers()
        activeId = Graph.prefs.activeServerId
        backend = Graph.backend()
    }

    // ------------------------------------------------------------ 线路 / 账号

    fun switchLine(serverId: String, lineId: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val next = target.withLine(lineId)
        Graph.saveServer(next)
        if (serverId == activeId) {
            backend = Graph.backend()
        }
        servers = Graph.servers()
    }

    fun switchUser(serverId: String, userId: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val next = target.withUser(userId)
        Graph.saveServer(next)
        if (serverId == activeId) {
            backend = Graph.backend()
        }
        servers = Graph.servers()
    }

    /**
     * 给服务器加一条线路。会先用现有 token 探一次，能连通才保存。
     */
    suspend fun addLine(serverId: String, name: String, rawUrl: String): Result<ServerConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = servers.firstOrNull { it.id == serverId } ?: error("服务器不存在")
                val url = BackendFactory.normalizeUrl(rawUrl)
                require(url.isNotBlank()) { "请输入线路地址" }
                require(target.lines.none { it.url == url }) { "这条线路已经添加过了" }

                val probe = target.copy(lines = target.lines + ServerLine("probe", name, url))
                    .withLine("probe")
                // 探活：能拉到媒体库列表就认为线路可用
                BackendFactory.create(probe).libraries()

                val line = ServerLine(id = "line-" + UUID.randomUUID().toString().take(8), name = name.ifBlank { url }, url = url)
                val next = target.addLine(line)
                Graph.saveServer(next)
                if (serverId == activeId) backend = Graph.backend()
                servers = Graph.servers()
                next
            }
        }

    /** 给服务器加一个登录账号。用该服务器当前线路登录，成功后把 token 记进目录。 */
    suspend fun addUser(serverId: String, username: String, password: String): Result<ServerConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = servers.firstOrNull { it.id == serverId } ?: error("服务器不存在")
                require(username.isNotBlank()) { "请输入用户名" }
                require(target.users.none { it.username.equals(username.trim(), ignoreCase = true) }) {
                    "这个账号已经登录过了"
                }
                val authed = BackendFactory.create(target).authenticate(username.trim(), password)
                val user = ServerUser(
                    id = "user-" + UUID.randomUUID().toString().take(8),
                    username = authed.username.ifBlank { username.trim() },
                    token = authed.token,
                    userId = authed.userId,
                )
                val next = target.addUser(user).copy(name = target.name)
                Graph.saveServer(next)
                if (serverId == activeId) backend = Graph.backend()
                servers = Graph.servers()
                next
            }
        }

    fun removeLine(serverId: String, lineId: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val next = target.removeLine(lineId)
        Graph.saveServer(next)
        if (serverId == activeId) backend = Graph.backend()
        servers = Graph.servers()
    }

    fun removeUser(serverId: String, userId: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val next = target.removeUser(userId)
        Graph.saveServer(next)
        if (serverId == activeId) backend = Graph.backend()
        servers = Graph.servers()
    }

    fun updateIcon(serverId: String, icon: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        update(target.copy(iconUrl = icon.trim()))
    }

    fun renameServer(serverId: String, name: String) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        update(target.copy(name = name.trim().ifBlank { target.name }))
    }

    // ------------------------------------------------------------ 媒体库偏好

    fun setLibraryHidden(serverId: String, libraryId: String, hidden: Boolean) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val hiddenSet = target.hiddenLibraries.toMutableSet()
        if (hidden) hiddenSet += libraryId else hiddenSet -= libraryId
        update(target.copy(hiddenLibraries = hiddenSet.toList()))
    }

    /** 移动媒体库顺序：delta = -1 上移，+1 下移。 */
    fun moveLibrary(serverId: String, allIds: List<String>, libraryId: String, delta: Int) {
        val target = servers.firstOrNull { it.id == serverId } ?: return
        val order = (target.libraryOrder + allIds.filterNot { it in target.libraryOrder }).toMutableList()
        val index = order.indexOf(libraryId)
        if (index < 0) return
        val newIndex = (index + delta).coerceIn(0, order.lastIndex)
        if (newIndex == index) return
        order.removeAt(index)
        order.add(newIndex, libraryId)
        update(target.copy(libraryOrder = order))
    }

    /** 按用户配置的顺序 + 可见性整理媒体库列表。 */
    fun arrangeLibraries(serverId: String, libraries: List<com.rainplayer.data.Library>): List<com.rainplayer.data.Library> {
        val target = servers.firstOrNull { it.id == serverId } ?: return libraries
        val visible = libraries.filterNot { it.id in target.hiddenLibraries }
        if (target.libraryOrder.isEmpty()) return visible
        val rank = target.libraryOrder.withIndex().associate { (i, id) -> id to i }
        return visible.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }
}
