package com.lagradost.shiro.utils

import android.content.Context
import android.os.Build
import com.lagradost.shiro.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, always-on (debug AND release) crash / error log.
 *
 * Unlike [com.lagradost.shiro.utils.mvvm.logError], which only prints to Logcat in debug
 * builds, this persists a rolling text log to app-private storage so users can view, save
 * or share it from Settings -> History -> Error log, even in a release build where nobody
 * is attached with a debugger/logcat.
 */
object ErrorLogger {
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "error_log.txt"

    // Keep the log from growing forever; trims oldest entries once exceeded.
    private const val MAX_LOG_SIZE_BYTES = 1_000_000L // ~1 MB

    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                log(throwable, fatal = true)
            } catch (_: Throwable) {
                // Never let the logger itself interfere with the crash handling chain.
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logFile(): File? {
        val context = appContext ?: return null
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE_NAME)
    }

    @Synchronized
    fun log(throwable: Throwable, tag: String? = null, fatal: Boolean = false) {
        try {
            val file = logFile() ?: return
            val header = buildString {
                append("---- ")
                append(dateFormat.format(Date()))
                if (fatal) append(" [FATAL]")
                if (tag != null) append(" [$tag]")
                append(" ----\n")
            }
            file.appendText(header + throwable.stackTraceToString() + "\n\n")
            trimIfNeeded(file)
        } catch (_: Throwable) {
            // A logging failure must never cause a crash of its own.
        }
    }

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_LOG_SIZE_BYTES) return
        val text = file.readText()
        file.writeText(text.takeLast(MAX_LOG_SIZE_BYTES.toInt()))
    }

    fun deviceInfoHeader(): String {
        return "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})\n" +
                "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n"
    }

    fun getLogText(): String {
        val text = try {
            logFile()?.takeIf { it.exists() }?.readText().orEmpty()
        } catch (_: Throwable) {
            ""
        }
        return text.ifBlank { "No errors have been logged yet." }
    }

    fun getLogFile(): File? = logFile()?.takeIf { it.exists() && it.length() > 0 }

    @Synchronized
    fun clear() {
        try {
            logFile()?.let { if (it.exists()) it.writeText("") }
        } catch (_: Throwable) {
        }
    }
}
