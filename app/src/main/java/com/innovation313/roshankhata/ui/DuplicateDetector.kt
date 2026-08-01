package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.ProductName

/**
 * Suggests which customers in the book might be the same person entered
 * twice — never decides. The screen that uses this shows every group to the
 * owner, who picks which record survives and confirms; nothing here writes
 * to the database or merges anything on its own.
 *
 * Two signals, because either alone misses real cases a shop's book actually
 * has: a name spelled two different ways with no phone recorded on either
 * ("Akram" / "Muhammad Akram"), or the very same phone number sitting under
 * two spellings that fold nowhere near each other.
 *
 * Name comparison reuses [ProductName.normalised] rather than repeating the
 * word-splitting — it is already "one definition of sameness" for a name in
 * this app (built on [NameSearch.fold], which the search box and voice entry
 * both lean on), and a second, slightly different one here would be exactly
 * the kind of two-screens-two-rules problem [NameSearch] itself was written
 * to avoid.
 *
 * Grouping is one pass, not a transitive chain across signals — a party that
 * shares a name with one record and a phone with a different, unrelated one
 * shows up in two group cards rather than one merged three-way suggestion.
 * Simpler, and self-correcting: merging either pair and reopening this screen
 * catches whatever is left.
 */
object DuplicateDetector {

    enum class Reason { NAME, PHONE, BOTH }

    data class Candidate(
        val partyId: Long,
        val name: String,
        val phone: String?,
        val isCustomer: Boolean,
        val balance: Double
    )

    data class Group(val members: List<Candidate>, val reason: Reason)

    /**
     * Last ten digits only, so a leading 0, a country code, or spaces and
     * dashes typed differently do not hide the same real number from itself
     * ("0301-1234567" and "+92 301 1234567" both become "3011234567").
     * Shorter than that is not a phone number worth grouping on — an owner
     * who left three digits typed by mistake should not see every other
     * short entry lumped in with it.
     *
     * Internal rather than private: the live check while typing in Add Party
     * reuses this exact definition rather than a second, slightly different
     * one — the same reasoning as leaning on [ProductName.normalised] above.
     */
    internal fun normalisedPhone(phone: String?): String? {
        val digits = phone?.filter { it.isDigit() } ?: return null
        if (digits.length < 7) return null
        return digits.takeLast(10)
    }

    /**
     * Canonical, order-independent identity for a group's membership —
     * sorted party ids joined by comma. Two groups made of the same people
     * always produce the same key regardless of which order the members
     * happen to be listed in; a group with a different membership always
     * produces a different one. Used to remember a dismissal (see
     * [com.innovation313.roshankhata.data.DismissedDuplicate]) against the
     * group it was actually made against.
     */
    fun groupKey(members: List<Candidate>): String =
        members.map { it.partyId }.sorted().joinToString(",")

    fun find(all: List<Candidate>, dismissedKeys: Set<String> = emptySet()): List<Group> {
        val nameGroups = all.groupBy { ProductName.normalised(it.name) }
            .filterKeys { it.isNotBlank() }
            .values
            .filter { it.size >= 2 }

        val phoneGroups = all.groupBy { normalisedPhone(it.phone) }
            .filterKeys { it != null }
            .values
            .filter { it.size >= 2 }

        // Keyed by the exact member-id set, so a group found by both signals
        // collapses into one card marked BOTH instead of showing twice.
        val byIds = LinkedHashMap<Set<Long>, Reason>()
        for (members in nameGroups) {
            byIds[members.map { it.partyId }.toSet()] = Reason.NAME
        }
        for (members in phoneGroups) {
            val ids = members.map { it.partyId }.toSet()
            byIds[ids] = if (byIds.containsKey(ids)) Reason.BOTH else Reason.PHONE
        }

        return byIds.map { (ids, reason) ->
            Group(all.filter { it.partyId in ids }, reason)
        }.filter { groupKey(it.members) !in dismissedKeys }
            .sortedByDescending { it.members.size }
    }

    /**
     * A single typed name/phone against the book, for the live warning while
     * still typing in Add Party — the same two signals as [find] above, just
     * checked against one candidate instead of grouping the whole book.
     *
     * Phone wins when both would match (a name coincidence next to a real
     * repeated number is the number's business, not the name's), otherwise
     * whichever signal actually matched. Returns null on a blank name, same
     * as leaving nothing worth warning about.
     */
    fun matchExisting(name: String, phone: String?, existing: List<Candidate>): Candidate? {
        val normalisedName = ProductName.normalised(name)
        if (normalisedName.isBlank()) return null

        val phoneKey = normalisedPhone(phone)
        if (phoneKey != null) {
            existing.firstOrNull { normalisedPhone(it.phone) == phoneKey }?.let { return it }
        }
        return existing.firstOrNull { ProductName.normalised(it.name) == normalisedName }
    }
}
