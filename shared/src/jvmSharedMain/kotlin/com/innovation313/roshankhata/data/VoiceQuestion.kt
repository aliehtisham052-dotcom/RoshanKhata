package com.innovation313.roshankhata.data

import java.text.Normalizer
import java.util.Locale

/**
 * Eight fixed questions the owner can ask by voice, recognised from words in
 * all nine of the app's languages — and in the script the phone's recogniser
 * actually writes, which for spoken Urdu is often Urdu letters and sometimes
 * Hindi ones.
 *
 * Deliberately not a chat model: the app's promise is that the ledger stays on
 * the phone, and a fixed list can be tested. Every answer only READS; and the
 * screen always says what it understood, so a misheard question shows as the
 * wrong question, never as a wrong figure for the right one.
 */
object VoiceQuestion {

    enum class Kind { PROFIT_MONTH, EXPIRING, NOT_PAID, WHO_OWES_MOST, WHO_OWES_LEAST, SALES_TODAY, SALES_MONTH, TO_GIVE, TO_GET }

    /**
     * The span of days a figure is asked for (12 Nov 2026, Hudhud): "kal ki sale", "pichle mahine ka munafa". The eight
     * questions keep their own day and month; a window said with them asks for that span instead ([windowOf]).
     *
     * The last four (15 Nov 2026) are the long spans: the six months up to today, the calendar year so far, the year
     * before it and the whole book. Only a sum can be read over a span that long — the rows of a year would be a
     * query the phone answers 2 MB at a time (lesson 70) — so a question that lists rows is offered the short spans
     * instead ([Hudhud.keepWithinReach]).
     */
    enum class Window {
        TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_6_MONTHS, THIS_YEAR, LAST_YEAR, ALL_TIME;

        /** A span of months or more. */
        val isLong: Boolean get() = this == LAST_6_MONTHS || this == THIS_YEAR || this == LAST_YEAR || this == ALL_TIME
    }

    private val PROFIT = listOf("munafa", "munafah", "profit", "laba", "untung", "منافع", "نفعو", "ربح", "أرباح", "ارباح",
        "سود", "मुनाफ़ा", "मुनाफा", "लाभ", "লাভ", "মুনাফা",
        // 12 Nov 2026 (Hudhud): "is mahine kitna faida hua", as most owners say it.
        "faida", "fayda", "faaida", "فائدہ", "फायदा")
    private val EXPIRING = listOf("expire", "expiry", "mudat", "kedaluwarsa", "kadaluarsa", "ایکسپائر", "میعاد", "مدت",
        "انقضا", "انتهاء", "صلاحيت", "एक्सपायर", "मियाद", "মেয়াদ", "এক্সপায়ার",
        // 12 Nov 2026 (Hudhud): the app's own chip words, so a chip typed as it reads is understood.
        "expiring", "expired", "ختم ٿيندڙ")
    private val NOT_PAID = listOf("nahi diya", "nahin diya", "paisa nahi", "not paid", "hasn't paid", "havent paid", "belum bayar",
        // 13 Nov 2026 (Hudhud Brain v2): "kaun nahi de raha", the present and the plural.
        "nahi diye", "nahin diye", "nahi de raha", "nahi de rahe", "nahi de rahi", "nahi deta", "nahi dete",
        "belum membayar", "نہیں دیا", "نہیں دیے", "نہیں دئیے", "ادا نہیں", "نه ڏنو", "نه ڏنا", "لم يدفع", "لم يسدد", "پرداخت نکرده",
        "नहीं दिया", "नहीं दिए", "দেয়নি", "দেননি", "পরিশোধ করেনি")
    private val MOST = listOf("sab se zyada", "sabse zyada", "sab se ziada", "most", "highest", "biggest", "terbanyak", "paling",
        "terbesar",
        "سب سے زیادہ", "سڀ کان وڌيڪ", "الأكثر", "أكثر", "اكثر", "بیشترین", "सबसे ज़्यादा", "सबसे ज्यादा",
        "সবচেয়ে বেশি")
    // The other end of "most" (14 Nov 2026, the owner's check log: "jisne mere sabse kam paise dene hain" was answered with
    // the shop's total to give). Two spellings of Sindhi's "less" (ھ and ه) and Persian's with and without the half-space.
    private val LEAST = listOf("sab se kam", "sabse kam", "least", "lowest", "smallest", "fewest", "terkecil", "terendah",
        "paling sedikit", "paling kecil", "minimum", "sab se chhota", "sabse chhota", "sab se chota", "sabse chota",
        "سب سے چھوٹا", "सबसे छोटा", "सबसे छोटी", "সবচেয়ে ছোট", "সবচেয়ে কম", "سڀ کان ننڍو", "الأصغر", "أصغر", "أدنى", "الاقل", "کوچک‌ترین", "کوچکترین",
        "کمتر",
        "سب سے کم", "سڀ کان گھٽ", "سڀ کان گهٽ", "الأقل", "أقل", "اقل", "کمترین", "کم‌ترین", "सबसे कम", "সবচেয়ে কম")
    /**
     * What a debt is called when the question asks for the smallest (15 Nov 2026): the balance, the debtor, Indonesian's "-nya".
     * Only beside a [LEAST] word, never alone: "balance" by itself is no debt question.
     */
    private val LEAST_DEBT = listOf("balance", "balances", "debtor", "debtors", "bakaya", "baqaya", "hisaab", "hisab", "khata", "khate", "utangnya",
        "hutangnya", "piutangnya", "بدهکار", "بدهی", "حساب", "کھاتہ", "کھاتے", "खाता", "हिसाब", "হিসাব", "رصيد", "رصيده")
    private val DEBT = listOf("baqaya", "bakaya", "udhar", "udhaar", "qarz", "owe", "owes", "due", "utang", "piutang",
        "بقایا", "ادھار", "قرض", "اڌار", "دين", "ديون", "طلب", "बकाया", "उधार", "क़र्ज़", "कर्ज", "বাকি", "ধার", "পাওনা",
        // 12 Nov 2026 (Hudhud): "Ali ka kitna baqi hai"; "dues", once the words count only whole.
        "baqi", "baaqi", "baki", "baaki", "باقی", "बाकी", "dues")
    private val SALES = listOf("bikri", "bechi", "becha", "bechay", "farokht", "sale", "sales", "sold", "penjualan", "jualan",
        "terjual", "بکری", "فروخت", "بیچا", "بیچی", "وڪرو", "وڪري", "مبيعات", "بيع", "فروش", "बिक्री", "बेचा", "बेची",
        "বিক্রি", "বিক্রয়",
        // 12 Nov 2026 (Hudhud): "aaj kitna bika", "how much did I sell".
        "sell", "selling", "bika", "biki", "bikay", "بکا", "بکی", "बिका", "बिकी",
        // 13 Nov 2026 (Hudhud's chips): Sindhi's "got sold", Arabic's "what was sold".
        "وڪاميو", "وڪاميا", "مباع")
    private val TODAY = listOf("aaj", "today", "hari ini", "آج", "اڄ", "اليوم", "امروز", "आज", "আজ")

