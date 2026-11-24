package com.innovation313.roshankhata.data

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The digits a printed sheet shows (24 Nov 2026, plan C5; lesson 28): 0-9 on screens, storage, CSV and codes; a
 * language's own digits on a printed sheet only when the owner chose so ([Digits.nativeOnPrint]).
 *
 * What is held: which languages have digits of their own (Arabic, Sindhi, Farsi, Bengali), which marks go with them,
 * that Hindi, Urdu, Roman Urdu, English and Indonesian never change, and that a code (an invoice number, an email, an
 * IBAN, a unit with a Latin letter) is left as written. What is documented, not fixed: a figure that touches a Latin
 * letter ("5kg", "Rs1,250") counts as part of a code and stays 0-9; that is by [Digits.native]'s own rule.
 */
class PrintDigitsTest {

    private val ar = Locale("ar")
    private val sd = Locale("sd")
    private val fa = Locale("fa")
    private val bn = Locale("bn")
    private val before = Digits.nativeOnPrint

    @After
    fun putTheChoiceBack() {
        Digits.nativeOnPrint = before
    }

    @Test
    fun arabicDigitsAndMarks() {
        assertEquals("Rs \u0661\u066C\u0662\u0665\u0660\u066B\u0665\u0660", Digits.native("Rs 1,250.50", ar))
        assertEquals("\u0660 \u0661 \u0669", Digits.native("0 1 9", ar))
    }

    @Test
    fun sindhiUsesTheArabicIndicDigitsToo() {
        assertEquals("\u0661\u0662\u0663\u066C\u0664\u0665\u0666", Digits.native("123,456", sd))
    }

    @Test
    fun farsiHasItsOwnDigitsButArabicMarks() {
        assertEquals("\u06F1\u066C\u06F2\u06F5\u06F0\u066B\u06F5\u06F0", Digits.native("1,250.50", fa))
        assertEquals("\u06F0\u06F9", Digits.native("09", fa))
    }

    @Test
    fun bengaliDigitsKeepTheLatinMarks() {
        // Bengali groups and marks with , and . as written; only the digits change.
        assertEquals("\u09E7,\u09E8\u09EB\u09E6.\u09EB\u09E6", Digits.native("1,250.50", bn))
    }

    @Test
    fun languagesWithoutDigitsOfTheirOwnNeverChange() {
        for (tag in listOf("en", "ur", "hi", "in", "id", "fr")) {
            val l = Locale(tag)
            assertEquals(tag, "Rs 1,250.50 and 09", Digits.native("Rs 1,250.50 and 09", l))
            assertEquals(tag, false, Digits.hasOwnDigits(l))
        }
        // Roman Urdu is a script tag on Urdu: still 0-9.
        assertEquals("1,250", Digits.native("1,250", Locale.forLanguageTag("ur-Latn")))
        for (tag in listOf("ar", "sd", "fa", "bn")) assertEquals(tag, true, Digits.hasOwnDigits(Locale(tag)))
    }

    @Test
    fun aCodeStaysAsWritten() {
        assertEquals("INV-000123", Digits.native("INV-000123", ar))
        assertEquals("RK-000013-B", Digits.native("RK-000013-B", fa))
        assertEquals("PK36SCBL0000001123456702", Digits.native("PK36SCBL0000001123456702", bn))
        assertEquals("shop1@mail.example", Digits.native("shop1@mail.example", ar))
        assertEquals("www.shop1.example", Digits.native("www.shop1.example", ar))
    }

    @Test
    fun anAtSignAloneMarksAnAddressAsACode() {
        // No Latin letter here, only the @: still an address, still 0-9.
        assertEquals("123@456", Digits.native("123@456", ar))
    }

    @Test
    fun aPhoneNumberAndADateAreFigures() {
        assertEquals("\u0660\u0663\u0660\u0660 \u0661\u0662\u0663\u0664\u0665\u0666\u0667", Digits.native("0300 1234567", ar))
        assertEquals("\u0662\u0664/\u0661\u0661/\u0662\u0660\u0662\u0666", Digits.native("24/11/2026", ar))
    }

    @Test
    fun aSignAndSpacingAreKept() {
        assertEquals("-\u0661\u066C\u0662\u0665\u0660", Digits.native("-1,250", ar))
        assertEquals("\u2212 \u0665\u0660", Digits.native("\u2212 50", ar))
        assertEquals("\u0661  \u0662\t\u0663", Digits.native("1  2\t3", ar))
    }

    @Test
    fun aMarkNotBetweenDigitsIsLeftAlone() {
        assertEquals("\u0661,", Digits.native("1,", ar))
        assertEquals(".\u0665", Digits.native(".5", ar))
    }

    @Test
    fun aFigureTouchingALatinLetterIsTakenForACodeAndStays() {
        // Documented limit of Digits.native: the word is the unit of decision, so "5kg" and "Rs1,250" stay 0-9.
        assertEquals("5kg", Digits.native("5kg", ar))
        assertEquals("Rs1,250", Digits.native("Rs1,250", ar))
        // With a space they are two words and the figure is converted.
        assertEquals("\u0665 kg", Digits.native("5 kg", ar))
    }

    @Test
    fun noDigitsNothingChanges() {
        val s = "Total due"
        assertSame(s, Digits.native(s, ar))
    }

    @Test
    fun forPrintIsOffByDefaultAndFollowsTheSwitch() {
        Digits.nativeOnPrint = false
        assertEquals("Rs 1,250", Digits.forPrint("Rs 1,250", ar))
        Digits.nativeOnPrint = true
        assertEquals("Rs \u0661\u066C\u0662\u0665\u0660", Digits.forPrint("Rs 1,250", ar))
        // The switch never changes a language that has none.
        assertEquals("Rs 1,250", Digits.forPrint("Rs 1,250", Locale("ur")))
    }

    @Test
    fun aMoneyFigureWrappedInDirectionMarksStillConverts() {
        // Format.ltr puts LRM/LRE...PDF round a figure (BidiPort): the marks are no letters and no digits, so the
        // figure inside is still converted and the marks stay where they were.
        val lre = "\u202A"; val pdf = "\u202C"
        assertEquals("${lre}Rs \u0661\u066C\u0662\u0665\u0660$pdf", Digits.native("${lre}Rs 1,250$pdf", ar))
    }
}
