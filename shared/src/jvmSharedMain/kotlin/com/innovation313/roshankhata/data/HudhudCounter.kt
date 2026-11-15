package com.innovation313.roshankhata.data

/**
 * The counter's own books, asked for in words (15 Nov 2026, the catalog's GAP group "counter money and stock":
 * sales, sale_items, sale_payments, sale_returns, sale_return_items, shifts, loyalty, day_close, stock_adjustments).
 *
 * What a shopkeeper asks of the till — "aaj ka sab se bada bill", "kis cashier ne void kiya", "yesterday's drawer count and
 * the difference", "who got loyalty points", "kitna maal kharab likha gaya", "last stock count", "kis wajah se return hua" —
 * is read here, by the rules only: the bank has no meaning for any of them, so [Hudhud.decide] never lets the learnt sense
 * turn a reading made here (lesson 145). [read] answers which question, or null; the books are read by the phone's
 * `HudhudCounterFacts`, behind REPORTS (every sum, list and "who wrote it" here is the owner's or a manager's, the counter
 * screens themselves being open to a cashier: the card says so).
 *
 * Plain Kotlin on purpose: the recogniser and the groupings below are run by the local tests (lesson 93), the SQL and
 * the card are CI's.
 */
object HudhudCounter {

    // ------------------------------------------------------------------------------------- which question

    /**
     * The question [joined] (the sentence, in Hudhud's spelling) asks of the till, or null when it asks none of them.
     *
     * [said] are its words; [how] says it asks how to do something (a help page's question); [supplierSide] that it
     * names a supplier, a company or goods bought (the supplier's bills are another page); [named] that a customer
     * or a product was found in it. The order is the order of the most specific question first: loyalty, the stock's
     * own books, the shifts and the drawer's count, the returns, the voids, then the day's tickets.
     */
    internal fun read(joined: String, said: List<String>, how: Boolean, supplierSide: Boolean, spanOwnsLast: Boolean = false): Hudhud.CounterFacet? {
        fun has(list: List<String>) = VoiceQuestion.saysAny(joined, list)
        // "Return ka paisa kaise diya": a how in the past tense asks the way it WAS done, not how to do it.
        val askedAsPast = has(RETURN_STRONG) && has(PAST_GIVEN)
        if ((how && !askedAsPast) || said.isEmpty()) return null
        val make = has(MAKE_WORDS) && !askedAsPast
        // ---- loyalty points
        if (has(LOYALTY) && !make) return Hudhud.CounterFacet.LOYALTY
        val goods = has(GOODS_WORDS)
        // ---- the stock's own books
        if (has(STOCK_COUNT) && !make && (has(LAST_OR_WHEN) || has(WHO_WORDS) || has(DIFFERENCE))) return Hudhud.CounterFacet.LAST_COUNT
        if ((has(STOCK_WORDS) || goods) && has(COUNT_STRICT) && !make && !has(DRAWER_WORDS) && (has(LAST_OR_WHEN) || has(WHO_WORDS) || has(DIFFERENCE))) return Hudhud.CounterFacet.LAST_COUNT
        if (has(STOCK_ADJUST) && !make && !has(TICKET)) return Hudhud.CounterFacet.STOCK_CHANGES
        if (has(STOCK_WORDS) && has(CHANGE_WORDS) && !make && !has(TICKET) && !has(RETURN_WORDS)) return Hudhud.CounterFacet.STOCK_CHANGES
        val ret = has(RETURN_STRONG)
        val otherPaper = has(OTHER_PAPERS)
        if (has(WRITE_OFF) && !make && !supplierSide && !ret && !has(TICKET) &&
            (goods || has(WRITE_VERBS) || has(STOCK_WORDS) || has(HOW_MUCH))
        ) return Hudhud.CounterFacet.WRITTEN_OFF
        // ---- shifts and the drawer's count
        if (has(SHIFT) && !make) {
            return if (has(DIFFERENCE) || has(SHORT_WORDS)) Hudhud.CounterFacet.SHIFT_SHORT else Hudhud.CounterFacet.SHIFTS
        }
        if (!make && !has(TICKET) && ((has(DRAWER_WORDS) && (has(COUNT_STRICT) || has(DIFFERENCE) || has(SHORT_WORDS))) ||
                (has(CLOSE_THE_DAY) && (has(DIFFERENCE) || has(COUNT_STRICT) || has(SHORT_WORDS)))) &&
            (has(LAST) || has(WHEN_STRICT) || has(YESTERDAY_WORDS) || has(WHO_WORDS))
        ) return Hudhud.CounterFacet.DAY_COUNT
        // ---- goods that came back
        val void = has(VOID)
        if (ret && !supplierSide && !make && !otherPaper) {
            if (void) return Hudhud.CounterFacet.VOIDED
            if (has(REASON)) return Hudhud.CounterFacet.RETURN_REASONS
            if (has(REFUND_WAY) || has(WAYS)) return Hudhud.CounterFacet.RETURN_WAYS
            if (goods && (has(WHICH_WHAT) || has(CAME_BACK))) return Hudhud.CounterFacet.RETURN_GOODS
        }
        // "Which goods came back today": the goods and the words for coming back (said with no word of the return block's own).
        if (goods && has(CAME_BACK_STRICT) && !supplierSide && !make && !otherPaper && !has(TICKET)) return Hudhud.CounterFacet.RETURN_GOODS
        // ---- cancelled bills
        val ticket = has(TICKET) || saleOne(said)
        val counter = ticket || has(COUNTER_CTX)
        val ofSales = has(SALES_WORDS)
        if (void && !supplierSide && !make && !has(OTHER_PAPERS) && (ticket || ofSales || has(COUNTER_CTX) || ret || has(CASHIER) || has(WHO_WORDS))) {
            return Hudhud.CounterFacet.VOIDED
        }
        // ---- the day's counter bills
        if (supplierSide || make || otherPaper) return null
        if (has(DISCOUNT) && !(has(ROUND_OFF) && !has(DISCOUNT_NO_OFF)) && !has(LIMIT_WORDS) && (counter || has(HOW_MUCH) || has(WHO_WORDS) || has(WHICH_WHAT))) {
            return Hudhud.CounterFacet.DISCOUNTS
        }
        if ((has(TAX) || has(ROUND_OFF)) && (counter || has(TAX_MONEY)) && !has(TAX_NUMBER) && !has(DOCS_ONLY)) return Hudhud.CounterFacet.TAX
        if (has(CASHIER) && (has(SELL_VERBS) || has(BIG_SIZE) || has(WISE) || has(WHO_WORDS))) {
            return Hudhud.CounterFacet.BY_CASHIER
        }
        if (has(COUNTER_CTX) && has(ON_ACCOUNT)) return Hudhud.CounterFacet.ON_ACCOUNT
        if (has(COUNTER_CTX) && (has(WAYS) || has(REFERENCE))) return Hudhud.CounterFacet.PAY_WAYS
        if (ticket && has(LAST) && !spanOwnsLast && !has(DEADLINE) && !has(CAME_WORDS) && (has(DETAIL_WORDS) || has(COUNTER_CTX) || has(TICKET_STRONG) || saleOne(said))) {
            return Hudhud.CounterFacet.LAST_TICKET
        }
        if (ticket && !goods && !has(CAME_WORDS) && has(BIG_SIZE)) return Hudhud.CounterFacet.BIGGEST
        return null
    }

