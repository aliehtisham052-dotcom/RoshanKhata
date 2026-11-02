package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.ReminderLadder.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderLadderTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_000 * day

    @Test
    fun `first reminder is gentle, then plain, then serious, and serious stays serious`() {
        assertEquals(1, ReminderLadder.step(null, 12_000.0, t0))
        var s = ReminderLadder.next(null, 12_000.0, t0)
        assertEquals(2, ReminderLadder.step(s, 12_000.0, t0 + 5 * day))
        s = ReminderLadder.next(s, 12_000.0, t0 + 5 * day)
        assertEquals(3, ReminderLadder.step(s, 12_000.0, t0 + 10 * day))
        s = ReminderLadder.next(s, 12_000.0, t0 + 10 * day)
        assertEquals(3, ReminderLadder.step(s, 12_000.0, t0 + 15 * day))
        assertEquals(3, s.count)
        assertEquals(t0, s.firstAt)
    }

    @Test
    fun `a payment since the last reminder starts the chase again, at the step he paid after`() {
        // He paid after the plain one (2 Nov 2026, the Payment Radar): the next chase starts plain, and says so.
        val s = State(2, t0, t0 + 5 * day, 12_000.0)
        assertEquals(2, ReminderLadder.learned(s, 9_000.0, t0 + 6 * day))
        assertEquals(2, ReminderLadder.step(s, 9_000.0, t0 + 6 * day))
        assertEquals(State(2, t0 + 6 * day, t0 + 6 * day, 9_000.0, worked = 2), ReminderLadder.next(s, 9_000.0, t0 + 6 * day))
        // One who paid after the gentle one is never pushed past it.
        val gentle = State(1, t0, t0, 12_000.0)
        assertEquals(1, ReminderLadder.step(gentle, 9_000.0, t0 + day))
    }

    @Test
    fun `what worked is kept through a chase that runs quiet, and reads back from its stored form`() {
        val learnt = State(3, t0, t0 + 5 * day, 12_000.0, worked = 2)
        // Sixty quiet days end the chase; the next starts at the step that worked last time, not gently.
        assertEquals(2, ReminderLadder.step(learnt, 12_000.0, t0 + 70 * day))
        assertEquals(learnt, ReminderLadder.decode(ReminderLadder.encode(learnt)))
        assertEquals("3,${t0},${t0 + 5 * day},12000.0,2", ReminderLadder.encode(learnt))
        // Nothing learnt: the old four fields, as every phone has them.
        assertEquals("2,10,20,300.5", ReminderLadder.encode(State(2, 10L, 20L, 300.5)))
    }

    @Test
    fun `more udhar does not reset the chase, sixty quiet days do`() {
        val s = State(2, t0, t0 + 5 * day, 12_000.0)
        assertEquals(3, ReminderLadder.step(s, 15_000.0, t0 + 6 * day))
        assertEquals(3, ReminderLadder.step(s, 12_000.0, t0 + 65 * day))
        assertEquals(1, ReminderLadder.step(s, 12_000.0, t0 + 66 * day))
    }
}
