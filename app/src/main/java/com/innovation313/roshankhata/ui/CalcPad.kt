package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * The amount pad (view_calculator.xml) and the box it types into.
 *
 * One binding for every form that writes an amount on the pad: the add-entry
 * form, and since 21 Oct the Edit entry dialog, which until then had only the
 * phone's number keyboard — no + − × ÷ %, no = (a user's report, sent on by
 * the owner). Two copies of the keys would drift apart; through this one the
 * pad behaves the same wherever it is.
 */
object CalcPad {

    /**
     * Every key of the pad under [root] types into [field]: digits, 00 and the
     * dot append, operators append, ⌫ deletes the last character, C clears, =
     * works the sum out in place. A backspace inside the amount box itself
     * (calcBack, on the add-entry form) is wired too when [root] has one.
     */
    fun bind(root: View, field: EditText) = bindKeys(root, root) { field }

    /**
     * The one table of keys. Every key under [pad] types into whatever
     * [target] names at the moment it is pressed (nothing, when it names
     * nothing); a backspace inside the amount box (calcBack) is looked for
     * under [backRoot].
     */
    private fun bindKeys(pad: View, backRoot: View, target: () -> EditText?) {
        fun append(ch: String) {
            val field = target() ?: return
            field.append(ch)
            field.setSelection(field.text.length)
        }
        fun key(id: Int, ch: String) = pad.findViewById<Button>(id).setOnClickListener { append(ch) }

        key(R.id.calc0, "0"); key(R.id.calc00, "00"); key(R.id.calc1, "1")
        key(R.id.calc2, "2"); key(R.id.calc3, "3"); key(R.id.calc4, "4")
        key(R.id.calc5, "5"); key(R.id.calc6, "6"); key(R.id.calc7, "7")
        key(R.id.calc8, "8"); key(R.id.calc9, "9"); key(R.id.calcDot, ".")
        key(R.id.calcPlus, "+"); key(R.id.calcMinus, "\u2212")
        key(R.id.calcTimes, "\u00d7"); key(R.id.calcDivide, "\u00f7")
        key(R.id.calcPercent, "%")

        pad.findViewById<Button>(R.id.calcClear).setOnClickListener { target()?.setText("") }

        val backspace = View.OnClickListener {
            val t = target()?.text ?: return@OnClickListener
            if (t.isNotEmpty()) t.delete(t.length - 1, t.length)
        }
        pad.findViewById<Button>(R.id.calcBackKey).setOnClickListener(backspace)
        backRoot.findViewById<Button>(R.id.calcBack)?.setOnClickListener(backspace)

        pad.findViewById<Button>(R.id.calcEquals).setOnClickListener {
            val field = target() ?: return@setOnClickListener
            // evalPad, not eval: it translates the pad's × ÷ − and resolves a
            // percentage before the arithmetic runs, so "1200−15%" comes out
            // as 1020 rather than as nothing at all. Written back as a
            // magnitude (abs), as everywhere else an amount box is read —
            // "7926−35554" lands back in the box as 27628, not −27628; a minus
            // sign has no meaning in an amount.
            val result = Calc.evalPad(field.text.toString())
            if (result != null) {
                field.setText(Calc.trim(kotlin.math.abs(result)))
                field.setSelection(field.text.length)
            }
        }
    }

    /**
     * The sum's running total under the box, as the Calculator screen answers
     * as you go. Only once there is arithmetic in the box: a plain "3500"
     * repeated underneath as "Rs 3,500" is noise.
     */
    fun showTotal(field: EditText, result: TextView) {
        val text = field.text.toString()
        val isSum = text.any { it in "+-\u2212*\u00d7/\u00f7%" }
        val value = if (isSum) Calc.evalPad(text) else null
        if (value == null) {
            result.visibility = View.GONE
        } else {
            result.text = Format.money(value)
            result.visibility = View.VISIBLE
        }
    }

    /**
     * What a box that may hold a sum is worth, as a magnitude, zero kept.
     *
     * [Calc.evalAmount] refuses zero, which is right for an amount to save
     * but wrong for a figure that may honestly be nothing: an empty drawer
     * counted at closing, a claim that brought nothing, "received: 0" on an
     * invoice. Null only for a blank or unreadable box — the same contract
     * as the Digits.parse these boxes were read with before they had the pad.
     */
    fun figure(text: CharSequence?): Double? =
        text?.toString()?.let { Calc.evalPad(it) }?.let { kotlin.math.abs(it) }

    /** What a box that takes the pad accepts, typed or added from code. */
    const val DIGITS = "0123456789.+−×÷*/-%"

