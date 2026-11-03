package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DocChain

/**
 * A document stepped on (3 Nov 2026, the owner's mockup "Documents — the chain"; the new PDF used to open with
 * nothing said): the sheet rises with the new document's kind and number and what it was made from, and its
 * chain plays the step just taken — the line fills, the old step takes its check, the new one arrives. Preview
 * opens the new document as before; Close leaves it in the list. With animations off it is all there at once.
 */
object DocConvertedSheet {

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
                    addView(TextView(activity).apply {
                        text = Format.ltr(n)
                        textSize = 11f
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(muted)
                    })
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val sheet = BottomSheetDialog(activity)
        view.findViewById<MaterialButton>(R.id.btnDocOpen).setOnClickListener { sheet.dismiss(); onOpen() }
        view.findViewById<MaterialButton>(R.id.btnDocClose).setOnClickListener { sheet.dismiss() }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { if (sheet.isShowing) sheet.dismiss() }
        })
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
