package com.innovation313.roshankhata.words

import com.ibm.icu.text.PluralRules
import java.util.Locale

actual fun pluralCategory(locale: Locale, n: Int): String = PluralRules.forLocale(locale).select(n.toDouble())

/** The computer's words: the strings.xml files packed with the app, under words/. */
fun desktopWords(locale: Locale = Locale.getDefault()): XmlWords =
    XmlWords(locale) { folder, file -> XmlWords::class.java.getResourceAsStream("/words/$folder/$file") }
