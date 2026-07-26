package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File

/**
 * When the app dies, the reason is written down — on the phone, nowhere else.
 *
 * "The app closed by itself" is a report that cannot be fixed. The stack trace
 * that was on the screen for a millisecond is the entire difference between
 * guessing and knowing, so it is saved to the app's private files the moment
 * a crash happens, before the process is allowed to end.
 *
 * What this deliberately is NOT: a crash reporter. Nothing is uploaded, ever.
 * The record sits on the phone until the owner opens Report a Problem, where
 * the latest one is placed into the email body they can read in full before
 * sending — the same rule as the rest of that screen: no second, silent
 * channel. An owner who never reports is never phoned home about.
 */
object CrashLog {

    private const val KEEP = 5
    private const val DIR = "crashlogs"

    /** Install once, from the Application. Chains to the system handler. */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context, thread, throwable)
            } catch (_: Exception) {
                // The recorder must never make the crash worse.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, e: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = android.text.format.DateFormat
            .format("yyyyMMdd-HHmmss", java.util.Date()).toString()
        File(dir, "crash-$stamp.txt").writeText(buildString {
            appendLine("Time: $stamp")
            appendLine("Thread: ${thread.name}")
            appendLine("App: ${com.innovation313.roshankhata.BuildConfig.VERSION_NAME} (${com.innovation313.roshankhata.BuildConfig.VERSION_CODE})")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(e))
        })
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /**
     * The newest crash record from the last week, trimmed to fit an email
     * body, or null when there is nothing to tell.
     */
    fun latestText(context: Context, maxChars: Int = 3500): String? {
        val dir = File(context.filesDir, DIR)
        val newest = dir.listFiles()?.maxByOrNull { it.name } ?: return null
        if (System.currentTimeMillis() - newest.lastModified() > 7L * 24 * 60 * 60 * 1000) {
            return null
        }
        val text = runCatching { newest.readText() }.getOrNull() ?: return null
        return if (text.length <= maxChars) text else text.take(maxChars) + "\n… (trimmed)"
    }
}
