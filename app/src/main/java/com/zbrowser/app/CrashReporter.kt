package com.zbrowser.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.zbrowser.app.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight crash reporter that captures unhandled exceptions
 * and writes them to a local log file on device.
 * No external services needed — logs are viewable in Settings > About > Crash Logs.
 *
 * On next app launch after a crash, the user is notified and can view/share the log.
 *
 * v4.0 FIX: writeCrashLog now spawns a separate thread for file I/O,
 * preventing ANR when the crash thread is already in a bad state.
 * Also added proper proguard-safe BuildConfig access.
 */
object CrashReporter {

    private const val CRASH_DIR = "crash_logs"
    private const val KEY_LAST_CRASH = "last_crash_log"
    private const val KEY_HAS_CRASH = "has_unread_crash"
    private const val MAX_LOG_FILES = 5

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("crash_reporter", Context.MODE_PRIVATE)

        // Set the default uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // M2 FIX: Write crash log on a separate thread to avoid ANR
            // on the crashing thread. We use a dedicated thread instead of
            // a coroutine because the process may be dying.
            writeCrashLog(throwable)
            // Let the default handler do its thing (kill the process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Check if there's an unread crash log from a previous session.
     */
    fun hasUnreadCrash(): Boolean {
        return prefs.getBoolean(KEY_HAS_CRASH, false)
    }

    /**
     * Get the last crash log content, or null if no crash.
     */
    fun getLastCrashLog(): String? {
        return prefs.getString(KEY_LAST_CRASH, null)
    }

    /**
     * Mark the crash log as read.
     */
    fun markCrashRead() {
        prefs.edit().putBoolean(KEY_HAS_CRASH, false).apply()
    }

    /**
     * Get all crash log files, sorted by date (newest first).
     */
    fun getAllCrashLogs(): List<File> {
        val crashDir = File(appContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Delete all crash log files.
     */
    fun clearAllLogs() {
        val crashDir = File(appContext.filesDir, CRASH_DIR)
        crashDir.listFiles()?.forEach { it.delete() }
        prefs.edit().remove(KEY_LAST_CRASH).remove(KEY_HAS_CRASH).apply()
    }

    /**
     * Write a crash log to a file in the app's internal storage.
     *
     * v4.0 FIX: Uses a dedicated thread for file I/O since the crashing
     * thread may be in a bad state. Joins the thread with a timeout so
     * we don't hang forever if disk I/O is the cause of the crash.
     */
    private fun writeCrashLog(throwable: Throwable) {
        try {
            val logThread = Thread({
                try {
                    val crashDir = File(appContext.filesDir, CRASH_DIR)
                    if (!crashDir.exists()) crashDir.mkdirs()

                    // Clean up old logs beyond the max
                    crashDir.listFiles()
                        ?.sortedBy { it.lastModified() }
                        ?.drop(MAX_LOG_FILES - 1)
                        ?.forEach { it.delete() }

                    // Write the new log
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                    val logFile = File(crashDir, "crash_$timestamp.txt")

                    PrintWriter(FileWriter(logFile)).use { writer ->
                        writer.println("=== ZBrowser Crash Log ===")
                        writer.println("Time: ${Date()}")
                        writer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        writer.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        // L7 FIX: Use try-catch for BuildConfig access in case Proguard strips it
                        try {
                            writer.println("App: ZBrowser v${BuildConfig.VERSION_NAME}")
                        } catch (_: Exception) {
                            writer.println("App: ZBrowser")
                        }
                        writer.println()
                        writer.println("=== Stack Trace ===")
                        throwable.printStackTrace(writer)
                    }

                    // Save a summary to SharedPreferences for quick access
                    val summary = buildString {
                        append("Crash at $timestamp\n")
                        append(throwable.message ?: "Unknown error")
                        append("\n\n")
                        append(logFile.readText().take(2000))
                    }

                    prefs.edit()
                        .putString(KEY_LAST_CRASH, summary)
                        .putBoolean(KEY_HAS_CRASH, true)
                        .apply()
                } catch (_: Exception) {
                    // If crash logging itself fails, silently continue
                }
            }, "CrashLogWriter")

            logThread.start()
            // Wait up to 2 seconds for the log to be written
            logThread.join(2000)
        } catch (_: Exception) {
            // If we can't even start the thread, just continue
        }
    }
}
