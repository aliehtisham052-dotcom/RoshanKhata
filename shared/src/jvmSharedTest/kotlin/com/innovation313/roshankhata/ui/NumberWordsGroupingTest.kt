package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Currency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The amount in words in the nine languages (24 Nov 2026, plan C5): the figures the plan names — 0, 1, 11, 19, 21, 99,
 * 100, 101, 1,000, 1,001, 99,999, 1,00,000, 1,000,000 — in every language, where the grouping differs (lakh/crore
 * against thousand/million), the decimals through [com.innovation313.roshankhata.data.Money.round], a negative and the
 * currency's word.
 *
 * What is held here is ARITHMETIC: which group a figure falls in, that no word between groups goes missing, that the
 * whole figure is spelled. The vocabulary of each table is somebody else's to check (a Sindhi or Arabic speaker), so
 * the expected strings were each read against the group they stand for before they were written down.
 *
 * Not held, said plainly: the singular ("One Rupees", "Ek Rupay") and Arabic's agreement of "two hundred thousand"
 * are grammar, not arithmetic, and are NOT asserted either way.
 */
class NumberWordsGroupingTest {

    private val before = Currency.symbol

    @After
    fun putTheSignBack() {
        Currency.symbol = before
    }

    private fun words(lang: String, v: Double): String {
        Currency.symbol = "Rs"
        return NumberWords.amountInWords(lang, v)
    }

    private val values = listOf(0.0, 1.0, 11.0, 19.0, 21.0, 99.0, 100.0, 101.0, 1000.0, 1001.0, 99999.0, 100000.0, 1000000.0)

    // ---- the exact words, per language, read against their groups ----

    private fun table(lang: String, expected: List<String>) {
        assertEquals(values.size, expected.size)
        for ((i, v) in values.withIndex()) {
            val got = words(lang, v)
            // 1 alone is checked by its number word: the singular of the unit is grammar, not arithmetic.
            if (v == 1.0) assertTrue("$lang 1 -> $got", got.startsWith(expected[i])) else assertEquals("$lang $v", expected[i], got)
        }
    }

    @Test
    fun englishCountsInLakh() = table(
        "en",
        listOf(
            "Zero Rupees", "One", "Eleven Rupees", "Nineteen Rupees", "Twenty-One Rupees", "Ninety-Nine Rupees",
            "One Hundred Rupees", "One Hundred One Rupees", "One Thousand Rupees", "One Thousand One Rupees",
            "Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees", "One Lakh Rupees", "Ten Lakh Rupees"
        )
    )

    @Test
    fun urduCountsInLakh() = table(
        "ur",
        listOf(
            "صفر روپے", "ایک", "گیارہ روپے", "انیس روپے", "اکیس روپے", "ننانوے روپے", "ایک سو روپے", "ایک سو ایک روپے",
            "ایک ہزار روپے", "ایک ہزار ایک روپے", "ننانوے ہزار نو سو ننانوے روپے", "ایک لاکھ روپے", "دس لاکھ روپے"
        )
    )

    @Test
    fun romanUrduCountsInLakh() = table(
        "ur-Latn",
        listOf(
            "Sifar Rupay", "Ek", "Gyarah Rupay", "Unnees Rupay", "Ikkees Rupay", "Ninyanwe Rupay", "Ek Sou Rupay",
            "Ek Sou Ek Rupay", "Ek Hazar Rupay", "Ek Hazar Ek Rupay", "Ninyanwe Hazar Nau Sou Ninyanwe Rupay",
            "Ek Lakh Rupay", "Dus Lakh Rupay"
        )
    )

    @Test
    fun hindiCountsInLakh() = table(
        "hi",
        listOf(
            "शून्य रुपये", "एक", "ग्यारह रुपये", "उन्नीस रुपये", "इक्कीस रुपये", "निन्यानवे रुपये", "एक सौ रुपये",
            "एक सौ एक रुपये", "एक हज़ार रुपये", "एक हज़ार एक रुपये", "निन्यानवे हज़ार नौ सौ निन्यानवे रुपये",
            "एक लाख रुपये", "दस लाख रुपये"
        )
    )

