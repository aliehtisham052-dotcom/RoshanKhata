package com.innovation313.roshankhata.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AndroidShopSettings
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.ReminderText
import com.innovation313.roshankhata.words.Words

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
     * Numbers arrive in many shapes: 03001234567, +923001234567, 0092...,
     * or with spaces and dashes. WhatsApp needs a bare international number
     * with no plus and no separators. A local number takes the SHOP'S
     * country's code (WhatsAppNumber's own default, ShopCountry.current):
     * this wrapper passed a literal "92" until 23 Oct, so a Bangladeshi
     * shop's reminder went to a Pakistani number.
     */
    fun toWhatsAppNumber(raw: String): String? =
        com.innovation313.roshankhata.data.WhatsAppNumber.of(raw)

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
        firstSentAt: Long? = null,
        /** The sale being chased (ReminderText.facts), quoted on its own line; null adds nothing. */
        reference: String? = null
    ): String {
        // The words are the shared ReminderText's (Marhala 4, 25 Oct 2026): the computer's customer page
        // and Follow-up say the same at each step. The figure and the names are sealed in their own
        // direction there; in an English sentence the wrapper adds nothing, so an SMS stays as short.
        val from = if (businessName.isNullOrBlank()) "" else "\n\n— " + Format.isolate(businessName)
        val words = AndroidWords(context)
        val body = ReminderText.body(
            words, partyName, balance, promisedDate, step, firstSentAt, reference = reference
        )

        // How to pay, only on a reminder that asks for money (2 Oct). Its heading is bold in every language
        // (*How to pay*), so an SMS strips the block too (12 Nov 2026, the handover: the asterisks showed in SMS).
        val pay = if (Money.isPositive(balance)) paymentBlock(context, words) else ""
        return (if (forSms) toPlainText(body + pay) else body + pay) + from
    }

    /**
     * "\n\nPayment:\nMobile wallet: …" — empty when nothing is set or it is switched off. The rule is the shared
     * ReminderText.paymentBlock (29 Oct 2026), read here from this phone's profile; the computer reads its own.
     */
    fun paymentBlock(context: Context, words: Words = AndroidWords(context)): String =
        ReminderText.paymentBlock(words, AndroidShopSettings(context))

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
            Format.isolate(partyName), Format.dateOnly(date), Format.ltr(Format.money(amount))
        )
        val standing = when {
            Money.isPositive(balanceAfter) -> context.getString(R.string.entry_share_balance, Format.ltr(Format.money(balanceAfter)))
            Money.isPositive(-balanceAfter) -> context.getString(R.string.entry_share_advance, Format.ltr(Format.money(-balanceAfter)))
            else -> context.getString(R.string.entry_share_settled)
        }
        val from = if (businessName.isNullOrBlank()) "" else "\n\n— " + Format.isolate(businessName)
        return line + "\n" + standing + from
    }

    /**
     * WhatsApp markup out, readable text in: drop the bold asterisks, which
     * SMS shows literally.
     */
    private fun toPlainText(message: String): String =
        message.replace("*", "")

    /** A WhatsApp message as an SMS shows it: no bold asterisks (the same text, sent by SMS after "Not sent"). */
    fun plainText(message: String): String = toPlainText(message)

    /**
     * Opens WhatsApp with the message ready. The user presses send. The plane lifts out of [from] when the
     * caller gives the button that was tapped (Follow-up's Message pill). [opened] runs only once WhatsApp (or
     * WhatsApp Business) has opened — never a browser, which a phone without WhatsApp opened the wa.me link in, and
     * that counted as reminded (3 Nov 2026, the review); [failed] then. With both WhatsApps on the phone Android asks
     * which, as it always did, and a question the owner closes still counts: Android does not say it was closed.
     */
    fun sendViaWhatsApp(
        context: Context,
        phone: String?,
        message: String,
        from: android.view.View? = null,
        opened: (() -> Unit)? = null,
        failed: (() -> Unit)? = null
    ) {
        val number = phone?.let { toWhatsAppNumber(it) }
        if (number.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            failed?.invoke()
            return
        }

        val tries = whatsAppIntents(context, number, message)
        // The golden plane takes the message out (5 Oct; the 3 Nov 2026 flight); WhatsApp opens as it leaves.
        fly(context, from) {
            val sent = tries.any { intent ->
                try { context.startActivity(intent); true } catch (e: ActivityNotFoundException) { false }
            }
            if (sent) opened?.invoke()
            else {
                Toast.makeText(context, R.string.whatsapp_not_installed, Toast.LENGTH_LONG).show()
                failed?.invoke()
            }
        }
    }

    /** WhatsApp and WhatsApp Business, the two that open a wa.me link as a chat. */
    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * The ways into the chat that no browser takes, tried in turn until one opens. First WhatsApp's own wa.me link:
     * on Android 11 and later the system refuses a browser for it (FLAG_ACTIVITY_REQUIRE_NON_BROWSER) and keeps the
     * owner's own choice between the two WhatsApps; before Android 11 every app is visible, so the one WhatsApp that is
     * there is named. Then the app's own whatsapp:// address, which only the two WhatsApps answer — for a phone whose
     * link settings send wa.me to the browser, and for one with both WhatsApps before Android 11.
     */
    private fun whatsAppIntents(context: Context, number: String, message: String): List<Intent> {
        val text = Uri.encode(message)
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$text")).addCategory(Intent.CATEGORY_BROWSABLE)
        val own = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$number&text=$text"))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return listOf(link.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER), own)
        }
        @Suppress("DEPRECATION")
        val here = runCatching {
            context.packageManager.queryIntentActivities(link, 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        val apps = WHATSAPP.filter { it in here }
        return if (apps.size == 1) listOf(link.setPackage(apps[0]), own) else listOf(own)
    }

    private fun fly(context: Context, from: android.view.View?, then: () -> Unit) {
        val activity = context as? android.app.Activity
        if (activity == null) then() else Motion.paperPlane(activity, from, then)
    }

    /** Opens the SMS app with the message ready. The user presses send. [opened] runs once the SMS app has opened. */
    fun sendViaSms(context: Context, phone: String?, message: String, opened: (() -> Unit)? = null) {
        if (phone.isNullOrBlank()) {
            Toast.makeText(context, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
        }
        fly(context, null) {
            try {
                context.startActivity(intent)
                opened?.invoke()
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_sms_app, Toast.LENGTH_LONG).show()
            }
        }
    }
}
