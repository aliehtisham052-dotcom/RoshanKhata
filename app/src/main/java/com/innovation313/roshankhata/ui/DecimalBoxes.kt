package com.innovation313.roshankhata.ui

import android.text.InputType
import android.text.Spanned
import android.text.method.DigitsKeyListener
import android.text.method.NumberKeyListener
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.innovation313.roshankhata.data.Digits

/**
 * A number box with a fraction (a quantity, a rate, a price, a tax %) takes the decimal mark the shop's language
 * writes, and the digits of every keyboard the app reads (3 Nov 2026 audit, M41).
 *
 * Android's own filter for `inputType="numberDecimal"` passes only 0-9 and "." unless the box names its
 * keyboard's language: an Indonesian owner's "2,5" became "25" — at Rp 4.000 a line of Rp 100.000 instead of
 * Rp 10.000 — and a keyboard typing its own digits (٢, ۲, २, ২) typed nothing. Each such box under a view
 * given to [TextFit.relax] (and each built by [FormBox.style]) now takes 0-9 and those digits ([Digits.toLatin]
 * reads them), "." and the Arabic "٫", and where the comma marks the fraction ([Digits.decimalComma]) the comma
 * too, read by [Digits.parse] with the language's rule. The keyboard asked for is unchanged.
 *
 * A box with its own characters (`android:digits`, the amount pad's boxes) is left alone: its key listener
 * does not answer for the box's input type, and only Android's stock decimal listener is replaced.
 */
object DecimalBoxes {

    /** Widen every stock decimal box under [root]. */
    fun apply(root: View?) {
        if (root == null) return
        if (root is EditText) widen(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i))
    }

    /** One box, once; a box that is not a stock decimal box is left as it is. */
    fun widen(box: EditText) {
        val type = box.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_NUMBER) return
        if (type and InputType.TYPE_NUMBER_FLAG_DECIMAL == 0) return
        if (type and InputType.TYPE_MASK_VARIATION != InputType.TYPE_NUMBER_VARIATION_NORMAL) return
        val current = box.keyListener
        if (current is Keys) return
        // Android's own listener for this input type answers for exactly it; a box's own characters do not.
        if (current !is DigitsKeyListener || current.inputType != type) return
        box.keyListener = Keys(type, Digits.decimalComma())
    }

    /**
     * Whether [text] is a figure being typed: digits of any script the app reads, a sign only at the start of a
     * [signed] box, and one mark for the fraction — "." or "٫", or where the [comma] marks it, "," (the dots are
     * then the thousands, as many as written). Public for the test.
     */
    fun shapeOk(text: String, signed: Boolean, comma: Boolean): Boolean {
        var marks = 0
        for ((i, c) in text.withIndex()) {
            when {
                c == '-' || c == '+' -> if (!signed || i != 0) return false
                c == '.' -> if (!comma) marks++
                c == '٫' -> marks++
                c == ',' -> if (comma) marks++ else return false
                c == '٬' -> Unit   // the Arabic thousands separator: dropped when read
                isDigit(c) -> Unit
                else -> return false
            }
        }
        return marks <= 1
    }

    private fun isDigit(c: Char): Boolean =
        c in '0'..'9' || c in '٠'..'٩' || c in '۰'..'۹' || c in '०'..'९' || c in '০'..'৯'

    private const val DIGITS = "0123456789" +
        "٠١٢٣٤٥٦٧٨٩" +
        "۰۱۲۳۴۵۶۷۸۹" +
        "०१२३४५६७८९" +
        "০১২৩৪৫৬৭৮৯"

    /**
     * The keys: the characters above, the box's own input type kept (so the keyboard is the one it asked for),
     * and each change checked whole ([shapeOk]) — a second decimal mark or a sign inside the figure is refused
     * as Android's own listener refuses them. Deleting is never refused.
     */
    private class Keys(private val type: Int, private val comma: Boolean) : NumberKeyListener() {
        private val signed = type and InputType.TYPE_NUMBER_FLAG_SIGNED != 0
        private val accepted: CharArray =
            (DIGITS + ".٫٬" + (if (comma) "," else "") + (if (signed) "-+" else "")).toCharArray()

        override fun getAcceptedChars(): CharArray = accepted

        override fun getInputType(): Int = type

        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            val kept = super.filter(source, start, end, dest, dstart, dend)
            if (start == end) return kept
            val piece = (kept ?: source.subSequence(start, end)).toString()
            if (piece.isEmpty()) return kept
            val after = StringBuilder(dest.length - (dend - dstart) + piece.length)
                .append(dest, 0, dstart).append(piece).append(dest, dend, dest.length).toString()
            return if (shapeOk(after, signed, comma)) kept else ""
        }
    }
}
