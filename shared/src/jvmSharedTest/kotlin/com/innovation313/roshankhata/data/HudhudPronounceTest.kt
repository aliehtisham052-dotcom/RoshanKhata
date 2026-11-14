package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudPronounce.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Words a phone's voice reads wrongly, put right for the ear (14 Nov 2026, the owner: "Alhamdulillah ki pronunciation sahi
 * nahi" and "bikri ko bakri parh deta hai"). What the engine hears changes; the card's own words never do.
 */
class HudhudPronounceTest {

    @Test fun `Alhamdulillah is respelt for each language's voice`() {
        assertEquals("Al-ham-doo-lil-laah, all is well.", HudhudPronounce.apply("Alhamdulillah, all is well.", "en"))
        assertEquals("Al-ham-doo-lil-laah", HudhudPronounce.apply("alhamdulillah", "en"))
        assertEquals("Al-ham-doo-lil-laah", HudhudPronounce.apply("Al-hamdu lillah", "en"))
        val ur = HudhudPronounce.ways(Word.ALHAMDULILLAH, "ur").first()
        val ar = HudhudPronounce.ways(Word.ALHAMDULILLAH, "ar").first()
        assertEquals("$ur، سب ٹھیک ہے", HudhudPronounce.apply("الحمدللہ، سب ٹھیک ہے", "ur"))
        assertEquals("$ur، سب ٹھیک ہے", HudhudPronounce.apply("الحمد للہ، سب ٹھیک ہے", "ur"))
        assertEquals("$ar، كل شيء بخير", HudhudPronounce.apply("الحمد لله، كل شيء بخير", "ar"))
        assertEquals("अल्हम्दुलिल्लाह, सब ठीक है", HudhudPronounce.apply("अल्हम्दुलिल्लाह, सब ठीक है", "hi"))
        assertEquals("Alhamdulillah, semua baik", HudhudPronounce.apply("Alhamdulillah, semua baik", "in"))
    }

    @Test fun `a spelling with the Quran's marks is found too`() {
        val ur = HudhudPronounce.ways(Word.ALHAMDULILLAH, "ur").first()
        assertEquals("$ur رب العالمین", HudhudPronounce.apply("ٱلْحَمْدُ لِلَّهِ رب العالمین", "ur"))
        assertEquals(ur, HudhudPronounce.apply("الْحَمْدُ لِلّٰہِ", "ur"))
    }

    @Test fun `the way the owner chose is the one said`() {
        for (language in listOf("en", "ur", "ar", "fa", "hi", "bn", "in")) {
            val ways = HudhudPronounce.ways(Word.ALHAMDULILLAH, language)
            assertEquals("$language has three ways", 3, ways.size)
            assertEquals(3, ways.toSet().size)
        }
        assertEquals(HudhudPronounce.ways(Word.ALHAMDULILLAH, "ur")[1], HudhudPronounce.apply("الحمدللہ", "ur") { 1 })
        assertEquals("Alhamdu lillaah", HudhudPronounce.apply("Alhamdulillah", "en") { 1 })
        // A way the language does not have falls back to its first.
        assertEquals("Al-ham-doo-lil-laah", HudhudPronounce.apply("Alhamdulillah", "en") { 9 })
    }

    @Test fun `Sindhi is left as written`() {
        val s = "الحمدلله، سڀ ٺيڪ آهي"
        assertEquals(s, HudhudPronounce.apply(s, "sd"))
    }

    @Test fun `bikri is said as farokht, the sale and not the goat`() {
        assertEquals("کل کی فروخت", HudhudPronounce.apply("کل کی بکری", "ur"))
        assertEquals("اس ہفتے کی فروخت", HudhudPronounce.apply("اس ہفتے کی بکری", "ur"))
        assertEquals("فروخت اور منافع: آج، کل", HudhudPronounce.apply("بکری اور منافع: آج، کل", "ur"))
        assertEquals("کل کی فروخت", HudhudPronounce.apply("کل کی بِکری", "ur"))
    }

    @Test fun `a goat or a customer called so is left alone`() {
        assertEquals("ایک بکری خریدی", HudhudPronounce.apply("ایک بکری خریدی", "ur"))
        assertEquals("محمد بکری", HudhudPronounce.apply("محمد بکری", "ur"))
        // Only Urdu: in any other language the word is not the sale.
        assertEquals("کل کی بکری", HudhudPronounce.apply("کل کی بکری", "sd"))
    }

    @Test fun `the Prophet's honour is said in full in the language's letters`() {
        assertEquals("حضرت محمد صلی اللہ علیہ وسلم", HudhudPronounce.apply("حضرت محمد ﷺ", "ur"))
        assertEquals("النبي محمد صلى الله عليه وسلم", HudhudPronounce.apply("النبي محمد ﷺ", "ar"))
        assertEquals("Prophet Muhammad sallallahu alaihi wasallam", HudhudPronounce.apply("Prophet Muhammad ﷺ", "en"))
        assertEquals("नबी मुहम्मद सल्लल्लाहु अलैहि व सल्लम", HudhudPronounce.apply("नबी मुहम्मद ﷺ", "hi"))
        assertTrue(HudhudPronounce.apply("নবী মুহাম্মদ ﷺ", "bn").endsWith("সাল্লাল্লাহু আলাইহি ওয়াসাল্লাম"))
    }

    @Test fun `the developer's name is said the way it is pronounced by an English voice`() {
        assertEquals("The developer is Ihtishaam Ali.", HudhudPronounce.apply("The developer is Ehtisham Ali.", "en"))
        assertNotEquals("Ihtishaam", HudhudPronounce.apply("Ehtisham", "in"))
    }

    @Test fun `any shop's currency is said in words through HudhudSay`() {
        val aed: (String) -> String = { digits -> "$digits dirhams" }
        assertEquals("Today's sale is 1250 dirhams.", HudhudSay.forVoice("Today's sale is AED 1,250.", "en", rupees = aed, signs = listOf("AED")))
        assertEquals("Sale 40 euros", HudhudSay.forVoice("Sale € 40", "en", rupees = { "$it euros" }, signs = listOf("€")))
        assertEquals("Total 12.50 dollars", HudhudSay.forVoice("Total \$ 12.50", "en", rupees = { "$it dollars" }, signs = listOf("\$")))
        // A shop of rupees is unchanged, and a sign the shop does not use is left as written.
        assertEquals("140868 rupees", HudhudSay.forVoice("Rs 140,868", "en", rupees = { "$it rupees" }))
        assertEquals("AED 1250", HudhudSay.forVoice("AED 1,250", "en", rupees = { "$it rupees" }))
        // A word that only ends in the sign is not a price.
        assertEquals("CAED 5", HudhudSay.forVoice("CAED 5", "en", rupees = aed, signs = listOf("AED")))
        // "Rs" must not match inside the sign "R".
        assertEquals("Rs 500", HudhudSay.forVoice("Rs 500", "en", rupees = { "$it rand" }, signs = listOf("R")))
    }
}
