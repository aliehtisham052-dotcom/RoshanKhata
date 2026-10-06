package com.innovation313.roshankhata.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.innovation313.roshankhata.R
import java.io.File

/**
 * A phone that only LOOKS at the owner's book (3 Oct 2026).
 *
 * The shop the owner described: one owner, two or three helpers. Helpers note
 * the day on paper; in the evening the owner enters it and backs up. Next
 * morning the helpers need the balances to chase payments, and nothing else.
 * So exactly one phone writes, and every other phone reads a copy.
 *
 * WHY THIS IS SAFE BY CONSTRUCTION, not by remembering every button:
 *
 *  - The copy lives in its own database file, [VIEWER_FILE], under its own
 *    business id, [VIEWER_ID]. A phone that already had its own ledger keeps
 *    it untouched; leaving viewer mode brings it straight back.
 *  - [KhataDatabase.khataDao] hands out a [ViewerDao] for that file. Every
 *    write method on it refuses — so a button this change forgot to hide still
 *    cannot change a single row.
 *  - [DriveBackup.backup] refuses to upload from a viewer. This is the rule
 *    that matters most: a viewer's copy is always older than the owner's book,
 *    and uploading it would overwrite the owner's newest backup on Drive.
 *  - Owner-only screens (backup, restore, bin, settings…) do not open at all;
 *    see [ownerOnly] and BaseActivity.
 *
 * Refreshing replaces the whole copy from the owner's latest backup, so
 * anything a viewer somehow changed locally is gone at the next refresh, and
 * never travelled anywhere in the meantime.
 */
object ViewerMode {

    /** Never handed out by [Businesses.create], whose ids start at 1. */
    const val VIEWER_ID = 0L

    /** The viewer's copy. Separate from every real business file on the phone. */
    const val VIEWER_FILE = "roshan_khata_viewer.db"

    const val SOURCE_DRIVE = "drive"
    const val SOURCE_FILE = "file"

    private const val PREFS = "viewer_mode"
    private const val KEY_ON = "on"
    private const val KEY_SOURCE = "source"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_DRIVE_BUSINESS = "drive_business_id"
    private const val KEY_SHOP = "shop_name"
    private const val KEY_DATA_AT = "data_at"
    private const val KEY_REFRESHED_AT = "refreshed_at"

    /** The business [Businesses.active] reports while this phone is a viewer. */
    val business = Businesses.Business(VIEWER_ID, null, VIEWER_FILE)

    @Volatile
    private var appContext: Context? = null

    /** Called once from the Application, so a refused write can say why. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun source(context: Context): String? = prefs(context).getString(KEY_SOURCE, null)
    fun account(context: Context): String? = prefs(context).getString(KEY_ACCOUNT, null)
    /** Which of the owner's businesses this copy follows on Drive. 1 = the first shop. */
    fun driveBusinessId(context: Context): Long = prefs(context).getLong(KEY_DRIVE_BUSINESS, 1L)
    fun shopName(context: Context): String? = prefs(context).getString(KEY_SHOP, null)

    /** When the OWNER made the backup this copy came from. 0 = unknown. */
    fun dataAt(context: Context): Long = prefs(context).getLong(KEY_DATA_AT, 0L)

    /** When this phone last loaded a copy. */
    fun refreshedAt(context: Context): Long = prefs(context).getLong(KEY_REFRESHED_AT, 0L)

    /**
     * Turn the role on BEFORE the copy is written, so the database that opens
     * next is the viewer's file and not this phone's own book. Committed, not
     * applied: the very next line opens the database and must see it.
     */
    internal fun switchOn(context: Context) {
        prefs(context).edit().putBoolean(KEY_ON, true).commit()
        KhataDatabase.closeActive()
        PartyPhoto.dropCaches()
    }

