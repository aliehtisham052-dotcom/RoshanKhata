package com.innovation313.roshankhata.ui

/** The computer has no androidx BidiFormatter: its rules, ported (BidiPort). */
actual object BidiWrap {
    actual fun ltr(text: String): String = BidiPort.ltr(text)

    actual fun auto(text: String): String = BidiPort.auto(text)
}