    internal fun defaultWindow(f: Hudhud.CounterFacet): VoiceQuestion.Window? = when (f) {
        Hudhud.CounterFacet.LAST_COUNT, Hudhud.CounterFacet.LAST_TICKET, Hudhud.CounterFacet.DAY_COUNT -> null
        Hudhud.CounterFacet.LOYALTY, Hudhud.CounterFacet.WRITTEN_OFF, Hudhud.CounterFacet.STOCK_CHANGES -> VoiceQuestion.Window.THIS_MONTH
        Hudhud.CounterFacet.SHIFTS, Hudhud.CounterFacet.SHIFT_SHORT -> VoiceQuestion.Window.THIS_WEEK
        else -> VoiceQuestion.Window.TODAY
    }

    /** The sentence says loyalty points (any language): used when a customer is named, so his points are read, not his balance. */
    internal fun loyaltyWord(joined: String): Boolean = VoiceQuestion.saysAny(joined, LOYALTY)

    /** A sale said in the singular — one counter bill. In the plural it is the figure ("sales"), which is not read here. */
    private fun saleOne(said: List<String>): Boolean = said.any { it in SALE_ONE }

    private val SALE_ONE = setOf("sale", "سیل", "सेल", "bikri", "bikree", "بکری", "बिक्री")

    // ------------------------------------------------------------------------------------- the words

    private val TICKET = listOf(
        "bill", "bills", "bil", "ticket", "tickets", "receipt", "receipts", "parchi", "parchiyan", "parchee", "raseed", "raseedein",
        "بل", "بلز", "بلوں", "ٹکٹ", "پرچی", "پرچیاں", "رسید", "رسیدیں",
        "بل", "ٽڪيٽ", "پرچي", "رسيد", "بلن",
        "فاتورة", "فاتوره", "فواتير", "إيصال", "ايصال", "إيصالات", "تذكرة", "بيع", "عملية بيع", "عمليات بيع",
        "فاکتور", "فاکتورها", "رسید", "قبض فروش",
        "बिल", "बिलों", "टिकट", "पर्ची", "पर्चियाँ", "रसीद", "रसीदें",
        "বিল", "বিলগুলো", "টিকিট", "রসিদ", "স্লিপ", "বিক্রি", "বিক্রয়",
        "struk", "nota", "bon", "tiket", "transaksi"
    )

    /** A word that is a bill and nothing else ("bill", "ticket", "receipt"), as against "sale" or "transaction", which are also the figure. */
    private val TICKET_STRONG = listOf(
        "bill", "bills", "ticket", "tickets", "receipt", "receipts", "parchi", "parchiyan", "raseed",
        "بل", "بلز", "بلوں", "ٹکٹ", "پرچی", "رسید", "ٽڪيٽ", "پرچي", "رسيد", "فاتورة", "فاتوره", "فواتير", "إيصال", "ايصال", "فاکتور", "فاکتورها",
        "बिल", "टिकट", "पर्ची", "रसीद", "বিল", "টিকিট", "রসিদ", "struk", "nota", "bon", "tiket"
    )

    private val SALE_STRONG = listOf("counter sale", "counter sales", "counter bill", "counter bills", "pos", "کاؤنٹر سیل", "काउंटर सेल")

    private val COUNTER_CTX = listOf(
        "counter", "counters", "pos", "till", "cash counter", "cashier",
        "کاؤنٹر", "کاونٹر", "کانٹر", "ڪائونٽر", "کائونٹر", "الكاونتر", "كاونتر", "الكاشير", "كاشير", "کانتر", "پیشخوان", "صندوق فروش",
        "काउंटर", "কাউন্টার", "kasir", "kassa"
    )

    private val CASHIER = listOf(
        "cashier", "cashiers", "counter staff", "salesman", "salesmen", "salesperson", "kaun sa banda", "kis banday", "kis banday ne",
        "کیشیئر", "کیشئر", "کیشیر", "کیشیرز", "سیلزمین", "سیلز مین", "ڪيشيئر", "ڪئشيئر",
        "أمين الصندوق", "امين الصندوق", "كاشير", "كاشيرات", "بائع", "البائع", "صندوقدار", "کاشیر", "فروشنده",
        "कैशियर", "सेल्समैन", "ক্যাশিয়ার", "বিক্রয়কর্মী", "kasir", "penjaga kasir", "karyawan kasir"
    )

    private val SELL_VERBS = listOf(
        "sale ki", "sale kiya", "bikri ki", "bech", "becha", "bechi", "bechay", "bechta", "bechti", "bikri", "banaye", "banaya", "banai",
        "bill banaye", "rang up", "rung up", "sold", "sells", "sell", "made",
        "بیچا", "بیچی", "بیچے", "بکری", "بنائے", "بنایا", "بنائی", "سیل کی",
        "وڪرو ڪيو", "وڪري", "باع", "باعت", "بيع", "عمل", "أنشأ", "فروخت", "فروختن", "فروش کرد",
        "बेचा", "बेची", "बेचे", "बिक्री की", "बनाए", "बनाया",
        "বিক্রি করেছে", "বিক্রি করেছেন", "বানিয়েছে", "বানিয়েছেন", "menjual", "dijual", "membuat"
    )

    private val WISE = listOf("wise", "wize", "के हिसाब से", "के हिसाब", "کے حساب سے", "ke hisaab se", "ke hisaab", "ke hisab se", "per cashier", "by cashier", "each cashier", "har cashier", "ہر کیشیئر",
        "ہر کیشیر", "حسب", "لكل", "هر کاشیر", "ہر کیشیئر", "প্রতিটি", "প্রত্যেক", "प्रत्येक", "हर", "setiap", "per")

