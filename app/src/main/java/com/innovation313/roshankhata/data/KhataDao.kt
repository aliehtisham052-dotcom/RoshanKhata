package com.innovation313.roshankhata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
     * What customers owe me, where I am confident of collecting it,
     * excluding Qarz-e-Hasna (counted separately) and doubtful debts.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
          AND t.isQarzeHasna = 0
          AND t.recovery = 0
        """
    )
    fun observeCertainReceivables(): Flow<Double>

    /** Debts the owner has marked as doubtful. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
          AND t.isQarzeHasna = 0
          AND t.recovery = 1
        """
    )
    fun observeDoubtfulReceivables(): Flow<Double>

    /** Interest-free loans the owner has given out — still their wealth. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
          AND t.isQarzeHasna = 1
        """
    )
    fun observeQarzeHasnaGiven(): Flow<Double>

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

    /**
     * Batches at or near expiry.
     *
     * Soonest first, expired ones at the very top — those are the ones already
     * costing money, and burying them under merely-approaching stock would get
     * the order exactly backwards.
     *
     * Only stock that actually has an expiry date recorded can appear here.
     * That is a real limit and the screen says so: the app can only warn about
     * what it was told.
     */
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
        """
    )
    fun observeExpiringCount(cutoff: Long): Flow<Int>

    /** Trace a batch back to where it came from — for an inspector, or a bad sample. */
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
}
