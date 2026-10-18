package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.string

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.BackupActivity
import com.innovation313.roshankhata.BusinessSettingsActivity
import com.innovation313.roshankhata.BusinessSwitchActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BackupAge
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.DriveAuth
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.data.ViewerMode

/**
 * The shop sheet (11 Oct): the one door for "my shop".
 *
 * The owner could not find his own profile. It was the first row of the
 * gear's sheet on Home, a person icon on the Khata screen, and switching
 * shops was a tap on a title that did not look tappable, or a card in a
 * drawer that repeated every Home tile. Now the shop button - the shop's
 * logo and name, on Home and on the Khata screen alike - opens this sheet,
 * and the gear's sheet points here too:
 *
 *  - the profile card: logo, name, phone and address, how complete the
 *    profile is and what is missing (invoices, statements and the business
 *    card are built from it), and Edit profile;
 *  - the profile's parts, each opening the profile screen at that part;
 *  - the Google account and its backup;
 *  - every shop on this phone, the open one ticked, and a new shop.
 *
 * A helper's read-only phone sees the card and nothing it may not change.
 */
object ShopSheet {

    /** One thing a complete profile has, and the word for it when missing. */
    private class Part(val done: Boolean, val missingRes: Int)

    /** How complete the open shop's profile is, as parts done of all parts. */
    fun completeness(activity: Activity): Pair<Int, List<String>> {
        val bp = BusinessProfile
        val payment = bp.hasQr(activity) || !bp.bankIban(activity).isNullOrBlank() ||
            !bp.bankJazzCash(activity).isNullOrBlank() || !bp.bankName(activity).isNullOrBlank()
        val parts = listOf(
            Part(!bp.businessName(activity).isNullOrBlank(), R.string.shop_missing_name),
            Part(!bp.businessPhone(activity).isNullOrBlank(), R.string.shop_missing_phone),
            Part(!bp.businessAddress(activity).isNullOrBlank(), R.string.shop_missing_address),
            Part(bp.hasLogo(activity), R.string.shop_missing_logo),
            Part(payment, R.string.shop_missing_payment),
            Part(bp.hasStamp(activity), R.string.shop_missing_stamp),
            Part(bp.hasSignature(activity), R.string.shop_missing_signature)
        )
        val done = parts.count { it.done }
        val percent = (done * 100 + parts.size / 2) / parts.size
        return percent to parts.filterNot { it.done }.map { activity.getString(it.missingRes) }
    }

