package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The register of businesses, and which one is open.
 *
 * Separation is by FILE, not by column: each business owns a whole database
 * file of its own, and nothing else. No table gained a businessId, no query
 * gained a WHERE clause — a query physically cannot see another shop's rows,
 * because they are not in the file it is reading. The strongest wall between
 * two ledgers is the one the query language cannot cross.
 *
 * The first business keeps the original file name, "roshan_khata.db". Every
 * ledger written before this feature existed is therefore, byte for byte,
 * Business 1 — no migration ran, nothing was copied, nothing could be lost
 * in a step that never happened.
 *
 * The registry itself is deliberately tiny: id, display name, file name.
 * Everything a business actually contains lives in its own database file;
 * this is only the list on the cupboard door saying which ledgers exist.
 */
object Businesses {

    /** The file every pre-multi-business install already has. */
    const val LEGACY_FILE = "roshan_khata.db"

    private const val PREFS = "businesses"
    private const val KEY_LIST = "list"
    private const val KEY_ACTIVE = "active"

    data class Business(
        val id: Long,
        /** Registry display name. Null for Business 1 — its name keeps coming
         *  from BusinessProfile, exactly as before this feature. */
        val name: String?,
        /** Database file name, unique per business, never reused. */
        val file: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Every business, Business 1 first.
     *
     * An install that has never touched multi-business has no registry at
     * all; it is answered as the single legacy business without writing
     * anything. The registry is only ever written by [create] — a phone that
     * never adds a second shop never gains the file.
     */
    fun list(context: Context): List<Business> {
        val raw = prefs(context).getString(KEY_LIST, null)
            ?: return listOf(Business(1L, null, LEGACY_FILE))
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Business>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Business(
                        id = o.getLong("id"),
                        name = o.optString("name").takeIf { it.isNotBlank() },
                        file = o.getString("file")
                    )
                )
            }
            // A registry that lost the legacy business would strand every
            // pre-existing ledger; refuse to believe it rather than act on it.
            if (out.none { it.file == LEGACY_FILE }) listOf(Business(1L, null, LEGACY_FILE))
            else out
        } catch (e: Exception) {
            // A corrupt registry must never lock the owner out of their book.
            listOf(Business(1L, null, LEGACY_FILE))
        }
    }

    /** The open business. Falls back to Business 1 if the stored id is gone. */
    fun active(context: Context): Business {
        val id = prefs(context).getLong(KEY_ACTIVE, 1L)
        val all = list(context)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    /** The database file the app should have open right now. */
    fun activeDbFile(context: Context): String = active(context).file

    /**
     * Make sure a business with this exact id exists, and return it.
     *
     * [create] hands out the next free id, which is right when the owner adds
     * a shop and wrong when a shop is being recovered: a shop discovered on
     * Drive already HAS an id, written into the name of its backup file, and
     * putting it back under a different one would point it at the wrong file
     * forever. Recovery therefore names its own id, and creation does not.
     *
     * Existing businesses are left exactly as they are — a recovery must never
     * quietly rename a shop that is already on this phone.
     */
    @Synchronized
    fun ensure(context: Context, id: Long, name: String?): Business {
        list(context).firstOrNull { it.id == id }?.let { return it }
        val file = if (id == 1L) LEGACY_FILE else "roshan_khata_b$id.db"
        val fresh = Business(id, name?.trim(), file)
        save(context, list(context) + fresh)
        if (!name.isNullOrBlank()) BusinessProfile.setNameOf(context, id, name)
        return fresh
    }

    /**
     * Add a business. Its file name embeds its id and an id is never reused,
     * so two businesses can never point at one file — the separation the
     * whole feature exists for is decided here, once, and nowhere else.
     *
     * The name is written straight into the new business's own profile too,
     * so the moment it is opened its header and its invoices already carry
     * it — the registry copy is only the fallback.
     */
    @Synchronized
    fun create(context: Context, name: String): Business {
        val all = list(context)
        val id = (all.maxOf { it.id }) + 1
        val fresh = Business(id, name.trim(), "roshan_khata_b$id.db")
        save(context, all + fresh)
        BusinessProfile.setNameOf(context, id, name)
        return fresh
    }

    /** Rename everywhere a name lives: the business's own profile and the registry. */
    @Synchronized
    fun rename(context: Context, id: Long, name: String) {
        BusinessProfile.setNameOf(context, id, name)
        save(context, list(context).map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** What the switcher shows: the shop's own profile name first, registry as fallback. */
    fun displayName(context: Context, b: Business): String? =
        BusinessProfile.nameOf(context, b.id) ?: b.name

    /**
     * The naming pattern every per-business key and file follows: nothing
     * for Business 1 — its keys and files predate this feature and must
     * keep their names — and "_b<id>" for everyone else. One definition,
     * so no two features can disagree about what belongs to whom.
     *
     * Split into a pure by-id form and the active-business convenience that
     * everywhere else already calls, so [delete] can name a business's own
     * keys without needing that business to be the open one.
     */
    fun suffixFor(id: Long): String = if (id == 1L) "" else "_b$id"

    fun suffix(context: Context): String = suffixFor(active(context).id)

    /**
     * Open a different business.
     *
     * The database singleton is closed here, inside the same lock that
     * [KhataDatabase.get] builds under, so there is no moment where a caller
     * can be handed the old shop's ledger after the switch has been asked
     * for. The next get() opens the new file.
     */
    fun switchTo(context: Context, id: Long) {
        require(list(context).any { it.id == id }) { "unknown business $id" }
        prefs(context).edit().putLong(KEY_ACTIVE, id).commit()
        KhataDatabase.closeActive()
        // Photo caches are keyed by party id, and party ids repeat across
        // businesses — a warm cache would put one shop's faces on another's
        // customers. See PartyPhoto.dropCaches.
        PartyPhoto.dropCaches()
    }

    /**
     * Whether [id] is even offered for delete.
     *
     * Business 1 can never be deleted — it is the ledger every install
     * already had before this feature existed, not a "shop created" in the
     * sense this feature means, and its file/keys are shared with things
     * that are not per-business at all (App Lock, Drive sign-in). Nor can
     * the OPEN business be deleted: its database file has a live Room
     * instance on it, and removing a file out from under an open connection
     * is not a risk worth taking. Switch away first, then delete.
     *
     * A useful side effect of the first rule: since the registry always
     * keeps Business 1, refusing to delete it also means "there must always
     * be one book left" is true by construction — no separate count check
     * is needed here.
     */
    fun canDelete(context: Context, id: Long): Boolean =
        id != 1L && id != active(context).id

    /**
     * A business's own numbers, read without switching to it — for the
     * delete confirmation, which must show what is about to be lost before
     * asking the owner to type the shop's name.
     *
     * Opens a Room instance of its own, straight onto the business's file,
     * and closes it again once the one query is answered. This is
     * deliberately NOT [KhataDatabase.get] — that call is reserved for the
     * ACTIVE business only, and a preview read of some other shop's numbers
     * must never be able to touch (or be confused with) the instance a
     * live screen is holding.
     */
    suspend fun summaryOf(context: Context, business: Business): BusinessDeleteSummary =
        withContext(Dispatchers.IO) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                KhataDatabase::class.java,
                business.file
            ).addMigrations(*ALL_MIGRATIONS).build()
            try {
                db.khataDao().deleteSummary()
            } finally {
                db.close()
            }
        }

    /**
     * Permanently removes a business from THIS PHONE: its ledger file, its
     * identity (profile text + its own stamp/QR/signature folder), its
     * customers' photos, its per-shop invoice/business-card settings, and
     * its backup bookkeeping — then its row in this registry, last, so
     * everything above can still be found by id while it is being removed.
     *
     * Requires [canDelete] to hold and fails loudly if it does not, rather
     * than silently doing nothing — a caller reaching this function is
     * expected to have already checked, and a delete that quietly no-ops
     * would be worse than one that crashes and gets noticed.
     *
     * Deliberately does NOT touch Drive. A shop's backup there, if any, is
     * left exactly where it was — "delete" is a promise about this phone,
     * and the owner may still want that copy, or may want to remove it
     * themselves. See [DriveBackup.clearLocalState].
     */
    @Synchronized
    fun delete(context: Context, id: Long) {
        require(canDelete(context, id)) {
            "business $id cannot be deleted (either Business 1, or the business currently open)"
        }
        val target = list(context).firstOrNull { it.id == id } ?: return

        // The ledger itself. deleteDatabase() also removes SQLite's
        // -wal/-shm/-journal companions — the framework's own way of
        // removing a database, not a hand-rolled guess at which suffixes
        // WAL mode happens to leave behind.
        context.deleteDatabase(target.file)

        // Identity: profile text, and its own biz_b<id>/ folder holding
        // whichever of stamp/QR/signature this shop had saved.
        context.deleteSharedPreferences("business_profile_b$id")
        File(context.filesDir, "biz_b$id").deleteRecursively()

        // Customer photos. Party ids repeat across files — file numbers
        // its own parties from 1 — so this shop's faces live in their own
        // folder and were never anywhere near Business 1's.
        File(context.filesDir, "party_photos_b$id").deleteRecursively()

        // Per-shop feature toggles that live in prefs files of their own.
        context.deleteSharedPreferences("biz_card_b$id")
        context.deleteSharedPreferences("invoice_feature_settings_b$id")

        // Bookkeeping that lives as SUFFIXED KEYS inside shared prefs files
        // rather than files of its own (those files are also Business 1's
        // and App Lock's home, so they cannot simply be deleted whole).
        // Each owner clears its own keys, so this never has to duplicate
        // constants that only DriveBackup/BackupReminder truly own.
        DriveBackup.clearLocalState(context, id)
        BackupReminder.clear(context, id)

        // The registry row itself, last.
        save(context, list(context).filterNot { it.id == id })
    }

    private fun save(context: Context, all: List<Business>) {
        val arr = JSONArray()
        all.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("name", b.name ?: "")
                    .put("file", b.file)
            )
        }
        // commit(), not apply(): the registry is the map to every ledger on
        // the phone, and a map is worth waiting one disk write for.
        prefs(context).edit().putString(KEY_LIST, arr.toString()).commit()
    }
}
