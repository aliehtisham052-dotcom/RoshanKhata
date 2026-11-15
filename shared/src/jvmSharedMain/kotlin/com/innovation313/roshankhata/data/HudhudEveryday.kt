package com.innovation313.roshankhata.data

/**
 * Everyday phrasings a shopkeeper really says (15 Nov 2026, a read-only review of 1,115 sentences in nine languages): who paid
 * and who does not, where the money went, what went out of the drawer, what sells best. Each is read from the words alone, as a
 * reading of the rules the sense may not turn ([Hudhud.decide] keeps whatever [read] says), and only when the sentence names
 * nobody and nothing the book holds — a customer called "Ramzan" or "Kamal" is never read as a word here.
 *
 * Nothing here writes or sends anything: it only says which question the words ask. Every answer is the existing one, gated as it
 * was (the day's money and the cashbook need REPORTS).
 */
internal object HudhudEveryday {
    private fun has(joined: String, list: List<String>) = VoiceQuestion.saysAny(joined, list)

    // ------------------------------------------------------------------------------------------------ the words

    /** "Not", "never", "no": a negation turns every payment word round (a payment that was not made, or will not be). */
    private val NEGATION = listOf(
        "nahi", "nahin", "nhi", "nai", "never", "not", "no one", "nobody", "don t", "doesn t", "hasn t", "haven t", "didn t", "won t",
        "نہیں", "نہی", "نا کیا", "لا", "لم", "لن", "هیچوقت", "هرگز", "نمی", "نه", "نہ", "नहीं", "नही", "না", "নি", "tidak", "tak", "bukan",
        "belum", "gak", "nggak", "enggak", "tdk", "ڪڏهن نه", "ڪڏهن به نه"
    )

    /** True when the sentence denies what it says. */
    fun negated(joined: String): Boolean = has(joined, NEGATION)

    /** Who: the person who gives, in a question. */
    private val WHO = listOf(
        "kis ne", "kisne", "kis kis ne", "kaun", "kon", "kaun kaun", "who", "who all", "من", "کس نے", "کس کس نے", "کون", "کون کون",
        "किस ने", "किसने", "कौन", "कौन कौन", "কে", "কারা", "ڪنهن", "ڪير", "چه کسی", "چه کسانی", "siapa", "siapa saja", "siapa aja"
    )

    private val MONEY = listOf(
        "paise", "paisa", "pese", "raqam", "rakam", "cash", "money", "funds", "payment", "payments", "پیسے", "پیسہ", "پیسا", "رقم",
        "پول", "پول ها", "پولها", "ادائیگی", "कैश", "पैसे", "पैसा", "रकम", "পেমেন্ট", "টাকা", "ক্যাশ", "ক্যাশে", "أموال", "الأموال", "اموال", "الاموال",
        "نقود", "النقود", "أموالي", "اموالي", "أمواله", "امواله", "أموالهم", "اموالهم", "فلوس", "مبلغ", "كاش", "کیش", "پئسا", "پئسن", "پئسو", "ادائيگي", "uang", "duit", "pembayaran", "tunai"
    )

    /** Giving money, in the past: "diye", "paid", "داد"… said of whoever gave it. Needs a word of money beside it. */
    private val GAVE = listOf(
        "diye", "diya", "dia", "dee", "dii", "di", "de diye", "de diya", "gave", "given", "دیے", "دیا", "دئیے", "دی", "دے دیے", "दिए", "दिया",
        "दी", "दिये", "दे दिए", "দিয়েছে", "দিয়েছেন", "দিল", "দিলো", "داد", "دادند", "ڏنا", "ڏنو", "ڏنائين", "ڏنائون", "أعطى", "أعطوا"
    )

    /** Paying, in the past, in words that mean money by themselves. */
    private val PAID = listOf(
        "paid", "ada kiye", "ada kiya", "ada ki", "jama kiye", "jama kiya", "ادا کیے", "ادا کیا", "جمع کرائے", "अदा किए", "जमा किए", "دفع",
        "دفعوا", "سدد", "سددوا", "پرداخت کرد", "پرداخت کردند", "পরিশোধ করেছে", "শোধ করেছে", "ادا ڪيا", "ادا ڪيو", "sudah bayar",
        "telah bayar", "telah membayar", "sudah membayar", "sudah melunasi"
    )

