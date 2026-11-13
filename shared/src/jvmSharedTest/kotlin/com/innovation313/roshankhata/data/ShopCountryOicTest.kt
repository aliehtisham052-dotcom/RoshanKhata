package com.innovation313.roshankhata.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One list of the OIC's member states (13 Nov 2026): Hudhud's salaam and the poster's Friday-prayer default (the
 * phone's PosterFieldsFridayTest) both read [ShopCountry.oic], so the two cannot disagree.
 */
class ShopCountryOicTest {

    @Test fun `the countries that rest on Friday are all members`() {
        for (c in ShopCountry.entries) {
            if (c.weeklyOff == Calendar.FRIDAY) assertTrue(c.name, c.oic)
        }
    }

    @Test fun `known members and non members`() {
        for (c in listOf(ShopCountry.PAKISTAN, ShopCountry.INDONESIA, ShopCountry.NIGERIA, ShopCountry.TURKEY, ShopCountry.PALESTINE)) assertTrue(c.name, c.oic)
        for (c in listOf(ShopCountry.INDIA, ShopCountry.UNITED_KINGDOM, ShopCountry.UNITED_STATES, ShopCountry.CANADA,
            ShopCountry.AUSTRALIA, ShopCountry.KENYA, ShopCountry.SOUTH_AFRICA, ShopCountry.NEPAL, ShopCountry.FIJI, ShopCountry.OTHER)) assertFalse(c.name, c.oic)
    }

    @Test fun `Hudhud says salaam exactly where the country is a member`() {
        for (c in ShopCountry.entries) {
            assertEquals(c.name, c.oic, Hudhud.greeting(c, 15) == Hudhud.Greeting.SALAAM)
        }
    }
}
