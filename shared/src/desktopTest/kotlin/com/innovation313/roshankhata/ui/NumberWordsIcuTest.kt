package com.innovation313.roshankhata.ui

import com.ibm.icu.text.RuleBasedNumberFormat
import com.ibm.icu.util.ULocale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The amount in words on an invoice ([NumberWords]) held to ICU's spellout rules — CLDR's own data, the words every
 * operating system's number speller uses — wherever ICU has the language (25 Nov 2026, plan item C5). ICU4J is a test
 * dependency only (shared/build.gradle.kts).
 *
 * The numbers: 0 to 1,000, every thousand to 10,000, 99,999, lakhs and crores up to 99,99,99,999 and, where both sides
 * count the same way above it, 100 crore and more.
 *
 * Only whitespace and the choices written below are normalised; each is a spelling or a style both forms of which are
 * in use, not a wrong word. A word that differs in any other way fails, named with its number.
 *  - English: ICU's en-IN (lakh and crore, as ours), compared in lower case. Our Western groups ([NumberWords.spellWestern])
 *    are held to ICU's plain en.
 *  - Indonesian: ICU's id, in lower case (ours starts the sentence with a capital).
 *  - Persian: ours says one thousand as هزار alone, ICU as یک هزار (both are written; NumberWords' own note).
 *  - Hindi: below 100 crore. Fourteen of ICU's spellings differ from ours (पन्द्रह for पंद्रह, the -नबे nineties for -नवे,
 *    चौवालीस, तिरेपन, तिरेसठ, उनासी); both forms are in use. From 100 crore ICU says अरब, ours goes on counting crores
 *    (एक सौ करोड़) — NumberWords' rule since 24 Nov 2026.
 *  - Urdu and Bengali: ICU4J 77.1 has no spellout rules for them (CLDR added ur and bn after release 48); these tests are
 *    skipped until ICU4J carries them, and then compare as written here. They were compared by hand on 25 Nov 2026
 *    against CLDR main's common/rbnf/ur.xml and bn.xml: every number here agrees after the variants below, and one word
 *    was wrong in ours — Urdu 26, چھببیس, now چھبیس. Urdu variants kept: تیئس, ترپن, ترسٹھ, چوسٹھ, اناسی. Bengali: ours
 *    writes hundreds as "<n> শত" and lakh as লক্ষ (CLDR "<n>শো", লাখ) and thirteen spellings of 20-89 otherwise.
 *  - Arabic: 0 to 999 and the round thousands 1,000 and 3,000 to 10,000. ICU's Arabic spellout is not the standard form in
 *    four places, so it is read in ours: اثنان (ICU writes a hamza, إثنان, on what is a hamzat al-wasl), the masculine
 *    أحد عشر (ICU the feminine إحدى عشر beside masculine 13-19), hundreds as one word (ثلاثمائة; ICU ثلاثة مائة). Above
 *    a thousand ICU gives no case endings (ألفين, أحد عشر ألف) where ours reads a figure aloud in the nominative with the
 *    accusative after 11-99 (ألفان, أحد عشر ألفًا); those are left out of the comparison, not called right or wrong here.
 *  - Sindhi and Roman Urdu: no CLDR or ICU spellout exists; not compared here (Sindhi's source is NumberWords' own note).
 */
class NumberWordsIcuTest {

    private val numbers: List<Long> = buildList {
        addAll(0L..1_000L)
        for (t in 2_000L..10_000L step 1_000L) add(t)
        addAll(listOf(99_999L, 100_000L, 150_000L, 1_234_567L, 2_500_000L, 9_999_999L, 10_000_000L, 25_000_000L,
            99_999_999L, 123_456_789L, 999_999_999L))
    }
    private val beyond = listOf(1_000_000_000L, 1_500_000_000L, 12_345_678_901L)

    private fun icu(tag: String): RuleBasedNumberFormat = RuleBasedNumberFormat(ULocale.forLanguageTag(tag), RuleBasedNumberFormat.SPELLOUT)

    private fun spell(f: RuleBasedNumberFormat, n: Long): String = words(f.format(n, "%spellout-numbering"))

    private fun words(s: String): String = s.replace(' ', ' ').trim().split(Regex("\\s+")).joinToString(" ")

    /** Ours without the currency word it ends in. */
    private fun ours(s: String, unit: String): String = words(s).removeSuffix(unit).trim()

    private fun each(word: (String) -> String, s: String): String = s.split(' ').joinToString(" ") { word(it) }

    private fun compare(what: String, ns: List<Long>, ours: (Long) -> String, theirs: (Long) -> String) {
        val wrong = ns.mapNotNull { n ->
            val a = ours(n); val b = theirs(n)
            if (a == b) null else "$n: ours \"$a\", ICU \"$b\""
        }
        assertTrue("$what: ${wrong.size} of ${ns.size} differ:\n" + wrong.take(25).joinToString("\n"), wrong.isEmpty())
    }

    /** Whether this ICU4J has spellout rules for [tag]: the word for five is [five] there and not a figure. */
    private fun icuHas(tag: String, five: String): Boolean = try {
        spell(icu(tag), 5) == five
    } catch (e: IllegalArgumentException) {
        false
    }

    @Test
    fun `English in lakh and crore is ICU's en-IN`() {
        val f = icu("en-IN")
        compare("en-IN", numbers + beyond, { ours(NumberWords.rupeesInWordsEnglish(it.toDouble()), "Rupees").lowercase() },
            { spell(f, it).lowercase() })
    }

    @Test
    fun `English in thousands and millions is ICU's en`() {
        val f = icu("en")
        compare("en", numbers + beyond, { ours(NumberWords.spellWestern(it.toDouble(), "U"), "U").lowercase() },
            { spell(f, it).lowercase() })
    }

