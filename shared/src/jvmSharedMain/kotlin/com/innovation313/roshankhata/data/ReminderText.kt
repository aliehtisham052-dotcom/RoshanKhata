package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A payment reminder's words (Marhala 4, 25 Oct 2026: shared, so the phone's
 * customer page and Follow-up and the computer's say the same thing at each
 * step of the [ReminderLadder]).
 *
 * Kept plain and polite — this goes to a real customer. [promisedDate] is the
 * day the customer themselves agreed to pay; the gentle reminder names it,
 * since "by the date you promised" is clearer and fairer than a vague ask,
 * and a reminder never invents a date nobody gave. To someone the shop owes
 * it is a courtesy note, never a demand. The figure and the names are sealed
 * in their own direction (21 Oct 2026): in an Urdu or Arabic sentence "Ali
 * (Shop 2)" and "Rs 5,000" were reordered piece by piece.
 */
object ReminderText {

    fun body(
        words: Words,
        partyName: String,
        balance: Double,
        promisedDate: Long? = null,
        /** [ReminderLadder] step: 1 gentle, 2 plain, 3 serious. Only a balance they owe climbs it. */
        step: Int = ReminderLadder.GENTLE,
        /** When this chase's first reminder went, for the plain step's "first sent on". */
        firstSentAt: Long? = null,
        now: Long = System.currentTimeMillis(),
        /**
         * The sale being chased — the customer's newest live "I gave" entry number ([facts]) — said on
         * its own line under a reminder that asks for money (29 Oct 2026, the payment-request record),
         * so his payment can quote it and the shop can match the two. Null adds nothing.
         */
        reference: String? = null
    ): String {
        val amount = Format.ltr(Format.money(balance))
        val who = Format.isolate(partyName)
        val owed = Money.isPositive(balance)
        val text = when {
            owed && step >= ReminderLadder.SERIOUS -> words.text(W.reminder_step3, who, amount)
            owed && step == ReminderLadder.PLAIN ->
                words.text(W.reminder_step2, who, amount, Format.dateOnly(firstSentAt ?: now))
            owed ->
                if (promisedDate != null) words.text(W.reminder_they_owe_by_date, who, amount, Format.dateOnly(promisedDate))
                else words.text(W.reminder_they_owe, who, amount)
            else -> words.text(W.reminder_i_owe, who, amount)
        }
        val ref = reference?.trim()?.takeIf { it.isNotEmpty() && owed } ?: return text
        return text + "\n" + words.text(W.reminder_ref, Format.ltr(ref))
    }

    /**
     * "\n\nPayment:\nMobile wallet: …\nBank: …" — how the customer can pay, under
     * a reminder that asks for money, from the profile's own fields through
     * [PaymentDetails]. Empty when nothing is filled in or the owner switched
     * it off ([ShopSettings.paymentOnReminder]). The phone's Reminder.paymentBlock
     * until 29 Oct 2026; shared so the computer's reminder carries the same.
     */
    fun paymentBlock(
        words: Words,
        wallet: String?,
        bankName: String?,
        accountTitle: String?,
        iban: String?,
        enabled: Boolean = true
    ): String {
        if (!enabled) return ""
        val lines = PaymentDetails.lines(
            jazzCash = wallet, bankName = bankName, accountTitle = accountTitle, iban = iban,
            walletLabel = words.text(W.pay_wallet_label), bankLabel = words.text(W.pay_bank_label)
        )
        if (lines.isEmpty()) return ""
        return "\n\n" + words.text(W.pay_heading) + "\n" + lines.joinToString("\n")
    }

    /** [paymentBlock] from the shop's settings, on either platform. */
    fun paymentBlock(words: Words, settings: ShopSettings): String = paymentBlock(
        words, settings.bankJazzCash(), settings.bankName(), settings.bankAccountTitle(), settings.bankIban(),
        settings.paymentOnReminder()
    )

    /** The shop's name under the message, when it has one. */
    fun signed(text: String, shopName: String?): String =
        text + if (shopName.isNullOrBlank()) "" else "\n\n— " + Format.isolate(shopName)

    /**
     * What a reminder reads from the book before its words are made (both platforms, 29 Oct 2026): the
     * day the customer promised ([KhataDao.reminderDateForParty]) and the sale being chased, his newest
     * live "I gave" entry number ([KhataDao.newestGaveNumber]) — read together, before the preview is
     * built, so the words the owner reads are the words that go.
     */
    data class Facts(val promisedDate: Long?, val reference: String?)

    suspend fun facts(dao: KhataDao, partyId: Long): Facts =
        Facts(dao.reminderDateForParty(partyId), dao.newestGaveNumber(partyId))
}
