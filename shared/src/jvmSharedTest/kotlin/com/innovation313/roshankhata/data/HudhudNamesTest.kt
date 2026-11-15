package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A customer saved in Roman letters and asked for in Hindi or Bengali (15 Nov 2026). Those two scripts write every vowel but the
 * inherent "a", so "बशीर" is "bsir" to the name search and the book's "Bashir" is "basir": nothing met, and every ask about
 * the man — his number, his balance, his last payment, his limit, his private note, an entry said by voice — read as if no
 * name had been said ("whose?", the whole book's balance, or a jump). Urdu, Sindhi, Arabic and Persian had the bridge since
 * 13 Nov ([HudhudMindTest]); [Hudhud]'s `acrossScripts` now serves the Indic scripts too, with their own looseness (one vowel for
 * another, z for j, w for b).
 *
 * Every sentence goes through [Hudhud.understand] with the shipped sense, as the app does. The sentences are written by the
 * maker of the tests, not by a native reader of each language (said plainly in the notes), and every name is made up (lesson 80).
 * What the bridge must refuse matters as much as what it finds — a wrong name is somebody else's balance told — so the second half
 * of the class is what stays unnamed: "Bushra" is not "बशीर", a word of the question is not a name whose letters are the same, a
 * name the book lacks is not turned into another.
 */
class HudhudNamesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val book = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Ahmad Khan"),
        Hudhud.Name(5, "Muhammad Waqas"), Hudhud.Name(8, "Bushra Bibi"), Hudhud.Name(9, "Nambar Khan")
    )
    private val withoutBashir = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(8, "Bushra Bibi"), Hudhud.Name(9, "Nambar Khan"))
    private val goods = listOf(Hudhud.Name(100, "Urea"))

    private fun read(s: String, language: String?, names: List<Hudhud.Name> = book) =
        Hudhud.understand(s, names, emptyList(), language, goods = goods)

    private fun person(facet: Facet, vararg ids: Long) = Ask.Person(ids.toList(), facet)

    private fun check(expected: Ask, language: String?, vararg said: String, names: List<Hudhud.Name> = book) {
        for (s in said) assertEquals("$language: $s", expected, read(s, language, names))
    }

    // ------------------------------------------------------------------------------------ Hindi

    @Test fun `Hindi - his number, balance, last payment, limit and note, his name saved in Roman letters`() {
        check(person(Facet.PHONE, 2), "hi", "बशीर अहमद का नंबर क्या है", "बशीर का नंबर")
        check(Ask.Parties(listOf(2L)), "hi", "बशीर अहमद का हिसाब")
        check(person(Facet.LAST_PAYMENT, 2), "hi", "बशीर अहमद की आखिरी पेमेंट कब आई")
        check(person(Facet.LIMIT, 2), "hi", "बशीर अहमद की लिमिट कितनी है")
        check(person(Facet.NOTE, 2), "hi", "बशीर अहमद का नोट")
        check(person(Facet.PHONE, 1), "hi", "अली रज़ा का नंबर क्या है", "अली का नंबर")
        check(Ask.Parties(listOf(1L)), "hi", "अली रज़ा का हिसाब")
        check(person(Facet.NOTE, 1), "hi", "अली रज़ा का नोट")
    }

    // ------------------------------------------------------------------------------------ Bengali

    @Test fun `Bengali - his number, balance and note, his name saved in Roman letters`() {
        check(person(Facet.PHONE, 2), "bn", "বশীর আহমদের নম্বর কী")
        check(Ask.Parties(listOf(2L)), "bn", "বশীর আহমদের হিসাব")
        check(person(Facet.NOTE, 2), "bn", "বশীর আহমদের নোট")
        check(person(Facet.PHONE, 1), "bn", "আলী রজার নম্বর কী")
        check(Ask.Parties(listOf(1L)), "bn", "আলী রজার হিসাব")
        check(person(Facet.NOTE, 1), "bn", "আলী রজার নোট")
    }

    @Test fun `Bengali - a name that ends in the letter of a case ending is still the name, bare and with its ending`() {
        // "বশীর" is Bashir: the final "র" is the name's own, yet "আলীর" is Ali's and its "র" is an ending. Both are read.
        check(person(Facet.NOTE, 2), "bn", "বশীর নোট", "বশীর আহমদ নোট", "বশীরের নোট", "বশীর আহমদের নোট")
        check(Ask.Parties(listOf(2L)), "bn", "বশীর হিসাব", "বশীরের হিসাব")
        check(person(Facet.NOTE, 1), "bn", "আলীর নোট")
        check(person(Facet.PHONE, 1), "bn", "আলীর নম্বর কী", "রজার নম্বর")
        check(person(Facet.NOTE, 2), "bn", "আহমদ নোট", "আহমদের নোট", names = listOf(Hudhud.Name(2, "Bashir Ahmad")))
    }

    // ------------------------------------------------------------------------------------ the looseness

    @Test fun `one vowel for another, z for j and w for b - the spellings Hindi and Bengali do not tell apart`() {
        check(person(Facet.PHONE, 5), "hi", "मोहम्मद का नंबर", "वकास का नंबर")
        check(person(Facet.PHONE, 1), "hi", "रजा का नंबर")
        check(person(Facet.PHONE, 5), "bn", "মোহাম্মদের নম্বর")
        check(person(Facet.PHONE, 8), "hi", "बुशरा का नंबर")
        check(person(Facet.PHONE, 8), "bn", "বুশরার নম্বর")
        check(person(Facet.NOTE, 8), "bn", "বুশরা নোট")
    }

    @Test fun `the customer met by most of the words is the one named, an even meeting is a choice of at most five`() {
        // Two words meet Bashir Ahmad and one meets Ahmad Khan: the first is named. One word alone meets both: a choice.
        check(person(Facet.PHONE, 2), "hi", "बशीर अहमद का नंबर")
        check(person(Facet.PHONE, 2, 3), "hi", "अहमद का नंबर")
        val six = listOf("Aslam", "Bilal", "Chand", "Daud", "Ejaz", "Farooq").mapIndexed { i, second -> Hudhud.Name(i + 1L, "Ahmad $second") }
        assertTrue("six names met alike are no choice", read("अहमद का नंबर", "hi", six) !is Ask.Person)
    }

    // ------------------------------------------------------------------------------------ what stays unnamed

    @Test fun `only vowels may be put in - Bushra is not Bashir, and a name the book lacks is not turned into another`() {
        check(Ask.NeedName(Facet.PHONE), "hi", "बशीर का नंबर", "बशीर अहमद का नंबर", names = withoutBashir)
        check(Ask.NeedName(Facet.PHONE), "bn", "বশীরের নম্বর", names = withoutBashir)
    }

    @Test fun `a word of the question is never a name, though a name in the book has the same letters`() {
        // "Nambar" is a customer in the book; "नंबर" and "নম্বর" are the word number. Nobody is named, and the note reader does not
        // take the concept word for the man either ("नंबर का नोट" is a question about a number, not about his note).
        check(Ask.NeedName(Facet.PHONE), "hi", "नंबर क्या है", "नंबर का नोट")
        check(Ask.NeedName(Facet.PHONE), "bn", "নম্বর নোট")
        check(person(Facet.PHONE, 9), "en", "Nambar Khan ka number", names = withoutBashir)
        check(person(Facet.NOTE, 9), "en", "what is Nambar Khan's note", names = withoutBashir)
    }

    @Test fun `Roman letters on both sides never go through the bridge`() {
        // Two Latin spellings that differ by a vowel are the name search's own matter (a near miss is no name): the bridge adds
        // nothing and takes nothing from them.
        assertEquals(person(Facet.PHONE, 2), read("Bashir Ahmad ka number", null))
        assertEquals(person(Facet.PHONE, 8), read("Bushra Bibi ka number", null))
    }

    // ------------------------------------------------------------------------------------ an entry said by voice

    @Test fun `an entry said in Hindi or Bengali names the customer saved in Roman letters`() {
        fun entry(s: String, language: String): Ask.Entry = read(s, language) as Ask.Entry
        assertEquals(listOf(2L), entry("बशीर को 500 दिए", "hi").ids)
        assertEquals(listOf(2L), entry("बशीर अहमद को 500 रुपये दिए", "hi").ids)
        assertEquals(listOf(5L), entry("मोहम्मद को 200 दिए", "hi").ids)
        assertEquals(listOf(2L), entry("বশীরকে ৫০০ দিয়েছি", "bn").ids)
        assertEquals(listOf(2L), entry("বশীর আহমদকে 500 দিয়েছি", "bn").ids)
        // The doer is read in Hindi: "ne" marks who gave, so this is money in.
        val back = entry("अली रज़ा ने 1000 दिए", "hi")
        assertEquals(listOf(1L), back.ids)
        assertEquals(false, back.isGiven)
    }

    // ------------------------------------------------------------------------------------ the nukta

    private val hindiBook = listOf(Hudhud.Name(1, "अली रज़ा"), Hudhud.Name(2, "बशीर अहमद"), Hudhud.Name(3, "फ़ारूक़ खान"))

    @Test fun `a name saved in Hindi letters with its nukta is met by the very word asked - the sentence loses the dot, so does the name`() {
        // VoiceQuestion.normalise takes the dot off every word that is said; "रज़ा" in the book kept it for the search, and the very
        // word asked met nothing ("रज़ा का नोट" was a jump, "अली रज़ा का नोट" a page).
        for (said in listOf("अली रज़ा का नोट", "रज़ा का नोट", "रजा का नोट", "अली रजा का नोट", "अली रज़ा का निजी नोट"))
            assertEquals(said, person(Facet.NOTE, 1), read(said, "hi", hindiBook))
        check(person(Facet.PHONE, 1), "hi", "अली रज़ा का नंबर क्या है", "रज़ा का नंबर", names = hindiBook)
        check(person(Facet.LAST_PAYMENT, 1), "hi", "अली रज़ा की आखिरी पेमेंट कब आई", names = hindiBook)
        check(person(Facet.LIMIT, 1), "hi", "रज़ा की लिमिट कितनी है", names = hindiBook)
        check(Ask.WhenPays(listOf(1L)), "hi", "रज़ा कब देगा", names = hindiBook)
        // Two dotted letters in one name ("फ़ारूक़") are met alike, said with or without them.
        check(person(Facet.PHONE, 3), "hi", "फ़ारूक़ का नंबर", "फारूक का नंबर", names = hindiBook)
    }

    @Test fun `the dotted letter saved as one character is the same name as the dotted letter saved as two`() {
        // U+095B is the letter with its dot built in; U+091C U+093C is the letter and the dot apart. A keyboard gives either.
        val built = listOf(Hudhud.Name(1, "अली र\u095Bा"))
        val apart = listOf(Hudhud.Name(1, "अली र\u091C\u093Cा"))
        for (names in listOf(built, apart)) {
            check(person(Facet.NOTE, 1), "hi", "अली रज़ा का नोट", "अली र\u095Bा का नोट", "रज़ा का नोट", names = names)
        }
    }

    @Test fun `an entry said by voice names the customer whose name keeps its nukta, in either direction`() {
        fun entry(s: String): Ask.Entry = read(s, "hi", hindiBook) as Ask.Entry
        assertEquals(listOf(1L), entry("अली रज़ा को 500 दिए").ids)
        val back = entry("अली रज़ा ने 1000 दिए")
        assertEquals(listOf(1L), back.ids)
        assertEquals(false, back.isGiven)
    }

    @Test fun `a product saved with its nukta is asked for as the sentence says it`() {
        val zinc = listOf(Hudhud.Name(100, "ज़िंक"))
        val ask = Hudhud.understand("ज़िंक का रेट क्या है", hindiBook, emptyList(), "hi", goods = zinc)
        assertEquals(Ask.Goods(listOf(100L), Hudhud.GoodsFacet.PRICE), ask)
    }

    @Test fun `Bengali keeps its dot in the sentence, so it keeps it in the name`() {
        // Bengali's nukta is a letter's own part ("য়", "ড়", "ঢ়"): the sentence keeps it, so the name must too, or "রাড়" would
        // become "রাড" in the book alone.
        val bangla = listOf(Hudhud.Name(1, "আলী রাড\u09BC"), Hudhud.Name(2, "রাড খান"))
        check(person(Facet.PHONE, 1), "bn", "আলী রাড\u09BCের নম্বর", "রাড\u09BC নম্বর", names = bangla)
        check(person(Facet.PHONE, 2), "bn", "রাড খানের নম্বর", names = bangla)
    }

    // ------------------------------------------------------------------------------------ the other direction

    private val banglaBook = listOf(Hudhud.Name(1, "আলী রজা"), Hudhud.Name(2, "বশীর আহমদ"))
    private val urduBook = listOf(Hudhud.Name(1, "علی رضا"), Hudhud.Name(2, "بشیر احمد"))

    @Test fun `a name saved in Hindi, Bengali or Urdu letters is met by the Roman letters it is said in`() {
        for (book in listOf(hindiBook, banglaBook, urduBook)) {
            check(person(Facet.PHONE, 2), "en", "Bashir Ahmad ka number", names = book)
            check(person(Facet.NOTE, 2), "en", "what is Bashir Ahmad's note", names = book)
            check(person(Facet.PHONE, 1), "en", "Ali Raza ka number", names = book)
        }
    }

    @Test fun `a name saved in Bengali letters is met in Bengali with its case ending, bare, and in Hindi`() {
        check(person(Facet.PHONE, 2), "bn", "বশীরের নম্বর", "বশীর আহমদের নম্বর", "বশীর নম্বর", names = banglaBook)
        check(person(Facet.NOTE, 1), "bn", "আলীর নোট", "আলী রজার নোট", names = banglaBook)
    }

    // ------------------------------------------------------------------------------------ what the letters may not do

    @Test fun `z and j are one letter whichever way round, and two names that differ only so are a choice, never a guess`() {
        val twins = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(11, "Raja Sultan"))
        check(person(Facet.PHONE, 1, 11), "hi", "रज़ा का नंबर", names = twins)
        check(person(Facet.PHONE, 1), "hi", "अली रज़ा का नंबर", names = twins)
    }

    @Test fun `every letter said must be met - a name that has one more is not that name, and two letters are not enough to name anybody`() {
        val near = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(12, "Ram Lal"), Hudhud.Name(13, "Basa Khan"))
        // "बशीर" is b-s-i-r: "Basa" has no "r" to meet, so Bashir is not Basa; "रम" is two letters, which "Ram" would only be by luck.
        check(Ask.NeedName(Facet.PHONE), "hi", "बशीर का नंबर", "रम का नंबर", names = near)
        check(person(Facet.PHONE, 13), "hi", "बसा का नंबर", names = near)
        check(person(Facet.PHONE, 12), "hi", "राम का नंबर", names = near)
    }
}