    /** Indonesian "bayar" has no tense: it is "paid" only beside a day that is past. "Hari ini" is the Radar chip's own "who pays today". */
    private val BAYAR = listOf("bayar", "membayar", "melunasi")

    /** The shop is the one who paid: not a payment that came in. */
    private val SHOP_PAID = listOf(
        "maine", "mainay", "main ne", "mai ne", "hum ne", "humne", "hamne", "kis ko", "kisko", "kis kis ko", "میں نے", "ہم نے", "کس کو", "کسے",
        "मैंने", "हमने", "किस को", "किसको", "i paid", "we paid", "saya bayar", "kepada siapa", "لمن", "to whom", "به کی"
    )

    /** "When": a question of the day, not of who (it is the Radar's or the span's). */
    private val WHEN = listOf("kab", "kb", "when", "کب", "कब", "কবে", "ڪڏهن", "متى", "kapan")

    /** Giving, in the present or the habit: "deta", "pays", "می‌دهد"… for "who does not give". */
    private val GIVES = listOf(
        "deta", "dete", "deti", "de raha", "de rahe", "de rahi", "dega", "degi", "denge", "pay", "pays", "paying", "give", "gives", "bayar",
        "membayar", "melunasi", "يرد", "يعيد", "دیتا", "دیتے", "دیتی", "دے رہا", "دے رہے", "دے گا", "دیگا", "देता", "देते", "देती", "दे रहा", "देगा", "দেয়",
        "দেবে", "দেন", "يدفع", "يسدد", "تدفع", "دهد", "می دهد", "میدهد", "پرداخت", "ڏيندو", "ڏيندا", "ڏئي", "ڏيندي"
    )


    // ------------------------------------------------------------------------------------------- who owes me, and the idioms

    /** "Whose": the person a thing belongs to ("kis ka udhaar", "پول چه کسی"). */
    private val WHOSE = listOf(
        "kis ka", "kis ki", "kis ke", "kiska", "kiski", "kiske", "whose", "کس کا", "کس کی", "کس کے", "किसका", "किसकी", "किसके", "কার", "কাদের",
        "ڪنهن جو", "ڪنهن جي", "ڪنهن جا", "چه کسی", "من الذي", "uang siapa", "utang siapa", "siapa yang"
    )

    /** "To me", said of whoever gives: the shop is the one who receives. */
    private val TO_ME = listOf(
        "mujhe", "mujhko", "mujh ko", "mujhay", "to me", "مجھے", "مجھ کو", "मुझे", "मुझको", "আমাকে", "مون کي", "به من", "بمن", "لي", "إلي", "kepada saya",
        "pada saya", "ke saya", "ke aku", "kepada aku"
    )

    /** Giving, ahead or in the habit, said of who gives ("dega", "देगा", "سيدفع", "ڏيندو"). */
    private val WILL_GIVE = listOf(
        "dega", "degi", "denge", "dete", "dena", "will give", "will pay", "gives", "pays", "دے گا", "دیگا", "دے گی", "دیں گے", "دینگے", "देगा", "देंगे",
        "দেবে", "দেবেন", "ڏيندو", "ڏيندا", "ڏيندي", "سيدفع", "سيعطي", "سيسدد", "سيرد", "يدفع", "يعطي", "می دهد", "میدهد", "می دهند", "میدهند",
        "دهد", "دهند", "akan bayar", "akan membayar", "bayar"
    )

    /** A payment that did not arrive, without a "no" of its own: "আসেনি", "نیامده", "belum masuk". */
    private val NOT_ARRIVED = listOf(
        "আসেনি", "আসে নি", "মেটায়নি", "মেটায় নি", "نیامده", "نیامد", "نیومده", "نکرده", "صاف نکرده", "نه آيو", "belum masuk", "belum lunas", "belum melunasi",
        "belum dibayar", "belum bayar"
    )

    /** A payment that arrived: needs a "no" beside it ("nahi aaya", "نہیں آیا"). */
    private val ARRIVED = listOf(
        "aaya", "aaye", "aayi", "aya", "mila", "mili", "wusool hua", "wasool hua", "آیا", "آئے", "آئی", "ملا", "ملی", "وصول ہوا", "आया", "आए", "आई",
        "मिला", "মেটেনি", "آيو", "آيا", "مليو", "وصل", "جاء", "masuk", "diterima"
    )

