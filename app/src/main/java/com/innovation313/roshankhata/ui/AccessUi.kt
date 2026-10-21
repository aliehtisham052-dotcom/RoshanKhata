package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PinHash
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is at the counter, on the phone (POS Phase 5; the owner's PIN, counter
 * mode and the screen gates since 21 Oct 2026 — the critical review).
 *
 *  - [signIn]: the sheet of who may sign in. The owner's row takes the
 *    owner's PIN once the book has one.
 *  - [allowed]: a manager's or the owner's PIN over a cashier's shoulder.
 *  - [gateScreen]: a screen a cashier may not open alone asks first.
 *  - [lockGate]: a counter phone with nobody signed in shows nothing else.
 *  - [ownerPin] / [counterMode]: the owner's own controls (Staff screen).
 *
 * Every PIN goes through [Access.tryPin]: five wrong ones in a row and the
 * phone waits, longer each time, and the log says so.
 */
object AccessUi {

    private const val PREFS = "access_device"

    /** Access's keeping on this phone (counter mode, wrong PINs). Called once, before any screen. */
    fun install(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Access.store = object : Access.DeviceStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String?) {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
    }

    private fun dao(activity: Activity): KhataDao = KhataDatabase.get(activity).khataDao()

    private fun log(dao: KhataDao, action: String, user: String?, detail: String? = null) {
        AppScope.launch { withContext(Dispatchers.IO) { dao.log(action, user, detail = detail) } }
    }

