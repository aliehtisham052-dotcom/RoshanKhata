package com.innovation313.roshankhata.ui

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
    fun bind(root: View, field: EditText) {
        fun append(ch: String) {
            field.append(ch)
            field.setSelection(field.text.length)
        }
        fun key(id: Int, ch: String) = root.findViewById<Button>(id).setOnClickListener { append(ch) }

        key(R.id.calc0, "0"); key(R.id.calc00, "00"); key(R.id.calc1, "1")
        key(R.id.calc2, "2"); key(R.id.calc3, "3"); key(R.id.calc4, "4")
        key(R.id.calc5, "5"); key(R.id.calc6, "6"); key(R.id.calc7, "7")
        key(R.id.calc8, "8"); key(R.id.calc9, "9"); key(R.id.calcDot, ".")
        key(R.id.calcPlus, "+"); key(R.id.calcMinus, "\u2212")
        key(R.id.calcTimes, "\u00d7"); key(R.id.calcDivide, "\u00f7")
        key(R.id.calcPercent, "%")

        root.findViewById<Button>(R.id.calcClear).setOnClickListener { field.setText("") }

        val backspace = View.OnClickListener {
            val t = field.text
            if (t.isNotEmpty()) t.delete(t.length - 1, t.length)
        }
        root.findViewById<Button>(R.id.calcBackKey).setOnClickListener(backspace)
        root.findViewById<Button>(R.id.calcBack)?.setOnClickListener(backspace)

        root.findViewById<Button>(R.id.calcEquals).setOnClickListener {
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
}
