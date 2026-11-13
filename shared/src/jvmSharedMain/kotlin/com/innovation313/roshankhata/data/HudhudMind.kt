package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch

/**
 * Hudhud's second reading of a sentence (13 Nov 2026, the owner: "kya aa gaya to nahi pata laga, se aa gaya to nahi
 * pata laga, main aa gaya, ko aa gaya… ye khud samjhe ke kehne wale ka matlab kya hai"): what each word MEANS, not
 * where it stands. Until now most of Hudhud's lists were phrases matched as one unbroken run of words, so "aap ka naam"
 * was understood and "aap ka kya naam hai" was not: one "kya" in the middle broke it, and so did any other order.
 *
 * Here every word is looked up on its own and carries one or more concepts ([C]): "naam", "نام", "नाम", "নাম", "اسم",
 * "nama" are all [C.NAME]; "aap", "tumhara", "your", "آپ", "आप", "আপনার", "kamu" are all [C.YOU]. A question is then
 * the set of concepts it holds, in any order, with any of the little words between them ("ka", "ki", "se", "ko",
 * "main", "kya", "to", "na", "ji"): they are never needed and never in the way. [Hudhud] decides what the set asks.
 *
 * How a word is matched, most certain first:
 *  - as it is, or with the small pieces its language joins to a word (Arabic's article in front, Bengali's endings
 *    behind, an English plural, Indonesian's -nya / -mu / -ku: "nomornya" is "his number");
 *  - a phrase where one word alone would mean something else ("ka pata" is an address, "pata" alone is "know");
 *  - by sound, for a Roman-Urdu word of five letters or more that is not one of the book's own names: the spelling
 *    NameSearch folds names with ("baqaya", "bakaya", "baqaaya" are one word), within one letter. A word the book
 *    knows as a customer or a product is never read as a concept by sound — "Naeem" is a man, not "naam".
 *
 * Deliberately not a language model: the book stays on the phone, every rule can be tested (HudhudMindTest holds
 * hundreds of ways of asking), and a sentence that fits nothing is said to fit nothing.
 */
internal object HudhudMind {

    /** What a word can mean to Hudhud. */
    enum class C {
        // Who the sentence points at.
        YOU, OWN, ME, THIS, HIS, AND,
        // Question words.
        WHAT, WHO, HOW_MANY, WHEN, WHERE, WHICH, SINCE_WHEN,
        // A customer's or a supplier's facts.
        NAME, PHONE, ADDRESS, VILLAGE, FATHER, LIMIT, PROMISE, LAST, FIRST, PAYMENT, ENTRY, CREDIT,
        // The shop.
        SHOP, OWNER, EMAIL, TAX, BANK, CURRENCY, COUNTRY, TRADE,
        // Goods.
        GOODS, PRICE, STOCK, LEFT, COST, CHEAP, DEAR, SUPPLIER, CUSTOMER, CAME, BOUGHT, FROM_WHOM,
        // The book's other pages.
        BILL, INVOICE, CHEQUE, PLAN, STAFF, TICKET, CALCULATOR, APP, OPEN, TIMES, MADE, PENDING, LOW, CASHBOOK, RETURN,
        HAPPENED, EDITED, DELETED, REPORT, INFO, DATA, SAFE, USED, SALARY, MONEY, ACCOUNT,
        // About Hudhud and the day.
        MAKER, LANGUAGE, VERSION, DATE, DAY, TIME, ALL, HEALTH, HOW_IS, ABLE, TELL, DO, KNOW
    }

