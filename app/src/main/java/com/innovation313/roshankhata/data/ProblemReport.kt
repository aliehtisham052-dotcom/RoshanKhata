package com.innovation313.roshankhata.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.innovation313.roshankhata.BuildConfig

/**
 * Letting the owner tell us something is wrong.
 *
 * Without this, a shopkeeper who hits a bug has exactly two options: uninstall,
 * or leave one star. Neither tells us what broke, and the second is permanent.
 * A route straight to the developer costs them thirty seconds and costs us
 * nothing — and it is the only way we ever hear about the bug that made them
 * stop trusting the app.
 *
 * WHAT THIS SENDS: the app version, the phone, the Android version. That is
 * what turns "it crashed" into something fixable.
 *
 * WHAT THIS NEVER SENDS: a single customer name, phone number, or balance. The
 * ledger is the most private thing this app holds — a record of who owes whom,
 * in a business where that is nobody else's affair. It does not leave the phone
 * to help us debug. If a bug cannot be found without it, the bug stays unfound.
 *
 * The email is composed in the owner's own mail app, so they see exactly what
 * is being sent and can delete any of it before it goes. Nothing is transmitted
 * behind their back.
 */
object ProblemReport {

    const val SUPPORT_EMAIL = "innovation313.support@gmail.com"

    /**
     * Technical context, and nothing else.
     *
     * Every line here describes the SOFTWARE or the DEVICE. Not one describes
     * the business, the customers, or the money.
     */
    fun deviceContext(): String = buildString {
        appendLine("---")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("---")
    }

    fun compose(context: Context, subject: String, body: String): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }
}
