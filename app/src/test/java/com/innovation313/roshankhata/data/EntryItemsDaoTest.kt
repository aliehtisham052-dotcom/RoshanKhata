package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goods lines against a real database (Robolectric runs native SQLite).
 *
 * The questions the owner asked, one test each: is money ever counted twice,
 * does an entry's goods ever land on another entry, does anything entered
 * fail to show, and does a backup bring every line back where it was.
 */
@RunWith(AndroidJUnit4::class)
class EntryItemsDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun open() {
        db = inMemory()
        dao = db.khataDao()
    }

    @After
    fun close() = db.close()

    private fun inMemory() = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()

    private fun visit(partyId: Long, amount: Double = 19200.0) =
        LedgerEntry(partyId = partyId, amount = amount, isGiven = true, entryNumber = "", timestamp = 1_000L)

    private val threeLines = listOf(
        EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0),
        EntryItem(entryId = 0, itemName = "Sulphur", quantity = 5.0, unit = "bag", rate = 750.0),
        EntryItem(entryId = 0, itemName = "Chlorpyrifos", quantity = 3.0, unit = "litre", rate = 1950.0)
    )

    @Test
    fun aThreeLineEntryCountsItsMoneyOnceEverywhere() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        dao.insertEntryWithItems(visit(ahmad), threeLines)

        // The balance, the sales total and the top-customer total must each be
        // 19,200 — not 57,600, which is what joining three lines would give.
        val balance = dao.partiesWithBalanceOnce().single { it.name == "Ahmad" }.balance
        assertEquals(19200.0, balance, 0.001)
        assertEquals(19200.0, dao.salesTotalBetween(0, Long.MAX_VALUE), 0.001)
        assertEquals(19200.0, dao.topCustomersBetween(0, Long.MAX_VALUE, 5).single().total, 0.001)
    }

    @Test
    fun linesBelongToTheirOwnEntryInTheOrderTyped() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val first = dao.insertEntryWithItems(visit(ahmad), threeLines)
        val second = dao.insertEntryWithItems(
            visit(bilal, 1500.0),
            listOf(EntryItem(entryId = 12345, itemName = "DAP", quantity = 1.0, unit = "bori"))
        )

        assertEquals(listOf("Urea", "Sulphur", "Chlorpyrifos"), dao.itemsOfEntry(first).map { it.itemName })
        assertEquals(listOf(0, 1, 2), dao.itemsOfEntry(first).map { it.lineNo })
        // The caller's entryId (12345) is ignored — the DAO ties the line to
        // the entry it actually wrote.
        assertEquals(listOf("DAP"), dao.itemsOfEntry(second).map { it.itemName })
        assertTrue(dao.itemsOfEntry(second).all { it.entryId == second })
    }

    @Test
    fun aMoneyOnlyEntryHasNoLines() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryWithItems(visit(ahmad, 500.0).copy(isGiven = false), emptyList())
        assertTrue(dao.itemsOfEntry(id).isEmpty())
        assertTrue(dao.allEntryItemsForBackup().isEmpty())
    }

    @Test
    fun binnedGoodsLeaveTheStockCountAndComeBackOnRestore() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val urea = dao.findOrCreateProduct("Urea", defaultUnit = "bori")
        val id = dao.insertEntryWithItems(
            visit(ahmad, 9600.0),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0, productId = urea.id))
        )
        assertEquals(2.0, dao.soldPerProduct().single().qty, 0.0)

        dao.softDeleteEntry(id)
        assertTrue("a binned sale must not count as stock sold", dao.soldPerProduct().isEmpty())
        assertEquals("binning keeps the lines for a restore", 1, dao.itemsOfEntry(id).size)

        dao.restoreEntry(id)
        assertEquals(2.0, dao.soldPerProduct().single().qty, 0.0)
    }

    @Test
    fun deletingForeverTakesTheLinesWithIt() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryWithItems(visit(ahmad), threeLines)

        dao.softDeleteEntry(id)
        dao.purgeEntry(id)
        assertTrue("no line may outlive its entry", dao.allEntryItemsForBackup().isEmpty())

        // And the same through a customer deleted for good (parties cascade
        // to transactions, which cascade to lines).
        val bilal = dao.insertParty(Party(name = "Bilal"))
        dao.insertEntryWithItems(visit(bilal), threeLines)
        dao.softDeleteParty(bilal)
        dao.purgeParty(bilal)
        assertTrue(dao.allEntryItemsForBackup().isEmpty())
    }

    @Test
    fun anEditReplacesTheLinesWhole() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryWithItems(visit(ahmad), threeLines)
        val entry = dao.getEntry(id)!!

        dao.updateEntryWithItems(
            entry.copy(amount = 4800.0),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0, unit = "bori", rate = 4800.0))
        )
        val lines = dao.itemsOfEntry(id)
        assertEquals(listOf("Urea"), lines.map { it.itemName })
        assertEquals(1.0, lines.single().quantity!!, 0.0)
        assertEquals(4800.0, dao.getEntry(id)!!.amount, 0.0)
        assertEquals(1, dao.allEntryItemsForBackup().size)
    }

    @Test
    fun theSalesRegisterHasARowPerLineAndNeverRepeatsMoney() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        // A single-line entry prints its own amount, as it always did.
        dao.insertEntryWithItems(
            visit(ahmad, 9000.0),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori"))
        )
        // A three-line entry prints each line's own quantity × rate.
        dao.insertEntryWithItems(visit(ahmad), threeLines)

        val rows = dao.salesRegister(0, Long.MAX_VALUE)
        assertEquals(4, rows.size)
        assertEquals(9000.0, rows[0].amount, 0.001)
        assertEquals(listOf(9600.0, 3750.0, 5850.0), rows.drop(1).map { it.amount })
        assertEquals(9000.0 + 19200.0, rows.sumOf { it.amount }, 0.001)
    }

    @Test
    fun theTieButtonLinksLinesToProducts() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryWithItems(visit(ahmad), threeLines)
        dao.linkGoodsToProducts()
        assertTrue(dao.itemsOfEntry(id).all { it.productId != null })
        assertEquals(3, dao.soldPerProduct().size)
    }

    @Test
    fun aBackupBringsEveryLineBackToItsOwnEntry() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val a = dao.insertEntryWithItems(visit(ahmad).copy(rateType = RateType.CREDIT), threeLines)
        val b = dao.insertEntryWithItems(
            visit(bilal, 750.0),
            listOf(EntryItem(entryId = 0, itemName = "Sulphur", quantity = 1.0, unit = "bag", rate = 750.0, isBonus = true))
        )

        val (result, parsed) = Backup.parseText(Backup.export(context, dao))
        assertTrue("backup did not parse: $result", result is Backup.ImportResult.Ok)

        val other = inMemory()
        try {
            val to = other.khataDao()
            Backup.restore(context, to, parsed!!)
            assertEquals(listOf("Urea", "Sulphur", "Chlorpyrifos"), to.itemsOfEntry(a).map { it.itemName })
            assertEquals(listOf(4800.0, 750.0, 1950.0), to.itemsOfEntry(a).map { it.rate })
            assertEquals(RateType.CREDIT, to.getEntry(a)!!.rateType)
            assertTrue(to.itemsOfEntry(b).single().isBonus)
            assertEquals(4, to.allEntryItemsForBackup().size)
        } finally {
            other.close()
        }
    }

    @Test
    fun anOlderBackupFileGetsItsLinesBuiltFromItsEntries() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryWithItems(visit(ahmad, 9600.0), emptyList())

        // Make the file look like format 6: no entryItems, goods on the entry.
        val root = JSONObject(Backup.export(context, dao))
        root.put("version", 6)
        root.remove("entryItems")
        root.getJSONArray("entries").getJSONObject(0)
            .put("itemName", "Urea").put("quantity", 2.0).put("unit", "bori")

        val (result, parsed) = Backup.parseText(root.toString())
        assertTrue("old file did not parse: $result", result is Backup.ImportResult.Ok)
        assertEquals(1, parsed!!.entryItems.size)

        val other = inMemory()
        try {
            val to = other.khataDao()
            Backup.restore(context, to, parsed)
            val line = to.itemsOfEntry(id).single()
            assertEquals("Urea", line.itemName)
            assertEquals(2.0, line.quantity!!, 0.0)
            assertEquals("bori", line.unit)
        } finally {
            other.close()
        }
    }

    @Test
    fun aFileWithALineThatBelongsToNoEntryIsRefused() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        dao.insertEntryWithItems(visit(ahmad), threeLines)

        val root = JSONObject(Backup.export(context, dao))
        root.getJSONArray("entryItems").getJSONObject(0).put("entryId", 999_999L)

        val (result, parsed) = Backup.parseText(root.toString())
        assertTrue("a line pointing at no entry must be refused", result is Backup.ImportResult.Failed)
        assertEquals(null, parsed)
    }

    @Test
    fun aReturnFindsTheCustomersLastPriceAndWhatHeStillHolds() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val sulphur = dao.findOrCreateProduct("Sulphur", defaultUnit = "bag")
        fun line(qty: Double, rate: Double?, bonus: Boolean = false) = EntryItem(
            entryId = 0, itemName = "Sulphur", quantity = qty, unit = "bag",
            rate = rate, isBonus = bonus, productId = sulphur.id
        )
        // Two sales at different prices, then a free bag, then a return.
        dao.insertEntryWithItems(visit(ahmad, 3000.0).copy(timestamp = 1_000L), listOf(line(4.0, 750.0)))
        dao.insertEntryWithItems(visit(ahmad, 780.0).copy(timestamp = 2_000L), listOf(line(1.0, 780.0)))
        dao.insertEntryWithItems(visit(ahmad, 0.0).copy(timestamp = 3_000L), listOf(line(1.0, null, bonus = true)))
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ahmad, amount = 750.0, isGiven = false, entryNumber = "", timestamp = 4_000L),
            listOf(line(1.0, 750.0))
        )
        // Another customer's sale must not leak in.
        dao.insertEntryWithItems(visit(bilal, 999.0).copy(timestamp = 5_000L), listOf(line(1.0, 999.0)))

        // The latest priced sale to Ahmad (the free bag has no price).
        val last = dao.lastSaleRate(ahmad, sulphur.id)!!
        assertEquals(780.0, last.rate, 0.0)
        assertEquals("bag", last.unit)

        // 4 + 1 + 1 free given, 1 returned = 5 bags still with him.
        assertEquals(5.0, dao.netGoodsWithParty(ahmad, sulphur.id, "bag"), 0.0)
        // A different unit is not added in.
        assertEquals(0.0, dao.netGoodsWithParty(ahmad, sulphur.id, "kg"), 0.0)
    }
}
