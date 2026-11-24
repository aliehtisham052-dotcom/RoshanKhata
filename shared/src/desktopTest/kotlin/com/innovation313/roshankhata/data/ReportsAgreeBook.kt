package com.innovation313.roshankhata.data

/**
 * One made-up shop book on which every report must agree (plan item A2, 24 Nov 2026). Invented names and figures only.
 *
 * The story, all written at [at] (one window around it reads everything), by the same DAO calls the screens make:
 *  - Aslam (customer): 2 bags of Urea at 4,500 on credit (9,000); a counter ticket of 1 more on his account (4,500);
 *    pays 3,000 in cash.                                                          -> he owes 10,500
 *  - Bilal (customer): 5,000 on credit with no goods written; pays 2,000 by bank; a cheque of 1,500 is cleared. -> owes 1,500
 *  - Kashif (customer): an opening balance of 7,000 (not a sale) and a Qarz-e-Hasna loan of 1,500 (not a sale). -> owes 8,500
 *  - Seeds Co. (supplier): a credit bill of 10,000 (his "I got"), 10 bags of Urea at 4,000; 4,000 paid to him in cash. -> the
 *    shop owes 6,000
 *  - A walk-in ticket of 1 bag at 4,500 paid in cash; the same bag came back, 4,500 refunded in cash.
 *  - The cashbook: tea 200 out, 1,000 in from "Other"; a cash supplier bill of 400; a staff advance of 2,000.
 *
 * Urea costs 4,200 on its product, so Profit and the sales report cost every sold bag alike.
 */
object ReportsAgreeBook {

    /** The figures the story comes to, worked out by hand (not by the code under test). */
    const val SALES = 9_000.0 + 5_000.0 + 4_500.0 + 4_500.0          // credit x2, no-goods credit, Aslam's ticket, the walk-in
    const val RECEIVED = 3_000.0 + 2_000.0 + 1_500.0 + 4_500.0       // Aslam cash, Bilal bank, the cheque, the walk-in's payment
    const val TO_GET = 10_500.0 + 1_500.0 + 8_500.0
    const val TO_GIVE = 6_000.0
    const val GROSS_PROFIT = (4 * 4_500.0 - 4 * 4_200.0) - (4_500.0 - 4_200.0)   // four bags sold, one back
    const val CASHBOOK_EXPENSES = 200.0                              // tea; the walk-in's refund is a return, not an expense
    const val STAFF_PAID = 2_000.0
    const val DRAWER_IN = 4_500.0 + 1_000.0 + 3_000.0                // walk-in cash, "Other" income, Aslam's cash
    const val DRAWER_OUT = 200.0 + 4_500.0 + 4_000.0 + 400.0 + 2_000.0   // tea, refund, supplier cash, cash bill, staff
    const val UREA_ON_HAND = 10.0 - 2.0 - 1.0                        // bought 10, sold 2 + 1 on the khata; the walk-in bag came back
    const val UREA_COST = 4_200.0

    class Ids(val aslam: Long, val bilal: Long, val kashif: Long, val seeds: Long, val urea: Long)

    suspend fun seed(dao: KhataDao, at: Long): Ids {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val kashif = dao.insertParty(Party(name = "Kashif"))
        val seeds = dao.insertParty(Party(name = "Seeds Co.", isCustomer = false))
        val urea = dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
            defaultUnit = "bag", salePrice = 4_500.0, costPrice = UREA_COST))

        // The supplier's credit bill: the shop owes 10,000, and 10 bags are on the shelf.
        dao.insertSupplierBill(
            LedgerEntry(partyId = seeds, amount = 10_000.0, isGiven = false, entryNumber = "", timestamp = at - 5_000),
            SupplierBill(partyId = seeds, billNumber = "B-1", totalAmount = 10_000.0, billDate = at - 5_000, createdAt = at - 5_000),
            listOf(BillItem(billId = 0, productName = "Urea", quantity = 10.0, unit = "bag", rate = 4_000.0, productId = urea))
        )
        dao.insertEntryWithItems(LedgerEntry(partyId = seeds, amount = 4_000.0, isGiven = true, entryNumber = "", timestamp = at - 4_000, paymentMethod = PaymentMethod.CASH), emptyList())

        // Aslam: goods on credit, a ticket on his account, a cash payment.
        dao.insertEntryWithItems(LedgerEntry(partyId = aslam, amount = 9_000.0, isGiven = true, entryNumber = "", timestamp = at - 3_000),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4_500.0, productId = urea)))
        dao.postSale(Sale(partyId = aslam, customerName = "Aslam", timestamp = at - 2_900, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = urea, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4_500.0, costRate = UREA_COST)), emptyList())
        dao.insertEntryWithItems(LedgerEntry(partyId = aslam, amount = 3_000.0, isGiven = false, entryNumber = "", timestamp = at - 2_800, paymentMethod = PaymentMethod.CASH), emptyList())

        // Bilal: credit with no goods, a bank payment, a cheque cleared.
        dao.insertEntryWithItems(LedgerEntry(partyId = bilal, amount = 5_000.0, isGiven = true, entryNumber = "", timestamp = at - 2_700), emptyList())
        dao.insertEntryWithItems(LedgerEntry(partyId = bilal, amount = 2_000.0, isGiven = false, entryNumber = "", timestamp = at - 2_600, paymentMethod = PaymentMethod.BANK), emptyList())
        val cheque = dao.insertCheque(Cheque(partyId = bilal, amount = 1_500.0, isReceived = true, dueDate = at, chequeNumber = "000123"))
        dao.clearCheque(cheque, "cleared", now = at - 2_500)

        // Kashif: an opening balance and a loan, neither a sale.
        dao.insertEntryWithItems(LedgerEntry(partyId = kashif, amount = 7_000.0, isGiven = true, entryNumber = "", timestamp = at - 2_400, entryKind = EntryKind.OPENING), emptyList())
        dao.insertEntryWithItems(LedgerEntry(partyId = kashif, amount = 1_500.0, isGiven = true, entryNumber = "", timestamp = at - 2_300, isQarzeHasna = true), emptyList())

        // The counter: a walk-in bag paid in cash, and the same bag back with a cash refund.
        val sale = dao.postSale(Sale(timestamp = at - 2_200, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = urea, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4_500.0, costRate = UREA_COST)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 4_500.0)))
        val line = dao.saleItems(sale).single()
        dao.postReturn(SaleReturn(saleId = sale, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 4_500.0, reason = "torn", timestamp = at - 2_100),
            listOf(SaleReturnItem(returnId = 0, saleItemId = line.id, itemName = "Urea", quantity = 1.0, rate = 4_500.0)))

        // The cashbook, a cash bill and the staff.
        dao.insertCashEntry(CashEntry(amount = 200.0, isIncome = false, category = "Tea", timestamp = at - 2_000))
        dao.insertCashEntry(CashEntry(amount = 1_000.0, isIncome = true, category = "Other", timestamp = at - 1_900))
        dao.insertBill(SupplierBill(partyId = seeds, billNumber = "B-2", totalAmount = 400.0, isPaidInFull = true, billDate = at - 1_800, createdAt = at - 1_800))
        val ali = dao.insertStaff(Staff(name = "Ali", monthlySalary = 20_000.0))
        dao.insertStaffPayment(StaffPayment(staffId = ali, amount = STAFF_PAID, kind = Payroll.ADVANCE, timestamp = at - 1_700))
        return Ids(aslam, bilal, kashif, seeds, urea)
    }
}