    /**
     * The words of each concept in the app's nine languages and the scripts a recogniser writes them in. Words in
     * other languages are the app's own translations where the app has the word (its strings.xml: "فون", "ڳوٺ",
     * "سقف نسیه", "পিতা", "Batas utang"…), so Hudhud understands what the app itself says.
     */
    private val LEXICON: Map<C, List<String>> = mapOf(
        C.YOU to listOf(
            "aap", "ap", "aapka", "apka", "aapki", "apki", "aapke", "apke", "aapko", "apko", "tum", "tumhara", "tumhari",
            "tumhare", "tumhe", "tumhein", "tumko", "tu", "tera", "teri", "tere", "tujhe", "tujhko", "you", "your",
            "yours", "ur", "u", "yourself",
            "آپ", "آپکا", "آپکی", "آپکے", "آپکو", "تم", "تمہارا", "تمہاری", "تمہارے", "تمہیں", "تیرا", "تیری", "تیرے", "تجھے",
            "आप", "आपका", "आपकी", "आपके", "आपको", "तुम", "तुम्हारा", "तुम्हारी", "तुम्हारे", "तुम्हें", "तू", "तेरा", "तेरी",
            "तेरे", "तुझे", "তুমি", "তোমার", "তোমাকে", "আপনি", "আপনার", "আপনাকে", "তুই", "তোর", "أنت", "انت", "شما",
            "توهان", "تون", "تنهنجو", "توهانجو", "kamu", "anda", "kau", "engkau", "dirimu",
            // His own name: "Hudhud kya hai" asks who he is.
            "hudhud", "hoopoe", "ہدہد", "هدهد", "हुदहुद", "হুদহুদ", "الهدهد"
        ),
        // "Apna naam batao": one's own, said to Hudhud — counted as "you" only where nothing else is asked.
        C.OWN to listOf("apna", "apni", "apne", "اپنا", "اپنی", "اپنے", "अपना", "अपनी", "अपने", "নিজের", "خودت", "پنهنجو"),
        C.ME to listOf(
"اسم من", "نام من", "دفتر من", "حساب من", "شماره من", "مغازه من", "اطلاعات من", "داده من",             "main", "mai", "mera", "meri", "mere", "mujhe", "muje", "mjhe", "mujhko", "hum", "hamara", "hamari", "hamare",
            "humara", "humari", "humare", "hame", "hamein", "my", "me", "mine", "i", "im",
            "میرا", "میری", "میرے", "مجھے", "ہمارا", "ہماری", "ہمارے", "ہمیں", "मैं", "मेरा", "मेरी", "मेरे", "मुझे", "हम",
            "हमारा", "हमारी", "हमारे", "हमें", "আমি", "আমার", "আমাকে", "আমাদের", "أنا", "انا", "اسمي", "مرا",
            "منهنجو", "مان", "آئون", "مون", "saya", "aku"
        ),
        C.THIS to listOf("ye", "yeh", "yah", "this", "یہ", "यह", "ये", "এই", "এটা", "هذا", "هذه", "این", "ini"),
        // "Uska number?": the one the last answer was about.
        C.HIS to listOf(
"wo", "woh", "وہ", "वह", "वो",             "uska", "uski", "uske", "usko", "iska", "iski", "iske", "isko", "unka", "unki", "unke", "unko", "inka", "inki",
            "inke", "his", "her", "its", "him", "their", "them", "us ka", "us ki", "us ke", "is ka", "is ki", "is ke",
            "un ka", "un ki", "un ke", "اسکا", "اسکی", "اسکے", "اسکو", "انکا", "انکی", "انکے", "اس کا", "اس کی", "اس کے",
            "ان کا", "ان کی", "ان کے", "उसका", "उसकी", "उसके", "उसको", "उसे", "इसका", "इसकी", "इसके", "उनका", "उनकी",
            "उनके", "उस का", "उस की", "इस का", "তার", "ওর", "ওনার", "তাঁর", "له", "لها", "اونجو", "هن جو", "dia"
        ),
        C.AND to listOf("aur", "or", "and", "also", "bhi", "phir", "اور", "بھی", "और", "भी", "আর", "আরও", "۽", "هم", "dan", "juga", "lalu"),
        C.WHAT to listOf("kya", "kia", "kyaa", "what", "whats", "کیا", "क्या", "কী", "কি", "ما", "ماذا", "چه", "چی", "چیست", "ڇا", "apa"),
        C.WHO to listOf(
            "kaun", "kon", "kis", "kisne", "kis ne", "who", "whom", "whose", "کون", "کس", "کسنے", "کس نے", "कौन", "किस",
            "किसने", "किस ने", "কে", "কারা", "কার", "কে কে", "من", "ڪير", "ڪنهن", "siapa", "چه کسی"
        ),
        C.HOW_MANY to listOf(
"عدد", "تعداد", "কতজন", "কয়টি", "কতবার", "jumlah",             "kitna", "kitni", "kitne", "kitnay", "ktna", "ktni", "ktne", "how much", "how many", "count",
            "کتنا", "کتنی", "کتنے", "कितना", "कितनी", "कितने", "কত", "কতটা", "কতগুলো", "كم", "چقدر", "چند", "چندتا",
            "ڪيترو", "ڪيترا", "ڪيتري", "berapa"
        ),
        C.WHEN to listOf("kab", "kb", "when", "کب", "कब", "কবে", "কখন", "متى", "چه وقت", "چه زمانی", "ڪڏهن", "kapan"),
        C.SINCE_WHEN to listOf("kab se", "kb se", "since when", "since", "کب سے", "कब से", "কবে থেকে", "منذ متى", "از کی", "ڪڏهن کان", "sejak kapan", "sejak"),
        C.WHERE to listOf(
            "kahan", "kahaan", "kidhar", "kaha", "where", "کہاں", "کدھر", "कहाँ", "कहां", "किधर", "কোথায়", "أين", "اين",
            "کجا", "ڪٿي", "dimana", "di mana"
        ),
        C.WHICH to listOf(
            "kaunsa", "konsa", "kaunsi", "konsi", "kaunse", "konse", "kaun sa", "kon sa", "kaun si", "kon si", "which",
            "کونسا", "کونسی", "کونسے", "کون سا", "کون سی", "कौनसा", "कौनसी", "कौनसे", "कौन सा", "कौन सी", "কোন", "কোনটা",
            "أي", "اي", "کدام", "ڪهڙو", "ڪهڙي", "yang mana", "mana"
        ),
        C.NAME to listOf(
            "naam", "nam", "name", "names", "نام", "नाम", "নাম", "اسم", "اسمك", "اسمي", "اسمت", "اسمم", "نالو",
            "nama", "namamu", "namaku", "namanya"
        ),
        C.PHONE to listOf(
            "phone", "fone", "number", "nambar", "numbar", "nmbr", "mobile", "mobail", "contact", "rabta", "raabta",
            "فون", "نمبر", "موبائل", "رابطہ", "फोन", "नंबर", "नम्बर", "मोबाइल", "ফোন", "নম্বর", "নাম্বার", "মোবাইল",
            "هاتف", "الهاتف", "جوال", "تلفن", "شماره", "موبایل", "telepon", "nomor", "nomornya", "ponsel"
        ),
        C.ADDRESS to listOf(
            "address", "adress", "ka pata", "ki pata", "ke pata", "کا پتہ", "کا پتا", "ایڈریس", "का पता", "एड्रेस", "ঠিকানা",
            "عنوان", "العنوان", "آدرس", "پتو", "alamat", "rehta", "rehte", "rehti", "رہتا", "रहता"
        ),
        C.VILLAGE to listOf(
            "gaon", "gaun", "gaoon", "village", "goth", "گاؤں", "گاؤ", "गाँव", "गांव", "গ্রাম", "قرية", "القرية", "روستا",
            "ڳوٺ", "desa", "kampung"
        ),
        C.FATHER to listOf(
            "walid", "waalid", "wald", "father", "baap", "abba", "abbu", "son of", "s o", "والد", "ولد", "باپ", "पिता",
            "वालिद", "बाप", "বাবা", "পিতা", "أب", "الأب", "الاب", "پدر", "پيءُ", "ayah", "bapak"
        ),
        C.LIMIT to listOf(
            "limit", "hadd", "حد", "لمٹ", "सीमा", "लिमिट", "সীমা", "লিমিট", "سقف", "batas"
        ),
        // Credit ("udhaar ki hadd", "ادھار قیمت"): beside a limit or a price; alone it stays the figure it always was.
        C.CREDIT to listOf(
            "credit", "udhaar", "udhar", "udhaari", "ادھار", "ادھاری", "اڌار", "اڌاري", "उधार", "ائتمان", "آجل", "نسیه",
            "utang", "kredit"
        ),
        C.PROMISE to listOf(
            "wada", "waada", "wadah", "waadah", "wade", "waade", "promise", "promised", "due date", "payment day",
            "وعدہ", "وعدے", "वादा", "वादे", "প্রতিশ্রুতি", "প্রতিশ্রুত", "وعد", "موعد", "وعده", "واعدو", "واعدي", "janji"
        ),
        C.LAST to listOf(
            "aakhri", "akhri", "aakhiri", "akhiri", "akhir", "aakhir", "last", "latest", "recent", "recently", "pichli",
            "آخری", "آخر", "پچھلی", "आखिरी", "आख़िरी", "अंतिम", "पिछली", "শেষ", "সর্বশেষ", "শেষবার", "اخر", "الأخير",
            "الاخير", "آخرین", "آخري", "terakhir"
        ),
        C.FIRST to listOf(
            "pehli", "pehla", "pahli", "pahla", "first", "shuru", "start", "پہلی", "پہلا", "شروع", "पहली", "पहला", "शुरू",
            "প্রথম", "أول", "اول", "اولین", "نخستین", "پهريون", "پهرين", "pertama"
        ),
        C.PAYMENT to listOf(
"pay",             "payment", "paymant", "payments", "adaigi", "adayegi", "adaygi", "wasooli", "wasuli", "wusooli", "vasooli",
            "recovery", "paid", "diye", "diya", "dia", "jama", "ادائیگی", "وصولی", "ادا", "دیے", "دیئے", "دیا", "जमा",
            "भुगतान", "अदायगी", "वसूली", "दिए", "दिया", "পেমেন্ট", "পরিশোধ", "দিয়েছে", "دفعة", "سداد", "دفع", "پرداخت",
            "ادائيگي", "وصولي", "ڏنا", "ڏنو", "pembayaran", "bayaran", "membayar", "تحصيل", "تحصل"
        ),
        C.ENTRY to listOf(
            "entry", "entries", "indiraj", "indraj", "len den", "lain dain", "lein dein", "lena dena", "transaction",
            "transactions", "sauda", "saude", "اندراج", "انٹری", "لین دین", "سودا", "एंट्री", "लेन देन", "लेनदेन",
            "লেনদেন", "এন্ট্রি", "معاملة", "معاملات", "قيد", "قيود", "تراکنش", "معامله", "ثبت", "entri", "transaksi",
            "اندراجات", "داخلا", "داخلائون", "catatan"
        ),
        C.SHOP to listOf(
            "dukaan", "dukan", "dokan", "shop", "store", "business", "karobar", "kaarobaar", "firm",
            "دکان", "دوکان", "کاروبار", "दुकान", "कारोबार", "बिजनेस", "দোকান", "ব্যবসা", "متجر", "المتجر", "محل", "المحل",
            "مغازه", "فروشگاه", "دڪان", "toko", "usaha", "bisnis"
        ),
        C.OWNER to listOf("malik", "maalik", "owner", "seth", "مالک", "मालिक", "মালিক", "مالك", "مالڪ", "pemilik"),
        C.EMAIL to listOf("email", "e mail", "ای میل", "ईमेल", "ইমেইল", "ইমেল", "بريد", "ایمیل", "اي ميل", "surel"),
        C.TAX to listOf("tax", "ntn", "strn", "gst", "vat", "ٹیکس", "टैक्स", "ট্যাক্স", "ضريبة", "الضريبة", "مالیات", "ٽيڪس", "pajak", "npwp"),
        C.BANK to listOf("bank", "iban", "بینک", "بنک", "बैंक", "ব্যাংক", "بنك", "بانک", "بئنڪ", "rekening"),
        C.CURRENCY to listOf("currency", "karansi", "کرنسی", "मुद्रा", "करेंसी", "মুদ্রা", "عملة", "العملة", "ارز", "واحد پول", "ڪرنسي", "mata uang"),
        C.COUNTRY to listOf("mulk", "country", "ملک", "देश", "দেশ", "بلد", "دولة", "کشور", "ملڪ", "negara"),
        C.TRADE to listOf("trade", "qisam", "kisam", "qism", "type", "قسم", "किस्म", "प्रकार", "টাইপ", "ধরন", "نوع", "jenis"),
        C.GOODS to listOf(
"کالایی",             "maal", "mal", "saman", "samaan", "cheez", "cheezein", "item", "items", "goods", "product", "products",
            "مال", "سامان", "چیز", "چیزیں", "آئٹم", "پروڈکٹ", "پروڈکٹس", "माल", "सामान", "चीज", "चीजें", "प्रोडक्ट",
            "পণ্য", "মাল", "জিনিস", "আইটেম", "البضائع", "بضاعة", "منتج", "منتجات", "سلعة", "کالا", "جنس", "محصول",
            "محصولات", "پراڊڪٽس", "barang", "produk"
        ),
        C.PRICE to listOf(
            "rate", "ret", "rates", "qeemat", "keemat", "qimat", "kimat", "price", "prices", "daam", "bhao", "bhaao", "bhav",
            "قیمت", "ریٹ", "دام", "بھاؤ", "भाव", "दाम", "कीमत", "रेट", "দাম", "দর", "মূল্য", "রেট", "سعر", "أسعار", "اسعار",
            "ثمن", "نرخ", "قيمت", "ريٽ", "harga", "मूल्य"
        ),
        C.STOCK to listOf(
            "stock", "stok", "maujood", "mojood", "majood", "available", "inventory", "اسٹاک", "سٹاک", "موجود", "स्टॉक",
            "मौजूद", "স্টক", "মজুদ", "مخزون", "المخزون", "موجودی", "انبار", "اسٽاڪ", "persediaan"
        ),
        C.LEFT to listOf(
            "bacha", "bachi", "bache", "remaining", "left", "بچا", "بچی", "بچے", "बचा", "बची", "बचे", "অবশিষ্ট", "تبقى",
            "متبقي", "sisa", "tersisa"
        ),
        C.COST to listOf(
            "cost", "khareed", "kharid", "purchase", "lagat", "laagat", "خرید", "لاگت", "खरीद", "लागत", "কেনা", "কেনার",
            "ক্রয়", "ক্রয়মূল্য", "شراء", "الشراء", "تكلفة", "هزینه", "خريد", "beli", "modal"
        ),
        C.CHEAP to listOf(
            "sasta", "sasti", "saste", "sastaa", "cheap", "cheaper", "cheapest", "lowest", "سستا", "سستی", "سستے", "सस्ता",
            "सस्ती", "सस्ते", "সস্তা", "رخيص", "أرخص", "ارخص", "ارزان", "ارزانتر", "سستو", "murah", "termurah"
        ),
        C.DEAR to listOf(
            "mehnga", "mehenga", "mahenga", "mahanga", "mehngi", "mehange", "mehnge", "expensive", "costly", "dearest",
            "مہنگا", "مہنگی", "مہنگے", "महंगा", "महंगी", "महंगे", "দামি", "দামী", "غالي", "أغلى", "اغلى", "گران",
            "گرانتر", "مهانگو", "mahal", "termahal"
        ),
        C.SUPPLIER to listOf(
            "supplier", "suplier", "supplyer", "suppliers", "vendor", "dealer", "company", "kampani", "distributor",
            "سپلائر", "کمپنی", "ڈیلر", "सप्लायर", "कंपनी", "डीलर", "সাপ্লায়ার", "সরবরাহকারী", "সরবরাহকারীর", "কোম্পানি",
            "مورد", "المورد", "الموردين", "موردين", "شركة", "تامین کننده", "تأمین کننده", "شرکت", "ڪمپني", "pemasok",
            "سپلائرز", "سپلائرن", "सप्लायरों", "تأمین کنندگان", "تامین کنندگان", "الموردون"
        ),
        C.CUSTOMER to listOf(
            "customer", "customers", "gahak", "gaahak", "grahak", "client", "clients", "khareedar", "گاہک", "گاہکوں",
            "کسٹمر", "کسٹمرز", "ग्राहक", "गाहक", "कस्टमर", "কাস্টমার", "গ্রাহক", "ক্রেতা", "عميل", "عملاء", "زبون",
            "زبائن", "مشتری", "مشتریان", "گراهڪ", "pelanggan"
        ),
        C.CAME to listOf(
"mila", "mile", "mili", "masuk", "آيا", "delivered", "رسیده",             "aaya", "aya", "aayi", "aai", "ayi", "aaye", "aye", "delivery", "deliveries", "pohncha", "pahuncha", "arrived",
            "came", "آیا", "آئی", "آئے", "پہنچا", "ڈلیوری", "आया", "आई", "आए", "पहुंचा", "डिलीवरी", "এসেছে", "এল",
            "ডেলিভারি", "وصل", "وصلت", "آمد", "آيو", "datang", "tiba", "kiriman", "توريد", "ورود", "money in"
        ),
        C.BOUGHT to listOf(
            "liya", "liye", "li", "khareeda", "kharida", "khareedi", "bought", "took", "purchased", "لیا", "لیے", "لئے",
            "خریدا", "خریدی", "लिया", "लिए", "खरीदा", "খরিদ", "কিনেছে", "নিয়েছে", "اشترى", "اشتراه", "خریده", "ورتو", "membeli", "dibeli", "خريد ڪيو"
        ),
        // "Kahan se / kis se aaya": where goods came from.
        C.FROM_WHOM to listOf("kahan se", "kis se", "kidhar se", "where from", "کہاں سے", "کس سے", "कहाँ से", "कहां से", "किस से", "কোথা থেকে", "من أين", "از کجا", "dari mana"),
        C.BILL to listOf("bill", "bills", "bil", "parchi", "بل", "بلز", "پرچی", "बिल", "বিল", "tagihan", "nota"),
        C.INVOICE to listOf(
            "invoice", "invoices", "anvoice", "document", "documents", "انوائس", "इनवॉइस", "इनवॉयस", "ইনভয়েস", "فاتورة",
            "فواتير", "الفاتورة", "فاکتور", "faktur"
        ),
        C.CHEQUE to listOf("cheque", "cheques", "chek", "چیک", "चेक", "চেক", "شيك", "شيكات", "الشيكات", "چک", "چيڪ", "cek"),
        C.PLAN to listOf(
            "qist", "qisht", "kist", "qistein", "qiston", "installment", "installments", "instalment", "plan", "plans",
            "قسط", "قسطیں", "قسطوں", "किस्त", "किस्तें", "প্ল্যান", "কিস্তি", "أقساط", "اقساط", "خطة", "رٿا", "رٿائون",
            "cicilan", "angsuran"
        ),
        C.STAFF to listOf(
            "staff", "mulazim", "mulazmeen", "mulazmin", "worker", "workers", "employee", "employees", "naukar", "karigar",
            "kaarigar", "salesman", "ملازم", "ملازمین", "اسٹاف", "سٹاف", "कर्मचारी", "स्टाफ", "কর্মী", "কর্মচারী", "স্টাফ",
            "موظف", "موظفين", "الموظفون", "الموظفين", "کارمند", "کارکنان", "کارکن", "اسٽاف", "karyawan", "pegawai"
        ),
        C.TICKET to listOf("ticket", "tickets", "counter", "pos", "receipt", "receipts", "کاؤنٹر", "काउंटर", "কাউন্টার", "الكاونتر", "پیشخوان", "ڪائونٽر", "kasir", "struk"),
        // 13 Nov 2026: the cashbook by the app's own name for it in each language (Sindhi's is "روڪڙ"; Arabic's and
        // Farsi's one word also means the cash in hand, so only with "book" before it), and goods that came back.
        C.CASHBOOK to listOf(
            "cashbook", "cash book", "کیش بک", "کیشبک", "कैशबुक", "कैश बुक", "ক্যাশবুক", "ক্যাশ বুক", "روڪڙ",
            "دفتر النقد", "سجل النقد", "دفتر الصندوق", "دفتر صندوق", "buku kas"
        ),
        C.RETURN to listOf(
            "wapsi", "wapasi", "return", "returns", "returned", "واپسی", "वापसी", "ফেরত", "مرتجع", "مرتجعات", "المرتجعات",
            "مرجوعی", "برگشتی", "واپسي", "retur"
        ),
        C.CALCULATOR to listOf(
            "calculator", "calculater", "calcolator", "kalkulator", "calc", "calculation", "calculations", "کیلکولیٹر",
            "کیلکیولیٹر", "कैलकुलेटर", "ক্যালকুলেটর", "حاسبة", "الحاسبة", "آلة حاسبة", "ماشین حساب", "ڪئلڪوليٽر", "hitungan"
        ),
        C.APP to listOf("app", "application", "roshan khata", "ایپ", "روشن کھاتہ", "ऐप", "एप", "রোশন খাতা", "অ্যাপ", "تطبيق", "التطبيق", "اپلیکیشن", "ايپ", "aplikasi"),
        C.OPEN to listOf(
"کليو",             "khuli", "khula", "khola", "kholi", "khole", "khulti", "open", "opened", "opens", "chali", "کھلی", "کھولا",
            "کھولی", "کھلا", "खुली", "खोला", "खुला", "खोली", "খোলা", "খুলেছে", "فتح", "فتحت", "باز", "کھلي", "buka", "dibuka"
        ),
        C.TIMES to listOf("ڀيرا", "ڀيرو", "কতবার", "dafa", "dafaa", "dafah", "martaba", "marataba", "baar", "bar", "times", "دفعہ", "مرتبہ", "بار", "बार", "दफा", "मर्तबा", "বার", "مرة", "مرات", "دفعو", "دفعا", "kali"),
        C.MADE to listOf(
"বানিয়েছে", "ساخت", "بنیں", "बनीं", "صادر", "ٺهيا",             "bani", "bana", "bane", "banai", "banaya", "banayi", "banaye", "banayi", "created", "made", "generated",
            "بنی", "بنا", "بنے", "بنائی", "بنایا", "بنائے", "बनी", "बना", "बने", "बनाई", "बनाया", "बनाए", "তৈরি", "বানানো", "صنع", "صنعت", "أنشأ",
            "أنشئت", "إنشاء", "ساخته", "ٺهيو", "ٺهي", "ٺاهيو", "dibuat", "membuat"
        ),
        C.PENDING to listOf(
            "pending", "clear nahi", "uncleared", "due", "baqi", "baaqi", "baki", "بقایا", "باقی", "پینڈنگ", "पेंडिंग",
            "बाकी", "পেন্ডিং", "বাকি", "معلق", "المعلقة", "منتظر", "در انتظار", "پينڊنگ", "tertunda", "belum"
        ),
        C.LOW to listOf("low", "kam", "khatam", "khatm", "finish", "finished", "out of stock", "کم", "ختم", "कम", "खत्म", "কম", "قليل", "نفد", "منخفض", "گهٽ", "rendah", "habis", "menipis"),
        C.HAPPENED to listOf(
"اتفاقی", "افتاد",             "hua", "hui", "hue", "hoa", "huwa", "happened", "happen", "going on", "ہوا", "ہوئی", "ہوئے", "हुआ", "हुई", "हुए",
            "ঘটেছে", "হয়েছে", "হলো", "হল", "حدث", "صار", "جرى", "يجري", "حصل", "شد", "گذشت", "اتفاق", "ٿيو", "ٿي", "terjadi"
        ),
        C.EDITED to listOf(
            "edit", "edited", "badla", "badli", "badle", "tabdeel", "change", "changed", "ایڈٹ", "بدلا", "بدلی", "تبدیل",
            "बदला", "बदली", "एडिट", "বদল", "এডিট", "تعديل", "عدل", "ویرایش", "تغییر", "ubah", "diubah"
        ),
        C.DELETED to listOf(
            "delete", "deleted", "mitaya", "mitayi", "hataya", "hatayi", "remove", "removed", "ڈیلیٹ", "مٹایا", "ہٹایا",
            "हटाया", "मिटाया", "डिलीट", "মুছে", "ডিলিট", "حذف", "ڊليٽ", "hapus", "dihapus"
        ),
        C.USED to listOf("use", "used", "istemal", "istemaal", "istamal", "استعمال", "इस्तेमाल", "ব্যবহার", "استخدام", "استفاده", "pakai", "dipakai", "gunakan", "digunakan"),
        C.SALARY to listOf(
            "tankhwah", "tankhah", "tankha", "tankhwa", "salary", "salaries", "wages", "pagar", "تنخواہ", "तनख्वाह", "वेतन",
            "বেতন", "راتب", "رواتب", "حقوق", "پگهار", "gaji"
        ),
        C.MONEY to listOf(
            "paise", "paisa", "pese", "paisay", "raqam", "rupay", "rupaye", "rupees", "rupee", "money", "پیسے", "پیسہ", "روپے",
            "रुपये", "पैसे", "पैसा", "रकम", "টাকা", "فلوس", "پول", "پئسا", "پئسو", "uang", "duit"
        ),
        // "Mera hisaab": the whole khata, when nobody is named.
        C.ACCOUNT to listOf(
            "hisaab", "hisab", "khata", "khaata", "khate", "account", "balance", "حساب", "کھاتہ", "کھاتا", "کھاتے", "हिसाब",
            "खाता", "खाते", "হিসাব", "খাতা", "کاتو", "saldo", "book", "ledger", "buku", "دفتر", "بیلنس", "بيلنس", "बैलेंस",
            "ব্যালেন্স", "رصيد", "مانده"
        ),
        C.REPORT to listOf("report", "riport", "reporting", "رپورٹ", "रिपोर्ट", "রিপোর্ট", "تقرير", "گزارش", "رپورٽ", "laporan"),
        C.INFO to listOf(
            "maloomat", "malumat", "maalumaat", "information", "info", "details", "detail", "tafseel", "tafsil", "معلومات",
            "تفصیل", "जानकारी", "मालूमात", "तफसील", "তথ্য", "বিস্তারিত", "اطلاعات", "جزئیات", "informasi", "rincian", "تفاصيل", "مشخصات"
        ),
        C.DATA to listOf("data", "deta", "record", "records", "ڈیٹا", "ریکارڈ", "डेटा", "डाटा", "रिकॉर्ड", "ডেটা", "ডাটা", "بيانات", "البيانات", "داده", "ڊيٽا"),
        C.SAFE to listOf(
"bahar", "bhejte", "bhejta", "send", "sends", "باہر", "بھیجتے", "बाहर", "भेजते",             "safe", "secure", "privacy", "private", "mehfooz", "mahfooz", "mehfuz", "محفوظ", "सुरक्षित", "प्राइवेसी",
            "নিরাপদ", "গোপনীয়তা", "آمن", "آمنة", "خصوصية", "امن", "حریم", "aman", "privasi"
        ),
        C.MAKER to listOf("developer", "maker", "creator", "innovation", "ڈیولپر", "डेवलपर", "ডেভেলপার", "مطور", "سازنده", "ڊولپر", "pembuat", "pengembang"),
        C.LANGUAGE to listOf(
            "zuban", "zaban", "zubaan", "zubanein", "zabanein", "bhasha", "language", "languages", "زبان", "زبانیں",
            "भाषा", "भाषाएं", "भाषाएँ", "জবান", "ভাষা", "لغة", "اللغات", "لغات", "ٻولي", "ٻوليون", "bahasa"
        ),
        C.VERSION to listOf("version", "ورژن", "वर्जन", "संस्करण", "ভার্সন", "সংস্করণ", "إصدار", "الإصدار", "الاصدار", "نسخه", "ورزن", "versi"),
        C.DATE to listOf(
"چندم",             "tareekh", "tarikh", "tareek", "date", "hijri", "islami", "chand", "تاریخ", "ہجری", "اسلامی", "तारीख", "दिनांक",
            "हिजरी", "তারিখ", "হিজরি", "تاريخ", "التاريخ", "هجري", "tanggal"
        ),
        C.DAY to listOf("din", "day", "weekday", "دن", "दिन", "वार", "দিন", "يوم", "روز", "ڏينهن", "hari"),
        C.TIME to listOf("waqt", "time", "taim", "baje", "bajay", "clock", "وقت", "ٹائم", "بجے", "बजे", "समय", "टाइम", "সময়", "কয়টা", "الوقت", "الساعة", "ساعت", "jam", "waktu"),
        C.ALL to listOf(
            "sab", "saari", "sari", "saara", "sara", "poori", "puri", "pura", "poora", "tamam", "all", "everything",
            "overview", "سب", "ساری", "سارا", "پوری", "پورا", "تمام", "सब", "सारी", "सारा", "पूरी", "पूरा", "সব", "সমস্ত",
            "جميع", "همه", "سڀ", "semua", "seluruh"
        ),
        C.HEALTH to listOf("haal", "khairiyat", "khair", "mizaj", "tabiyat", "حال", "خیریت", "مزاج", "طبیعت", "हाल", "खैरियत", "मिजाज", "খবর", "كيفك", "حالك", "kabar"),
        // "Kaisa / kaise": how something is (the "how to" of a help page is Hudhud's own HOW list).
        C.HOW_IS to listOf("kaisa", "kaisi", "kaise", "kesa", "kesi", "kese", "kaisay", "kesay", "کیسا", "کیسی", "کیسے", "कैसा", "कैसी", "कैसे", "কেমন", "كيف", "چطور", "ڪيئن", "bagaimana", "gimana"),
        C.ABLE to listOf(
            "sakte", "sakta", "sakti", "sakty", "sakoge", "sakogay", "can", "could", "سکتے", "سکتا", "سکتی", "सकते", "सकता",
            "सकती", "পারেন", "পারো", "পারবে", "يمكنك", "تستطيع", "توانی", "میتونی", "سگهو", "سگهين", "bisa", "dapat"
        ),
        C.TELL to listOf("bata", "batao", "bta", "btao", "tell", "say", "بتا", "بتاؤ", "बता", "बताओ", "বলতে", "বলো", "تخبر", "أخبر", "بگو", "ٻڌائي", "beritahu"),
        C.DO to listOf("kar", "kr", "karte", "do", "کر", "کرتے", "कर", "करते", "করতে", "تفعل", "بکنی", "ڪري", "lakukan"),
        C.KNOW to listOf("samajhte", "samajhta", "samajh", "understand", "سمجھتے", "سمجھتا", "समझते", "समझता", "বোঝো", "বোঝেন", "تفهم", "سمجهو", "mengerti", "paham", "bolte", "bolta", "speak", "بولتے", "बोलते", "pata", "پتہ", "पता", "aata", "aati", "maloom", "malum", "know", "knows", "آتا", "آتی", "معلوم", "आता", "आती", "मालूम", "জানো", "জানেন", "تعرف", "بلدی", "میدانی", "ڄاڻو", "tahu")
    )

