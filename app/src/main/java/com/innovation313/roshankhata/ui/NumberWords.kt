package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.R

/**
 * Whole rupees, spelled out — in the language the app is actually running
 * in, not one fixed language. An invoice's amount-in-words exists so a
 * customer can read the total back and check it; printing it in a language
 * they do not read defeats the whole point of the line.
 *
 * Which set of words to use is decided by a string resource
 * (number_words_language) rather than by inspecting the locale in code:
 * Android already resolved which values-* folder applies, so reading a
 * value out of the chosen folder is exactly right by construction, and
 * needs no locale-tag parsing or fallback rules of its own.
 *
 * South Asian grouping (crore/lakh/hazar/sau), not the Western
 * thousand/million split, because that is the scale a shopkeeper and a
 * customer both read a number in — including in Pakistani English, where
 * "Five Lakh" is what an invoice says, not "Five Hundred Thousand".
 *
 * 1-99 is a lookup table in Urdu and Roman Urdu rather than composed from
 * tens+ones: those names are irregular in that range, so there is no
 * shortcut that is also correct. English composes, because English in that
 * range genuinely is regular.
 *
 * HONEST LIMIT, and the reason [rupeesInWords] falls back rather than
 * guessing: Sindhi, Persian and Arabic are not implemented. Sindhi's 1-99
 * names are irregular the same way Urdu's are and I could not write them
 * with enough confidence; Arabic number-word grammar carries gender
 * agreement and dual forms that are easy to get subtly wrong; Persian uses
 * a different grouping (hezār/milyun) than the crore/lakh structure here.
 * A wrong number spelled out on a financial document is worse than a
 * correct one in a second language, so those three print the English
 * words. Adding any of them properly is a real task, not a translation of
 * this table.
 */
object NumberWords {

    private val romanUrduOnes = arrayOf(
        "Sifar", "Ek", "Do", "Teen", "Chaar", "Paanch", "Chhay", "Saat", "Aath", "Nau", "Dus",
        "Gyarah", "Barah", "Terah", "Chaudah", "Pandrah", "Solah", "Satrah", "Atharah", "Unnees", "Bees",
        "Ikkees", "Baees", "Teis", "Chaubees", "Pachees", "Chhabees", "Sattaees", "Athaees", "Untees", "Tees",
        "Ikattees", "Battees", "Taintees", "Chauntees", "Paintees", "Chhattees", "Saintees", "Adhattees", "Untalees", "Chaalees",
        "Iktalees", "Bayalees", "Taintalees", "Chawalees", "Paintalees", "Chhiyalees", "Saintalees", "Adtalees", "Uncanchas", "Pachas",
        "Ikyawan", "Bawan", "Tirpan", "Chauwan", "Pachpan", "Chhappan", "Sattawan", "Atthawan", "Unsath", "Saath",
        "Iksath", "Basath", "Tirsath", "Chausath", "Painsath", "Chhiyasath", "Sarsath", "Adsath", "Unhattar", "Sattar",
        "Ikhattar", "Bahattar", "Tihattar", "Chauhattar", "Pachhattar", "Chhihattar", "Sathattar", "Athhattar", "Unaasi", "Assi",
        "Ikyasi", "Bayasi", "Tirasi", "Chaurasi", "Pichhasi", "Chhiyasi", "Sattasi", "Athasi", "Navasi", "Nabbe",
        "Ikyanwe", "Banwe", "Tiranwe", "Chauranwe", "Pachanwe", "Chhiyanwe", "Sattanwe", "Athanwe", "Ninyanwe"
    )