    @Test
    fun sindhiCountsInLakh() = table(
        "sd",
        listOf(
            "ٻڙي روپيا", "هڪ", "يارهن روپيا", "اوڻيهه روپيا", "ايڪيهه روپيا", "نوانوي روپيا", "هڪ سؤ روپيا", "هڪ سؤ هڪ روپيا",
            "هڪ هزار روپيا", "هڪ هزار هڪ روپيا", "نوانوي هزار نو سؤ نوانوي روپيا", "هڪ لک روپيا", "ڏهه لک روپيا"
        )
    )

    @Test
    fun bengaliCountsInLakh() = table(
        "bn",
        listOf(
            "শূন্য রুপি", "এক", "এগারো রুপি", "উনিশ রুপি", "একুশ রুপি", "নিরানব্বই রুপি", "এক শত রুপি", "এক শত এক রুপি",
            "এক হাজার রুপি", "এক হাজার এক রুপি", "নিরানব্বই হাজার নয় শত নিরানব্বই রুপি", "এক লক্ষ রুপি", "দশ লক্ষ রুপি"
        )
    )

    @Test
    fun indonesianCountsInRibuAndJuta() = table(
        "id",
        listOf(
            "Nol rupee", "Satu", "Sebelas rupee", "Sembilan belas rupee", "Dua puluh satu rupee", "Sembilan puluh sembilan rupee",
            "Seratus rupee", "Seratus satu rupee", "Seribu rupee", "Seribu satu rupee",
            "Sembilan puluh sembilan ribu sembilan ratus sembilan puluh sembilan rupee", "Seratus ribu rupee", "Satu juta rupee"
        )
    )

    @Test
    fun persianJoinsEveryPartWithAnd() = table(
        "fa",
        listOf(
            "صفر روپیه", "یک", "یازده روپیه", "نوزده روپیه", "بیست و یک روپیه", "نود و نه روپیه", "صد روپیه", "صد و یک روپیه",
            "هزار روپیه", "هزار و یک روپیه", "نود و نه هزار و نهصد و نود و نه روپیه", "صد هزار روپیه", "یک میلیون روپیه"
        )
    )

    @Test
    fun arabicJoinsEveryPartWithAnd() = table(
        "ar",
        listOf(
            "صفر روبية", "واحد", "أحد عشر روبية", "تسعة عشر روبية", "واحد وعشرون روبية", "تسعة وتسعون روبية", "مائة روبية",
            "مائة وواحد روبية", "ألف روبية", "ألف وواحد روبية", "تسعة وتسعون ألفًا وتسعمائة وتسعة وتسعون روبية",
            "مائة ألف روبية", "مليون روبية"
        )
    )

    // ---- the grouping, held without the vocabulary ----

    private val lakhLanguages = listOf("en", "ur", "ur-Latn", "hi", "sd", "bn")
    private val westernLanguages = listOf("id", "fa", "ar")

    @Test
    fun aLakhIsOneGroupAndAMillionIsTenOfThem() {
        for (lang in lakhLanguages) {
            val lakh = words(lang, 100_000.0)
            val tenLakh = words(lang, 1_000_000.0)
            // Two words and the unit, nothing of the thousands group, and the million is the lakh group with another number.
            assertEquals("$lang: $lakh", 3, lakh.split(" ").size)
            assertEquals("$lang: $tenLakh", 3, tenLakh.split(" ").size)
            assertEquals("$lang lakh word", lakh.split(" ")[1], tenLakh.split(" ")[1])
            assertNotEquals(lang, lakh.split(" ")[0], tenLakh.split(" ")[0])
            assertNotEquals(lang, words(lang, 99_999.0).split(" ")[1], lakh.split(" ")[1])
        }
    }

    @Test
    fun aMillionIsNotAGroupOfItsOwnInTheLakhLanguages() {
        // 12,34,56,789: crore, lakh, thousand, hundred, and the last two digits - each group once, in order.
        val g = words("en", 123_456_789.0)
        assertEquals("Twelve Crore Thirty-Four Lakh Fifty-Six Thousand Seven Hundred Eighty-Nine Rupees", g)
        assertFalse(g.contains("Million"))
    }