    /**
     * Concept words that are also common parts of people's and shops' names ("Malik Riaz", "Bismillah Store", ملک
     * — Malik in Urdu letters). They count as a concept only when the sentence names nobody with them.
     */
    private val SOFT: Set<String> by lazy {
        listOf("malik", "maalik", "seth", "ملک", "مالک", "store", "shop", "bank", "company", "dealer", "goth", "counter",
            "chand", "din", "khair", "bacha", "walid", "mehfooz", "bata", "kam")
            .map { norm(it) }.toSet()
    }

    private fun norm(s: String) = VoiceQuestion.normalise(s)

    /** Each single word with what it means, and each phrase (two words or more) with what it means. */
    private val WORDS: Map<String, Set<C>> by lazy {
        val out = HashMap<String, MutableSet<C>>()
        for ((c, list) in LEXICON) for (w in list) {
            val k = norm(w)
            if (k.isNotEmpty() && !k.contains(' ')) out.getOrPut(k) { HashSet() } += c
        }
        out
    }

    private val PHRASES: List<Pair<List<String>, C>> by lazy {
        LEXICON.flatMap { (c, list) -> list.map { norm(it) }.filter { it.contains(' ') }.map { it.split(' ') to c } }
            .sortedByDescending { it.first.size }
    }

    /** Folded spellings of the Roman-Urdu and English words, for a word heard a letter off. */
    private val BY_SOUND: Map<String, Set<C>> by lazy {
        val out = HashMap<String, MutableSet<C>>()
        for ((w, cs) in WORDS) {
            if (w.length < 4 || w.any { it !in 'a'..'z' }) continue
            val f = NameSearch.fold(w)
            if (f.length >= 4) out.getOrPut(f) { HashSet() } += cs
        }
        out
    }

