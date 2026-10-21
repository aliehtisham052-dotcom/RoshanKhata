package com.innovation313.roshankhata.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom

/**
 * Who is at the counter, and what they may do (POS Phase 5; the owner's PIN,
 * counter mode and the wait after wrong PINs since 21 Oct 2026, the critical
 * review).
 *
 * Two kinds of device:
 *
 *  - The owner's own phone or computer (counter mode off, the default): the
 *    owner is at it, and nothing is withheld. A staff member may sign in here
 *    so their name goes on what they do, but signing back in as the owner
 *    takes the owner's PIN once one is set.
 *  - A counter that staff use ([counterMode] on, a setting of this device
 *    alone): it opens [locked] — nobody is anybody until they sign in with
 *    their own PIN — and only the owner's PIN turns counter mode off again.
 *
 * A signed-in manager may do what the owner can at the counter but not the
 * owner's own things (settings, restoring a backup, staff); a cashier rings
 * up, takes money and holds tickets, and for anything that undoes, marks down
 * or rewrites the book — a void, a return, a discount past
 * [CASHIER_DISCOUNT_CAP], a price, an edit or delete, an export, a report — a
 * manager or the owner types their PIN over the cashier's shoulder
 * ([override]), and the audit log says who did.
 *
 * The owner's PIN lives in the book ([BookSettings.OWNER_PIN_HASH], a salted
 * PBKDF2 hash — never the PIN), so it travels with every copy of the book;
 * counter mode and the count of wrong PINs belong to the device ([store]).
 * Five wrong PINs in a row wait 30 seconds, and every five more wait twice as
 * long, up to 15 minutes — kept on the device, so closing the app does not
 * clear it.
 *
 * The signed-in user is kept in memory only: a restart is a sign-out, as a
 * till's is. Every sale, return and void carries the name of whoever was
 * signed in ([userName]).
 */
object Access {

    enum class Permission {
        VOID, RETURN, DISCOUNT_ANY, PRICE_EDIT, EXPORT, STOCK_ADJUST, SETTINGS, BACKUP, STAFF, CREDIT_OVER,
        /** Changing or removing what is already written: entries, parties, cashbook rows, cheques, bills, the bin. */
        EDIT_BOOK,
        /** The shop's figures: sales report, profit, insights, cash flow, zakat, the khata report. */
        REPORTS
    }

    /** Of the subtotal: what a cashier may give off a ticket without asking. */
    const val CASHIER_DISCOUNT_CAP = 0.05

    /** The owner's name in an audit row. */
    const val OWNER = "owner"

    /** What a manager may not do even at the counter: the owner's own things. */
    private val OWNER_ONLY = setOf(Permission.SETTINGS, Permission.BACKUP, Permission.STAFF)

    /**
     * This device's own keeping (the phone's preferences, the computer's
     * app.properties): counter mode and the wrong-PIN count. Set once at
     * start; null in tests, where everything lives in memory.
     */
    interface DeviceStore {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }

    @Volatile
    var store: DeviceStore? = null
        set(value) {
            field = value
            counterMode = value?.get(KEY_COUNTER) == "1"
            current = null
            ownerIn = false
        }

    /** The signed-in staff member, or null. */
    var current: Staff? by mutableStateOf(null)
        private set

    /** The owner signed in with their PIN (on a counter, or after a staff member). */
    var ownerIn: Boolean by mutableStateOf(false)
        private set

    /** This device is a counter that staff use: it opens locked. */
    var counterMode: Boolean by mutableStateOf(false)
        private set

    /** The owner is at the device: signed in with the PIN, or on the owner's own device with nobody else signed in. */
    val isOwner: Boolean get() = current == null && (ownerIn || !counterMode)

    /** A counter with nobody signed in: everything waits for a sign-in. */
    val locked: Boolean get() = counterMode && current == null && !ownerIn

    val role: String? get() = current?.role

    /** The name written on what is done now: the staff member's, or null for the owner (as every entry before). */
    val userName: String? get() = current?.name

    fun signIn(staff: Staff) { current = staff; ownerIn = false }
    fun signInOwner() { current = null; ownerIn = true }

    /** Back to the start: on the owner's device that is the owner; on a counter, locked. */
    fun signOut() { current = null; ownerIn = false }

    /** True for what only the owner may allow: a manager's PIN does not open it. */
    fun ownerOnly(p: Permission): Boolean = p in OWNER_ONLY

    fun can(p: Permission): Boolean = when {
        isOwner -> true
        current?.role == Role.MANAGER && current?.isActive == true -> p !in OWNER_ONLY
        else -> false
    }

    /**
     * Counter mode on or off for this device. The screens ask for the owner's
     * PIN first (and refuse to turn it on while the book has none). Turning it
     * on leaves the owner signed in — they just typed the PIN; turning it off
     * makes this the owner's device again.
     */
    fun setCounterMode(on: Boolean) {
        store?.put(KEY_COUNTER, if (on) "1" else null)
        counterMode = on
        current = null
        ownerIn = on
    }

    /** Whether this discount on this subtotal needs a manager's PIN from whoever is signed in. */
    fun discountNeedsOverride(discount: Double, subtotal: Double): Boolean =
        !can(Permission.DISCOUNT_ANY) && subtotal > 0.0 && discount > subtotal * CASHIER_DISCOUNT_CAP + 0.005

    // ---- The owner's PIN (in the book) ----

