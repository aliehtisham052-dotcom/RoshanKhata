package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R

/** A ScrollView that never grows past [maxPx]: the explanation scrolls, what is under it stays in sight. */
class CappedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var maxPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) maxPx else minOf(size, maxPx)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}

/**
 * The one "type the word to confirm" dialog (8 Nov 2026, the owner's phone test, point 24): Erase all data and
 * Delete business. The explanation scrolls in a capped box; under it the instruction line (the word in bold)
 * and the typing box stay above the keyboard with the buttons. The destructive button is grey until the
 * word is typed EXACTLY (case-sensitive; [matches]) and then turns the app's "gave" red. The box's hint is
 * "Type here", never the word itself, so the box does not look filled in.
 */
object TypeToConfirm {

    /** Exactly the word: surrounding spaces and keyboards' invisible joiners are ignored, letter case is not. */
    fun exact(typed: String?, word: String): Boolean {
        val entered = clean(typed.orEmpty())
        return entered.isNotEmpty() && entered == clean(word)
    }

    private fun clean(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
            .filter { it != '‌' && it != '‍' && it != '‎' && it != '‏' }
            .trim()

    /**
     * [instructionRes] is a sentence with one %1$s for [word] ("Type %1$s to erase everything").
     * [confirm] runs when the owner presses the destructive button, which is disabled at once; the dialog stays
     * open until [confirm] dismisses it.
     */
    fun show(
        activity: Activity,
        title: String,
        body: String,
        instructionRes: Int,
        word: String,
        matches: (String?) -> Boolean = { exact(it, word) },
        confirm: (AlertDialog) -> Unit
    ): AlertDialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_type_confirm, null)
        TextFit.relax(view)
        FormFocus.noAutofill(view)
        val scroll: CappedScrollView = view.findViewById(R.id.scrollConfirmBody)
        scroll.maxPx = (activity.resources.displayMetrics.heightPixels * 0.30f).toInt()
        view.findViewById<TextView>(R.id.tvConfirmBody).text = body
        val tvInstruction: TextView = view.findViewById(R.id.tvConfirmInstruction)
        val sentence = activity.getString(instructionRes, word)
        val spanned = SpannableString(sentence)
        val at = sentence.indexOf(word)
        if (at >= 0) {
            spanned.setSpan(StyleSpan(Typeface.BOLD), at, at + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvInstruction.text = spanned
        val et: EditText = view.findViewById(R.id.etConfirmWord)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.delete_forever, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            // Grey until the word matches, then the app's red for what cannot be undone (night-aware).
            positive.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(
                        ContextCompat.getColor(activity, R.color.text_muted),
                        ContextCompat.getColor(activity, R.color.red_gave_text)
                    )
                )
            )
            positive.isEnabled = false
            positive.setOnClickListener {
                positive.isEnabled = false
                confirm(dialog)
            }
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    positive.isEnabled = matches(s?.toString())
                }
            })
        }
        dialog.show()
        // Full screen (10 Nov 2026, the owner's photo): a floating dialog over a tall keyboard kept the explanation
        // and cut the typing box away. Now the explanation scrolls in whatever is left; the instruction, the box
        // and the buttons sit on the keyboard. The 30% cap stays only if the window could not be made full.
        CalcPad.fullScreen(dialog, view)
        if (dialog.window?.attributes?.height == android.view.ViewGroup.LayoutParams.MATCH_PARENT) scroll.maxPx = Int.MAX_VALUE
        KeyboardFollow.dialog(dialog)
        // Opened to be filled in: the cursor is in the box and the keyboard is up.
        FormFocus.focusFirstEmpty(listOf(et), true, dialog.window)
        return dialog
    }
}
