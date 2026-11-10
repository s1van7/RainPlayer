package com.rainplayer.core

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃自检与反馈。
 *
 * 任何未捕获异常都会先落一份日志到应用私有目录，下次启动时由 UI 弹窗提示用户，
 * 用户同意后再把日志复制 / 分享出去。
 *
 * 日志里只有版本号、机型、系统版本和堆栈，**不含服务器地址、账号、token 或任何播放记录**。
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val FILE = "last.txt"
    private const val MAX_CHARS = 60_000

    /** 在 Application.onCreate 里调用一次。 */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** 有没有待处理的崩溃日志。 */
    fun pending(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        return text?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** 复制到剪贴板，用户可以直接粘贴给我们。 */
    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("RainPlayer 崩溃报告", text))
    }

    /** 拉起系统分享面板。 */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "RainPlayer 崩溃报告")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "发送崩溃报告")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    /** 给用户看的摘要：异常类型 + 第一行消息。 */
    fun summary(text: String): String {
        val line = text.lineSequence()
            .firstOrNull { it.startsWith("异常: ") }
            ?.removePrefix("异常: ")
            ?: "未知错误"
        return line.take(160)
    }

    private fun file(context: Context): File =
        File(File(context.filesDir, DIR), FILE)

    private fun write(app: Application, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        PrintWriter(stack).use { error.printStackTrace(it) }

        val header = buildString {
            appendLine("RainPlayer 崩溃报告")
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("版本: ${versionName(app)} (${versionCode(app)})")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(
                "系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / " +
                    (Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
            )
            appendLine("线程: ${thread.name}")
            appendLine("异常: ${error.javaClass.name}: ${error.message}")
            appendLine()
        }

        val dir = File(app.filesDir, DIR).apply { runCatching { mkdirs() } }
        File(dir, FILE).writeText((header + stack.toString()).take(MAX_CHARS))
    }

    private fun versionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun versionCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)
}