    // The windows' words (12 Nov 2026). Urdu writes "kal" (yesterday) and "kul" (total) alike, so in its own script
    // yesterday is only "کل کی/کا/کے" or "گزشتہ روز"; Hindi, Sindhi and the rest have a word of their own. Indonesian
    // "kemarin" is yesterday alone and "last" beside "bulan" or "minggu".
    private val YESTERDAY = listOf("kal", "yesterday", "guzishta roz", "pichle din", "کل کی", "کل کا", "کل کے", "گزشتہ روز",
        "گذشتہ روز", "कल", "গতকাল", "ڪالهه", "أمس", "امس", "البارحة", "دیروز", "kemarin")
    private val WEEK = listOf("hafta", "hafte", "haftay", "week", "ہفتہ", "ہفتے", "हफ्ता", "हफ्ते", "सप्ताह", "هفتو", "هفتي",
        "أسبوع", "اسبوع", "هفته", "সপ্তাহ", "minggu", "pekan")
    private val MONTH = listOf("mahina", "mahine", "mahinay", "maheena", "maheene", "maheenay", "mahiney", "maheeney", "month", "مہینہ", "مہینے", "ماہ",
        "महीना", "महीने", "माह", "مهينو", "مهيني", "مهينا", "شهر", "ماه", "মাস", "bulan")
    private val LAST = listOf("pichle", "pichhle", "pichlay", "pichhlay", "pichla", "pichhla", "guzishta", "guzashta",
        "last", "previous", "پچھلے", "پچھلا", "پچھلی", "گزشتہ", "گذشتہ", "पिछले", "पिछला", "पिछली", "गत", "গত", "আগের",
        "پوئين", "گذريل", "الماضي", "الماضية", "السابق", "الفائت", "گذشته", "قبل", "پیش", "lalu", "kemarin", "sebelumnya",
        "terakhir")

    // The long spans' words (15 Nov 2026). "Saal" is a year in Urdu, Hindi, Sindhi and Persian alike. Arabic's "عام" is also
    // "general" (lesson 92: one spelling, two words in two languages), so it is only ever said inside a phrase. A year is
    // the calendar year, January to December, as the shop's books are kept.
    private val YEAR = listOf("saal", "baras", "year", "tahun", "سال", "سنہ", "سنة", "سنوات", "سنين", "साल", "वर्ष", "বছর")
    private val THIS_YEAR = listOf("امسال", "هذا العام", "العام الحالي")
    private val LAST_YEAR = listOf("parsaal", "paarsaal", "پارسال", "العام الماضي", "العام الفائت", "العام السابق")
    // Half a year is the six months up to today, said another way.
    private val HALF_YEAR = listOf("half year", "aadha saal", "aadhe saal", "adha saal", "nisf saal", "نصف سال", "آدھا سال",
        "آدھے سال", "आधा साल", "आधे साल", "نصف سنة", "نصف عام", "نیم سال", "نیمسال", "ششماهه", "setengah tahun",
        "অর্ধ বছর", "অর্ধেক বছর", "اڌ سال")
    // Parts of the day (15 Nov 2026, the everyday review): "subah se ab tak kitna bika", "shaam tak ki sale", "how much did we sell this
    // morning". The book keeps the day, not the hour, so each is TODAY's figure — told as exact only when it runs up to now ("since
    // morning"), otherwise as a guess ([partOfDay]). "Sham" is left out: it is a man's name too. "Raat" beside "kal" stays yesterday's.
    private val PARTS = listOf("subah", "subha", "savere", "sawere", "shaam", "dopahar", "dupahar", "dophar", "raat", "morning", "afternoon",
        "evening", "noon", "tonight", "صبح", "شام", "دوپہر", "رات", "सुबह", "सवेरे", "शाम", "दोपहर", "रात", "সকাল", "সকালে", "বিকাল", "বিকেল",
        "সন্ধ্যা", "দুপুর", "রাত", "الصباح", "صباح", "صباحا", "المساء", "مساء", "الظهر", "العصر", "الليلة", "منجهند", "pagi", "siang", "sore", "malam")
    /** "Since", "from", "up to now": a part of the day that runs to the present. */
    private val SINCE = listOf("se", "since", "from", "dari", "sejak", "منذ", "از", "سے", "से", "থেকে", "کان", "ab tak", "so far", "till now",
        "اب تک", "अब तक", "এখন পর্যন্ত", "حتى الآن", "تا حالا", "تا الان", "sampai sekarang", "تائين", "هينئر")

