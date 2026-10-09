package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * What a supplier's bill already says about a product's label, offered as a
 * starting point for the label-details form after a bill is saved (1 Oct).
 *
 * The owner saw the form open blank for products the bill had just named in
 * full — "LEPTOKILL 20%EC 800 ML". Its formulation, its type and its unit are
 * in that name; the company that sent it is on the bill. These are only
 * SUGGESTIONS: they go into empty boxes of a form the owner still has to
 * save, and a box he has already filled is never touched.
 *
 * Deliberately not guessed: the technical name (Leptokill does not say what
 * is in it) and the registration number. Those come off the label itself,
 * and a wrong one is worse than a blank one in an inspection.
 */
object LabelGuess {

    data class Guess(
        val company: String? = null,
        val unit: String? = null,
        val type: String? = null,
        val formulation: String? = null
    )

    /** Formulation codes as printed on pesticide labels: 20%EC, 5 EC, 75WG, (TAB). */
    private val FORMULATION = Regex(
        """(?<![A-Za-z])(EC|SC|WP|WG|WDG|SL|GR|SP|DP|AS|CS|OD|EW|ME|FS|ULV|TAB)(?![A-Za-z])""",
        RegexOption.IGNORE_CASE
    )

    private val FERTILIZER = Regex(
        """\b(urea|dap|npk|nitrophos|phosphate|potash|sop|mop|can|ammonium|nitrate|sulphate|sulfate|zinc|boron|gypsum|humic|fertili[sz]er|khad)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PESTICIDE = Regex(
        """(cide|kill|\binsect|\bfung|\bherb|\bweed|\bgoli\b)""",
        RegexOption.IGNORE_CASE
    )
    private val SEED = Regex("""\b(seed|seeds|beej|hybrid)\b""", RegexOption.IGNORE_CASE)

    /** Pack size: "800 ML", "1L", "25KG", "90 GM". */
    private val PACK = Regex("""(\d+(?:\.\d+)?)\s*(ml|ltr|litre|liter|l|kg|kgs|gm|gms|g)\b""", RegexOption.IGNORE_CASE)

    fun of(productName: String, billUnit: String?, supplier: String?, trade: Trade = Trade.DEFAULT): Guess {
        val name = productName.trim()
        // The formulation codes (EC, SC, WP…) and the three kinds are an
        // agri dealer's reading of a label (9 Oct). On any other trade they
        // are left blank: a kiryana's "SC" is a brand, not a suspension
        // concentrate, and a wrong kind on the register is worse than none.
        val agri = trade == Trade.AGRI
        val formulation = if (agri) FORMULATION.find(name)?.value?.uppercase(Locale.ROOT) else null

        val type = if (!agri) null else when {
            SEED.containsMatchIn(name) -> "Seed"
            formulation != null || PESTICIDE.containsMatchIn(name) -> "Pesticide"
            FERTILIZER.containsMatchIn(name) -> "Fertilizer"
            else -> null
        }

        val unit = billUnit?.takeIf { it.isNotBlank() } ?: run {
            val pack = PACK.find(name)
            val size = pack?.groupValues?.get(1)?.toDoubleOrNull()
            when (pack?.groupValues?.get(2)?.lowercase(Locale.ROOT)) {
                "ml", "ltr", "litre", "liter", "l" -> "bottle"
                "kg", "kgs" -> if (size != null && size >= 10) "bag" else "packet"
                "gm", "gms", "g" -> "packet"
                else -> if (type == "Fertilizer") "bag" else null
            }
        }

        return Guess(
            company = supplier?.trim()?.takeIf { it.isNotEmpty() },
            unit = unit,
            type = type,
            formulation = formulation
        )
    }
}