    private val WHO_WORDS = listOf(
        "kis", "kisne", "kis ne", "kaun", "kon", "kaunsa", "kaun sa", "kaun si", "kis kis", "who", "whom", "by whom", "which",
        "کس", "کس نے", "کون", "کونسا", "کون سا", "کون سی", "ڪنهن", "ڪنهن ڪيو", "ڪير", "من الذي", "من قام", "مين", "چه کسی", "کدام",
        "किस", "किसने", "किस ने", "कौन", "कौन सा", "कौन सी", "কে", "কোন", "কার", "siapa", "yang mana", "oleh siapa"
    )

    private val WHICH_WHAT = listOf(
        "kaun", "kaunsa", "kaun sa", "kaun si", "kaunse", "kon sa", "kya", "kis", "which", "what", "list", "dikhao", "batao", "bataiye", "show",
        "کون", "کونسا", "کون سا", "کون سی", "کونسے", "کیا", "کس", "دکھاؤ", "بتاؤ", "بتائیں", "ڪهڙو", "ڪهڙي", "ڇا", "ڏيکار", "ٻڌاءِ",
        "أي", "اي", "ما هي", "ما هو", "ماذا", "اعرض", "أرني", "كم", "کدام", "چه", "کدوم", "نشان بده", "بگو",
        "कौन", "कौन सा", "कौन सी", "कौनसा", "क्या", "कौन से", "दिखाओ", "बताओ", "কোন", "কোনটি", "কী", "কি", "দেখাও", "বলো",
        "mana", "apa", "yang mana", "tampilkan", "sebutkan"
    )

    private val HOW_MUCH = listOf(
        "kitna", "kitni", "kitne", "kitna hua", "kitni hui", "how much", "how many", "total", "sum", "amount", "figure",
        "کتنا", "کتنی", "کتنے", "کل", "ٹوٹل", "کتنے ہوئے", "ڪيترو", "ڪيتري", "ڪيترا", "ڪل", "ٽوٽل",
        "كم", "كم بلغ", "المجموع", "الإجمالي", "الاجمالي", "مجموع", "چقدر", "چند", "جمع", "جمع کل",
        "कितना", "कितनी", "कितने", "कुल", "টোটাল", "কত", "কতগুলো", "মোট", "berapa", "total", "jumlah"
    )

    private val HOW_MANY_ONLY = listOf("kitne", "kitni", "how many", "کتنے", "کتنی", "كم عدد", "چند", "कितने", "কতগুলো", "berapa banyak")

    private val BIG_SIZE = listOf(
        "sab se bada", "sab se bara", "sab se badi", "sab se bari", "sab se bade", "sab se barah", "sab se uncha", "sabse bada", "sabse bara",
        "sabse badi", "sabse bari", "biggest", "largest", "highest", "maximum", "max", "greatest", "top", "bigger", "big", "large", "bada", "bara", "badi",
        "bari",
        "سب سے بڑا", "سب سے بڑی", "سب سے بڑے", "سب سے اونچا", "سب سے اونچی", "بڑا", "بڑی", "بڑے",
        "سڀ کان وڏو", "سڀ کان وڏي", "سڀ کان وڏا", "وڏو", "وڏي",
        "أكبر", "اكبر", "أعلى", "اعلى", "الأكبر", "الاكبر", "الأعلى", "الاعلى", "أقصى", "اقصى", "كبير", "كبيرة",
        "بزرگ‌ترین", "بزرگترین", "بزرگ ترین", "بالاترین", "بیشترین", "بیش‌ترین", "بزرگ", "بالاترین",
        "सबसे बड़ा", "सबसे बड़ी", "सबसे बड़े", "सबसे ऊँचा", "सबसे ऊंचा", "बड़ा", "बड़ी", "बड़े",
        "সবচেয়ে বড়", "সর্বোচ্চ", "সবচেয়ে বেশি", "সবচেয়ে বড়", "বড়", "সবথেকে বড়",
        "terbesar", "tertinggi", "paling besar", "paling tinggi", "maksimum", "besar"
    )

    /** "The most": for the bills a size is not enough ("sab se zyada bill" can be most of them, by whom). */
    private val BIG_ALL = listOf(
        "sab se zyada", "sab se ziada", "sab se ziyada", "sabse zyada", "most", "highest",
        "سب سے زیادہ", "سڀ کان گهڻو", "سڀ کان وڌيڪ", "أكثر", "اكثر", "الأكثر", "بیشتر",
        "सबसे ज्यादा", "सबसे ज़्यादा", "सबसे अधिक", "সবচেয়ে বেশি", "paling banyak"
    )

    private val BIG_ANY: List<String> by lazy { BIG_SIZE + BIG_ALL }

    private val DISCOUNT = listOf(
        "discount", "discounts", "discounted", "rebate", "riayat", "riyayat", "chhoot", "chhut", "choot", "kam kiya", "kam keya", "rate kam", "off", "diskaunt",
        "ڈسکاؤنٹ", "ڈسکاونٹ", "ڈسکاؤنٹس", "رعایت", "چھوٹ", "چھوٹ", "ڇوٽ", "ڊسڪائونٽ", "رعايت",
        "خصم", "خصومات", "تخفيض", "تخفيضات", "حسم", "حسومات", "تخفیف", "تخفیفات", "تخفیف‌ها",
        "डिस्काउंट", "छूट", "रियायत", "ডিসকাউন্ট", "ছাড়", "কমিশন", "diskon", "potongan", "potongan harga", "rabat"
    )

    /** The discount's words without "off": "round off" holds it. */
    private val DISCOUNT_NO_OFF: List<String> by lazy { DISCOUNT.filter { it != "off" } }

    private val LIMIT_WORDS = listOf("limit", "hadd", "حد", "hudood", "maximum allowed", "اجازت", "permission", "allowed", "manager pin", "pin",
        "kaise", "set", "default", "ڈیفالٹ", "ترتیب", "सीमा", "সীমা", "batas")

    private val TAX = listOf(
        "tax", "taxes", "gst", "vat", "sales tax", "ppn", "fbr", "levy", "ٹیکس", "ٹیکسز", "ٽيڪس", "ضريبة", "ضرائب", "الضريبة", "ضريبه", "مالیات", "مالیاتی",
        "टैक्स", "जीएसटी", "ট্যাক্স", "কর", "জিএসটি", "pajak", "pph", "pb1"
    )

    private val ROUND_OFF = listOf(
        "round off", "roundoff", "rounded", "rounding", "round-off", "راؤنڈ آف", "راؤنڈ", "رائونڈ", "تقريب", "تقريب", "گرد کردن", "گردکردن",
        "राउंड ऑफ", "राउंड", "রাউন্ড অফ", "রাউন্ড", "pembulatan"
    )