    @Test
    fun theWesternLanguagesGroupByThousands() {
        // 1,000,000 is one million and 100,000 is a hundred thousand: no lakh anywhere.
        assertEquals("Satu juta rupee", words("id", 1_000_000.0))
        assertEquals("Seratus ribu rupee", words("id", 100_000.0))
        assertEquals("یک میلیون روپیه", words("fa", 1_000_000.0))
        assertEquals("مليون روبية", words("ar", 1_000_000.0))
        assertEquals(
            "Seratus dua puluh tiga juta empat ratus lima puluh enam ribu tujuh ratus delapan puluh sembilan rupee",
            words("id", 123_456_789.0)
        )
        for (lang in westernLanguages) assertNotEquals(lang, words(lang, 100_000.0), words(lang, 1_000_000.0))
    }

    @Test
    fun nothingGoesMissingBetweenGroupsOneThousandOneAndOneHundredOne() {
        // The ones word after a group is appended, never lost: n + 1 is n's words with the number inserted before the unit.
        for (lang in lakhLanguages + westernLanguages) {
            for ((base, plusOne) in listOf(100.0 to 101.0, 1000.0 to 1001.0)) {
                val unit = words(lang, 0.0).substringAfterLast(' ')
                val a = words(lang, base).removeSuffix(" $unit")
                val b = words(lang, plusOne).removeSuffix(" $unit")
                assertTrue("$lang: $b should extend $a", b.length > a.length)
                assertTrue("$lang: $b should start with $a", b.startsWith(a))
            }
        }
    }

    // ---- crores past 99 (24 Nov 2026: the count was capped at 99 and printed a wrong figure) ----

    @Test
    fun ninetyNineCroreIsTheLastOneWordCount() {
        assertEquals(
            "Ninety-Nine Crore Ninety-Nine Lakh Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees",
            words("en", 999_999_999.0)
        )
    }

    @Test
    fun aHundredCroreAndMoreAreSpelledNotCapped() {
        assertEquals("One Hundred Crore Rupees", words("en", 1_000_000_000.0))
        assertEquals("One Hundred Fifty Crore Rupees", words("en", 1_500_000_000.0))
        assertEquals("Ek Sou Crore Rupay", words("ur-Latn", 1_000_000_000.0))
        assertEquals("एक सौ करोड़ रुपये", words("hi", 1_000_000_000.0))
        assertEquals("ایک سو کروڑ روپے", words("ur", 1_000_000_000.0))
        assertEquals("One Thousand Crore Rupees", words("en", 10_000_000_000.0))
        // 1,00,000 crore is one lakh crore.
        assertEquals("One Lakh Crore Rupees", words("en", 1_000_000_000_000.0))
        // 12,345 crore and 60 lakh: the count past 99 and the groups under a crore both kept.
        assertEquals("Twelve Thousand Three Hundred Forty-Five Crore Sixty Lakh Rupees", words("en", 123_456_000_000.0))
    }

    @Test
    fun aHundredCroreIsNotNinetyNineCroreInAnyLakhLanguage() {
        for (lang in lakhLanguages) assertNotEquals(lang, words(lang, 999_999_999.0).substringBefore(" "), words(lang, 1_000_000_000.0).substringBefore(" "))
        // And a figure with a count over 99 keeps what is under a crore.
        assertTrue(words("en", 1_500_000_000.0).endsWith("Rupees"))
        assertTrue(words("en", 1_000_000_001.0).startsWith("One Hundred Crore One "))
    }

    // ---- decimals: the figure is rounded as Format.money rounds, then the fraction is left off ----

    @Test
    fun theFractionIsDroppedAfterTheRoundingNotBefore() {
        // Whole rupees only: 0.50 is "Zero" in words (the digits beside it say 0.50), 100.75 is a hundred.
        assertEquals("Zero Rupees", words("en", 0.50))
        assertEquals("One Hundred Rupees", words("en", 100.75))
        // 229.995 is 230.00 by half-up on its decimal text (Money.round), so the words say 230, not 229.
        assertEquals("Two Hundred Thirty Rupees", words("en", 229.995))
        assertEquals("Two Hundred Thirty Rupees", words("en", 229.99999999999997))
        // 1.005 is 1.01 as the screen prints it: one rupee and a fraction.
        assertTrue(words("en", 1.005).startsWith("One "))
        assertTrue(words("en", 0.995).startsWith("One "))
        assertEquals("Zero Rupees", words("en", 0.994))
    }

