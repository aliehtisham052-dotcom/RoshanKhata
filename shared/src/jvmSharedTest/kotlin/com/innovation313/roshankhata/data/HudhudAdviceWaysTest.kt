package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.AdviseOf
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The advisor's three questions in the everyday ways a shopkeeper says them (25 Nov 2026, plan H5, second part), in the nine
 * languages, through the whole pipeline with the shipped sense (lesson 145): the business growing ("is my business growing",
 * "karobar barh raha hai") is the sales' trend; more or less THAN one span before ("pichle hafte se zyada bika?") is the trend of
 * the span after it; better or worse; "than before"; asking or taking money first ("pehle kis se paise maangun"); money paid to
 * the shop next month ("how much will customers pay me next month"). By class, never by sentence (lesson 111): each class is
 * said several ways and in every language. Then each new guard alone, called on [HudhudAdviceAsk] directly so that no other
 * layer can refuse the same sentence (lesson 129). Names and goods are invented (lesson 80).
 *
 * The Sindhi, Bengali, Farsi and Arabic sentences use the words those languages' strings already use (businesses_title, the help
 * pages); they are not a native speaker's read.
 */
class HudhudAdviceWaysTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private val chase = Ask.Advise(AdviseOf.CHASE)
    private val next30 = Ask.Advise(AdviseOf.NEXT_30)
    private fun trend(w: Window = Window.THIS_MONTH) = Ask.Trend(w)

    private fun all(rows: List<Triple<String, String, Ask>>) {
        val wrong = rows.mapNotNull { (lang, said, want) ->
            val got = read(lang, said)
            if (got == want) null else "[$lang] $said  =>  $got  (want $want)"
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    // ------------------------------------------------------------------------------------------------ the classes, nine languages

    @Test fun `the business growing is the sales' trend`() = all(listOf(
        Triple("ur-Latn", "mera karobar barh raha hai ya nahi", trend()),
        Triple("ur-Latn", "dhanda barh raha hai ya ghat raha hai", trend()),
        Triple("ur-Latn", "is mahine karobar kam ho raha hai kya", trend()),
        Triple("en", "is my business growing", trend()),
        Triple("en", "is the business going down this week", trend(Window.THIS_WEEK)),
        Triple("ur", "کیا میرا کاروبار بڑھ رہا ہے", trend()),
        Triple("ur", "دھندا گھٹ رہا ہے یا بڑھ رہا ہے", trend()),
        Triple("hi", "क्या मेरा व्यापार बढ़ रहा है", trend()),
        Triple("hi", "मेरा कारोबार घट रहा है क्या", trend()),
        Triple("bn", "আমার ব্যবসা কি বাড়ছে", trend()),
        Triple("bn", "ব্যবসা কি কমছে", trend()),
        Triple("ar", "هل تجارتي تنمو", trend()),
        Triple("ar", "هل نشاطي التجاري يتراجع", trend()),
        Triple("fa", "آیا کسب و کارم رشد می‌کند", trend()),
        Triple("fa", "کسب‌وکار من رو به کاهش است", trend()),
        Triple("sd", "ڇا منهنجو ڪاروبار وڌي رهيو آهي", trend()),
        Triple("sd", "ڪاروبار گهٽجي رهيو آهي ڇا", trend()),
        Triple("in", "apakah usaha saya berkembang", trend()),
        Triple("in", "apakah bisnis saya turun bulan ini", trend())
    ))

    @Test fun `more or less than one span before is the trend of the span after it`() = all(listOf(
        Triple("ur-Latn", "pichle hafte se zyada bika?", trend(Window.THIS_WEEK)),
        Triple("ur-Latn", "pichle mahine se kam bikri hui kya", trend(Window.THIS_MONTH)),
        Triple("ur-Latn", "kal se zyada sale hui?", trend(Window.TODAY)),
        Triple("ur-Latn", "pichle mahine se sale kam ho rahi hai", trend(Window.THIS_MONTH)),
        Triple("en", "did I sell more than last week", trend(Window.THIS_WEEK)),
        Triple("en", "am I selling more or less than last month", trend(Window.THIS_MONTH)),
        Triple("en", "are my sales up compared to last week", trend(Window.THIS_WEEK)),
        Triple("en", "are sales lower than yesterday", trend(Window.TODAY)),
        Triple("ur", "پچھلے ہفتے سے زیادہ بکا؟", trend(Window.THIS_WEEK)),
        Triple("ur", "پچھلے مہینے سے کم سیل ہوئی کیا", trend(Window.THIS_MONTH)),
        Triple("hi", "पिछले हफ्ते से ज्यादा बिका?", trend(Window.THIS_WEEK)),
        Triple("hi", "क्या पिछले महीने से कम बिक्री हुई", trend(Window.THIS_MONTH)),
        Triple("bn", "গত সপ্তাহের চেয়ে বেশি বিক্রি হয়েছে?", trend(Window.THIS_WEEK)),
        Triple("bn", "গত মাসের চেয়ে কম বিক্রি হয়েছে কি", trend(Window.THIS_MONTH)),
        Triple("ar", "هل بعت أكثر من الأسبوع الماضي", trend(Window.THIS_WEEK)),
        Triple("ar", "هل المبيعات أقل من الشهر الماضي", trend(Window.THIS_MONTH)),
        Triple("fa", "آیا فروش از هفته پیش بیشتر شده", trend(Window.THIS_WEEK)),
        Triple("fa", "فروش از ماه پیش کمتر شده", trend(Window.THIS_MONTH)),
        Triple("sd", "گذريل هفتي کان وڌيڪ وڪرو ٿيو؟", trend(Window.THIS_WEEK)),
        Triple("sd", "گذريل مهيني کان گهٽ وڪرو ٿيو ڇا", trend(Window.THIS_MONTH)),
        Triple("in", "apakah penjualan lebih banyak dari minggu lalu", trend(Window.THIS_WEEK)),
        Triple("in", "apakah penjualan lebih sedikit dari bulan lalu", trend(Window.THIS_MONTH))
    ))

    @Test fun `better or worse, and than before`() = all(listOf(
        Triple("ur-Latn", "sale pehle se behtar hai ya buri", trend()),
        Triple("ur-Latn", "pehle se zyada bik raha hai?", trend()),
        Triple("ur-Latn", "meri bikri kharab ho rahi hai", trend()),
        Triple("en", "are my sales getting better or worse", trend()),
        Triple("en", "am I selling more than before", trend()),
        Triple("ur", "کیا میری سیل بہتر ہو رہی ہے", trend()),
        Triple("ur", "سیل پہلے سے زیادہ ہے؟", trend()),
        Triple("hi", "क्या मेरी बिक्री बेहतर हो रही है", trend()),
        Triple("hi", "बिक्री पहले से ज्यादा है?", trend()),
        Triple("bn", "আমার বিক্রি কি খারাপ হচ্ছে", trend()),
        Triple("bn", "বিক্রি আগের চেয়ে বেশি?", trend()),
        Triple("ar", "هل مبيعاتي في زيادة", trend()),
        Triple("fa", "آیا فروش من بهتر شده", trend()),
        Triple("fa", "فروش از قبل بیشتر شده؟", trend()),
        Triple("sd", "ڇا منهنجو وڪرو بهتر ٿي رهيو آهي", trend()),
        Triple("in", "apakah penjualan saya membaik", trend()),
        Triple("in", "penjualan lebih banyak dari sebelumnya?", trend())
    ))

    @Test fun `whom to ask or take money from first is the chase`() = all(listOf(
        Triple("ur-Latn", "pehle kis se paise maangun", chase),
        Triple("ur-Latn", "sab se pehle kis ka udhaar wasool karun", chase),
        Triple("ur-Latn", "kis se pehle paise lene chahiye", chase),
        Triple("en", "who do I ask for money first", chase),
        Triple("en", "whose dues should I collect first", chase),
        Triple("en", "who should I take money from first", chase),
        Triple("ur", "پہلے کس سے پیسے مانگوں", chase),
        Triple("ur", "سب سے پہلے کس سے پیسے لینے چاہئیں", chase),
        Triple("hi", "पहले किससे पैसे मांगूं", chase),
        Triple("hi", "सबसे पहले किससे पैसे लेने चाहिए", chase),
        Triple("bn", "আগে কার কাছে টাকা চাইব", chase),
        Triple("bn", "আগে কার কাছ থেকে টাকা নেব", chase),
        Triple("ar", "من يجب أن أطالبه بالدفع أولاً", chase),
        Triple("ar", "ممن أحصل المال أولاً", chase),
        Triple("fa", "اول از چه کسی طلب کنم", chase),
        Triple("fa", "اول از کی پول بگیرم", chase),
        Triple("sd", "پهريان ڪنهن کان پيسا گهران", chase),
        Triple("sd", "پهريان ڪنهن کان پيسا وٺان", chase),
        Triple("in", "tagih siapa duluan", chase),
        Triple("in", "siapa yang harus saya tagih dulu", chase)
    ))

    @Test fun `money to come next month, or paid to the shop, is the next thirty days`() = all(listOf(
        Triple("ur-Latn", "agle mahine paisa kitna aayega", next30),
        Triple("ur-Latn", "agle mahine kitni wasooli hogi", next30),
        Triple("ur-Latn", "aane wale mahine mein kitni raqam aayegi", next30),
        Triple("en", "how much will customers pay me next month", next30),
        Triple("en", "expected collections in the next 30 days", next30),
        Triple("en", "how much money will I get next month", next30),
        Triple("ur", "اگلے مہینے کتنے پیسے آئیں گے", next30),
        Triple("ur", "اگلے مہینے کتنی وصولی ہوگی", next30),
        Triple("hi", "अगले महीने कितना पैसा आएगा", next30),
        Triple("hi", "अगले महीने कितनी वसूली होगी", next30),
        Triple("bn", "আগামী মাসে কত টাকা আসবে", next30),
        Triple("ar", "كم من المال سيأتي الشهر القادم", next30),
        Triple("fa", "ماه آینده چقدر پول می‌آید", next30),
        Triple("sd", "ايندڙ مهيني ڪيترا پيسا ايندا", next30),
        Triple("in", "berapa uang masuk bulan depan", next30)
    ))

    // ------------------------------------------------------------------------------------------------ what stays as it was

    @Test fun `the sentences beside these keep their readings`() {
        // Two spans the sentence names itself are its own two figures, as the owner's list reads them.
        assertFalse(read("ur-Latn", "Is mahine pichle mahine se zyada bika") is Ask.Trend)
        assertFalse(read("ur-Latn", "Kal se aaj ki sale zyada hai ya kam") is Ask.Trend)
        assertFalse(read("ur", "کیا اس مہینے سیل پچھلے مہینے سے زیادہ ہے") is Ask.Trend)
        // The most is the best sellers, never a comparison.
        assertEquals(Ask.TopGoods(Window.LAST_WEEK), read("ur-Latn", "pichle hafte sab se zyada kya bika"))
        assertFalse(read("sd", "گذريل هفتي سڀ کان وڌيڪ ڇا وڪرو ٿيو") is Ask.Trend)
        // A bare up or down of the sales is still the sales page's own question (the owner's list).
        assertFalse(read("ur-Latn", "Sale gir rahi hai ya barh rahi hai") is Ask.Trend)
        // Much sale is no way.
        assertFalse(read("ur-Latn", "meri sale zyada hai") is Ask.Trend)
        assertFalse(read("ur", "میری سیل زیادہ ہے") is Ask.Trend)
        // The other shops' page keeps its business words.
        assertEquals(Ask.Help("faq_business"), read("ur-Latn", "Naya karobar add karna hai"))
        assertEquals(Ask.Help("faq_business"), read("ur-Latn", "karobar ka naam badlo"))
        // Asking first without money is not the chase; a named customer is his own page.
        assertFalse(read("en", "who should I ask first") is Ask.Advise)
        assertFalse(read("ur-Latn", "Ali Raza se pehle paise lene hain") is Ask.Advise)
        // What the shop pays next month is money going out.
        assertFalse(read("en", "how much do I pay next month") is Ask.Advise)
    }

    // ------------------------------------------------------------------------------------------------ each guard alone (lesson 129)

    private val none: (String) -> Boolean = { false }

    @Test fun `a comparison names one span, the other is the sentence's own`() {
        // Two weeks named: the sentence compares its own two figures.
        assertNull(HudhudAdviceAsk.trend("is hafte pichle hafte se zyada bika", none, none))
        // Today and yesterday both named, with "than before" too.
        assertNull(HudhudAdviceAsk.trend("aaj kal pehle se zyada bika", none, none))
        // One week named: a comparison.
        assertEquals(trend(Window.THIS_WEEK), HudhudAdviceAsk.trend("pichle hafte se zyada bika", none, none))
    }

    @Test fun `more or less is a way only after than`() {
        // No "than": "pichle hafte zyada bika" is how much sold last week, not a comparison.
        assertNull(HudhudAdviceAsk.trend("pichle hafte zyada bika", none, none))
    }

    @Test fun `the most is never a comparison`() {
        // "sab" is also a little word, so only the superlative guard refuses this.
        assertNull(HudhudAdviceAsk.trend("pichle hafte se sab zyada bika", none, none))
    }

    @Test fun `than a span before is told for the span after it`() {
        assertEquals(trend(Window.THIS_WEEK), HudhudAdviceAsk.trend("did I sell more than last week", none, none))
        assertEquals(trend(Window.TODAY), HudhudAdviceAsk.trend("are sales lower than yesterday", none, none))
        assertEquals(trend(Window.THIS_YEAR), HudhudAdviceAsk.trend("pichle saal se zyada bika", none, none))
        // With no "than", the span said is the span told.
        assertEquals(trend(Window.LAST_WEEK), HudhudAdviceAsk.trend("sales trend last week", none, none))
    }

    @Test fun `Urdu's more is Arabic's increase only in an Arabic sentence`() {
        assertNull(HudhudAdviceAsk.trend("میری سیل زیادہ ہے", none, none))
        assertEquals(trend(), HudhudAdviceAsk.trend("هل مبيعاتي في زيادة", none, none))
    }

    @Test fun `the business is the asker's own, and a word of the question`() {
        // No "my", no span, no "trend": the business alone makes it the asker's.
        assertEquals(trend(), HudhudAdviceAsk.trend("karobar barh raha hai", none, none))
        // The business is the only word of the shop's selling here.
        assertEquals(trend(), HudhudAdviceAsk.trend("dhanda ghat raha hai", none, none))
    }

    @Test fun `than before is the comparison's own words`() {
        // Without "pehle se" taken as the comparison, "pehle" is no word of this reader.
        assertEquals(trend(), HudhudAdviceAsk.trend("sale pehle se behtar hai", none, none))
    }

    @Test fun `the reader's own selling word is never a name`() {
        // Arabic "بعت" (I sold) meets a customer called Butt by its letters.
        assertEquals(trend(Window.THIS_WEEK), HudhudAdviceAsk.trend("هل بعت أكثر من الأسبوع الماضي", { it == VoiceQuestion.normalise("بعت") }, none))
        // Any other word of the book's names still refuses it.
        assertNull(HudhudAdviceAsk.trend("meri sale barh rahi hai kafi", { it == "kafi" }, none))
    }

    @Test fun `Indonesian kemarin beside a week is that week's last`() {
        assertEquals(trend(Window.THIS_WEEK), HudhudAdviceAsk.trend("apakah penjualan lebih banyak dari minggu kemarin", none, none))
    }

    @Test fun `asking or taking is the chase only beside money`() {
        assertNull(HudhudAdviceAsk.chase("who should I ask first", none, none))
        assertEquals(chase, HudhudAdviceAsk.chase("who should I ask for money first", none, none))
    }

    @Test fun `money paid to the shop is coming in, money the shop pays is not`() {
        assertEquals(next30, HudhudAdviceAsk.nextDays("how much will customers pay me next month", none, none))
        assertNull(HudhudAdviceAsk.nextDays("how much will I pay next month", none, none))
    }
}
