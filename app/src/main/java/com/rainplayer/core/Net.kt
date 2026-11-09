package com.rainplayer.core

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, val body: String?, message: String) : IOException(message)

/**
 * 按「当前服务器」自动注入鉴权头的拦截器。
 *
 * Emby / Jellyfin 用 X-Emby-Token，飞牛影视用 Authorization + Cookie + Authx 签名，
 * 这里只处理前两者的静态头；飞牛的 Authx 是逐请求签名，由 FnOsBackend 自行拼装。
 * 只对匹配 host 的请求注入，避免把 token 泄漏给第三方图片/弹幕域名。
 */
class ServerAuthInterceptor : okhttp3.Interceptor {

    @Volatile
    var host: String = ""

    @Volatile
    var headers: Map<String, String> = emptyMap()

    fun update(serverUrl: String, newHeaders: Map<String, String>) {
        // 只比 host 不够：同一台机器上可能跑着多个服务器（不同端口），
        // 把端口带上才能保证不会把 A 服务器的 token 发给 B。
        host = runCatching {
            val uri = java.net.URI(serverUrl)
            val h = uri.host ?: ""
            val p = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            "$h:$p"
        }.getOrDefault("")
        headers = newHeaders
    }

    fun clear() {
        host = ""
        headers = emptyMap()
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (host.isEmpty() || request.url.host.isEmpty() || headers.isEmpty()) {
            return chain.proceed(request)
        }
        val requestHost = "${request.url.host}:${request.url.port}"
        if (requestHost != host) {
            return chain.proceed(request)
        }
        val builder = request.newBuilder()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return chain.proceed(builder.build())
    }
}

object Http {

    val authInterceptor = ServerAuthInterceptor()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .build()

    /** 图片专用：更长的读超时、复用连接池。 */
    val imageClient: OkHttpClient = client.newBuilder()
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** 长视频播放：禁用读超时（边下边播可能长时间无数据返回）。 */
    val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val userAgent: String =
        "RainPlayer/1.0 (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url).get().applyHeaders(headers).build())

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).get().applyHeaders(headers).build())
                .execute().use { response ->
                    if (!response.isSuccessful) {
                        throw HttpException(response.code, null, "HTTP ${response.code}")
                    }
                    response.body?.bytes() ?: ByteArray(0)
                }
        }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMedia))
                .applyHeaders(headers)
                .build()
        )

    suspend fun deleteJson(url: String, body: String?, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder()
                .url(url)
                .delete(body?.toRequestBody(jsonMedia))
                .applyHeaders(headers)
                .build()
        )

    suspend fun putJson(url: String, body: String?, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder()
                .url(url)
                .put((body ?: "").toRequestBody(jsonMedia))
                .applyHeaders(headers)
                .build()
        )

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        header("User-Agent", userAgent)
        header("Accept", "application/json")
        headers.forEach { (k, v) -> header(k, v) }
        return this
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful) {
                throw HttpException(
                    response.code,
                    text?.take(400),
                    "HTTP ${response.code} ${request.url.encodedPath}"
                )
            }
            text ?: ""
        }
    }
}
