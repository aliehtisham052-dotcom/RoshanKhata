package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.ShopCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone's reminders and invoice sends add the SHOP'S calling code, never a fixed +92. */
class ReminderNumberTest {
    @Test
    fun `a local number takes the shop's country on the phone too`() {
        val before = ShopCountry.current
        try {
            ShopCountry.current = ShopCountry.PAKISTAN
            assertEquals("923001234567", Reminder.toWhatsAppNumber("0300 1234567"))
            ShopCountry.current = ShopCountry.BANGLADESH
            assertEquals("8801712345678", Reminder.toWhatsAppNumber("01712345678"))
            // An international number keeps its own code.
            assertEquals("923001234567", Reminder.toWhatsAppNumber("+92 300 1234567"))
            // A country the app does not know gives no link (the no_phone_number toast), not +92.
            ShopCountry.current = ShopCountry.OTHER
            assertNull(Reminder.toWhatsAppNumber("0300 1234567"))
        } finally {
            ShopCountry.current = before
        }
    }
}
