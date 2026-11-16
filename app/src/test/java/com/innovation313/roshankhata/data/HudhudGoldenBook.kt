package com.innovation313.roshankhata.data

import java.util.TimeZone

/**
 * The made-up book the golden answers and the parity test read (16 Nov 2026, audit T21). Invented names and numbers only
 * (lesson 80): Ali Raza and Bashir Ahmad are customers, Tariq Butt is a supplier, Hamid Khan is the shop's cashier; the
 * goods are Urea, DAP and Paracetamol.
 *
 * Every moment is written relative to [seed]'s `now`, never on a calendar day, so the answers come out the same on any
 * day of any month: what is "today" is a few minutes before now, everything else is 70 to 90 days before it — never in
 * this month or the last one (the last month starts at most 62 days back), never in this week or the last, always in
 * the last six months. A span whose answer would move with the calendar (this year, last year) is not asked of it.
 * [clockIsSafe] keeps the run away from midnight, where "a few minutes before now" would be yesterday.
 *
 * What is in it:
 * - Ali Raza: a Qarz-e-Hasna loan and a credit sale of Urea 75 days ago, a cash payment 70 days ago; a credit sale of DAP
 *   and a cash payment today; a credit limit, a promised day, a plan, a pending cheque and an invoice.
 * - Bashir Ahmad: an opening balance (schema 45's OPENING kind) 90 days ago and a paid-now sale of Urea today (the sale and
 *   its cash "I got" as a pair); a private note with a phone number in it; a bounced cheque.
 * - Tariq Butt (supplier): one bill of Urea, DAP and Paracetamol 75 days ago, batches with one expiring within 60 days, a
 *   cash payment 70 days ago, a sent purchase order and a scheme.
 * - The counter: a walk-in ticket of Paracetamol today (its cash in the cashbook), a cash return of one strip of it, a
 *   write-off, a closed shift that came out short, a day closed 75 days ago.
 * - The cashbook: the owner's own money put in and the rent paid today.
 * - Staff: Hamid Khan, an advance today, marked absent today.
 * - The audit log: an edit by the cashier and a promised day set, today.
 */
internal object HudhudGoldenBook {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** The rows a test asks about by id. */
    class Ids(
        val ali: Long, val bashir: Long, val tariq: Long,
        val urea: Long, val dap: Long, val paracetamol: Long,
        val staff: Long
    )

    /**
     * Whether "a few minutes before now" is still today and stays today for the length of a run: between 00:30 and
     * 23:30 in the default zone. The tests skip (Assume) outside it — a few runs in a hundred at most.
     */
    fun clockIsSafe(now: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val intoDay = now - DayMath.startOfDay(now, tz)
        return intoDay >= 30 * MINUTE && intoDay < DAY - 30 * MINUTE
    }

