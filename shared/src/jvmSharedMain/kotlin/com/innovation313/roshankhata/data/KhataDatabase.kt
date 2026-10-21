package com.innovation313.roshankhata.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabaseConstructor
import androidx.room.RoomDatabase

@Database(
    entities = [
        Party::class,
        LedgerEntry::class,
        Cheque::class,
        CashEntry::class,
        PaymentPlan::class,
        Installment::class,
        SupplierBill::class,
        BillItem::class,
        Product::class,
        DismissedDuplicate::class,
        Invoice::class,
        InvoiceItem::class,
        EntryItem::class,
        DayClose::class,
        Scheme::class,
        Staff::class,
        StaffAttendance::class,
        StaffPayment::class,
        Sale::class,
        SaleItem::class,
        SalePayment::class,
        AuditLog::class,
        SaleReturn::class,
        SaleReturnItem::class,
        StockAdjustment::class,
        PurchaseOrder::class,
        PurchaseOrderItem::class,
        BookSetting::class,
        LoyaltyEntry::class,
        Shift::class
    ],
    version = KHATA_DB_VERSION,
    exportSchema = true
)
@ConstructedBy(KhataDatabaseConstructor::class)
abstract class KhataDatabase : RoomDatabase() {

    /**
     * The real DAO, as Room built it. Only two kinds of caller use this name
     * directly: [khataDao] below, and [ViewerSync], whose whole job is the
     * one write a read-only phone's copy ever receives (replacing it whole).
     */
    abstract fun ledgerDao(): KhataDao

    /**
     * The read-only DAO on a helper's phone ([ViewerMode] in the app): reads
     * pass through, every write refuses. Set once by the app's
     * KhataDatabase.get(), before the instance is handed out; null on every
     * other phone, in every test, and on the desktop.
     */
    @Volatile
    var viewer: KhataDao? = null

    /** True when this instance is the read-only copy on a helper's phone. */
    val viewerCopy: Boolean get() = viewer != null

    /**
     * What every screen uses. On a read-only phone it is the viewer DAO; on
     * every other phone, and in every test, it is simply Room's own DAO.
     */
    fun khataDao(): KhataDao = viewer ?: ledgerDao()

    /**
     * Opening the database is each platform's own business (Android: the
     * app's KhataDatabase.get(context), with its migrations; desktop: its own
     * file), so the shared class only declares the companion they extend.
     */
    companion object
}

/**
 * Room's constructor for the platforms that cannot find the generated class
 * by name (desktop). Room's KSP writes the actual for each target.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object KhataDatabaseConstructor : RoomDatabaseConstructor<KhataDatabase> {
    override fun initialize(): KhataDatabase
}

/**
 * The schema version (38 since 22 Oct 2026: a uid and its index on every row a
 * live sync can carry; 37 since 21 Oct 2026: a stock adjustment's batch; 36 the
 * same night: book settings, loyalty, shifts,
 * product packs/tax/variants, line tax, the audit chain and row uids; 35 the
 * same day: stock adjustments and purchase orders; 34 the same day: staff logins — role, pinHash,
 * pinSalt on staff; 33 the same day: returns — sale_returns,
 * sale_return_items; 32 the same day: the counter — sales, sale_items,
 * sale_payments, audit_log; saleId on transactions and cashbook,
 * paymentMethod on cashbook, barcode/costPrice/lowStockLevel on products). Moved here from Migrations.kt
 * with the database class (KMP Phase 3); the Android migrations stay in the
 * app and still end at this number.
 */
const val KHATA_DB_VERSION = 38

object EntryNumber {
    fun next(existingCount: Int): String =
        "RK-%06d".format(Digits.FIGURES, existingCount + 1)
}
