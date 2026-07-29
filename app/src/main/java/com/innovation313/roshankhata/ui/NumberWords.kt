package com.innovation313.roshankhata.ui

/**
 * Whole rupees, spelled out in Roman Urdu — "Unnees Hazar Do Sou Pichhasi
 * Rupay" for 19,285. South Asian grouping (crore/lakh/hazar/sau), not the
 * Western thousand/million split, because that is the scale a shopkeeper
 * and a customer both read a number in.
 *
 * 1-99 is a lookup table rather than composed from tens+ones — Urdu number
 * names are irregular in that range (technically the same fact that made
 * this worth writing instead of reaching for a library), so there is no
 * shortcut that is also correct.
 */
object NumberWords {

    private val ones = arrayOf(
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

    /**
     * Whole rupees only — an invoice figure that matters is being read
     * aloud or checked against a printed number, not audited to the paisa.
     *
     * Caps each of the crore/lakh/hazar digit-groups at 99 rather than
     * recursing further (an "Ek Sau Crore" shape) — genuinely out of range
     * for what a small shop's invoice will ever total, and a safe cap beats
     * a crash on the rare figure that overflows it.
     */
    fun rupeesInWords(amount: Double): String {
        var n = amount.toLong().coerceAtLeast(0)
        if (n == 0L) return "Sifar Rupay"

        val parts = mutableListOf<String>()
        val crore = (n / 10000000).coerceAtMost(99); n %= 10000000
        val lakh = (n / 100000).coerceAtMost(99); n %= 100000
        val hazar = (n / 1000).coerceAtMost(99); n %= 1000
        val sau = n / 100; n %= 100

        if (crore > 0) parts.add("${ones[crore.toInt()]} Crore")
        if (lakh > 0) parts.add("${ones[lakh.toInt()]} Lakh")
        if (hazar > 0) parts.add("${ones[hazar.toInt()]} Hazar")
        if (sau > 0) parts.add("${ones[sau.toInt()]} Sou")
        if (n > 0) parts.add(ones[n.toInt()])

        return parts.joinToString(" ") + " Rupay"
    }
}
