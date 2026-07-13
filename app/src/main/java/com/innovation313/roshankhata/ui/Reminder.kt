package com.innovation313.roshankhata.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.innovation313.roshankhata.R

/**
 * Payment reminders.
 *
 * Nothing is ever sent silently. Both paths hand off to the user's own
 * WhatsApp or SMS app with the message pre-filled — the owner reads it and
 * presses send themselves. Roshan Khata asks for no SMS permission and
 * dispatches nothing on the user's behalf.
 */
object Reminder {

    /**
     * Pakistani numbers arrive in many shapes: 03001234567, +923001234567,
     * 0092..., or with spaces and dashes. WhatsApp needs a bare international
     * number with no plus and no separators.
     */
    fun toWhatsAppNumber(raw: String, defaultCountryCode: String = "92"): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        return when {
            // 00 92 300 1234567
            digits.startsWith("00") && digits.length > 4 -> digits.removePrefix("00")
            // 92 300 1234567  (already international)
            digits.startsWith(defaultCountryCode) && digits.length >= 12 -> digits
            // 0300 1234567  (local, leading zero)
            digits.startsWith("0") -> defaultCountryCode + digits.drop(1)
            // 300 1234567  (local, no leading zero)
            digits.length in 9..10 -> defaultCountryCode + digits
            else -> digits
        }
    }

    /** The message body. Kept plain and polite — this goes to a real customer. */
    fun buildMessage(
        context: Context,
        partyName: String,
        balance: Double,
        businessName: String?
    ): String {
        val amount = Format.money(balance)
        val from = if (businessName.isNullOrBlank()) "" else "\n\n— $businessName"

        return if (balance > 0) {
            // They owe me.
            context.getString(R.string.reminder_they_owe, partyName, amount) + from
        } else {
            // I owe them — a courtesy note, not a demand.
            context.getString(R.string.reminder_i_owe, partyName, amount) + from
        }
    }

    /** Opens WhatsApp with the message ready. The user presses send. */
    fun sendViaWhatsApp(context: Context, phone: String?, message: String) {
        val number = phone?.let { toWhatsAppNumber(it) }
        if (number.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.whatsapp_not_installed, Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the SMS app with the message ready. The user presses send. */
    fun sendViaSms(context: Context, phone: String?, message: String) {
        if (phone.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_sms_app, Toast.LENGTH_LONG).show()
        }
    }
}