    private fun toast(activity: Activity, text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    /**
     * [pin] checked off the main thread (PBKDF2 takes a moment), with the
     * wait after wrong PINs in force. [ok] on the main thread when it is
     * right; [failed] when it is wrong or the phone is waiting.
     */
    private fun check(activity: Activity, dao: KhataDao, pin: String, matches: (String) -> Boolean, failed: () -> Unit = {}, ok: () -> Unit) {
        AppScope.launch {
            val result = withContext(Dispatchers.Default) {
                Access.tryPin(pin, onWait = { log(dao, AuditAction.PIN_WAIT, Access.userName) }, check = matches)
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                when (result) {
                    null -> { toast(activity, Digits.string(activity.resources, R.string.lock_pin_wait, Access.waitSeconds())); failed() }
                    false -> { toast(activity, activity.getString(R.string.access_wrong_pin)); failed() }
                    true -> ok()
                }
            }
        }
    }

    /**
     * Who is at the counter. [gate] is the counter's locked state: the sheet
     * cannot be dismissed, a wrong PIN brings it back, and its button closes
     * the app instead of cancelling.
     */
    fun signIn(activity: Activity, dao: KhataDao, gate: Boolean = false, done: () -> Unit) {
        AppScope.launch {
            val staff = withContext(Dispatchers.IO) { dao.loginStaffOnce() }
            val owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val again = { if (gate && Access.locked) signIn(activity, dao, true, done) }
                val labels = (listOf(activity.getString(R.string.access_owner)) + staff.map { it.name + " · " + roleWord(activity, it.role) }).toTypedArray()
                val b = MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.access_who)
                    .setItems(labels) { _, i ->
                        if (i == 0) {
                            if (owner == null) {
                                // No owner PIN in this book: only the owner's own device lets the owner in without one,
                                // and a counter cannot exist without it (counter mode is refused until it is set).
                                if (Access.counterMode) { again(); return@setItems }
                                Access.signOut(); done(); return@setItems
                            }
                            askPin(activity, activity.getString(R.string.access_owner), onCancel = again,
                                forgot = { recover(activity, dao, then = { Access.signInOwner(); done() }, cancelled = again) }) { pin ->
                                check(activity, dao, pin, { owner.matches(it) }, failed = again) {
                                    Access.signInOwner()
                                    log(dao, AuditAction.SIGN_IN, Access.OWNER)
                                    done()
                                }
                            }
                            return@setItems
                        }
                        val s = staff[i - 1]
                        askPin(activity, s.name, onCancel = again) { pin ->
                            check(activity, dao, pin, { Access.pinMatches(s, it) }, failed = again) {
                                Access.signIn(s)
                                log(dao, AuditAction.SIGN_IN, s.name)
                                done()
                            }
                        }
                    }
                if (gate) {
                    b.setCancelable(false)
                    b.setNegativeButton(R.string.access_close_app) { _, _ -> activity.finishAffinity() }
                } else {
                    b.setNegativeButton(R.string.cancel, null)
                    if (Access.current != null || Access.ownerIn) {
                        b.setNeutralButton(R.string.access_sign_out) { _, _ ->
                            log(dao, AuditAction.SIGN_OUT, Access.userName ?: Access.OWNER)
                            Access.signOut()
                            done()
                            if (Access.locked) lockGate(activity)
                        }
                    }
                }
                b.show()
            }
        }
    }

    /**
     * Runs [then] at once when whoever is signed in may; otherwise after a
     * manager's or the owner's PIN. [cancelled] when nobody allows it (or
     * nobody could).
     */
    fun allowed(activity: Activity, dao: KhataDao, permission: Access.Permission, what: String, cancelled: () -> Unit = {}, then: (by: String?) -> Unit) {
        if (Access.can(permission)) { then(null); return }
        AppScope.launch {
            // The owner's own things (settings, backups, staff) are allowed by the owner only.
            val ownerOnly = Access.ownerOnly(permission)
            val managers: List<Staff> = if (ownerOnly) emptyList()
                else withContext(Dispatchers.IO) { dao.loginStaffOnce().filter { it.role == Role.MANAGER } }
            val owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (managers.isEmpty() && owner == null) { toast(activity, activity.getString(R.string.access_denied)); cancelled(); return@runOnUiThread }
                val names = (if (owner != null) listOf(activity.getString(R.string.access_owner)) else emptyList()) + managers.map { it.name }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.access_override_title)
                    .setMessage(activity.getString(R.string.access_override_help) + "\n" + what)
                    .setItems(names.toTypedArray()) { _, i ->
                        val isOwner = owner != null && i == 0
                        val m = if (isOwner) null else managers[i - (if (owner != null) 1 else 0)]
                        askPin(activity, names[i], onCancel = cancelled) { pin ->
                            check(activity, dao, pin, { typed -> Access.override(managers, m?.id, typed, owner?.let { o -> { p: String -> o.matches(p) } }) != null }, failed = cancelled) {
                                val by = if (isOwner) Access.OWNER else m!!.name
                                log(dao, AuditAction.MANAGER_OVERRIDE, by, what)
                                then(by)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> cancelled() }
                    .setOnCancelListener { cancelled() }
                    .show()
            }
        }
    }

    /** One allowance a manager or the owner just gave, taken by the call it was given for. */
    @Volatile private var granted: Access.Permission? = null

    /**
     * For the first line of a function that changes the book: true when the
     * signed-in user may go on now. Otherwise false — the function returns —
     * and a manager's or the owner's PIN is asked for; once given, [retry]
     * calls the function again and this time it goes through.
     *
     *     if (!AccessUi.may(this, dao) { confirmDelete(entry) }) return
     */
    fun may(activity: Activity, dao: KhataDao, p: Access.Permission = Access.Permission.EDIT_BOOK, retry: () -> Unit): Boolean {
        if (Access.can(p)) return true
        if (granted == p) { granted = null; return true }
        allowed(activity, dao, p, what(activity, p)) { granted = p; retry() }
        return false
    }

    /** [block] now when the signed-in user may change the book; otherwise after a manager's or the owner's PIN. */
    fun edit(activity: Activity, dao: KhataDao, block: () -> Unit) =
        allowed(activity, dao, Access.Permission.EDIT_BOOK, what(activity, Access.Permission.EDIT_BOOK)) { block() }

    /** Screens a cashier may not open alone, by simple name (as ViewerMode matches them), and what each needs. */
    private val SCREENS = mapOf(
        "BackupActivity" to Access.Permission.BACKUP,
        "SnapshotsActivity" to Access.Permission.BACKUP,
        "BusinessSwitchActivity" to Access.Permission.SETTINGS,
        "BusinessSettingsActivity" to Access.Permission.SETTINGS,
        "InvoiceSettingsActivity" to Access.Permission.SETTINGS,
        "StaffActivity" to Access.Permission.STAFF,
        "RecycleBinActivity" to Access.Permission.EDIT_BOOK,
        "DuplicateCustomersActivity" to Access.Permission.EDIT_BOOK,
        "SalesReportActivity" to Access.Permission.REPORTS,
        "InsightsActivity" to Access.Permission.REPORTS,
        "CashFlowActivity" to Access.Permission.REPORTS,
        "ZakatActivity" to Access.Permission.REPORTS,
        "ReportActivity" to Access.Permission.REPORTS,
        "SupplierRatesActivity" to Access.Permission.REPORTS,
        "AuditActivity" to Access.Permission.REPORTS
    )

    /** What [screen] needs, or null when anyone signed in may open it. */
    fun needs(screen: Class<*>): Access.Permission? = SCREENS[screen.simpleName]

    /**
     * Called by every screen once it is built (BaseActivity): a screen the
     * signed-in user may not open stays hidden until a manager or the owner
     * allows it, and closes if nobody does.
     */
    fun gateScreen(activity: Activity) {
        val p = needs(activity.javaClass) ?: return
        if (Access.locked || Access.can(p)) return
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        content.visibility = View.INVISIBLE
        allowed(activity, dao(activity), p, what(activity, p), cancelled = { activity.finish() }) {
            content.visibility = View.VISIBLE
        }
    }

    /** What a permission is, in words, for the PIN sheet. */
    fun what(activity: Activity, p: Access.Permission): String = activity.getString(
        when (p) {
            Access.Permission.BACKUP -> R.string.access_need_backup
            Access.Permission.SETTINGS -> R.string.access_need_settings
            Access.Permission.STAFF -> R.string.access_need_staff
            Access.Permission.REPORTS -> R.string.access_need_reports
            else -> R.string.access_need_edit
        }
    )

    /** The screen the counter's sign-in sheet is up on, if any (a sheet goes with its screen). */
    private var gateOn: java.lang.ref.WeakReference<Activity>? = null

    /**
     * A counter with nobody signed in (Access.locked): the screen is hidden
     * behind the sign-in sheet until someone signs in, and the sheet cannot
     * be dismissed. Called from every screen's onResume.
     */
    fun lockGate(activity: Activity) {
        if (!Access.locked) return
        if (gateOn?.get() === activity && !activity.isFinishing) return
        val content = activity.findViewById<View>(android.R.id.content)
        content?.visibility = View.INVISIBLE
        gateOn = java.lang.ref.WeakReference(activity)
        signIn(activity, dao(activity), gate = true) {
            gateOn = null
            content?.visibility = View.VISIBLE
            // The screen that was opened may itself need more than the person who signed in has.
            gateScreen(activity)
        }
    }

    /** A screen going away takes its sheet with it; the next screen shows its own. */
    fun screenGone(activity: Activity) { if (gateOn?.get() === activity) gateOn = null }

    private fun pinBox(activity: Activity, hint: Int): EditText = EditText(activity).apply {
        setHint(hint)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun box(activity: Activity, vararg views: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        views.forEach { addView(it) }
    }

    private fun askPin(activity: Activity, title: String, onCancel: () -> Unit = {}, forgot: (() -> Unit)? = null, onPin: (String) -> Unit) {
        val input = pinBox(activity, R.string.staff_pin)
        val b = MaterialAlertDialogBuilder(activity).setTitle(title).setView(box(activity, input))
            .setPositiveButton(R.string.access_sign_in) { _, _ -> onPin(input.text.toString()) }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
        if (forgot != null) b.setNeutralButton(R.string.access_forgot_pin) { _, _ -> forgot() }
        b.show().also { KeyboardFollow.dialog(it) }
    }

    /** The owner's recovery code typed instead of a forgotten PIN; [then] after a new PIN is set. */
    private fun recover(activity: Activity, dao: KhataDao, then: () -> Unit, cancelled: () -> Unit = {}) {
        val input = EditText(activity).apply {
            setHint(R.string.access_recovery_code)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.access_forgot_pin)
            .setMessage(R.string.access_recovery_ask)
            .setView(box(activity, input))
            .setPositiveButton(R.string.ok) { _, _ ->
                val code = input.text.toString()
                AppScope.launch {
                    val waitFirst = Access.waitMillis() > 0L
                    val right = !waitFirst && withContext(Dispatchers.Default) { Access.recoveryMatches(dao, code) }
                    if (!waitFirst && Access.pinTried(right)) log(dao, AuditAction.PIN_WAIT, Access.userName)
                    activity.runOnUiThread {
                        when {
                            waitFirst -> { toast(activity, Digits.string(activity.resources, R.string.lock_pin_wait, Access.waitSeconds())); cancelled() }
                            !right -> { toast(activity, activity.getString(R.string.access_recovery_wrong)); cancelled() }
                            else -> newOwnerPin(activity, dao, cancelled = cancelled) { then() }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> cancelled() }
            .setOnCancelListener { cancelled() }
            .show().also { KeyboardFollow.dialog(it) }
    }

    /** A new owner PIN, typed twice; the recovery code that comes with it is shown once. */
    private fun newOwnerPin(activity: Activity, dao: KhataDao, cancelled: () -> Unit = {}, done: () -> Unit) {
        val first = pinBox(activity, R.string.lock_pin_new)
        val second = pinBox(activity, R.string.lock_pin_again)
        val help = TextView(activity).apply { setText(R.string.access_owner_pin_help); textSize = 13f }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.access_owner_pin)
            .setView(box(activity, help, first, second))
            .setPositiveButton(R.string.save) { _, _ ->
                val a = first.text.toString()
                val b = second.text.toString()
                when {
                    !PinHash.valid(a) -> { toast(activity, activity.getString(R.string.staff_pin_invalid)); cancelled() }
                    a != b -> { toast(activity, activity.getString(R.string.lock_pin_mismatch)); cancelled() }
                    else -> AppScope.launch {
                        val code = withContext(Dispatchers.IO) { Access.setOwnerPin(dao, a) }
                        activity.runOnUiThread { showRecoveryCode(activity, code, done) }
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> cancelled() }
            .setOnCancelListener { cancelled() }
            .show().also { KeyboardFollow.dialog(it) }
    }

    private fun showRecoveryCode(activity: Activity, code: String, done: () -> Unit) {
        val shown = TextView(activity).apply {
            text = code
            textSize = 26f
            setTextIsSelectable(true)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_LTR
        }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.access_recovery_title)
            .setMessage(R.string.access_recovery_help)
            .setView(box(activity, shown))
            .setCancelable(false)
            .setPositiveButton(R.string.access_recovery_written) { _, _ -> done() }
            .show()
    }

    /**
     * The owner's PIN, from the Staff screen: set when the book has none,
     * otherwise changed after the current PIN (or the recovery code). [done]
     * gets true when a PIN is now set.
     */
    fun ownerPin(activity: Activity, done: (Boolean) -> Unit = {}) {
        val dao = dao(activity)
        AppScope.launch {
            val owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (owner == null) { newOwnerPin(activity, dao, cancelled = { done(false) }) { done(true) }; return@runOnUiThread }
                askPin(activity, activity.getString(R.string.access_owner_pin_current), onCancel = { done(true) },
                    forgot = { recover(activity, dao, then = { done(true) }, cancelled = { done(true) }) }) { pin ->
                    check(activity, dao, pin, { owner.matches(it) }, failed = { done(true) }) {
                        newOwnerPin(activity, dao, cancelled = { done(true) }) { done(true) }
                    }
                }
            }
        }
    }

    /** True (on the main thread) when the book has an owner PIN; otherwise asks for one first. */
    fun requireOwnerPin(activity: Activity, then: () -> Unit) {
        val dao = dao(activity)
        AppScope.launch {
            val owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (owner != null) then()
                else MaterialAlertDialogBuilder(activity).setTitle(R.string.access_owner_pin)
                    .setMessage(R.string.access_owner_pin_needed)
                    .setPositiveButton(R.string.access_owner_pin_set) { _, _ -> newOwnerPin(activity, dao) { then() } }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    /**
     * Counter mode on this phone, from the Staff screen. On: the book must
     * have an owner PIN (set it here if not), and the owner confirms with it.
     * Off: only the owner's PIN. One audit row either way.
     */
    fun counterMode(activity: Activity, on: Boolean, done: () -> Unit) {
        val dao = dao(activity)
        val flip = { pin: String, owner: Access.OwnerPin ->
            check(activity, dao, pin, { owner.matches(it) }) {
                Access.switchCounterMode(on)
                log(dao, if (on) AuditAction.COUNTER_ON else AuditAction.COUNTER_OFF, Access.OWNER)
                toast(activity, activity.getString(if (on) R.string.access_counter_now_on else R.string.access_counter_now_off))
                done()
            }
        }
        requireOwnerPin(activity) {
            AppScope.launch {
                val owner = withContext(Dispatchers.IO) { Access.ownerPin(dao) } ?: return@launch
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    MaterialAlertDialogBuilder(activity).setTitle(R.string.access_counter_mode)
                        .setMessage(if (on) R.string.access_counter_mode_help else R.string.access_counter_off_help)
                        .setPositiveButton(if (on) R.string.access_counter_turn_on else R.string.access_counter_turn_off) { _, _ ->
                            askPin(activity, activity.getString(R.string.access_owner_pin)) { pin -> flip(pin, owner) }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    fun roleWord(activity: Activity, role: String?): String = activity.getString(
        when (role) {
            Role.MANAGER -> R.string.staff_role_manager
            Role.CASHIER -> R.string.staff_role_cashier
            else -> R.string.staff_role_none
        }
    )

    /** The name on the pill: the signed-in staff member's, or Owner. */
    fun who(activity: Activity, staff: Staff? = Access.current): String = staff?.name ?: activity.getString(R.string.access_owner)
}
