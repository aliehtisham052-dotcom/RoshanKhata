package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ShopCountry
import java.util.Locale

/**
 * "Where is your shop?" (5 Nov 2026, the owner's approved setup mockup).
 *
 * The country used to be a sheet that opened by itself after the shop's name; Back or a tap
 * beside it kept the phone's guess and went straight on to Home, so a shop could be set to the
 * wrong country without ever seeing the question. Now the country is a box on the setup's
 * details page (and in Business Profile) and this page opens only from it: closing it changes
 * nothing.
 *
 * The country the phone itself names ([DeviceRegion]) comes first under "From your phone", the
 * rest under "All countries" in [ShopCountry.choices]'s order, Other last. Each is the
 * chooser's card (item_choice_row, so every "pick one" looks alike): the phone's own flag, the
 * country's name in the app's language, its calling code and currency sign. A search narrows
 * the list by the name in the app's language or in English, the two letters, or the code
 * ("92", "+92"); Other always stays.
 */
object CountryPicker {

    fun show(
        activity: Activity,
        current: ShopCountry?,
        fromPhone: String? = DeviceRegion.of(activity),
        title: CharSequence = activity.getString(R.string.country_ask_title),
        body: CharSequence? = activity.getString(R.string.country_ask_body),
        /** Runs when the page closes with nothing chosen (Back). */
        onCancel: (() -> Unit)? = null,
        onPick: (ShopCountry) -> Unit
    ): Dialog {
        val dialog = Dialog(activity, R.style.Theme_RoshanKhata)
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.dialog_country_picker, null)
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        root.findViewById<TextView>(R.id.tvCountryTitle).text = title
        root.findViewById<TextView>(R.id.tvCountryBody).apply {
            if (body.isNullOrBlank()) visibility = View.GONE else text = body
        }

        val locale = activity.resources.configuration.locales[0]
        val all = ShopCountry.choices(locale)
        val phone = fromPhone?.let { code -> all.firstOrNull { it != ShopCountry.OTHER && it.code == code } }

        val list = root.findViewById<LinearLayout>(R.id.countryList)
        var decided = false

        class Row(val country: ShopCountry, val view: View, val words: String)
        val rows = ArrayList<Row>(all.size)