    /** Money stuck with somebody: "phansa", "atka", "عالقة", "گیر کرده" — nothing more is needed to say it is not paid. */
    private val STUCK = listOf(
        "phansa", "phansi", "phansay", "atka", "atki", "atke", "atka hua", "atki hui", "ruka hua", "ruki hui", "stuck", "pending", "فنسا", "پھنسا",
        "پھنسی", "اٹکا", "اٹکی", "اٹکے", "फँसा", "फंसा", "फँसी", "फंसी", "अटका", "अटकी", "আটকে", "আটকা", "عالقة", "عالقه", "معلقة", "گیر کرده", "گیر",
        "ڦاٿل", "ڦاٿو", "اڙيل", "terjebak", "macet", "tersangkut", "tertahan"
    )

    /** An account left open: "hisaab saaf", "account cleared", "حساب را صاف". */
    private val ACCOUNT = listOf(
        "hisaab", "hisab", "account", "accounts", "حساب", "हिसाब", "হিসাব", "رصيد", "akun", "rekening"
    )
    private val CLEARED = listOf(
        "saaf", "clear", "cleared", "settle", "settled", "chukaya", "chuka", "صاف", "چکایا", "साफ", "चुकाया", "মেটায়", "মেটাল", "مٽايو", "ڪيو", "ڪيو آهي",
        "سوّى", "صفى", "صفّى", "ختم", "lunas", "melunasi", "diselesaikan"
    )

    /** "In debt": the owner's own words for what he owes ("qarzdar", "مقروض"), with himself the one in debt. */
    private val INDEBTED = listOf("qarzdar", "karzdar", "mazrooz", "مقروض", "قرضدار", "कर्जदार", "क़र्ज़दार", "indebted", "debtor")
    private val SELF = listOf("main", "mai", "hun", "hoon", "i am", "am i", "میں", "ہوں", "मैं", "हूं", "हूँ")

    /** A debt word alone ("baqaya", "baqi", "udhaar"): the total the shop is owed. */
    private val BARE_DEBT = listOf(
        "baqaya", "baqi", "baaqi", "udhaar", "udhar", "udhari", "بقایا", "باقی", "ادھار", "اُدھار", "बकाया", "बाकी", "उधार", "বাকি", "বকেয়া", "اڌار", "باقي", "بقايا",
        "dues", "due", "owed", "arrears"
    )


    // ------------------------------------------------------------------------------- a product's price, cost, stock: the shapes

    /** Asking the selling price in a shape of its own ("Urea kitne ki hai", "how much is Urea", "Urea چند است"). */
    private val PRICE_SHAPE = listOf(
        "kitne ki", "kitne ka", "kitne ke", "kitne mein", "kitne me", "kitne ka hai", "how much is", "how much for", "how much are", "what do you charge",
        "what is the price", "کتنے کی", "کتنے کا", "کتنے کے", "کتنے میں", "कितने की", "कितने का", "कितने के", "कितने में", "بكم", "بکم", "چند است", "چند",
        "ڪيترين جي", "ڪيتري جي", "ڪيترين جو", "ڪيترو آهي", "কত করে", "কত টাকা", "berapaan", "berapa harganya"
    )

    /** What the shop paid, in a shape of its own ("kitne ki aati hai", "kitne ki parti hai", "what did I buy it for"). */
    private val COST_SHAPE = listOf(
        "aati hai", "aata hai", "aati thi", "aayi thi", "aaya tha", "parti hai", "padti hai", "padta hai", "padi thi", "did i buy", "did i pay", "do i pay",
        "do i buy", "i bought", "i pay for", "آتی ہے", "آتا ہے", "پڑتی ہے", "پڑتا ہے", "आती है", "आता है", "पड़ती है", "पड़ता है", "কিনেছি", "কিনি",
        "اشتريت", "اشتريته", "خريدم", "خریدم", "خريد", "beli berapa", "beli dengan harga"
    )
    private val BUY_FOR = listOf("kitne ki aati", "kitne ka aata", "kitne mein aati", "kitne mein aata", "kitne ki parti", "kitne ka padta", "mujhe kitne")

