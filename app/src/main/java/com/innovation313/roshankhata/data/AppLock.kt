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

    /** Away this long from the book's screens, App Lock asks again. */
    const val RELOCK_MS = 5 * 60_000L

    /**
     * When App Lock asks again (21 Oct 2026; 7 Nov 2026, the review's L2). Only the book's own screens count: the
     * doors before it, the lock itself and Mera Khata (a customer's page, opened by any shop's link) neither start nor
     * stop the time away — a link opened on a phone left on the counter used to clear it, and Back then showed the
     * book unlocked. Once due, the lock stays due until it is answered ([unlocked]); a process started afresh (Android
     * ended it in the background) starts due, so a screen it brings back asks first. Times are the phone's own clock
     * since it started (elapsedRealtime), which setting the date back does not move. No Android in here, for the tests.
     */
    object Away {
        private var started = 0
        /** When the last of the book's screens stopped; 0 while one is on screen. */
        private var leftAt = 0L
        /** Unlocked in this process (or come in while no lock was needed), and not away long since. */
        private var open = false
        /** Unlocks answered in this process: a screen that asked can tell whether one came since. */
        @Volatile var unlocks = 0
            private set

        /**
         * One of the book's screens comes on: true when the lock must be asked first. [required] (AppLock.required)
         * is asked only when it decides something.
         */
        @Synchronized
        fun bookStarted(now: Long, required: () -> Boolean): Boolean {
            if (started++ == 0) {
                if (open && now - leftAt > RELOCK_MS) open = false
                leftAt = 0L
            }
            if (open) return false
            if (!required()) { open = true; return false }
            return true
        }

        @Synchronized
        fun bookStopped(now: Long) {
            if (--started <= 0) { started = 0; leftAt = now }
        }

        /** The lock answered: the book may show. The time away starts again now if none of its screens is on. */
        @Synchronized
        fun unlocked(now: Long) {
            open = true
            leftAt = if (started == 0) now else 0L
            unlocks++
        }

        /** Would a screen that is not the book (Mera Khata) find the lock due now? Counts nothing. */
        @Synchronized
        fun due(now: Long, required: () -> Boolean): Boolean {
            if (open && started == 0 && now - leftAt > RELOCK_MS) open = false
            return !open && required()
        }

        @Synchronized
        fun isOpen(): Boolean = open

        /** Tests only: as a process starting. */
        @Synchronized
        fun resetForTest() { started = 0; leftAt = 0L; open = false; unlocks = 0 }
    }
}
