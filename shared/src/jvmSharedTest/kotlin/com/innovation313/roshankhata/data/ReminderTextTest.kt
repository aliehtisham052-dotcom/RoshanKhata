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
    fun `the ladder's stored form reads back, and anything else is nothing`() {
        val s = ReminderLadder.next(null, 12_000.0, 1_000L)
        assertEquals(s, ReminderLadder.decode(ReminderLadder.encode(s)))
        // The phone's preferences written before the move read the same.
        assertEquals(ReminderLadder.State(2, 10L, 20L, 300.5), ReminderLadder.decode("2,10,20,300.5"))
        assertNull(ReminderLadder.decode(null))
        assertNull(ReminderLadder.decode("2,10,20"))
        assertNull(ReminderLadder.decode("two,10,20,300"))
    }
}
