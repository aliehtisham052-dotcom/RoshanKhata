package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marhala 1, "money first" (26 Nov 2026, docs/hudhud-master-plan-v1.md): the money's side, its amount and its person are said by the
 * rules, never left to the learnt sense (lesson 163). One class per block, each in the nine languages, through the whole pipeline with
 * the shipped sense (lesson 145), and one direct test per guard where only that guard can decide (lesson 129):
 *
 * - BC-320  "my share" said with a word of giving is the shop's to give; a bare share left is the shop's to get.
 * - BC-321  a giving or taking denied ("Ali ne 500 nahi diye") is no entry: the named man's own account.
 * - BC-322  a sum still owed ("Ali se 500 lene hain", "I owe Ali 500") is no entry: the named man's own account.
 * - BC-323  the shop's own "I will pay" ("Kal Ali ko 500 dunga") is no entry, and never the customer's promised day.
 * - BC-324  a phone or identity number is never an amount.
 * - BC-325  a document's own number (invoice, bill, receipt, "no", "#", RK-000013) is never an amount.
 * - BC-318  a bare loan ("Kitna qarz baqi hai") asks the side first (D15), never answers one.
 * - BC-319  whose payment or money is pending or late, nobody's side said ("Kis ki payment late ho gayi"), asks the side first (D15).
 * - BC-336  Arabic's "we / I pay, settle, owe" (ندفع, نسدد, أدفع, ندين) is the shop's to give by rule, not by "علينا" or the sense alone.
 *
 * Names and numbers are invented (lesson 80): the phone numbers are the annotation guide's own. Sindhi, Bengali, Farsi and Arabic
 * sentences were written by Claude and need a native read (D17) before they are frozen.
 */
class HudhudMoneyFirstTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, emptyList(), null)

    private fun inner(a: Ask): Ask = when (a) {
        is Ask.Guessed -> inner(a.ask)
        is Ask.Now -> inner(a.ask)
        else -> a
    }

    /** No entry of money that moved, in any wrapping. */
    private fun noEntry(a: Ask): Boolean {
        val x = inner(a)
        return x !is Ask.Entry && x !is Ask.Entries && !(x is Ask.DidYouMean && x.options.any { it is Ask.Entry || it is Ask.Entries })
    }

    /** Ali Raza's own account (id 1), or one of his facts: the right person, and no entry. */
    private fun hisAccount(a: Ask): Boolean = when (val x = inner(a)) {
        is Ask.Parties -> x.ids == listOf(1L)
        is Ask.Person -> x.ids == listOf(1L)
        else -> false
    }

    // ------------------------------------------------------------------------------------------------ BC-320, my share

    @Test fun `my share with a word of giving is the shop's to give, a bare share left is its to get`() {
        val give = listOf(
            "ur-Latn" to "Mere hisse ka kitna dena baqi hai",
            "ur" to "میرے حصے کا کتنا دینا باقی ہے",
            "hi" to "मेरे हिस्से का कितना देना बाकी है",
            "en" to "how much of my share do I still have to pay"
        )
        for ((lang, said) in give) assertEquals("[$lang] $said", Ask.Figure(Kind.TO_GIVE), inner(read(lang, said)))
        val get = listOf(
            "ur-Latn" to "Mere hisse ka kitna baqi hai",
            "ur-Latn" to "Mere hisse ka kitna lena baqi hai",
            "ur" to "میرے حصے کا کتنا باقی ہے",
            "hi" to "मेरे हिस्से का कितना बाकी है",
            "en" to "how much of my share is left",
            // native read
            "sd" to "منهنجي حصي جو ڪيترو باقي آهي",
            "bn" to "আমার ভাগের কত বাকি আছে",
            "ar" to "كم تبقى من حصتي",
            "fa" to "از سهم من چقدر مانده",
            "in" to "berapa sisa bagian saya"
        )
        for ((lang, said) in get) assertEquals("[$lang] $said", Ask.Figure(Kind.TO_GET), inner(read(lang, said)))
    }

    @Test fun `the share guard alone - a word of giving turns it`() {
        // Only the share rule reads these as owed to the shop ([VoiceQuestion.owedToMe]); with "dena" it must not.
        assertTrue(VoiceQuestion.owedToMe(VoiceQuestion.normalise("mere hisse ka kitna baqi hai")))
        assertFalse(VoiceQuestion.owedToMe(VoiceQuestion.normalise("mere hisse ka kitna dena baqi hai")))
        assertEquals(Kind.TO_GIVE, VoiceQuestion.classify("mere hisse ka kitna dena baqi hai"))
        assertEquals(Kind.TO_GET, VoiceQuestion.classify("حصتي كم تبقى"))
    }

    // ------------------------------------------------------------------------------------------------ BC-321, a giving denied

    private val denied = listOf(
        "ur-Latn" to "Ali Raza ne 500 nahi diye",
        "ur-Latn" to "Ali Raza ne abhi 500 nahi diye",
        "ur-Latn" to "Ali Raza ko 500 nahi diye",
        "ur-Latn" to "Ali Raza se 500 nahi liye",
        "ur" to "Ali Raza نے 500 نہیں دیے",
        "hi" to "Ali Raza ने 500 नहीं दिए",
        "en" to "Ali Raza did not pay 500",
        "en" to "Ali Raza didn't pay me 500",
        "in" to "Ali Raza tidak bayar 500",
        // native read
        "sd" to "Ali Raza 500 نه ڏنا",
        "bn" to "Ali Raza 500 দেয়নি",
        "ar" to "Ali Raza لم يدفع 500",
        "fa" to "Ali Raza 500 نداد"
    )

    @Test fun `a giving denied is no entry and names the man`() {
        for ((lang, said) in denied) {
            val a = read(lang, said)
            assertTrue("[$lang] $said -> $a", noEntry(a))
            assertTrue("[$lang] $said is not his account: $a", hisAccount(a) || inner(a) == Ask.Figure(Kind.NOT_PAID))
        }
        // Roman Urdu, Urdu, Hindi and English name him outright: his account, never the list of everybody who has not paid.
        for ((lang, said) in denied.take(8)) assertTrue("[$lang] $said -> ${read(lang, said)}", hisAccount(read(lang, said)))
    }

    @Test fun `the denial guard alone`() {
        for ((_, said) in denied) assertTrue(said, VoiceQuestion.moneyNotMoved(said))
        // Money that moved, said beside "until" or "new", stays money that moved.
        for (said in listOf("Ali ne aaj tak 500 diye", "Ali ko nai bori ke 500 diye", "Ali Raza ne 500 diye", "I gave Ali Raza 500"))
            assertFalse(said, VoiceQuestion.moneyNotMoved(said))
        assertTrue(VoiceQuestion.moveDenied(VoiceQuestion.normalise("Ali Raza 500 نداد")))
        assertFalse(VoiceQuestion.moveDenied(VoiceQuestion.normalise("Ali Raza 500 داد")))
    }

    // ------------------------------------------------------------------------------------------------ BC-322, a sum still owed

    private val owed = listOf(
        "ur-Latn" to "Ali Raza se 500 lene hain",
        "ur-Latn" to "Ali Raza se 500 lena hai",
        "ur-Latn" to "Ali Raza se 500 leni hai",
        "ur-Latn" to "Ali Raza ko 500 dene hain",
        "ur-Latn" to "Ali Raza ko 500 dena hai",
        "ur-Latn" to "Ali Raza ne 500 dene hain",
        "ur" to "Ali Raza سے 500 لینے ہیں",
        "ur" to "Ali Raza کو 500 دینے ہیں",
        "hi" to "Ali Raza से 500 लेने हैं",
        "hi" to "Ali Raza को 500 देने हैं",
        "en" to "I have to pay Ali Raza 500",
        "en" to "I have to get 500 from Ali Raza",
        "en" to "Ali Raza owes me 500",
        "en" to "I owe Ali Raza 500",
        "in" to "saya harus terima 500 dari Ali Raza",
        "in" to "saya harus bayar 500 ke Ali Raza",
        // native read
        "sd" to "Ali Raza کان 500 وٺڻا آهن",
        "sd" to "Ali Raza کي 500 ڏيڻا آهن",
        "bn" to "Ali Raza এর কাছে 500 পাওনা",
        "bn" to "Ali Raza কে 500 দিতে হবে",
        "ar" to "لي عند Ali Raza 500",
        "fa" to "از Ali Raza 500 طلب دارم",
        "fa" to "به Ali Raza 500 بدهکارم"
    )

    @Test fun `a sum still owed is no entry - his account`() {
        for ((lang, said) in owed) {
            val a = read(lang, said)
            assertTrue("[$lang] $said -> $a", noEntry(a))
            assertTrue("[$lang] $said is not his account: $a", hisAccount(a))
        }
    }

    @Test fun `the owed guard alone`() {
        for ((_, said) in owed) assertTrue(said, VoiceQuestion.moneyNotMoved(said))
        assertTrue(VoiceQuestion.stillOwed(VoiceQuestion.normalise("ali raza se 500 leni hai")))
        assertFalse(VoiceQuestion.stillOwed(VoiceQuestion.normalise("ali raza se 500 liye")))
        assertTrue(VoiceQuestion.stillOwed(VoiceQuestion.normalise("به Ali Raza 500 بدهکارم")))
    }

    @Test fun `two entries where one half is owed or denied are no entries`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "Ali Raza ko 500 diye aur Bashir Ahmad se 300 lene hain",
            "ur-Latn" to "Ali Raza ko 500 diye aur Bashir Ahmad ne 300 nahi diye",
            "ur-Latn" to "Ali Raza ko 500 diye aur Bashir Ahmad ko kal 300 dunga"
        )) assertTrue("[$lang] $said -> ${read(lang, said)}", noEntry(read(lang, said)))
        // Two that moved stay two entries.
        assertTrue(inner(read("ur-Latn", "Ali Raza ko 500 diye aur Bashir Ahmad se 300 liye")) is Ask.Entries)
    }

    // ------------------------------------------------------------------------------------------------ BC-323, the shop's "I will"

    private val shopWill = listOf(
        "ur-Latn" to "Kal Ali Raza ko 500 dunga",
        "ur-Latn" to "Kal Ali Raza ko 500 doonga",
        "ur-Latn" to "main kal Ali Raza ko 500 dungi",
        "ur" to "میں کل Ali Raza کو 500 دوں گا",
        "hi" to "मैं कल Ali Raza को 500 दूँगा",
        "en" to "I will pay Ali Raza 500 tomorrow",
        "en" to "I'll give Ali Raza 500 tomorrow",
        "in" to "besok saya akan bayar Ali Raza 500",
        // native read
        "sd" to "مان سڀاڻي Ali Raza کي 500 ڏيندس",
        "bn" to "আমি কাল Ali Raza কে 500 দেব",
        "ar" to "سأدفع لـ Ali Raza 500 غدا",
        "fa" to "فردا به Ali Raza 500 می‌دهم"
    )

    @Test fun `the shop's own I will is never a past entry`() {
        for ((lang, said) in shopWill) {
            val a = read(lang, said)
            assertTrue("[$lang] $said -> $a", noEntry(a))
            assertTrue("[$lang] $said is not his account: $a", hisAccount(a))
        }
        // A customer's own promise stays his promised day.
        assertEquals(Ask.WhenPays(listOf(1L)), read("ur-Latn", "Ali Raza kal 500 dega"))
    }

    @Test fun `I will pay Ali is never the day Ali pays`() {
        for ((lang, said) in listOf("en" to "I will pay Ali Raza tomorrow", "ur-Latn" to "main Ali Raza ko kal dunga", "ur" to "میں Ali Raza کو کل دوں گا")) {
            val a = inner(read(lang, said))
            assertFalse("[$lang] $said -> $a", a is Ask.WhenPays)
        }
    }

    @Test fun `the future guards alone`() {
        for (said in listOf("Kal Ali ko 500 dunga", "Ali ko 500 doongi", "مان Ali کي 500 ڏيندس", "আমি Ali কে 500 দেব", "سأدفع 500 لعلي",
            "فردا 500 میدهم", "I'll pay 500")) assertTrue(said, VoiceQuestion.saysFuture(said))
        assertFalse(VoiceQuestion.saysFuture("Ali ko kal 500 diye"))
        assertTrue(VoiceQuestion.shopWillPay("I will pay Ali tomorrow"))
        assertTrue(VoiceQuestion.shopWillPay("مان Ali کي ڏيندس"))
        // Bengali's "I will give" is the whole word; "দেবে" (he will give) is the customer's (lesson 129: only this guard decides).
        assertTrue(VoiceQuestion.shopWillPay("আমি কাল Ali কে 500 দেব"))
        assertFalse(VoiceQuestion.shopWillPay("Ali কবে দেবে"))
        assertFalse(VoiceQuestion.shopWillPay("Ali will pay me tomorrow"))
        assertFalse(VoiceQuestion.shopWillPay("Ali kal dega"))
        // The rules' own "when … pay" with the shop as the payer (only the willPay guard decides it).
        for ((lang, said) in listOf("en" to "when will I pay Ali Raza", "ur-Latn" to "Ali Raza ko kab dunga"))
            assertFalse("[$lang] $said -> ${read(lang, said)}", inner(read(lang, said)) is Ask.WhenPays)
        // The date of a promise is no day of the book.
        assertNull(VoiceQuestion.entryDay("Kal Ali ko 500 dunga"))
    }

    // ------------------------------------------------------------------------------------------------ BC-324, BC-325, number kinds

    private fun amountOf(said: String, lang: String? = null) = VoiceEntry.parse(said, listOf("Ali Raza"), lang).amount

    @Test fun `a phone or identity number is never an amount`() {
        for (said in listOf("Ali Raza ko 03001234567 diye", "Ali Raza ko 0300 1234567 diye", "Ali Raza ko 0300-1234567 diye",
            "Ali Raza ko +92 300 1234567 diye", "Ali Raza ko +923001234567 diye", "Ali Raza ko 0092 300 1234567 diye",
            "Ali Raza ko 0300 diye", "Ali Raza ko 35202-1234567-1 diye", "Ali Raza ko 3001234567 diye"))
            assertNull(said, amountOf(said))
        // The sum beside a number is still read, whichever side of it.
        for (said in listOf("Ali Raza ko 0300-1234567 par 500 bheje", "Ali Raza ne 03001234567 se 500 bheje", "sent 500 to Ali Raza on 0300 1234567",
            "Ali Raza ko +92 300 1234567 par 500 bheje", "Ali Raza کو 03001234567 پر 500 بھیجے", "Ali Raza को 03001234567 पर 500 भेजे"))
            assertEquals(said, 500.0, amountOf(said)!!, 0.0)
        // Through Hudhud: no entry of three billion rupees.
        assertFalse(read("ur-Latn", "Ali Raza ko 03001234567 diye").let { inner(it) is Ask.Entry })
        assertEquals(500.0, (inner(read("ur-Latn", "Ali Raza ne 03001234567 se 500 bheje")) as Ask.Entry).amount, 0.0)
        // Large sums written as people write them stay sums.
        assertEquals(1500000.0, amountOf("Ali Raza ko 1500000 diye")!!, 0.0)
        assertEquals(1500000.0, amountOf("Ali Raza ko 15,00,000 diye")!!, 0.0)
    }

    @Test fun `a document's number is never an amount`() {
        for (said in listOf("Ali Raza ko invoice 1234 diya", "Ali Raza ko bill number 4521 diya", "Ali Raza paid invoice #1234",
            "I gave Ali Raza receipt no 77", "Ali Raza ko RK-000013 wali entry ke paise diye", "Ali Raza ne cheque 123456 diya"))
            assertNull(said, amountOf(said))
        for (said in listOf("Ali Raza ne invoice 1234 ke 500 diye", "Ali Raza paid 500 for invoice 1234", "Ali Raza ko 500 ka bill diya",
            "Ali Raza ko bill 500 ka diya", "Ali Raza ne cheque 123456 se 500 diye"))
            assertEquals(said, 500.0, amountOf(said)!!, 0.0)
    }

    @Test fun `the number guard alone`() {
        assertEquals(" ".repeat(11), VoiceEntry.withoutOtherNumbers("03001234567"))
        assertEquals("bill 500 ka", VoiceEntry.withoutOtherNumbers("bill 500 ka"))
        assertTrue(VoiceEntry.withoutOtherNumbers("invoice 1234 ke 500").trim().endsWith("500"))
        assertFalse("1234" in VoiceEntry.withoutOtherNumbers("invoice 1234 ke 500"))
        assertFalse("77" in VoiceEntry.withoutOtherNumbers("receipt no 77"))
        assertEquals("ali ko 500 diye", VoiceEntry.withoutOtherNumbers("ali ko 500 diye"))
        // An app series with no leading zero (only the series guard blanks it).
        assertFalse("1234" in VoiceEntry.withoutOtherNumbers("inv-1234 ke 500"))
        assertFalse("77" in VoiceEntry.withoutOtherNumbers("s-77 wala bill"))
    }
    // ------------------------------------------------------------------------------------------------ BC-318, BC-319, D15's question

    private fun asked(a: Ask) = a is Ask.DidYouMean && a.unsure == Hudhud.Unsure.SIDE

    private val bareLoan = listOf(
        "ur-Latn" to "Kitna qarz baqi hai",
        "ur-Latn" to "qarz kitna hai",
        "ur-Latn" to "Kitna karza reh gaya hai",
        "ur" to "کتنا قرض باقی ہے",
        "hi" to "कितना क़र्ज़ बाकी है",
        "en" to "how much loan is left",
        "in" to "berapa pinjaman yang tersisa",
        // native read
        "sd" to "ڪيترو قرض باقي آهي",
        "bn" to "কত ঋণ বাকি আছে",
        "ar" to "كم القرض المتبقي",
        "fa" to "چقدر وام مانده است"
    )

    private val whosePending = listOf(
        "ur-Latn" to "Kis ki payment late ho gayi",
        "ur-Latn" to "Kis ka maal ka paisa baqi hai",
        "ur-Latn" to "Kis ki raqam baqi hai",
        "ur" to "کس کی پیمنٹ لیٹ ہو گئی",
        "hi" to "किसकी पेमेंट लेट हो गई",
        "en" to "whose payment is late",
        "en" to "whose payment is pending",
        "in" to "pembayaran siapa yang terlambat",
        // native read
        "sd" to "ڪنهن جي پيمينٽ دير سان آهي",
        "bn" to "কার পেমেন্ট বাকি আছে",
        "ar" to "دفعة من متأخرة",
        "fa" to "پرداخت چه کسی دیر شده"
    )

    @Test fun `a side nobody said is asked first, both sides offered`() {
        for ((lang, said) in bareLoan + whosePending) {
            val a = read(lang, said)
            assertTrue("[$lang] $said -> $a", asked(a))
            val sides = (a as Ask.DidYouMean).options
            assertEquals("[$lang] $said", 2, sides.size)
            assertTrue("[$lang] $said offers the shop's to give", Ask.Figure(Kind.TO_GIVE) in sides)
            assertTrue("[$lang] $said offers the shop's to get",
                Ask.Figure(Kind.TO_GET) in sides || Ask.Figure(Kind.NOT_PAID) in sides)
        }
        // Late is asked as who has not paid against what the shop owes.
        assertEquals(listOf(Ask.Figure(Kind.NOT_PAID), Ask.Figure(Kind.TO_GIVE)), (read("ur-Latn", "Kis ki payment late ho gayi") as Ask.DidYouMean).options)
        assertEquals(listOf(Ask.Figure(Kind.TO_GET), Ask.Figure(Kind.TO_GIVE)), (read("ur-Latn", "Kitna qarz baqi hai") as Ask.DidYouMean).options)
    }

    @Test fun `a side the sentence says is never asked`() {
        val said = listOf(
            "ur-Latn" to "Mera qarz kitna hai" to Ask.Figure(Kind.TO_GIVE),
            "ur-Latn" to "mere upar kitna qarza hai" to Ask.Figure(Kind.TO_GIVE),
            "ur-Latn" to "main kitna qarzdar hun" to Ask.Figure(Kind.TO_GIVE),
            "ur-Latn" to "sab se bara qarzdar kaun hai" to Ask.Figure(Kind.WHO_OWES_MOST),
            "ur-Latn" to "qarz e hasna kitna diya hua hai" to Ask.Qarz,
            "ur-Latn" to "Kaun si payment late ho gayi hai" to Ask.Figure(Kind.NOT_PAID),
            "ur-Latn" to "kis ka paisa phansa hua hai" to Ask.Figure(Kind.NOT_PAID),
            "ur-Latn" to "Kis ki raqam deni hai is hafte" to Ask.Figure(Kind.TO_GIVE),
            "ur-Latn" to "kis ki wusooli baqi hai" to Ask.Figure(Kind.NOT_PAID),
            "ur-Latn" to "Late payment wale kaun hain" to Ask.Figure(Kind.NOT_PAID),
            "en" to "whose money is stuck" to Ask.Figure(Kind.NOT_PAID),
            "en" to "who is late with payment" to Ask.Figure(Kind.NOT_PAID),
            "ar" to "ديون" to Ask.Figure(Kind.TO_GET),
            "ar" to "كم علي من ديون" to Ask.Figure(Kind.TO_GIVE),
            "in" to "utang" to Ask.Figure(Kind.TO_GET),
            "in" to "berapa utang saya" to Ask.Figure(Kind.TO_GIVE)
        )
        for ((ls, want) in said) {
            val (lang, s) = ls
            val a = read(lang, s)
            assertFalse("[$lang] $s was asked", asked(a))
            assertEquals("[$lang] $s", want, inner(a))
        }
        // A named person with a loan is his account, never asked.
        assertTrue(hisAccount(read("ur-Latn", "Ali Raza ka qarz kitna baqi hai")))
    }

    @Test fun `the side-unsure guard alone`() {
        for ((_, s) in bareLoan + whosePending) assertTrue(s, VoiceQuestion.sideUnsure(s))
        for (s in listOf("Mera qarz kitna hai", "Kaun si payment late ho gayi hai", "kis ka paisa phansa hua hai", "kitna udhaar baqi hai",
            "Kis supplier ko payment late ho gayi", "qarz e hasna kitna hai", "Kis ki raqam deni hai", "sab se zyada kis ka baqi hai",
            "logon par mera kitna qarz hai", "Kis ka udhaar bohat dair se baqi hai", "kitne customers ka qarz baqi hai"))
            assertFalse(s, VoiceQuestion.sideUnsure(s))
        // Most and least say the side (who owes the shop most or least): only this guard refuses these (a guess of the sense would
        // otherwise be asked; lesson 129).
        for (s in listOf("sab se zyada kis ka paisa baqi hai", "kis ki payment sab se kam baqi hai", "whose payment is the biggest pending")) assertFalse(s, VoiceQuestion.sideUnsure(s))
        assertTrue(VoiceQuestion.saysLate("Kis ki payment late ho gayi"))
        assertFalse(VoiceQuestion.saysLate("Kitna qarz baqi hai"))
    }

    // ------------------------------------------------------------------------------------------------ BC-336, Arabic's "we pay"

    @Test fun `Arabic we pay is the shop's to give`() {
        // Native read: all of these were written by Claude.
        for (said in listOf("كم باقي ندفع", "كم باقي علينا ندفع", "كم يجب أن ندفع", "ما الذي يجب أن نسدده", "كم ندين للموردين",
            "كم يجب أن أدفع", "كم المبلغ الذي علينا دفعه")) {
            assertEquals(said, Ask.Figure(Kind.TO_GIVE), inner(read("ar", said)))
            assertEquals(said, Kind.TO_GIVE, VoiceQuestion.classify(said))
        }
        // The other side stays the other side.
        assertEquals(Ask.Figure(Kind.TO_GET), inner(read("ar", "كم باقي لنا عند الناس")))
        assertEquals(Ask.Figure(Kind.TO_GET), inner(read("ar", "كم المبلغ الذي يجب أن أستلمه من العملاء")))
    }
}