    /** What the tax is asked as: how much was charged, collected, put on. */
    private val TAX_MONEY = listOf(
        "laga", "lagaya", "lagi", "lage", "collected", "charged", "added", "liya", "bana", "kitna", "kitni", "how much", "total", "amount",
        "wasool", "kamaya", "jama", "paid", "owed", "اضافہ", "لگا", "لگایا", "لگی", "لگے", "وصول", "کتنا", "کتنی", "جمع", "کل", "ڪيترو", "ڪيتري", "لڳو",
        "كم", "المحصلة", "محصلة", "مفروضة", "مضافة", "المجموع", "چقدر", "جمع‌آوری", "گرفته", "کل", "कितना", "कितनी", "लगा", "लगाया", "वसूल", "कुल",
        "কত", "লেগেছে", "মোট", "আদায়", "berapa", "dikenakan", "terkumpul", "total"
    )

    /** The shop's own tax number or registration, which is the profile's (Ask.Shop), and what makes a tax figure a settings question. */
    private val TAX_NUMBER = listOf(
        "number", "no", "id", "ntn", "strn", "gstin", "registration", "registered", "reg", "nombor", "نمبر", "رجسٹریشن", "رقم", "رقم ضريبي", "رقم التسجيل",
        "شناسه", "شماره", "नंबर", "पंजीकरण", "नम्बर", "নম্বর", "নিবন্ধন", "nomor", "npwp", "name", "naam", "نام"
    )

    /** Documents' own pages: "invoice mein tax" is the invoice settings' question. */
    private val DOCS_ONLY = listOf("invoice", "invoices", "quotation", "انوائس", "انوائيس", "فاکتور رسمی", "इनवॉइस", "ইনভয়েস", "faktur")

    private val VOID = listOf(
        "void", "voided", "voids", "voiding", "cancel", "cancelled", "canceled", "cancels", "cancellation", "cancelled", "mansookh", "mansukh", "radd",
        "radd kiya", "radd kiye", "radd hua", "radd hue", "cancel kiya", "cancel kiye", "cancel hua", "cancel hue", "reverse", "reversed", "ulta",
        "منسوخ", "رد", "رد کیا", "رد کیے", "رد ہوا", "کینسل", "کینسل کیا", "کینسل کیے", "کینسل ہوا", "وائڈ", "ویڈ", "واڈ",
        "منسوخ ڪيو", "رد ڪيو", "ڪينسل", "ملغى", "ملغاة", "إلغاء", "الغاء", "ألغى", "الغى", "ألغيت", "الغيت", "ملغي", "ملغية", "باطل", "ابطال", "باطل شد", "لغو", "لغو شد",
        "रद्द", "रद्द किया", "रद्द हुआ", "कैंसिल", "वॉइड", "বাতিল", "বাতিল করা", "ক্যান্সেল", "ভয়েড", "batal", "dibatalkan", "membatalkan", "pembatalan", "void"
    )

    private val MAKE_WORDS = listOf(
        "kaise", "kese", "how to", "how do", "tareeqa", "banana", "banao", "karna", "karni", "karein", "karen", "kar sakta", "kar sakte",
        "how can", "can i", "chahiye", "karo", "kar do", "kardo", "kijiye", "کرو", "کر دو", "करो", "कीजिए", "dena hai", "dene hain", "dena", "dene", "lagana", "lagani", "lagao", "lagane", "دینا ہے", "دینا", "دینے", "لگانا", "लगाना", "देना है", "देना", "দিতে হবে", "দিতে চাই", "memberi", "memberikan", "کیسے", "طریقہ", "بنانا", "بناؤ", "کرنا", "کرنی", "کریں", "ڪيئن", "كيف", "چگونه", "چطور", "कैसे", "तरीका", "করব", "কিভাবে", "bagaimana", "cara",
        "undo", "make", "create", "new", "naya", "naye", "nayi", "نیا", "نئی", "نئے", "جديد", "جدید", "नया", "नई", "নতুন", "baru"
    )

    /** "kaise ... diya": a thing already done. */
    private val PAST_GIVEN = listOf("diya", "diye", "dia", "di gayi", "gaya", "gayi", "gaye", "hua", "hui", "hue", "did", "was", "were", "given", "paid",
        "دیا", "دیے", "دی", "گیا", "گئی", "گئے", "ہوا", "ہوئی", "ہوئے", "دیا گیا", "दिया", "दिये", "गया", "हुआ", "হয়েছে", "দেওয়া হয়েছে", "diberikan", "dibayar", "dikembalikan")

    /** The goods have come back, said without a word for a return. */
    private val CAME_BACK_STRICT = listOf("wapas aaya", "wapas aayi", "wapas aaye", "wapas aa gaya", "wapas aa gayi", "came back", "come back", "wapas ho", "wapas hua", "wapas hue",
        "واپس آیا", "واپس آئی", "واپس آئے", "واپس ہوا", "वापस आया", "वापस आई", "वापस आए", "वापस हुआ", "ফেরত এসেছে", "ফেরত এল", "برگشت خورد", "dikembalikan", "kembali")

    /** Sales said in the plural or as the figure: a sentence that voids them is about the till. */
    private val SALES_WORDS = listOf("sale", "sales", "bikri", "bikree", "farokht", "فروخت", "فروخت", "فروش", "بکری", "مبيعات", "مبيعات", "विक्री", "बिक्री", "বিক্রি", "penjualan", "وڪرو")

    /** The papers that are not the till's: a cheque, an order, a plan, a quotation that is cancelled. */
    private val OTHER_PAPERS = listOf("cheque", "cheques", "check", "checks", "order", "orders", "plan", "plans", "quotation", "scheme", "schemes", "skema", "اسکیم", "invoice", "invoices", "پلان", "چیک", "چیکس", "آرڈر", "شيك", "شيكات",
        "طلب", "سفارش", "چک", "چکها", "योजना", "चेक", "ऑर्डर", "চেক", "অর্ডার", "পরিকল্পনা", "cek", "pesanan", "rencana", "faktur")

    private val CHANGE_WORDS = listOf("tabdeeli", "tabdili", "tabdeel", "badlav", "badla", "badli", "change", "changes", "changed", "adjust", "adjusted", "edit", "edited",
        "تبدیلی", "تبدیلیاں", "تبدیل", "بدلا", "تبديلي", "تبديليون", "تغيير", "تغييرات", "تعديل", "تعديلات", "تغییر", "تغییرات", "बदलाव", "बदला", "परिवर्तन", "পরিবর্তন", "বদল",
        "perubahan", "ubah", "diubah")

    /** The count's words without those that also say "closing" a day. */
    private val COUNT_STRICT: List<String> by lazy { COUNT_WORDS.filter { it !in setOf("closing", "closed", "close", "tally") } }