    private val urduOnes = arrayOf(
        "صفر", "ایک", "دو", "تین", "چار", "پانچ", "چھ", "سات", "آٹھ", "نو", "دس",
        "گیارہ", "بارہ", "تیرہ", "چودہ", "پندرہ", "سولہ", "سترہ", "اٹھارہ", "انیس", "بیس",
        "اکیس", "بائیس", "تیئس", "چوبیس", "پچیس", "چھببیس", "ستائیس", "اٹھائیس", "انتیس", "تیس",
        "اکتیس", "بتیس", "تینتیس", "چونتیس", "پینتیس", "چھتیس", "سینتیس", "اڑتیس", "انتالیس", "چالیس",
        "اکتالیس", "بیالیس", "تینتالیس", "چوالیس", "پینتالیس", "چھیالیس", "سینتالیس", "اڑتالیس", "انچاس", "پچاس",
        "اکیاون", "باون", "ترپن", "چون", "پچپن", "چھپن", "ستاون", "اٹھاون", "انسٹھ", "ساٹھ",
        "اکسٹھ", "باسٹھ", "ترسٹھ", "چوسٹھ", "پینسٹھ", "چھیاسٹھ", "سڑسٹھ", "اڑسٹھ", "انہتر", "ستر",
        "اکہتر", "بہتر", "تہتر", "چوہتر", "پچہتر", "چھہتر", "ستہتر", "اٹھہتر", "اناسی", "اسی",
        "اکیاسی", "بیاسی", "تراسی", "چوراسی", "پچاسی", "چھیاسی", "ستاسی", "اٹھاسی", "نواسی", "نوے",
        "اکیانوے", "بانوے", "ترانوے", "چورانوے", "پچانوے", "چھیانوے", "ستانوے", "اٹھانوے", "ننانوے"
    )

    private val englishOnes = arrayOf(
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )
    private val englishTens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    /**
     * Whole rupees only — an invoice figure that matters is being read
     * aloud or checked against a printed number, not audited to the paisa.
     *
     * Caps each of the crore/lakh/hazar digit-groups at 99 rather than
     * recursing further (an "Ek Sau Crore" shape) — genuinely out of range
     * for what a small shop's invoice will ever total, and a safe cap beats
     * a crash on the rare figure that overflows it.
     */
    fun rupeesInWords(context: Context, amount: Double): String =
        when (context.getString(R.string.number_words_language)) {
            "ur" -> spell(amount, urduOnes, "کروڑ", "لاکھ", "ہزار", "سو", "روپے") { urduOnes[it] }
            "ur-Latn" -> spell(amount, romanUrduOnes, "Crore", "Lakh", "Hazar", "Sou", "Rupay") { romanUrduOnes[it] }
            else -> spell(amount, englishOnes, "Crore", "Lakh", "Thousand", "Hundred", "Rupees") { englishBelow100(it) }
        }

    /** Kept for the unit tests, which assert the Roman Urdu wording specifically. */
    fun rupeesInWordsRomanUrdu(amount: Double): String =
        spell(amount, romanUrduOnes, "Crore", "Lakh", "Hazar", "Sou", "Rupay") { romanUrduOnes[it] }

    /** Same, for the English wording — both exist so the tables can be tested without a Context. */
    fun rupeesInWordsEnglish(amount: Double): String =
        spell(amount, englishOnes, "Crore", "Lakh", "Thousand", "Hundred", "Rupees") { englishBelow100(it) }

    private fun spell(
        amount: Double,
        onesTable: Array<String>,
        croreWord: String,
        lakhWord: String,
        thousandWord: String,
        hundredWord: String,
        rupeesWord: String,
        below100: (Int) -> String
    ): String {
        var n = amount.toLong().coerceAtLeast(0)
        if (n == 0L) return "${onesTable[0]} $rupeesWord"

        val parts = mutableListOf<String>()
        val crore = (n / 10000000).coerceAtMost(99); n %= 10000000
        val lakh = (n / 100000).coerceAtMost(99); n %= 100000
        val hazar = (n / 1000).coerceAtMost(99); n %= 1000
        val sau = n / 100; n %= 100

        if (crore > 0) parts.add("${below100(crore.toInt())} $croreWord")
        if (lakh > 0) parts.add("${below100(lakh.toInt())} $lakhWord")
        if (hazar > 0) parts.add("${below100(hazar.toInt())} $thousandWord")
        if (sau > 0) parts.add("${below100(sau.toInt())} $hundredWord")
        if (n > 0) parts.add(below100(n.toInt()))

        return parts.joinToString(" ") + " " + rupeesWord
    }

    /** English 21-99 composes regularly (Twenty-One), unlike Urdu — no lookup table needed past 20. */
    private fun englishBelow100(value: Int): String = when {
        value < 20 -> englishOnes[value]
        value % 10 == 0 -> englishTens[value / 10]
        else -> "${englishTens[value / 10]}-${englishOnes[value % 10]}"
    }
}
