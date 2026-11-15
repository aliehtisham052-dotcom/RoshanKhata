package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.ChequeState
import com.innovation313.roshankhata.data.Hudhud.StatusOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 15 Nov 2026, the catalog's GAP group "cheques and invoices" (importance 8: money). A cheque is asked by its state
 * (waiting, bounced, cleared), an invoice by what is still due on it, and the other documents (quotation, sales order,
 * challan) by their own page. Each is read through the whole pipeline with the learnt sense (lesson 145). Sentences and
 * names are invented (lesson 80).
 */
class HudhudChequeInvoiceTest {

    private val topics = listOf("intro_documents", "intro_cashbook", "screen_bills", "intro_cheques")
        .map { Hudhud.Topic(it, it, it) }

    private fun heard(s: String, lang: String) = Hudhud.understand(s, emptyList(), topics, lang)

    @Test fun `a bounced or cleared cheque is asked by its state`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Status(StatusOf.CHEQUES, ChequeState.BOUNCED), heard("Kaun sa cheque bounce hua", "ur-Latn"))
        assertEquals(Ask.Status(StatusOf.CHEQUES, ChequeState.BOUNCED), heard("Which cheques bounced", "en"))
        assertEquals(Ask.Status(StatusOf.CHEQUES, ChequeState.CLEARED), heard("Which cheques have cleared", "en"))
        assertEquals(Ask.Status(StatusOf.CHEQUES, ChequeState.CLEARED), heard("Kaun sa cheque clear ho gaya", "ur-Latn"))
    }

    @Test fun `a cheque with no state word is the cheques waiting, as before`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Status(StatusOf.CHEQUES), heard("Kitne cheque pending hain", "ur-Latn"))
    }

    @Test fun `an invoice with money still due is read in every language`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val want = Ask.Status(StatusOf.INVOICES)
        assertEquals(want, heard("Kis invoice ka paisa baqi hai", "ur-Latn"))
        assertEquals(want, heard("Which invoices are unpaid", "en"))
        assertEquals(want, heard("Which invoice is overdue", "en"))
        assertEquals(want, heard("Invoices due today", "en"))
        assertEquals(want, heard("کس انوائس کی رقم باقی ہے", "ur"))
        assertEquals(want, heard("कौन सा इनवॉइस बकाया है", "hi"))
        assertEquals(want, heard("Faktur mana yang belum dibayar", "in"))
        assertEquals(want, heard("أي فاتورة غير مدفوعة", "ar"))
    }

    @Test fun `counting the invoices made stays a count`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Count(Hudhud.Thing.INVOICES, null), heard("Kitne invoice banaye", "ur-Latn"))
    }

    @Test fun `a quotation or a sales order is the documents page`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for (s in listOf("How many quotations are open", "Kitni quotation abhi open hain", "Sales order kitne hain")) {
            assertEquals(s, Ask.Help("intro_documents"), heard(s, "ur-Latn"))
        }
    }

    // The book has no screen for a supplier's quotation; the documents page is the only one that names them, so a supplier
    // word does not move it (a guard for that was tried and changed no answer).
    @Test fun `a supplier's quotation is still the only page that names quotations`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Help("intro_documents"), heard("supplier ki quotation kab aayi", "ur-Latn"))
    }

    // The supplier's own papers are not the customers' invoices still due: a supplier or a bill word keeps them out.
    @Test fun `a supplier's invoice or a bill is not the invoices still due`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val due = Ask.Status(StatusOf.INVOICES)
        for (s in listOf("supplier ka invoice baqi hai", "company ka invoice unpaid hai", "Which supplier invoice is unpaid")) {
            assertNotEquals(s, due, heard(s, if (s.startsWith("Which")) "en" else "ur-Latn"))
        }
        for (s in listOf("invoice bill baqi hai", "Which invoice bill is unpaid")) {
            assertNotEquals(s, due, heard(s, if (s.startsWith("Which")) "en" else "ur-Latn"))
        }
    }
}