    private val CAME_WORDS = listOf("aaya", "aaye", "aai", "aayi", "aya", "came", "arrived", "deliver", "delivery", "آیا", "آئے", "آئی", "وصل", "جاء", "आया", "आए", "आई", "এসেছে", "datang", "tiba")

    private val ON_ACCOUNT = listOf(
        "udhaar", "udhar", "udhaar mein", "khate mein", "khaate mein", "credit", "credit par", "on account", "on credit", "to account", "baqi", "baqaya",
        "unpaid", "not paid", "pending", "due", "owed", "balance",
        "ادھار", "اُدھار", "کھاتے میں", "کھاتے", "باقی", "ادا نہیں", "بقایا", "اڌار", "کاتي ۾", "باقي",
        "آجل", "بالآجل", "دين", "على الحساب", "غير مدفوع", "غير مدفوعة", "نسيئة", "نسیه", "اعتباری", "نقدی نشده", "پرداخت نشده", "باقی‌مانده", "بدهی",
        "उधार", "खाते में", "बाकी", "बकाया", "অপরিশোধিত", "বাকি", "খাতায়", "বকেয়া", "utang", "piutang", "kredit", "belum dibayar", "belum lunas", "ke akun"
    )

    private val WAYS = listOf(
        "cash", "card", "bank", "online", "wallet", "cheque", "check", "easypaisa", "jazzcash", "jazz cash", "bkash", "upi", "gopay", "transfer",
        "payment method", "payment methods", "method", "methods", "mode", "way", "ways",
        "naqd", "naqad", "tarah", "tareeqe", "tariqe", "kis tarah", "kaise di",
        "کیش", "نقد", "کارڈ", "بینک", "آن لائن", "چیک", "ایزی پیسہ", "جاز کیش", "والیٹ", "طریقے", "طریقہ", "کس طرح",
        "ڪيش", "ڪارڊ", "بئنڪ", "آن لائين", "چيڪ", "نقد",
        "نقدا", "نقدًا", "بطاقة", "بنك", "تحويل", "شيك", "محفظة", "طريقة الدفع", "طرق الدفع",
        "نقد", "کارت", "بانک", "آنلاین", "چک", "کیف پول", "روش پرداخت", "روش‌های پرداخت",
        "नकद", "कैश", "कार्ड", "बैंक", "ऑनलाइन", "चेक", "वॉलेट", "तरीके", "भुगतान का तरीका",
        "নগদ", "ক্যাশ", "কার্ড", "ব্যাংক", "অনলাইন", "চেক", "ওয়ালেট", "পদ্ধতি", "পেমেন্টের মাধ্যম",
        "tunai", "kartu", "transfer", "dompet", "cek", "metode pembayaran", "cara bayar", "metode"
    )

    private val REFERENCE = listOf(
        "reference", "references", "ref", "ref no", "tid", "transaction id", "trx", "txn", "txn id",
        "ریفرنس", "حوالہ", "ٹی آئی ڈی", "ڈی آئی ڈی", "حوالو", "مرجع", "رقم مرجع", "الرقم المرجعي", "کد پیگیری", "شماره مرجع", "رفرنس", "संदर्भ", "रेफरेंस",
        "রেফারেন্স", "referensi", "nomor referensi", "no referensi"
    )

    private val LAST = listOf(
        "last", "latest", "recent", "newest", "most recent", "previous", "aakhri", "akhri", "aakhir", "akhir", "pichla", "pichli", "pichle",
        "آخری", "آخر", "پچھلا", "پچھلی", "پچھلے", "تازہ", "آخري", "پوئين", "پوئين", "اخير", "آخر", "الأخير", "الاخير", "الأخيرة", "الاخيرة", "السابق", "الأحدث",
        "آخرین", "اخیر", "قبلی", "جدیدترین", "आखिरी", "आख़िरी", "पिछला", "पिछली", "पिछले", "ताज़ा", "শেষ", "সর্বশেষ", "আগের", "সাম্প্রতিক", "terakhir", "baru saja", "sebelumnya", "paling baru"
    )

    private val LAST_OR_WHEN: List<String> by lazy { LAST + WHEN }

    /** "When" without the words that are only "day": "close the day" is not a question of when. */
    private val WHEN_STRICT: List<String> by lazy { WHEN.filter { it !in setOf("day", "din", "date") } }

    private val WHEN = listOf(
        "when", "kab", "kab hui", "kab hua", "kab ki", "kab kiya", "date", "tareekh", "tarikh", "day", "din",
        "کب", "تاریخ", "کب ہوئی", "کب ہوا", "ڪڏهن", "تاريخ", "متى", "تاريخ", "چه وقت", "कब", "तारीख", "কবে", "তারিখ", "kapan", "tanggal"
    )

    private val DETAIL_WORDS = listOf(
        "detail", "details", "note", "notes", "discount", "tax", "cashier", "who", "kis ne", "kisne", "kitne ka", "kitne ki", "total", "paid", "balance",
        "customer", "kis ka", "kis ki", "wapas", "kaisa", "kaise", "tha", "thi", "kya", "what", "how much",
        "تفصیل", "نوٹ", "رعایت", "ٹیکس", "کیشیئر", "کس نے", "کتنے کا", "کتنے کی", "ادا", "کس کا", "کس کی", "تھا", "تھی", "کیا", "تفصيل", "ملاحظة", "خصم", "ضريبة",
        "من", "كم", "كان", "جمع", "تفاصيل", "جزئیات", "یادداشت", "تخفیف", "مالیات", "چقدر", "बारे", "विवरण", "नोट", "छूट", "टैक्स", "किसने", "कितने का", "था", "थी",
        "বিস্তারিত", "নোট", "ছাড়", "ট্যাক্স", "কে", "কত", "ছিল", "rincian", "catatan", "diskon", "pajak", "siapa", "berapa"
    )

    private val LOYALTY = listOf(
        "loyalty", "loyalty points", "loyalty point", "points", "point", "reward", "rewards", "reward points", "poin", "poin loyalitas",
        "لائلٹی", "لائلٹی پوائنٹس", "پوائنٹس", "پوائنٹ", "لائلٽي", "پوائنٽس", "پوائنٽ", "نقاط", "نقاط الولاء", "نقاط الولاء", "نقطة", "امتیاز", "امتیازات", "امتیاز وفاداری",
        "لایالتی", "पॉइंट", "पॉइंट्स", "लॉयल्टी", "लॉयल्टी पॉइंट्स", "পয়েন্ট", "লয়্যালটি", "লয়্যালটি পয়েন্ট", "লয়ালটি", "poin loyalitas"
    )