    /**
     * A money box made in code (no layout to carry android:digits): the
     * pad's characters let through — the digits key-listener filters text
     * added from code too, so without this the pad's − × ÷ % are dropped —
     * and the phone keyboard asked for as the dialler pad. setRawInputType,
     * not inputType =: the latter would replace the key listener again.
     */
    fun moneyBox(field: EditText) {
        field.keyListener = DigitsKeyListener.getInstance(DIGITS)
        field.setRawInputType(InputType.TYPE_CLASS_PHONE)
        field.textDirection = View.TEXT_DIRECTION_LTR
        field.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** The running-total line for a dialog built in code: green, 13sp bold, gone until a sum. */
    fun resultLine(context: Context): TextView = TextView(context).apply {
        setTextColor(ContextCompat.getColor(context, R.color.brand_green_text))
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_LTR
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        maxLines = 1
        visibility = View.GONE
    }

    /**
     * The pad for a dialog built in code, ready to add to [parent]. Inflated
     * WITH its parent (not attached): inflate(layout, null) would drop the
     * layout's own width and height, and the pad needs its match_parent
     * width. A little space above it, set here for the same reason.
     */
    fun inflatePad(parent: ViewGroup): View {
        val pad = LayoutInflater.from(parent.context).inflate(R.layout.view_calculator, parent, false)
        (pad.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            (8 * parent.resources.displayMetrics.density).toInt()
        TextFit.relax(pad)
        return pad
    }

    /**
     * A money form as a full screen, with its pad docked like a keyboard (30 Oct 2026, the owner's photos
     * of Edit entry and Add bill: a small card floated over the page and its pad scrolled with the form, so
     * the pad's last row — 0, 00, the dot and "=" — sat cut off below the card's foot).
     *
     * Call right after [dialog] is shown, with [content] the view given to setView. The add-entry form's own
     * shape is made around it: [content] and [pad] go into one column in [content]'s place — the form above,
     * taking what height is left and scrolling, the pad under it, always whole — and the window fills the
     * screen ([fullScreen]). The dialog's own panels are not touched: AlertDialogLayout measures its title,
     * form and buttons itself and gives up that measuring when a fourth panel is added to it.
     * Whether the pad shows is still [bindAll]'s (or the form's own) rule: while an amount box has the
     * focus; a name or a note gets the phone's keyboard and the pad's room.
     *
     * Best effort, like [fillDialogHeight]: a dialog built some other way is left as it was.
     */
    fun dock(dialog: android.app.Dialog, content: View, pad: View) {
        try {
            val host = content.parent as? ViewGroup ?: return fullScreen(dialog, content)
            val at = host.indexOfChild(content)
            val keep = content.layoutParams
            host.removeView(content)
            (pad.parent as? ViewGroup)?.removeView(pad)
            val side = (16 * content.resources.displayMetrics.density).toInt()
            val column = LinearLayout(content.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(side, side / 4, side, side / 4) })
            }
            host.addView(column, at, keep ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            fullScreen(dialog, column)
        } catch (e: Exception) {
            // Leave the dialog as it was.
        }
    }

    /**
     * The window part of [dock] alone, for a form whose pad is already docked in its own layout (the
     * add-entry layout, which a plan's instalment also opens): full screen, [content] given the height.
     */
    fun fullScreen(dialog: android.app.Dialog, content: View) {
        try {
            dialog.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ContextCompat.getColor(context, R.color.page_bg)))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // A name or a note brings the phone's keyboard; the buttons ride above it.
                setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            content.fillDialogHeight()
        } catch (e: Exception) {
            // Leave the dialog as it was.
        }
    }

    /** One pad bound to several boxes; see [bindAll]. */
    class Binding internal constructor(private val onRefresh: () -> Unit) {
        /**
         * Call when whether a box takes the pad has changed (the invoice's
         * discount switched between % and money): the phone keyboard and
         * the pad are put right for the box that has the focus.
         */
        fun refresh() = onRefresh()
    }

    /**
     * One pad under [root] for several money boxes: it types into whichever
     * of [fields] was focused (or tapped) last.
     *
     * The rest of what the Edit entry dialog does by hand, once, for every
     * form: the phone keyboard is kept off these boxes; the pad shows while
     * one of them has the focus and steps aside when any other text box
     * takes it (a note, a name — they get the phone's keyboard and the room
     * the pad took); a tap on a box brings the pad back and puts the
     * keyboard away; each box in [results] shows its sum's running total.
     *
     * [usesPad] says whether a box takes the pad right now: the invoice's
     * discount does only while it is in money — "10" with a % switch is not
     * a sum, and a lone "10%" on the pad would come out as 0.10. A box that
     * does not gets the phone keyboard as before.
     *
     * The focus is followed through the window's global focus listener, not
     * each box's own: some boxes (a bill's total) already have one, and a
     * view keeps only one.
     */
    fun bindAll(
        root: View,
        pad: View,
        fields: List<EditText>,
        results: Map<EditText, TextView> = emptyMap(),
        usesPad: (EditText) -> Boolean = { true }
    ): Binding {
        var last: EditText? = fields.firstOrNull()
        val target: () -> EditText? = {
            fields.firstOrNull { it.isFocused && usesPad(it) } ?: last?.takeIf { usesPad(it) }
        }

        bindKeys(pad, root, target)

        val keyboard = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val showPad: (EditText) -> Unit = { field ->
            last = field
            pad.visibility = View.VISIBLE
            keyboard?.hideSoftInputFromWindow(field.windowToken, 0)
        }

        fields.forEach { field ->
            results[field]?.let { result ->
                field.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) = showTotal(field, result)
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
                showTotal(field, result)
            }
            field.setOnClickListener { if (usesPad(field)) showPad(field) }
        }

        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            if (now is EditText) {
                val mine = fields.firstOrNull { it === now }
                if (mine != null && usesPad(mine)) showPad(mine) else pad.visibility = View.GONE
            }
        }

        val refresh: () -> Unit = {
            fields.forEach { it.showSoftInputOnFocus = !usesPad(it) }
            val focused = fields.firstOrNull { it.isFocused }
            if (focused != null) {
                if (usesPad(focused)) {
                    showPad(focused)
                } else {
                    pad.visibility = View.GONE
                    keyboard?.showSoftInput(focused, 0)
                }
            }
        }
        refresh()
        return Binding(refresh)
    }
}
