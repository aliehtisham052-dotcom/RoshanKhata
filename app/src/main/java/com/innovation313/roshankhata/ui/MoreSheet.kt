package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.AboutActivity
import com.innovation313.roshankhata.DuplicateCustomersActivity
import com.innovation313.roshankhata.HelpActivity
import com.innovation313.roshankhata.LanguageActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode

/**
 * The one "More" list (2 Oct). Home and the Khata screen each had their own
 * copy, and the two drifted: Home lost Products & stock, Khata never got
 * Text size or Theme. Both now open this, so a setting can never again be
 * reachable from one bar and missing from the other. Since 7 Oct it is the
 * ⋮ menu beside the bell (showMenu); the drawer keeps only the features.
 */
object MoreSheet {

    /** One door of the More list: its icon (a Home-tile duotone), the disc's tint, its name, and what it opens. */
    class Entry(val icon: Int, val tint: Int, val label: Int, val open: () -> Unit)

    /** The settings and doors, in order; the ⋮ menu on Home and Khata lists the same (7 Oct). */
    fun entries(activity: Activity): List<Entry> {
        val viewer = com.innovation313.roshankhata.data.ViewerMode.isOn(activity)
        fun e(icon: Int, tint: Int, label: Int, open: () -> Unit) = Entry(icon, tint, label, open)
        return listOfNotNull(
            e(R.drawable.ic_menu_lock, R.color.tile_bills_bg, R.string.app_lock) { appLock(activity) },
            e(R.drawable.ic_menu_privacy, R.color.tile_cheques_bg, R.string.screen_privacy) { ScreenPrivacyDialog.show(activity) },
            // Products & stock is a Home tile and a drawer row; a third door
            // here was the one duplicate the owner pointed at (7 Oct).
            // Merging customers rewrites the book; a read-only phone has no book to rewrite.
            if (viewer) null else e(R.drawable.ic_menu_duplicate, R.color.tile_card_bg, R.string.duplicate_customers) { activity.startActivity(Intent(activity, DuplicateCustomersActivity::class.java)) },
            // The helper's phone: the owner sends a copy from here, and a helper's
            // phone becomes read-only (or stops being) from here too.
            e(R.drawable.ic_menu_viewer, R.color.tile_insights_bg, R.string.viewer_menu) { activity.startActivity(Intent(activity, com.innovation313.roshankhata.ViewerActivity::class.java)) },
            e(R.drawable.ic_menu_language, R.color.tile_khata_bg, R.string.language) { activity.startActivity(Intent(activity, LanguageActivity::class.java)) },
            e(R.drawable.ic_menu_textsize, R.color.tile_invoice_bg, R.string.text_size) { textSize(activity) },
            e(R.drawable.ic_menu_theme, R.color.tile_card_bg, R.string.theme) { theme(activity) },
            // Reporting a problem lives inside Help, one door for "something is wrong".
            e(R.drawable.ic_menu_help, R.color.tile_insights_bg, R.string.help_support) { activity.startActivity(Intent(activity, HelpActivity::class.java)) },
            e(R.drawable.ic_menu_about, R.color.tile_cheques_bg, R.string.about_us) { activity.startActivity(Intent(activity, AboutActivity::class.java)) }
        )
    }

    /**
     * The ⋮ menu (7 Oct): the same list as a popup under the three dots in
     * the header, each row an icon disc and a name, like the drawer's rows.
     * [homeFirst] puts a "Home" row at the top — the Khata screen's way home
     * once its bottom bar is gone.
     */
    fun showMenu(activity: Activity, anchor: android.view.View, homeFirst: Boolean = false) {
        val items = ArrayList<Entry>()
        if (homeFirst) items.add(Entry(R.drawable.ic_menu_home, R.color.tile_khata_bg, R.string.nav_home) {
            activity.startActivity(
                Intent(activity, com.innovation313.roshankhata.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        })
        items.addAll(entries(activity))
        val dp = activity.resources.displayMetrics.density
        val popup = androidx.appcompat.widget.ListPopupWindow(activity)
        popup.anchorView = anchor
        popup.width = (236 * dp).toInt()
        popup.isModal = true
        popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_more_menu))
        popup.setAdapter(object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(i: Int) = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = convert ?: android.view.LayoutInflater.from(activity).inflate(R.layout.item_more_row, parent, false)
                val e = items[i]
                v.findViewById<android.widget.ImageView>(R.id.ivMoreIcon).setImageResource(e.icon)
                v.findViewById<android.widget.FrameLayout>(R.id.moreIconDisc).backgroundTintList =
                    android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(activity, e.tint))
                v.findViewById<android.widget.TextView>(R.id.tvMoreLabel).setText(e.label)
                return v
            }
        })
        popup.setOnItemClickListener { _, _, i, _ -> popup.dismiss(); items[i].open() }
        popup.show()
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