    /**
     * A word's consonants as they are heard, in Latin letters, whatever script wrote it (13 Nov 2026, the owner: "har
     * zaban ke hisaab se khud ba khud", not one word at a time): the phone writes the English and Roman-Urdu words a
     * shopkeeper says in his own letters — "کیلکولیٹر", "سپلائر", "कैलकुलेटर", "ক্যালকুলেটর", "اسٹاک", "بیک اپ" — and each
     * of them sounds like a word the lexicon already holds in Latin letters. NameSearch.fold turns any of the app's
     * scripts into Latin letters by sound (its rule for names, "بشیر" and "Bashir"); the vowels then go (a script
     * writes them or not, long or short, as it pleases) and a letter said twice is said once: "calculator",
     * "کیلکولیٹر" and "कैलकुलेटर" are all "klkltr". Bengali's ya-phala (্য) is a glide, not a letter of its own.
     */
    internal fun skeleton(word: String): String {
        val folded = NameSearch.fold(word.replace("\u09CD\u09AF", ""))
        val out = StringBuilder()
        for (ch in folded) {
            if (ch in "aeiouyw" || !ch.isLetter()) continue
            if (out.isNotEmpty() && out.last() == ch) continue
            out.append(ch)
        }
        return out.toString()
    }

    /** The fewest consonants a word must have to be met by its sound alone: two meet far too many words. */
    private const val SKELETON_MIN = 3

