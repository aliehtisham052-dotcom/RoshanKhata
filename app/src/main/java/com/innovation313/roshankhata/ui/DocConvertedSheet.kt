package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DocChain
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.quantity

/**
 * A document stepped on (3 Nov 2026, the owner's mockup "Documents — the chain"; the new PDF used to open with
 * nothing said): the sheet rises with the new document's kind and number and what it was made from, and its
 * chain plays the step just taken — the line fills, the old step takes its check, the new one arrives. Preview
 * opens the new document as before; Close leaves it in the list. With animations off it is all there at once.
 */
object DocConvertedSheet {

    /**
     * Who the new document is for and what it holds — "Test Customer · 3 items · Rs 54,000" — the line [show] is
     * given. A challan prints no prices (DocKind.showsPrices), so its line says none either; the sign and its figure
     * stay on one line (a no-break space) whichever way the line runs. The convert and its proof picture both say it.
     */
    fun line(res: android.content.res.Resources, name: String, items: Int, total: Double, kind: String): String = listOfNotNull(
        name,
        Digits.quantity(res, R.plurals.items_count, items, items),
        if (com.innovation313.roshankhata.data.DocKind.showsPrices(kind)) Format.ltr(Format.money(total).replace(' ', '\u00A0')) else null
    ).joinToString(" \u00B7 ")

    fun show(
        activity: Activity,
        chain: List<DocChain.Node>,
        /** The kind's word for a [DocChain.Node.kind]. */
        kindWord: (String) -> String,
        /** Who it is for and what it holds: "Test Customer · 3 items · Rs 54,000". */
        what: String,
        onOpen: () -> Unit
    ): BottomSheetDialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_doc_converted, null)
        // The document just made is the last with a number; the one before it is what it was made from.
        val current = chain.indexOfLast { it.number != null }.coerceAtLeast(0)
        val made = chain.getOrNull(current)
        val from = chain.getOrNull(current - 1)
        view.findViewById<TextView>(R.id.tvDocTitle).text =
            listOfNotNull(made?.let { kindWord(it.kind) }, made?.number?.let { Format.ltr(it) }).joinToString(" · ")
        view.findViewById<TextView>(R.id.tvDocFrom).apply {
            val number = from?.number
            if (number != null) text = activity.getString(R.string.doc_converted_from, Format.ltr(number))
            else visibility = android.view.View.GONE
        }
        view.findViewById<TextView>(R.id.tvDocWhat).text = what

        val marks = view.findViewById<DocChainMarks>(R.id.docChain)
        marks.set(chain.size, current, playing = true)
        val words = view.findViewById<LinearLayout>(R.id.docChainWords)
        val ink = ContextCompat.getColor(activity, R.color.ink)
        val faint = ContextCompat.getColor(activity, R.color.text_faint)
        val muted = ContextCompat.getColor(activity, R.color.text_muted)
        chain.forEachIndexed { i, node ->
            words.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(activity).apply {
                    text = kindWord(node.kind)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setTextColor(if (i <= current) ink else faint)
                    if (i == current) setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                node.number?.let { n ->
                    // AppCompat's view: below Android 8.1 a plain TextView ignores the size it is told to fit in.
                    addView(androidx.appcompat.widget.AppCompatTextView(activity).apply {
                        text = Format.ltr(n)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setTextColor(muted)
                        // A whole number always: smaller when its step's share of the width is narrow (150% text).
                        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this, 7, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP
                        )
                    }, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, activity.resources.displayMetrics).toInt()
                    ))
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val sheet = BottomSheetDialog(activity)
        view.findViewById<MaterialButton>(R.id.btnDocOpen).setOnClickListener { sheet.dismiss(); onOpen() }
        view.findViewById<MaterialButton>(R.id.btnDocClose).setOnClickListener { sheet.dismiss() }
        DialogLife.bind(activity, sheet)
        TextFit.relax(view)
        sheet.setContentView(view)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        // The step itself once the sheet is up: the words first, then the chain.
        MotionKit.riseIn(view.findViewById(R.id.tvDocTitle), delay = 60, duration = 200)
        marks.postDelayed({ marks.stepOn() }, if (Motion.enabled(activity)) 160L else 0L)
        return sheet
    }
}
