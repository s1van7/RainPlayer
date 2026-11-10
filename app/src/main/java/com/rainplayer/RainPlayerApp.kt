package com.rainplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.rainplayer.core.CrashReporter
import com.rainplayer.core.Http
import com.rainplayer.core.Prefs
import com.rainplayer.data.BackendFactory
import com.rainplayer.data.FailoverBackend
import com.rainplayer.data.MediaBackend
import com.rainplayer.data.ServerConfig
import com.rainplayer.ui.theme.AppTheme
import com.rainplayer.ui.theme.ThemeState

/**
 * 极简服务定位器：只有一个「活动服务器会话」，用全局单例比引入 DI 框架更轻，
 * 也能避免 API 21 设备上注解处理器的额外方法数。
 *
 * 多服务器的区别在于：服务器列表全量存在 [Prefs] 里，但同一时刻只有一个「活动」的，
 * 聚合搜索才会临时实例化其它服务器的后端。
 */
object Graph {

    lateinit var app: Application
        private set

    lateinit var prefs: Prefs
        private set

    @Volatile
    private var backendCache: MediaBackend? = null

    fun init(application: Application) {
        app = application
        prefs = Prefs(application)
        ThemeState.apply(
            AppTheme.of(prefs.themeKey),
            prefs.glassEffect,
            com.rainplayer.ui.theme.ThemeMode.of(prefs.themeMode),
        )
        migrate()
    }

    /** 老版本只存单 url / 单 token，启动时折叠成线路与账号目录。 */
    private fun migrate() {
        val raw = prefs.servers
        if (raw.isEmpty()) return
        val normalized = raw.map { it.normalize() }
        if (normalized != raw) prefs.servers = normalized
    }

    val deviceId: String get() = prefs.deviceId

    fun servers(): List<ServerConfig> = prefs.servers

    fun activeConfig(): ServerConfig? {
        val id = prefs.activeServerId ?: return null
        return prefs.servers.firstOrNull { it.id == id }
    }

    /** 当前活动服务器对应的后端；服务器 / 线路 / 账号切换时自动重建。 */
    fun backend(): MediaBackend? {
        val config = activeConfig() ?: return null
        val cached = backendCache
        if (cached != null &&
            cached.config.id == config.id &&
            cached.config.url == config.url &&
            cached.config.token == config.token
        ) {
            return cached
        }
        // 包一层自动切换线路：连不上就换下一条，切换结果落盘
        val created = FailoverBackend(config) { updated -> saveServer(updated, invalidate = false) }
        backendCache = created
        return created
    }

    fun invalidateBackend() {
        backendCache = null
        Http.authInterceptor.clear()
    }

    /**
     * 保存一台服务器。
     *
     * @param invalidate 是否同时让后端缓存失效。线路自动切换时传 false，
     *   否则会把正在使用的后端对象丢掉。
     */
    fun saveServer(config: ServerConfig, invalidate: Boolean = true) {
        val normalized = config.normalize()
        val list = prefs.servers.toMutableList()
        val index = list.indexOfFirst { it.id == normalized.id }
        if (index >= 0) list[index] = normalized else list.add(normalized)
        prefs.servers = list
        if (invalidate) invalidateBackend()
    }

    fun deleteServer(id: String) {
        val remaining = prefs.servers.filterNot { it.id == id }
        prefs.servers = remaining
        if (prefs.activeServerId == id) {
            prefs.activeServerId = remaining.firstOrNull()?.id
            invalidateBackend()
        }
    }
}

class RainPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // 最先装崩溃捕获：后面任何初始化出问题都能留下日志
        CrashReporter.install(this)
        Graph.init(this)
        // 预加载活动服务器的鉴权头，保证图片能直接加载
        Graph.backend()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Http.imageClient }
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.22)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(320L * 1024 * 1024)
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(false)
        .build()
}
