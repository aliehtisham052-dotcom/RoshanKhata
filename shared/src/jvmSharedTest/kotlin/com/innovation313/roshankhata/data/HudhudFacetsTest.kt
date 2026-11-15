package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three things about a customer only the rules know — since when he deals with the shop, his last entry and how many
 * entries he has (15 Nov 2026). The bank has no meaning of their own, so the sense, sure of another meaning, turned the rules'
 * right reading: Roman Urdu "Ali Raza ki aakhri entry" into his last payment, and — his name saved in Latin letters, said in
 * Devanagari, Bengali or Sindhi, which two short words do not name surely — "कितनी एंट्री" into the count of the book and
 * "कब से ग्राहक है" into the count of customers, with the man dropped from the answer. Every sentence here goes through
 * [Hudhud.understand] with the shipped sense: the rules alone read them all right, so only the sense can break them, and only
 * [Hudhud]'s guard for these three facets holds them. The sentences are written by the maker of the tests, not by a native reader
 * of each language (said plainly in the notes). Names are made up (lesson 80).
 */
class HudhudFacetsTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"))
    private val goods = listOf(Hudhud.Name(100, "Urea"))

    private fun read(s: String, language: String? = null) = Hudhud.understand(s, names, emptyList(), language, goods = goods)

    private fun check(facet: Facet, said: List<Pair<String?, String>>) {
        for ((language, s) in said) assertEquals("$language: $s", Ask.Person(listOf(1L), facet), read(s, language))
    }

    @Test fun `his last entry is his last entry, in nine languages - not his last payment`() = check(
        Facet.LAST_ENTRY, listOf(
            "en" to "when was Ali Raza's last entry",
            null to "Ali Raza ki aakhri entry",
            null to "Ali Raza ki aakhri entry kab thi",
            null to "Ali Raza ki last transaction",
            null to "Ali Raza ka aakhri lena dena",
            "ur" to "علی رضا کی آخری انٹری",
            "hi" to "अली रज़ा की आखिरी एंट्री",
            "bn" to "আলী রজার শেষ এন্ট্রি",
            "fa" to "آخرین ثبت علی رضا",
            "ar" to "آخر قيد لعلي رضا",
            "sd" to "علي رضا جي آخري انٽري",
            "in" to "entri terakhir Ali Raza"
        )
    )

    @Test fun `his first entry and since when he is a customer, in nine languages - his, not the count of the shop customers`() = check(
        Facet.SINCE, listOf(
            "en" to "when was Ali Raza's first entry",
            "en" to "since when is Ali Raza a customer",
            null to "Ali Raza ki pehli entry",
            null to "Ali Raza kab se customer hai",
            "ur" to "علی رضا کی پہلی انٹری",
            "ur" to "علی رضا کب سے گاہک ہے",
            "hi" to "अली रज़ा की पहली एंट्री",
            "hi" to "अली रज़ा कब से ग्राहक है",
            "bn" to "আলী রজার প্রথম এন্ট্রি",
            "bn" to "আলী রজা কবে থেকে গ্রাহক",
            "fa" to "اولین ثبت علی رضا",
            "ar" to "أول قيد لعلي رضا",
            "sd" to "علي رضا جي پهرين انٽري",
            "sd" to "علي رضا ڪڏهن کان گراهڪ آهي",
            "in" to "entri pertama Ali Raza"
        )
    )

    @Test fun `how many entries he has, in the languages whose word for entry the rules know - his, not the book's count`() = check(
        Facet.ENTRIES, listOf(
            "en" to "how many entries does Ali Raza have",
            null to "Ali Raza ki kitni entries hain",
            null to "Ali Raza ki saari entries",
            "hi" to "अली रज़ा की कितनी एंट्री हैं",
            "bn" to "আলী রজার কয়টি এন্ট্রি আছে",
            "fa" to "علی رضا چند ثبت دارد",
            "sd" to "علي رضا جا ڪيترا انٽري آهن",
            "in" to "berapa entri Ali Raza"
        )
    )

    /**
     * Where a payment is in the sentence the sense is the better judge, and the guard stands aside: the rules read "paisa kab aaya"
     * and "since when … not paid" as an entry's and a first day's, the sense as the last payment — and the sense is right.
     */
    @Test fun `a sentence with a payment in it is left to the sense`() {
        for ((language, s) in listOf(
            null to "Ali Raza ki aakhri adaigi",
            null to "Ali Raza ki aakhri payment entry",
            null to "Ali Raza ka aakhri paisa kab aaya",
            "en" to "since when has Ali Raza not paid",
            null to "Ali Raza ne kab se paisa nahi diya"
        )) assertEquals("$language: $s", Ask.Person(listOf(1L), Facet.LAST_PAYMENT), read(s, language))
    }
}