    /**
     * Every Latin word of the lexicon by its [skeleton], with what it means; a sound that two different meanings share
     * is left out (it would be a guess), as is one shorter than [SKELETON_MIN].
     */
    private val SKELETONS: Map<String, Set<C>> by lazy {
        val out = HashMap<String, Set<C>>()
        val clash = HashSet<String>()
        for ((w, cs) in WORDS) {
            if (w.any { it !in 'a'..'z' } || w in SOFT) continue
            val k = skeleton(w)
            if (k.length < SKELETON_MIN) continue
            val had = out[k]
            when {
                had == null -> out[k] = cs
                had != cs -> clash += k
            }
        }
        for (k in clash) out.remove(k)
        out
    }

    /** What a word written in any script means by its sound ([SKELETONS]); empty when nothing sounds like it. */
    internal fun bySkeleton(word: String): Set<C> {
        val k = skeleton(word)
        return if (k.length < SKELETON_MIN) emptySet() else SKELETONS[k].orEmpty()
    }

    /** "the", "and the", "with the", "for the", "and", "with", "for", "so": joined to the word after them in Arabic. */
    private val ARABIC_FRONT = listOf("وال", "بال", "فال", "ال", "لل", "و", "ب", "ل", "ف")

    /** Bengali's case endings, joined to a word. */
    private val BENGALI_ENDS by lazy { listOf("দের", "কের", "ের", "কে", "র", "এ", "ে", "টা", "টি", "ও").map { norm(it) } }