    /** Asking what is left, in a shape of its own ("Urea hai ya nahi", "Urea kam hai kya", "is Urea running low", "كم بقي من"). */
    private val STOCK_SHAPE = listOf(
        "hai ya nahi", "hai ya nhi", "hai ya nahin", "kam hai", "kam ho", "kam reh", "kam rah", "kam bachi", "running low", "run low", "running out",
        "low on", "is low", "كم بقي", "كم تبقى", "كم بقيت", "مانده", "مونده", "باقی مانده", "ہے یا نہیں", "ہے یا نہی", "کم ہے", "کم ہو", "है या नहीं", "कम है", "कम हो", "گهٽ آهي",
        "گھٹ آھي", "কম আছে", "আছে কি না", "ada tidak", "masih ada", "tinggal berapa", "sisa berapa", "tersisa berapa"
    )

    /** The shop buying, in the verbs a cost is asked with (review O). */
    private val WE_BUY = listOf(
        "نشتري", "نشتريه", "نشتريها", "اشتري", "نشتري به", "می خریم", "میخریم", "خریدیم", "می خرید", "কিনি", "কিনেছি", "কিনে", "kharidte", "khareedte",
        "kharidi", "khareedi", "we buy", "do we buy", "did we buy", "kami beli", "kita beli"
    )

    /** "Who supplies it", "from whom do we get it" (review O). */
    private val SUPPLIES = listOf("يورد", "يوردها", "يورده", "من يورد", "از کی می گیریم", "از کی میگیریم", "از چه کسی می گیریم", "از چه کسی میگیریم")

    /** The stock's own noun (review O). */
    private val STOCK_NOUN = listOf("stock", "stok", "اسٹاک", "اسٽاڪ", "स्टॉक", "স্টক", "مخزون", "المخزون", "موجودی", "موجودي")

    /**
     * The facet a product's question asks when its shape says so and the rules left it at the whole overview (or took a price's
     * "how many" for the shelf): the cost first, then the price, then the shelf. Null: no change.
     */
    fun goodsFacet(heard: String, current: Hudhud.GoodsFacet): Hudhud.GoodsFacet? {
        val joined = Hudhud.norm(heard)
        // "At what price do WE buy Sugar" (review O: "بكم نشتري", "Sugar কত দামে কিনি", "Sugar را چند می خریم" were told the selling
        // price): a verb of the shop buying turns a price into the cost, the figure the shop paid.
        if (current == Hudhud.GoodsFacet.PRICE && has(joined, WE_BUY)) return Hudhud.GoodsFacet.COST
        if (current != Hudhud.GoodsFacet.OVERVIEW && current != Hudhud.GoodsFacet.STOCK) return null
        // "Who supplies it", "from whom do we get it" (review O): its suppliers.
        if (current == Hudhud.GoodsFacet.OVERVIEW && has(joined, SUPPLIES)) return Hudhud.GoodsFacet.SUPPLIERS
        if (current == Hudhud.GoodsFacet.OVERVIEW) {
            // "When did Urea last come in": the last delivery. "Urea ڪنهن ورتي" (Sindhi): who bought it.
            if (has(joined, listOf("come in", "came in", "come", "arrive", "arrived")) && has(joined, listOf("last", "latest", "recently")) &&
                has(joined, listOf("when", "date", "day"))
            ) return Hudhud.GoodsFacet.LAST_IN
            if (has(joined, listOf("ڪنهن")) && has(joined, listOf("ورتي", "ورتو", "ورتا", "خريد ڪيو"))) return Hudhud.GoodsFacet.BUYERS
        }
        val cost = has(joined, COST_SHAPE) || has(joined, BUY_FOR) || has(joined, WE_BUY)
        val price = has(joined, PRICE_SHAPE)
        val stock = has(joined, STOCK_SHAPE)
        // The stock said by its own noun ("Urea جو اسٽاڪ ڪيترو آهي") is never turned into the price by a "how much" (review O).
        val stockNoun = has(joined, STOCK_NOUN)
        val to = when {
            cost -> Hudhud.GoodsFacet.COST
            price && !stock && !stockNoun -> Hudhud.GoodsFacet.PRICE
            stock && !price -> Hudhud.GoodsFacet.STOCK
            else -> return null
        }
        return if (to == current) null else to
    }


    // ---------------------------------------------------------------------------------------------------- counting people