    /**
     * A part of the day named in [heard], or null (15 Nov 2026). [Part.toNow] says it runs up to the present ("subah se ab tak",
     * "since morning"): that is today's figure exactly. Otherwise ("this morning", "shaam tak") today's figure is a superset: the
     * book has no hour, and Hudhud tells it as a guess.
     */
    class Part(val toNow: Boolean)

    private val UP_TO_NOW = listOf("ab tak", "so far", "till now", "until now", "اب تک", "अब तक", "এখন পর্যন্ত", "حتى الآن", "تا حالا", "تا الان",
        "sampai sekarang", "هينئر تائين")
    /** "Until" a time of day (not the present): "shaam tak", "till noon". */
    private val UNTIL = listOf("tak", "تک", "तक", "until", "till", "حتى", "تا", "sampai", "تائين")

    /** A greeting names a part of the day without asking about it ("good morning", "صباح الخير", "selamat pagi"). */
    private val DAY_GREETINGS = listOf("good morning", "good afternoon", "good evening", "good night", "subah bakhair", "suprabhat",
        "shubh prabhat", "shubh sandhya", "صباح الخير", "مساء الخير", "صبح بخیر", "عصر بخیر", "شب بخیر", "صبح بخير", "شام بخیر", "सुप्रभात", "शुभ संध्या",
        "শুভ সকাল", "শুভ সন্ধ্যা", "selamat pagi", "selamat siang", "selamat sore", "selamat malam", "صبح بخير", "ڀلي صبح")

    private fun withoutGreeting(t: String): String = DAY_GREETINGS.fold(" $t ") { acc, g -> acc.replace(" ${normalise(g)} ", " ") }.trim()

    fun partOfDay(heard: String): Part? {
        val t = withoutGreeting(normalise(heard))
        if (!saysAny(t, PARTS)) return null
        if (saysAny(t, YESTERDAY) || saysAny(t, WEEK) || saysAny(t, MONTH) || saysAny(t, YEAR)) return null
        // "Since the morning" (a since-word) or "until now" runs to the present; "until the evening" does not.
        val toNow = saysAny(t, UP_TO_NOW) || (saysAny(t, SINCE) && !saysAny(t, UNTIL))
        return Part(toNow)
    }

    // An event or a festival as a span (15 Nov 2026): the book keeps days, not the Ramzan, so it is none of the spans he tells. Said
    // only with the word that frames a span ("Ramzan mein", "Eid se pehle", "since Eid"): "Ramzan" is a man's name too.
    private val EVENTS = listOf("ramzan", "ramadan", "ramazan", "رمضان", "रमज़ान", "रमजान", "রমজান", "eid", "عید", "عيد", "ईद", "ঈদ", "muharram",
        "محرم", "diwali", "دیوالی", "दिवाली", "holi", "christmas", "کرسمس", "baisakhi", "شب برات", "shab e barat", "ramadhan", "lebaran", "idul fitri")
    private val EVENT_FRAME = listOf("mein", "me", "ke dauran", "ke baad", "se pehle", "se pahle", "se baad", "ke din", "during", "before", "after",
        "since", "میں", "کے دوران", "کے بعد", "سے پہلے", "सेپहले", "में", "के दौरान", "के बाद", "से पहले", "আগে", "পরে", "خلال", "قبل", "بعد", "في",
        "قبل از", "بعد از", "در", "sebelum", "setelah", "sesudah", "saat", "selama", "ڪري", "کان اڳ", "کان پوءِ")
    private val WEEKDAYS = listOf("jumma", "jumme", "jume", "jummah", "juma", "friday", "somwar", "itwar", "monday", "sunday", "جمعہ", "جمعے", "इतवार",
        "جمعه", "الجمعة")

    private fun isEventSpan(t: String): Boolean {
        if (saysAny(t, EVENTS) && saysAny(t, EVENT_FRAME)) return true
        // "Pichle jumme se ab tak": since a weekday, which is none of the spans.
        return saysAny(t, WEEKDAYS) && saysAny(t, LAST) && saysAny(t, SINCE)
    }

    // The whole book. The first group means it wherever it stands: "aaj tak" is "until today", and the TODAY word inside it
    // does not make the question today's. The second group means it only when no other span is said, because "aaj ab tak"
    // is today so far and "is saal ab tak" this year so far. "So far" and "to date" are left out on purpose: said alone
    // they are as often today as the whole book.
    private val EVER = listOf("aaj tak", "ajj tak", "shuru se", "shuruat se", "pehle din se", "start se", "since the beginning",
        "since the start", "from the beginning", "from the start", "since day one", "all time", "of all time", "ever since",
        "آج تک", "شروع سے", "شروعات سے", "پہلے دن سے", "ابتدا سے", "आज तक", "शुरू से", "शुरुआत से", "पहले दिन से",
        "আজ পর্যন্ত", "শুরু থেকে", "প্রথম দিন থেকে", "حتى اليوم", "منذ البداية", "منذ البدء", "من البداية", "منذ اليوم الأول",
        "تا امروز", "از ابتدا", "از آغاز", "از شروع", "از روز اول", "از اول تا حالا", "اڄ تائين", "شروع کان", "شروعات کان", "شروعات سے", "پهرين ڏينهن کان",
        "sampai hari ini", "hingga hari ini", "sejak awal", "dari awal", "sejak hari pertama")
    private val EVER_SO_FAR = listOf("ab tak", "abtak", "abhi tak", "till now", "until now", "up to now", "so far", "to date",
        "till date", "up till now", "اب تک", "ابھی تک",
        "अब तक", "अभी तक", "এখন পর্যন্ত", "حتى الآن", "تا الان", "تا کنون", "تا حالا", "هينئر تائين", "sampai sekarang",
        "hingga sekarang", "selama ini")