    private val GOODS_WORDS = listOf(
        "item", "items", "maal", "saman", "samaan", "cheez", "cheezein", "cheezen", "product", "products", "goods", "stock", "mal",
        "آئٹم", "آئٹمز", "مال", "سامان", "چیز", "چیزیں", "پروڈکٹ", "پروڈکٹس", "اسٹاک", "اشیاء", "اشیا", "مواد", "ڪم", "اسٽاڪ", "شيون", "شيءِ", "چيز", "مال",
        "بضاعة", "بضائع", "أصناف", "صنف", "منتج", "منتجات", "سلع", "مخزون", "کالا", "کالاها", "اجناس", "محصول", "موجودی",
        "आइटम", "माल", "सामान", "चीज़", "चीज", "चीजें", "प्रोडक्ट", "स्टॉक", "আইটেম", "মাল", "পণ্য", "জিনিস", "মালামাল", "স্টক",
        "barang", "produk", "stok", "item"
    )

    private val STOCK_WORDS = listOf("stock", "inventory", "godown", "shelf", "اسٹاک", "اسٽاڪ", "مخزون", "جرد", "موجودی", "انبار", "स्टॉक", "গুদাম", "স্টক", "stok", "gudang", "rak")

    private val RETURN_WORDS = listOf(
        "return", "returns", "returned", "wapsi", "wapasi", "wapas", "wapis", "waapsi", "refund", "refunds", "refunded", "credit note",
        "واپسی", "واپس", "واپسیاں", "ریفنڈ", "واپسي", "واپس ڪيو", "مرتجع", "مرتجعات", "إرجاع", "ارجاع", "استرجاع", "المرتجعات", "ردّ", "مرجوعی", "مرجوعی‌ها", "برگشت", "برگشتی",
        "برگشت داده", "वापसी", "वापस", "रिटर्न", "रिफंड", "ফেরত", "রিটার্ন", "রিফান্ড", "retur", "pengembalian", "dikembalikan", "kembali"
    )

    /** A return's own words: "wapas"/"واپس"/"वापस" alone are "back" (money coming back, a cheque coming back), not a return. */
    private val RETURN_STRONG: List<String> by lazy { RETURN_WORDS.filter { it !in setOf("wapas", "wapis", "واپس", "वापस", "kembali") } }

    private val YESTERDAY_WORDS = listOf("kal", "yesterday", "yesterday's", "kal ki", "kal ka", "گزشتہ", "کل کی", "کل کا", "کل کے", "ڪالهه", "أمس", "امس", "ديروز", "دیروز", "कल", "গতকাল", "kemarin")

    /** "Last date" is a deadline, not the last bill. */
    private val DEADLINE = listOf("aakhri tareekh", "akhri tareekh", "last date", "due date", "deadline", "آخری تاریخ", "आखिरी तारीख", "अंतिम तारीख", "শেষ তারিখ", "tenggat", "batas waktu")

    private val REASON = listOf(
        "reason", "reasons", "why", "wajah", "wajhe", "wajah se", "kis wajah", "kyun", "kyu", "kiun", "kion", "kis liye", "sabab", "cause", "because",
        "وجہ", "وجوہات", "کیوں", "کس وجہ", "کس لیے", "سبب", "ڇو", "سبب", "ڪهڙي سبب", "لماذا", "لماذا", "السبب", "أسباب", "سبب", "چرا", "دلیل", "علت", "دلایل", "क्यों", "कारण", "वजह",
        "কেন", "কারণ", "কী কারণে", "mengapa", "kenapa", "alasan", "sebab"
    )

    private val REFUND_WAY = listOf(
        "refund", "refunded", "paisa wapas", "paise wapas", "cash wapas", "wapas diye", "wapas kiye", "wapas di", "wapas kia", "paisa diya", "paise diye",
        "paisa kaise", "paise kaise", "refund kaise", "how refunded", "paid back", "money back", "khate mein wapas", "khate mein", "ریفنڈ", "پیسے واپس", "پیسہ واپس", "رقم واپس", "کھاتے میں",
        "استرداد", "استرجاع المبلغ", "رد المبلغ", "ردّ المبلغ", "برگشت وجه", "بازپرداخت", "پس دادن", "वापस पैसे", "पैसे वापस", "रिफंड", "খাতায়", "টাকা ফেরত", "রিফান্ড",
        "uang kembali", "pengembalian dana", "refund"
    )

    private val CAME_BACK = listOf(
        "wapas aaya", "wapas aayi", "wapas aaye", "wapas aa gaya", "wapas aa gayi", "wapas ho", "wapas hua", "wapas hue", "wapas hui", "wapas kiya", "wapas kiye",
        "wapas aane", "wapas ayi", "came back", "come back", "returned", "waapis", "واپس آیا", "واپس آئی", "واپس آئے", "واپس ہوا", "واپس ہوئی", "واپس ہوئے", "واپس کیا",
        "واپس آیل", "واپس ٿيو", "رجع", "رجعت", "عاد", "ارتجع", "برگشت", "برگشت خورد", "برگشت داده شد", "वापस आया", "वापस आई", "वापस आए", "वापस हुआ", "वापस हुए", "वापस किया",
        "ফেরত এসেছে", "ফেরত দেওয়া", "ফেরত হয়েছে", "dikembalikan", "dikembalikan oleh", "kembali"
    )

    private val SHIFT = listOf(
        "shift", "shifts", "duty", "duties", "شفٹ", "شفٹیں", "شفٽ", "شفٽون", "وردية", "ورديات", "الوردية", "شیفت", "شیفت‌ها", "شیفتها", "शिफ्ट", "शिफ़्ट", "शिफ्टें",
        "শিফট", "শিফটগুলো", "shift kerja"
    )

    private val DIFFERENCE = listOf(
        "difference", "differences", "diff", "variance", "mismatch", "farq", "fark", "frq", "gap", "discrepancy",
        "فرق", "فرقز", "اختلاف", "فارق", "الفرق", "عجز", "مغایرت", "तफावत", "अंतर", "फर्क", "फ़र्क", "পার্থক্য", "ফারাক", "selisih", "perbedaan"
    )

    private val SHORT_WORDS = listOf(
        "short", "shortage", "shortfall", "missing", "less", "over", "excess", "extra", "kam", "kami", "kam nikla", "zyada nikla", "ghata", "ghat", "ghaata",
        "کم", "کمی", "کم نکلا", "زیادہ نکلا", "گھاٹا", "گھٹا", "گھٹ", "ڪمي", "گهٽ", "نقص", "عجز", "زيادة", "كسر", "کمبود", "اضافه", "कम", "कमी", "घटा", "ज्यादा", "ज़्यादा", "अधिक",
        "কম", "কমতি", "বেশি", "ঘাটতি", "kurang", "lebih", "kekurangan", "selisih"
    )

