package com.innovation313.roshankhata.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Who is at the counter, and what they may do (POS Phase 5).
 *
 * Until a staff member signs in, the owner is at the counter — the book is
 * theirs, and nothing is withheld from them. A signed-in manager may do what
 * the owner can at the counter; a cashier rings up, takes money and holds
 * tickets, and for anything that undoes or marks down a sale — a void, a
 * return, a discount past [CASHIER_DISCOUNT_CAP], a price change, an export —
 * a manager or the owner types their PIN over the cashier's shoulder
 * ([override]), and the audit log says who did.
 *
 * The signed-in user is kept in memory only: a restart is a sign-out, as a
 * till's is. Every sale, return and void carries the name of whoever was
 * signed in ([userName]).
 */
object Access {

    enum class Permission { VOID, RETURN, DISCOUNT_ANY, PRICE_EDIT, EXPORT, STOCK_ADJUST, SETTINGS, BACKUP, STAFF }

    /** Of the subtotal: what a cashier may give off a ticket without asking. */
    const val CASHIER_DISCOUNT_CAP = 0.05

    /** The signed-in staff member, or null for the owner. */
    var current: Staff? by mutableStateOf(null)
        private set

    val role: String? get() = current?.role

    /** The name written on what is done now: the staff member's, or null for the owner (as every entry before). */
    val userName: String? get() = current?.name

    fun signIn(staff: Staff) { current = staff }
    fun signOut() { current = null }

    fun can(p: Permission): Boolean = when (role) {
        null -> true
        Role.MANAGER -> p !in setOf(Permission.SETTINGS, Permission.BACKUP, Permission.STAFF)
        Role.CASHIER -> false
        else -> false
    }

    /** Whether this discount on this subtotal needs a manager's PIN from whoever is signed in. */
    fun discountNeedsOverride(discount: Double, subtotal: Double): Boolean =
        !can(Permission.DISCOUNT_ANY) && subtotal > 0.0 && discount > subtotal * CASHIER_DISCOUNT_CAP + 0.005

    /**
     * A manager's or the owner's PIN typed to allow one thing. [ownerPin]
     * checks the owner's own PIN where the platform keeps one (the
     * computer's App Lock); null when there is none, and then the owner's
     * row is not offered. Returns who allowed it, for the audit row, or null.
     */
    fun override(candidates: List<Staff>, staffId: Long?, pin: String, ownerPin: ((String) -> Boolean)?): String? {
        if (staffId == null) return if (ownerPin != null && ownerPin(pin)) OWNER else null
        val s = candidates.firstOrNull { it.id == staffId && it.role == Role.MANAGER && it.isActive } ?: return null
        val hash = s.pinHash ?: return null
        val salt = s.pinSalt ?: return null
        return if (PinHash.matches(pin, salt, hash)) s.name else null
    }

    /** True when [pin] is this staff member's. */
    fun pinMatches(s: Staff, pin: String): Boolean {
        val hash = s.pinHash ?: return false
        val salt = s.pinSalt ?: return false
        return PinHash.matches(pin, salt, hash)
    }

    /** The owner's name in an audit row. */
    const val OWNER = "owner"
}