    /** The shop's own logo in a tile, or the storefront glyph until one is set. */
    fun paintLogo(activity: Activity, image: ImageView, glyphPaddingDp: Float) {
        val logo = try { BusinessProfile.loadLogo(activity) } catch (e: Exception) { null }
        val d = activity.resources.displayMetrics.density
        if (logo != null) {
            image.imageTintList = null
            image.setImageBitmap(logo)
            image.setPadding(0, 0, 0, 0)
            (image.parent as? View)?.clipToOutline = true
        } else {
            image.setImageResource(R.drawable.ic_storefront)
            image.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.shop_card_gold))
            val p = (glyphPaddingDp * d).toInt()
            image.setPadding(p, p, p, p)
        }
    }

    fun show(activity: Activity) {
        val sheet = BottomSheetDialog(activity)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.sheet_shop, null)
        val viewer = ViewerMode.isOn(activity)
        val bp = BusinessProfile

        // The card.
        val name = bp.businessName(activity)
        view.findViewById<TextView>(R.id.tvSheetShopName).text =
            if (name.isNullOrBlank()) activity.getString(R.string.shop_add_name) else name
        view.findViewById<TextView>(R.id.tvSheetShopLine).apply {
            val line = listOfNotNull(
                Trade.current(activity).label(activity),
                bp.businessAddress(activity)?.takeIf { it.isNotBlank() },
                bp.businessPhone(activity)?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }
            ).joinToString("\n")
            text = line
            visibility = if (line.isBlank()) View.GONE else View.VISIBLE
        }
        paintLogo(activity, view.findViewById(R.id.ivSheetLogo), 13f)

        fun openProfile(focus: String?) {
            sheet.dismiss()
            activity.startActivity(
                Intent(activity, BusinessSettingsActivity::class.java)
                    .apply { if (focus != null) putExtra(BusinessSettingsActivity.EXTRA_FOCUS, focus) }
            )
        }

        val (percent, missing) = completeness(activity)
        view.findViewById<View>(R.id.sheetProgressBox).visibility = if (viewer) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tvSheetProgress).text =
            if (missing.isEmpty()) activity.getString(R.string.shop_progress_done)
            else Digits.string(activity.resources, R.string.shop_progress, percent)
        view.findViewById<ProgressBar>(R.id.pbSheetProgress).progress = percent
        view.findViewById<TextView>(R.id.tvSheetMissing).apply {
            if (missing.isEmpty()) visibility = View.GONE
            else text = activity.getString(R.string.shop_progress_missing, missing.joinToString(", "))
        }
        view.findViewById<MaterialButton>(R.id.btnSheetEditProfile).apply {
            visibility = if (viewer) View.GONE else View.VISIBLE
            setOnClickListener { openProfile(null) }
        }

        // The profile's parts.
        val profileRows = view.findViewById<LinearLayout>(R.id.sheetProfileRows)
        if (viewer) {
            listOf(R.id.tvSheetSecProfile, R.id.sheetProfileRows, R.id.tvSheetSecGoogle, R.id.sheetGoogleRow,
                R.id.tvSheetSecShops, R.id.sheetShopRows).forEach { view.findViewById<View>(it).visibility = View.GONE }
        } else {
            val calendar = activity.getString(when (bp.calendar(activity)) {
                ShopCalendar.Kind.GREGORIAN -> R.string.calendar_gregorian
                ShopCalendar.Kind.HIJRI -> R.string.calendar_hijri
                ShopCalendar.Kind.JALALI -> R.string.calendar_jalali
            })
            val rows = listOf(
                Row(R.drawable.ic_storefront, R.color.tile_khata_bg, R.color.brand_green_text,
                    activity.getString(R.string.shop_row_name), activity.getString(R.string.shop_row_name_sub)) { openProfile(BusinessSettingsActivity.FOCUS_NAME) },
                Row(R.drawable.ic_seal_stamp, R.color.tile_invoice_bg, R.color.tile_invoice_fg,
                    activity.getString(R.string.shop_row_stamp), activity.getString(R.string.shop_row_stamp_sub)) { openProfile(BusinessSettingsActivity.FOCUS_STAMP) },
                Row(R.drawable.ic_qr_scan, R.color.tile_insights_bg, R.color.tile_insights_fg,
                    activity.getString(R.string.shop_row_payment), activity.getString(R.string.shop_row_payment_sub)) { openProfile(BusinessSettingsActivity.FOCUS_PAYMENT) },
                Row(Trade.current(activity).iconRes, Trade.current(activity).chipRes, Trade.current(activity).inkRes,
                    activity.getString(R.string.shop_row_trade), Trade.current(activity).label(activity)) { openProfile(BusinessSettingsActivity.FOCUS_TRADE) },
                Row(R.drawable.ic_hash, R.color.tile_bills_bg, R.color.backup_ink,
                    activity.getString(R.string.shop_row_money),
                    "${com.innovation313.roshankhata.data.Currency.label(bp.currency(activity))}  ·  $calendar") { openProfile(BusinessSettingsActivity.FOCUS_TRADE) }
            )
            addRows(activity, inflater, profileRows, rows)

            // The Google account.
            val account = DriveAuth.accountName(activity)
            val google = if (account != null) {
                Row(R.drawable.ic_google_g, R.color.tile_settings_bg, 0, account,
                    activity.getString(R.string.shop_google_backup, BackupAge.line(activity).text)) {
                    sheet.dismiss(); activity.startActivity(Intent(activity, BackupActivity::class.java))
                }
            } else {
                Row(R.drawable.ic_google_g, R.color.tile_settings_bg, 0, activity.getString(R.string.welcome_connect),
                    activity.getString(R.string.shop_google_why)) {
                    sheet.dismiss(); activity.startActivity(BackupActivity.connectIntent(activity))
                }
            }
            addRows(activity, inflater, view.findViewById(R.id.sheetGoogleRow), listOf(google))

            // Every shop on this phone.
            val active = Businesses.active(activity).id
            val shops = Businesses.list(activity).map { b ->
                val label = Businesses.displayName(activity, b) ?: activity.getString(R.string.app_name)
                val open = b.id == active
                Row(0, if (open) R.color.brand_green_soft else R.color.tile_bills_bg,
                    if (open) R.color.brand_green_text else R.color.backup_ink, label,
                    activity.getString(if (open) R.string.shop_open_now else R.string.shop_tap_open),
                    initials = initials(label), ticked = open) {
                    if (!open) {
                        sheet.dismiss()
                        BusinessSwitchActivity.switchAndRestart(activity, b.id)
                    }
                }
            } + Row(R.drawable.ic_add_plain, R.color.brand_green_soft, R.color.brand_green_text,
                activity.getString(R.string.shop_new), null, chevron = false) {
                sheet.dismiss(); activity.startActivity(Intent(activity, BusinessSwitchActivity::class.java))
            }
            addRows(activity, inflater, view.findViewById(R.id.sheetShopRows), shops)
        }

        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
    }

    private class Row(
        val icon: Int,
        val tile: Int,
        /** The icon's tint; 0 draws the icon in its own colours. */
        val tint: Int,
        val title: String,
        val sub: String?,
        val initials: String? = null,
        val ticked: Boolean = false,
        val chevron: Boolean = true,
        val open: () -> Unit
    )

    private fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1) }.uppercase().ifEmpty { "•" }

    private fun addRows(activity: Activity, inflater: LayoutInflater, box: LinearLayout, rows: List<Row>) {
        val dp = activity.resources.displayMetrics.density
        rows.forEachIndexed { i, r ->
            val row = inflater.inflate(R.layout.item_shop_row, box, false)
            row.findViewById<FrameLayout>(R.id.shopRowTile).backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(activity, r.tile))
            val icon = row.findViewById<ImageView>(R.id.ivShopRowIcon)
            val letters = row.findViewById<TextView>(R.id.tvShopRowInitials)
            if (r.initials != null) {
                icon.visibility = View.GONE
                letters.visibility = View.VISIBLE
                letters.text = r.initials
                letters.setTextColor(ContextCompat.getColor(activity, r.tint))
            } else {
                icon.setImageResource(r.icon)
                icon.imageTintList = if (r.tint != 0) ColorStateList.valueOf(ContextCompat.getColor(activity, r.tint)) else null
            }
            row.findViewById<TextView>(R.id.tvShopRowTitle).text = r.title
            row.findViewById<TextView>(R.id.tvShopRowSub).apply {
                if (r.sub.isNullOrBlank()) visibility = View.GONE
                else { text = r.sub; visibility = View.VISIBLE }
            }
            row.findViewById<ImageView>(R.id.ivShopRowEnd).apply {
                when {
                    r.ticked -> {
                        setImageResource(R.drawable.ic_check_circle)
                        imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.brand_green_text))
                    }
                    !r.chevron -> visibility = View.GONE
                }
            }
            row.contentDescription = listOfNotNull(r.title, r.sub).joinToString(", ")
            row.setOnClickListener { r.open() }
            box.addView(row)
            if (i < rows.size - 1) {
                box.addView(View(activity).apply {
                    setBackgroundColor(ContextCompat.getColor(activity, R.color.page_line))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1))
                    .apply { marginStart = (62 * dp).toInt() })
            }
        }
    }
}