    private val DRAWER_WORDS = listOf(
        "galla", "gala", "galle", "gallay", "galley", "drawer", "cash drawer", "till", "cash count", "cash counted", "closing cash", "naqdi", "naqd",
        "گلہ", "گلا", "گلے", "گلي", "نقدی", "نقد", "الدرج", "درج النقد", "صندوق النقد", "صندوق", "गल्ला", "गल्ले", "नकद", "नकदी", "ক্যাশ বাক্স", "ক্যাশ", "ড্রয়ার", "নগদ",
        "laci", "laci kas", "kas", "uang tunai", "اغلاق", "إغلاق", "اغلاق اليوم", "إغلاق اليوم"
    )

    private val COUNT_WORDS = listOf(
        "count", "counted", "counting", "ginti", "gintee", "gina", "gine", "ginna", "gin", "tally", "closing", "closed", "close",
        "گنتی", "گنا", "گنے", "گنی", "گنے", "ڳڻپ", "ڳڻيو", "ڳڻيا", "عدّ", "عد", "عدد", "عدّ", "جرد", "عددت", "شمارش", "شمرده", "شمردم", "शमार", "गिनती", "गिना", "गिने",
        "গণনা", "গুনেছি", "গুনেছে", "hitung", "dihitung", "hitungan", "penghitungan", "hitungan kas"
    )

    private val CLOSE_WORDS = listOf(
        "close", "closed", "closing", "band", "band kiya", "band kiye", "band kia", "din band", "end of day", "eod",
        "بند", "بند کیا", "بند کی", "بند ڪيو", "إغلاق", "اغلاق", "أغلق", "اغلق", "بستن", "بستم", "बंद", "बंद किया", "বন্ধ", "শেষ", "tutup", "ditutup", "penutupan"
    )

    private val CLOSE_THE_DAY = listOf(
        "din band", "din bandh", "day close", "close the day", "close day", "closing the day", "end of day", "eod", "z report", "z-report",
        "دن بند", "ڏينهن بند", "اغلاق اليوم", "إغلاق اليوم", "بستن روز", "दिन बंद", "দিন বন্ধ", "tutup hari", "tutup buku"
    )

    private val STOCK_COUNT = listOf(
        "stock count", "stock counts", "stock ginti", "stock gintee", "stock gina", "stock ki ginti", "inventory count", "stock check", "stock taking",
        "stocktake", "stock take", "inventory", "stock opname", "physical count", "count stock",
        "اسٹاک گنتی", "اسٹاک کی گنتی", "اسٹاک گنا", "اسٹاک چیک", "اسٽاڪ ڳڻپ", "اسٽاڪ جي ڳڻپ", "جرد المخزون", "جرد", "جرد البضاعة", "شمارش موجودی", "موجودی‌شماری", "شمارش انبار",
        "स्टॉक गिनती", "स्टॉक की गिनती", "स्टॉक गिना", "স্টক গণনা", "স্টক গোনা", "hitung stok", "stok opname", "penghitungan stok", "cek stok"
    )

    private val STOCK_ADJUST = listOf(
        "stock adjust", "stock adjustment", "stock adjustments", "adjustment", "adjustments", "adjusted", "stock correction", "stock corrections",
        "stock change", "stock changes", "stock badla", "stock badli", "stock tabdeel", "stock mein tabdeeli", "stock mein kami", "stock mein kami beshi",
        "stock edit", "stock write", "اسٹاک ایڈجسٹ", "اسٹاک ایڈجسٹمنٹ", "اسٹاک میں تبدیلی", "اسٹاک بدلا", "اسٹاک درست", "اسٽاڪ ايڊجسٽ", "اسٽاڪ ۾ تبديلي", "تسوية المخزون", "تعديل المخزون",
        "تسويات المخزون", "تعدیل موجودی", "اصلاح موجودی", "تغییر موجودی", "स्टॉक एडजस्ट", "स्टॉक में बदलाव", "स्टॉक बदला", "স্টক সমন্বয়", "স্টকে পরিবর্তন", "penyesuaian stok", "koreksi stok",
        "ubah stok"
    )

    private val WRITE_OFF = listOf(
        "damage", "damaged", "damages", "kharab", "kharabi", "kharabay", "kharaab", "wastage", "waste", "wasted", "spoil", "spoilt", "spoiled",
        "write off", "written off", "writeoff", "write-off", "wrote off", "phenka", "phenk", "phenkna", "thrown", "thrown out", "chori", "theft", "stolen", "gum",
        "gumshuda", "lost", "missing", "breakage", "broken", "toota", "tootay", "toot", "expired and", "nikala", "nikale", "likhe gaye",
        "خراب", "خرابی", "ضائع", "ضائع شدہ", "پھینکا", "پھینکے", "چوری", "گم", "گمشدہ", "ٹوٹا", "ٹوٹے", "نکالا", 
        "خراب", "ضايع", "اڇليو", "چوري", "گم", "ڀڄي", "ڪڍيو",
        "تالف", "تالفة", "هالك", "هدر", "سرقة", "مسروق", "مفقود", "مفقودة", "كسر", "مكسور", "شطب", "شُطب", "أتلف", "اتلف",
        "ضایعات", "خراب", "آسیب‌دیده", "آسیب دیده", "دزدی", "دزدیده", "گم‌شده", "گم شده", "شکسته", "دور ریخته",
        "खराब", "बर्बाद", "फेंका", "फेंके", "चोरी", "गुम", "टूटा", "टूटे", "निकाला", "नष्ट", "নষ্ট", "ক্ষতিগ্রস্ত", "চুরি", "হারানো", "ফেলে", "ভাঙা",
        "rusak", "dibuang", "hilang", "dicuri", "susut", "pecah", "dibuang karena"
    )

    private val WRITE_VERBS = listOf(
        "likha", "likhe", "likhi", "likha gaya", "likhe gaye", "write off", "written off", "wrote off", "adjust", "adjusted", "nikala", "nikale", "phenka", "phenke",
        "لکھا", "لکھے", "لکھی", "لکھا گیا", "لکھے گئے", "نکالا", "پھینکا", "ڪڍيو", "اڇليو", "شطب", "شُطب", "أُتلف", "حذف", "लिखा", "लिखे", "लिखा गया", "निकाला", "फेंका",
        "লেখা", "লেখা হয়েছে", "ফেলা", "ফেলে", "dicatat", "dibuang", "dihapus", "ditulis"
    )

