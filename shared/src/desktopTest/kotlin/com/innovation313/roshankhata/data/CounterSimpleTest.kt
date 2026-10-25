package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The simple counter (24 Oct 2026, the owner: "ye kya bala bana di"): a sale
 * on account carries the customer's promised day as a khata entry does, and
 * the big-shop tools show only when the owner says so or the book already
 * uses one of them.
 */
class CounterSimpleTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-counter-simple").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    /** A ticket of one hand-typed line of [total] for [partyId], [paid] of it in cash. */
    private fun ticket(partyId: Long?, name: String?, total: Double, paid: Double): CounterDraft {
        val draft = CounterDraft()
        draft.setParty(partyId, name)
        draft.addFree("Urea").rate = total.toLong().toString()
        if (paid > 0.0) draft.addPayment(PaymentMethod.CASH, paid)
        return draft
    }

    @Test
    fun `a sale on account sets the customer's promised day`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val due = System.currentTimeMillis() + 30 * day
        val draft = ticket(aslam, "Aslam", 2000.0, 500.0)
        draft.due = due
        assertNull(draft.problem)
        Counter.post(dao, draft)
        assertEquals(due, dao.getParty(aslam)!!.harvestPromise)
    }

    @Test
    fun `a ticket paid in full leaves the promised day alone`() = runBlocking {
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val draft = ticket(bilal, "Bilal", 1500.0, 1500.0)
        draft.due = System.currentTimeMillis() + 7 * day
        Counter.post(dao, draft)
        assertNull("nothing went to his account", dao.getParty(bilal)!!.harvestPromise)
    }

    @Test
    fun `another customer or a cleared till forgets the day`() {
        val draft = CounterDraft()
        draft.setParty(1L, "Aslam")
        draft.due = 123L
        draft.setParty(2L, "Bilal")
        assertNull(draft.due)
        draft.due = 456L
        draft.clear()
        assertNull(draft.due)
    }

    @Test
    fun `a stored default scale prefix is not a scale in use`() = runBlocking {
        // Every counter-settings save wrote "21" until 25 Oct 2026; such a book opened with the tools on.
        dao.setSetting(BookSettings.SCALE_PREFIX, ScaleCode.DEFAULT_PREFIX)
        assertFalse(BookSettings.counterAdvanced(dao))
        dao.setSetting(BookSettings.SCALE_PREFIX, "23")
        assertTrue("a shop's own prefix is a scale it uses", BookSettings.counterAdvanced(dao))
    }

    @Test
    fun `the big-shop tools follow the owner's word, else what the book already uses`() = runBlocking {
        assertFalse("a fresh book's counter is simple", BookSettings.counterAdvanced(dao))
        dao.setSetting(BookSettings.LOYALTY_POINTS_PER_100, "1")
        dao.setSetting(BookSettings.LOYALTY_POINT_VALUE, "1")
        assertTrue("loyalty in use keeps the tools in sight", BookSettings.counterAdvanced(dao))
        dao.setSetting(BookSettings.COUNTER_ADVANCED, "0")
        assertFalse("the owner turned them off", BookSettings.counterAdvanced(dao))
        dao.setSetting(BookSettings.COUNTER_ADVANCED, "1")
        assertTrue(BookSettings.counterAdvanced(dao))
    }
}
