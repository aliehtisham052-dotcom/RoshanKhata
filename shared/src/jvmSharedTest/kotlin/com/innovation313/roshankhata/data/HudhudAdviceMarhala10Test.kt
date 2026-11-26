package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.AdviseOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marhala 10 of the master plan (26 Nov 2026): the advisor's three new questions, in the nine languages, through the whole pipeline
 * with the shipped sense (lesson 145) — more credit for one named customer (D8: three facts and "the decision is yours"), the share of
 * all credit one party holds (D19, F65), a joke aimed at the dues ("jadoo kar ke udhaar wapas la do") — and what each refuses. Then
 * each new guard alone, on [HudhudAdviceAsk] directly, so no other layer can refuse the same sentence (lesson 129); and the two pure
 * rules ([HudhudAdviceRules.owedMost], [HudhudAdviceRules.pricePressure]). Names are invented (lesson 80).
 *
 * The Sindhi, Bengali, Farsi and Arabic sentences use the words those languages' strings use; they are not a native speaker's read.
 */
class HudhudAdviceMarhala10Test {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Aslam Khan", "Ali Raza", "Bashir Ahmad", "Naeem Butt").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun credit(id: Long) = Ask.Advise(AdviseOf.MORE_CREDIT, listOf(id))
    private val conc = Ask.Advise(AdviseOf.CONCENTRATION)
    private val magic = Ask.Advise(AdviseOf.MAGIC)