        fun heading(words: Int): TextView = TextView(activity).apply {
            setText(words)
            isAllCaps = true
            letterSpacing = 0.08f
            setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            val d = activity.resources.displayMetrics.density
            setPaddingRelative((4 * d).toInt(), (16 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            ViewCompat.setAccessibilityHeading(this, true)
        }

        fun choose(row: Row) {
            if (decided) return
            decided = true
            for (r in rows) {
                val on = r === row
                r.view.isSelected = on
                r.view.findViewById<View>(R.id.choiceTick).visibility = if (on) View.VISIBLE else View.INVISIBLE
            }
            // Let the ring and tick show for a beat, as the chooser does, then close and act.
            row.view.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    dialog.dismiss()
                    onPick(row.country)
                }
            }, 160L)
        }

        fun rowFor(country: ShopCountry): Row {
            val view = inflater.inflate(R.layout.item_choice_row, list, false)
            val called = name(activity, country, locale)
            val code = codeLine(country)
            view.findViewById<TextView>(R.id.tvChoiceRowTitle).text = called
            view.findViewById<TextView>(R.id.tvChoiceRowSub).apply {
                if (code == null) visibility = View.GONE else { text = code; visibility = View.VISIBLE }
            }
            view.findViewById<FrameLayout>(R.id.choiceTile).backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.tile_settings_bg))
            view.findViewById<View>(R.id.ivChoiceIcon).visibility = View.GONE
            view.findViewById<TextView>(R.id.tvChoiceGlyph).apply {
                visibility = View.VISIBLE
                text = DeviceRegion.flag(country)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            }
            val on = country == current
            view.isSelected = on
            view.findViewById<View>(R.id.choiceTick).visibility = if (on) View.VISIBLE else View.INVISIBLE
            view.contentDescription = listOfNotNull(called, code).joinToString(", ")
            // Read by TalkBack as a radio button: "Pakistan, +92 · Rs, selected".
            view.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            })
            val words = listOf(
                called, country.displayName(Locale.ENGLISH), country.code,
                country.dialing, if (country.dialing.isEmpty()) "" else "+" + country.dialing
            ).joinToString(" ").lowercase(Locale.ROOT)
            val row = Row(country, view, words)
            view.setOnClickListener { choose(row) }
            return row
        }

        val phoneHeading = phone?.let { heading(R.string.country_section_phone) }
        val allHeading = heading(R.string.country_section_all)
        val none = TextView(activity).apply {
            setText(R.string.country_search_none)
            setTextColor(ContextCompat.getColor(activity, R.color.ink_soft))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            val d = activity.resources.displayMetrics.density
            setPaddingRelative((4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            visibility = View.GONE
        }

        if (phone != null && phoneHeading != null) {
            list.addView(phoneHeading)
            rowFor(phone).also { rows.add(it); list.addView(it.view) }
        }
        list.addView(allHeading)
        for (country in all) {
            if (country == phone) continue
            if (country == ShopCountry.OTHER) list.addView(none)
            rowFor(country).also { rows.add(it); list.addView(it.view) }
        }

        root.findViewById<EditText>(R.id.etCountrySearch).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                val q = com.innovation313.roshankhata.data.Digits.toLatin(e?.toString().orEmpty()).trim().lowercase(Locale.ROOT)
                var found = false
                for (r in rows) {
                    val shown = q.isEmpty() || r.country == ShopCountry.OTHER || r.words.contains(q)
                    r.view.visibility = if (shown) View.VISIBLE else View.GONE
                    if (shown && r.country != ShopCountry.OTHER) found = true
                }
                phoneHeading?.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
                allHeading.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
                none.visibility = if (q.isNotEmpty() && !found) View.VISIBLE else View.GONE
                root.findViewById<View>(R.id.countryScroll).scrollTo(0, 0)
            }
        })

        root.findViewById<View>(R.id.btnCountryBack).setOnClickListener { dialog.cancel() }
        dialog.setOnCancelListener { if (!decided) onCancel?.invoke() }

        fitBars(dialog, root, activity)
        TextFit.relax(root)
        dialog.show()
        // The page opens on the chosen country when it is far down the list.
        rows.firstOrNull { it.country == current }?.view?.let { chosen ->
            chosen.post { chosen.requestRectangleOnScreen(android.graphics.Rect(0, 0, chosen.width, chosen.height), true) }
        }
        return dialog
    }

    /** The country's name in the app's language; Other in the app's own word. */
    fun name(activity: Activity, country: ShopCountry, locale: Locale = activity.resources.configuration.locales[0]): String =
        if (country == ShopCountry.OTHER) activity.getString(R.string.country_other) else country.displayName(locale)

    /** "+92 · Rs", kept left to right in every language; none for Other. */
    fun codeLine(country: ShopCountry): String? =
        if (country.dialing.isEmpty()) null else Format.ltr("+" + country.dialing + " · " + country.currencySign)

    /**
     * Behind the bars like the setup pages: the header takes the status bar, the list's foot the
     * gesture bar or the keyboard, whichever is taller (the lesson in [ScreenInsets]: added to the
     * designed padding, never replacing it).
     */
    private fun fitBars(dialog: Dialog, root: View, activity: Activity) {
        val window = dialog.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = ContextCompat.getColor(activity, R.color.page_bg)
        val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        val header = root.findViewById<View>(R.id.countryHeader)
        val scroll = root.findViewById<View>(R.id.countryScroll)
        val h = intArrayOf(header.paddingStart, header.paddingTop, header.paddingEnd, header.paddingBottom)
        val s = intArrayOf(scroll.paddingStart, scroll.paddingTop, scroll.paddingEnd, scroll.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val rtl = root.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val start = if (rtl) bars.right else bars.left
            val end = if (rtl) bars.left else bars.right
            val ime = KeyboardFollow.imeBottom(insets)
            header.setPaddingRelative(h[0] + start, h[1] + bars.top, h[2] + end, h[3])
            scroll.setPaddingRelative(s[0] + start, s[1], s[2] + end, s[3] + maxOf(bars.bottom, ime))
            insets
        }
        // The room above is this page's own; KeyboardFollow only keeps the box in the middle (6 Nov 2026).
        KeyboardFollow.keepFocusedVisible(root)
        ViewCompat.requestApplyInsets(root)
    }
}
