package com.innovation313.roshankhata.data

/**
 * How a customer can pay, as lines for the bottom of a reminder (2 Oct).
 *
 * Taken from the same Business Profile fields the invoice's payment box
 * prints, so the owner types them once. Only filled fields appear; nothing
 * filled means no block at all, never an empty heading. The labels come in
 * from string resources so this stays plain and testable.
 */
object PaymentDetails {

    fun lines(
        jazzCash: String?,
        bankName: String?,
        accountTitle: String?,
        iban: String?,
        walletLabel: String,
        bankLabel: String
    ): List<String> {
        fun clean(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }
        val out = mutableListOf<String>()
        clean(jazzCash)?.let { out += "$walletLabel: $it" }
        val bank = listOfNotNull(clean(bankName), clean(accountTitle), clean(iban))
        // A bank block is only useful with an account number in it.
        if (clean(iban) != null) out += "$bankLabel: " + bank.joinToString(" · ")
        return out
    }
}
