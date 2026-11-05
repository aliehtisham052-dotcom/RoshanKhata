package com.innovation313.roshankhata

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.SetupProgress
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.data.set
import com.innovation313.roshankhata.ui.CountryPicker
import com.innovation313.roshankhata.ui.DeviceRegion
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.StepDots
import com.innovation313.roshankhata.ui.TextFit

/**
 * First run, step 3 of 3: the shop's details (11 Oct; redrawn 5 Nov 2026 from the owner's
 * approved mockup).
 *
 * Until a name is written the app shows its own name where the shop's belongs - Home,
 * invoices, statements, the business card - so the setup asks for it, with the country and an
 * optional phone, under a picture of the invoice's band that fills in as they are typed.
 *
 * The country is a box filled with what the phone names ([DeviceRegion]) and changed on its own
 * page ([CountryPicker]). Until 5 Nov it was a sheet that opened by itself after the name, and
 * Back or a tap beside it kept the guess and opened Home - so the owner never saw it, and a
 * setup closed before it never asked at all: the country stayed unsaved, which reads as
 * Pakistan everywhere.
 *
 * Every way off the page keeps what is on it: Back goes to step 2, "Maybe later" goes on without
 * a name, "Start my khata" needs one. Only those two end the setup ([SetupProgress.markDone]).
 * While the setup runs, the currency sign and calendar follow the country shown, since nothing
 * else has set them yet; a page opened after the setup (a test) changes them only from the
 * defaults, as Business Profile does.
 */
class ShopNameActivity : BaseActivity() {

