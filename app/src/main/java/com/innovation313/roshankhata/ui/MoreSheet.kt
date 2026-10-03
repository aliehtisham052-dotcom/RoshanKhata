package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.AboutActivity
import com.innovation313.roshankhata.DuplicateCustomersActivity
import com.innovation313.roshankhata.HelpActivity
import com.innovation313.roshankhata.LanguageActivity
import com.innovation313.roshankhata.ProductsActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode

/**
 * The one "More" list (2 Oct). Home and the Khata screen each had their own
 * copy, and the two drifted: Home lost Products & stock, Khata never got
 * Text size or Theme. Both now open this, so a setting can never again be
 * reachable from one bar and missing from the other.
 */
object MoreSheet {

    fun show(activity: Activity) {
        val viewer = com.innovation313.roshankhata.data.ViewerMode.isOn(activity)
        val items = listOfNotNull<Pair<Int, () -> Unit>>(
            R.string.app_lock to { appLock(activity) },
            R.string.screen_privacy to { ScreenPrivacyDialog.show(activity) },
            R.string.products_stock to { activity.startActivity(Intent(activity, ProductsActivity::class.java)) },
            // Merging customers rewrites the book; a read-only phone has no book to rewrite.
            if (viewer) null else R.string.duplicate_customers to { activity.startActivity(Intent(activity, DuplicateCustomersActivity::class.java)) },
            // The helper's phone: the owner sends a copy from here, and a helper's
            // phone becomes read-only (or stops being) from here too.
            R.string.viewer_menu to { activity.startActivity(Intent(activity, com.innovation313.roshankhata.ViewerActivity::class.java)) },
            R.string.language to { activity.startActivity(Intent(activity, LanguageActivity::class.java)) },
            R.string.text_size to { textSize(activity) },
            R.string.theme to { theme(activity) },
            // Reporting a problem lives inside Help, one door for "something is wrong".
            R.string.help_support to { activity.startActivity(Intent(activity, HelpActivity::class.java)) },
            R.string.about_us to { activity.startActivity(Intent(activity, AboutActivity::class.java)) }
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.more_title)
            .setItems(items.map { activity.getString(it.first) }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    /**
     * The owner's own text size (see [TextSize]). This screen is rebuilt now;
     * every other open screen rebuilds itself when it comes back to the front
     * (BaseActivity compares the size it was built at).
     */
    private fun textSize(activity: Activity) {
        val labels = arrayOf(
            activity.getString(R.string.text_size_small),
            activity.getString(R.string.text_size_normal),
            activity.getString(R.string.text_size_large),
            activity.getString(R.string.text_size_largest)
        )
        val current = TextSize.level(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.text_size)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) {
                    TextSize.setLevel(activity, which)
                    activity.recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Light, dark, or the phone's own (see [ThemeMode]); setDefaultNightMode rebuilds open screens itself. */
    private fun theme(activity: Activity) {
        val labels = arrayOf(
            activity.getString(R.string.theme_light),
            activity.getString(R.string.theme_dark),
            activity.getString(R.string.theme_system)
        )
        val current = ThemeMode.get(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) ThemeMode.set(activity, which)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun appLock(activity: Activity) {
        if (AppLock.noneEnrolled(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_no_screen_lock)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        if (!AppLock.isAvailable(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_unavailable)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val enabled = AppLock.isEnabled(activity)
        val status = activity.getString(if (enabled) R.string.app_lock_enabled else R.string.app_lock_disabled)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_lock)
            .setMessage(status + "\n\n" + activity.getString(R.string.app_lock_explain))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (enabled) R.string.app_lock_turn_off else R.string.app_lock_turn_on) { _, _ ->
                AppLock.setEnabled(activity, !enabled)
                Toast.makeText(
                    activity,
                    if (!enabled) R.string.app_lock_enabled else R.string.app_lock_disabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }
}