    internal fun recordCopy(
        context: Context,
        source: String,
        account: String?,
        driveBusinessId: Long,
        shopName: String?,
        dataAt: Long
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ON, true)
            .putString(KEY_SOURCE, source)
            .putString(KEY_ACCOUNT, account)
            .putLong(KEY_DRIVE_BUSINESS, driveBusinessId)
            .putString(KEY_SHOP, shopName)
            .putLong(KEY_DATA_AT, dataAt)
            .putLong(KEY_REFRESHED_AT, System.currentTimeMillis())
            .commit()
    }

    /**
     * Back to a normal phone: the viewer's copy and everything filed under
     * its id are removed, and the phone's own book (if it had one) is what
     * opens next. Nothing on Drive is touched — the owner's backup is his.
     *
     * The Drive account the viewer used is stored only here, never in
     * [DriveAuth], so leaving cannot leave this phone signed in to the
     * owner's Drive for its OWN backups. That would make it a second writer
     * to the owner's file — the exact accident this mode exists to prevent.
     */
    fun leave(context: Context) {
        val ctx = context.applicationContext
        prefs(ctx).edit().clear().commit()
        KhataDatabase.closeActive()
        PartyPhoto.dropCaches()
        ctx.deleteDatabase(VIEWER_FILE)
        val suffix = Businesses.suffixFor(VIEWER_ID)
        ctx.deleteSharedPreferences("business_profile$suffix")
        File(ctx.filesDir, "biz$suffix").deleteRecursively()
        File(ctx.filesDir, "party_photos$suffix").deleteRecursively()
        ctx.deleteSharedPreferences("biz_card$suffix")
        ctx.deleteSharedPreferences("invoice_feature_settings$suffix")
        DriveBackup.clearLocalState(ctx, VIEWER_ID)
        BackupReminder.clear(ctx, VIEWER_ID)
    }

    /**
     * Screens that only exist to change the book or its backups. On a viewer
     * they do not open; BaseActivity checks this before anything is built.
     * Matched by simple name so this file need not import every screen.
     */
    private val OWNER_ONLY = setOf(
        "BackupActivity",
        "BusinessSwitchActivity",
        "BusinessSettingsActivity",
        "InvoiceEditorActivity",
        "InvoiceSettingsActivity",
        "ImportContactsActivity",
        "DuplicateCustomersActivity",
        "RecycleBinActivity",
        "SnapshotsActivity",
        "ImageCropActivity",
        // Salaries are private: a helper may be on the list himself.
        "StaffActivity"
    )

    fun ownerOnly(screen: Class<*>): Boolean = screen.simpleName in OWNER_ONLY

    fun ownerOnly(simpleName: String): Boolean = simpleName in OWNER_ONLY

    /**
     * For a button handler: true (and a short note to the person holding the
     * phone) when this phone may not do it. Use as `if (ViewerMode.refuse(this)) return`.
     */
    fun refuse(context: Context): Boolean {
        if (!isOn(context)) return false
        say(context)
        return true
    }

    /** Hide controls that would only lead to a refusal. No-op on a normal phone. */
    fun hide(activity: Activity, vararg ids: Int) {
        if (!isOn(activity)) return
        ids.forEach { id -> activity.findViewById<View>(id)?.visibility = View.GONE }
    }

    /** Hide views already in hand (dialog rows, adapters). */
    fun hide(context: Context, vararg views: View?) {
        if (!isOn(context)) return
        views.forEach { it?.visibility = View.GONE }
    }

    /** Why a Drive upload was refused: this phone only reads the owner's copy. */
    class ReadOnlyPhone : IllegalStateException("Read-only phone: backups come only from the owner's phone")

    /**
     * Buttons whose only job is to write. On a read-only phone they are
     * hidden when the screen is built, and — because a screen may show one
     * again later (a selection bar, a button that waits for data) — they also
     * stop answering touches, saying why instead. The DAO refuses anyway;
     * this is so the person holding the phone is never offered a button
     * that cannot work.
     */
    private val WRITE_BUTTONS = intArrayOf(
        R.id.btnGave, R.id.btnGot, R.id.fabAddParty, R.id.btnDeleteSelected,
        R.id.btnEdit, R.id.btnDelete, R.id.fabAddBill, R.id.fabAddCash, R.id.fabAddCheque,
        R.id.fabAddInvoice, R.id.fabAddPlan, R.id.btnCloseDay, R.id.btnAddScheme
    )

    /** Called by BaseActivity after every setContentView. No-op on a normal phone. */
    @SuppressLint("ClickableViewAccessibility")
    fun lockWriteButtons(activity: Activity) {
        if (!isOn(activity)) return
        for (id in WRITE_BUTTONS) {
            val v = activity.findViewById<View>(id) ?: continue
            v.visibility = View.GONE
            v.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) say(activity)
                true
            }
        }
    }

    @Volatile
    private var lastSaidAt = 0L

    /**
     * Called by [ViewerDao] when a write reaches the database anyway. It may
     * run on any thread; the note is posted to the main one, and at most once
     * every few seconds so a screen that tries three writes says it once.
     */
    internal fun writeRefused() {
        val ctx = appContext ?: return
        say(ctx)
    }

    private fun say(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSaidAt < 3000L) return
        lastSaidAt = now
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(app, R.string.viewer_refused, Toast.LENGTH_LONG).show() }
        }
    }
}