    private val PEOPLE = listOf(
        "log", "banday", "bande", "bandey", "people", "persons", "workers", "orang", "لوگ", "بندے", "ماڻهو", "लोग", "জন", "লোক", "أشخاص", "اشخاص", "افراد", "نفر",
        "عمال", "کارگر"
    )
    private val WORKS = listOf(
        "kaam karte", "kaam karta", "kaam kar rahe", "work", "works", "working", "کام کرتے", "کام کرتا", "کام کر رہے", "काम करते", "काम करता", "कार्य करते",
        "কাজ করে", "কাজ করেন", "ڪم ڪن", "ڪم ڪندا", "يعمل", "يعملون", "کار می کنند", "کار میکنند", "bekerja", "kerja"
    )
    private val CAME_TO_BUY = listOf(
        "kharidne aaye", "kharidne aye", "kharidne aaya", "kharidne aate", "came to buy", "bought today", "خریدنے آئے", "خریدنے آتے", "खरीदने आए", "खरीदने आते",
        "কিনতে এসেছে", "কিনতে এলো", "اشترى", "خريد آمدند", "datang beli", "datang membeli"
    )
    private val SALES_COUNT = listOf("how many sales", "kitni sales", "کتنی سیلز", "कितनी सेल्स")

    // ----------------------------------------------------------------------------------------- where the money goes

    /** A debt's words: "what I have" is then the book's, not the drawer's. */
    private val OWING = listOf("udhaar", "baqaya", "baqi", "qarz", "owe", "owed", "owes", "ادھار", "بقایا", "قرض", "دين", "دين", "ديون", "utang", "hutang",
        "उधार", "बकाया", "बाकी", "বাকি", "বকেয়া", "اڌار", "اڌارو")

    private val WHERE = listOf(
        "kahan", "kahaan", "kaha", "kidhar", "where", "کہاں", "کدھر", "कहाँ", "कहां", "কোথায়", "أين", "اين", "کجا", "کجاست", "ڪٿي", "ڪيڏانهن",
        "ke mana", "kemana", "di mana", "dimana"
    )

    private val WENT = listOf(
        "gaye", "gaya", "gayi", "gae", "chale gaye", "chala gaya", "gone", "went", "go", "goes", "going", "گئے", "گیا", "گئی", "गए", "गया", "गये",
        "গেল", "গেছে", "গিয়েছে", "ذهبت", "ذهب", "ذهبوا", "رفت", "رفتند", "ويا", "وئا", "وئي"
    )

    /** "Where did the money go": the money that went out, the cashbook's. */
    private val WENT_TO = listOf("ke mana", "kemana", "pergi ke mana")

    private val MINE = listOf(
        "mera", "meri", "mere", "my", "mine", "میرا", "میری", "میرے", "मेरा", "मेरी", "मेरे", "আমার", "أموالي", "مالي", "من", "منهنجو", "منهنجا",
        "منهنجي", "saya", "aku", "punyaku"
    )

    /** A word that says money the shop has, in the drawer: "mere paas". */
    private val WITH_ME = listOf(
        "mere paas", "mere pas", "میرے پاس", "मेरे पास", "আমার কাছে", "معي", "لدي", "عندي", "نزدم", "پیش من", "مون وٽ", "saya punya", "ada di saya",
        "i have", "do i have", "have i got", "with me", "in hand"
    )

    private val HOW_MUCH = listOf(
        "kitna", "kitne", "kitni", "how much", "how many", "کتنا", "کتنے", "کتنی", "कितना", "कितने", "कितनी", "কত", "كم", "چقدر", "ڪيترو", "ڪيترا",
        "ڪيتري", "berapa"
    )

    // --------------------------------------------------------------------------------------------- money going out

    private val OUT_VERBS = listOf(
        "nikle", "nikla", "nikli", "nikal", "bahar gaye", "bahar gaya", "spend", "spent", "spending", "spend kiya", "نکلے", "نکلا", "نکلی", "باہر گئے",
        "निकले", "निकला", "বের", "বেরিয়ে", "خارج", "صرفت", "صرفنا", "صرف", "أنفقت", "اتفقت", "خرج", "نڪتا", "نڪتو", "نڪتي", "keluar", "dikeluarkan"
    )

    private val IN_VERBS = listOf(
        "aaye", "aaya", "aayi", "ae", "came", "came in", "in", "آئے", "آیا", "آئی", "आए", "आया", "এলো", "এল", "এসেছে", "جاء", "دخل", "آمد", "آمدند",
        "آيا", "masuk"
    )

    /** "Habis" is "ran out" for goods and "spent" for money: money only, with a day and a figure asked, and no goods word. */
    private val HABIS = listOf("habis", "terpakai")
    private val STOCK_WORDS = listOf("stok", "barang", "produk", "stock", "maal", "saman", "سامان", "مال", "كالا", "کالا")