    suspend fun seed(dao: KhataDao, now: Long): Ids {
        fun ago(days: Int, hours: Int = 0) = now - days * DAY - hours * HOUR
        fun today(minutes: Int) = now - minutes * MINUTE

        // ---- the people
        val ali = dao.insertParty(
            Party(
                name = "Ali Raza", phone = "0300 1234567", fatherName = "Raza Ahmad", village = "Chak 12",
                creditLimit = 50_000.0, createdAt = ago(90), harvestPromise = now + 5 * DAY
            )
        )
        val bashir = dao.insertParty(
            Party(
                name = "Bashir Ahmad", phone = "0300 7654321", createdAt = ago(90),
                note = "Pays after the harvest. Call 0300 7654321 first."
            )
        )
        val tariq = dao.insertParty(Party(name = "Tariq Butt", phone = "0300 0000001", isCustomer = false, createdAt = ago(90)))

        // ---- the goods
        val urea = dao.insertProduct(
            Product(
                name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
                defaultUnit = "bag", company = "Green Field Co.", salePrice = 4_000.0, creditPrice = 4_200.0, costPrice = 3_500.0,
                lowStockLevel = 20.0, barcode = "2100000000017", note = "Shelf A", createdAt = ago(90)
            )
        )
        val dap = dao.insertProduct(
            Product(
                name = "DAP", nameKey = ProductName.key("DAP"), normalisedName = ProductName.normalised("DAP"),
                defaultUnit = "bag", company = "Kisan Co.", salePrice = 9_000.0, costPrice = 8_500.0, createdAt = ago(90)
            )
        )
        val paracetamol = dao.insertProduct(
            Product(
                name = "Paracetamol", nameKey = ProductName.key("Paracetamol"), normalisedName = ProductName.normalised("Paracetamol"),
                defaultUnit = "packet", salePrice = 50.0, costPrice = 40.0, lowStockLevel = 200.0, createdAt = ago(90)
            )
        )

        // ---- the khata, oldest first (RK- numbers follow the order written)
        dao.insertEntryWithItems(
            LedgerEntry(partyId = bashir, amount = 3_000.0, isGiven = true, note = "Opening balance", entryNumber = "",
                timestamp = ago(90), entryKind = EntryKind.OPENING),
            emptyList()
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 5_000.0, isGiven = true, note = "Loan", entryNumber = "",
                timestamp = ago(75, 2), isQarzeHasna = true, paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        dao.insertSupplierBill(
            LedgerEntry(partyId = tariq, amount = 264_000.0, isGiven = false, note = "B-101", entryNumber = "", timestamp = ago(75, 1)),
            SupplierBill(partyId = tariq, billNumber = "B-101", totalAmount = 264_000.0, billDate = ago(75, 1), dueDate = ago(45), createdAt = ago(75, 1)),
            listOf(
                BillItem(billId = 0, productName = "Urea", batchNumber = "U-01", quantity = 50.0, unit = "bag", rate = 3_500.0, productId = urea),
                BillItem(billId = 0, productName = "DAP", batchNumber = "D-01", quantity = 10.0, unit = "bag", rate = 8_500.0, productId = dap),
                BillItem(billId = 0, productName = "Paracetamol", batchNumber = "P-01", expiryDate = now + 40 * DAY,
                    quantity = 100.0, unit = "packet", rate = 40.0, productId = paracetamol)
            )
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 8_000.0, isGiven = true, entryNumber = "", timestamp = ago(75), rateType = RateType.CREDIT),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4_000.0, productId = urea))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = tariq, amount = 100_000.0, isGiven = true, note = "Part payment", entryNumber = "",
                timestamp = ago(70, 1), paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 4_000.0, isGiven = false, entryNumber = "", timestamp = ago(70), paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 9_000.0, isGiven = true, entryNumber = "", timestamp = today(20), rateType = RateType.CREDIT),
            listOf(EntryItem(entryId = 0, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 9_000.0, productId = dap))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 1_000.0, isGiven = false, entryNumber = "", timestamp = today(15), paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        // A sale paid on the spot: the sale and its cash "I got", each pointing at the other.
        dao.insertEntryWithItemsPaidNow(
            LedgerEntry(partyId = bashir, amount = 4_000.0, isGiven = true, entryNumber = "", timestamp = today(12), rateType = RateType.CASH),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4_000.0, productId = urea))
        )

        // ---- the counter: a walk-in ticket, its return, a write-off, a shift, a day closed long ago
        dao.restoreSales(
            listOf(
                Sale(id = 1, saleNumber = "S-000001", timestamp = today(10), subtotal = 200.0, grandTotal = 200.0, paidTotal = 200.0,
                    status = SaleStatus.DONE, createdBy = "Hamid Khan")
            )
        )
        dao.restoreSaleItems(
            listOf(SaleItem(id = 1, saleId = 1, lineNo = 0, productId = paracetamol, itemName = "Paracetamol", quantity = 4.0,
                unit = "packet", rate = 50.0, costRate = 40.0))
        )
        val ticketCash = dao.insertCashEntry(
            CashEntry(amount = 200.0, isIncome = true, category = CashCategory.SALE, note = "S-000001", timestamp = today(10),
                paymentMethod = PaymentMethod.CASH, saleId = 1)
        )
        dao.restoreSalePayments(listOf(SalePayment(saleId = 1, method = PaymentMethod.CASH, amount = 200.0, cashEntryId = ticketCash)))
        val refundCash = dao.insertCashEntry(
            CashEntry(amount = 50.0, isIncome = false, category = CashCategory.RETURN, note = "R-000001", timestamp = today(8),
                paymentMethod = PaymentMethod.CASH)
        )
        dao.restoreReturns(
            listOf(
                SaleReturn(id = 1, returnNumber = "R-000001", saleId = 1, timestamp = today(8), total = 50.0,
                    refundMethod = PaymentMethod.CASH, refundAmount = 50.0, reason = "Wrong strength", createdBy = "Hamid Khan",
                    refundCashId = refundCash)
            )
        )
        dao.restoreReturnItems(
            listOf(SaleReturnItem(returnId = 1, lineNo = 0, saleItemId = 1, productId = paracetamol, itemName = "Paracetamol",
                quantity = 1.0, unit = "packet", rate = 50.0))
        )
        dao.insertAdjustment(
            StockAdjustment(productId = paracetamol, timestamp = today(4), quantity = -1.0, unit = "packet",
                reason = AdjustReason.DAMAGE, note = "Torn packet", createdBy = "Hamid Khan")
        )
        dao.insertShift(
            Shift(user = "Hamid Khan", openedAt = today(25), openingFloat = 1_000.0, closedAt = today(2), counted = 1_150.0, expected = 1_200.0)
        )
        dao.saveDayClose(
            DayClose(day = DayCloseMath.dayOf(ago(75)), opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 500.0, closedAt = ago(75)),
            user = null
        )

        // ---- the cashbook's own rows
        dao.insertCashEntry(CashEntry(amount = 2_000.0, isIncome = true, category = "Other", note = "Owner's own money", timestamp = today(6),
            paymentMethod = PaymentMethod.CASH))
        dao.insertCashEntry(CashEntry(amount = 500.0, isIncome = false, category = "Rent", timestamp = today(5),
            paymentMethod = PaymentMethod.CASH))

        // ---- cheques
        dao.insertCheque(
            Cheque(partyId = ali, amount = 2_500.0, isReceived = true, chequeNumber = "000111", bankName = "Test Bank",
                dueDate = now + 10 * DAY, createdAt = ago(70))
        )
        dao.insertCheque(
            Cheque(partyId = bashir, amount = 1_000.0, isReceived = true, chequeNumber = "000222", bankName = "Test Bank",
                dueDate = ago(70), status = ChequeStatus.BOUNCED, settledAt = ago(68), createdAt = ago(72))
        )

        // ---- staff
        val staff = dao.insertStaff(Staff(name = "Hamid Khan", phone = "0300 0000002", monthlySalary = 30_000.0, role = Role.CASHIER, createdAt = ago(90)))
        dao.insertStaffPayment(StaffPayment(staffId = staff, amount = 2_000.0, kind = Payroll.ADVANCE, timestamp = today(3)))
        dao.markAttendance(StaffAttendance(staffId = staff, day = DayMath.startOfDay(now), status = Payroll.ABSENT))

        // ---- a plan, an invoice, a purchase order, a scheme
        dao.insertPlan(
            PaymentPlan(partyId = ali, totalAmount = 10_000.0, installmentAmount = 2_000.0, note = "Tractor parts",
                nextDueDate = now + 15 * DAY, createdAt = ago(70))
        )
        val invoice = dao.insertInvoice(
            Invoice(invoiceNumber = "INV-000001", customerName = "Ali Raza", customerPhone = "0300 1234567", invoiceDate = today(19),
                dueDate = now + 7 * DAY, receivedAmount = 1_000.0, createdAt = today(19))
        )
        dao.insertInvoiceItem(InvoiceItem(invoiceId = invoice, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 9_000.0))
        val order = dao.insertOrder(PurchaseOrder(orderNumber = "PO-000001", partyId = tariq, timestamp = today(9), status = OrderStatus.SENT))
        dao.insertOrderItems(listOf(PurchaseOrderItem(orderId = order, lineNo = 0, productId = urea, itemName = "Urea", quantity = 20.0,
            unit = "bag", rate = 3_500.0)))
        dao.restoreSchemes(
            listOf(
                Scheme(id = 1, name = "Urea season scheme", partyId = tariq, company = "Green Field Co.", startDate = ago(75), endDate = now + 30 * DAY,
                    measure = SchemeMath.BY_QTY, unit = "bag", rewardKind = SchemeMath.AMOUNT, target1 = 50.0, reward1 = 5_000.0,
                    createdAt = ago(75))
            )
        )

        // ---- the audit log
        dao.insertAudit(AuditLog(timestamp = today(1), user = "Hamid Khan", action = AuditAction.ENTRY_EDITED, refTable = "transactions",
            refId = 1, detail = "note"))
        dao.insertAudit(AuditLog(timestamp = today(1) + 1, user = null, action = AuditAction.PROMISE_SET, refTable = "parties",
            refId = ali, detail = (now + 5 * DAY).toString()))

        return Ids(ali = ali, bashir = bashir, tariq = tariq, urea = urea, dap = dap, paracetamol = paracetamol, staff = staff)
    }
}
