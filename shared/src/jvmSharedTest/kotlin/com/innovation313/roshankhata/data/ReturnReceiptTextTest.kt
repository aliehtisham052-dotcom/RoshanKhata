package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A return's slip (3 Nov 2026): one set of lines for the phone's text receipt and the computer's thermal one. */
class ReturnReceiptTextTest {

    /** Words that say which string was asked for and with what. */
    private val words = object : Words {
        override fun text(name: String, vararg args: Any) = name + args.joinToString(",", prefix = "(", postfix = ")")
        override fun plural(name: String, count: Int, vararg args: Any) = name
        override fun list(name: String) = emptyList<String>()
    }

    private fun ret(method: String, refund: Double, party: Long? = null, reason: String = "Leaking") = SaleReturn(
        id = 4, returnNumber = "R-000004", saleId = 41, partyId = party, customerName = party?.let { "Aslam" },
        timestamp = 0, total = 1_200.0, refundMethod = method, refundAmount = refund, reason = reason, note = null,
        status = SaleStatus.DONE, ledgerEntryId = null, refundEntryId = null, refundCashId = null, createdBy = null,
        voidReason = null, voidedAt = null
    )

    private val items = listOf(
        SaleReturnItem(id = 2, returnId = 4, lineNo = 2, saleItemId = null, productId = null, itemName = "Nozzle", quantity = 1.0, unit = null, rate = 200.0, billItemId = null),
        SaleReturnItem(id = 1, returnId = 4, lineNo = 1, saleItemId = null, productId = null, itemName = "Confidor", quantity = 2.0, unit = null, rate = 500.0, billItemId = null)
    )

    @Test
    fun `a walk-in's cash refund says where the money went and opens the drawer`() {
        val r = ret(PaymentMethod.CASH, 1_200.0)
        val lines = ReturnReceiptText.lines(words, "Al-Noor Traders", r, items, "S-000041", null)
        val text = ReturnReceiptText.text(lines)
        assertEquals(ReturnReceiptText.Kind.SHOP, lines.first().kind)
        assertTrue(text.contains("pos_return_title()"))
        assertTrue(text.contains("R-000004"))
        assertTrue(text.contains("pos_return_of(S-000041)"))
        assertTrue(text.contains("pos_walk_in()"))
        // The lines in the ticket's own order, whatever order they were read in.
        assertTrue(text.indexOf("Confidor") < text.indexOf("Nozzle"))
        assertTrue(lines.any { it.kind == ReturnReceiptText.Kind.PLAIN && it.text.startsWith("pos_return_refund()") })
        assertTrue(text.contains("pos_return_reason(): Leaking"))
        // A walk-in has no balance to show.
        assertFalse(text.contains("net_balance"))
        assertTrue(ReturnReceiptText.cashOut(r))
    }

    @Test
    fun `a customer's return kept on his account shows his balance and leaves the drawer shut`() {
        val r = ret(SaleReturn.ACCOUNT, 0.0, party = 7, reason = "")
        val text = ReturnReceiptText.text(ReturnReceiptText.lines(words, "Al-Noor Traders", r, items, null, 3_800.0))
        assertTrue(text.contains("pos_refund_account()"))
        assertFalse(text.contains("pos_return_of"))
        assertFalse(text.contains("pos_return_reason"))
        assertTrue(text.contains("net_balance()"))
        assertTrue("named, as the entry receipt says it", text.contains("receipt_owes(") && !text.contains("pdf_stmt_you_will_pay"))
        assertFalse(ReturnReceiptText.cashOut(r))
        // A bank refund moves no cash either.
        assertFalse(ReturnReceiptText.cashOut(ret(PaymentMethod.BANK, 1_200.0)))
    }
}