    private val TICKET_ONLY = listOf("bill", "bills", "ticket", "receipt", "بل", "ٹکٹ", "فاتورة", "बिल", "বিল", "struk")

    // ------------------------------------------------------------------------------------- groupings (plain data, tested here)

    /** One reason's tally: how many returns gave it, and what the goods were worth. */
    data class ReasonTally(val reason: String, val count: Int, val worth: Double)

    /**
     * Free-text reasons tallied alike however they were typed ("Damaged", "damaged ", "DAMAGED"): trimmed, case folded and the
     * inner spaces closed; the commonest first, then the biggest worth, then by the first spelling met; the spelling shown is
     * the first one met (newest first, as the rows come). A blank reason is "" — the card says it is not written.
     */
    fun tallyReasons(rows: List<Pair<String, Double>>): List<ReasonTally> {
        val order = LinkedHashMap<String, MutableList<Pair<String, Double>>>()
        for (r in rows) {
            val key = r.first.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
            order.getOrPut(key) { ArrayList() }.add(r)
        }
        return order.values.map { g ->
            val shown = g.first().first.trim().replace(Regex("\\s+"), " ")
            ReasonTally(shown, g.size, g.sumOf { it.second })
        }.sortedWith(compareByDescending<ReasonTally> { it.count }.thenByDescending { it.worth })
    }

    /** One kind of goods that came back (or went out of the shelf): the quantity in its own unit, and what it was worth. */
    data class GoodsTally(val name: String, val unit: String?, val qty: Double, val worth: Double, val lines: Int)

    /**
     * Lines (name, unit, quantity, worth) gathered by name and unit, never across units — five bags and five kilos are not
     * ten of anything. Names are met whatever their case or spacing; the largest worth first, then the larger quantity.
     * [limit] keeps the first few. Quantities are summed as they are (never rounded here).
     */
    fun tallyGoods(lines: List<GoodsLine>, limit: Int = Int.MAX_VALUE): List<GoodsTally> {
        val groups = LinkedHashMap<Pair<String, String>, MutableList<GoodsLine>>()
        for (l in lines) {
            val k = l.name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT) to (l.unit?.trim()?.lowercase(java.util.Locale.ROOT) ?: "")
            groups.getOrPut(k) { ArrayList() }.add(l)
        }
        return groups.values.map { g ->
            GoodsTally(g.first().name.trim().replace(Regex("\\s+"), " "), g.first().unit?.trim()?.takeIf { it.isNotEmpty() }, g.sumOf { it.qty }, g.sumOf { it.worth }, g.size)
        }.sortedWith(compareByDescending<GoodsTally> { it.worth }.thenByDescending { it.qty }.thenBy { it.name }).take(limit)
    }

    /** A line of goods for [tallyGoods]. */
    data class GoodsLine(val name: String, val unit: String?, val qty: Double, val worth: Double)

    /** Points of one customer over a span: earned (positive rows), spent (negative rows), and what is left of the span's. */
    data class PointsTally(val partyId: Long, val earned: Double, val spent: Double, val rows: Int) {
        val net: Double get() = earned - spent
    }

    /**
     * Loyalty rows (customer, signed points) grouped by customer: earned is the sum of the positive rows and spent the sum of
     * the negative ones, as a positive figure. Most earned first (then the most rows, then the lowest id, so the order never
     * shifts between two reads). A customer whose rows add to nothing of either is left out.
     */
    fun tallyPoints(rows: List<Pair<Long, Double>>): List<PointsTally> =
        rows.groupBy { it.first }.map { (id, g) ->
            PointsTally(id, g.filter { it.second > 0 }.sumOf { it.second }, -g.filter { it.second < 0 }.sumOf { it.second }, g.size)
        }.filter { it.earned > 0.0 || it.spent > 0.0 }
            .sortedWith(compareByDescending<PointsTally> { it.earned }.thenByDescending { it.rows }.thenBy { it.partyId })

    /** An adjustment, as the tallies read it: product id, signed quantity, unit, reason. */
    data class AdjustLine(val productId: Long, val qty: Double, val unit: String?, val reason: String)

    /** The reasons that are goods lost: damage, expiry, theft. ("Other" and a transfer are not written off; a count is a finding.) */
    val LOST_REASONS = setOf("damage", "expired", "theft") // AdjustReason.DAMAGE, EXPIRED, THEFT (Inventory.kt: held equal by a test)

    /**
     * What was written off over a span, by product, unit and reason: the quantities that went (a loss is written as a negative
     * quantity, shown here as a positive amount lost; a positive row under such a reason is a correction upward and is netted
     * in). Only [LOST_REASONS]. A product-and-unit whose rows net to nothing lost is left out. The biggest loss first.
     */
    data class LostTally(val productId: Long, val unit: String?, val reason: String, val lost: Double, val rows: Int)

    fun tallyLost(lines: List<AdjustLine>): List<LostTally> {
        val g = lines.filter { it.reason in LOST_REASONS }.groupBy { Triple(it.productId, it.unit?.trim()?.lowercase(java.util.Locale.ROOT) ?: "", it.reason) }
        return g.map { (k, rows) ->
            LostTally(k.first, rows.first().unit?.trim()?.takeIf { it.isNotEmpty() }, k.third, -rows.sumOf { it.qty }, rows.size)
        }.filter { it.lost > 0.0 }.sortedWith(compareByDescending<LostTally> { it.lost }.thenBy { it.productId })
    }
}

// ----------------------------------------------------------------------------------------- rows the DAO reads (read only)

/** What one cashier rang up over a span; a null [cashier] is the owner. */
data class CashierTotal(val cashier: String?, val tickets: Int, val total: Double)

/** A ticket or a return that was voided: who rang it up, who voided it (from the audit row; null: the owner) and why. */
data class VoidedDoc(
    val id: Long, val docNumber: String, val customerName: String?, val amount: Double, val whenAt: Long,
    val reason: String?, val rungBy: String?, val voidedBy: String?, val isReturn: Boolean
)

/** A finished return with what the owner asks of it: its reason, the way it was refunded and who took it. */
data class ReturnWhy(
    val id: Long, val docNumber: String, val customerName: String?, val whenAt: Long, val amount: Double,
    val refundMethod: String, val refundAmount: Double, val reason: String, val note: String?, val rungBy: String?
)

/** One line of goods that came back. */
data class ReturnedGoods(val itemName: String, val quantity: Double, val unit: String?, val rate: Double)

/** One loyalty row: earned (positive) or spent (negative). */
data class PointRow(val partyId: Long, val points: Double)
