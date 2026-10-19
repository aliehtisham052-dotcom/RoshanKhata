package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.util.Locale

/** A supplier bill written on the computer lands as the phone writes it. */
class BillSaveTest {

    private fun <T> withBook(block: suspend (com.innovation313.roshankhata.data.KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-bill").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try { runBlocking { block(db.khataDao()) } } finally { db.close(); dir.deleteRecursively() }
    }

    private val words = desktopWords(Locale.ENGLISH)

    private fun bill(name: String, total: Double, cash: Boolean) = NewBill(
        supplierId = null, newSupplierName = name, billNumber = "77", total = total,
        billDate = 1_760_000_000_000, dueDate = null, paidCash = cash, note = null,
        items = listOf(BillItem(billId = 0, productName = "Urea", batchNumber = "B1", expiryDate = 1_800_000_000_000, quantity = 10.0, unit = "bag", rate = 300.0))
    )

    @Test
    fun `a credit bill owes the supplier once, with its lines and product`() = withBook { dao ->
        saveBill(dao, bill("Sayban", 3000.0, cash = false), words)
        val party = dao.partiesWithBalanceOnce().single()
        assertEquals("Sayban", party.name)
        assertEquals(false, party.isCustomer)
        assertEquals(-3000.0, party.balance, 0.001)
        val b = dao.observeBills().first().single()
        assertEquals(1, b.itemCount)
        assertEquals("Urea", dao.observeBillItems(b.id).first().single().productName)
        assertEquals(listOf("Urea"), dao.productsOnce().map { it.name })
        // The same supplier typed again is reused, not doubled.
        saveBill(dao, bill("Sayban", 1000.0, cash = false), words)
        assertEquals(1, dao.partiesWithBalanceOnce().size)
        assertEquals(-4000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }

    @Test
    fun `a cash bill posts nothing to the khata`() = withBook { dao ->
        saveBill(dao, bill("Suncrop", 5000.0, cash = true), words)
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertEquals(1, dao.observeBills().first().size)
    }
}
