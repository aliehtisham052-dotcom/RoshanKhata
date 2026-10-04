package com.innovation313.roshankhata.data

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager

/**
 * Keeping the ledger off every screen that is not this one.
 *
 * App Lock guards the front door. It does not guard the window: Android keeps a
 * live thumbnail of the last screen for the recent-apps switcher, and that
 * thumbnail is shown WITHOUT any unlock. So a locked app still hands over the
 * customer list, balances and all, to anyone who presses the square button.
 *
 * [BalancePrivacy] already says why this matters — the net balance is legible
 * from across a counter and is nobody's business but the owner's. A lock and a
 * mask on the front of the app, with the whole book sitting in the switcher
 * behind it, is a lock that only looks like a lock.
 *
 * TWO LAYERS, DELIBERATELY SEPARATE, BECAUSE THEY COST DIFFERENT THINGS:
 *
 *  1. [hideFromRecents] — always on, never a setting. From Android 13 the
 *     system will skip making the switcher thumbnail at all if asked, and this
 *     does NOT stop the owner taking a screenshot while they are using the app.
 *     It closes the leak that nobody chose and costs the owner nothing, so
 *     there is nothing to ask them about.
 *
 *  2. [applyTo] / FLAG_SECURE — a setting, and OFF unless the owner turns it
 *     on. This one is absolute: no screenshot, no screen recording, blank in
 *     the switcher on every Android version. It is the only thing that covers
 *     Android 12 and older, where layer 1 does not exist. But it also stops
 *     the OWNER photographing a customer's ledger to send it on, which is a
 *     real thing a shopkeeper does every day. A privacy default that quietly
 *     breaks the daily work is not a good default; it is one the owner will
 *     turn off in irritation and never look at again.
 *
 * So the leak that has no cost is closed for everyone, and the protection that
 * has a cost is offered, explained, and left to the owner.
 *
 * What the switcher shows INSTEAD of the screen is this file's business too:
 * see [plainCard].
 */
object ScreenSecurity {

    private const val PREFS = "screen_security"
    private const val KEY_BLOCKED = "screens_blocked"

    /** Off unless the owner has deliberately turned it on. */
    fun isBlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCKED, false)

    fun setBlocked(context: Context, blocked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCKED, blocked)
            .apply()
    }

    /** True where layer 1 exists, so the dialog can tell the owner the truth. */
    val recentsCanBeHidden: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Ask the system not to keep a picture of this screen for the switcher.
     *
     * Android 13 and later only. On older phones the switcher thumbnail can be
     * stopped by nothing short of FLAG_SECURE, which is why the setting exists.
     */
    fun hideFromRecents(activity: Activity) {
        // The check is written out here rather than read from
        // [recentsCanBeHidden], because lint recognises this exact shape as a
        // version guard and would otherwise fail the build on an API 33 call
        // in a minSdk 24 app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }

    /**
     * The card the switcher shows in place of this screen: one plain page.
     *
     * With the thumbnail switched off ([hideFromRecents]) the system does not
     * leave the card empty. It draws a stand-in from what the app's theme
     * says about itself: the page colour, and across the top a strip in the
     * theme's STATUS BAR colour. This app's status bar is the dark green of
     * its section header, so every other app in the switcher showed a clean
     * white card and this one a white card with a green band along its top
     * edge, which reads as a fault (the owner's report, 4 Oct).
     *
     * So the system is told, for the card only, that the two bars are the
     * page's own colour: white by day, the dark page by night, the same as
     * the rest of the stand-in. The real status bar on the screen is not
     * touched; this is the task's description of itself, not the window.
     *
     * Android 13 and later, like the stand-in itself. Called as each screen
     * is created and again as it resumes: a screen that sets its own bar
     * colours in onCreate (the language and welcome screens) resets this
     * description when it does, and the resume puts it back before the
     * switcher can ask for the card.
     */
    fun plainCard(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The colour the stand-in's page is filled with: the theme's own
            // background, which is where the system takes it from too.
            val page = opaqueThemeColor(activity, android.R.attr.colorBackground) ?: return
            val description = ActivityManager.TaskDescription.Builder()
                .setStatusBarColor(page)
                .setNavigationBarColor(page)
            // Setting a description replaces its primary colour as well, and
            // an unset one would wipe what the theme gave. Hand it back as is.
            opaqueThemeColor(activity, android.R.attr.colorPrimary)
                ?.let { description.setPrimaryColor(it) }
            activity.setTaskDescription(description.build())
        }
    }

    /** A colour attribute of the screen's theme, or null unless it is a solid colour. */
    private fun opaqueThemeColor(activity: Activity, attr: Int): Int? {
        val value = TypedValue()
        if (!activity.theme.resolveAttribute(attr, value, true)) return null
        if (value.type < TypedValue.TYPE_FIRST_COLOR_INT || value.type > TypedValue.TYPE_LAST_COLOR_INT) return null
        return value.data.takeIf { Color.alpha(it) == 0xFF }
    }

    /**
     * Put the window into the state the setting asks for.
     *
     * Called on every activity as it is created AND again as it resumes. The
     * second call is what makes the switch take effect immediately: an activity
     * created while the flag was on is still sitting in memory when the owner
     * turns it off, and it clears the flag the moment it comes back to front.
     *
     * Adding and clearing a window flag is idempotent, so calling this on every
     * resume costs nothing and cannot drift out of step with the setting.
     */
    fun applyTo(activity: Activity) {
        if (isBlocked(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
