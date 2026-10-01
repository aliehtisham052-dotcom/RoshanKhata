package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** "Who do I remind today": promise due, then late by habit, then quiet longest. */
class FollowUpRankTest {

    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_790_000_000_000L

    private fun p(id: Long, last: Long, balance: Double = 1000.0) =
        PartyWithBalance(id = id, name = "P$id", phone = null, isCustomer = true, photoPath = null, balance = balance, lastActivity = last)

    @Test
    fun `a promise that has come due goes to the top, oldest promise first`() {
        val list = listOf(p(1, now - 90 * day), p(2, now - 5 * day), p(3, now - 5 * day))
        val promises = mapOf(2L to now - 2 * day, 3L to now - 6 * day)
        assertEquals(listOf(3L, 2L, 1L), FollowUpRank.order(list, emptyMap(), promises, now, tz).map { it.id })
    }

    @Test
    fun `a promise still in the future is not a reason to remind`() {
        val promises = mapOf(1L to now + 3 * day)
        assertEquals(false, FollowUpRank.remindToday(1, emptyMap(), promises, now, tz))
        val list = listOf(p(1, now - 1 * day), p(2, now - 40 * day))
        assertEquals(listOf(2L, 1L), FollowUpRank.order(list, emptyMap(), promises, now, tz).map { it.id })
    }

    @Test
    fun `late by habit comes after a due promise and before the quiet ones`() {
        val habits = mapOf(3L to PaymentHabit.Habit(typicalDays = 10, waitingDays = 30, isLate = true))
        val list = listOf(p(1, now - 90 * day), p(2, now), p(3, now))
        val promises = mapOf(2L to now)
        assertEquals(listOf(2L, 3L, 1L), FollowUpRank.order(list, habits, promises, now, tz).map { it.id })
        assertEquals(true, FollowUpRank.remindToday(3, habits, promises, now, tz))
        assertEquals(false, FollowUpRank.remindToday(1, habits, promises, now, tz))
    }

    @Test
    fun `a customer reminded today drops to the bottom and is not counted`() {
        val promises = mapOf(1L to now - day)
        val list = listOf(p(1, now - 90 * day), p(2, now - 5 * day))
        assertEquals(listOf(2L, 1L), FollowUpRank.order(list, emptyMap(), promises, now, tz, setOf(1L)).map { it.id })
        assertEquals(false, FollowUpRank.remindToday(1, emptyMap(), promises, now, tz, setOf(1L)))
    }
}
