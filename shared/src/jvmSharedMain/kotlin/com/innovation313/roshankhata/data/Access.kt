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
 *    so their name goes on what they do — for names only: signing out is the
 *    owner again, as the device is the owner's (22 Oct 2026, the review: this
 *    said it took the owner's PIN, and it never did).
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
 * counter mode and the count of wrong PINs belong to the device ([store]),
 * and so does a copy of the owner's last right PIN's hash, which a book on a
 * counter without an owner PIN of its own takes ([rememberOwner], [ownerPin]).
 * Five wrong PINs in a row wait 30 seconds, and every five more wait twice as
 * long, up to 15 minutes — kept on the device, so closing the app does not
 * clear it, and never longer than 15 minutes whatever the clock said.
 *
 * On a counter, an owner's or a manager's session ends after
 * [ELEVATED_IDLE_MS] with nothing done ([touch], [expireIfIdle]); a cashier's
 * stays, as the till is theirs all day.
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

    fun signIn(staff: Staff) { current = staff; ownerIn = false; lastTouch = System.currentTimeMillis() }
    fun signInOwner() { current = null; ownerIn = true; lastTouch = System.currentTimeMillis() }

    /**
     * How long an owner's or a manager's session on a counter lasts with
     * nothing done (22 Oct 2026, the review: one sign-in to change a price
     * left the cashier with owner rights — staff, restore, export — all day).
     */
    const val ELEVATED_IDLE_MS = 5 * 60_000L

    @Volatile private var lastTouch: Long = System.currentTimeMillis()

    /** Something was done at the device (a key, a click, a tap). A session idle too long ends first. */
    fun touch(now: Long = System.currentTimeMillis()) {
        expireIfIdle(now)
        lastTouch = now
    }

    /** Ends an owner's or a manager's session on a counter idle for [ELEVATED_IDLE_MS]; true when it did. */
    fun expireIfIdle(now: Long = System.currentTimeMillis()): Boolean {
        if (!counterMode) return false
        val elevated = ownerIn || current?.role == Role.MANAGER
        if (!elevated || now - lastTouch < ELEVATED_IDLE_MS) return false
        signOut()
        return true
    }

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
     * on locks the counter at once (22 Oct 2026, the review: it left the owner
     * signed in, and the device was handed over with the owner's rights);
     * turning it off makes this the owner's device again.
     */
    fun switchCounterMode(on: Boolean) {
        store?.put(KEY_COUNTER, if (on) "1" else null)
        counterMode = on
        current = null
        ownerIn = false
    }

    /** This device's own id, made once and kept in the device's settings; "" in tests without a store. */
    fun deviceId(): String {
        val s = store ?: return ""
        s.get(KEY_DEVICE)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = Uid.next()
        s.put(KEY_DEVICE, id)
        return id
    }

    private suspend fun ownerDevices(dao: KhataDao): Set<String> =
        dao.settingValue(BookSettings.OWNER_DEVICES).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * The switch written into the book too ([BookSettings.COUNTER_USED],
     * [BookSettings.OWNER_DEVICES]): on, this device stops being one of the
     * owner's; off, it becomes one. Called with [switchCounterMode] after the
     * owner's PIN.
     */
    suspend fun recordCounterMode(dao: KhataDao, on: Boolean) {
        val me = deviceId()
        if (me.isEmpty()) return
        if (on) dao.setSetting(BookSettings.COUNTER_USED, "1")
        val owners = ownerDevices(dao)
        val next = if (on) owners - me else owners + me
        if (next != owners) dao.setSetting(BookSettings.OWNER_DEVICES, next.sorted().joinToString(",").ifEmpty { null })
    }

    /**
     * The book's word on this device (the computer calls it when a book
     * opens): a book that has had a counter makes this device a counter
     * unless the owner has made it theirs — whatever the device's own
     * settings file says. A counter this device already is, is told to the
     * book. Returns true when this turned counter mode on.
     */
    suspend fun applyBook(dao: KhataDao): Boolean {
        val me = deviceId()
        if (me.isEmpty()) return false
        val used = BookSettings.isOn(dao.settingValue(BookSettings.COUNTER_USED))
        if (!used) {
            if (counterMode) recordCounterMode(dao, true)
            return false
        }
        if (counterMode || me in ownerDevices(dao)) return false
        store?.put(KEY_COUNTER, "1")
        counterMode = true
        current = null
        ownerIn = false
        return true
    }

    /**
     * Whether this discount needs a manager's PIN from whoever is signed in.
     * [gross] is the goods before any discount (21 Oct 2026): measured on
     * what was left after the lines' own discounts, a 100% line discount left
     * nothing to measure on and went through with no PIN.
     */
    fun discountNeedsOverride(discount: Double, gross: Double): Boolean =
        !can(Permission.DISCOUNT_ANY) && discount > 0.005 && discount > gross.coerceAtLeast(0.0) * CASHIER_DISCOUNT_CAP + 0.005

    // ---- The owner's PIN (in the book) ----

    /** The owner's PIN as the book keeps it: a hash and its salt. */
    data class OwnerPin(val hash: String, val salt: String) {
        fun matches(pin: String): Boolean = runCatching { PinHash.matches(pin, salt, hash) }.getOrDefault(false)
    }

    /**
     * The book's owner PIN, or null when none has been set. On a counter, a
     * book without one first takes the one this device keeps
     * ([rememberOwner]), so a counter is never locked against its owner;
     * elsewhere a book without one stays without.
     */
    suspend fun ownerPin(dao: KhataDao): OwnerPin? {
        bookOwnerPin(dao)?.let { return it }
        if (!counterMode) return null
        val s = store ?: return null
        val kept = OWNER_KEYS.map { (_, k) -> s.get(k) }
        if (kept.any { it.isNullOrEmpty() }) return null
        // A book that cannot be written (a helper's read-only copy) stays as it is.
        runCatching { dao.adoptOwnerPin(kept[0]!!, kept[1]!!, kept[2]!!, kept[3]!!) }
        return bookOwnerPin(dao)
    }

    private suspend fun bookOwnerPin(dao: KhataDao): OwnerPin? {
        val hash = dao.settingValue(BookSettings.OWNER_PIN_HASH) ?: return null
        val salt = dao.settingValue(BookSettings.OWNER_PIN_SALT) ?: return null
        return OwnerPin(hash, salt)
    }

    // The owner's PIN this device keeps (22 Oct 2026, the review). A counter
    // is a device the owner made one with the owner's PIN; a book opened on it
    // without an owner PIN of its own — a shop added on the counter, a phone's
    // book paired in, a shop never set up for it — left the owner no way back
    // in: the counter opened locked, its owner row asked for a PIN the book
    // did not have, and the device stayed so after every restart. So each time
    // the owner's PIN is typed right on a device (or set on it), the device
    // keeps the book's four owner keys — hashes and salts, never a PIN — and on
    // a counter a book without them takes them, with an audit row.
    private val OWNER_KEYS = listOf(
        BookSettings.OWNER_PIN_HASH to KEY_OWNER_HASH,
        BookSettings.OWNER_PIN_SALT to KEY_OWNER_SALT,
        BookSettings.OWNER_RECOVERY_HASH to KEY_OWNER_RECOVERY_HASH,
        BookSettings.OWNER_RECOVERY_SALT to KEY_OWNER_RECOVERY_SALT
    )

    /**
     * The owner's PIN was just typed right on this device, or set on it: the
     * device keeps this book's owner keys for a book on this counter that has
     * none ([ownerPin]). Nothing is kept from a book without all four.
     */
    suspend fun rememberOwner(dao: KhataDao) {
        val s = store ?: return
        val values = OWNER_KEYS.map { (book, _) -> dao.settingValue(book) }
        if (values.any { it.isNullOrEmpty() }) return
        OWNER_KEYS.forEachIndexed { i, (_, device) -> s.put(device, values[i]) }
    }

    /** True when this device keeps an owner's PIN ([rememberOwner]). */
    fun remembersOwner(): Boolean = store?.let { s -> OWNER_KEYS.all { (_, k) -> !s.get(k).isNullOrEmpty() } } == true

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
        rememberOwner(dao)
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

    /**
     * Whose PIN is being tried (21 Oct 2026, the critical review): each
     * account keeps its own count and its own wait. With one count for all,
     * a cashier's own right PIN cleared the misses made guessing the owner's,
     * so the owner's PIN could be guessed without end, four at a time.
     */
    fun account(staff: Staff?): String = staff?.let { "staff:" + it.id } ?: OWNER

    /** The account of a PIN typed where it is not known whose it is (the old single count). */
    const val ANY = "any"

    private val memMisses = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val memWaitUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun key(base: String, account: String) = if (account == ANY) base else "$base:$account"
    private fun misses(account: String): Int = store?.get(key(KEY_MISSES, account))?.toIntOrNull() ?: memMisses[account] ?: 0
    private fun waitUntil(account: String): Long = store?.get(key(KEY_WAIT_UNTIL, account))?.toLongOrNull() ?: memWaitUntil[account] ?: 0L

    /**
     * Milliseconds still to wait before another PIN of [account] may be tried;
     * 0 when one may. Never more than the longest wait (22 Oct 2026, the
     * review: a wait set while the clock read years ahead lasted until then,
     * and blocked the recovery code too).
     */
    fun waitMillis(now: Long = System.currentTimeMillis(), account: String = ANY): Long =
        (maxOf(waitUntil(account), waitUntil(ANY)) - now).coerceIn(0L, LONGEST_WAIT_MS)

    /** The same, in whole seconds rounded up, for the screen. */
    fun waitSeconds(now: Long = System.currentTimeMillis(), account: String = ANY): Int = ((waitMillis(now, account) + 999) / 1000).toInt()

    /**
     * A PIN was tried. A right one clears the count; a wrong one adds to it,
     * and every [MISSES_PER_WAIT]th starts a wait: 30 s, then 1 min, 2, 4 …
     * up to 15 minutes. Returns true when this miss started a wait (the
     * caller writes the audit row).
     */
    @Synchronized
    fun pinTried(right: Boolean, now: Long = System.currentTimeMillis(), account: String = ANY): Boolean {
        if (right) {
            // Only this account's count is cleared: another's right PIN says nothing about this one.
            memMisses.remove(account); memWaitUntil.remove(account)
            store?.put(key(KEY_MISSES, account), null); store?.put(key(KEY_WAIT_UNTIL, account), null)
            return false
        }
        val n = misses(account) + 1
        memMisses[account] = n
        store?.put(key(KEY_MISSES, account), n.toString())
        if (n % MISSES_PER_WAIT != 0) return false
        val doublings = (n / MISSES_PER_WAIT - 1).coerceAtMost(10)
        val wait = (FIRST_WAIT_MS shl doublings).coerceAtMost(LONGEST_WAIT_MS)
        memWaitUntil[account] = now + wait
        store?.put(key(KEY_WAIT_UNTIL, account), (now + wait).toString())
        return true
    }

    /**
     * Checks [pin] with the wait in force: null while waiting (nothing is
     * compared), else whether [check] accepted it — counted either way.
     * [onWait] is told when this miss started a wait, for the audit row.
     */
    fun tryPin(pin: String, now: Long = System.currentTimeMillis(), onWait: () -> Unit = {}, account: String = ANY, check: (String) -> Boolean): Boolean? {
        if (waitMillis(now, account) > 0L) return null
        val right = check(pin)
        if (pinTried(right, now, account)) onWait()
        return right
    }

    /** For tests: forget the device and every count. */
    fun resetForTest() {
        store = null
        memMisses.clear(); memWaitUntil.clear()
        counterMode = false
        current = null; ownerIn = false
    }

    private const val KEY_COUNTER = "access_counter_mode"
    private const val KEY_DEVICE = "access_device_id"
    private const val KEY_MISSES = "access_pin_misses"
    private const val KEY_WAIT_UNTIL = "access_pin_wait_until"
    private const val KEY_OWNER_HASH = "access_owner_hash"
    private const val KEY_OWNER_SALT = "access_owner_salt"
    private const val KEY_OWNER_RECOVERY_HASH = "access_owner_recovery_hash"
    private const val KEY_OWNER_RECOVERY_SALT = "access_owner_recovery_salt"
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
