package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A product saved in Latin letters and named in another script (25 Nov 2026, Marhala 11, BC-350..352). The baseline's entity
 * table had the product "missing" 17 times in Urdu script and 13 in Hindi on review set O, and 0 in Roman Urdu and English:
 * the book's "Urea" was "یوریا", "यूरिया" and "ইউরিয়া" in the question, and the name bridge, which serves the goods as it
 * serves the people, did not meet them. Two glides those scripts write and a Latin spelling does not:
 *  - a first "u" said "you" is written with a "y" before it: "یو" (Urdu, Sindhi, Persian, Arabic), "यू" (Hindi), "ইউ"
 *    (Bengali). The Arabic-script matcher read that "ی" as a "y" the Latin spelling lacks ([HudhudNameBridge.meetsYouGlide]);
 *  - Hindi and Bengali write a "y" between two vowels ("-iya" for "-ia" and "-ea"), and the search folds a Latin "ea" as one
 *    long "i" ("Urea" is "uri"), so "यूरिया" ("yuriya") met neither (`Hudhud.indicGlides`, `Form.eaApart`).
 * The same reading serves a person's name: Bengali "ইউসুফ" is Yusuf, Hindi "शाज़िया" Shazia (the "accepted" gap of lesson 136).
 *
 * What must stay unmet matters as much: a product read must never become another product. The glide is read only against a
 * Latin spelling that begins with "u" (or "eu"), only with three letters or more after it, never for a word the language
 * taught ("یوں", "یوم"), and it never makes a consonant vanish: "Aliya" is not "Ali".
 *
 * Every product name here is invented (lesson 80; "Ulora", "Kalorea", "Umo", "Olora", "Lora Plus" are nobody's goods), the
 * people's names are common given names. The sentences are written by the maker of the tests, not by a native reader: the
 * Sindhi, Persian, Arabic and Bengali lines are marked for a native read.
 */
class HudhudGoodsScriptsTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val ulora = Hudhud.Name(201, "Ulora")
    private val kalorea = Hudhud.Name(202, "Kalorea")
    private val goods = listOf(ulora, kalorea, Hudhud.Name(203, "Sugar"))
    private val people = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"))

    /** The products a sentence's words meet across scripts, as the name bridge reads them. */
    private fun met(said: String, book: List<Hudhud.Name> = goods): List<Long> = Hudhud.acrossScriptsOf(said, book)

    /** The products the whole reading carries (what the shopkeeper is told about). */
    private fun carried(said: String, language: String, book: List<Hudhud.Name> = goods): Set<Long> =
        HudhudEntities.of(Hudhud.understand(said, people, emptyList(), language, goods = book)).goods

    // ---- the "you" glide before a first "u" ----

    @Test fun `a product that begins with a u said you is met by its spelling in every script`() {
        val spellings = listOf(
            "ur" to "یولورا",
            "sd" to "يولورا",      // native read: Sindhi writes the Arabic yeh
            "fa" to "یولورا",      // native read
            "ar" to "يولورا",      // native read
            "hi" to "यूलोरा",
            "bn" to "ইউলোরা",      // native read
        )
        for ((lang, w) in spellings) assertEquals("$lang: $w", listOf(201L), met(w))
    }

    @Test fun `a rate or stock question names the product in each script`() {
        val asks = listOf(
            "ur" to "یولورا کا ریٹ کیا ہے",
            "ur" to "یولورا کتنی پڑی ہے",
            "hi" to "यूलोरा का रेट क्या है",
            "hi" to "यूलोरा कितनी पड़ी है",
            "bn" to "ইউলোরার দাম কত",                  // native read
            "ar" to "كم سعر يولورا",                    // native read
            "fa" to "قیمت یولورا چند است",               // native read
            "sd" to "يولورا جي قيمت ڪيتري آهي",          // native read
        )
        for ((lang, s) in asks) assertEquals("$lang: $s", setOf(201L), carried(s, lang))
    }

    @Test fun `the glide is the bridge's own rule, asked apart from the plain letters`() {
        val a = HudhudNameBridge.arabicLetters("یولورا")
        assertFalse("the plain rules read the first ی as a y", HudhudNameBridge.meetsLetters(a, "ulora"))
        assertTrue(HudhudNameBridge.meetsYouGlide(a, "ulora"))
        assertTrue("Euro's eu is the same sound", HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("یورو"), "euro"))
        // Only a Latin spelling that begins with the u: never one that begins with another vowel or a consonant.
        assertFalse(HudhudNameBridge.meetsYouGlide(a, "olora"))
        assertFalse(HudhudNameBridge.meetsYouGlide(a, "lora"))
        // Only a word that begins with the glide.
        assertFalse(HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("ولورا"), "ulora"))
        // Three letters or more after it: "یوں" (thus) and "یوم" (day) are never "Un" or "Uma".
        assertFalse(HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("یوں"), "un"))
        assertFalse(HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("يوم"), "uma"))
        // Every consonant still counted: "یولورا" is not "Ulara" nor "Ulorak".
        assertFalse(HudhudNameBridge.meetsYouGlide(a, "ulara"))
        assertFalse(HudhudNameBridge.meetsYouGlide(a, "ulorak"))
    }

    // ---- the "y" between two vowels, and the Latin "ea" said as two sounds ----

    @Test fun `a product whose Latin ea is two sounds is met by its iya in Hindi and Bengali`() {
        assertEquals(listOf(202L), met("कलोरिया"))
        assertEquals(listOf(202L), met("কলোরিয়া"))    // native read
        assertEquals(listOf(202L), met("کلوریا"))     // the Arabic script met it before; it still does
        assertEquals(setOf(202L), carried("कलोरिया का रेट क्या है", "hi"))
    }

    @Test fun `the same glides name a person`() {
        val book = listOf(Hudhud.Name(7, "Shazia Bibi"), Hudhud.Name(8, "Yusuf Khan"), Hudhud.Name(1, "Ali Raza"))
        assertEquals(listOf(7L), met("शाज़िया", book))
        assertEquals(listOf(8L), met("ইউসুফ", book))  // native read: Bengali writes Yusuf's y as ই
    }

    // ---- never another product, never a word of the language ----

    @Test fun `a product read never becomes another product`() {
        // "Lora Plus" and "Olora" are not "یولورا": a choice or nothing, never the other one.
        val book = listOf(ulora, Hudhud.Name(204, "Lora Plus"), Hudhud.Name(205, "Olora"))
        for (w in listOf("یولورا", "يولورا", "यूलोरा", "ইউলোরা")) assertEquals(w, listOf(201L), met(w, book))
        // Without Ulora in the book nothing is named: missing, never a near one.
        val without = listOf(Hudhud.Name(204, "Lora Plus"), Hudhud.Name(205, "Olora"))
        for (w in listOf("یولورا", "يولورا", "यूलोरा", "ইউলোরা")) assertEquals(w, emptyList<Long>(), met(w, without))
        // Two products the glide and the plain y both meet are a choice, never one of them picked.
        val both = listOf(ulora, Hudhud.Name(206, "Yulora"))
        assertEquals(listOf(201L, 206L), met("یولورا", both).sorted())
    }

    @Test fun `one product under several names in several scripts is met once`() {
        // The product's other names carry its one id: the Latin word now meets the Urdu and Hindi names as well, and must not
        // make a choice between the product and itself (BC-352).
        val book = listOf(ulora, Hudhud.Name(201, "یولورا"), Hudhud.Name(201, "यूलोरा"), Hudhud.Name(209, "Sugar"))
        for ((lang, s) in listOf("ur-Latn" to "Ulora ka rate kya hai", "ur" to "یولورا کا ریٹ کیا ہے", "hi" to "यूलोरा का रेट क्या है")) {
            assertEquals("$lang: $s", Ask.Goods(listOf(201L), Hudhud.GoodsFacet.PRICE),
                Hudhud.understand(s, people, emptyList(), lang, goods = book))
        }
        assertEquals(listOf(201L), met("Ulora", book))
    }

    @Test fun `the language's own words keep away from a u product`() {
        val book = listOf(Hudhud.Name(207, "Umo"), Hudhud.Name(208, "Unani Oil"))
        assertEquals(emptyList<Long>(), met("یوں ہی بتاؤ", book))
        assertEquals(emptyList<Long>(), met("كم بعنا يوم الاثنين", book))   // native read
        assertEquals(emptyList<Long>(), met("यूं ही बताओ", book))
        // Arabic's verbs that begin "يو" write no vowel after it: "هل يوجد سكر" (is there sugar) never names a product "Ujad".
        val verbs = listOf(Hudhud.Name(211, "Ujad"), Hudhud.Name(212, "Ulad Plus"))
        assertEquals(emptyList<Long>(), met("هل يوجد سكر", verbs))   // native read
        assertEquals(emptyList<Long>(), met("متى يولد", verbs))       // native read
        assertFalse(HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("يوجد"), "ujad"))
        assertTrue("a written vowel after the glide is a loanword", HudhudNameBridge.meetsYouGlide(HudhudNameBridge.arabicLetters("یوجاد"), "ujad"))
    }

    @Test fun `a glide never makes a letter vanish that the Latin spelling needs`() {
        // Hindi "अलिया" (Aliya) is not Ali: the "a" after the glide is still there to be met.
        val book = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(3, "Ali Khan"))
        assertEquals(emptyList<Long>(), met("अलिया", book))
        assertEquals(emptyList<Long>(), met("আলিয়া", book))   // native read
        // Only a y between two vowels is a glide: after a consonant it is a letter, so "सूर्या" (Surya) is not "Sura".
        assertEquals(emptyList<Long>(), met("सूर्या", listOf(Hudhud.Name(210, "Sura Gold"))))
        // The plain y of a Latin spelling still meets it ("Riyaz" keeps its y).
        assertEquals(listOf(9L), met("रियाज़", listOf(Hudhud.Name(9, "Riyaz Ahmad"))))
    }
}
