package com.innovation313.roshankhata.data

/**
 * A copy's big tables as the restore takes them (17 Nov 2026, the audit's L001): each one a page at a time, in the
 * order the restore asks for them ([KhataDao.restoreAllFrom]). A copy read whole hands each list over as its one page
 * ([of]) — the restore exactly as it was; a big copy read a page at a time reads each table again from its file
 * ([BackupCodec.ParsedBackup.paged]) and lets every page go once it is written. One restore body for both, so a rule
 * the restore keeps is kept for every copy.
 */
interface BigRows {
    suspend fun entries(take: suspend (List<LedgerEntry>) -> Unit)
    suspend fun entryItems(take: suspend (List<EntryItem>) -> Unit)
    suspend fun cash(take: suspend (List<CashEntry>) -> Unit)
    suspend fun sales(take: suspend (List<Sale>) -> Unit)
    suspend fun saleItems(take: suspend (List<SaleItem>) -> Unit)
    suspend fun salePayments(take: suspend (List<SalePayment>) -> Unit)
    suspend fun audit(take: suspend (List<AuditLog>) -> Unit)
    suspend fun seals(take: suspend (List<BookSeal>) -> Unit)

    companion object {
        /** Lists as one page each, every one handed over even when empty — what the restore always did with them. */
        fun of(
            entries: List<LedgerEntry>, entryItems: List<EntryItem>, cash: List<CashEntry>, sales: List<Sale>,
            saleItems: List<SaleItem>, salePayments: List<SalePayment>, audit: List<AuditLog>, seals: List<BookSeal>
        ): BigRows = object : BigRows {
            override suspend fun entries(take: suspend (List<LedgerEntry>) -> Unit) = take(entries)
            override suspend fun entryItems(take: suspend (List<EntryItem>) -> Unit) = take(entryItems)
            override suspend fun cash(take: suspend (List<CashEntry>) -> Unit) = take(cash)
            override suspend fun sales(take: suspend (List<Sale>) -> Unit) = take(sales)
            override suspend fun saleItems(take: suspend (List<SaleItem>) -> Unit) = take(saleItems)
            override suspend fun salePayments(take: suspend (List<SalePayment>) -> Unit) = take(salePayments)
            override suspend fun audit(take: suspend (List<AuditLog>) -> Unit) = take(audit)
            override suspend fun seals(take: suspend (List<BookSeal>) -> Unit) = take(seals)
        }
    }
}
