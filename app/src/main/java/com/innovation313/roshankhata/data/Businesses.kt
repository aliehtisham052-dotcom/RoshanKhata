package com.innovation313.roshankhata.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
     * Add a business. Its file name embeds its id and an id is never reused,
     * so two businesses can never point at one file — the separation the
     * whole feature exists for is decided here, once, and nowhere else.
     *
     * The name is written straight into the new business's own profile too,
     * so the moment it is opened its header and its invoices already carry
     * it — the registry copy is only the fallback.
     */
    fun create(context: Context, name: String): Business {
        val all = list(context)
        val id = (all.maxOf { it.id }) + 1
        val fresh = Business(id, name.trim(), "roshan_khata_b$id.db")
        save(context, all + fresh)
        BusinessProfile.setNameOf(context, id, name)
        return fresh
    }

    /** Rename everywhere a name lives: the business's own profile and the registry. */
    fun rename(context: Context, id: Long, name: String) {
        BusinessProfile.setNameOf(context, id, name)
        save(context, list(context).map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** What the switcher shows: the shop's own profile name first, registry as fallback. */
    fun displayName(context: Context, b: Business): String? =
        BusinessProfile.nameOf(context, b.id) ?: b.name

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
