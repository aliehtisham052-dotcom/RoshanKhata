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
        now: Long = System.currentTimeMillis()
    ): String {
        val amount = Format.ltr(Format.money(balance))
        val who = Format.isolate(partyName)
        return when {
            Money.isPositive(balance) && step >= ReminderLadder.SERIOUS -> words.text(W.reminder_step3, who, amount)
            Money.isPositive(balance) && step == ReminderLadder.PLAIN ->
                words.text(W.reminder_step2, who, amount, Format.dateOnly(firstSentAt ?: now))
            Money.isPositive(balance) ->
                if (promisedDate != null) words.text(W.reminder_they_owe_by_date, who, amount, Format.dateOnly(promisedDate))
                else words.text(W.reminder_they_owe, who, amount)
            else -> words.text(W.reminder_i_owe, who, amount)
        }
    }

    /** The shop's name under the message, when it has one. */
    fun signed(text: String, shopName: String?): String =
        text + if (shopName.isNullOrBlank()) "" else "\n\n— " + Format.isolate(shopName)
}