    private val GOODS = listOf(
        "cheez", "cheezein", "cheezon", "chiz", "item", "items", "maal", "saman", "samaan", "product", "products", "goods", "چیز", "چیزی",
        "چیزیں", "آئٹم", "مال", "سامان", "پروڈکٹ", "منتج", "سلعة", "بضاعة", "كالا", "کالا", "جنس", "شيءِ", "सामान", "माल", "चीज़", "चीज",
        "आइटम", "জিনিস", "পণ্য", "barang", "produk", "شيء", "شي"
    )

    /** Selling well, in the words a shop says it (not the bare "sells", which a rank needs a "which" and goods with). */
    private val SELLS = listOf(
        "bikta", "bikti", "bikne", "bik raha", "bik rahi", "bik rahe", "bika", "biki", "sell", "sells", "selling", "sold", "chalta", "chalti",
        "chal raha", "chal rahi", "chal rahe", "chalne", "laris", "laku", "بکتا", "بکتی", "بکنے", "بک رہا", "بک رہی", "بک رہے", "چلتا", "چلتی",
        "چل رہا", "چل رہی", "چل رہے", "बिकता", "बिकती", "बिकने", "बिक रहा", "बिक रही", "बिक रहे", "चलता", "चलती", "चल रहा", "चल रही", "चल रहे",
        "চলছে", "চলে", "বিক্রি", "مبيعا", "مبيعاً", "مبيعات", "تباع", "يباع", "تباع", "تباع", "فروش", "فروخت", "فروشد", "می فروشد", "فروش رفت",
        "وڪامي", "وڪامجي", "وڪاڻي", "وڪاڻو", "وڪاڻا", "بيع", "يباع"
    )

    private val BEST = listOf(
        "zyada", "ziada", "sab se", "sabse", "most", "best", "top", "highest", "biggest", "bohat", "bahut", "well", "good", "khoob", "زیادہ", "سب سے",
        "بہت", "बहुत", "ज़्यादा", "ज्यादा", "सबसे", "أكثر", "الأكثر", "اكثر", "الاكثر", "بیشتر", "بیشترین", "خوب", "سڀ کان", "وڌيڪ", "বেশি",
        "সবচেয়ে", "terbanyak", "paling", "banyak"
    )

    private val WHICH = listOf(
        "kaun sa", "kon sa", "kaun si", "kaun se", "kya", "kia", "which", "what", "کون سا", "کون سی", "کون سے", "کیا", "कौन सा", "कौन सी", "कौन से",
        "क्या", "কোন", "কী", "কি", "أي", "اي", "ما", "ماذا", "کدام", "چه", "چی", "ڪهڙو", "ڪهڙي", "ڇا", "apa", "mana", "yang mana"
    )

    private val DEAD = listOf(
        "nahi", "nahin", "kam", "least", "worst", "slow", "dead", "نہیں", "کم", "नहीं", "कम", "না", "كم", "لا", "نمی", "tidak", "tak", "belum"
    )

    // --------------------------------------------------------------------------------------------------- reading

    /**
     * The question the words ask, or null. [window] is the span of days the sentence names. [knows] says a word is somebody's or
     * some product's name in the book: a sentence with one is the book's, never read here.
     */
    fun read(heard: String, window: VoiceQuestion.Window?, knows: (String) -> Boolean = { false }): Hudhud.Ask? {
        val joined = Hudhud.norm(heard)
        if (joined.isEmpty()) return null
        if (joined.split(' ').any { it.length > 2 && knows(it) }) return null
        val money = has(joined, MONEY)
        val negated = negated(joined)
        val who = has(joined, WHO)

        // Who gave money, in the past ("kis ne aaj paise diye", "من دفع أمس"): the day's money in, never the unpaid list. A past day
        // is needed for Indonesian's tenseless "bayar". The shop's own paying out, a question of "when" and a denial are other
        // readings.
        if (who && !negated && !has(joined, SHOP_PAID) && !has(joined, WHEN)) {
            val past = has(joined, PAID) || (money && has(joined, GAVE)) ||
                (has(joined, BAYAR) && window == VoiceQuestion.Window.YESTERDAY)
            // Urdu and Hindi write yesterday and tomorrow alike ("کل", "कल"); a past verb says which.
            val day = if (window == null && has(joined, listOf("کل", "कल"))) VoiceQuestion.Window.YESTERDAY else window
            if (past) return Hudhud.Ask.Collected(day)
        }
        // Who does not pay, as a habit or a fact ("kaun nahi deta", "who never pays", "من لا يدفع"): the ones not paid.
        if ((who || has(joined, listOf("کی"))) && negated && has(joined, GIVES + PAID + GAVE) && !has(joined, SHOP_PAID) && !has(joined, WHEN)) {
            return Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID)
        }

