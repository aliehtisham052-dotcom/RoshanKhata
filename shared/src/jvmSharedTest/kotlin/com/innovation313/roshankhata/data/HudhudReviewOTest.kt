package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review O (15 Nov 2026): 1,167 everyday sentences written fresh in the nine languages — the trades (kiryana, pharmacy, seeds and
 * sprays, clothes, mobiles, wholesale, a restaurant, hardware), the speakers (owner, manager, cashier, a helper), short asks and long
 * rambling ones, voice garbles, mixed languages and the dangerous asks. Each class of miss that was fixed by a rule has its test here,
 * read through the whole pipeline with the shipped sense and the app's real help pages (lesson 145), with a guard or two beside it
 * that the fix must not take. The readings are HudhudShown's; a person or a product is held by its id. Names and goods are invented.
 */
class HudhudReviewOTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private val pages = HashMap<String, List<Hudhud.Topic>>()

    /** One sentence: its language, the words, the sentence before it (a follow-up) or "", the reading and the ids it must name. */
    private class Row(val lang: String, val said: String, val before: String, val shown: String, val ids: String)

    private fun ids(a: Hudhud.Ask): String = Regex("ids=\\[([0-9, ]*)\\]").find(a.toString())?.groupValues?.get(1)?.replace(" ", "") ?: ""

    private fun check(vararg rows: Row) {
        val wrong = ArrayList<String>()
        for (r in rows) {
            val topics = pages.getOrPut(r.lang) { HudhudRealPages.forApp(r.lang) }
            val before = r.before.takeIf { it.isNotEmpty() }?.let { Hudhud.understand(it, names, topics, r.lang, goods, null) }
            val got = Hudhud.understand(r.said, names, topics, r.lang, goods, before)
            val shown = HudhudShown.of(got)
            if (shown != r.shown || (r.ids.isNotEmpty() && ids(got) != r.ids)) wrong += "  [${r.lang}] ${r.said}  =>  $got  (want ${r.shown} ${r.ids})"
        }
        assertTrue("read otherwise:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `which way money is owed, said in the accountant's words or the language's own, is read the right way round`() = check(
        Row("en", "receivables", "", "Figure:TO_GET", ""),
        Row("en", "payables", "", "Figure:TO_GIVE", ""),
        Row("en", "total amount customers still have to pay me", "", "Figure:TO_GET", ""),
        Row("ur-Latn", "supplier payable kitna hai", "", "Figure:TO_GIVE", ""),
        Row("ur-Latn", "seth sahab poochh rahe hain logon se kitna wusool karna hai", "", "Figure:TO_GET", ""),
        Row("ur", "ہم نے آگے کتنا ادا کرنا ہے", "", "Figure:TO_GIVE", ""),
        Row("bn", "খদ্দেরদের কাছে কত পাব", "", "Figure:TO_GET", ""),
        Row("fa", "مشتری ها روی هم چقدر بدهکارند", "", "Figure:TO_GET", ""),
        Row("ar", "كم المبلغ المتبقي على الزبائن", "", "Figure:TO_GET", ""),
        Row("ur-Latn", "paise kitne ane hain", "", "Figure:TO_GET", ""),
        Row("ur-Latn", "kitne paise aane hain market se", "", "Figure:TO_GET", ""),
        Row("ur-Latn", "jo paise aane the un mein se kitne aaye", "", "Collected", "")
    )

    @Test fun `a how-to opens the page of the thing it names, not the page its verb happens to share`() = check(
        Row("ur-Latn", "naya gahak kaise daalun", "", "Help:screen_khata", ""),
        Row("ur-Latn", "backup kaise lete hain", "", "Help:faq_backup", ""),
        Row("ur-Latn", "app ki zabaan kaise badlun", "", "Help:faq_language", ""),
        Row("ur-Latn", "app par taala kaise lagaun", "", "Help:faq_lock", ""),
        Row("ur-Latn", "installment kaise lagaun", "", "Help:intro_plans", ""),
        Row("en", "how do I add a new customer", "", "Help:screen_khata", ""),
        Row("en", "how do I back up my data", "", "Help:faq_backup", ""),
        Row("ar", "كيف أقفل التطبيق", "", "Help:faq_lock", ""),
        Row("ar", "كيف أضيف زبونا جديدا", "", "Help:screen_khata", ""),
        Row("fa", "چطور برنامه را قفل کنم", "", "Help:faq_lock", ""),
        Row("fa", "چطور پیش فاکتور بسازم", "", "Help:intro_documents", ""),
        Row("bn", "নতুন খদ্দের কিভাবে যোগ করব", "", "Help:screen_khata", ""),
        Row("in", "cara kunci aplikasi", "", "Help:faq_lock", ""),
        Row("in", "ganti HP gimana pindahin data", "", "Help:faq_restore", ""),
        Row("ur-Latn", "naye phone mein backup kaise laun", "", "Help:faq_restore", ""),
        Row("en", "how do I restore my backup on a new phone", "", "Help:faq_restore", ""),
        Row("in", "cara bikin penawaran harga", "", "Help:intro_documents", ""),
        Row("hi", "नया ग्राहक कैसे जोड़ूं", "", "Help:screen_khata", ""),
        Row("ur", "نیا گاہک کیسے ڈالوں", "", "Help:screen_khata", ""),
        Row("sd", "نئون گراهڪ ڪيئن شامل ڪريان", "", "Help:screen_khata", ""),
        Row("in", "bagaimana cara membuat kunci ahli waris", "", "Help:screen_waris", "")
    )

    @Test fun `a delete said as an order is the delete page, never the person or entries it names`() = check(
        Row("ur-Latn", "Ali Raza ka khata delete kar do", "", "Help:faq_delete", ""),
        Row("ur-Latn", "Ali Raza ki aakhri entry hata do", "", "Help:faq_delete", ""),
        Row("en", "delete Ali Raza's account", "", "Help:faq_delete", ""),
        Row("en", "wipe all the entries", "", "Help:faq_delete", ""),
        Row("hi", "सारी एंट्री मिटा दो", "", "Help:faq_delete", ""),
        Row("fa", "حساب علی رضا را پاک کن", "", "Help:faq_delete", ""),
        Row("ar", "احذف حساب علي رضا", "", "Help:faq_delete", ""),
        Row("in", "hapus akun Ali Raza", "", "Help:faq_delete", ""),
        Row("bn", "আলী রাজার খাতা মুছে দাও", "", "Help:faq_delete", ""),
        Row("ur", "علی رضا کا کھاتہ ڈیلیٹ کر دو", "", "Help:faq_delete", ""),
        Row("ur-Latn", "kisne kya delete kiya", "", "Activity", ""),
        Row("ur-Latn", "ghalti se entry delete ho gayi wapas kaise aayegi", "", "Help:faq_delete", "")
    )

    @Test fun `the order's do is never the amount two`() = check(
        Row("ur-Latn", "Saleem Bhai ka udhaar maaf kar do", "", "Parties", "5"),
        Row("ur", "سلیم بھائی کا ادھار معاف کر دو", "", "Parties", "5"),
        Row("ur-Latn", "mere liye Ali Raza ko WhatsApp bhej do ke paise de", "", "Parties", "1"),
        Row("ur-Latn", "Ali Raza ko do sau diye", "", "Entry", "1"),
        Row("ur-Latn", "Ali Raza ko do rupay diye", "", "Entry", "1"),
        Row("ur-Latn", "Ali Raza ko do hazar do", "", "Parties", "1")
    )

    @Test fun `a supplier's bills not paid are the bills that wait, in Arabic and Farsi invoice words too`() = check(
        Row("en", "unpaid supplier bills", "", "Status:BILLS", ""),
        Row("ar", "فواتير الموردين غير المدفوعة", "", "Status:BILLS", ""),
        Row("fa", "فاکتورهای پرداخت نشده تامین کننده", "", "Status:BILLS", ""),
        Row("ur-Latn", "company ka bill kitna reh gaya", "", "Status:BILLS", ""),
        Row("ur-Latn", "supplier bill ka number kya hai jo pending hai", "", "Paper", ""),
        Row("ur-Latn", "Kaun sa bill baqi hai", "", "Help:intro_cashbook", ""),
        Row("en", "which invoices are unpaid", "", "Status:INVOICES", "")
    )

    @Test fun `customers who stopped coming and the oldest money have their own pages`() = check(
        Row("ur-Latn", "kaun sa gahak aana band ho gaya", "", "Help:screen_lapsed", ""),
        Row("en", "which customers stopped coming", "", "Help:screen_lapsed", ""),
        Row("in", "pelanggan mana yang sudah tidak datang", "", "Help:screen_lapsed", ""),
        Row("hi", "कौन सा ग्राहक आना बंद हो गया", "", "Help:screen_lapsed", ""),
        Row("ur-Latn", "kis ka paisa sab se purana hai", "", "Help:screen_aging", ""),
        Row("hi", "किसका पैसा सबसे पुराना है", "", "Help:screen_aging", "")
    )

    @Test fun `whether the shop is losing money is its profit`() = check(
        Row("ur-Latn", "nuqsan to nahi ho raha", "", "Figure:PROFIT_MONTH", ""),
        Row("ur-Latn", "dukaan ghaate mein to nahi ja rahi", "", "Figure:PROFIT_MONTH", ""),
        Row("en", "are we making a loss", "", "Figure:PROFIT_MONTH", ""),
        Row("ur-Latn", "pichle mahine nuqsan hua kya", "", "Period:PROFIT", ""),
        Row("ur-Latn", "kis cheez par nuqsan hai", "", "Help:screen_insights", ""),
        Row("ur-Latn", "Ali Raza se nuqsan hua", "", "Parties", "1"),
        Row("ur-Latn", "maal ka nuqsan kitna hua", "", "Help:screen_insights", "")
    )

    @Test fun `Bengali please is not the tax`() = check(
        Row("bn", "দয়া করে আজকের বিক্রি বলো", "", "Figure:SALES_TODAY", ""),
        Row("bn", "দয়া করে মোট বাকি বলো", "", "Figure:TO_GET", ""),
        Row("bn", "আজ কত কর হয়েছে", "", "Counter", "")
    )

    @Test fun `Arabic I sold names nobody, even to the rules alone`() {
        val topics = HudhudRealPages.forApp("ar")
        for (s in listOf("بعت كثير اليوم", "كم بعت هذا الشهر")) {
            val got = Hudhud.byRules(s, names, topics, "ar", goods)
            assertTrue("$s => $got", got !is Hudhud.Ask.Parties && got !is Hudhud.Ask.Person)
        }
    }

    @Test fun `Arabic I sold is the sales, never a customer called Butt`() = check(
        Row("ar", "كم بعت هذا الشهر", "", "Figure:SALES_MONTH", ""),
        Row("ar", "بعت كثير اليوم", "", "Figure:SALES_TODAY", ""),
        Row("ar", "كم بعت اليوم", "", "Figure:SALES_TODAY", "")
    )

    @Test fun `a product's cost, stock and suppliers are asked by the shape of the sentence`() = check(
        Row("ar", "بكم نشتري Sugar", "", "Goods:COST", "102"),
        Row("fa", "Sugar را چند می خریم", "", "Goods:COST", "102"),
        Row("bn", "Sugar কত দামে কিনি", "", "Goods:COST", "102"),
        Row("fa", "چند کیسه Urea مانده", "", "Goods:STOCK", "100"),
        Row("sd", "Urea جو اسٽاڪ ڪيترو آهي", "", "Goods:STOCK", "100"),
        Row("ar", "من يورد Paracetamol", "", "Goods:SUPPLIERS", "103"),
        Row("fa", "Paracetamol را از کی می گیریم", "", "Goods:SUPPLIERS", "103"),
        Row("ur-Latn", "Urea kitne din chal jayegi", "", "Goods:COVER", "100")
    )

    @Test fun `who is asking is taken out of the question`() = check(
        Row("ur-Latn", "main cashier hun mujhe aaj ki sale batao", "", "Figure:SALES_TODAY", ""),
        Row("ur", "میں کیشیئر ہوں مجھے آج کی سیل بتاؤ", "", "Figure:SALES_TODAY", ""),
        Row("hi", "मैं कैशियर हूं मुझे आज की सेल बताओ", "", "Figure:SALES_TODAY", ""),
        Row("sd", "آئون ڪيشيئر آهيان اڄ جي سيل ٻڌايو", "", "Figure:SALES_TODAY", ""),
        Row("fa", "من مدیر هستم سود این ماه را بگو", "", "Figure:PROFIT_MONTH", ""),
        Row("en", "I'm the cashier, tell me today's sales", "", "Figure:SALES_TODAY", ""),
        Row("ur-Latn", "is mahine mein cashier ne kitna becha", "", "Counter", "")
    )

    @Test fun `a follow-up in Arabic or Farsi with its pronoun joined to the word carries on`() = check(
        Row("ar", "ورقم هاتفه", "حساب علي رضا", "Person:PHONE", "1"),
        Row("fa", "و شماره اش", "حساب علی رضا", "Person:PHONE", "1"),
        Row("ar", "وعنوانه", "كم سيدفع سليم بهائي", "Person:DETAILS", "5"),
        Row("fa", "و آدرسش", "سلیم بهائی چقدر می دهد", "Person:DETAILS", "5"),
        Row("ar", "وسعره", "مخزون Urea", "Goods:PRICE", "100"),
        Row("fa", "و قیمتش", "موجودی Urea", "Goods:PRICE", "100"),
        Row("ar", "وDAP", "سعر Urea", "Goods:PRICE", "101"),
        Row("ur-Latn", "aur uska pata", "Saleem Bhai kitna dega", "Person:DETAILS", "5"),
        Row("fa", "هفته گذشته؟", "Ali Raza ne is hafte kitna diya", "Moved", "1")
    )

    @Test fun `a PIN, a secret number or a recovery code is the lock's page`() = check(
        Row("ar", "ما هو رقم سر المالك", "", "Help:faq_lock", ""),
        Row("ur-Latn", "recovery code kya hai", "", "Help:faq_lock", ""),
        Row("ar", "نسيت رقم الكاشير السري", "", "Help:faq_lock", "")
    )

    @Test fun `the smaller everyday phrasings read as meant`() = check(
        Row("ur-Latn", "staff ko advance kitna diya", "", "Status:STAFF", ""),
        Row("ur-Latn", "aj ka munafa kitna", "", "Period:PROFIT", ""),
        Row("ur-Latn", "aj ki sel", "", "Figure:SALES_TODAY", ""),
        Row("sd", "هن مهيني ڪهڙي شيءِ سڀ کان وڌيڪ وڪاڻي", "", "TopGoods", ""),
        Row("ar", "ما أكثر شيء بيع هذا الشهر", "", "TopGoods", ""),
        Row("ur-Latn", "aaj din kaisa raha", "", "Today", ""),
        Row("ur-Latn", "is mahine kitna udhaar diya gaya", "", "Activity", ""),
        Row("ur-Latn", "kal kitna maal wapis aaya", "", "Returns", ""),
        Row("ur-Latn", "kal kya wapis aaya", "", "Returns", ""),
        Row("ur", "کتنی انوائسز کا پیسہ نہیں آیا", "", "Status:INVOICES", ""),
        Row("in", "pelanggan terhapus tidak sengaja", "", "Help:screen_bin", ""),
        Row("en", "before I go home tell me how much cash should actually be in the drawer", "", "Drawer", ""),
        Row("ur-Latn", "kin logon ne kaafi dino se paisa nahi diya", "", "Figure:NOT_PAID", ""),
        Row("ur-Latn", "Aaj kitne paise wapas aaye", "", "Collected", ""),
        Row("ur-Latn", "Kaun sa cheque wapas aaya", "", "Status:CHEQUES", ""),
        Row("ur-Latn", "Agle hafte kitne paise aane hain", "", "Help:screen_cashflow", ""),
        Row("ur-Latn", "Cheque se kitna paisa aane wala hai", "", "Status:CHEQUES", ""),
        Row("ur", "آج کی ٹوٹل سیلز", "", "Figure:SALES_TODAY", ""),
        Row("ur", "عملے کو ایڈوانس کتنا دیا", "", "Status:STAFF", "")
    )
}
