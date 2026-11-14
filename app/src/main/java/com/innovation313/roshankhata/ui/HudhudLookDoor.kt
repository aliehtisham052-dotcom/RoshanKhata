package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.innovation313.roshankhata.data.HudhudLook
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode
import java.lang.ref.WeakReference

/**
 * The one door through which Hudhud changes a setting of the phone (14 Nov 2026, the owner's tiers: the text size and the
 * theme are the "look" he may change at the owner's word, with an Undo; every other setting he only shows, opens or says
 * where it is). Nothing else of Hudhud's writes a setting: `scripts/check_hudhud_readonly.py` refuses it in every other
 * file of his, so this file is the whole of what he can change.
 *
 * Both changes rebuild the screen the sheet is over — a text size is read when a screen is built, and a theme that turns
 * the screen from day to night is rebuilt by AppCompat — and a rebuilt screen takes its open sheet with it. So the sheet
 * does not show the card of a change it is about to make: it makes the change ([apply]) and leaves a short note; the
 * screen that comes back out of the rebuild finds the note ([resume], called by every screen's onResume) and opens his
 * sheet again with the card that says what changed and its Undo. A change that does not rebuild (the theme from "Same as
 * phone" to the one the phone is in) is shown at once by the sheet that made it.
 *
 * The note lives in memory only, for a few seconds: it is never written down, never in the book, a backup or a sync.
 */
object HudhudLookDoor {

    /** What a sheet opened again after the rebuild shows: the change that was made, and the words that asked for it. */
    class Reopen(val made: HudhudLook.Made, val heard: String, val speak: Boolean)

    private class Note(
        val made: HudhudLook.Made, val heard: String, val speak: Boolean, val screen: String,
        val host: WeakReference<Activity>, val at: Long
    )

    /** The note of the change just made, until the rebuilt screen has taken it or [FRESH_MS] has gone. */
    private var note: Note? = null

    private val main = Handler(Looper.getMainLooper())

    /** How long after a change a rebuilt screen may still show it (a slow phone rebuilds slowly). */
    private const val FRESH_MS = 10_000L

    /** How long the sheet that made the change waits for a rebuild before it shows the card itself. */
    private const val WAIT_MS = 1_500L

    /**
     * Makes [made] on [host], the screen his sheet is over. [heard] is what the owner said (the new sheet writes it as
     * "You asked"); [speak] is whether that sheet was allowed to talk (a silent one stays silent after the rebuild);
     * [inPlace] shows the card on the sheet that is still open, called only when the screen does not rebuild.
     */
    fun apply(host: Activity, made: HudhudLook.Made, heard: String, speak: Boolean, inPlace: () -> Unit) {
        val mine = Note(made, heard, speak, host.javaClass.name, WeakReference(host), SystemClock.elapsedRealtime())
        note = mine
        // Whether this screen is rebuilt is known only before the change: it is what the change does to the screen.
        val rebuilds = made.setting == HudhudLook.Setting.SIZE || night(made.from) != night(made.to)
        when (made.setting) {
            HudhudLook.Setting.SIZE -> {
                TextSize.setLevel(host, made.to)
                host.recreate()
            }
            HudhudLook.Setting.THEME -> ThemeMode.set(host, made.to)
        }
        if (!rebuilds) inPlace()
        // Whatever the screen did, the note is gone after a moment; a rebuild that never came is shown here after all.
        main.postDelayed({
            if (note !== mine) return@postDelayed
            note = null
            val screen = mine.host.get()
            if (rebuilds && screen != null && !screen.isFinishing && !screen.isDestroyed) inPlace()
        }, WAIT_MS)
    }

    /**
     * Called from every screen's onResume: when this screen is the one a change just rebuilt (the same kind of screen,
     * built anew, the one before gone), his sheet is opened on it with the card of that change.
     */
    fun resume(screen: Activity) {
        val mine = note ?: return
        if (SystemClock.elapsedRealtime() - mine.at > FRESH_MS) {
            note = null
            return
        }
        if (screen.javaClass.name != mine.screen || screen.isFinishing || screen.isDestroyed) return
        // The screen the change was made on is still there (the phone came back to it): nothing was rebuilt.
        val before = mine.host.get()
        if (before === screen || (before != null && !before.isDestroyed && !before.isFinishing)) return
        note = null
        val again = Reopen(mine.made, mine.heard, mine.speak)
        screen.window.decorView.post {
            if (!screen.isFinishing && !screen.isDestroyed) HudhudSheet.open(screen, speak = again.speak, reopen = again)
        }
    }

    /**
     * Whether a screen is drawn in its night colours under [mode]: the choice itself, else the phone's own. The one thing
     * AppCompat compares to decide whether to rebuild the open screens when the theme is set.
     */
    private fun night(mode: Int): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
