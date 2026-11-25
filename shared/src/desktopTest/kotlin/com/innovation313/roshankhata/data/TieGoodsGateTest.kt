package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The tie button, the last write the gate check had waiting (25 Nov 2026, the owner's D8): Products' "tie the goods" is
 * EDIT_BOOK at the data layer (a cashier needs a manager's or the owner's PIN, [KhataDao.linkGoodsToProducts]'s
 * `allowedBy`) and leaves one GOODS_LINKED row in Who did what whose detail is how many lines were tied. A refused call
 * changes nothing. Made-up names and figures.
 */
class TieGoodsGateTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-tie-goods").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        dir.deleteRecursively()
    }

    private fun cashier() = Access.signIn(Staff(id = 4, name = "Test Cashier", monthlySalary = 1.0, role = Role.CASHIER))

    private suspend fun looseSale(): Long {
        val party = dao.insertParty(Party(name = "Tie Test Customer"))
        return dao.insertEntryWithItems(
            LedgerEntry(partyId = party, amount = 100.0, isGiven = true, note = null, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Tie Test Seed", quantity = 1.0, unit = "bag", rate = 100.0))
        )
    }

    private suspend fun tieRows() = dao.auditInOrder().filter { it.action == AuditAction.GOODS_LINKED }

    @Test
    fun `a cashier without a PIN is refused and nothing is tied or written`() = runBlocking {
        val sale = looseSale()
        cashier()
        try { dao.linkGoodsToProducts(); fail("a cashier tied the goods alone") }
        catch (e: CounterRefused) { assertEquals(CounterRefused.Why.NOT_ALLOWED, e.why) }
        try { dao.linkGoodsToProducts(allowedBy = "  "); fail("a blank name is no PIN") }
        catch (e: CounterRefused) { assertEquals(CounterRefused.Why.NOT_ALLOWED, e.why) }
        assertNull("the line stays loose", dao.itemsOfEntry(sale).single().productId)
        assertTrue("no product was made", dao.allProductsForBackup().isEmpty())
        assertTrue("nothing written down", tieRows().isEmpty())
    }

    @Test
    fun `with a manager's PIN a cashier ties the goods and the row names who allowed it`() = runBlocking {
        val sale = looseSale()
        cashier()
        assertEquals(1, dao.linkGoodsToProducts(allowedBy = "Test Manager"))
        assertNotNull(dao.itemsOfEntry(sale).single().productId)
        assertEquals("Tie Test Seed", dao.itemsOfEntry(sale).single().itemName)
        val row = tieRows().single()
        assertEquals("1 · Test Manager", row.detail)
        assertEquals("Test Cashier", row.user)
    }

    @Test
    fun `the owner ties without a PIN and the row counts the lines`() = runBlocking {
        val first = looseSale()
        val second = looseSale()
        assertEquals(2, dao.linkGoodsToProducts())
        assertNotNull(dao.itemsOfEntry(first).single().productId)
        assertNotNull(dao.itemsOfEntry(second).single().productId)
        val row = tieRows().single()
        assertEquals("2", row.detail)
        assertNull("the owner's rows carry no name", row.user)
        // Run again: nothing left to tie, so nothing is written.
        assertEquals(0, dao.linkGoodsToProducts())
        assertEquals(1, tieRows().size)
    }

    @Test
    fun `a manager signed in may tie without being asked for a PIN`() = runBlocking {
        looseSale()
        Access.signIn(Staff(id = 5, name = "Test Manager", monthlySalary = 1.0, role = Role.MANAGER))
        assertEquals(1, dao.linkGoodsToProducts())
        assertEquals("1", tieRows().single().detail)
    }
}