    /**
     * "رقم" is a telephone number in Arabic ("رقم علي", Ali's number) and an amount of money in Urdu and Sindhi ("آج کتنی
     * رقم آئی"): which, the sentence's own language decides ([arabicOnly]).
     */
    private val RAQAM by lazy { norm("رقم") }

    private fun twoTongued(word: String, arabic: Boolean): Set<C> =
        if (word == RAQAM || ARABIC_FRONT.any { word == it + RAQAM }) setOf(if (arabic) C.PHONE else C.MONEY) else emptySet()

    /** A word whose own meaning gives way when a phrase takes it ("من": Persian's "me" in "دفتر من"). */
    private val GIVES_WAY: Set<String> by lazy { setOf(norm("من")) }

    /** Letters only Urdu, Sindhi or Persian write, after [VoiceQuestion.normalise] (which folds ي ك ه into ی ک ہ). */
    private const val NOT_ARABIC_LETTERS = "\u0679\u0688\u0691\u06BA\u06D2\u06BE\u06AF\u067E\u0686\u0698\u06AA\u067B\u0680\u0684\u0683" +
        "\u0687\u068C\u068D\u068F\u0699\u06B3\u06B1\u06BB\u067A\u067D\u067F\u06A6\u06FE\u06FD"

    /**
     * Little words of Urdu, Sindhi and Persian that Arabic never writes. Not Persian's "آن" (that): with the madda
     * folded away it is "ان", which is also how Arabic's own "أن" and "إن" are spelt for matching (13 Nov 2026).
     */
    private val NOT_ARABIC_WORDS: Set<String> by lazy {
        listOf(
            "کا", "کی", "کے", "ہے", "ہیں", "ہو", "میں", "کو", "سے", "نے", "آج", "کتنا", "کتنی", "کتنے", "کب", "کون", "کیا", "یہ",
            "وہ", "اور", "بھی", "تھا", "تھی", "آئی", "آیا", "آئے", "ہوا", "ہوئی", "دیا", "دیے", "کس", "کسی", "کچھ", "نہیں",
            "اپنا", "اپنی", "اپنے", "میرا", "میری", "میرے", "تمام", "سب", "اب", "پر", "تک", "اس", "جو", "جي", "جا", "آهي",
            "هن", "مان", "توهان", "است", "را", "می", "این", "برای", "از", "با", "هست", "شما", "امروز", "دیروز", "تو"
        ).map { norm(it) }.toSet()
    }

