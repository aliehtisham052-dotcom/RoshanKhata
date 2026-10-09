package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import java.security.MessageDigest

/**
 * Why Google sign-in failed, kept on screen long enough to read and copy (9 Oct).
 *
 * The owner reported "cannot sign in" again and again. The reason was only in
 * a Toast (gone in four seconds), and a sheet that Google itself closed
 * - which is what a missing Android OAuth client looks like - came back as a
 * "cancellation" and showed no reason at all.
 *
 * Google matches a sign-in to an Android OAuth client by this build's package
 * name AND the SHA-1 of the key that signed it. The test APK (".dev", signed
 * by CI) and the Play build (re-signed by Google) are two different pairs,
 * and each needs its own client in the Google Cloud project. So the dialog
 * prints both values for the build in hand: they are exactly what has to be
 * entered there, copied rather than retyped.
 */
object SignInProblem {

    /** A dismissal by the owner, as Credential Manager words it: not a fault. */
    fun isOwnerDismissal(message: String?): Boolean =
        message?.contains("cancelled by the user", ignoreCase = true) == true

    fun show(activity: Activity, reason: String?) {
        if (activity.isFinishing || activity.isDestroyed) return
        val details = buildString {
            if (!reason.isNullOrBlank()) append(reason).append("\n\n")
            append("Package: ").append(activity.packageName).append('\n')
            append("SHA-1: ").append(signingSha1(activity) ?: "?")
        }
        val text = activity.getString(R.string.drive_signin_failed) + "\n\n" +
            activity.getString(R.string.drive_signin_details) + "\n" + details
        MaterialAlertDialogBuilder(activity)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.copy_details) { _, _ ->
                try {
                    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Roshan Khata sign-in", details))
                    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
            }
            .show()
    }

    /** SHA-1 of the certificate that signed this install, as Google Cloud asks for it. */
    fun signingSha1(context: Context): String? = try {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        certs?.lastOrNull()?.toByteArray()?.let { der ->
            MessageDigest.getInstance("SHA-1").digest(der).joinToString(":") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
    }
}
