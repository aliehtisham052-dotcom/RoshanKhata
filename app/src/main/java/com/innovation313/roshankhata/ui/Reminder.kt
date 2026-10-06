package com.innovation313.roshankhata.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentDetails

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

    /**
     * The message body. Kept plain and polite — this goes to a real customer.
     *
     * [promisedDate] is the date the party themselves agreed to pay on, taken
     * from an open payment plan. When there is one the message names it, since
     * "by the date you promised" is both clearer and fairer than a vague ask.
     * When there is none it simply asks for payment soon: a reminder must
     * never invent a date the customer never gave.
     *
     * [forSms] strips the WhatsApp markup. The strings are written once, in
     * WhatsApp's own *bold* syntax — SMS does not render it, so there the
     * asterisks would show up as literal clutter around every figure.
     */
    fun buildMessage(
        context: Context,
        partyName: String,
        balance: Double,
        businessName: String?,
        promisedDate: Long? = null,
        forSms: Boolean = false,
        /** ReminderLadder step (6 Oct): 1 gentle (the message as before), 2 plain, 3 serious. */
        step: Int = com.innovation313.roshankhata.data.ReminderLadder.GENTLE,
        /** When this chase's first reminder went, for step 2's "first sent on". */
        firstSentAt: Long? = null
    ): String {
        val amount = Format.money(balance)
        val from = if (businessName.isNullOrBlank()) "" else "\n\n— $businessName"

        val body = if (Money.isPositive(balance) && step >= com.innovation313.roshankhata.data.ReminderLadder.SERIOUS) {
            context.getString(R.string.reminder_step3, partyName, amount)
        } else if (Money.isPositive(balance) && step == com.innovation313.roshankhata.data.ReminderLadder.PLAIN) {
            context.getString(R.string.reminder_step2, partyName, amount, Format.dateOnly(firstSentAt ?: System.currentTimeMillis()))
        } else if (Money.isPositive(balance)) {
            // They owe me.
            if (promisedDate != null) {
                context.getString(
                    R.string.reminder_they_owe_by_date,
                    partyName, amount, Format.dateOnly(promisedDate)
                )
            } else {
                context.getString(R.string.reminder_they_owe, partyName, amount)
            }
        } else {
            // I owe them — a courtesy note, not a demand.
            context.getString(R.string.reminder_i_owe, partyName, amount)
        }

        // How to pay, only on a reminder that asks for money (2 Oct).
        val pay = if (Money.isPositive(balance)) paymentBlock(context) else ""
        return (if (forSms) toPlainText(body) else body) + pay + from
    }

    /** "\n\nPayment:\nJazzCash/Easypaisa: …" — empty when nothing is set or it is switched off. */
    fun paymentBlock(context: Context): String {
        if (!BusinessProfile.paymentOnReminder(context)) return ""
        val lines = PaymentDetails.lines(
            jazzCash = BusinessProfile.bankJazzCash(context),
            bankName = BusinessProfile.bankName(context),
            accountTitle = BusinessProfile.bankAccountTitle(context),
            iban = BusinessProfile.bankIban(context),
            walletLabel = context.getString(R.string.pay_wallet_label),
            bankLabel = context.getString(R.string.pay_bank_label)
        )
        if (lines.isEmpty()) return ""
        return "\n\n" + context.getString(R.string.pay_heading) + "\n" + lines.joinToString("\n")
    }

    /**
     * The after-entry confirmation (2 Oct): what was written, on which day,
     * and where the account stands now, so both sides hold the same figure.
     */
    fun buildEntryMessage(
        context: Context,
        partyName: String,
        isGiven: Boolean,
        amount: Double,
        date: Long,
        balanceAfter: Double,
        businessName: String?
    ): String {
        val line = context.getString(
            if (isGiven) R.string.entry_share_gave else R.string.entry_share_got,
            partyName, Format.dateOnly(date), Format.money(amount)
        )
        val standing = when {
            Money.isPositive(balanceAfter) -> context.getString(R.string.entry_share_balance, Format.money(balanceAfter))
            Money.isPositive(-balanceAfter) -> context.getString(R.string.entry_share_advance, Format.money(-balanceAfter))
            else -> context.getString(R.string.entry_share_settled)
        }
        val from = if (businessName.isNullOrBlank()) "" else "\n\n— $businessName"
        return line + "\n" + standing + from
    }

    /**
     * WhatsApp markup out, readable text in: drop the bold asterisks, which
     * SMS shows literally.
     */
    private fun toPlainText(message: String): String =
        message.replace("*", "")

    /** Opens WhatsApp with the message ready. The user presses send. */
    fun sendViaWhatsApp(context: Context, phone: String?, message: String) {
        val number = phone?.let { toWhatsAppNumber(it) }
        if (number.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
        // The golden plane takes the message out (5 Oct); WhatsApp opens as it leaves.
        fly(context) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.whatsapp_not_installed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fly(context: Context, then: () -> Unit) {
        val activity = context as? android.app.Activity
        if (activity == null) then() else Motion.paperPlane(activity, then)
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
        fly(context) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_sms_app, Toast.LENGTH_LONG).show()
            }
        }
    }
}
