package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.BusinessSettingsActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.PdfExport
import com.innovation313.roshankhata.data.ProfileChecks
import com.innovation313.roshankhata.data.ShopCountry

/**
 * "Add to your statement" (8 Nov 2026, the owner's phone test): the statement's ready dialog said what the
 * Business Profile lacked, and its button threw the owner into the long profile at some box, scrolled so the
 * card's heading was gone, with the address in another card and the statement to be found and made again by
 * hand. This asks only for the boxes that are missing — the way to pay (bank name, account title, IBAN or
 * account number, wallet number) and/or the shop's phone and address — as boxed fields, and writes them with
 * the very setters the profile's Save uses. The caller then makes the statement again ([onSaved]), so the next
 * ready dialog no longer lists them.
 *
 * The IBAN and wallet checks are the profile's own (ProfileChecks, by the shop's country) and warn rather
 * than block, because an account number that is not an IBAN is legitimate: the first Save shows the warning,
 * a second Save keeps what was typed. The full profile stays one tap away.
 */
object StatementDetails {

    fun show(activity: Activity, gaps: PdfExport.Gaps, onSaved: () -> Unit) {
        val d = activity.resources.displayMetrics.density
        val pakistani = BusinessProfile.country(activity) == ShopCountry.PAKISTAN

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
        }
        fun add(v: View, topDp: Int = 0) {
            body.addView(
                v,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (topDp * d).toInt() }
            )
        }
        fun label(res: Int, bold: Boolean = false) = TextView(activity, null, 0, R.style.FormFieldLabel).apply {
            setText(res)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            if (bold) {
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(activity, R.color.ink))
            }
        }
        fun box(type: Int, ltr: Boolean = false) = FormBox.style(EditText(activity)).apply {
            inputType = type
            if (ltr) {
                textDirection = View.TEXT_DIRECTION_LTR
            }
        }

        val intro = TextView(activity).apply {
            setText(R.string.stmt_add_help)
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
        }
        add(intro)

        var etBank: EditText? = null
        var etTitle: EditText? = null
        var etIban: EditText? = null
        var etWallet: EditText? = null
        var etPhone: EditText? = null
        var etAddress: EditText? = null

        if (gaps.payMissing) {
            add(label(R.string.stmt_add_pay_section, bold = true), 16)
            add(label(R.string.invoice_bank_name_hint), 8)
            etBank = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS).also { add(it, 2) }
            add(label(R.string.invoice_bank_title_hint), 8)
            etTitle = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                .also { add(it, 2) }
            add(label(R.string.invoice_bank_iban_hint), 8)
            etIban = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, ltr = true)
                .also { add(it, 2) }
            add(label(R.string.wallet_number), 8)
            etWallet = box(InputType.TYPE_CLASS_PHONE, ltr = true).also { add(it, 2) }
        }
        if (gaps.phoneMissing) {
            add(label(R.string.bp_phone, bold = !gaps.payMissing && !gaps.addressMissing), if (gaps.payMissing) 16 else 12)
            etPhone = box(InputType.TYPE_CLASS_PHONE, ltr = true).also { add(it, 2) }
        }
        if (gaps.addressMissing) {
            add(label(R.string.business_address), 12)
            etAddress = box(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ).also { add(it, 2) }
        }

        val note = TextView(activity).apply {
            textSize = 13f
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setTextColor(ContextCompat.getColor(activity, R.color.red_gave_text))
            visibility = View.GONE
        }
        add(note, 12)

        fun text(b: EditText?) = b?.text?.toString()?.trim().orEmpty()
        fun all() = listOf(etBank, etTitle, etIban, etWallet, etPhone, etAddress)

        /** The profile's own setters; only the boxes shown here, and only the ones typed in. */
        fun write() {
            if (text(etBank).isNotEmpty()) BusinessProfile.setBankName(activity, text(etBank))
            if (text(etTitle).isNotEmpty()) BusinessProfile.setBankAccountTitle(activity, text(etTitle))
            if (text(etIban).isNotEmpty()) BusinessProfile.setBankIban(activity, text(etIban))
            if (text(etWallet).isNotEmpty()) BusinessProfile.setBankJazzCash(activity, text(etWallet))
            if (text(etPhone).isNotEmpty()) BusinessProfile.setBusinessPhone(activity, text(etPhone))
            if (text(etAddress).isNotEmpty()) BusinessProfile.setBusinessAddress(activity, text(etAddress))
        }

        val scroll = ScrollView(activity).apply { addView(body) }
        TextFit.relax(scroll)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.stmt_add_title)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.stmt_add_open_profile, null)
            .setPositiveButton(R.string.save, null)
            .show()
        KeyboardFollow.dialog(dialog)

        FormGuard.guard(activity, dialog, typed = { all().any { text(it).isNotEmpty() } })

        // The full profile: what was typed here is kept first, then the profile opens at the same part.
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
            write()
            val focus = when {
                gaps.payMissing -> BusinessSettingsActivity.FOCUS_PAYMENT
                else -> BusinessSettingsActivity.FOCUS_PHONE
            }
            dialog.dismiss()
            activity.startActivity(
                Intent(activity, BusinessSettingsActivity::class.java).putExtra(BusinessSettingsActivity.EXTRA_FOCUS, focus)
            )
        }

        var warned = false
        FormGuard.onSave(dialog) {
            if (all().all { text(it).isEmpty() }) {
                note.setText(R.string.stmt_add_nothing)
                note.visibility = View.VISIBLE
                return@onSave false
            }
            val problems = buildList {
                if (ProfileChecks.ibanLooksWrong(text(etIban), pakistani)) add(activity.getString(R.string.bp_iban_bad))
                if (ProfileChecks.mobileLooksWrong(text(etWallet), pakistani)) add(activity.getString(R.string.bp_mobile_bad))
            }
            if (problems.isNotEmpty() && !warned) {
                warned = true
                note.text = (problems + activity.getString(R.string.stmt_add_check_then_save)).joinToString("\n")
                note.visibility = View.VISIBLE
                return@onSave false
            }
            write()
            onSaved()
            true
        }
    }
}
