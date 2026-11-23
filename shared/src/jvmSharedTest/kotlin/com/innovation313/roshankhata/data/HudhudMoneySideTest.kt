package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way the money is owed, in all nine languages, through the whole pipeline (16 Nov 2026, audit T11). The held-out set
 * read four of 36 the opposite way (Arabic "كم باقي علينا ندفع", Farsi "کل پولی که باید بدم", "کل بدهی مشتریان به مغازه",
 * Indonesian "utang aku") and four more as something else (Hindi लेनदारी/देनदारी, Indonesian "ditagih"). A to-get told as a
 * to-give is the costliest misreading there is: every pair here must come out on its own side.
 */
class HudhudMoneySideTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    private val GET = VoiceQuestion.Kind.TO_GET
    private val GIVE = VoiceQuestion.Kind.TO_GIVE

    private val pairs get() = PAIRS

    companion object {
        private val GET = VoiceQuestion.Kind.TO_GET
        private val GIVE = VoiceQuestion.Kind.TO_GIVE

        /** Every pair: language, sentence, the side it says. HudhudSeedSafeTest reads them under other shuffles too. */
        internal val PAIRS = listOf(
            Triple("en", "how much total is still to come from customers", GET),
            Triple("en", "money to get total", GET),
            Triple("en", "how much I owe to suppliers in total", GIVE),
            Triple("en", "my debt total how much buddy", GIVE),
            Triple("ur", "مارکیٹ میں میرا کتنا پیسہ پھنسا ہے", GET),
            Triple("ur", "میرے اوپر کتنا قرضہ ہے", GIVE),
            Triple("ur-Latn", "market mein mera kitna paisa phansa hai", GET),
            Triple("ur-Latn", "mere upar kitna qarza hai", GIVE),
            Triple("hi", "मार्केट में मेरा कितना पैसा फंसा है", GET),
            Triple("hi", "ज़रा बताओ टोटल लेनदारी कितनी है", GET),
            Triple("hi", "मेरे ऊपर कितना कर्ज़ा है", GIVE),
            Triple("hi", "ज़रा बताओ कुल देनदारी कितनी है", GIVE),
            Triple("bn", "টোটাল পাওনা", GET),
            Triple("bn", "সাপ্লায়ারদের মোট কত দিতে হবে", GIVE),
            Triple("ar", "مجموع ما يجب أن أستلمه من العملاء", GET),
            Triple("ar", "كل اللي عليا للموردين كام", GIVE),
            Triple("ar", "كم باقي علينا ندفع", GIVE),
            Triple("fa", "مردم چقدر به من بدهکارند", GET),
            Triple("fa", "کل بدهی مشتریان به مغازه", GET),
            Triple("fa", "مغازه چقدر بدهی داره", GIVE),
            Triple("fa", "کل پولی که باید بدم چقدره", GIVE),
            Triple("sd", "ادا سڀني گراهڪن کان ڪيترا وٺڻا آهن", GET),
            Triple("sd", "سپلائرن کي ڪيترا ڏيڻا آهن", GIVE),
            Triple("in", "total yang harus diterima", GET),
            Triple("in", "berapa total yang harus ditagih dari pelanggan", GET),
            Triple("in", "total utang saya ke pemasok berapa", GIVE),
            Triple("in", "utang aku totalnya berapa ya", GIVE)
        )
    }

    @Test fun `every side is read as its own side`() {
        val wrong = pairs.mapNotNull { (lang, said, side) ->
            // A side right but told as a guess ("Taken as") is still its side.
            val a = Hudhud.understand(said, names, emptyList(), lang).let { (it as? Hudhud.Ask.Guessed)?.ask ?: it }
            if (a == Hudhud.Ask.Figure(side)) null else "[$lang] $said  wanted $side, got $a"
        }
        assertTrue(wrong.joinToString("\n", "${wrong.size} read otherwise:\n"), wrong.isEmpty())
    }

    @Test fun `a bare word of debt is still the sense's to place`() {
        // "How much debt is on me": no word of giving or getting, the side comes from the sense (review D's reading).
        val a = Hudhud.understand("كم علي من ديون", names, emptyList(), "ar").let { (it as? Hudhud.Ask.Guessed)?.ask ?: it }
        assertTrue("$a", a == Hudhud.Ask.Figure(GIVE))
    }

    @Test fun `the sense never turns a side the rules read into the other`() {
        // These three the sense alone reads the opposite way; the rules' side stands.
        for ((lang, said, side) in listOf(Triple("fa", "کل بدهی مشتریان به مغازه", GET), Triple("fa", "کل پولی که باید بدم چقدره", GIVE),
            Triple("ar", "كم باقي علينا ندفع", GIVE))) {
            assertTrue(said, Hudhud.understand(said, names, emptyList(), lang) == Hudhud.Ask.Figure(side))
        }
    }
}
