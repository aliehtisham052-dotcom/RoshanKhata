package com.innovation313.roshankhata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * App Lock's two rules from the 3 Nov 2026 audit (C3), read from the files themselves.
 *
 * On Android 7-10 the phone's PIN is asked on a screen of its own and its answer is delivered back to the lock
 * screen, which Android never does for a `noHistory` screen: a PIN-only phone could never be unlocked. And the
 * lock is turned on only after it has answered once on that phone.
 */
class LockScreenRulesTest {

    private fun file(path: String) = listOf(File(path), File("app/$path")).first { it.isFile }

    @Test
    fun `the lock screen is not noHistory, so the phone's PIN can answer it`() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        val lock = Regex("""<activity\s[^>]*android:name="\.LockActivity"[^>]*>""").find(manifest)?.value
            ?: error("LockActivity is not in the manifest")
        assertFalse(lock, lock.contains("noHistory"))
    }

    @Test
    fun `app lock is turned on only after the phone's own lock has answered`() {
        val sheet = file("src/main/java/com/innovation313/roshankhata/ui/MoreSheet.kt").readText()
        val body = sheet.substringAfter("private fun appLock(").substringBefore("\n    }\n}")
        assertFalse("turning on must not skip the prompt", body.contains("if (!enabled) flip()"))
        assertTrue(body.contains("R.string.app_lock_turn_on"))
    }

    @Test
    fun `a secure phone that answers no device credential is asked again, not let in`() {
        val src = file("src/main/java/com/innovation313/roshankhata/LockActivity.kt").readText()
        val branch = src.substringAfter("val secure =").substringBefore("android.widget.Toast")
        assertTrue(branch, branch.contains("createConfirmDeviceCredentialIntent"))
        assertTrue(branch, branch.contains("secure && code == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL"))
        assertTrue(branch, branch.contains("finishAffinity()"))
    }
}