    /** A sentence in Arabic: Arabic letters, and nothing only Urdu, Sindhi or Persian would write. */
    internal fun arabicOnly(said: List<String>): Boolean =
        said.any { w -> w.any { it in '\u0600'..'\u06FF' } } &&
            said.none { w -> w in NOT_ARABIC_WORDS || w.any { it in NOT_ARABIC_LETTERS } }

    /**
     * Arabic's "your" and "my" joined to a word's end, in an Arabic sentence only: "صنعك" (made you), "بياناتي" (my
     * data), "دفتري" (my book), "رقمك" (your number). The word without them must be one Hudhud knows.
     */
    private fun arabicJoined(word: String): Set<C> {
        if (word.length < 4) return emptySet()
        val who = when (word.last()) {
            '\u06A9' -> C.YOU
            '\u06CC' -> C.ME
            else -> return emptySet()
        }
        val stem = word.dropLast(1)
        val base = meaningOf(stem).ifEmpty { twoTongued(stem, true) }
        return if (base.isEmpty()) emptySet() else base + who
    }

    /** Indonesian's "his", "your", "my", joined to the word they own: "nomornya", "namamu". */
    private val INDONESIAN_ENDS = listOf("nya" to C.HIS, "mu" to C.YOU, "ku" to C.ME)