    private companion object {
        const val STATE_COUNTRY = "country"
    }

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnStart: MaterialButton
    private lateinit var country: ShopCountry
    /** The country the phone itself names (SIM, network, its languages), if any. */
    private var region: String? = null
    private var setupRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop_name)
        val root = findViewById<View>(R.id.shopNameRoot)
        fitBars(root)
        setupRunning = !SetupProgress.isDone(this)

        StepDots.show(findViewById(R.id.nameStepDots), findViewById<TextView>(R.id.tvNameKicker), 3)

        etName = findViewById(R.id.etShopName)
        etPhone = findViewById(R.id.etShopPhone)
        btnStart = findViewById(R.id.btnShopNameContinue)

        region = DeviceRegion.of(this)
        val saved = if (BusinessProfile.hasCountry(this)) BusinessProfile.country(this) else null
        val restored = savedInstanceState?.getString(STATE_COUNTRY)
        country = when {
            restored != null -> ShopCountry.of(restored)
            saved != null -> saved
            else -> ShopCountry.guess(region, BusinessProfile.currency(this), com.innovation313.roshankhata.ui.LocaleRefresh.tag(this))
        }

        if (savedInstanceState == null) {
            BusinessProfile.businessName(this)?.let { etName.setText(it) }
            BusinessProfile.businessPhone(this)?.let { etPhone.setText(nationalPart(it)) }
        }

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnStart.isEnabled = !s.isNullOrBlank()
                styleName(etName)
                renderPreview()
            }
        })
        etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = renderPreview()
        })
        btnStart.isEnabled = etName.text.isNotBlank()
        styleName(etName)

        findViewById<View>(R.id.btnShopCountry).setOnClickListener {
            CountryPicker.show(this, country, fromPhone = region) { picked ->
                if (isFinishing || isDestroyed) return@show
                country = picked
                applyCountry(picked)
                renderCountry()
            }
        }
        btnStart.setOnClickListener {
            if (etName.text.isBlank()) return@setOnClickListener
            save()
            finishSetup()
        }
        findViewById<MaterialButton>(R.id.btnShopNameLater).setOnClickListener {
            save()
            finishSetup()
        }
        findViewById<View>(R.id.btnShopNameBack).setOnClickListener { back() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })

        findViewById<TextView>(R.id.tvPreviewDoc).text =
            getString(R.string.coach_title_invoice).uppercase(resources.configuration.locales[0]) + " · " + Format.ltr("INV-000001")
        findViewById<TextView>(R.id.tvPreviewDate).text = Format.dateOnly(System.currentTimeMillis())
        renderCountry()
        TextFit.relax(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_COUNTRY, country.code)
    }

    private fun renderCountry() {
        findViewById<TextView>(R.id.tvShopFlag).text = DeviceRegion.flag(country)
        findViewById<TextView>(R.id.tvShopCountry).text = CountryPicker.name(this, country)
        findViewById<TextView>(R.id.tvShopCountryCode).apply {
            val code = CountryPicker.codeLine(country)
            text = code
            visibility = if (code == null) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnShopCountry).contentDescription = listOfNotNull(
            getString(R.string.bp_country), CountryPicker.name(this, country), CountryPicker.codeLine(country)
        ).joinToString(", ")
        // Said only when it is true: the country shown is the one the phone itself names.
        findViewById<TextView>(R.id.tvShopCountryHelp).setText(
            if (region != null && country.code == region) R.string.country_found_phone else R.string.country_tap_change
        )
        val dialing = country.dialing
        findViewById<TextView>(R.id.tvShopPhonePrefix).apply {
            text = "+$dialing"
            visibility = if (dialing.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.shopPhoneRule).visibility = if (dialing.isEmpty()) View.GONE else View.VISIBLE
        renderPreview()
    }

    /** The band as an invoice will print it: the name (or its label, faint), then phone · country. */
    private fun renderPreview() {
        val name = etName.text.toString().trim()
        findViewById<TextView>(R.id.tvPreviewName).apply {
            text = name.ifEmpty { getString(R.string.shop_name_field) }
            alpha = if (name.isEmpty()) 0.6f else 1f
        }
        findViewById<TextView>(R.id.tvPreviewLine).text = listOfNotNull(
            fullPhone()?.let { Format.ltr(it) },
            CountryPicker.name(this, country).takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    }

    /**
     * The number as the profile keeps it: with the country's calling code in front, the national
     * 0 dropped ("0300 1234567" in Pakistan is "+92 300 1234567"); a number typed with + or 00 is
     * kept as the owner wrote it. Digits in any script are kept as 0-9 (lesson 28).
     */
    private fun fullPhone(): String? {
        val typed = Digits.toLatin(etPhone.text.toString()).trim()
        if (typed.isEmpty()) return null
        if (typed.startsWith("+")) return typed
        if (typed.startsWith("00")) return "+" + typed.drop(2).trimStart()
        if (country.dialing.isEmpty()) return typed
        val national = typed.removePrefix("0").trimStart()
        return if (national.isEmpty()) null else "+${country.dialing} $national"
    }

    /** What the phone box shows of a kept number: without the shown country's own code. */
    private fun nationalPart(kept: String): String {
        val code = "+" + country.dialing
        return if (country.dialing.isNotEmpty() && kept.startsWith(code)) kept.removePrefix(code).trimStart() else kept
    }

    /** Keeps everything on the page: the name, the phone and the country. */
    private fun save() {
        BusinessProfile.setBusinessName(this, etName.text.toString().trim().ifEmpty { null })
        BusinessProfile.setBusinessPhone(this, fullPhone())
        applyCountry(country)
    }

    private fun applyCountry(c: ShopCountry) {
        val defaultCurrency = BusinessProfile.currency(this) == Currency.DEFAULT
        val gregorian = BusinessProfile.calendar(this) == ShopCalendar.Kind.GREGORIAN
        BusinessProfile.setCountry(this, c)
        if (setupRunning) {
            // Nothing but this page has set them during the setup: they follow the country shown,
            // back again when the owner changes his mind (UAE's AED, then Pakistan's Rs).
            Currency.set(this, if (c == ShopCountry.OTHER) Currency.DEFAULT else c.currencySign)
            ShopCalendar.set(this, c.calendar)
        } else {
            if (defaultCurrency && c != ShopCountry.OTHER && c.currencySign != Currency.DEFAULT) Currency.set(this, c.currencySign)
            if (gregorian && c.calendar != ShopCalendar.Kind.GREGORIAN) ShopCalendar.set(this, c.calendar)
        }
    }

    /** Back to step 2 with what is typed kept; the shop type shows the trade already chosen. */
    private fun back() {
        if (isFinishing || isDestroyed) return
        save()
        startActivity(Intent(this, ShopTypeActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_close_enter, R.anim.screen_close_exit)
        finish()
    }

    private fun finishSetup() {
        SetupProgress.markDone(this)
        proceed()
    }

    /**
     * Behind the bars like the shop-type page: the header takes the status
     * bar, the footer the gesture bar or the keyboard, whichever is taller,
     * so the buttons stay above the keyboard while the name is typed.
     */
    private fun fitBars(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        val header = (root as android.view.ViewGroup).getChildAt(0)
        val footer = root.findViewById<View>(R.id.shopNameFooter)
        val h = intArrayOf(header.paddingStart, header.paddingTop, header.paddingEnd, header.paddingBottom)
        val f = intArrayOf(footer.paddingStart, footer.paddingTop, footer.paddingEnd, footer.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val rtl = root.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val start = if (rtl) bars.right else bars.left
            val end = if (rtl) bars.left else bars.right
            header.setPaddingRelative(h[0] + start, h[1] + bars.top, h[2] + end, h[3])
            footer.setPaddingRelative(f[0] + start, f[1], f[2] + end, f[3] + maxOf(bars.bottom, ime.bottom))
            if (ime.bottom > 0) root.post { com.innovation313.roshankhata.ui.KeyboardFollow.reveal(root) }
            insets
        }
        com.innovation313.roshankhata.ui.KeyboardFollow.keepFocusedVisible(root)
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Bold once the owner has typed, plain while only the pale example shows
     * (13 Oct). A bold example looked like a name already filled in, so the
     * owner waited for a Continue that, rightly, stayed off on an empty field.
     */
    private fun styleName(et: EditText) {
        val style = if (et.text.isNullOrEmpty()) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD
        if (et.typeface?.style != style) et.setTypeface(android.graphics.Typeface.create(et.typeface, style))
    }

    private fun proceed() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
