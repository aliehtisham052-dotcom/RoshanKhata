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
import com.innovation313.roshankhata.ui.StepDots
import com.innovation313.roshankhata.ui.TextFit

/**
 * First run, step 3 of 3: the shop's name (11 Oct).
 *
 * Until a name is written the app shows its own name where the shop's
 * belongs - Home, invoices, statements, the business card - and the owner,
 * who saw "Roshan Khata" at the top of his own Home, did not know there was
 * a profile to fill in. So the setup asks: name (required for Continue) and
 * phone (optional), both saved to the shop's profile. Maybe later goes on
 * to Home, where the shop button reads "Add your shop name" until it is.
 *
 * Skipped when the shop already has a name (a restored backup brings it).
 * Back does nothing: the setup is a straight line, and Maybe later is the
 * way past this step.
 */
class ShopNameActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BusinessProfile.businessName(this).isNullOrBlank()) {
            proceed()
            return
        }
        setContentView(R.layout.activity_shop_name)
        val root = findViewById<View>(R.id.shopNameRoot)
        fitBars(root)

        StepDots.show(findViewById(R.id.nameStepDots), findViewById<TextView>(R.id.tvNameKicker), 3)

        val etName = findViewById<EditText>(R.id.etShopName)
        val etPhone = findViewById<EditText>(R.id.etShopPhone)
        val btnContinue = findViewById<MaterialButton>(R.id.btnShopNameContinue)
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnContinue.isEnabled = !s.isNullOrBlank()
                styleName(etName)
            }
        })
        styleName(etName)
        btnContinue.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            BusinessProfile.setBusinessName(this, name)
            etPhone.text.toString().trim().takeIf { it.isNotEmpty() }?.let { BusinessProfile.setBusinessPhone(this, it) }
            proceed()
        }
        findViewById<MaterialButton>(R.id.btnShopNameLater).setOnClickListener { proceed() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        TextFit.relax(root)
    }

    /**
     * Behind the bars like the shop-type page: the header takes the status
     * bar, the footer the gesture bar or the keyboard, whichever is taller,
     * so Continue stays above the keyboard while the name is typed.
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
