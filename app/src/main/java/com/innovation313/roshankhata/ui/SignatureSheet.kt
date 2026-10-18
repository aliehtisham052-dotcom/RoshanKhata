package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * The customer's signature screen (18 Oct): one screen for every place a
 * signature is taken — the receipt, a new entry, and Edit.
 *
 * The owner hands the phone over, so the top states what is being signed
 * for: the customer, which way the money went, how much, and when. Below,
 * a large white pad (SignaturePad) with Undo and Clear; Save stays off until
 * there is ink, so an empty signature can never be saved.
 *
 * It only hands the picture back ([onSaved]); where it is filed is the
 * caller's business, because a new entry has no number until it is saved.
 */
object SignatureSheet {

    /** What the signature is for, shown at the top. Null fields are left out. */
    data class About(
        val party: String?,
        val isGiven: Boolean,
        val amount: Double?,
        val time: Long?
    )

    fun show(activity: Activity, about: About, onSaved: (Bitmap) -> Unit) {
        val dialog = Dialog(activity, R.style.Theme_RoshanKhata)
        val root = activity.layoutInflater.inflate(R.layout.dialog_signature, null)
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        TextFit.relax(root)

        fill(activity, root, about)

        val pad = root.findViewById<SignaturePad>(R.id.sigPad)
        val placeholder = root.findViewById<TextView>(R.id.tvSigPlaceholder)
        val undo = root.findViewById<MaterialButton>(R.id.btnSigUndo)
        val clear = root.findViewById<MaterialButton>(R.id.btnSigClear)
        val save = root.findViewById<MaterialButton>(R.id.btnSigSave)

        fun paint(hasInk: Boolean) {
            placeholder.animate().alpha(if (hasInk) 0f else 1f).setDuration(150).start()
            undo.isEnabled = pad.canUndo
            clear.isEnabled = hasInk
            // Faded, not greyed: a disabled Material button turns its own
            // fill grey and its label with it (CLAUDE.md lesson 10).
            save.alpha = if (hasInk) 1f else 0.45f
            undo.alpha = if (pad.canUndo) 1f else 0.4f
            clear.alpha = if (hasInk) 1f else 0.4f
        }
        pad.onInkChanged = { paint(it) }
        paint(false)

        undo.setOnClickListener { pad.undo(); paint(pad.hasInk) }
        clear.setOnClickListener { pad.clear(); paint(false) }
        root.findViewById<View>(R.id.btnSigClose).setOnClickListener { dialog.dismiss() }
        root.findViewById<View>(R.id.btnSigCancel).setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            if (!pad.hasInk) {
                android.widget.Toast.makeText(activity, R.string.sig_empty, android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bmp = pad.toBitmap()
            dialog.dismiss()
            onSaved(bmp)
        }

        fitBars(activity, dialog, root)
        dialog.show()
    }

    private fun fill(activity: Activity, root: View, about: About) {
        val color = { id: Int -> ContextCompat.getColor(activity, id) }
        val party = root.findViewById<TextView>(R.id.tvSigParty)
        val direction = root.findViewById<TextView>(R.id.tvSigDirection)
        val amount = root.findViewById<TextView>(R.id.tvSigAmount)
        val date = root.findViewById<TextView>(R.id.tvSigDate)

        party.text = about.party.orEmpty()
        party.visibility = if (about.party.isNullOrBlank()) View.GONE else View.VISIBLE

        direction.setText(if (about.isGiven) R.string.you_gave else R.string.you_got)
        // You gave: red pill, white text. You got: green pill, white text.
        direction.backgroundTintList = ColorStateList.valueOf(
            color(if (about.isGiven) R.color.red_gave else R.color.brand_green)
        )
        direction.setTextColor(Color.WHITE)

        val money = about.amount
        amount.text = if (money != null) Format.money(money) else ""
        amount.setTextColor(color(if (about.isGiven) R.color.red_gave_text else R.color.brand_green_text))

        date.text = about.time?.let { Format.dateTime(it) }.orEmpty()
        date.visibility = if (about.time == null) View.GONE else View.VISIBLE
    }

    /**
     * Drawn behind the bars like every screen: the top bar takes the status
     * bar, the footer the gesture bar (the keyboard never opens here).
     */
    private fun fitBars(activity: Activity, dialog: Dialog, root: View) {
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
        val top = root.findViewById<View>(R.id.sigTopBar)
        val foot = root.findViewById<View>(R.id.sigFooter)
        val t = intArrayOf(top.paddingStart, top.paddingTop, top.paddingEnd, top.paddingBottom)
        val f = intArrayOf(foot.paddingStart, foot.paddingTop, foot.paddingEnd, foot.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val rtl = root.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val start = if (rtl) bars.right else bars.left
            val end = if (rtl) bars.left else bars.right
            top.setPaddingRelative(t[0] + start, t[1] + bars.top, t[2] + end, t[3])
            foot.setPaddingRelative(f[0] + start, f[1], f[2] + end, f[3] + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
