package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * One look for every text box in a form (30 Oct 2026, the owner: the bill's boxes looked professional, the
 * product's label form and the small dialogs were bare lines). The layouts give their boxes
 * `@style/EditorField`; a box built in code gets the same here — the outlined fill, its padding, 48 dp at the
 * least, 15 sp ink with muted hints — and [HintFit], so a long hint wraps instead of being cut.
 */
object FormBox {

    /** [@style/EditorField] on a box made in code. */
    fun <T : EditText> style(box: T): T = box.apply {
        val d = resources.displayMetrics.density
        background = ContextCompat.getDrawable(context, R.drawable.bg_editor_field)
        setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        minHeight = (48 * d).toInt()
        textSize = 15f
        setTextColor(ContextCompat.getColor(context, R.color.ink))
        setHintTextColor(ContextCompat.getColor(context, R.color.text_muted))
        HintFit.apply(this)
    }

    /**
     * A dialog's body built in code: [views] in one column with the dialog's side margins and 8 dp between
     * them, so boxed fields never touch (a bare line needed no gap; a box does).
     */
    fun column(context: Context, vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val d = resources.displayMetrics.density
        setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) topMargin = (8 * d).toInt() })
        }
    }
}