    // A count of days, weeks, months or years ("6 mahine", "pichle 3 din", "last 2 weeks") is a span of its own that no window
    // is, except six months. These are the words that name a unit when a count stands before them, with the plurals and the
    // oblique forms the windows' own lists leave out. Said alone ("is mahine") they count nothing, and they are not in [ALL],
    // so no customer loses a word of his name to them.
    private val UNIT_DAY = listOf("din", "dino", "dinon", "days", "day", "hari", "دن", "دنوں", "روز", "दिन", "दिनों", "দিন",
        "يوم", "ايام", "أيام", "يوما", "ڏينهن")
    private val UNIT_WEEK = WEEK + listOf("hafton", "ہفتوں", "हफ्तों", "أسابيع", "اسابيع", "هفتن")
    private val UNIT_MONTH = MONTH + listOf("mahinon", "mahino", "مہینوں", "महीनों", "أشهر", "اشهر", "شهور", "مهينن")
    private val UNIT_YEAR = YEAR + listOf("salon", "saalon", "سالوں", "برسوں", "सालों", "वर्षों", "سالن")
    /** Arabic's dual says the count in the unit itself: "two months", "two weeks", "two days", "two years". */
    private val DUALS: Map<String, SpanUnit> by lazy {
        mapOf(
            "شهرين" to SpanUnit.MONTH, "شهران" to SpanUnit.MONTH, "أسبوعين" to SpanUnit.WEEK, "أسبوعان" to SpanUnit.WEEK,
            "يومين" to SpanUnit.DAY, "يومان" to SpanUnit.DAY, "سنتين" to SpanUnit.YEAR, "سنتان" to SpanUnit.YEAR,
            "عامين" to SpanUnit.YEAR, "عامان" to SpanUnit.YEAR
        ).mapKeys { normalise(it.key) }
    }
    // The count words beyond the ones voice entry already reads ([VoiceEntry.countWord]): a figure written as a word is a count
    // here whatever it is, and six is six in every language below. Written out for 1 to 12 and 30 only: a count said any other
    // way as a word is left to its digits, and a shop that says "26 months" in words is not read as six.
    private val COUNT_WORDS: Map<String, Double> by lazy {
        val table = listOf(
            1.0 to listOf("hek", "هڪ", "এক", "एक", "satu"),
            2.0 to listOf("দুই", "দু", "ٻه", "dua"),
            3.0 to listOf("ٽي", "ثلاثة", "ثلاث", "तीन", "তিন", "tiga"),
            4.0 to listOf("أربعة", "أربع", "empat", "चार", "চার"),
            5.0 to listOf("पांच", "পাঁচ", "خمسة", "خمس", "lima"),
            6.0 to listOf("chhe", "chhay", "chhai", "chhey", "chhah", "cheh", "six", "شش", "ستة", "ست", "ڇهه", "ڇه", "छह", "छः",
                "छे", "ছয়", "enam"),
            7.0 to listOf("सात", "সাত", "سبعة", "سبع", "هفت", "tujuh"),
            8.0 to listOf("आठ", "আট", "ثمانية", "ثمان", "هشت", "اٺ", "delapan"),
            9.0 to listOf("नौ", "নয়", "تسعة", "تسع", "sembilan"),
            10.0 to listOf("दस", "দশ", "عشرة", "عشر", "ڏهه", "sepuluh"),
            11.0 to listOf("gyarah", "گیارہ", "ग्यारह", "এগারো", "یازده", "sebelas"),
            12.0 to listOf("barah", "بارہ", "बारह", "বারো", "دوازده"),
            30.0 to listOf("tees", "تیس", "तीस", "ত্রিশ", "ثلاثون", "ثلاثين")
        )
        val out = HashMap<String, Double>()
        for ((n, words) in table) for (w in words) out[normalise(w)] = n
        out
    }
    // Which way a count is said. "6 mahine pehle" is six months AGO and "pehle 6 mahine" the FIRST six: neither is the six
    // months up to today.
    private val AGO_AFTER: Set<String> by lazy {
        listOf("pehle", "pehlay", "pahle", "ago", "lalu", "پہلے", "पहले", "আগে", "پیش", "اڳ").map { normalise(it) }.toSet()
    }
    private val AGO_BEFORE: Set<String> by lazy { listOf("قبل", "منذ").map { normalise(it) }.toSet() }
    private val FIRST_BEFORE: Set<String> by lazy {
        listOf("pehle", "pehlay", "pahle", "first", "پہلے", "पहले", "প্রথম", "اول", "الاول").map { normalise(it) }.toSet()
    }
    private val FIRST_AFTER: Set<String> by lazy { listOf("pertama", "اول", "الاولی").map { normalise(it) }.toSet() }
    // A span ahead is no span of the book's. "Agle 30 din" (the cash flow) is read before a figure is asked for.
    private val NEXT = listOf("agle", "aglay", "agli", "next", "اگلے", "اگلی", "آئندہ", "अगले", "अगली", "আগামী", "القادمة",
        "القادم", "آینده", "depan", "mendatang")
    // The financial year starts on 1 July in Pakistan and on 1 April in India: a year of ours is January to December, so a
    // question about the other kind is said not to be one we have (never answered with the calendar year's figure).
    private val FISCAL = listOf("fiscal", "financial year", "maali saal", "mali saal", "مالی سال", "مالي سال", "سال مالی",
        "वित्तीय वर्ष", "वित्त वर्ष", "السنة المالية", "العام المالي", "tahun fiskal", "tahun anggaran", "অর্থবছর", "অর্থ বছর")
    // The day before yesterday ("parson" is also the day after tomorrow): a day no window is.
    private val DAY_BEFORE = listOf("parson", "parsoon", "parso", "پرسوں", "परसों", "day before yesterday", "پریروز",
        "اول امس", "পরশু")
    private val TO_GIVE = listOf("dena", "dene", "to give", "i owe", "to pay", "utang saya", "دینا", "دینے", "ڏيڻا", "ڏيڻو",
        "علي دفع", "پرداختنی", "بدهکار", "देना", "देने", "দেনা", "দিতে",
        // 12 Nov 2026 (Hudhud), from the app's own chips: Farsi's debt is what the shop owes ("جمع بدهی"), Arabic's
        // "what is on me", Indonesian's "what must be paid".
        "بدهی", "ما عليّ", "harus dibayar")
    private val TO_GET = listOf("lena", "lene", "to get", "receive", "collect", "piutang", "tagih", "لینا", "لینے", "وٺڻا",
        "وٺڻو", "لي عند", "طلبکار", "دریافت", "लेना", "लेने", "পাওনা", "পেতে",
        // 12 Nov 2026 (Hudhud), from the app's own chips: Arabic "what is mine", Indonesian "what must be received".
        "ما لي", "harus diterima")

