package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.AppLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D17: Sindhi, Bengali, Farsi and Arabic are "beta" until a native read; the other five are not. */
class LanguageStatusTest {
    @Test fun `exactly the four unread languages are beta`() {
        val beta = AppLanguages.ALL.map { it.tag }.filter { LanguageStatus.isBeta(it) }
        assertEquals(listOf("sd", "fa", "ar", "bn"), beta)
    }

    @Test fun `every beta tag is one of the app's languages`() {
        val tags = AppLanguages.ALL.map { it.tag }.toSet()
        assertTrue(LanguageStatus.BETA.all { it in tags })
    }

    @Test fun `a region or a script does not change the answer`() {
        assertTrue(LanguageStatus.isBeta("ar-SA"))
        assertTrue(LanguageStatus.isBeta("bn_BD"))
        assertTrue(LanguageStatus.isBeta("FA"))
        assertTrue(LanguageStatus.isBeta("sd-PK"))
        assertFalse(LanguageStatus.isBeta("ur-Latn"))
        assertFalse(LanguageStatus.isBeta("ur-PK"))
        assertFalse(LanguageStatus.isBeta("in"))
        assertFalse(LanguageStatus.isBeta("en-US"))
    }

    @Test fun `no language is no beta`() {
        assertFalse(LanguageStatus.isBeta(null))
        assertFalse(LanguageStatus.isBeta(""))
        assertFalse(LanguageStatus.isBeta("  "))
    }
}
