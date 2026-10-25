package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * A new business on the computer starts where the open one is (Marhala 4, part 8; the phone's copyRegion):
 * its country, currency sign and calendar, never over a choice the new shop already made.
 */
class ShopRegionTest {

    @Test
    fun `a new shop takes the open shop's country, sign and calendar, and keeps its own`() {
        val dir = Files.createTempDirectory("rk-region").toFile()
        try {
            val open = FileShopSettings(dir.resolve("shop.properties")).apply {
                setCountry(ShopCountry.INDIA); setCurrency("\u20b9"); setCalendar(ShopCalendar.Kind.GREGORIAN)
            }
            val fresh = FileShopSettings(dir.resolve("shop_b2.properties"))
            fresh.copyRegionFrom(open)
            assertEquals(ShopCountry.INDIA, fresh.country())
            assertEquals("\u20b9", fresh.currency())
            // Read again from the file: it was written, not only held.
            assertEquals(ShopCountry.INDIA, FileShopSettings(dir.resolve("shop_b2.properties")).country())

            val own = FileShopSettings(dir.resolve("shop_b3.properties")).apply { setCountry(ShopCountry.OTHER) }
            own.copyRegionFrom(open)
            assertEquals("a choice already made stays", ShopCountry.OTHER, own.country())
            assertEquals("\u20b9", own.currency())
        } finally {
            dir.deleteRecursively()
        }
    }
}
