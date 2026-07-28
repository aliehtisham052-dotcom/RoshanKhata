package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.ui.DuplicateDetector.Candidate
import com.innovation313.roshankhata.ui.DuplicateDetector.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private fun c(id: Long, name: String, phone: String? = null, balance: Double = 0.0) =
        Candidate(id, name, phone, isCustomer = true, balance = balance)

    @Test
    fun `names that fold the same are grouped as NAME`() {
        val all = listOf(
            c(1, "Bavistin"),
            c(2, "Bavisteen"),
            c(3, "Unrelated Person")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(1, groups.size)
        assertEquals(Reason.NAME, groups[0].reason)
        assertEquals(setOf(1L, 2L), groups[0].members.map { it.partyId }.toSet())
    }

    @Test
    fun `same phone under different formatting is grouped as PHONE`() {
        val all = listOf(
            c(1, "Akram", phone = "0301-1234567"),
            c(2, "Muhammad Akram Store", phone = "+92 301 1234567"),
            c(3, "Someone Else", phone = "0300-9999999")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(1, groups.size)
        assertEquals(Reason.PHONE, groups[0].reason)
        assertEquals(setOf(1L, 2L), groups[0].members.map { it.partyId }.toSet())
    }

    @Test
    fun `a group matching on both name and phone is marked BOTH, not shown twice`() {
        val all = listOf(
            c(1, "Asghar", phone = "03011234567"),
            c(2, "Asgar", phone = "03011234567")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(1, groups.size)
        assertEquals(Reason.BOTH, groups[0].reason)
    }

    @Test
    fun `distinct people with distinct names and numbers are never grouped`() {
        val all = listOf(
            c(1, "Bilal", phone = "03011111111"),
            c(2, "Kashif", phone = "03022222222"),
            c(3, "Waseem", phone = null)
        )
        assertTrue(DuplicateDetector.find(all).isEmpty())
    }

    @Test
    fun `two parties with no phone at all are not grouped by phone`() {
        val all = listOf(
            c(1, "Ali", phone = null),
            c(2, "Kashif", phone = "")
        )
        assertTrue(DuplicateDetector.find(all).isEmpty())
    }

    @Test
    fun `a short number typed by mistake does not pull in unrelated short numbers`() {
        val all = listOf(
            c(1, "Ali", phone = "123"),
            c(2, "Kashif", phone = "456")
        )
        assertTrue(DuplicateDetector.find(all).isEmpty())
    }

    @Test
    fun `a group can hold three or more members`() {
        val all = listOf(
            c(1, "Khurpa"),
            c(2, "Kurpa"),
            c(3, "Khurpaa"),
            c(4, "Nothing Alike")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].members.size)
    }

    @Test
    fun `a party sharing a name with one record and a phone with an unrelated one appears in two groups`() {
        // Deliberately not chained into one three-way suggestion — see the
        // class doc. Party 2 sits in both a NAME group with 1 and a PHONE
        // group with 3, and both should surface.
        val all = listOf(
            c(1, "Asghar"),
            c(2, "Asgar", phone = "03011234567"),
            c(3, "Totally Different Name", phone = "03011234567")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(2, groups.size)
        val reasons = groups.map { it.reason }.toSet()
        assertEquals(setOf(Reason.NAME, Reason.PHONE), reasons)
    }

    @Test
    fun `groups are ordered largest first`() {
        val all = listOf(
            c(1, "Khurpa"), c(2, "Kurpa"), c(3, "Khurpaa"),
            c(4, "Bavistin"), c(5, "Bavisteen")
        )
        val groups = DuplicateDetector.find(all)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].members.size)
        assertEquals(2, groups[1].members.size)
    }
}
