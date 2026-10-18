package com.innovation313.roshankhata.words

import android.icu.text.PluralRules
import java.util.Locale

actual fun pluralCategory(locale: Locale, n: Int): String = PluralRules.forLocale(locale).select(n.toDouble())