    /**
     * What a sentence's words mean. [names] says whether a word is one of the book's own names (a customer, a supplier
     * or a product): such a word is never read as a concept by its sound, only when it is the concept's very word.
     */
    class Read internal constructor(val said: List<String>, private val meanings: List<Set<C>>, val bySound: Set<Int>) {
        val all: Set<C> = meanings.flatten().toSet()
        fun has(c: C) = c in all
        fun any(vararg cs: C) = cs.any { it in all }
        /** What word [i] means (empty: nothing Hudhud knows). */
        fun at(i: Int): Set<C> = meanings[i]
        /** Words that mean none of Hudhud's concepts. */
        fun bare(): List<String> = said.filterIndexed { i, _ -> meanings[i].isEmpty() }
        /**
         * True when every word is one of [allowed] concepts or a word that asks nothing ([quiet]: the little words
         * between, a greeting, a friendly "yaar").
         */
        fun onlyOf(allowed: Set<C>, quiet: (String) -> Boolean): Boolean =
            said.indices.all { i -> quiet(said[i]) || (meanings[i].isNotEmpty() && meanings[i].any { it in allowed }) }
    }

    /** The concepts a word means by itself or with its language's joined pieces; empty when none. */
    fun meaningOf(word: String): Set<C> {
        WORDS[word]?.let { return it }
        if (word.isEmpty()) return emptySet()
        val first = word[0]
        when {
            first in '؀'..'ۿ' -> for (p in ARABIC_FRONT) {
                if (word.length > p.length + 1 && word.startsWith(p)) WORDS[word.substring(p.length)]?.let { return it }
            }
            first in 'ঀ'..'৿' -> for (e in BENGALI_ENDS) {
                if (word.length > e.length + 1 && word.endsWith(e)) WORDS[word.dropLast(e.length)]?.let { return it }
            }
            first in 'a'..'z' -> {
                if (word.length >= 5 && word.endsWith("es")) WORDS[word.dropLast(2)]?.let { return it }
                if (word.length >= 5 && word.endsWith("s")) WORDS[word.dropLast(1)]?.let { return it }
                for ((end, c) in INDONESIAN_ENDS) {
                    if (word.length >= end.length + 4 && word.endsWith(end)) {
                        WORDS[word.dropLast(end.length)]?.let { return it + c }
                    }
                }
            }
        }
        return emptySet()
    }

    /** A word that is surely a concept, whatever the book's names: its own word, not soft, not only by sound. */
    fun isHardWord(word: String): Boolean = word !in SOFT && meaningOf(word).isNotEmpty()

    /** A concept word that is also a common name part ("Malik", "Store"). */
    fun isSoftWord(word: String): Boolean = word in SOFT

    /**
     * Reads [said] (already normalised and split). [isName] answers whether a word is one of the book's own names;
     * only words it says no to may be read by sound.
     */
    fun read(said: List<String>, isName: (String) -> Boolean = { false }): Read {
        val meanings = MutableList<Set<C>>(said.size) { emptySet() }
        // Phrases first: their words take the phrase's meaning together ("ka pata" is an address).
        val covered = BooleanArray(said.size)
        for ((words, c) in PHRASES) {
            var i = 0
            while (i + words.size <= said.size) {
                if ((words.indices).all { said[i + it] == words[it] }) {
                    for (j in words.indices) {
                        meanings[i + j] = meanings[i + j] + c
                        covered[i + j] = true
                    }
                    i += words.size
                } else {
                    i++
                }
            }
        }
        val arabic = arabicOnly(said)
        for (i in said.indices) {
            val w = said[i]
            // Persian's "my" after its noun ("دفتر من") is not Arabic's "who": the phrase alone says what it is.
            if (covered[i] && w in GIVES_WAY) continue
            val m = meaningOf(w).ifEmpty { twoTongued(w, arabic) }.ifEmpty { if (arabic) arabicJoined(w) else emptySet() }
            // A phrase's word keeps its own meaning too ("is ka": "is" is also "this"), except "pata" and friends,
            // whose only meaning here is the phrase's.
            if (m.isNotEmpty()) meanings[i] = meanings[i] + m
        }
        val bySound = HashSet<Int>()
        for (i in said.indices) {
            if (meanings[i].isNotEmpty() || covered[i]) continue
            val w = said[i]
            if (w.length < 5 || w.any { it !in 'a'..'z' }) continue
            if (isName(w)) continue
            val f = NameSearch.fold(w)
            if (f.length < 4) continue
            val near = BY_SOUND[f] ?: if (f.length >= 5) {
                BY_SOUND.entries.firstOrNull { (k, _) -> k.length >= 5 && NameSearch.withinOneEdit(k, f) }?.value
            } else null
            if (near != null) {
                meanings[i] = near
                bySound += i
            }
        }
        // By sound across scripts (13 Nov 2026): a word still unknown — "کیلکولیٹر", "सप्लायर", "ক্যালকুলেটর", or a Roman
        // word spelt by ear ("kalkuletar") — meets the lexicon's Latin word that sounds the same. Then two unknown words
        // side by side, for the English words a recogniser writes in two ("کیش بک", "بیک اپ").
        fun unknown(i: Int) = meanings[i].isEmpty() && !covered[i] && said[i].length >= 3 && !isName(said[i]) &&
            !said[i].all { it.isDigit() }
        for (i in said.indices) {
            if (!unknown(i)) continue
            val near = bySkeleton(said[i])
            if (near.isNotEmpty()) {
                meanings[i] = near
                bySound += i
            }
        }
        for (i in 0 until said.size - 1) {
            if (!unknown(i) || !unknown(i + 1)) continue
            if (said[i].any { it in 'a'..'z' } != said[i + 1].any { it in 'a'..'z' }) continue
            val near = bySkeleton(said[i] + said[i + 1])
            if (near.isNotEmpty()) {
                meanings[i] = near
                meanings[i + 1] = near
                bySound += i
                bySound += i + 1
            }
        }
        return Read(said, meanings, bySound)
    }

    /** Every single word of every concept, normalised (tests and the name search's exclusions). */
    internal fun allWords(): Set<String> = WORDS.keys
}
