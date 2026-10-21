package com.innovation313.roshankhata.ui

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.Staff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is at the counter, on the phone (POS Phase 5): the sign-in sheet and
 * the manager's PIN over a cashier's shoulder. The phone has no owner PIN of
 * its own (Android's screen lock is the owner's), so an override here is a
 * manager's PIN, or signing out to the owner.
 */
object AccessUi {

    fun signIn(activity: Activity, dao: KhataDao, done: () -> Unit) {
        AppScope.launch {
            val staff = withContext(Dispatchers.IO) { dao.loginStaffOnce() }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                val labels = (listOf(activity.getString(R.string.access_owner)) + staff.map { it.name + " · " + roleWord(activity, it.role) }).toTypedArray()
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.access_who)
                    .setItems(labels) { _, i ->
                        if (i == 0) { Access.signOut(); done(); return@setItems }
                        askPin(activity, staff[i - 1].name) { pin ->
                            val s = staff[i - 1]
                            if (Access.pinMatches(s, pin)) {
                                Access.signIn(s)
                                AppScope.launch { withContext(Dispatchers.IO) { dao.log(AuditAction.SIGN_IN, s.name) } }
                                done()
                            } else Toast.makeText(activity, R.string.access_wrong_pin, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    /** Runs [then] at once when the signed-in user may; otherwise after a manager's PIN. */
    fun allowed(activity: Activity, dao: KhataDao, permission: Access.Permission, what: String, then: (by: String?) -> Unit) {
        if (Access.can(permission)) { then(null); return }
        AppScope.launch {
            val managers = withContext(Dispatchers.IO) { dao.loginStaffOnce().filter { it.role == Role.MANAGER } }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (managers.isEmpty()) { Toast.makeText(activity, R.string.access_denied, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.access_override_title)
                    .setMessage(activity.getString(R.string.access_override_help) + "\n" + what)
                    .setItems(managers.map { it.name }.toTypedArray()) { _, i ->
                        askPin(activity, managers[i].name) { pin ->
                            val by = Access.override(managers, managers[i].id, pin, null)
                            if (by == null) Toast.makeText(activity, R.string.access_wrong_pin, Toast.LENGTH_LONG).show()
                            else {
                                AppScope.launch { withContext(Dispatchers.IO) { dao.log(AuditAction.MANAGER_OVERRIDE, by, detail = what) } }
                                then(by)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun askPin(activity: Activity, title: String, onPin: (String) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.staff_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0); addView(input)
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setView(box)
            .setPositiveButton(R.string.access_sign_in) { _, _ -> onPin(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show().also { KeyboardFollow.dialog(it) }
    }

    fun roleWord(activity: Activity, role: String?): String = activity.getString(
        when (role) {
            Role.MANAGER -> R.string.staff_role_manager
            Role.CASHIER -> R.string.staff_role_cashier
            else -> R.string.staff_role_none
        }
    )
}