    /** The owner's PIN as the book keeps it: a hash and its salt. */
    data class OwnerPin(val hash: String, val salt: String) {
        fun matches(pin: String): Boolean = runCatching { PinHash.matches(pin, salt, hash) }.getOrDefault(false)
    }

    /** The book's owner PIN, or null when none has been set. */
    suspend fun ownerPin(dao: KhataDao): OwnerPin? {
        val hash = dao.settingValue(BookSettings.OWNER_PIN_HASH) ?: return null
        val salt = dao.settingValue(BookSettings.OWNER_PIN_SALT) ?: return null
        return OwnerPin(hash, salt)
    }

    /**
     * Sets (or replaces) the owner's PIN, and returns a new recovery code —
     * shown to the owner once, kept only as its hash — that can set a new PIN
     * if this one is forgotten. The screens ask for the old PIN, or the
     * recovery code, before replacing one. One audit row.
     */
    suspend fun setOwnerPin(dao: KhataDao, pin: String): String {
        require(PinHash.valid(pin)) { "a PIN is 4 to 8 digits" }
        val salt = PinHash.salt()
        val code = RecoveryCode.next()
        val codeSalt = PinHash.salt()
        dao.saveOwnerPin(PinHash.hash(pin, salt), salt, PinHash.hash(RecoveryCode.normal(code), codeSalt), codeSalt, userName)
        return code
    }

    /** True when [code] is the book's recovery code (spaces, dashes and case do not matter). */
    suspend fun recoveryMatches(dao: KhataDao, code: String): Boolean {
        val hash = dao.settingValue(BookSettings.OWNER_RECOVERY_HASH) ?: return false
        val salt = dao.settingValue(BookSettings.OWNER_RECOVERY_SALT) ?: return false
        return runCatching { PinHash.matches(RecoveryCode.normal(code), salt, hash) }.getOrDefault(false)
    }

    /**
     * A manager's or the owner's PIN typed to allow one thing. [ownerPin]
     * checks the owner's PIN (the book's, [OwnerPin.matches]); null when the
     * book has none, and then the owner's row is not offered. Returns who
     * allowed it, for the audit row, or null.
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

    // ---- Wrong PINs ----

    /** Wrong PINs in a row before a wait. */
    const val MISSES_PER_WAIT = 5
    private const val FIRST_WAIT_MS = 30_000L
    private const val LONGEST_WAIT_MS = 15 * 60_000L

    @Volatile private var memMisses = 0
    @Volatile private var memWaitUntil = 0L

    private fun misses(): Int = store?.get(KEY_MISSES)?.toIntOrNull() ?: memMisses
    private fun waitUntil(): Long = store?.get(KEY_WAIT_UNTIL)?.toLongOrNull() ?: memWaitUntil

    /** Milliseconds still to wait before another PIN may be tried; 0 when one may. */
    fun waitMillis(now: Long = System.currentTimeMillis()): Long = (waitUntil() - now).coerceAtLeast(0L)

    /** The same, in whole seconds rounded up, for the screen. */
    fun waitSeconds(now: Long = System.currentTimeMillis()): Int = ((waitMillis(now) + 999) / 1000).toInt()

    /**
     * A PIN was tried. A right one clears the count; a wrong one adds to it,
     * and every [MISSES_PER_WAIT]th starts a wait: 30 s, then 1 min, 2, 4 …
     * up to 15 minutes. Returns true when this miss started a wait (the
     * caller writes the audit row).
     */
    @Synchronized
    fun pinTried(right: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (right) {
            memMisses = 0; memWaitUntil = 0L
            store?.put(KEY_MISSES, null); store?.put(KEY_WAIT_UNTIL, null)
            return false
        }
        val n = misses() + 1
        memMisses = n
        store?.put(KEY_MISSES, n.toString())
        if (n % MISSES_PER_WAIT != 0) return false
        val doublings = (n / MISSES_PER_WAIT - 1).coerceAtMost(10)
        val wait = (FIRST_WAIT_MS shl doublings).coerceAtMost(LONGEST_WAIT_MS)
        memWaitUntil = now + wait
        store?.put(KEY_WAIT_UNTIL, (now + wait).toString())
        return true
    }

    /**
     * Checks [pin] with the wait in force: null while waiting (nothing is
     * compared), else whether [check] accepted it — counted either way.
     * [onWait] is told when this miss started a wait, for the audit row.
     */
    fun tryPin(pin: String, now: Long = System.currentTimeMillis(), onWait: () -> Unit = {}, check: (String) -> Boolean): Boolean? {
        if (waitMillis(now) > 0L) return null
        val right = check(pin)
        if (pinTried(right, now)) onWait()
        return right
    }

    /** For tests: forget the device and every count. */
    fun resetForTest() {
        store = null
        memMisses = 0; memWaitUntil = 0L
        counterMode = false
        current = null; ownerIn = false
    }

    private const val KEY_COUNTER = "access_counter_mode"
    private const val KEY_MISSES = "access_pin_misses"
    private const val KEY_WAIT_UNTIL = "access_pin_wait_until"
}

/**
 * The owner's recovery code: eight letters and figures, none that look alike
 * (no 0/O, 1/I/L), shown as two groups of four. Typed back with or without
 * the dash, in either case.
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun next(random: SecureRandom = SecureRandom()): String {
        val s = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return s.substring(0, 4) + "-" + s.substring(4)
    }

    /** Upper case, letters and figures only: what the hash is taken of. */
    fun normal(code: String): String = code.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
}
