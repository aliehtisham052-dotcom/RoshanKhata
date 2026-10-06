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
    fun `any payment since the last reminder starts the chase again`() {
        val s = State(2, t0, t0 + 5 * day, 12_000.0)
        assertEquals(1, ReminderLadder.step(s, 9_000.0, t0 + 6 * day))
        assertEquals(State(1, t0 + 6 * day, t0 + 6 * day, 9_000.0), ReminderLadder.next(s, 9_000.0, t0 + 6 * day))
    }

    @Test
    fun `more udhar does not reset the chase, sixty quiet days do`() {
        val s = State(2, t0, t0 + 5 * day, 12_000.0)
        assertEquals(3, ReminderLadder.step(s, 15_000.0, t0 + 6 * day))
        assertEquals(3, ReminderLadder.step(s, 12_000.0, t0 + 65 * day))
        assertEquals(1, ReminderLadder.step(s, 12_000.0, t0 + 66 * day))
    }
}