    private fun all(rows: List<Triple<String, String, Ask>>) {
        val wrong = rows.mapNotNull { (lang, said, want) ->
            val got = read(lang, said)
            if (got == want) null else "[$lang] $said  =>  $got  (want $want)"
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    // ------------------------------------------------------------------------------------------------ the classes, nine languages

    @Test fun `more credit for a named customer is his three facts`() = all(listOf(
        Triple("ur-Latn", "Aslam ko aur udhaar doon?", credit(1)),
        Triple("ur-Latn", "kya Aslam ko mazeed udhaar dena chahiye", credit(1)),
        Triple("ur-Latn", "Ali Raza ko aur udhaar de doon kya", credit(2)),
        Triple("en", "should I give Ali more credit?", credit(2)),
        Triple("en", "can I give Bashir more credit", credit(3)),
        Triple("ur", "کیا اسلم کو اور ادھار دوں؟", credit(1)),
        Triple("hi", "क्या असलम को और उधार दूं?", credit(1)),
        Triple("bn", "আসলাম কে কি আরও বাকি দেব?", credit(1)),
        Triple("ar", "هل أعطي أسلم المزيد من الدين؟", credit(1)),
        Triple("fa", "آیا به اسلم نسیه بیشتر بدهم؟", credit(1)),
        Triple("sd", "ڇا اسلم کي وڌيڪ اڌار ڏيان؟", credit(1)),
        Triple("in", "apakah saya kasih Aslam utang lagi?", credit(1))
    ))

    @Test fun `the share of credit with one party is the concentration`() = all(listOf(
        Triple("ur-Latn", "kul udhaar ka kitna hissa aik party ke paas hai", conc),
        Triple("ur-Latn", "mera udhaar kitna ek party ke paas hai feesad", conc),
        Triple("en", "how concentrated is my credit", conc),
        Triple("en", "what share of my credit is with one customer", conc),
        Triple("ur", "کل ادھار کا کتنا حصہ ایک پارٹی کے پاس ہے", conc),
        Triple("hi", "कुल उधार का कितना हिस्सा एक पार्टी के पास है", conc),
        Triple("bn", "মোট বাকির কত অংশ এক পার্টির কাছে", conc),
        Triple("ar", "كم حصة الدين عند عميل واحد", conc),
        Triple("fa", "چند درصد نسیه دست یک مشتری است", conc),
        Triple("sd", "ڪل اڌار جو ڪيترو حصو هڪ پارٽي وٽ آهي", conc),
        Triple("in", "berapa persen piutang ada pada satu pelanggan", conc)
    ))

    @Test fun `a joke aimed at the dues is a light line and who owes most`() = all(listOf(
        Triple("ur-Latn", "jadoo kar ke udhaar wapas la do", magic),
        Triple("ur-Latn", "apna jadoo se sara udhaar wapas lao", magic),
        Triple("en", "use your magic to bring my credit back", magic),
        Triple("ur", "جادو کر کے ادھار واپس لا دو", magic),
        Triple("hi", "जादू करके उधार वापस ला दो", magic),
        Triple("bn", "জাদু করে বাকি ফেরত এনে দাও", magic),
        Triple("ar", "استخدم سحرك لاسترداد الديون", magic),
        Triple("fa", "با جادو نسیه را برگردان", magic),
        Triple("sd", "جادو ڪري اڌار واپس آڻي ڏي", magic),
        Triple("in", "pakai sihir untuk kembalikan utang", magic)
    ))

    // ------------------------------------------------------------------------------------------------ what they leave alone

    @Test fun `an entry, a limit, a past credit and his balance are not more credit`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "Aslam ko 500 aur udhaar do",
            "ur-Latn" to "Aslam ko udhaar diya",
            "en" to "how much more credit can I give Ali",
            "ur-Latn" to "Aslam ki udhaar ki hadd kitni hai",
            "ur-Latn" to "Aslam ka udhaar kitna hai",
            "en" to "how much does Ali Raza owe me"
        )) {
            val got = read(lang, said)
            assertFalse("[$lang] $said => $got", got is Ask.Advise && got.of == AdviseOf.MORE_CREDIT)
        }
    }

    @Test fun `who owes most and a plain joke are not the concentration or the joke card`() {
        assertFalse(read("ur-Latn", "sab se zyada udhaar kis ka hai").let { it is Ask.Advise })
        assertFalse(read("en", "tell me a joke").let { it is Ask.Advise })
        assertFalse(read("en", "do some magic").let { it is Ask.Advise })
    }

    // ------------------------------------------------------------------------------------------------ each guard alone (lesson 129)

    private val isName: (String) -> Boolean = { w -> w in setOf(VoiceQuestion.normalise("Aslam"), VoiceQuestion.normalise("Ali")) }
    private val noCall: (String) -> Boolean = { false }

    @Test fun `more credit needs credit, giving, more or should, a name and nothing else`() {
        assertTrue(HudhudAdviceAsk.moreCredit("Aslam ko aur udhaar doon", isName, noCall))
        assertTrue(HudhudAdviceAsk.moreCredit("kya Aslam ko udhaar doon", isName, noCall))
        assertFalse("no credit word", HudhudAdviceAsk.moreCredit("Aslam ko aur paise doon", isName, noCall))
        assertFalse("no giving", HudhudAdviceAsk.moreCredit("Aslam ko aur udhaar", isName, noCall))
        assertFalse("neither more nor should", HudhudAdviceAsk.moreCredit("Aslam ko udhaar doon", isName, noCall))
        assertFalse("how much", HudhudAdviceAsk.moreCredit("Aslam ko kitna aur udhaar doon", isName, noCall))
        assertFalse("a figure", HudhudAdviceAsk.moreCredit("Aslam ko aur 500 udhaar doon", isName, noCall))
        assertFalse("no name", HudhudAdviceAsk.moreCredit("kya aur udhaar doon", isName, noCall))
        assertFalse("another word", HudhudAdviceAsk.moreCredit("Aslam ko aur udhaar doon kal", isName, noCall))
    }

    @Test fun `the concentration needs credit and a share with one, or the word concentrated, and no name`() {
        assertEquals(conc, HudhudAdviceAsk.concentration("kitna hissa udhaar ek party ke paas hai", isName, noCall))
        assertEquals(conc, HudhudAdviceAsk.concentration("how concentrated is my credit", isName, noCall))
        assertNull("no credit", HudhudAdviceAsk.concentration("how concentrated is my stock", isName, noCall))
        assertNull("a share with nobody one", HudhudAdviceAsk.concentration("udhaar ka kitna hissa hai", isName, noCall))
        assertNull("a name", HudhudAdviceAsk.concentration("Aslam ke paas udhaar ka kitna hissa hai ek", isName, noCall))
        assertNull("another word", HudhudAdviceAsk.concentration("how concentrated is my credit yesterday", isName, noCall))
        // Only the name check can refuse this: "party" is also a word around the share (lesson 129).
        assertNull("a customer called Party", HudhudAdviceAsk.concentration("kitna hissa udhaar ek party ke paas hai",
            { it == "party" }, noCall))
    }

    @Test fun `the joke needs magic and the dues, no name and no figure`() {
        assertEquals(magic, HudhudAdviceAsk.magic("jadoo kar ke udhaar wapas la do", isName, noCall))
        assertNull("no dues", HudhudAdviceAsk.magic("jadoo kar ke dikhao", isName, noCall))
        assertNull("no magic", HudhudAdviceAsk.magic("udhaar wapas la do", isName, noCall))
        assertNull("a name", HudhudAdviceAsk.magic("jadoo se Aslam ka udhaar wapas la do", isName, noCall))
        assertNull("a figure", HudhudAdviceAsk.magic("jadoo se 500 udhaar wapas la do", isName, noCall))
        assertNull("another word", HudhudAdviceAsk.magic("jadoo ki film mein udhaar", isName, noCall))
        // Only the name check can refuse this: "sara" (all) is also the joke's own word (lesson 129).
        assertNull("a customer called Sara", HudhudAdviceAsk.magic("jadoo se sara udhaar wapas la do", { it == "sara" }, noCall))
        assertEquals(magic, HudhudAdviceAsk.magic("jadoo se sara udhaar wapas la do", isName, noCall))
    }

    // ------------------------------------------------------------------------------------------------ the pure rules

    private class P(val id: Long, val balance: Double)
    private fun party(id: Long, balance: Double) = P(id, balance)

    @Test fun `who owes most is the positive balances, largest first, the book's order on a tie`() {
        val parties = listOf(party(1, 300.0), party(2, -900.0), party(3, 500.0), party(4, 300.0), party(5, 0.0))
        assertEquals(listOf(3L, 1L, 4L), HudhudAdviceRules.owedMost(parties, 5) { it.balance }.map { it.id })
        assertEquals(listOf(3L), HudhudAdviceRules.owedMost(parties, 1) { it.balance }.map { it.id })
        // The first is F65's largest.
        assertEquals(parties.indexOfFirst { it.id == 3L }, PriceMath.concentration(parties.map { it.balance })!!.index)
    }

    @Test fun `price pressure is sales up with the margin as shown down`() {
        assertTrue(HudhudAdviceRules.pricePressure(1200.0, 1000.0, 18.0, 20.0))
        assertFalse("sales down", HudhudAdviceRules.pricePressure(900.0, 1000.0, 18.0, 20.0))
        assertFalse("sales the same", HudhudAdviceRules.pricePressure(1000.0, 1000.0, 18.0, 20.0))
        assertFalse("margin up", HudhudAdviceRules.pricePressure(1200.0, 1000.0, 21.0, 20.0))
        assertFalse("a fall the card cannot show", HudhudAdviceRules.pricePressure(1200.0, 1000.0, 19.996, 20.0))
        assertTrue("a fall it shows", HudhudAdviceRules.pricePressure(1200.0, 1000.0, 19.99, 20.0))
    }
}
