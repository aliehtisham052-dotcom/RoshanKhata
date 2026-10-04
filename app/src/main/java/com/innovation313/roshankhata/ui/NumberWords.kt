package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Currency

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
 * Hindi and Bengali have their own tables too (added 4 Oct 2026).
 *
 * Indonesian (4 Oct 2026) is fully regular, so it is composed, not looked
 * up, and it counts the way Indonesia does: ribu, juta, miliar, triliun
 * (thousand, million, billion, trillion), never lakh and crore, whichever
 * currency the shop uses. Rupiah figures run long (a small invoice is
 * millions), so it goes up to the trillions. See [inWordsIndonesian].
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

    // Hindi and Bengali (4 Oct 2026): 0-99 are lookup tables for the same
    // reason as Urdu — the names are irregular all the way up — and both
    // count in lakh and crore, so they fit [spell] as it stands.
    private val hindiOnes = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
        "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
        "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
        "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
        "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
        "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
        "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर", "सत्तर",
        "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी", "अस्सी",
        "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
        "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
    )

    private val bengaliOnes = arrayOf(
        "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়", "দশ",
        "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ", "বিশ",
        "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আটাশ", "ঊনত্রিশ", "ত্রিশ",
        "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "ঊনচল্লিশ", "চল্লিশ",
        "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "ঊনপঞ্চাশ", "পঞ্চাশ",
        "একান্ন", "বাহান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "ঊনষাট", "ষাট",
        "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "ঊনসত্তর", "সত্তর",
        "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "ঊনআশি", "আশি",
        "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "ঊননব্বই", "নব্বই",
        "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই"
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
            "hi" -> rupeesInWordsHindi(amount)
            "bn" -> inWordsBengali(amount, "রুপি")
            "id" -> inWordsIndonesian(amount, "rupee")
            else -> spell(amount, englishOnes, "Crore", "Lakh", "Thousand", "Hundred", "Rupees") { englishBelow100(it) }
        }

    /**
     * The invoice's "amount in words" for whatever sign the shop chose (4 Oct 2026).
     *
     * Rs, ₹ and ৳ are counted the South Asian way (lakh, crore) in the app's
     * language, as before. Any other sign is spelled in English with the
     * Western groups (thousand, million) and ends with the currency's own
     * code — "Five Thousand Two Hundred AED" — because "Lakh Dirham" is not
     * how anyone in a Gulf shop reads a figure, and the Urdu/Sindhi tables
     * only know rupees.
     */
    fun amountInWords(context: Context, amount: Double): String {
        val sign = Currency.symbol
        // Indonesian reads every currency the same way, in its own words:
        // "rupiah" for Rp, "taka" for the taka sign, and the currency's code
        // for the rest ("lima ribu dua ratus USD"), as the English line does.
        if (context.getString(R.string.number_words_language) == "id") {
            return inWordsIndonesian(
                amount,
                when (sign) {
                    "Rp" -> "rupiah"
                    "Rs", "₹" -> "rupee"
                    "৳" -> "taka"
                    else -> Currency.CHOICES.firstOrNull { it.first == sign }?.second ?: sign
                }
            )
        }
        return when (sign) {
            "Rs", "₹" -> rupeesInWords(context, amount)
            "৳" ->
                if (context.getString(R.string.number_words_language) == "bn") inWordsBengali(amount, "টাকা")
                else spell(amount, englishOnes, "Crore", "Lakh", "Thousand", "Hundred", "Taka") { englishBelow100(it) }
            else -> spellWestern(amount, Currency.CHOICES.firstOrNull { it.first == sign }?.second ?: sign)
        }
    }

    /** Thousand / million / billion groups, English only; capped the same way as [spell]. */
    fun spellWestern(amount: Double, unitWord: String): String {
        var n = amount.toLong().coerceAtLeast(0)
        if (n == 0L) return "${englishOnes[0]} $unitWord"
        val parts = mutableListOf<String>()
        val billion = (n / 1_000_000_000).coerceAtMost(999); n %= 1_000_000_000
        val million = n / 1_000_000; n %= 1_000_000
        val thousand = n / 1_000; n %= 1_000
        fun below1000(v: Long): String {
            val h = (v / 100).toInt(); val r = (v % 100).toInt()
            return listOfNotNull(
                if (h > 0) "${englishOnes[h]} Hundred" else null,
                if (r > 0) englishBelow100(r) else null
            ).joinToString(" ")
        }
        if (billion > 0) parts.add("${below1000(billion)} Billion")
        if (million > 0) parts.add("${below1000(million)} Million")
        if (thousand > 0) parts.add("${below1000(thousand)} Thousand")
        if (n > 0) parts.add(below1000(n))
        return parts.joinToString(" ") + " " + unitWord
    }

    private val indonesianOnes = arrayOf(
        "nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan"
    )

    /**
     * Indonesian wording, ending in [unitWord]: "Dua juta lima ratus ribu
     * rupiah". Public so it can be tested without a Context.
     *
     * The rules, all of them: 10 sepuluh, 11 sebelas, 12-19 "<digit> belas",
     * tens "<digit> puluh", hundreds "<digit> ratus", and ONE of a ten, a
     * hundred or a thousand takes the prefix se- (sepuluh, seratus, seribu)
     * instead of "satu". From a million up it is "satu juta", "satu miliar",
     * "satu triliun". Written as a sentence, so only the first letter is
     * capital. Whole units only and never negative, like every other
     * language here; capped at 999 triliun rather than failing on a figure
     * no shop's invoice reaches.
     */
    fun inWordsIndonesian(amount: Double, unitWord: String): String {
        var n = amount.toLong().coerceAtLeast(0)
        if (n == 0L) return "Nol $unitWord"

        fun below1000(v: Int): String {
            val parts = mutableListOf<String>()
            val h = v / 100
            val r = v % 100
            if (h == 1) parts.add("seratus") else if (h > 1) parts.add("${indonesianOnes[h]} ratus")
            when {
                r == 0 -> Unit
                r < 10 -> parts.add(indonesianOnes[r])
                r == 10 -> parts.add("sepuluh")
                r == 11 -> parts.add("sebelas")
                r < 20 -> parts.add("${indonesianOnes[r - 10]} belas")
                else -> {
                    parts.add("${indonesianOnes[r / 10]} puluh")
                    if (r % 10 > 0) parts.add(indonesianOnes[r % 10])
                }
            }
            return parts.joinToString(" ")
        }

        val parts = mutableListOf<String>()
        val triliun = (n / 1_000_000_000_000).coerceAtMost(999).toInt(); n %= 1_000_000_000_000
        val miliar = (n / 1_000_000_000).toInt(); n %= 1_000_000_000
        val juta = (n / 1_000_000).toInt(); n %= 1_000_000
        val ribu = (n / 1_000).toInt(); n %= 1_000

        if (triliun > 0) parts.add("${below1000(triliun)} triliun")
        if (miliar > 0) parts.add("${below1000(miliar)} miliar")
        if (juta > 0) parts.add("${below1000(juta)} juta")
        if (ribu == 1) parts.add("seribu") else if (ribu > 1) parts.add("${below1000(ribu)} ribu")
        if (n > 0) parts.add(below1000(n.toInt()))

        val words = parts.joinToString(" ")
        return words.replaceFirstChar { it.uppercase() } + " " + unitWord
    }

    /** Hindi wording; public so the table can be tested without a Context. */
    fun rupeesInWordsHindi(amount: Double): String =
        spell(amount, hindiOnes, "करोड़", "लाख", "हज़ार", "सौ", "रुपये") { hindiOnes[it] }

    /** Bengali wording, ending in [unitWord] (টাকা for ৳, রুপি for rupees). */
    fun inWordsBengali(amount: Double, unitWord: String): String =
        spell(amount, bengaliOnes, "কোটি", "লক্ষ", "হাজার", "শত", unitWord) { bengaliOnes[it] }

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
