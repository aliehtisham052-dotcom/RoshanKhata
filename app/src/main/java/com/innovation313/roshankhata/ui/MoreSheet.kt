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

    /** One door of the More list: its icon (a Home-tile duotone), the disc's tint, its name, the group it sits in on the sheet, and what it opens. */
    class Entry(val icon: Int, val tint: Int, val label: Int, val group: Int, val chevron: Boolean = true, val open: () -> Unit)

    /** The settings and doors, in order; Home's gear and the Khata screen's ⋮ list the same (7 Oct). */
    fun entries(activity: Activity): List<Entry> {
        val viewer = com.innovation313.roshankhata.data.ViewerMode.isOn(activity)
        fun e(icon: Int, tint: Int, label: Int, group: Int, chevron: Boolean = true, open: () -> Unit) = Entry(icon, tint, label, group, chevron, open)
        val security = R.string.settings_group_security
        val data = R.string.settings_group_data
        val display = R.string.settings_group_display
        val help = R.string.settings_group_help
        val shop = R.string.home_sec_business
        return listOfNotNull(
            // The shop's own settings - name, logo, QR, signature, shop type,
            // currency (9 Oct). Until now a Home tile beside a gear that opened
            // this sheet, so "Settings" was in two places meaning two things;
            // it is now the first row of the one settings sheet. A read-only
            // phone cannot change the shop.
            if (viewer && com.innovation313.roshankhata.data.ViewerMode.ownerOnly(com.innovation313.roshankhata.BusinessSettingsActivity::class.java)) null
            // Since 11 Oct it opens the shop sheet, the same one the shop
            // button opens, so whoever looks for the profile in settings
            // lands in the one place it lives.
            else e(R.drawable.ic_tile_settings, R.color.tile_settings_bg, R.string.shop_settings_row, shop) {
                ShopSheet.show(activity)
            },
            e(R.drawable.ic_menu_lock, R.color.tile_bills_bg, R.string.app_lock, security) { appLock(activity) },
            e(R.drawable.ic_menu_privacy, R.color.tile_cheques_bg, R.string.screen_privacy, security) { ScreenPrivacyDialog.show(activity) },
            // Products & stock is a Home tile and a drawer row; a third door
            // here was the one duplicate the owner pointed at (7 Oct).
            // Merging customers rewrites the book; a read-only phone has no book to rewrite.
            if (viewer) null else e(R.drawable.ic_menu_duplicate, R.color.tile_card_bg, R.string.duplicate_customers, data) { activity.startActivity(Intent(activity, DuplicateCustomersActivity::class.java)) },
            // The helper's phone: the owner sends a copy from here, and a helper's
            // phone becomes read-only (or stops being) from here too.
            // The shop's daily work said at its own time (31 Oct 2026); a read-only phone says none of it.
            if (viewer) null else e(R.drawable.ic_menu_bell, R.color.tile_followup_bg, R.string.auto_title, data) { AutomationsDialog.show(activity) },
            // Payment Radar's one switch (2 Nov 2026); a read-only phone never shows the Radar at all.
            if (viewer) null else e(R.drawable.ic_menu_radar, R.color.tile_followup_bg, R.string.radar_title, data) { RadarUi.settings(activity) },
            // Payment voice (5 Nov 2026): a payment a wallet app announced, said aloud and offered to a khata.
            // Not on a read-only phone, which could not add it.
            if (viewer) null else e(R.drawable.ic_menu_paisa, R.color.tile_cashbook_bg, R.string.paisa_title, data) {
                activity.startActivity(Intent(activity, com.innovation313.roshankhata.PaymentVoiceActivity::class.java))
            },
            e(R.drawable.ic_menu_viewer, R.color.tile_insights_bg, R.string.viewer_menu, data) { activity.startActivity(Intent(activity, com.innovation313.roshankhata.ViewerActivity::class.java)) },
            // Waris ki Chaabi (4 Nov 2026): the owner's heirs and the books this phone is heir to; on a read-only phone it only says so.
            e(R.drawable.ic_menu_waris, R.color.tile_bills_bg, R.string.waris_title, data) { activity.startActivity(Intent(activity, com.innovation313.roshankhata.WarisActivity::class.java)) },
            // Mera Khata (7 Nov 2026): the shops' khata links this phone's owner was sent as a customer; shown
            // once there is one. Any phone, a helper's too: it is the holder's own, not the book.
            if (!com.innovation313.roshankhata.MeraKhataActivity.has(activity)) null
            else e(R.drawable.ic_tile_khata, R.color.tile_khata_bg, R.string.mera_khata_title, data) {
                activity.startActivity(Intent(activity, com.innovation313.roshankhata.MeraKhataActivity::class.java))
            },
            e(R.drawable.ic_menu_language, R.color.tile_khata_bg, R.string.language, display) { activity.startActivity(Intent(activity, LanguageActivity::class.java)) },
            e(R.drawable.ic_menu_textsize, R.color.tile_invoice_bg, R.string.text_size, display) { textSize(activity) },
            e(R.drawable.ic_menu_theme, R.color.tile_card_bg, R.string.theme, display) { theme(activity) },
            // Hudhud's morning greeting on Home (12 Nov 2026): its one switch, on this phone.
            e(R.drawable.ic_menu_greeting, R.color.tile_bills_bg, R.string.hudhud_greet_title, display) { HudhudGreet.settings(activity) },
            // Hudhud's voice (13 Nov 2026): his style, the phone's voices, what he calls the owner, how he listens.
            e(R.drawable.ic_menu_hudhud_voice, R.color.tile_insights_bg, R.string.hudhud_voice_title, display) { HudhudVoiceSheet.show(activity) },
            // Reporting a problem lives inside Help, one door for "something is wrong".
            e(R.drawable.ic_menu_help, R.color.tile_insights_bg, R.string.help_support, help) { activity.startActivity(Intent(activity, HelpActivity::class.java)) },
            // Telling another shopkeeper (8 Nov 2026 review, L168): the system share sheet with one line and the tagged Play link.
            e(R.drawable.ic_menu_share, R.color.tile_khata_bg, R.string.share_app, help, chevron = false) { shareApp(activity) },
            e(R.drawable.ic_menu_about, R.color.tile_cheques_bg, R.string.about_us, help) { activity.startActivity(Intent(activity, AboutActivity::class.java)) }
        )
    }

    private fun shareApp(activity: Activity) {
        val link = com.innovation313.roshankhata.data.PdfBranding.storeUrl(activity)
            .replace(com.innovation313.roshankhata.data.PdfBranding.REFERRER_PDF, "&referrer=utm_source%3Dapp%26utm_medium%3Dshare")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.share_app_text, link))
        runCatching { activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_app))) }
    }

    /**
     * The Settings sheet (7 Oct): Home's gear and the Khata screen's ⋮ both
     * open this bottom sheet — the one list above, grouped under Security,
     * Data, Display and Help, each group a paper card of rows with a
     * hairline between them. It replaces the ⋮ popup, ten rows in one
     * unbroken column. [homeFirst] shows a Home pill in the title row — the
     * Khata screen's way home once its bottom bar is gone — rather than a
     * "Home" row sitting among the settings.
     */
    fun showMenu(activity: Activity, anchor: android.view.View, homeFirst: Boolean = false) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val view = android.view.LayoutInflater.from(activity).inflate(R.layout.sheet_settings, null)
        val dp = activity.resources.displayMetrics.density
        val inflater = android.view.LayoutInflater.from(activity)

        val btnHome = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSheetHome)
        if (homeFirst) {
            btnHome.visibility = android.view.View.VISIBLE
            btnHome.setOnClickListener {
                sheet.dismiss()
                activity.startActivity(
                    Intent(activity, com.innovation313.roshankhata.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }

        val groups = view.findViewById<android.widget.LinearLayout>(R.id.settingsGroups)
        // Groups in the order the entries name them; an entry's group is the
        // card it lands in, so a group with no entry (a viewer's phone has no
        // Duplicate customers, but still has Helper's phone) still appears.
        val order = ArrayList<Int>()
        val byGroup = LinkedHashMap<Int, ArrayList<Entry>>()
        for (e in entries(activity)) {
            if (e.group !in byGroup) { byGroup[e.group] = ArrayList(); order.add(e.group) }
            byGroup[e.group]!!.add(e)
        }
        for (g in order) {
            val label = android.widget.TextView(activity).apply {
                setText(g)
                isAllCaps = true
                letterSpacing = 0.08f
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.text_muted))
                setPadding((6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            }
            groups.addView(label)
            val card = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_party_card)
                clipToOutline = true
            }
            val rows = byGroup[g]!!
            rows.forEachIndexed { i, e ->
                val row = inflater.inflate(R.layout.item_more_row, card, false)
                row.findViewById<android.widget.ImageView>(R.id.ivMoreIcon).setImageResource(e.icon)
                row.findViewById<android.widget.FrameLayout>(R.id.moreIconDisc).backgroundTintList =
                    android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(activity, e.tint))
                row.findViewById<android.widget.TextView>(R.id.tvMoreLabel).setText(e.label)
                row.findViewById<android.view.View>(R.id.ivMoreChevron).visibility = if (e.chevron) android.view.View.VISIBLE else android.view.View.GONE
                row.minimumHeight = (52 * dp).toInt()
                row.setOnClickListener { sheet.dismiss(); e.open() }
                card.addView(row)
                if (i < rows.size - 1) {
                    val line = android.view.View(activity).apply {
                        setBackgroundColor(androidx.core.content.ContextCompat.getColor(activity, R.color.page_line))
                    }
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1)
                    )
                    lp.marginStart = (60 * dp).toInt()
                    card.addView(line, lp)
                }
            }
            groups.addView(card)
        }

        TextFit.relax(view)
        sheet.setContentView(view)
        // Open at full height: the four cards are the whole point, and a
        // half-open sheet showing one and a half of them reads as broken.
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
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
        // Each size shows itself: "Aa" in the tile at that size (10 Oct).
        val glyphSizes = floatArrayOf(12f, 15f, 18f, 21f)
        val choices = labels.mapIndexed { i, label ->
            ChoiceSheet.Option(label, glyph = activity.getString(R.string.text_size_glyph), glyphSp = glyphSizes[i.coerceAtMost(glyphSizes.size - 1)],
                iconTint = R.color.tile_followup_fg, tile = R.color.tile_followup_bg)
        }
        // The app's green, not the invoice tile's pink (6 Nov 2026, the owner's photo): the sheet's chosen row is green too.
        ChoiceSheet.show(activity, activity.getString(R.string.text_size), choices, current) { which ->
            if (which != current) {
                TextSize.setLevel(activity, which)
                activity.recreate()
            }
        }
    }

    /** Light, dark, or the phone's own (see [ThemeMode]); setDefaultNightMode rebuilds open screens itself. */
    private fun theme(activity: Activity) {
        val labels = arrayOf(
            activity.getString(R.string.theme_light),
            activity.getString(R.string.theme_dark),
            activity.getString(R.string.theme_system)
        )
        val current = ThemeMode.get(activity)
        // Sun, moon, phone (10 Oct); same order as the labels above.
        val choices = listOf(
            ChoiceSheet.Option(labels[0], activity.getString(R.string.theme_light_hint),
                icon = R.drawable.ic_theme_light, iconTint = R.color.backup_ink, tile = R.color.tile_bills_bg),
            ChoiceSheet.Option(labels[1], activity.getString(R.string.theme_dark_hint),
                icon = R.drawable.ic_theme_dark, iconTint = R.color.tile_card_fg, tile = R.color.tile_card_bg),
            ChoiceSheet.Option(labels[2], activity.getString(R.string.theme_system_hint),
                icon = R.drawable.ic_theme_phone, iconTint = R.color.tile_settings_fg, tile = R.color.tile_settings_bg)
        )
        ChoiceSheet.show(activity, activity.getString(R.string.theme), choices, current) { which ->
            if (which != current) ThemeMode.set(activity, which)
        }
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
        // A sheet like Automations' and Theme's (8 Nov 2026): the state as a pill, the status line, the long
        // explanation behind Learn more, one button naming the result (red when it takes the lock away).
        SettingSheet.show(
            activity,
            icon = R.drawable.ic_menu_lock,
            tile = R.color.tile_bills_bg,
            title = activity.getString(R.string.app_lock),
            on = enabled,
            summary = activity.getString(if (enabled) R.string.app_lock_enabled else R.string.app_lock_disabled),
            more = activity.getString(R.string.app_lock_explain),
            actionLabel = activity.getString(if (enabled) R.string.app_lock_turn_off else R.string.app_lock_turn_on),
            danger = enabled
        ) {
            val flip = {
                AppLock.setEnabled(activity, !enabled)
                Toast.makeText(
                    activity,
                    if (!enabled) R.string.app_lock_enabled else R.string.app_lock_disabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Both ways take the phone's own fingerprint or screen lock first. OFF (21 Oct 2026): anyone
            // holding the open app could switch it off in two taps. ON (3 Nov 2026 audit, C3): the lock is
            // proven to answer on THIS phone before it guards the book — a lock that could not be answered
            // shut the owner out of his own ledger at the next opening.
            val host = activity as? androidx.fragment.app.FragmentActivity
            if (host != null) {
                val prompt = androidx.biometric.BiometricPrompt(host, androidx.core.content.ContextCompat.getMainExecutor(host),
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { flip() }
                    })
                prompt.authenticate(
                    androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(if (enabled) R.string.app_lock_turn_off else R.string.app_lock_turn_on))
                        .setSubtitle(activity.getString(R.string.lock_subtitle))
                        .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                        .build()
                )
            }
        }
    }
}
