package com.innovation313.roshankhata.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * The units of measure, shown in the app's language and SAVED in one (4 Oct 2026).
 *
 * The unit list used to be English in every language: a Hindi screen offered
 * "bag, kg, bottle". It was left that way on purpose, because the unit is
 * saved with every goods line and the app groups a product's lines, its
 * stock and its last rate by item AND unit. A translated word saved on one
 * entry and "bag" on the next would split one product in two.
 *
 * So the two are separated. What is SAVED is always the same key ([KEYS],
 * the old English list, exactly what every existing book already holds).
 * What is SHOWN is that key's word in the app's language ([label]). What is
 * TYPED or picked goes back to the key before anything is saved or compared
 * ([canonical]) — from any of the app's languages, so a helper typing "بوری"
 * on a phone set to English still saves "bag".
 *
 * A unit that is not one of these (the owner's own word: "tin", "تھیلا") is
 * left exactly as typed, shown as typed, in every language. It always was.
 *
 * Words the owner typed by hand BEFORE this, where they are one of these
 * labels ("بوری" typed under the old English list), are turned into the key
 * once as the book opens ([normalizeStored]); otherwise the same product
 * would sit under "بوری" (old) and "bag" (new) while both read "بوری".
 */
object UnitWords {

    /** What is saved. Order is the order of the list the owner picks from. */
    val KEYS: List<String> = listOf(
        "bag", "kg", "maund", "seer", "litre", "ml", "packet", "bottle", "piece", "box", "ton"
    )

    /** One array per language, in [KEYS] order. English shows the keys themselves. */
    private val LABELS: Map<String, Array<String>> = mapOf(
        "ur-latn" to arrayOf(
            "bori", "kg", "mann", "ser", "litre", "ml", "packet", "botal", "adad", "dabba", "ton"
        ),
        "ur" to arrayOf(
            "بوری", "کلو", "من", "سیر", "لیٹر", "ملی لیٹر", "پیکٹ", "بوتل", "عدد", "ڈبہ", "ٹن"
        ),
        "sd" to arrayOf(
            "ٻوري", "ڪلو", "مڻ", "سير", "ليٽر", "ملي ليٽر", "پيڪيٽ", "بوتل", "عدد", "دٻو", "ٽن"
        ),
        "fa" to arrayOf(
            "کیسه", "کیلو", "من", "سیر", "لیتر", "میلی‌لیتر", "بسته", "بطری", "عدد", "جعبه", "تن"
        ),
        "ar" to arrayOf(
            "كيس", "كغ", "مَنّ", "سير", "لتر", "مل", "عبوة", "زجاجة", "قطعة", "صندوق", "طن"
        ),
        "hi" to arrayOf(
            "बोरी", "किलो", "मन", "सेर", "लीटर", "मिली", "पैकेट", "बोतल", "नग", "डिब्बा", "टन"
        ),
        "bn" to arrayOf(
            "বস্তা", "কেজি", "মণ", "সের", "লিটার", "মিলি", "প্যাকেট", "বোতল", "পিস", "বাক্স", "টন"
        ),
        "id" to arrayOf(
            "karung", "kg", "maund", "seer", "liter", "ml", "bungkus", "botol", "buah", "kotak", "ton"
        )
    )

    /** Every label of every language, and every key, to its key. Lower-cased. */
    private val TO_KEY: Map<String, String> = buildMap {
        for ((i, key) in KEYS.withIndex()) {
            put(key, key)
            for (labels in LABELS.values) put(fold(labels[i]), key)
        }
    }

    private fun fold(text: String): String = text.trim().lowercase(Locale.ROOT)

    internal fun tableOf(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        return when {
            language == "ur" && locale.script.equals("Latn", ignoreCase = true) -> "ur-latn"
            language == "in" -> "id"
            else -> language
        }
    }

    /**
     * The word to SHOW for a saved unit: its name in the app's language when
     * it is one of [KEYS] (in any letter case), otherwise the text as saved.
     * Blank for none.
     */
    fun label(stored: String?, locale: Locale = DateWords.appLocale()): String {
        val text = stored?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val index = KEYS.indexOf(text.lowercase(Locale.ROOT))
        if (index < 0) return text
        return LABELS[tableOf(locale)]?.get(index) ?: text
    }

    /** The list the owner picks from, in the app's language. */
    fun choices(locale: Locale = DateWords.appLocale()): List<String> =
        KEYS.map { label(it, locale) }

    /**
     * What to SAVE or COMPARE for a unit the owner typed or picked: the key
     * when the text is a key or any language's word for one, otherwise the
     * text itself, trimmed. Null for nothing typed.
     */
    fun canonical(typed: String?): String? {
        val text = typed?.trim().orEmpty()
        if (text.isEmpty()) return null
        return TO_KEY[fold(text)] ?: text
    }

    /** Every table and column a unit is saved in. */
    internal val STORED_IN = listOf(
        "transactions" to "unit",
        "entry_items" to "unit",
        "bill_items" to "unit",
        "invoice_items" to "unit",
        "products" to "defaultUnit",
        "schemes" to "unit"
    )

    /**
     * Turn units that were typed by hand as one of the app's own labels into
     * their key, once per opening of a book. One UPDATE per table, touching
     * only rows whose unit is exactly such a label; a key, an empty unit and
     * the owner's own words are not rows it matches. Each table on its own
     * and never fatal: a book must open whether or not this ran.
     *
     * English keys are deliberately not in the list: "bag" is already right,
     * and "Bag" typed by hand is left as the owner wrote it (the app has
     * always compared units without regard to letter case).
     */
    fun normalizeStored(db: SupportSQLiteDatabase) {
        val pairs = TO_KEY.filter { (label, key) -> label != key }
        if (pairs.isEmpty()) return
        val quoted = { s: String -> "'" + s.replace("'", "''") + "'" }
        val cases = pairs.entries.joinToString(" ") { (label, key) -> "WHEN ${quoted(label)} THEN ${quoted(key)}" }
        val labels = pairs.keys.joinToString(", ") { quoted(it) }
        for ((table, column) in STORED_IN) {
            runCatching {
                db.execSQL(
                    "UPDATE $table SET $column = CASE lower(trim($column)) $cases ELSE $column END " +
                        "WHERE $column IS NOT NULL AND lower(trim($column)) IN ($labels)"
                )
            }
        }
    }
}