    @Test
    fun theRoundingFollowsTheShopsOwnCoin() {
        Currency.symbol = "KWD"
        // Three decimals: 0.9996 rounds up to 1.000, 0.9994 stays 0.999 and is zero whole dinars.
        assertEquals("One KWD", NumberWords.amountInWords("en", 0.9996))
        assertEquals("Zero KWD", NumberWords.amountInWords("en", 0.9994))
        Currency.symbol = "Rp"
        // No decimals: 1000.5 is 1001 (half up), 1000.4 is 1000.
        assertEquals("Seribu satu rupiah", NumberWords.amountInWords("id", 1000.5))
        assertEquals("Seribu rupiah", NumberWords.amountInWords("id", 1000.4))
    }

    @Test
    fun aNegativeIsZeroInEveryLanguageNeverACrash() {
        for (lang in lakhLanguages + westernLanguages) assertEquals(lang, words(lang, 0.0), words(lang, -500.0))
        Currency.symbol = "AED"
        assertEquals("Zero AED", NumberWords.amountInWords("en", -1.0))
        // Not a number or infinity: spelled as zero, no exception.
        Currency.symbol = "Rs"
        assertEquals("Zero Rupees", NumberWords.amountInWords("en", Double.NaN))
    }

    // ---- the currency's word ----

    @Test
    fun theCurrencyWordFollowsTheShopsSignInEveryLanguage() {
        Currency.symbol = "Rs"
        assertTrue(NumberWords.amountInWords("en", 5200.0).endsWith("Rupees"))
        assertEquals("Lima ribu dua ratus rupee", NumberWords.amountInWords("id", 5200.0))
        Currency.symbol = "Rp"
        assertEquals("Lima ribu dua ratus rupiah", NumberWords.amountInWords("id", 5200.0))
        Currency.symbol = "৳"
        assertEquals("পাঁচ হাজার দুই শত টাকা", NumberWords.amountInWords("bn", 5200.0))
        assertEquals("Five Thousand Two Hundred Taka", NumberWords.amountInWords("en", 5200.0))
        assertEquals("Lima ribu dua ratus taka", NumberWords.amountInWords("id", 5200.0))
        Currency.symbol = "USD"
        // A custom sign that is no choice is printed as typed, after the words.
        assertEquals("Five Thousand Two Hundred USD", NumberWords.amountInWords("en", 5200.0))
        Currency.symbol = "$"
        // "$" is USD's sign in the picker: the code is the word.
        assertEquals("Five Thousand Two Hundred USD", NumberWords.amountInWords("en", 5200.0))
        assertEquals("Lima ribu dua ratus USD", NumberWords.amountInWords("id", 5200.0))
        assertEquals("پنج هزار و دویست USD", NumberWords.amountInWords("fa", 5200.0))
        assertEquals("خمسة آلاف ومائتان USD", NumberWords.amountInWords("ar", 5200.0))
        assertEquals("پانچ ہزار دو سو USD", NumberWords.amountInWords("ur", 5200.0))
    }

    @Test
    fun aGulfShopCountsInThousandsAndMillionsInEnglishAndInLakhsInUrdu() {
        Currency.symbol = "AED"
        // English: the Western groups, since "Lakh Dirham" is not how a Gulf shop reads a figure.
        assertEquals("One Million Two Hundred Thirty-Four Thousand Five Hundred Sixty-Seven AED", NumberWords.amountInWords("en", 1_234_567.0))
        // Urdu / Hindi / Roman Urdu keep their own lakh counting and end in the code.
        assertEquals("Barah Lakh Chauntees Hazar Paanch Sou Sarsath AED", NumberWords.amountInWords("ur-Latn", 1_234_567.0))
    }
}
