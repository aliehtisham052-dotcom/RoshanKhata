package com.innovation313.roshankhata.data

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * App Lock.
 *
 * The lock is delegated entirely to the operating system: fingerprint or face
 * where the phone has it, falling back to the device PIN, pattern, or password
 * where it does not. Roshan Khata stores no PIN of its own — Android already
 * keeps credentials in hardware-backed secure storage, and a home-grown PIN
 * kept in app preferences would be strictly worse while looking just as
 * reassuring. Only the on/off preference lives here.
 */
object AppLock {

    private const val PREFS = "roshan_khata_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"

    /** Fingerprint/face, or the device PIN as fallback. */
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Whether this phone can actually lock the app.
     * If the owner has no screen lock set at all, there is nothing to
     * authenticate against — we say so honestly rather than pretending the
     * app is protected when it is not.
     */
    fun canAuthenticate(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)

    fun isAvailable(context: Context): Boolean =
        canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Whether the app must be unlocked now: the one decision the front door (GateActivity) and the re-lock
     * after time away (BaseActivity) both use (23 Oct 2026, the review's R98: the door let everyone in on any
     * status but SUCCESS, while the re-lock asked regardless and closed the app when the prompt could not run).
     * Off, or a phone with no way to authenticate at all (no screen lock, no hardware): not required — the
     * settings say so plainly. A status Android calls unknown or temporary (the sensor busy, a security update
     * pending): the phone's own screen lock decides, since the prompt falls back to the PIN, pattern or password.
     */
    fun required(context: Context): Boolean {
        if (!isEnabled(context)) return false
        return when (canAuthenticate(context)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            else -> (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
        }
    }

    /** No screen lock configured on the phone at all. */
    fun noneEnrolled(context: Context): Boolean =
        canAuthenticate(context) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}