        // Money still to come in, asked as an amount ("paise kitne aane hain", "kitne paise aane hain", review O): what the shop is to
        // get. Only the money's word, a "how much" and the verb of coming that is still ahead ("aane"); "aaye" is money that came.
        // Not "aane wale" or "agle" (what is coming: the cash flow), not a cheque's money, not money that already came ("aaye").
        if (money && has(joined, HOW_MUCH) && has(joined, TO_COME) && !negated && !who && !has(joined, SHOP_PAID) &&
            !has(joined, COMING_SOON) && !has(joined, IN_VERBS)
        ) {
            return Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)
        }

        // A debt word on its own ("baqaya", "udhaar"): the total the shop is owed, never a question about the app.
        val words = joined.split(' ')
        if (words.size == 1 && words[0] in BARE_DEBT.map { Hudhud.norm(it) }) return Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)

        // "Kaun kaun mujhe dega", "چه کسانی به من می‌دهند": who will give the shop money — the ones who owe it, most first.
        if (who && !negated && has(joined, TO_ME) && has(joined, WILL_GIVE) && !has(joined, SHOP_PAID) && !has(joined, WHEN)) {
            return Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST)
        }
        // Money that has not come, stuck or an account left open, asked of a person ("kis ka udhaar nahi aaya", "پول چه کسی گیر کرده",
        // "kisne hisaab saaf nahi kiya", "uang siapa yang belum masuk"): the ones not paid.
        val asksWho = who || has(joined, WHOSE)
        if (asksWho && !has(joined, SHOP_PAID) && !has(joined, WHEN) && !has(joined, GOODS)) {
            val debt = money || has(joined, OWING) || has(joined, listOf("wusooli", "wasooli", "وصولی", "वसूली", "recovery"))
            val notCame = (has(joined, NOT_ARRIVED) || (negated && has(joined, ARRIVED))) && debt
            val stuck = has(joined, STUCK) && debt && !negated
            val open = has(joined, ACCOUNT) && (has(joined, NOT_ARRIVED) || (negated && has(joined, CLEARED)))
            val baqi = has(joined, WHOSE) && has(joined, listOf("wusooli", "wasooli", "وصولی", "वसूली")) && has(joined, listOf("baqi", "باقی", "बाकी", "বাকি"))
            if (notCame || stuck || open || baqi) return Hudhud.Ask.Figure(VoiceQuestion.Kind.NOT_PAID)
        }
        // "Main kitna qarzdar hun": what the shop owes.
        if (has(joined, INDEBTED) && has(joined, HOW_MUCH) && has(joined, SELF)) return Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)

        // How many people work here, how many came to buy: the staff's and the day's tickets' counts.
        if (has(joined, HOW_MUCH) && has(joined, PEOPLE) && has(joined, WORKS) && !negated) return Hudhud.Ask.Count(Hudhud.Thing.STAFF)
        if (has(joined, HOW_MUCH) && has(joined, PEOPLE) && has(joined, CAME_TO_BUY) && !negated) {
            return Hudhud.Ask.Count(Hudhud.Thing.TICKETS, window ?: VoiceQuestion.Window.TODAY)
        }
        if (has(joined, SALES_COUNT) && window != null && !money) return Hudhud.Ask.Count(Hudhud.Thing.TICKETS, window)

        // Where the money went ("paise kahan gaye", "where did the money go", "uang saya ke mana"): the cashbook's money out.
        // Where it is ("mere paise kahan hain", "where is my money"): what is owed to the shop.
        val where = has(joined, WHERE)
        if (where && money && !negated) {
            if (has(joined, WENT) || has(joined, WENT_TO)) return Hudhud.Ask.Cashbook(window)
            if (has(joined, MINE)) return Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)
        }
        // What is in hand ("kitna paisa hai mere paas", "كم معي من النقود"): the drawer.
        if (money && has(joined, HOW_MUCH) && has(joined, WITH_ME) && !has(joined, OWING)) return Hudhud.Ask.Drawer

        // Money that left ("aaj kitne paise nikle", "how much did I spend today", "امروز چقدر پول خارج شد") and money in and out
        // together ("aaj paise kitne aaye kitne gaye"): the cashbook, over the days said.
        val asksAmount = has(joined, HOW_MUCH) || window != null
        val out = has(joined, OUT_VERBS)
        if (asksAmount && out && (money || has(joined, listOf("spend", "spent", "spending", "صرفت", "صرفنا", "أنفقت")))) {
            return Hudhud.Ask.Cashbook(window)
        }
        // "...should be in the drawer" before "I go home" is the drawer, not money in and out (review O).
        if (money && asksAmount && has(joined, IN_VERBS) && has(joined, WENT) && !has(joined, listOf("drawer", "till", "galla", "galle"))) {
            return Hudhud.Ask.Cashbook(window)
        }
        if (has(joined, HABIS) && has(joined, HOW_MUCH) && !has(joined, STOCK_WORDS) && joined.split(' ').size <= 5) {
            return Hudhud.Ask.Cashbook(window)
        }

        // Whether the shop is losing money (15 Nov 2026, review O: "nuqsan to nahi ho raha" was small talk, "dukaan ghaate mein to nahi
        // ja rahi" the shop's profile): the profit, which the card shows with its minus. A loss on goods ("maal ka nuqsan", an item
        // ranked by loss) and somebody's loss are other questions.
        if (has(joined, LOSS) && !has(joined, GOODS) && !has(joined, STOCK_WORDS) && !who && !has(joined, WHOSE)) {
            return if (window == null || window == VoiceQuestion.Window.THIS_MONTH) Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH)
            else Hudhud.Ask.Period(Hudhud.Measure.PROFIT, window)
        }

        // The goods that sell best, said as a shop says it ("kaun sa maal zyada chalta hai", "which item sells best", "barang apa
        // yang lagi laris"): a goods word, a word of selling, a which-word; nothing that denies or asks the slowest.
        if (has(joined, GOODS) && has(joined, SELLS) && has(joined, WHICH) && !has(joined, DEAD) && !negated) {
            return Hudhud.Ask.TopGoods(window)
        }
        // "Sab se zyada bikne wali cheez": the goods word, the selling and the best said, with no figure asked.
        if (has(joined, GOODS) && has(joined, SELLS) && has(joined, BEST) && !has(joined, DEAD) && !negated && !money && !has(joined, HOW_MUCH)) {
            return Hudhud.Ask.TopGoods(window)
        }
        // Without a goods word, "what sells best" with the best said ("kya zyada bik raha hai", "الان چه چیزی خوب می‌فروشد").
        if (has(joined, SELLS) && has(joined, BEST) && has(joined, WHICH) && !has(joined, DEAD) && !negated && !money &&
            !has(joined, SALES_FIGURE)
        ) return Hudhud.Ask.TopGoods(window)
        return null
    }

    /** Money still to come (review O): the verb of coming, ahead. */
    private val TO_COME = listOf("aane", "ane", "aani", "aana", "آنے", "آنی", "آنا", "आने", "आनी", "आना")
    private val COMING_SOON = listOf("wala", "wale", "wali", "agle", "agla", "agli", "next", "cheque", "check", "chek", "والا", "والے", "والی",
        "اگلے", "چیک", "वाला", "वाले", "वाली", "अगले", "चेक")

    /** A loss, the shop's (review O). "Ghata" alone is "reduce" and is left out. */
    private val LOSS = listOf(
        "nuqsan", "nuksan", "nuqsaan", "nuksaan", "ghaata", "ghaate", "ghate mein", "ghatay mein", "loss", "losing money", "in the red",
        "نقصان", "گھاٹا", "گھاٹے", "घाटा", "घाटे", "नुकसान", "লোকসান", "خسارة", "نخسر", "خاسرون", "زیان", "ضرر", "rugi", "merugi",
        "kerugian", "گهاٽو", "گهاٽي"
    )

    /** A figure of sales asked for itself ("kitni sale", "how much did we sell"): not the best-selling goods. */
    private val SALES_FIGURE = listOf(
        "kitni sale", "kitna bika", "kitni bikri", "how much", "how many", "کتنی سیل", "کتنا بکا", "कितनी बिक्री", "কত বিক্রি", "كم بعنا", "چقدر",
        "berapa", "ڪيتري وڪرو"
    )
}