    @Test
    fun `Indonesian is ICU's id`() {
        val f = icu("id")
        compare("id", numbers + beyond, { ours(NumberWords.inWordsIndonesian(it.toDouble(), "U"), "U").lowercase() },
            { spell(f, it).lowercase() })
    }

    @Test
    fun `Persian is ICU's fa, one thousand said alone`() {
        val f = icu("fa")
        val oneThousand = Regex("(^|(میلیون|میلیارد) و )یک هزار")
        compare("fa", numbers + beyond, { ours(NumberWords.inWordsPersian(it.toDouble(), "U"), "U") },
            { spell(f, it).replace(oneThousand, "$1هزار") })
    }

    @Test
    fun `Hindi is ICU's hi below a hundred crore`() {
        val f = icu("hi")
        compare("hi", numbers, { each({ w -> HI_VARIANTS[w] ?: w }, ours(NumberWords.rupeesInWordsHindi(it.toDouble()), "रुपये")) },
            { spell(f, it) })
    }

    @Test
    fun `Urdu is CLDR's ur below a hundred crore, once ICU4J has it`() {
        assumeTrue("ICU4J has no Urdu spellout yet (CLDR added it after release 48)", icuHas("ur", "پانچ"))
        val f = icu("ur")
        compare("ur", numbers, { each({ w -> UR_VARIANTS[w] ?: w }, ours(NumberWords.rupeesInWords("ur", it.toDouble()), "روپے")) },
            { spell(f, it) })
    }

    @Test
    fun `Bengali is CLDR's bn below a hundred crore, once ICU4J has it`() {
        assumeTrue("ICU4J has no Bengali spellout yet (CLDR added it after release 48)", icuHas("bn", "পাঁচ"))
        val f = icu("bn")
        val hundreds = Regex("(\\S+) শত")
        compare("bn", numbers, {
            each({ w -> BN_VARIANTS[w] ?: w }, ours(NumberWords.inWordsBengali(it.toDouble(), "U"), "U"))
                .replace(hundreds, "$1শো").replace("লক্ষ", "লাখ")
        }, { spell(f, it) })
    }

    @Test
    fun `Arabic is ICU's ar to 999 and in round thousands, read in the standard forms`() {
        val f = icu("ar")
        val ns = numbers.filter { it <= 999 || (it in 1_000L..10_000L && it % 1_000L == 0L && it != 2_000L) }
        compare("ar", ns, { ours(NumberWords.inWordsArabic(it.toDouble(), "U"), "U") }, { n ->
            var s = spell(f, n).replace("إثن", "اثن").replace("إحدى عشر", "أحد عشر")
            for ((icuForm, standard) in AR_HUNDREDS) s = s.replace(icuForm, standard)
            s
        })
    }

    @Test
    fun `the Urdu 26 is the dictionary's word, everywhere it is spelled`() {
        // The fault this comparison found (25 Nov 2026): a doubled ب. Held here too, so it is held while ICU4J has no Urdu.
        assertTrue(NumberWords.rupeesInWords("ur", 26.0) == "چھبیس روپے")
        assertTrue(NumberWords.rupeesInWords("ur", 126_026.0).contains("چھبیس ہزار") && !NumberWords.rupeesInWords("ur", 126_026.0).contains("چھببیس"))
    }

    companion object {
        /** Ours to ICU's: spellings of the same Hindi word, both in use. */
        val HI_VARIANTS = mapOf(
            "पंद्रह" to "पन्द्रह", "चवालीस" to "चौवालीस", "तिरपन" to "तिरेपन", "तिरसठ" to "तिरेसठ", "उन्यासी" to "उनासी",
            "इक्यानवे" to "इक्यानबे", "बानवे" to "बानबे", "तिरानवे" to "तिरानबे", "चौरानवे" to "चौरानबे", "पंचानवे" to "पंचानबे",
            "छियानवे" to "छियानबे", "सत्तानवे" to "सत्तानबे", "अट्ठानवे" to "अट्ठानबे", "निन्यानवे" to "निन्यानबे"
        )

        /** Ours to CLDR's: Urdu spellings in use beside CLDR's. */
        val UR_VARIANTS = mapOf("تیئس" to "تئیس", "ترپن" to "تریپن", "ترسٹھ" to "تریسٹھ", "چوسٹھ" to "چونسٹھ", "اناسی" to "انیاسی")

        /** Ours to CLDR's: Bengali spellings of 20-89 in use beside CLDR's. */
        val BN_VARIANTS = mapOf(
            "বিশ" to "কুড়ি", "আটাশ" to "আঠাশ", "ঊনত্রিশ" to "উনত্রিশ", "ঊনচল্লিশ" to "উনচল্লিশ", "ঊনপঞ্চাশ" to "উনপঞ্চাশ",
            "বাহান্ন" to "বায়ান্ন", "তিপ্পান্ন" to "তিপান্ন", "ছাপ্পান্ন" to "ছাপান্ন", "ঊনষাট" to "উনষাট", "ঊনসত্তর" to "উনসত্তর",
            "ঊনআশি" to "উনআশি", "আটাশি" to "অষ্টআশি", "ঊননব্বই" to "উননব্বই"
        )

        /** ICU's Arabic hundreds, written as two words, to the standard one word. */
        val AR_HUNDREDS = listOf(
            "ثلاثة مائة" to "ثلاثمائة", "أربعة مائة" to "أربعمائة", "خمسة مائة" to "خمسمائة", "ستة مائة" to "ستمائة",
            "سبعة مائة" to "سبعمائة", "ثمانية مائة" to "ثمانمائة", "تسعة مائة" to "تسعمائة"
        )
    }
}
