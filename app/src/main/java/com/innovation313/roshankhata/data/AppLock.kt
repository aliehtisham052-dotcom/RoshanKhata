package com.innovation313.roshankhata.data

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

    /** No screen lock configured on the phone at all. */
    fun noneEnrolled(context: Context): Boolean =
        canAuthenticate(context) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}
