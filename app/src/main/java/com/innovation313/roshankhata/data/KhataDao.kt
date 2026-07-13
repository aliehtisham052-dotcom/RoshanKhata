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
               COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0) AS balance
        FROM parties p
        LEFT JOIN transactions t
               ON t.partyId = p.id AND t.isDeleted = 0
        WHERE p.isDeleted = 0
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observePartiesWithBalance(): Flow<List<PartyWithBalance>>

    /** Soft delete — party goes to the Recycle Bin, not gone for good. */
    @Query("UPDATE parties SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteParty(id: Long, now: Long = System.currentTimeMillis())

    // ---------- Ledger entries ----------

    @Insert
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeEntries(partyId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun totalEntryCount(): Int

    /** Soft delete — entry goes to the Recycle Bin. */
    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDeleteEntry(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreEntry(id: Long)

    // ---------- Totals for the home summary ----------

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t
        JOIN parties p ON p.id = t.partyId
        WHERE t.isDeleted = 0 AND p.isDeleted = 0
        """
    )
    fun observeNetBalance(): Flow<Double>
}
