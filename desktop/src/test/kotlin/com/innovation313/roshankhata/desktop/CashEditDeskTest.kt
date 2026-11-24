package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.CashCategory
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

/**
 * The cashbook line's correction on the computer (17 Nov 2026, audit L023, the phone's Edit on a cashbook row): what the
 * form changes and what it leaves, the day picked keeping the time of day, and that the pane writes it only through
 * [com.innovation313.roshankhata.data.KhataDao.editCashEntryAudited] behind the EDIT_BOOK gate. The DAO's own refusals
 * (a ticket's line, a binned one, the amount, the audit row) are EditCashChequeTest's.
 */
class CashEditDeskTest {

    private val stored = CashEntry(
        id = 7, amount = 500.0, isIncome = false, category = "Rent", note = "March", timestamp = 1_000L,
        paymentMethod = PaymentMethod.CASH, paymentRef = null, photoPath = "bills/r.jpg", uid = "u-7", createdBy = "Bilal"
    )

    @Test
    fun `a correction changes the six fields and nothing else`() {
        val out = correctedCash(stored, 650.5, "  Electricity ", "Rent", " Feb bill ", 2_000L, PaymentMethod.BANK, " TX99 ")
        assertEquals(650.5, out.amount, 0.0)
        assertEquals("Electricity", out.category)
        assertEquals("Feb bill", out.note)
        assertEquals(2_000L, out.timestamp)
        assertEquals(PaymentMethod.BANK, out.paymentMethod)
        assertEquals("TX99", out.paymentRef)
        // The direction, the picture, the identity, the writer and the id are as stored.
        assertEquals(stored.isIncome, out.isIncome)
        assertEquals("bills/r.jpg", out.photoPath)
        assertEquals("u-7", out.uid)
        assertEquals("Bilal", out.createdBy)
        assertEquals(7L, out.id)
    }

    @Test
    fun `a blank note is none and cash keeps no reference`() {
        val out = correctedCash(stored, 10.0, "Rent", "Rent", "   ", 1_000L, PaymentMethod.CASH, "left over")
        assertNull(out.note)
        assertNull(out.paymentRef)
        assertNull(correctedCash(stored, 10.0, "Rent", "Rent", "", 1_000L, null, "x").paymentRef)
    }

    @Test
    fun `the counter's own category keeps its stored key when typed as the language's word`() {
        val sale = stored.copy(category = CashCategory.SALE, isIncome = true)
        // The pane shows the key as "Sale" in the owner's words (here a made-up Urdu word); typed back unchanged it stays the key.
        assertEquals(CashCategory.SALE, correctedCash(sale, 10.0, "بِکری", "بِکری", "", 1_000L, null, "").category)
        // Changed by the owner, it is what he typed.
        assertEquals("Tips", correctedCash(sale, 10.0, "Tips", "بِکری", "", 1_000L, null, "").category)
    }

    @Test
    fun `a day picked keeps the time of day and is never ahead of now`() {
        fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = Calendar.getInstance().apply { clear(); set(y, m, d, h, min) }.timeInMillis
        val written = at(2026, Calendar.NOVEMBER, 3, 14, 35)
        val now = at(2026, Calendar.NOVEMBER, 17, 9, 0)
        val pickedDay = at(2026, Calendar.NOVEMBER, 10, 0, 0)
        val out = Calendar.getInstance().apply { timeInMillis = dayKeepingTime(pickedDay, written, now) }
        assertEquals(10, out.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, out.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, out.get(Calendar.MINUTE))
        // Today at a later hour than now is now.
        val today = at(2026, Calendar.NOVEMBER, 17, 0, 0)
        assertEquals(now, dayKeepingTime(today, written, now))
    }

    @Test
    fun `the pane writes a correction only through the audited, gated DAO call`() {
        // Gradle runs a module's tests in the module's folder; a run from the repository's root finds it under desktop/.
        fun src(name: String): String {
            val rel = "src/main/kotlin/com/innovation313/roshankhata/desktop/$name"
            return listOf(File(rel), File("desktop/$rel")).first { it.isFile }.readText().replace("\r\n", "\n")
        }
        val dialog = src("CashEditDialog.kt")
        val pane = src("CashbookPane.kt")
        assertTrue("the write is editCashEntryAudited with who let a cashier through", dialog.contains("dao.editCashEntryAudited(corrected, by)"))
        assertTrue("no other write of a cashbook row from the form", !dialog.contains("insertCashEntryAudited") && !dialog.contains("setCashEntryFields"))
        assertTrue("it closes only after the call returned", dialog.contains(".onSuccess { refused -> if (refused == null) onClose() else onRefused(refused) }"))
        assertTrue("a failure says not saved and keeps the form", dialog.contains(".onFailure { problem = notSaved }") && dialog.contains("W.save_failed_not_saved"))
        assertTrue("Save and the close wait while it writes", dialog.contains("enabled = !saving") && dialog.contains("if (!saving) onClose()"))
        assertTrue("typed words are asked about before Esc or the cross", dialog.contains("FormTyped {") && dialog.contains("typedSince(opened, formNow())"))
        assertTrue("the app's own frame", dialog.contains("AppDialog(") && dialog.contains("DialogFrame("))
        assertTrue("never the direction or the picture", !dialog.contains("TwoWay(") && !dialog.contains("FormPhoto("))
        assertTrue("the row's Edit asks EDIT_BOOK and looks for a ticket's line first",
            pane.contains("Gates.needBy(com.innovation313.roshankhata.data.Access.Permission.EDIT_BOOK) { by ->") && pane.contains("dao.cashOwner(e.id)"))
        assertTrue("a refused line says where to change it", pane.contains("CashEdit.LINKED") && pane.contains("W.entry_linked_title"))
    }
}
