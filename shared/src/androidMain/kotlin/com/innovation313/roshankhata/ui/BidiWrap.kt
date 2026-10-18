package com.innovation313.roshankhata.ui

import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat

actual object BidiWrap {
    actual fun ltr(text: String): String =
        BidiFormatter.getInstance().unicodeWrap(text, TextDirectionHeuristicsCompat.LTR)

    actual fun auto(text: String): String =
        BidiFormatter.getInstance().unicodeWrap(text)
}
