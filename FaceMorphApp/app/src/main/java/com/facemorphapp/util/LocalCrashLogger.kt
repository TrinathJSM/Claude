package com.facemorphapp.util

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val LOG_FILE = "crash_log.txt"
private const val MAX_LOG_BYTES = 512 * 1024L  // 512 KB — rotate when exceeded

/**
 * Lightweight crash logger that writes stack traces to a local file.
 *
 * Replaces Firebase Crashlytics — nothing leaves the device.
 * The default uncaught exception handler is installed in [Application.onCreate].
 * Crash reports are viewable in Settings > About (tap 7×) and shareable via
 * the system mail Intent (user-initiated only).
 */
@Singleton
class LocalCrashLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logFile = File(context.filesDir, LOG_FILE)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        rotateIfNeeded()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = buildString {
            appendLine("════════════════════════════════")
            appendLine("CRASH  $timestamp")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("App versionCode: ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrElse { "?" }}")
            appendLine()
            appendLine(throwable.stackTraceToString())
        }
        logFile.appendText(entry)
    }

    fun readLog(): String = if (logFile.exists()) logFile.readText() else ""

    fun clearLog() = logFile.delete().let { }

    fun logWarning(tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        logFile.appendText("WARN  [$timestamp] $tag: $message\n")
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > MAX_LOG_BYTES) {
            val backup = File(context.filesDir, "crash_log.bak.txt")
            logFile.copyTo(backup, overwrite = true)
            logFile.delete()
        }
    }
}
