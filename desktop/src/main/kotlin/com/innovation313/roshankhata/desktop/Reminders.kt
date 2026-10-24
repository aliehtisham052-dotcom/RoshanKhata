package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.awt.SystemTray
import java.awt.TrayIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The phone's ReminderWorker on the computer (20 Oct): once a day, what the
 * bell holds (cheques due, plans whose day has come, stock expiring, bills)
 * and a backup older than a week are said out loud as a Windows
 * notification from the tray, so the owner hears of them without opening
 * the bell. One notification per day, never one per item; nothing when
 * there is nothing to say. Everything is computed from the local book;
 * no network. On a computer without a tray (rare on Windows) it says nothing.
 */
object Reminders {

    const val BACKUP_WEEK_MS = 7L * 24 * 60 * 60 * 1000

    private var tray: TrayIcon? = null

    /** The tray icon, made on first use and kept while the app runs. Null where Windows gives no tray. */
    private fun icon(title: String): TrayIcon? {
        tray?.let { return it }
        if (!SystemTray.isSupported()) return null
        return runCatching {
            val img = Reminders::class.java.getResourceAsStream("/roshan_khata_icon.png")!!.use { ImageIO.read(it) }
            TrayIcon(img, title).apply { isImageAutoSize = true }
                .also { SystemTray.getSystemTray().add(it); tray = it }
        }.getOrNull()
    }

    /** Takes the icon off the tray; called when the window closes so nothing of the app lingers. */
    fun remove() {
        tray?.let { t -> runCatching { SystemTray.getSystemTray().remove(t) } }
        tray = null
    }

    /** A Windows notification [title] / [text]. */
    fun show(appName: String, title: String, text: String) {
        val t = icon(appName) ?: return
        java.awt.EventQueue.invokeLater { runCatching { t.displayMessage(title, text, TrayIcon.MessageType.INFO) } }
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    /**
     * Today's reminder, once: the alerts' titles in one notification, and
     * the backup nudge in another when the newest copy is over a week old
     * (or there is none). [prefs] remembers the day so a second opening
     * of the app the same day says nothing again.
     */
    fun daily(prefs: AppPrefs, words: Words, alerts: List<LedgerAlerts.Alert>, lastBackupAt: Long?) {
        val day = today()
        if (prefs.remindedDay() == day) return
        val appName = words.text(W.app_name)
        var said = false
        if (alerts.isNotEmpty()) {
            val lines = alerts.take(4).joinToString("\n") { it.title }
            val more = if (alerts.size > 4) "\n+${alerts.size - 4}" else ""
            show(appName, words.text(W.home_today), lines + more)
            said = true
        }
        val old = lastBackupAt == null || System.currentTimeMillis() - lastBackupAt > BACKUP_WEEK_MS
        if (old) {
            show(appName, words.text(W.notif_backup_title), words.text(W.notif_backup_body))
            said = true
        }
        if (said) prefs.setRemindedDay(day)
    }
}
