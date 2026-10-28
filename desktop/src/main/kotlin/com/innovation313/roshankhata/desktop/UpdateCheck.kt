package com.innovation313.roshankhata.desktop

import java.net.HttpURLConnection
import java.net.URI

/**
 * Is there a newer Windows build? (20 Oct.) The phone has Play for this;
 * the computer asks GitHub once a day for the small `version.json` that
 * desktop.yml puts beside the MSI on the desktop-test release, and compares
 * it with the version jpackage stamped into this install. Nothing about the
 * owner or the book is sent: a plain GET of a public file, five seconds at
 * most, and a failure is silence until tomorrow. A newer version is a row
 * in the bell that opens the download page in the browser; the app never
 * downloads or installs anything itself.
 */
object UpdateCheck {

    const val VERSION_URL = "https://github.com/aliehtisham052-dotcom/RoshanKhata/releases/download/desktop-test/version.json"
    const val PAGE_URL = "https://github.com/aliehtisham052-dotcom/RoshanKhata/releases/tag/desktop-test"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** The version this install carries (jpackage's), or null when run from Gradle. */
    fun installed(): String? = System.getProperty("jpackage.app-version")?.takeIf { it.isNotBlank() }

    /** "1.8.10" is newer than "1.8.9": each part as a number, missing parts as 0. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The "version" field of a small JSON text, without a JSON library. */
    fun versionIn(json: String): String? =
        Regex("\"version\"\\s*:\\s*\"([0-9][0-9.]*)\"").find(json)?.groupValues?.get(1)

    /** The newest published version, read over the network; null on any trouble. Blocking. */
    fun fetchLatest(url: String = VERSION_URL): String? = runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "RoshanKhata-Windows")
        try {
            if (c.responseCode != 200) return null
            c.inputStream.use { it.readNBytes(4096).toString(Charsets.UTF_8) }.let(::versionIn)
        } finally { c.disconnect() }
    }.getOrNull()

    /**
     * The version to offer the owner, if any: the day's answer from [prefs]
     * when it was asked today, else a fresh one (and remembered). Null when
     * this is a Gradle run, nothing newer exists, or the network said nothing.
     */
    fun newer(prefs: AppPrefs): String? {
        val current = installed() ?: return null
        val now = System.currentTimeMillis()
        val latest = if (now - prefs.updateCheckedAt() < DAY_MS) prefs.updateLatest()
            else fetchLatest()?.also { prefs.setUpdateLatest(it, now) }
        return latest?.takeIf { isNewer(it, current) }
    }

    fun openPage() { runCatching { java.awt.Desktop.getDesktop().browse(URI(PAGE_URL)) } }
}