    // Made once, before anything below first spells a word.
    // Arabic-script vowel marks, madda and hamza above or below (taken off their letter by [normalise]'s NFD), the
    // tatweel that stretches a word, and the accents Latin letters may carry.
    private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u0300-\\u036F]")
    private val PUNCT = Regex("[\\p{Punct}،؟۔!?]")
    private val SPACES = Regex("\\s+")

    /** Each list word as it is spelt for matching, worked out once rather than at every question. */
    private val spelt = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Lower case, one spelling for letters Urdu, Persian and Arabic write differently. Arabic-script vowel marks go
     * (a recogniser or keyboard may write them, a list never does), the joiners Persian writes inside words are a
     * space, and Bengali's ya, rra and rha are one way however a keyboard writes them (12 Nov 2026, Hudhud: his own
     * spelling until then, now the one both use).
     *
     * Every way a letter can be written is one letter (13 Nov 2026, the owner's screenshot: the phone heard "آپ کا
     * نام" and wrote "اپ کا نام", without the madda, and Hudhud knew only "آپ"). The text is first taken apart into
     * letters and their marks (NFD), and the marks go: آ أ إ are ا, ؤ is و, ئ is ی, ۂ ۀ are ہ, ۓ is ے, क़ is क, é is e —
     * the same for the lists and for whatever a recogniser or a keyboard writes, so no word has to be listed twice.
     */
    internal fun normalise(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(MARKS, "").replace('\u200C', ' ').replace('\u200D', ' ')
        .replace("\u09DF", "\u09AF\u09BC").replace("\u09DC", "\u09A1\u09BC").replace("\u09DD", "\u09A2\u09BC")
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ه', 'ہ').replace('ة', 'ہ').replace('\u06D5', 'ہ')
        .replace("\u093C", "") // Devanagari nukta: ज़ and ज are one word to a listener
        .replace('\u0901', '\u0902') // Devanagari chandrabindu and anusvara: हूँ and हूं are one word
        .replace(PUNCT, " ")
        .replace(SPACES, " ").trim()

    /**
     * True when one of [words] is said (12 Nov 2026, Hudhud): a single word as a whole word, or with the small pieces
     * its language joins to a word — Arabic's article and "and/with/for" in front ("الربح", "والمبيعات"), Bengali's
     * endings behind ("আজকের", "বিক্রির"), an English plural — and a phrase as a run of whole words. The letters were
     * looked for anywhere before: "Saleem ka baqaya" asked for the sales, "Ali Wholesale" too, "Kalabagh" the profit.
     */
    internal fun saysAny(text: String, words: List<String>): Boolean {
        val said = text.split(' ')
        return words.any { w ->
            val k = spelt.getOrPut(w) { normalise(w) }
            if (k.contains(' ')) " $text ".contains(" $k ") else said.any { s -> isWord(s, k) }
        }
    }

    /** [s] is the word [k], or [k] with its language's small joined pieces. */
    private fun isWord(s: String, k: String): Boolean {
        if (s == k) return true
        if (s.length <= k.length) return false
        val first = k[0]
        return when {
            first in '\u0600'..'\u06FF' -> ARABIC_FRONT.any { s == it + k }
            first in '\u0980'..'\u09FF' -> BENGALI_ENDS.any { s == k + it }
            // Four letters or more: "hi" is no word for "his".
            first in 'a'..'z' -> k.length >= 4 && (s == k + "s" || s == k + "es")
            else -> false
        }
    }

    /** "the", "and the", "with the", "for the", "and", "with", "for", "so": written joined to the word after them. */
    private val ARABIC_FRONT = listOf("ال", "وال", "بال", "فال", "لل", "و", "ب", "ل", "ف")

    /** Bengali's case endings and the "the" endings, written joined to a word: আজ-কের, বিক্রি-র, লাভ-ের. */
    private val BENGALI_ENDS = listOf("ের", "র", "এ", "ে", "কে", "কের", "টা", "টি", "ও").map { normalise(it) }

    /**
     * The words of [text] (normalised) that are the eight questions' own (12 Nov 2026): a list word said whole or with
     * its joined pieces, or a word of a list phrase said whole. Hudhud looks for a customer's name only among the
     * rest, so "baqaya" or "aaj" never passes for a name — while علی, a word of the Arabic phrase "ما عليّ", is still
     * Ali whenever that phrase is not said.
     */
    internal fun keywordsIn(text: String): Set<String> = wordsOf(text, ALL)

    /**
     * The words of [text] (normalised) that are the span's own (15 Nov 2026): today, yesterday, the week, the month, the
     * year, "last", this year, last year, half a year, "since the beginning", "so far", the units a count stands before
     * ("din", "mahinon"), "ago", "first", a span ahead, a fiscal year — a word said whole, or a word of a phrase said
     * whole. Such a word may also mean something else by itself (the "since" of "since the beginning" asks since when,
     * the "اب" of "اب تک" is Arabic's "father"): in a sentence that only carries a question on over another span these
     * words are the span's, not a question of their own ([Hudhud]'s follow-ups and its bare spans).
     */
    internal fun spanWordsIn(text: String): Set<String> = wordsOf(text, SPAN_WORDS)

    private fun wordsOf(text: String, lists: List<String>): Set<String> {
        val said = text.split(' ').filter { it.isNotEmpty() }
        val out = HashSet<String>()
        for (w in lists) {
            val k = spelt.getOrPut(w) { normalise(w) }
            if (k.contains(' ')) {
                if (" $text ".contains(" $k ")) out += k.split(' ')
            } else {
                said.filterTo(out) { s -> isWord(s, k) }
            }
        }
        return out
    }

    private val ALL: List<String> by lazy {
        listOf(PROFIT, EXPIRING, NOT_PAID, MOST, LEAST, DEBT, SALES, TODAY, TO_GIVE, TO_GET, YESTERDAY, WEEK, MONTH, LAST,
            YEAR, THIS_YEAR, LAST_YEAR, HALF_YEAR, EVER, EVER_SO_FAR).flatten()
    }

    private val SPAN_WORDS: List<String> by lazy {
        listOf(TODAY, YESTERDAY, WEEK, MONTH, LAST, YEAR, THIS_YEAR, LAST_YEAR, HALF_YEAR, EVER, EVER_SO_FAR, UNIT_DAY, UNIT_WEEK,
            UNIT_MONTH, UNIT_YEAR, NEXT, FISCAL, DAY_BEFORE, AGO_AFTER.toList(), AGO_BEFORE.toList(), FIRST_BEFORE.toList(),
            FIRST_AFTER.toList()).flatten()
    }

    /**
     * The span of days [heard] names, or null when it names none (12 Nov 2026): last month and last week (a "last"
     * word beside the month's or the week's), this week, today, yesterday, this month. A sentence naming none keeps
     * the figure's own span, and the card's heading says which span its figure is for.
     *
     * 15 Nov 2026, four long spans: the six months up to today, this year, last year and the whole book — and a sentence
     * that names a span none of these is ("last 3 days", "pichle 2 hafte", "6 months ago", "parson", a fiscal year) is
     * [unsupportedSpan], never quietly read as another span (it used to be this month's figure, or last month's).
     */
    fun windowOf(heard: String): Window? = spans(heard).window

    /**
     * True when [heard] names a span no [Window] is (15 Nov 2026): a count of days, weeks, months or years other than the
     * six months up to today, "ago", "the first 6 months", a span ahead, the day before yesterday, a fiscal year. The
     * figure asked for is then not answered for some other span: Hudhud says which spans he tells ([Hudhud.limitSpan]).
     * A sentence that says the whole book ("aaj tak", "since the beginning") is never one of these.
     */
    fun unsupportedSpan(heard: String): Boolean = spans(heard).unsupported

    /**
     * The counts that belong to a span in [heard] ("3" of "pichle 3 mahine", "6" of "last 6 months") — figures that are not
     * an amount of money. A sentence's number that is only one of these is no amount: "Bashir ne pichle 3 mahine kitna diya"
     * asks what Bashir paid, it does not say that 3 rupees moved.
     */
    fun spanFigures(heard: String): Set<Double> =
        countedSpans(heard).map { it.n }.filter { !it.isNaN() }.toSet()

    /**
     * True when the "last" of [heard] is the span's own: "last month", "last week", "last year", "last 6 months", "pichle 3
     * mahine" — and not a last payment or a last entry ("when did he last pay"). The two read alike in English.
     */
    fun lastIsSpan(heard: String): Boolean {
        val t = normalise(heard)
        if (!saysAny(t, LAST)) return false
        return windowOf(heard) in LAST_WINDOWS || countedSpans(heard).isNotEmpty()
    }

    /** The spans whose "last" is the span's own ("last week", "pichle saal", "the last 6 months"). */
    internal val LAST_WINDOWS = setOf(Window.LAST_MONTH, Window.LAST_WEEK, Window.LAST_YEAR, Window.LAST_6_MONTHS)

    private class Spans(val window: Window?, val unsupported: Boolean)

    private val NO_SPAN = Spans(null, false)
    private val NOT_OURS = Spans(null, true)

    private fun spans(heard: String): Spans {
        val t = normalise(heard)
        if (t.isEmpty()) return NO_SPAN
        // The whole book first: "aaj tak" holds the word "aaj" and is not today.
        if (saysAny(t, EVER)) return Spans(Window.ALL_TIME, false)
        if (isEventSpan(t)) return NOT_OURS
        val counted = countedSpans(heard)
        val half = saysAny(t, HALF_YEAR)
        if (counted.isNotEmpty() || half) {
            // Six months, said alone, is the six months up to today. Any other count, "ago", "the first 6 months", a span ahead,
            // a fiscal year, or six months with another span beside it ("is saal ke pichle 6 mahine") is none we tell.
            val six = if (counted.isEmpty()) half else counted.size == 1 && counted[0].sixMonths
            val alone = !saysAny(t, THIS_YEAR) && !saysAny(t, LAST_YEAR) && !saysAny(t, WEEK) && !saysAny(t, TODAY) &&
                !saysAny(t, YESTERDAY) && !saysAny(t, NEXT) && !saysAny(t, FISCAL) && (half || !saysAny(t, YEAR))
            return if (six && alone) Spans(Window.LAST_6_MONTHS, false) else NOT_OURS
        }
        // A span ahead ("agle mahine ki bikri", "next week") is no span of the book at all.
        val ahead = saysAny(t, NEXT) && (saysAny(t, MONTH) || saysAny(t, WEEK) || saysAny(t, YEAR))
        if (saysAny(t, FISCAL) || saysAny(t, DAY_BEFORE) || ahead) return NOT_OURS
        val last = saysAny(t, LAST)
        val year = saysAny(t, YEAR)
        val window = when {
            saysAny(t, LAST_YEAR) || (last && year) -> Window.LAST_YEAR
            year || saysAny(t, THIS_YEAR) -> Window.THIS_YEAR
            last && saysAny(t, MONTH) -> Window.LAST_MONTH
            last && saysAny(t, WEEK) -> Window.LAST_WEEK
            saysAny(t, WEEK) -> Window.THIS_WEEK
            saysAny(t, TODAY) -> Window.TODAY
            saysAny(t, YESTERDAY) -> Window.YESTERDAY
            saysAny(t, MONTH) -> Window.THIS_MONTH
            saysAny(withoutGreeting(t), PARTS) -> Window.TODAY
            // "Ab tak" is only the whole book when no other span is said: "aaj ab tak" is today so far.
            saysAny(t, EVER_SO_FAR) -> Window.ALL_TIME
            else -> null
        }
        return if (window == null) NO_SPAN else Spans(window, false)
    }

    private enum class SpanUnit { DAY, WEEK, MONTH, YEAR }

    /** [n] of a [unit]: "pichle 6 mahine" is 6 of MONTH. NaN is a figure written in several words ("twenty six"). */
    private class Counted(val n: Double, val unit: SpanUnit, val ago: Boolean, val first: Boolean) {
        val sixMonths: Boolean get() = n == 6.0 && unit == SpanUnit.MONTH && !ago && !first
    }

    private val DIGIT_THEN_LETTER = Regex("(\\d)(\\p{L})")

    private fun unitOf(token: String): SpanUnit? = when {
        saysAny(token, UNIT_DAY) -> SpanUnit.DAY
        saysAny(token, UNIT_WEEK) -> SpanUnit.WEEK
        saysAny(token, UNIT_MONTH) -> SpanUnit.MONTH
        saysAny(token, UNIT_YEAR) -> SpanUnit.YEAR
        else -> null
    }

    /**
     * Every count of days, weeks, months or years the sentence says: a figure (in any script's digits, or one of the number
     * words of the nine languages) straight before the unit's word, or Arabic's dual in the unit itself. The figure is
     * taken as it is written: a count made of several words is NaN, so "twenty six months" is never six.
     */
    private fun countedSpans(heard: String): List<Counted> {
        val spaced = DIGIT_THEN_LETTER.replace(Digits.toLatin(heard), "\$1 \$2")
        val raw = PUNCT.replace(spaced, " ").split(SPACES).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return emptyList()
        val said = raw.map { normalise(it) }
        fun numberAt(j: Int): Double? {
            if (j < 0) return null
            val s = said[j]
            if (s.isNotEmpty() && s.all { it in '0'..'9' }) return s.toDoubleOrNull()
            return COUNT_WORDS[s] ?: VoiceEntry.countWord(raw[j])
        }
        val out = ArrayList<Counted>(1)
        for (i in said.indices) {
            val dual = DUALS[said[i]]
            val unit = dual ?: unitOf(said[i]) ?: continue
            val n = if (dual != null) 2.0 else (numberAt(i - 1) ?: continue)
            // Where the figure stands: the word before the unit, or the unit itself when it is Arabic's dual.
            val figureAt = if (dual != null) i else i - 1
            val compound = dual == null && numberAt(i - 2) != null
            val before = said.getOrNull(figureAt - 1)
            val after = said.getOrNull(i + 1)
            val ago = (after != null && after in AGO_AFTER) || (before != null && before in AGO_BEFORE)
            val first = (before != null && before in FIRST_BEFORE) || (after != null && after in FIRST_AFTER)
            out.add(Counted(if (compound) Double.NaN else n, unit, ago, first))
        }
        return out
    }

    fun classify(heard: String): Kind? {
        val t = normalise(heard)
        if (t.isEmpty()) return null
        return when {
            saysAny(t, PROFIT) -> Kind.PROFIT_MONTH
            saysAny(t, EXPIRING) -> Kind.EXPIRING
            saysAny(t, NOT_PAID) -> Kind.NOT_PAID
            // The smallest first (15 Nov 2026): Indonesian's "paling sedikit" holds the word "paling" of "most", and the balance, the debtor
            // and "-nya" are the debt's own words beside a least.
            saysAny(t, LEAST) && (owedToUs(t) || saysAny(t, LEAST_DEBT)) && !owedByTheShop(t) -> Kind.WHO_OWES_LEAST
            // "Sab se zyada kis ko dena hai" is whom the shop owes most: the shop's payables, not who owes it most.
            saysAny(t, MOST) && saysAny(t, TO_GIVE) && !owedToTheShop(t) -> Kind.TO_GIVE
            // "Kisi ne sabse zyada paise dene hain" says "dene" and somebody else gives: who owes the shop most (14 Nov 2026).
            saysAny(t, MOST) && owedToUs(t) -> Kind.WHO_OWES_MOST
            saysAny(t, SALES) -> if (saysAny(t, TODAY) || windowOf(heard) == Window.TODAY) Kind.SALES_TODAY else Kind.SALES_MONTH
            // Which way the money is owed, by who gives (14 Nov 2026, the owner's sentences): "kis kis ne mera paisa dena
            // hai" says "dena" but somebody else gives — the shop's to get; "mera qarz", "supplier ka baqaya" are what the
            // shop owes — its to give.
            saysAny(t, TO_GIVE) -> if (owedToTheShop(t)) Kind.TO_GET else Kind.TO_GIVE
            saysAny(t, TO_GET) || saysAny(t, DEBT) -> if (owedByTheShop(t)) Kind.TO_GIVE else Kind.TO_GET
            else -> null
        }
    }

    /**
     * Somebody else is the one who gives: "kis ne dena hai", "logon ne dene hain", "who owes me", and a name before "ne"
     * ("Zaid ne dene hain": Zaid gives, the shop gets).
     */
    private fun owedToTheShop(t: String): Boolean = saysAny(t, OTHERS_GIVE) || otherDoer(t)

    /** Money is the shop's to get: its debtors' words, "lena", or somebody else who has to give. */
    private fun owedToUs(t: String): Boolean =
        saysAny(t, DEBT) || saysAny(t, TO_GET) || (saysAny(t, TO_GIVE) && owedToTheShop(t))

    /** The doer's mark in Urdu, Hindi and Roman Urdu ("ne"), normalised. */
    private val NE_WORDS = setOf("ne", "نے", "ने")

    /** The shop's own "I" and "we" before that mark ("main ne", "hum ne"), normalised: these give, the shop is the doer. */
    private val SHOP_DOERS = setOf("main", "mai", "mein", "me", "mujh", "hum", "ham", "میں", "ہم", "मैं", "मै", "हम")

    /** [otherDoer] for a sentence as it was said (Hudhud keeps a figure the rules read from it; see his `decide`). */
    fun hasOtherDoer(heard: String): Boolean = otherDoer(normalise(heard))

    /** Somebody other than the shop is before a lone "ne": a name, "kisi", "logon", "customer"… */
    internal fun otherDoer(t: String): Boolean {
        val w = t.split(' ')
        for (i in 1 until w.size) if (w[i] in NE_WORDS && w[i - 1] !in SHOP_DOERS) return true
        return false
    }

    /** The shop is the one who owes: its own debt, or a supplier's balance with nothing to take ("supplier ka baqaya"). */
    private fun owedByTheShop(t: String): Boolean =
        saysAny(t, MY_DEBT) || (saysAny(t, SUPPLIER_SIDE) && !saysAny(t, TO_GET) && !saysAny(t, OTHERS_GIVE))

    private val OTHERS_GIVE = listOf(
        "kis ne", "kisne", "kis kis ne", "logon ne", "logo ne", "unhon ne", "unhone", "us ne", "usne", "customer ne",
        "customers ne", "gahak ne", "gahakon ne", "who owes me", "owes me", "owe me", "owing me",
        // "Somebody", "the one who" (14 Nov 2026): "kisi ne mujhe paise dene hain", "jisne sabse zyada dene hain".
        "kisi ne", "kisine", "jis ne", "jisne", "kisi kisi ne",
        "کس نے", "کس کس نے", "لوگوں نے", "انہوں نے", "اس نے", "گاہک نے", "किस ने", "किसने", "लोगों ने", "उसने", "ग्राहक ने",
        "کسی نے", "جس نے", "किसी ने", "जिसने", "जिस ने",
        "কে দেবে", "কারা দেবে", "আমাকে দেবে", "من يدين لي", "يدينون لي", "به من بدهکار", "siapa yang berutang"
    )

    private val MY_DEBT = listOf(
        "mera qarz", "meri qarz", "mere qarz", "mera karz", "mere zimme", "mere zime", "mujh par", "mujhpar", "humara qarz",
        "my debt", "i owe", "we owe", "میرا قرض", "میرے ذمے", "مجھ پر", "ہمارا قرض", "मेरा कर्ज", "मेरा क़र्ज़", "मेरे जिम्मे",
        "मुझ पर", "আমার ঋণ", "আমার দেনা", "ديني", "علي دين", "بدهی من", "منهنجو قرض", "utang saya", "hutang saya"
    )

    /** A supplier's side of the book: what is owed there is the shop's to pay. */
    private val SUPPLIER_SIDE = listOf(
        "supplier", "suppliers", "company", "kampani", "maal wale", "maal wala", "distributor", "wholesaler",
        "سپلائر", "کمپنی", "مال والے", "सप्लायर", "कंपनी", "সাপ্লায়ার", "কোম্পানি", "المورد", "الموردين", "تامین کننده",
        "تأمین‌کننده", "سپلائر", "pemasok", "supplier"
    )

    /** The first of the recogniser's guesses that is a question this app knows. */
    fun classify(candidates: List<String>): Pair<String, Kind>? =
        candidates.firstNotNullOfOrNull { c -> classify(c)?.let { c to it } }
}
