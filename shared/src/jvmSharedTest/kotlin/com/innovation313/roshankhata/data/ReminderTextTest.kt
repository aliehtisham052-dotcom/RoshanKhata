package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reminder's words at each step of the ladder (Marhala 4, 25 Oct 2026: shared, so the phone's and the
 * computer's say the same), and the ladder's stored form, which both platforms keep.
 */
class ReminderTextTest {

    /** Words that say which string was asked for and with what. */
    private val words = object : Words {
        override fun text(name: String, vararg args: Any) = name + args.joinToString(",", prefix = "(", postfix = ")")
        override fun plural(name: String, count: Int, vararg args: Any) = name
        override fun list(name: String) = emptyList<String>()
    }

    @Test
    fun `each step has its own words, and only money owed climbs`() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0).startsWith("reminder_they_owe("))
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0, promisedDate = 20 * day).startsWith("reminder_they_owe_by_date("))
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0, step = ReminderLadder.PLAIN, firstSentAt = 10 * day).startsWith("reminder_step2("))
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0, step = ReminderLadder.SERIOUS).startsWith("reminder_step3("))
        // Owed by the shop: a courtesy note, whatever step was asked.
        assertTrue(ReminderText.body(words, "Seeds Co.", -1_200.0, step = ReminderLadder.SERIOUS).startsWith("reminder_i_owe("))
        // The shop's name under it, only when it has one.
        assertEquals("x", ReminderText.signed("x", null))
        val signed = ReminderText.signed("x", "Al-Noor")
        assertTrue(signed.startsWith("x\n\n\u2014 ") && signed.contains("Al-Noor"))
    }

    @Test
    fun `the sale being chased sits with the amount, before the closing, and never on a courtesy note`() {
        // The reference is the last argument of each text that asks for money, so the string puts it under the amount.
        val gentle = ReminderText.body(words, "Aslam", 5_000.0, reference = "RK-000013")
        assertTrue(gentle, gentle.startsWith("reminder_they_owe(") && gentle.contains("reminder_ref("))
        assertTrue(gentle, gentle.contains("RK-000013"))
        val serious = ReminderText.body(words, "Aslam", 5_000.0, step = ReminderLadder.SERIOUS, reference = "RK-000013")
        assertTrue(serious, serious.startsWith("reminder_step3(") && serious.contains("RK-000013"))
        val plain = ReminderText.body(words, "Aslam", 5_000.0, step = ReminderLadder.PLAIN, reference = "RK-000013")
        assertTrue(plain, plain.contains("RK-000013"))
        // Nothing to quote, or blank: the last argument is empty.
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0).endsWith(",)"))
        assertEquals(ReminderText.body(words, "Aslam", 5_000.0), ReminderText.body(words, "Aslam", 5_000.0, reference = null))
        assertEquals(ReminderText.body(words, "Aslam", 5_000.0), ReminderText.body(words, "Aslam", 5_000.0, reference = "  "))
        // Money the shop owes: no sale is being chased.
        assertEquals(ReminderText.body(words, "Seeds Co.", -1_200.0), ReminderText.body(words, "Seeds Co.", -1_200.0, reference = "RK-000013"))
    }

    @Test
    fun `a party saved under only a phone number is greeted without it`() {
        for (number in listOf("+923001234567", "0300 1234567", "  ", "")) {
            val text = ReminderText.body(words, number, 5_000.0)
            assertTrue(number + " -> " + text, text.startsWith("reminder_they_owe(reminder_greeting_neutral(),"))
            assertTrue(!text.contains(number.trim()) || number.isBlank())
        }
        assertTrue(ReminderText.body(words, "Aslam", 5_000.0).startsWith("reminder_they_owe(reminder_greeting("))
        // A name with a digit in it is still a name.
        assertTrue(ReminderText.body(words, "Shop 2", 5_000.0).startsWith("reminder_they_owe(reminder_greeting("))
        assertTrue(ReminderText.body(words, "+923001234567", -100.0, step = ReminderLadder.SERIOUS).startsWith("reminder_i_owe(reminder_greeting_neutral(),"))
    }

    @Test
    fun `how to pay is one block on both platforms, and nothing when off or empty`() {
        val block = ReminderText.paymentBlock(words, "03001234567", "HBL", "Ali Traders", "PK36HABB0001")
        assertEquals("\n\npay_heading()\npay_wallet_label(): 03001234567\npay_bank_label(): HBL · Ali Traders · PK36HABB0001", block)
        assertEquals("", ReminderText.paymentBlock(words, "03001234567", "HBL", "Ali Traders", "PK36HABB0001", enabled = false))
        assertEquals("", ReminderText.paymentBlock(words, null, "HBL", "Ali Traders", null))
        // The whole message as the phone and the computer both put it together: body, reference, how to pay, the shop.
        val whole = ReminderText.signed(ReminderText.body(words, "Aslam", 5_000.0, reference = "RK-000013") + block, "Al-Noor")
        val order = listOf("reminder_they_owe(", "reminder_ref(", "pay_heading()", "pay_wallet_label()", "\u2014 ").map { whole.indexOf(it) }
        assertTrue(order.toString(), order.all { it >= 0 } && order == order.sorted())
    }

    @Test
    fun `the ladder's stored form reads back, and anything else is nothing`() {
        val s = ReminderLadder.next(null, 12_000.0, 1_000L)
        assertEquals(s, ReminderLadder.decode(ReminderLadder.encode(s)))
        // The phone's preferences written before the move read the same.
        assertEquals(ReminderLadder.State(2, 10L, 20L, 300.5), ReminderLadder.decode("2,10,20,300.5"))
        assertNull(ReminderLadder.decode(null))
        assertNull(ReminderLadder.decode("2,10,20"))
        assertNull(ReminderLadder.decode("two,10,20,300"))
    }

    @Test
    fun `the step the owner sent is the one the ladder remembers`() {
        val day = 86_400_000L
        // The ladder offered Gentle; the owner chose Serious (3 Nov 2026 audit, M33).
        val sent = ReminderLadder.next(null, 12_000.0, 1_000L, sent = ReminderLadder.SERIOUS)
        assertEquals(ReminderLadder.SERIOUS, sent.count)
        // He paid two days later: Serious is what worked, not Gentle.
        assertEquals(ReminderLadder.SERIOUS, ReminderLadder.learned(sent, 9_000.0, 1_000L + 2 * day))
    }
}
