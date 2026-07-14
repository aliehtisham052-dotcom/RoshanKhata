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

    // Restore wipes and rewrites. Guarded behind an explicit warning in the UI,
    // because it replaces the current books entirely.

    @Query("DELETE FROM transactions")
    suspend fun wipeEntries()

    @Query("DELETE FROM cheques")
    suspend fun wipeCheques()

    @Query("DELETE FROM cashbook")
    suspend fun wipeCash()

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
}
