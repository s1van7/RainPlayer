package com.rainplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.rainplayer.core.Http
import com.rainplayer.core.Prefs
import com.rainplayer.data.BackendFactory
import com.rainplayer.data.MediaBackend

/**
 * 极简服务定位器：只有一个活动服务器会话，用全局单例比引入 DI 框架更轻，
 * 也能避免 API 21 设备上注解处理器的额外方法数。
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
    }

    val deviceId: String get() = prefs.deviceId

    /** 当前活动服务器对应的后端；服务器切换时自动重建。 */
    fun backend(): MediaBackend? {
        val activeId = prefs.activeServerId ?: return null
        val config = prefs.servers.firstOrNull { it.id == activeId } ?: return null
        val cached = backendCache
        if (cached != null && cached.config.id == config.id && cached.config.token == config.token) {
            return cached
        }
        val created = BackendFactory.create(config)
        backendCache = created
        return created
    }

    fun invalidateBackend() {
        backendCache = null
        Http.authInterceptor.clear()
    }

    fun saveServer(config: com.rainplayer.data.ServerConfig) {
        val list = prefs.servers.toMutableList()
        val index = list.indexOfFirst { it.id == config.id }
        if (index >= 0) list[index] = config else list.add(config)
        prefs.servers = list
    }
}

class RainPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
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
