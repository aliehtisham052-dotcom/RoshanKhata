package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * The owner's yes of 17 Nov 2026 on the eleven writes the gate check had left waiting: a new customer, product,
 * supplier bill and plan, and a plan's next day, are written in Who did what with who did it (no PIN); a customer's
 * face and card token are written one column at a time, so no other field of the row is put back from an old copy.
 * Made-up names.
 */
class AuditedAddsTest {

    private fun withBook(block: suspend (KhataDao) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("rk-audited-adds").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            block(db.khataDao())
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun KhataDao.rows(action: String) = auditInOrder().filter { it.action == action }

    @Test
    fun `every add from a screen leaves its row in Who did what`() = withBook { dao ->
        val party = dao.insertPartyAudited(Party(name = "Audit Test Customer"))
        assertEquals(listOf(party), dao.rows(AuditAction.PARTY_ADDED).map { it.refId })

        val added = dao.addProduct(Product(name = "Test Spray", nameKey = "", normalisedName = "")) as ProductAdd.Added
        val made = dao.findOrCreateProduct(name = "Test Seed")
        // Found again, not made: no second row.
        dao.findOrCreateProduct(name = "Test Seed")
        assertEquals(listOf(added.id, made.id), dao.rows(AuditAction.PRODUCT_ADDED).map { it.refId })

        val supplier = dao.insertPartyAudited(Party(name = "Audit Test Supplier"))
        dao.insertSupplierBill(
            entry = null,
            bill = SupplierBill(partyId = supplier, billNumber = "B-77", totalAmount = 1200.0),
            items = listOf(BillItem(billId = 0, productName = "Test Seed", quantity = 2.0, rate = 600.0))
        )
        val bill = dao.rows(AuditAction.BILL_ADDED).single()
        assertEquals("supplier_bills", bill.refTable)
        assertEquals("B-77", bill.detail)
        // The bill's line named a product already in the book: no product row for it.
        assertEquals(2, dao.rows(AuditAction.PRODUCT_ADDED).size)

        val plan = dao.insertPlanAudited(PaymentPlan(partyId = party, totalAmount = 5000.0, installmentAmount = 1000.0))
        assertEquals(listOf(plan), dao.rows(AuditAction.PLAN_ADDED).map { it.refId })
        dao.setPlanNextDueAudited(plan, 1_800_000_000_000L)
        dao.setPlanNextDueAudited(plan, null)
        assertEquals(listOf("1800000000000", null), dao.rows(AuditAction.PLAN_NEXT_DUE).map { it.detail })
    }

    @Test
    fun `a face or a card token never puts back the rest of the row from an old copy`() = withBook { dao ->
        val id = dao.insertPartyAudited(Party(name = "Old Name"))
        val old = dao.getParty(id)!!
        // Meanwhile the name and limit were changed (the edit dialog, or live sync).
        dao.updateParty(old.copy(name = "New Name", creditLimit = 9000.0))

        dao.setPartyPhotoPath(id, "/faces/test.jpg")
        dao.setPartyQrToken(id, "test-token-1")

        val now = dao.getParty(id)!!
        assertEquals("New Name", now.name)
        assertEquals(9000.0, now.creditLimit!!, 0.0)
        assertEquals("/faces/test.jpg", now.photoPath)
        assertEquals("test-token-1", now.qrToken)
    }
}
