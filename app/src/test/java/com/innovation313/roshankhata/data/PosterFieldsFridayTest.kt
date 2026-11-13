package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The poster's opening hours offer the Friday-prayer row by default only where the country is an OIC member
 * (13 Nov 2026, the poster review): a shop in India or Fiji was handed a "Friday prayers" row to find and switch off.
 * What the owner chose is kept whatever the country says.
 */
class PosterFieldsFridayTest {

    @Test
    fun theDefaultFollowsTheCountry() {
        val before = ShopCountry.current
        try {
            ShopCountry.current = ShopCountry.PAKISTAN
            assertTrue(PosterFields().prayerBreak)
            ShopCountry.current = ShopCountry.SAUDI_ARABIA
            assertTrue(PosterFields().prayerBreak)
            ShopCountry.current = ShopCountry.INDIA
            assertFalse(PosterFields().prayerBreak)
            ShopCountry.current = ShopCountry.UNITED_KINGDOM
            assertFalse(PosterFields().prayerBreak)
        } finally {
            ShopCountry.current = before
        }
    }

    @Test
    fun theOwnersChoiceIsKept() {
        val before = ShopCountry.current
        try {
            ShopCountry.current = ShopCountry.PAKISTAN
            assertFalse(PosterFields(prayerBreak = false).prayerBreak)
            ShopCountry.current = ShopCountry.INDIA
            assertTrue(PosterFields(prayerBreak = true).prayerBreak)
        } finally {
            ShopCountry.current = before
        }
    }
}
