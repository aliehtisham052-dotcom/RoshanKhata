package com.innovation313.roshankhata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataDao {

    // ---------- Parties ----------

    @Insert
    suspend fun insertParty(party: Party): Long

    @Update
    suspend fun updateParty(party: Party)

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getParty(id: Long): Party?

    /**
     * The customer a scanned card belongs to.
     *
     * Deleted customers are excluded on purpose: their card may still be in a
     * pocket, and a scan of it should say "not found" rather than open a
     * ledger the owner chose to remove. Restore the customer from the bin and
     * the same card works again — the token survives deletion.
     */
    @Query("SELECT * FROM parties WHERE qrToken = :token AND isDeleted = 0 LIMIT 1")
    suspend fun partyByQrToken(token: String): Party?

    /**
     * An existing customer of this name, if there is one.
     *
     * Case- and space-insensitive, because "Bilal", "bilal" and " Bilal " are
     * one person to the shopkeeper who typed them. Deleted parties are ignored
     * — a name freed by deleting someone should be usable again.
     */
    @Query(
        "SELECT * FROM parties " +
            "WHERE isDeleted = 0 " +
            "AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) " +
            "LIMIT 1"
    )
    suspend fun findPartyByName(name: String): Party?

    /**
     * An existing customer on this number, if there is one.
     *
     * A phone number identifies a person more reliably than a name does — the
     * same man may be entered as "Bilal" one week and "Bilal Bhai" the next,
     * but the number he answers on does not change. Matching on it catches
     * the duplicate a name check would let through.
     *
     * Spaces and dashes are stripped before comparing, so 0300-1234567 and
     * 03001234567 are one number.
     */
    @Query(
        "SELECT * FROM parties " +
            "WHERE isDeleted = 0 " +
            "AND phone IS NOT NULL " +
            "AND REPLACE(REPLACE(REPLACE(phone, ' ', ''), '-', ''), '+', '') = " +
            "REPLACE(REPLACE(REPLACE(:phone, ' ', ''), '-', ''), '+', '') " +
            "LIMIT 1"
    )
    suspend fun findPartyByPhone(phone: String): Party?

    /**
     * Balance convention:
     *   "I Gave"  (isGiven = 1) => party owes me more  => +amount
     *   "I Got"   (isGiven = 0) => party owes me less  => -amount
     * Deleted entries are excluded from the balance.
     */
    @Query(
        """
        SELECT p.id, p.name, p.phone, p.isCustomer, p.photoPath,
               COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0) AS balance,
               COALESCE(MAX(t.timestamp), 0) AS lastActivity,
               p.creditLimit AS creditLimit
        FROM parties p
        LEFT JOIN transactions t
               ON t.partyId = p.id AND t.isDeleted = 0
        WHERE p.isDeleted = 0
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observePartiesWithBalance(): Flow<List<PartyWithBalance>>

    // ---------- Ledger entries ----------

    @Insert
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeEntries(partyId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun totalEntryCount(): Int

    // ---------- Recycle Bin: soft delete ----------

    /** Party goes to the bin. Its entries go with it, so a restore brings back the whole ledger intact. */
    @Query("UPDATE parties SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteParty(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :now WHERE partyId = :partyId AND isDeleted = 0")
    suspend fun softDeleteEntriesOfParty(partyId: Long, now: Long = System.currentTimeMillis())

    /**
     * Move a picked set of customers to the bin, with their entries.
     *
     * @Transaction, so a crash or a failure partway leaves the ledger exactly
     * as it was — never some customers gone and their entries still counting,
     * or the other way round. All of them go under one timestamp, so a restore
     * can tell exactly which deletion a row belonged to and put the whole set
     * back together rather than a scattering of parts.
     */
    @Transaction
    suspend fun softDeleteParties(ids: List<Long>, now: Long = System.currentTimeMillis()) {
        for (id in ids) {
            softDeleteEntriesOfParty(id, now)
            softDeleteParty(id, now)
        }
    }

    /** How many customers are still in the ledger — the number the confirmation shows. */
    @Query("SELECT COUNT(*) FROM parties WHERE isDeleted = 0")
    suspend fun countActiveParties(): Int

    @Query("UPDATE parties SET isDeleted = 1, deletedAt = :now WHERE isDeleted = 0")
    suspend fun softDeleteAllParties(now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :now WHERE isDeleted = 0")
    suspend fun softDeleteAllEntries(now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteEntry(id: Long, now: Long = System.currentTimeMillis())

    // ---------- Recycle Bin: restore ----------

    @Query("UPDATE parties SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreParty(id: Long)

    /** Restores only entries that were binned together with the party (same deletedAt window). */
    @Query("UPDATE transactions SET isDeleted = 0, deletedAt = NULL WHERE partyId = :partyId AND deletedAt = :deletedAt")
    suspend fun restoreEntriesOfParty(partyId: Long, deletedAt: Long)

    @Query("UPDATE transactions SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreEntry(id: Long)

    /** An entry cannot live in an active ledger if its party is still in the bin. */
    @Query("SELECT isDeleted FROM parties WHERE id = :partyId")
    suspend fun isPartyDeleted(partyId: Long): Boolean?

    // ---------- Recycle Bin: listing ----------

    @Query("SELECT * FROM parties WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedParties(): Flow<List<Party>>

    /**
     * Entries shown in the bin are only those deleted *on their own*.
     * Entries that were swept in alongside a deleted party are hidden here —
     * they reappear automatically when the party is restored.
     */
    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 1 AND p.isDeleted = 0
        ORDER BY t.deletedAt DESC
        """
    )
    fun observeDeletedEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT name FROM parties WHERE id = :id")
    suspend fun getPartyName(id: Long): String?

    // ---------- Recycle Bin: permanent purge ----------

    @Query("DELETE FROM transactions WHERE isDeleted = 1 AND deletedAt < :cutoff")
    suspend fun purgeOldEntries(cutoff: Long)

    @Query("DELETE FROM parties WHERE isDeleted = 1 AND deletedAt < :cutoff")
    suspend fun purgeOldParties(cutoff: Long)

    @Query("DELETE FROM transactions WHERE isDeleted = 1")
    suspend fun purgeAllEntries()

    @Query("DELETE FROM parties WHERE isDeleted = 1")
    suspend fun purgeAllParties()

    // ---------- Totals ----------

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
        """
    )
    fun observeNetBalance(): Flow<Double>

    // ---------- Zakat inputs ----------

    /**
     * Every customer's ledger, split the three ways Zakat cares about.
     *
     * Per customer, deliberately. Summed across the whole book first, a total
     * cannot tell money owed TO the shop from money the shop OWES: five
     * hundred from one customer against three hundred owed to another came
     * back as two hundred receivable and nothing payable, which is not what
     * the book says and not how Zakat treats them. The splitting is arithmetic
     * and belongs where it can be tested, so this hands back the rows and
     * Zakat.fromParties does the rest.
     */
    @Query(
        """
        SELECT t.partyId AS partyId,
               COALESCE(SUM(CASE WHEN t.isQarzeHasna = 0 AND t.recovery = 0
                    THEN (CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END)
                    ELSE 0 END), 0) AS certain,
               COALESCE(SUM(CASE WHEN t.isQarzeHasna = 0 AND t.recovery = 1
                    THEN (CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END)
                    ELSE 0 END), 0) AS doubtful,
               COALESCE(SUM(CASE WHEN t.isQarzeHasna = 1
                    THEN (CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END)
                    ELSE 0 END), 0) AS qarz
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
        GROUP BY t.partyId
        """
    )
    fun observeZakatBalancesByParty(): Flow<List<PartyZakatBalance>>

    // ---------- Qarz-e-Hasna listing ----------

    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0 AND t.isQarzeHasna = 1
        ORDER BY t.timestamp DESC
        """
    )
    fun observeQarzeHasnaEntries(): Flow<List<LedgerEntry>>

    // ---------- Contact import ----------

    /** Phone numbers already on the books, so a contact is never added twice. */
    @Query("SELECT phone FROM parties WHERE isDeleted = 0 AND phone IS NOT NULL")
    suspend fun existingPhones(): List<String>

    @Insert
    suspend fun insertParties(parties: List<Party>): List<Long>

    // ---------- Cheques ----------

    @Insert
    suspend fun insertCheque(cheque: Cheque): Long

    @Update
    suspend fun updateCheque(cheque: Cheque)

    @Query("SELECT * FROM cheques WHERE id = :id")
    suspend fun getCheque(id: Long): Cheque?

    /**
     * All live cheques, soonest due first — because the one about to mature
     * is the one that needs attention today.
     */
    @Query(
        """
        SELECT c.id, c.partyId, p.name AS partyName, p.phone AS partyPhone,
               c.amount, c.isReceived, c.chequeNumber, c.bankName,
               c.dueDate, c.status, c.note
        FROM cheques c
        JOIN parties p ON p.id = c.partyId
        WHERE c.isDeleted = 0 AND p.isDeleted = 0
        ORDER BY
            CASE WHEN c.status = 0 THEN 0 ELSE 1 END,
            c.dueDate ASC
        """
    )
    fun observeCheques(): Flow<List<ChequeWithParty>>

    /** Pending cheques already at or past their date — these are the urgent ones. */
    @Query(
        """
        SELECT COUNT(*) FROM cheques c
        JOIN parties p ON p.id = c.partyId
        WHERE c.isDeleted = 0 AND p.isDeleted = 0
          AND c.status = 0
          AND c.dueDate <= :now
        """
    )
    fun observeDueChequeCount(now: Long): Flow<Int>

    @Query("UPDATE cheques SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteCheque(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM cheques WHERE partyId = :partyId AND isDeleted = 0 ORDER BY dueDate ASC")
    fun observeChequesOfParty(partyId: Long): Flow<List<Cheque>>

    // ---------- Cashbook ----------

    @Insert
    suspend fun insertCashEntry(entry: CashEntry): Long

    @Query("SELECT * FROM cashbook WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun observeCashEntries(): Flow<List<CashEntry>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cashbook WHERE isDeleted = 0 AND isIncome = 1")
    fun observeCashIncome(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cashbook WHERE isDeleted = 0 AND isIncome = 0")
    fun observeCashExpense(): Flow<Double>

    /** Categories the owner has already used, so they need not retype them. */
    @Query("SELECT DISTINCT category FROM cashbook WHERE isDeleted = 0 ORDER BY category COLLATE NOCASE ASC")
    suspend fun cashCategories(): List<String>

    @Query("UPDATE cashbook SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteCashEntry(id: Long, now: Long = System.currentTimeMillis())

    // ---------- Backup / restore ----------
    //
    // Backup reads EVERYTHING, including soft-deleted rows: the Recycle Bin is
    // part of the user's data, and a restore that silently emptied it would be
    // destroying something they still had a right to get back.

    @Query("SELECT * FROM parties")
    suspend fun allPartiesForBackup(): List<Party>

    @Query("SELECT * FROM transactions")
    suspend fun allEntriesForBackup(): List<LedgerEntry>

    @Query("SELECT * FROM cheques")
    suspend fun allChequesForBackup(): List<Cheque>

    @Query("SELECT * FROM cashbook")
    suspend fun allCashForBackup(): List<CashEntry>

    @Query("SELECT * FROM payment_plans")
    suspend fun allPlansForBackup(): List<PaymentPlan>

    @Query("SELECT * FROM installments")
    suspend fun allInstallmentsForBackup(): List<Installment>

    // Restore wipes and rewrites. Guarded behind an explicit warning in the UI,
    // because it replaces the current books entirely.

    @Query("DELETE FROM transactions")
    suspend fun wipeEntries()

    @Query("DELETE FROM cheques")
    suspend fun wipeCheques()

    @Query("DELETE FROM cashbook")
    suspend fun wipeCash()

    @Query("DELETE FROM installments")
    suspend fun wipeInstallments()

    @Query("DELETE FROM payment_plans")
    suspend fun wipePlans()

    @Query("DELETE FROM parties")
    suspend fun wipeParties()

    @Insert
    suspend fun restoreParties(items: List<Party>)

    @Insert
    suspend fun restoreEntries(items: List<LedgerEntry>)

    @Insert
    suspend fun restoreCheques(items: List<Cheque>)

    @Insert
    suspend fun restoreCash(items: List<CashEntry>)

    @Insert
    suspend fun restorePlans(items: List<PaymentPlan>)

    @Insert
    suspend fun restoreInstallments(items: List<Installment>)

    // ---------- Payment plans ----------

    @Insert
    suspend fun insertPlan(plan: PaymentPlan): Long

    @Update
    suspend fun updatePlan(plan: PaymentPlan)

    @Query("SELECT * FROM payment_plans WHERE id = :id")
    suspend fun getPlan(id: Long): PaymentPlan?

    /**
     * Plans with what has actually been paid against them.
     * Open plans first, then by soonest due — a closed plan needs no attention.
     */
    @Query(
        """
        SELECT pl.id, pl.partyId, p.name AS partyName,
               pl.totalAmount, pl.installmentAmount,
               COALESCE((
                   SELECT SUM(i.amount) FROM installments i
                   WHERE i.planId = pl.id AND i.isDeleted = 0
               ), 0) AS paidSoFar,
               pl.nextDueDate, pl.note, pl.isClosed
        FROM payment_plans pl
        JOIN parties p ON p.id = pl.partyId
        WHERE pl.isDeleted = 0 AND p.isDeleted = 0
        ORDER BY pl.isClosed ASC,
                 CASE WHEN pl.nextDueDate IS NULL THEN 1 ELSE 0 END,
                 pl.nextDueDate ASC
        """
    )
    fun observePlans(): Flow<List<PlanProgress>>

    @Query(
        """
        SELECT pl.id, pl.partyId, p.name AS partyName,
               pl.totalAmount, pl.installmentAmount,
               COALESCE((
                   SELECT SUM(i.amount) FROM installments i
                   WHERE i.planId = pl.id AND i.isDeleted = 0
               ), 0) AS paidSoFar,
               pl.nextDueDate, pl.note, pl.isClosed
        FROM payment_plans pl
        JOIN parties p ON p.id = pl.partyId
        WHERE pl.isDeleted = 0 AND p.isDeleted = 0 AND pl.partyId = :partyId
        ORDER BY pl.isClosed ASC, pl.createdAt DESC
        """
    )
    fun observePlansOfParty(partyId: Long): Flow<List<PlanProgress>>

    @Query("UPDATE payment_plans SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeletePlan(id: Long, now: Long = System.currentTimeMillis())

    // ---------- Instalments ----------

    @Insert
    suspend fun insertInstallment(item: Installment): Long

    @Query("SELECT * FROM installments WHERE planId = :planId AND isDeleted = 0 ORDER BY paidAt DESC")
    fun observeInstallments(planId: Long): Flow<List<Installment>>

    // ---------- Supplier bills ----------

    @Insert
    suspend fun insertBill(bill: SupplierBill): Long

    @Update
    suspend fun updateBill(bill: SupplierBill)

    @Query("SELECT * FROM supplier_bills WHERE id = :id")
    suspend fun getBill(id: Long): SupplierBill?

    @Query(
        """
        SELECT b.id, b.partyId, p.name AS partyName, b.billNumber,
               b.totalAmount, b.billDate, b.dueDate, b.isPaidInFull,
               (SELECT COUNT(*) FROM bill_items i
                WHERE i.billId = b.id AND i.isDeleted = 0) AS itemCount,
               b.note
        FROM supplier_bills b
        JOIN parties p ON p.id = b.partyId
        WHERE b.isDeleted = 0 AND p.isDeleted = 0
        ORDER BY b.billDate DESC
        """
    )
    fun observeBills(): Flow<List<BillSummary>>

    @Query("UPDATE supplier_bills SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteBill(id: Long, now: Long = System.currentTimeMillis())

    // ---------- Bill items (batch + expiry) ----------

    @Insert
    suspend fun insertBillItem(item: BillItem): Long

    @Query("SELECT * FROM bill_items WHERE billId = :billId AND isDeleted = 0 ORDER BY id ASC")
    fun observeBillItems(billId: Long): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE billId = :billId AND isDeleted = 0 ORDER BY id ASC")
    suspend fun billItems(billId: Long): List<BillItem>

    @Update
    suspend fun updateBillItem(item: BillItem)

    /**
     * Soft delete, matching every other row in this app. The batch record is
     * kept, not erased — a deleted line may still be the answer to "which
     * batch did this customer's sale come from", asked long after the owner
     * decided the line itself was a mistake.
     */
    @Query("UPDATE bill_items SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteBillItem(id: Long)

    @Query("SELECT * FROM bill_items WHERE id = :id")
    suspend fun getBillItem(id: Long): BillItem?

    // ---------- Invoices ----------

    @Insert
    suspend fun insertInvoice(invoice: Invoice): Long

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun totalInvoiceCount(): Int

    @Insert
    suspend fun insertInvoiceItem(item: InvoiceItem): Long

    /**
     * The ONLY way an invoice should be created. If the invoice already
     * carries a number (the owner typed their own, or is re-using their
     * existing paper series), that number is kept exactly as given — no
     * uniqueness is enforced here, the same way Vyapar does not stop an
     * owner typing a duplicate by hand; the printed number is theirs to
     * manage. Only a genuinely blank number is auto-assigned, and that read
     * of the count happens inside this same @Transaction for the same
     * reason as [insertEntryNumbered]: reading it outside could let two
     * invoices saved close together both become INV-000123. Every item is
     * written in the same transaction, so a crash or a cancelled screen can
     * never leave an invoice on record with none of its lines, or half of
     * them.
     */
    @Transaction
    suspend fun saveInvoiceWithItems(invoice: Invoice, items: List<InvoiceItem>): Long {
        val numbered = if (invoice.invoiceNumber.isBlank()) {
            invoice.copy(invoiceNumber = InvoiceNumber.next(totalInvoiceCount()))
        } else {
            invoice
        }
        val invoiceId = insertInvoice(numbered)
        for (item in items) {
            insertInvoiceItem(item.copy(id = 0, invoiceId = invoiceId))
        }
        return invoiceId
    }

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoice(id: Long): Invoice?

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    /**
     * Editing a saved invoice replaces its whole item list rather than
     * trying to match old rows to new ones — far simpler and safer than a
     * diff, and there is no ledger balance here for a stray extra row to
     * throw off (see the class doc on [Invoice]). The old rows are soft-
     * deleted, not hard-deleted, matching every other soft-delete in this
     * app; a crash between the two loops leaves either the full old set or
     * the full new set findable, never a partial mix, since both loops run
     * inside this one @Transaction.
     */
    @Transaction
    suspend fun updateInvoiceWithItems(invoice: Invoice, items: List<InvoiceItem>) {
        updateInvoice(invoice)
        softDeleteInvoiceItems(invoice.id)
        for (item in items) {
            insertInvoiceItem(item.copy(id = 0, invoiceId = invoice.id))
        }
    }

    @Query("UPDATE invoice_items SET isDeleted = 1 WHERE invoiceId = :invoiceId")
    suspend fun softDeleteInvoiceItems(invoiceId: Long)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId AND isDeleted = 0 ORDER BY id ASC")
    suspend fun invoiceItems(invoiceId: Long): List<InvoiceItem>

    @Query(
        """
        SELECT i.id, i.invoiceNumber, i.customerName, i.invoiceDate,
               (SELECT COUNT(*) FROM invoice_items li
                WHERE li.invoiceId = i.id AND li.isDeleted = 0) AS itemCount,
               (
                 (SELECT COALESCE(SUM(li.quantity * li.rate), 0) FROM invoice_items li
                  WHERE li.invoiceId = i.id AND li.isDeleted = 0)
                 * (1 - COALESCE(i.discountPercent, 0) / 100.0)
                 * (1 + COALESCE(i.taxPercent, 0) / 100.0)
                 + COALESCE(i.additionalChargeAmount, 0)
               ) AS grandTotal
        FROM invoices i
        WHERE i.isDeleted = 0
        ORDER BY i.invoiceDate DESC
        """
    )
    fun observeInvoices(): Flow<List<InvoiceSummary>>

    @Query("UPDATE invoices SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteInvoice(id: Long, now: Long = System.currentTimeMillis())

    /**
     * Every party in the book, for the customer field's suggestion list —
     * name AND phone, so picking a suggestion fills both rather than just
     * the name. NOT filtered to customers only — a shopkeeper occasionally
     * invoices a supplier too (a return, a one-off sale of something spare) —
     * and the field accepts any typed name besides, on or off this list.
     * Where a name is entered under more than one party, [PartyNameAndPhone]
     * is deliberately just a name/phone pair, not an id — this stays the
     * same plain convenience lookup it always was, never a link to a row.
     */
    @Query("SELECT name, phone FROM parties WHERE isDeleted = 0 ORDER BY name ASC")
    suspend fun allPartiesForInvoice(): List<PartyNameAndPhone>

    /**
     * Every batch of this product a sale could be tagged to, soonest-expiring
     * first — the same ordering ExpiringActivity uses, because selling the
     * batch that runs out soonest first is the point of tracking batches at
     * all. [BatchOption.soldFromBatch] is summed from transactions already
     * tagged to that exact bill_items row.
     */
    @Query(
        """
        SELECT i.id AS id, i.batchNumber AS batchNumber, i.expiryDate AS expiryDate,
               i.quantity AS quantity, i.unit AS unit,
               COALESCE((
                   SELECT SUM(t.quantity) FROM transactions t
                   WHERE t.billItemId = i.id AND t.isGiven = 1 AND t.isDeleted = 0
                     AND (t.unit = i.unit OR (t.unit IS NULL AND i.unit IS NULL))
               ), 0) AS soldFromBatch
        FROM bill_items i
        JOIN supplier_bills b ON b.id = i.billId
        WHERE i.productId = :productId AND i.isDeleted = 0 AND b.isDeleted = 0
        ORDER BY i.expiryDate ASC
        """
    )
    suspend fun batchOptionsForProduct(productId: Long): List<BatchOption>

    /**
     * Batches at or near expiry — showing what is LEFT of each, not what
     * arrived.
     *
     * Soonest first, expired ones at the very top — those are the ones already
     * costing money, and burying them under merely-approaching stock would get
     * the order exactly backwards.
     *
     * The quantity is the bill line minus every sale TAGGED to this exact
     * batch, under the same unit rule as Stock.combine: a sale in a different
     * unit does not subtract, because inventing a conversion would be
     * inventing stock. A batch with nothing left is dropped entirely — a
     * warning about stock that is no longer on the shelf trains the owner to
     * ignore the screen, and then it is ignored on the day it matters. The
     * honest caveat runs the other way too: an UNTAGGED sale subtracts
     * nothing, so the figure shown is "at most this much", never less than
     * the truth.
     *
     * Only stock that actually has an expiry date recorded can appear here.
     * That is a real limit and the screen says so: the app can only warn about
     * what it was told.
     */
    @Query(
        """
        SELECT i.id AS itemId, i.productName, i.batchNumber, i.expiryDate,
               (i.quantity - COALESCE((
                   SELECT SUM(t.quantity) FROM transactions t
                   WHERE t.billItemId = i.id AND t.isGiven = 1 AND t.isDeleted = 0
                     AND (t.unit = i.unit OR (t.unit IS NULL AND i.unit IS NULL))
               ), 0)) AS quantity,
               i.unit, p.name AS partyName, b.billNumber
        FROM bill_items i
        JOIN supplier_bills b ON b.id = i.billId
        JOIN parties p ON p.id = b.partyId
        WHERE i.isDeleted = 0 AND b.isDeleted = 0
          AND i.expiryDate IS NOT NULL
          AND i.expiryDate <= :cutoff
          AND i.quantity > COALESCE((
                   SELECT SUM(t.quantity) FROM transactions t
                   WHERE t.billItemId = i.id AND t.isGiven = 1 AND t.isDeleted = 0
                     AND (t.unit = i.unit OR (t.unit IS NULL AND i.unit IS NULL))
               ), 0)
        ORDER BY i.expiryDate ASC
        """
    )
    fun observeExpiringBatches(cutoff: Long): Flow<List<ExpiringBatch>>

    /** For the home badge: how many batches need attention right now. */
    @Query(
        """
        SELECT COUNT(*)
        FROM bill_items i
        JOIN supplier_bills b ON b.id = i.billId
        WHERE i.isDeleted = 0 AND b.isDeleted = 0
          AND i.expiryDate IS NOT NULL
          AND i.expiryDate <= :cutoff
          AND i.quantity > COALESCE((
                   SELECT SUM(t.quantity) FROM transactions t
                   WHERE t.billItemId = i.id AND t.isGiven = 1 AND t.isDeleted = 0
                     AND (t.unit = i.unit OR (t.unit IS NULL AND i.unit IS NULL))
               ), 0)
        """
    )
    fun observeExpiringCount(cutoff: Long): Flow<Int>

    /**
     * Trace a batch back to where it came from — for an inspector, or a bad
     * sample.
     *
     * DELIBERATELY shows the ORIGINAL bought quantity, not what remains —
     * the one screen in the app that does. An inspector's question is "which
     * supplier, which bill, how much came in", and a trace that silently
     * shrank as stock sold would misstate the very record it exists to prove.
     */
    @Query(
        """
        SELECT i.id AS itemId, i.productName, i.batchNumber, i.expiryDate,
               i.quantity, i.unit, p.name AS partyName, b.billNumber
        FROM bill_items i
        JOIN supplier_bills b ON b.id = i.billId
        JOIN parties p ON p.id = b.partyId
        WHERE i.isDeleted = 0 AND b.isDeleted = 0
          AND i.batchNumber LIKE '%' || :query || '%'
        ORDER BY b.billDate DESC
        """
    )
    suspend fun findByBatch(query: String): List<ExpiringBatch>

    // Backup coverage — a restore that dropped bills would lose the batch
    // records the dealer may legally need.

    @Query("SELECT * FROM supplier_bills")
    suspend fun allBillsForBackup(): List<SupplierBill>

    @Query("SELECT * FROM bill_items")
    suspend fun allBillItemsForBackup(): List<BillItem>

    @Query("DELETE FROM bill_items")
    suspend fun wipeBillItems()

    @Query("DELETE FROM supplier_bills")
    suspend fun wipeBills()

    @Insert
    suspend fun restoreBills(items: List<SupplierBill>)

    @Insert
    suspend fun restoreBillItems(items: List<BillItem>)

    // ---------- Products ----------
    //
    // The master list goods are tied to. Everything here is soft-delete aware
    // in the same way the rest of the app is: a product is never removed, so
    // an entry pointing at it can never be left pointing at nothing.

    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun productById(id: Long): Product?

    /** Exact identity. Deleted rows included on purpose — see [findOrCreateProduct]. */
    @Query("SELECT * FROM products WHERE nameKey = :key LIMIT 1")
    suspend fun productByKey(key: String): Product?

    /**
     * Products that merely LOOK like this one, for suggesting rather than
     * deciding. Never used to refuse a name.
     */
    @Query("SELECT * FROM products WHERE normalisedName = :normalised AND isDeleted = 0")
    suspend fun productsLike(normalised: String): List<Product>

    @Insert
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteProduct(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreProduct(id: Long)

    /**
     * The one way a product should ever be created from a typed name.
     *
     * Written as a transaction rather than left to each caller, because the
     * unique index on nameKey turns a race into a crash: two screens saving
     * "Urea" at the same moment would have one of them throw. Here the lookup
     * and the insert cannot be separated.
     *
     * A name that matches a DELETED product brings that product back rather
     * than inserting a second one. Inserting would fail on the unique index,
     * and even if it did not, the owner asking for "Urea" after having removed
     * "Urea" means the one they had, with its history still attached.
     */
    @Transaction
    suspend fun findOrCreateProduct(
        name: String,
        category: String? = null,
        defaultUnit: String? = null
    ): Product {
        val key = ProductName.key(name)
        val existing = productByKey(key)
        if (existing != null) {
            if (existing.isDeleted) {
                restoreProduct(existing.id)
                return existing.copy(isDeleted = false, deletedAt = null)
            }
            return existing
        }
        val fresh = Product(
            name = name.trim(),
            nameKey = key,
            normalisedName = ProductName.normalised(name),
            category = category,
            defaultUnit = defaultUnit
        )
        return fresh.copy(id = insertProduct(fresh))
    }

    // Backup coverage for products. A restore that dropped this table would
    // leave every productId on every entry pointing at nothing — the ledger
    // would survive and the goods would quietly stop being goods.

    @Query("SELECT * FROM products")
    suspend fun allProductsForBackup(): List<Product>

    @Query("DELETE FROM products")
    suspend fun wipeProducts()

    @Insert
    suspend fun restoreProducts(items: List<Product>)

    // ---------- Stock, batches, and season targeting ----------
    //
    // The two sides are summed in SEPARATE queries and married in Stock.combine.
    // One query joining purchases and sales to the same product would multiply
    // the rows — three bills and four sales give twelve rows, and every total
    // counted several times over. That mistake produces a believable number,
    // which is the worst kind.

    /** Stock that arrived, per product. Deleted bills and lines excluded. */
    @Query(
        "SELECT i.productId AS productId, SUM(i.quantity) AS qty, MIN(i.unit) AS unit " +
        "FROM bill_items i JOIN supplier_bills b ON b.id = i.billId " +
        "WHERE i.productId IS NOT NULL AND i.isDeleted = 0 AND b.isDeleted = 0 " +
        "GROUP BY i.productId"
    )
    suspend fun boughtPerProduct(): List<Stock.ProductQty>

    /**
     * Stock that left, per product.
     *
     * isGiven = 1 is the shop handing goods over. A quantity is required: a
     * bare cash entry against a product name moved money, not stock.
     */
    @Query(
        "SELECT t.productId AS productId, SUM(t.quantity) AS qty, MIN(t.unit) AS unit " +
        "FROM transactions t JOIN parties p ON p.id = t.partyId " +
        "WHERE t.productId IS NOT NULL AND t.quantity IS NOT NULL AND t.isGiven = 1 " +
        "AND t.isDeleted = 0 AND p.isDeleted = 0 " +
        "GROUP BY t.productId"
    )
    suspend fun soldPerProduct(): List<Stock.ProductQty>

    /** Goods that came back in. Same shape, other direction. */
    @Query(
        "SELECT t.productId AS productId, SUM(t.quantity) AS qty, MIN(t.unit) AS unit " +
        "FROM transactions t JOIN parties p ON p.id = t.partyId " +
        "WHERE t.productId IS NOT NULL AND t.quantity IS NOT NULL AND t.isGiven = 0 " +
        "AND t.isDeleted = 0 AND p.isDeleted = 0 " +
        "GROUP BY t.productId"
    )
    suspend fun returnedPerProduct(): List<Stock.ProductQty>

    /**
     * Everyone who was sold a specific batch, exactly.
     *
     * Only answers for sales the owner actually tagged with the bill line.
     * For the rest there is [customersWhoBought], which asks the weaker but
     * still useful question: who took this product while that batch was here.
     *
     * p.isCustomer = 1 on purpose: isGiven only means goods left the shop
     * TOWARDS this party, which is exactly as true of a supplier as of a
     * customer if their account is ever used for anything unusual. A batch
     * recall is for the people who might use the product, not for whoever a
     * ledger entry happened to be filed against.
     */
    @Query(
        "SELECT p.id, p.name, p.phone, p.isCustomer, p.photoPath, " +
        "0.0 AS balance, MAX(t.timestamp) AS lastActivity, p.creditLimit " +
        "FROM transactions t JOIN parties p ON p.id = t.partyId " +
        "WHERE t.billItemId = :billItemId AND t.isGiven = 1 " +
        "AND p.isCustomer = 1 AND t.isDeleted = 0 AND p.isDeleted = 0 " +
        "GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
    )
    suspend fun customersWhoGotBatch(billItemId: Long): List<PartyWithBalance>

    /**
     * Everyone who bought a product inside a window of time.
     *
     * This is both the fallback for an untagged batch and the whole of season
     * targeting. A season does not need a table of its own: "who bought urea
     * last October" is the same question as "who should be told the urea is
     * in", and asking the book directly means no configuration to fill in, get
     * wrong, or forget to update next year.
     *
     * p.isCustomer = 1 on purpose — found from the user's own test data,
     * where a party carrying a supplier bill also picked up a manually
     * entered sale and showed up in their own promotion list. Promoting to a
     * supplier is not merely unwanted; it can read as odd or even
     * embarrassing to the person receiving it, so this is filtered at the
     * query, not left to the picker to work around.
     */
    @Query(
        "SELECT p.id, p.name, p.phone, p.isCustomer, p.photoPath, " +
        "0.0 AS balance, MAX(t.timestamp) AS lastActivity, p.creditLimit " +
        "FROM transactions t JOIN parties p ON p.id = t.partyId " +
        "WHERE t.productId = :productId AND t.isGiven = 1 " +
        "AND p.isCustomer = 1 AND t.timestamp >= :from AND t.timestamp < :to " +
        "AND t.isDeleted = 0 AND p.isDeleted = 0 " +
        "GROUP BY p.id ORDER BY MAX(t.timestamp) DESC"
    )
    suspend fun customersWhoBought(productId: Long, from: Long, to: Long): List<PartyWithBalance>

    // ---------- Tying existing free text to products ----------
    //
    // Every row written before products existed carries a name and no link.
    // These three do the tying, and they are written so that the worst thing
    // they can do is nothing.

    @Query(
        "SELECT DISTINCT itemName FROM transactions " +
        "WHERE productId IS NULL AND itemName IS NOT NULL AND TRIM(itemName) != ''"
    )
    suspend fun unlinkedEntryNames(): List<String>

    @Query(
        "SELECT DISTINCT productName FROM bill_items " +
        "WHERE productId IS NULL AND TRIM(productName) != ''"
    )
    suspend fun unlinkedBillItemNames(): List<String>

    /** Fills the blank only. An entry already tied to a product is never re-tied. */
    @Query(
        "UPDATE transactions SET productId = :productId " +
        "WHERE productId IS NULL AND itemName = :name"
    )
    suspend fun linkEntriesNamed(name: String, productId: Long): Int

    @Query(
        "UPDATE bill_items SET productId = :productId " +
        "WHERE productId IS NULL AND productName = :name"
    )
    suspend fun linkBillItemsNamed(name: String, productId: Long): Int

    /**
     * Tie every unlinked name in the book to a product, creating the products
     * the names imply.
     *
     * WHAT THIS CANNOT DO, BY CONSTRUCTION:
     *
     *  - It cannot lose a name. itemName and productName are never written to,
     *    only read. Whatever the owner typed stays exactly as they typed it,
     *    and remains what they are shown.
     *  - It cannot overwrite a link. Both updates carry `productId IS NULL`,
     *    so a product the owner has already chosen by hand is never replaced
     *    by one guessed from text.
     *  - It cannot half-finish. One @Transaction: either every name is tied or
     *    the book is exactly as it was.
     *  - It cannot do damage twice. Run it again and there are no unlinked
     *    names left, so it links nothing and returns zero.
     *
     * Names are tied by exact text, not by the folded key. Folding is for
     * suggesting; a backfill that quietly merged two names the owner keeps
     * apart would be deciding something about their stock without asking.
     * findOrCreateProduct still collapses case and stray spacing, because
     * "Urea" and "urea " are not two decisions.
     */
    @Transaction
    suspend fun linkGoodsToProducts(): Int {
        var linked = 0
        for (name in unlinkedEntryNames()) {
            val product = findOrCreateProduct(name)
            linked += linkEntriesNamed(name, product.id)
        }
        for (name in unlinkedBillItemNames()) {
            val product = findOrCreateProduct(name)
            linked += linkBillItemsNamed(name, product.id)
        }
        return linked
    }

    // ---------- One-shot reads, for the printed report ----------
    //
    // A report is a snapshot, not a live view, so these return a value rather
    // than a Flow. Using the observing queries here would mean subscribing to
    // updates for a document that is written once and never changes again.

    @Query(
        """
        SELECT p.id, p.name, p.phone, p.isCustomer, p.photoPath,
               COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0) AS balance,
               COALESCE(MAX(t.timestamp), 0) AS lastActivity,
               p.creditLimit AS creditLimit
        FROM parties p
        LEFT JOIN transactions t
               ON t.partyId = p.id AND t.isDeleted = 0
        WHERE p.isDeleted = 0
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    suspend fun partiesWithBalanceOnce(): List<PartyWithBalance>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cashbook WHERE isDeleted = 0 AND isIncome = 1")
    suspend fun cashIncomeOnce(): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cashbook WHERE isDeleted = 0 AND isIncome = 0")
    suspend fun cashExpenseOnce(): Double

    @Query(
        """
        SELECT * FROM cheques
        WHERE isDeleted = 0 AND status = ${ChequeStatus.PENDING}
        ORDER BY dueDate ASC
        """
    )
    suspend fun pendingChequesOnce(): List<Cheque>

    @Query(
        """
        SELECT pl.id, pl.partyId, p.name AS partyName,
               pl.totalAmount, pl.installmentAmount,
               COALESCE((
                   SELECT SUM(i.amount) FROM installments i
                   WHERE i.planId = pl.id AND i.isDeleted = 0
               ), 0) AS paidSoFar,
               pl.nextDueDate, pl.note, pl.isClosed
        FROM payment_plans pl
        JOIN parties p ON p.id = pl.partyId
        WHERE pl.isDeleted = 0 AND p.isDeleted = 0 AND pl.isClosed = 0
        ORDER BY pl.nextDueDate ASC
        """
    )
    suspend fun openPlansOnce(): List<PlanProgress>

    @Query(
        """
        SELECT i.id AS itemId, i.productName, i.batchNumber, i.expiryDate,
               i.quantity, i.unit, p.name AS partyName, b.billNumber
        FROM bill_items i
        JOIN supplier_bills b ON b.id = i.billId
        JOIN parties p ON p.id = b.partyId
        WHERE i.isDeleted = 0 AND b.isDeleted = 0
          AND i.expiryDate IS NOT NULL
          AND i.expiryDate <= :cutoff
        ORDER BY i.expiryDate ASC
        """
    )
    suspend fun expiringBatchesOnce(cutoff: Long): List<ExpiringBatch>

    /**
     * Wipe everything and load a backup, as ONE transaction.
     *
     * This is the single most dangerous operation in the app: it deletes the
     * entire ledger and rebuilds it. If it runs as a series of separate steps
     * and any one of them fails partway — a foreign-key timing, a constraint, a
     * bad row — the owner is left with a ledger half-erased and half-restored,
     * having LOST data in the act of trying to recover it. That is the worst
     * possible outcome for a backup feature.
     *
     * Inside @Transaction it is all-or-nothing. Either the whole restore
     * succeeds, or it rolls back and the existing ledger is left exactly as it
     * was. A failed restore should cost nothing.
     *
     * Order matters even within the transaction: children are cleared before
     * parents, and parents are inserted before children, so a foreign key never
     * points at a row that is not there yet.
     */
    @Transaction
    suspend fun restoreAll(
        parties: List<Party>,
        entries: List<LedgerEntry>,
        cheques: List<Cheque>,
        cash: List<CashEntry>,
        plans: List<PaymentPlan>,
        installments: List<Installment>,
        bills: List<SupplierBill>,
        billItems: List<BillItem>,
        products: List<Product> = emptyList()
    ) {
        wipeBillItems()
        wipeBills()
        wipeInstallments()
        wipePlans()
        wipeEntries()
        wipeCheques()
        wipeCash()
        wipeParties()
        wipeProducts()

        restoreProducts(products)
        restoreParties(parties)
        restoreEntries(entries)
        restoreCheques(cheques)
        restoreCash(cash)
        restorePlans(plans)
        restoreInstallments(installments)
        restoreBills(bills)
        restoreBillItems(billItems)
    }


    // ---------- Sale Insights (all read-only, all on-device) ----------
    //
    // "A sale" here means an entry where goods/money went OUT to the customer
    // (isGiven = 1) — that is the shop selling something. Money coming back in
    // is a payment, not a new sale, so it is excluded from these totals. All
    // queries skip deleted rows.

    /** Total value of sales between two timestamps. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE isGiven = 1 AND isDeleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun salesTotalBetween(from: Long, to: Long): Double

    /** How many sales (entry count) between two timestamps. */
    @Query("SELECT COUNT(*) FROM transactions " +
           "WHERE isGiven = 1 AND isDeleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun salesCountBetween(from: Long, to: Long): Int

    /**
     * Top products by quantity sold in a period. Only entries that actually
     * named a product and gave a quantity count — a bare cash sale has no
     * product to rank.
     */
    @Query("SELECT itemName AS name, SUM(quantity) AS qty, unit AS unit, COUNT(*) AS lines " +
           "FROM transactions " +
           "WHERE isGiven = 1 AND isDeleted = 0 AND timestamp >= :from AND timestamp < :to " +
           "AND itemName IS NOT NULL AND itemName != '' AND quantity IS NOT NULL " +
           "GROUP BY itemName, unit ORDER BY qty DESC LIMIT :limit")
    suspend fun topProductsBetween(from: Long, to: Long, limit: Int): List<ProductStat>

    /** Top customers by total purchases in a period. */
    @Query("SELECT p.name AS name, SUM(t.amount) AS total " +
           "FROM transactions t JOIN parties p ON p.id = t.partyId " +
           "WHERE t.isGiven = 1 AND t.isDeleted = 0 AND p.isDeleted = 0 " +
           "AND t.timestamp >= :from AND t.timestamp < :to " +
           "GROUP BY t.partyId ORDER BY total DESC LIMIT :limit")
    suspend fun topCustomersBetween(from: Long, to: Long, limit: Int): List<CustomerStat>


    // ---------- Today's summary (a day's activity at a glance) ----------

    /** Total GIVEN (goods/money out to parties) in a time range. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE isGiven = 1 AND isDeleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun givenBetween(from: Long, to: Long): Double

    /** Total RECEIVED (payments in) in a time range. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE isGiven = 0 AND isDeleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun receivedBetween(from: Long, to: Long): Double

    /** How many ledger entries in a time range. */
    @Query("SELECT COUNT(*) FROM transactions " +
           "WHERE isDeleted = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun entryCountBetween(from: Long, to: Long): Int


    // ---------- Single entry detail (view / edit) ----------

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getEntry(id: Long): LedgerEntry?

    /**
     * The ONLY way a numbered ledger entry should be created.
     *
     * The receipt number is count+1, and for that to be trustworthy the
     * count must be read and the row written as one indivisible step. Read
     * outside the transaction — as every call site used to do — two saves
     * landing close together could both read the same count and both become
     * RK-000123, and a duplicate receipt number on a business record is the
     * kind of flaw an auditor reads as intent. SQLite runs one write
     * transaction at a time, so inside @Transaction the pair is safe.
     */
    @Transaction
    suspend fun insertEntryNumbered(entry: LedgerEntry): Long {
        val count = totalEntryCount()
        return insertEntry(entry.copy(entryNumber = EntryNumber.next(count)))
    }

    @Update
    suspend fun updateEntry(entry: LedgerEntry)

    // ---------- Duplicate-customer merge ----------

    @Query("UPDATE transactions SET partyId = :survivorId WHERE partyId = :loserId")
    suspend fun reassignEntriesToParty(loserId: Long, survivorId: Long)

    @Query("UPDATE supplier_bills SET partyId = :survivorId WHERE partyId = :loserId")
    suspend fun reassignBillsToParty(loserId: Long, survivorId: Long)

    @Query("UPDATE cheques SET partyId = :survivorId WHERE partyId = :loserId")
    suspend fun reassignChequesToParty(loserId: Long, survivorId: Long)

    @Query("UPDATE payment_plans SET partyId = :survivorId WHERE partyId = :loserId")
    suspend fun reassignPlansToParty(loserId: Long, survivorId: Long)

    /**
     * A customer entered twice, folded into one.
     *
     * Every entry, bill, cheque, and payment plan that pointed at [loserId]
     * now points at [survivorId] instead. The balance needs no arithmetic of
     * its own to stay right — it is only ever a sum over these rows, and the
     * sum does not change because of which party id they carry.
     *
     * [loserId] then goes to the recycle bin exactly like any other deleted
     * customer, but WITHOUT taking its entries with it — they already belong
     * to [survivorId] by the time this line runs. Using [softDeleteParty]
     * here rather than [softDeleteParties] is deliberate: the latter also
     * bins a party's own entries, which would be wrong the moment after this
     * function has just moved them out from under it.
     *
     * One @Transaction, so a crash partway can never leave some of a
     * customer's book moved to the survivor and the rest still scattered
     * under the old id.
     */
    @Transaction
    suspend fun mergeParty(loserId: Long, survivorId: Long, now: Long = System.currentTimeMillis()) {
        reassignEntriesToParty(loserId, survivorId)
        reassignBillsToParty(loserId, survivorId)
        reassignChequesToParty(loserId, survivorId)
        reassignPlansToParty(loserId, survivorId)
        softDeleteParty(loserId, now)
    }

    /**
     * A whole suspected-duplicate group folded into one survivor at once.
     *
     * One @Transaction across every loser in the group, for the same reason
     * [softDeleteParties] batches its parties under one: a group of three or
     * four found together should merge together, not leave the owner with
     * two folded in and a third stuck half-done by a crash between them.
     */
    @Transaction
    suspend fun mergeParties(loserIds: List<Long>, survivorId: Long, now: Long = System.currentTimeMillis()) {
        for (loserId in loserIds) {
            mergeParty(loserId, survivorId, now)
        }
    }

    /**
     * Records that the owner looked at a suspected-duplicate group and
     * confirmed it is not one. REPLACE, not IGNORE: if the same key is ever
     * dismissed twice — it should not normally reach the screen a second
     * time once dismissed, but a crash or an old cached list could still
     * show it — this keeps the newer timestamp rather than failing on the
     * primary key.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismissDuplicate(dismissed: DismissedDuplicate)

    /**
     * Every group key the owner has already ruled out, for
     * [com.innovation313.roshankhata.ui.DuplicateDetector.find] to skip.
     */
    @Query("SELECT partyIdsKey FROM dismissed_duplicates")
    suspend fun getDismissedDuplicateKeys(): List<String>
}
