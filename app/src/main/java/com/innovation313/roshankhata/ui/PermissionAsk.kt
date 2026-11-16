package com.innovation313.roshankhata.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R

/**
 * The one place the app asks Android for a runtime permission (16 Nov 2026, the owner: wherever a prompt is
 * duplicated, find and fix it across the whole app). Before this, notifications were asked three ways (Khata with
 * its reason, Waris and Payment voice bare, each with its own unused request code), Bluetooth twice (an entry's
 * receipt and the shared printer picker) and the microphone twice (Hudhud's sheet and his voice settings), each
 * with its own copy of the register, launch, unregister and "screen already gone" handling.
 *
 * What every caller gets: a permission this Android does not ask about (notifications before 13, Bluetooth before
 * 12) counts as granted; a screen that is gone when the answer comes is told nothing; and a launcher that cannot
 * open is said through [ask]'s `onUnavailable`, so a phone that has no permission screen is never recorded as
 * "denied". The microphone's and the contacts' own follow-ups (the app's settings page after a "never ask again",
 * the "why" card) stay with their screens: they are what those screens do with the answer, not another way of asking.
 *
 * Not moved, on purpose: the contacts screen (its refused-for-good state is its own page) and the Downloads
 * permission on Android 7-9 (an answer that must outlive a rebuilt screen, BaseActivity.withDownloadsAccess).
 */
object PermissionAsk {

    /** Whether this Android asks about [permission] at all; where it does not, the app already has it. */
    fun asked(permission: String): Boolean = when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
        Manifest.permission.BLUETOOTH_CONNECT -> Build.VERSION.SDK_INT >= 31
        else -> true
    }

    /** True when the app may use [permission] now (also where this Android never asks about it). */
    fun granted(context: Context, permission: String): Boolean =
        !asked(permission) ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks for [permission] and tells [onResult] the answer: at once with true when it is already given, else after
     * Android's own question. Nothing is told when [host] was closed or rebuilt meanwhile (the owner asks again from
     * the new screen, which then finds the permission given or refused). [onUnavailable] runs when the question
     * cannot be opened at all; left out, that is told to [onResult] as a refusal.
     */
    fun ask(
        host: ComponentActivity,
        permission: String,
        onUnavailable: (() -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) {
        if (granted(host, permission)) { onResult(true); return }
        lateinit var launcher: ActivityResultLauncher<String>
        launcher = host.activityResultRegistry.register(
            "permission-" + permission + "-" + System.nanoTime(), ActivityResultContracts.RequestPermission()
        ) { answer ->
            launcher.unregister()
            if (host.isFinishing || host.isDestroyed) return@register
            onResult(answer)
        }
        try {
            launcher.launch(permission)
        } catch (e: Exception) {
            launcher.unregister()
            if (onUnavailable != null) onUnavailable() else onResult(false)
        }
    }

    /**
     * The notification question at the start of a screen that has no reason of its own to give (Khata). After a
     * "no" its reason is told first, in the owner's words, and at most once a week (8 Nov 2026, M61: it came back at
     * every opening); before any "no" Android's question is asked straight away, with no "asked once" flag — a backup
     * restores SharedPreferences across reinstalls, so a remembered flag once blocked the question for good (found
     * on the owner's phone). Android itself stops showing it after two refusals, so asking at each opening until it
     * is given cannot become spam. The screens that turn on something the notification is the point of (Waris, Payment
     * voice) call [ask] directly: their reason is the switch the owner just turned.
     */
    fun notificationsWithReason(host: AppCompatActivity) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (granted(host, permission)) return
        if (!ActivityCompat.shouldShowRequestPermissionRationale(host, permission)) {
            ask(host, permission) { }
            return
        }
        val whyPrefs = host.getSharedPreferences("notif_permission_ui", Context.MODE_PRIVATE)
        val whyDue = System.currentTimeMillis() - whyPrefs.getLong("why_at", 0L) > WHY_EVERY_MS
        if (!whyDue) return
        whyPrefs.edit().putLong("why_at", System.currentTimeMillis()).apply()
        MaterialAlertDialogBuilder(host)
            .setMessage(R.string.notif_permission_why)
            .setPositiveButton(R.string.ok) { _, _ -> ask(host, permission) { } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private const val WHY_EVERY_MS = 7L * 24 * 60 * 60 * 1000
}
