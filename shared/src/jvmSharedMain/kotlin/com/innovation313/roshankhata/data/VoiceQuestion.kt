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

    private val PROFIT = listOf("munafa", "munafah", "profit", "laba", "untung", "منافع", "نفعو", "ربح", "ربحت", "ربحنا", "أرباح", "ارباح",
        "سود", "मुनाफ़ा", "मुनाफा", "लाभ", "লাভ", "মুনাফা",
        // 12 Nov 2026 (Hudhud): "is mahine kitna faida hua", as most owners say it.
        "faida", "fayda", "faaida", "فائدہ", "फायदा",
        // 16 Nov 2026 (audit T32): the oblique of munafa, as "is mahine ke munafe ka farq" says it.
        "munafe", "munafay", "munafey", "मुनाफे")
    private val EXPIRING = listOf("expire", "expiry", "mudat", "kedaluwarsa", "kadaluarsa", "ایکسپائر", "میعاد", "مدت",
        "انقضا", "انتهاء", "صلاحيت", "एक्सपायर", "मियाद", "মেয়াদ", "এক্সপায়ার",
        // 12 Nov 2026 (Hudhud): the app's own chip words, so a chip typed as it reads is understood.
        "expiring", "expired", "ختم ٿيندڙ")
    private val NOT_PAID = listOf("nahi diya", "nahin diya", "paisa nahi", "not paid", "hasn't paid", "havent paid", "belum bayar",
        // 13 Nov 2026 (Hudhud Brain v2): "kaun nahi de raha", the present and the plural.
        "nahi diye", "nahin diye", "nahi de raha", "nahi de rahe", "nahi de rahi", "nahi deta", "nahi dete",
        "belum membayar", "نہیں دیا", "نہیں دیے", "نہیں دئیے", "ادا نہیں", "نه ڏنو", "نه ڏنا", "لم يدفع", "لم يدفعوا", "لم يسدد", "لم يسددوا", "پرداخت نکرده",
        "नहीं दिया", "नहीं दिए", "দেয়নি", "দেননি", "পরিশোধ করেনি",
        // 16 Nov 2026 (audit T10, wrong side): "paise na dene wale kaun hain" are the ones who do not pay, not the shop's debt.
        "na dene wale", "na dene wala", "na dene waale", "nahi dene wale", "nahi dene wala", "نہ دینے والے", "न देने वाले")
    private val MOST = listOf("sab se zyada", "sabse zyada", "sab se ziada", "most", "highest", "biggest", "terbanyak", "paling",
        "terbesar",
        "سب سے زیادہ", "سڀ کان وڌيڪ", "الأكثر", "أكثر", "اكثر", "بیشترین", "सबसे ज़्यादा", "सबसे ज्यादा",
        "সবচেয়ে বেশি",
        // 24 Nov 2026 (BC-256): Arabic "biggest" and "highest" ("من يدين لي بأكبر مبلغ": the one who owes me the biggest sum; "بأكبر" is "with
        // biggest": the joined "ب" is taken by isWord).
        "أكبر", "اكبر", "الأكبر", "أعلى", "اعلى", "الأعلى")
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
        "baqi", "baaqi", "baki", "baaki", "باقی", "बाकी", "dues",
        // 15 Nov 2026 (review O): Farsi's "they are in debt" (the plural of بدهکار, which alone is the shop's own debt).
        "بدهکارند")
    private val SALES = listOf("bikri", "bechi", "becha", "bechay", "farokht", "sale", "sales", "sold", "penjualan", "jualan",
        "terjual", "بکری", "فروخت", "بیچا", "بیچی", "وڪرو", "وڪري", "مبيعات", "بيع", "فروش", "बिक्री", "बेचा", "बेची",
        "বিক্রি", "বিক্রয়", "সেল",
        // 12 Nov 2026 (Hudhud): "aaj kitna bika", "how much did I sell".
        "sell", "selling", "bika", "biki", "bikay", "بکا", "بکی", "बिका", "बिकी",
        // 13 Nov 2026 (Hudhud's chips): Sindhi's "got sold", Arabic's "what was sold".
        "وڪاميو", "وڪاميا", "مباع",
        // 15 Nov 2026 (review O): Arabic "I sold", "we sold" — which the name search had folded into a customer called Butt.
        "بعت", "بعنا",
        // 25 Nov 2026 (H4): Farsi "I sold", "we sold".
        "فروختم", "فروختیم",
        // The English word as Urdu and Hindi write it ("آج کی ٹوٹل سیلز" was what the shop is owed).
        "سیل", "سیلز", "सेल", "सेल्स")
    private val TODAY = listOf("aaj", "today", "hari ini", "آج", "اڄ", "اليوم", "امروز", "आज", "আজ",
        // 15 Nov 2026 (review O): "aaj" as it is typed fast ("aj ki sel", "aj ka munafa").
        "aj", "ajj",
        // 25 Nov 2026 (H4): "since the morning (until now)" is the day so far.
        "از صبح", "subah se", "since morning", "since the morning")

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

    // A festival framed as a span by the words beside it (17 Nov 2026, L004: "Eid se ab tak ki sale" was the whole book, "Eid par kitni
    // sale hui" and "Christmas ki sale" this month's figure). Side by side only, so "Eid Muhammad se ab tak kitna mila" stays a man
    // called Eid; "Ramzan" and "Muharram" are left to the frames above ("Ramzan se ab tak kitna mila" asks a man called Ramzan).
    private val EID = listOf("eid", "عید", "ईद", "ঈদ")
    private val FESTIVALS = listOf("christmas", "کرسمس", "क्रिसमस", "diwali", "دیوالی", "दिवाली", "दीवाली", "holi", "होली", "lebaran",
        "idul fitri", "idul adha", "chand raat", "chaand raat", "چاند رات", "bakra eid", "bakri eid", "bakrid", "बकरीद", "eid ul fitr",
        "eid ul adha", "eid ul azha", "عید الفطر", "عید الاضحی", "عید الاضحیٰ")
    private val SPAN_AFTER = listOf("se ab tak", "se abhi tak", "se aaj tak", "se lekar", "tak", "par", "pe", "ke mauqe par", "ke moqe par",
        "ke mauke par", "سے اب تک", "سے ابھی تک", "سے آج تک", "تک", "پر", "کے موقع پر", "से अब तक", "से अभी तक", "से आज तक", "तक", "पर",
        "पे", "के मौके पर", "থেকে")
    private val FESTIVAL_AFTER = SPAN_AFTER + listOf("ki", "ka", "کی", "کا", "की", "का")
    private val SPAN_BEFORE = listOf("on", "since", "till", "until", "from", "منذ", "از", "sejak", "sampai", "hingga")

    /** Whether [t] (normalised) holds a festival with a span's word right beside it ([EID], [FESTIVALS]). */
    internal fun framedEvent(t: String): Boolean {
        val toks = t.split(' ')
        val before = SPAN_BEFORE.map { normalise(it) }.toSet()
        fun runAt(i: Int, phrase: List<String>) = i + phrase.size <= toks.size && phrase.indices.all { toks[i + it] == phrase[it] }
        fun framed(events: List<String>, after: List<String>): Boolean {
            val afters = after.map { normalise(it).split(' ') }
            for (e in events) {
                val ev = normalise(e).split(' ')
                for (i in 0..toks.size - ev.size) {
                    if (!runAt(i, ev)) continue
                    if (i > 0 && toks[i - 1] in before) return true
                    if (afters.any { runAt(i + ev.size, it) }) return true
                }
            }
            return false
        }
        return framed(EID, SPAN_AFTER) || framed(FESTIVALS, FESTIVAL_AFTER)
    }

    private fun isEventSpan(t: String): Boolean {
        if (framedEvent(t)) return true
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
        "بدهی", "ما عليّ", "harus dibayar",
        // 15 Nov 2026 (review O, everyday asks): the accountant's word, and "to pay off" said as "ada karna".
        "payable", "payables", "ada karna", "ادا کرنا", "अदा करना",
        // 16 Nov 2026 (audit T11, the held-out set read these the opposite way): Farsi "the money I must give", Hindi's
        // accountant's word for payables.
        "باید بدم", "باید بدهم", "باید بپردازم", "باید بدیم", "باید بدهیم", "باید بپردازیم", "देनदारी",
        // 17 Nov 2026 (the lead's retrain check): the feminine of "dena" ("kis ki raqam deni hai" is whom the shop pays).
        "deni", "دینی", "देनी",
        // 25 Nov 2026 (BC-336, Marhala 1, from Agent B's out-of-fold measure): Arabic's "we / I pay, settle, owe" — "كم باقي ندفع" was the shop's
        // to GET by the bare "باقي", and the sense read "كم باقي علينا ندفع" as to get at 0.9966; only "علينا" held it. Native read.
        "ندفع", "ندفعه", "ندفعها", "ندفعهم", "نسدد", "نسدده", "نسددها", "أدفع", "أدفعه", "أسدد", "أسدده", "ندين")
    private val TO_GET = listOf("lena", "lene", "to get",
        // Marhala 1 (25 Nov 2026, BC-322): the feminine of "lena", as "deni" is of "dena" ("Ali se 500 leni hai": he owes the shop).
        "leni", "لینی", "लेनी", "receive", "collect", "piutang", "tagih", "لینا", "لینے", "وٺڻا",
        // 25 Nov 2026 (BC-240, seed safety): Farsi "I must get" ("چقدر باید بگیرم" was a customer's balance under one shuffle).
        "باید بگیرم", "باید بگیریم", "بگیرم",
        "وٺڻو", "لي عند", "طلبکار", "دریافت", "लेना", "लेने", "পাওনা", "পেতে",
        // 12 Nov 2026 (Hudhud), from the app's own chips: Arabic "what is mine", Indonesian "what must be received".
        "ما لي", "harus diterima",
        // 15 Nov 2026 (review O, everyday asks): the accountant's word; "to collect" said as "wusool karna"; Bengali's "I will get";
        // Arabic's "on the customers". Money still to come ("paise kitne aane hain") is HudhudEveryday's: it needs its guards.
        "receivable", "receivables", "wusool karna", "wasool karna", "wasool karne", "wusool karne", "وصول کرنا", "वसूल करना",
        "পাব", "পাবো", "على الزبائن", "على العملاء",
        // 23 Nov 2026 (BC-240): Arabic "what I must receive" ("ما يجب أن أستلمه من العملاء"); only the app's shuffle of the sense read it.
        "أستلمه", "أستلم", "استلمه",
        // 16 Nov 2026 (audit T11): Hindi's receivables; Indonesian "to be billed" (tagih alone was there, not its forms).
        "लेनदारी", "ditagih", "menagih")

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
     * How many spans [heard] names (25 Nov 2026, plan H5, the advisor's comparisons): each word of a week, a month or a year, each
     * time it is said, and today, yesterday, this year and last year once each. "Pichle hafte se zyada bika" names one span (the
     * one it is compared against); "is hafte pichle hafte se zyada bika" names two, a comparison of two spans the sentence states
     * itself. Indonesian "kemarin" beside a unit is that unit's "last" ("minggu kemarin"), not yesterday as well.
     */
    internal fun spansNamed(heard: String): Int {
        val t = normalise(heard)
        if (t.isEmpty()) return 0
        val said = t.split(' ')
        var n = said.count { s -> UNIT_SPANS.any { k -> isWord(s, k) } }
        if (saysAny(t, TODAY)) n++
        if (saysAny(t, YESTERDAY) && !(n > 0 && said.contains("kemarin"))) n++
        if (saysAny(t, THIS_YEAR) || saysAny(t, LAST_YEAR)) n++
        return n
    }

    /** The words of a week, a month and a year, each one word (normalised), for [spansNamed]. */
    private val UNIT_SPANS: List<String> by lazy { (WEEK + MONTH + YEAR).map { normalise(it) }.filter { ' ' !in it } }

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

    // ------------------------------------------------------------------------------------ a day or a month named outright

    /**
     * A day or a month named outright (16 Nov 2026, audit T31 / H-02, H-09): "15 tareekh ki sale", "12 November ki sale",
     * "November ki sale", "parson ki sale", "pichle jumme ki sale". None of them is a [Window]: they were answered as this
     * month's figure, or offered the spans he tells. Only what was said is set; [Hudhud.span] turns it into days against the
     * clock, and a day that has not yet come is never answered with a figure.
     *
     * [day] 1..31 with or without [month] 1..12; [year] only as said; [daysBack] 2 for the day before yesterday; [monthsBack]
     * and [yearsBack] when "this/last month" or "this/last year" stands beside a day or a month; [weekday] (Calendar's
     * SUNDAY..SATURDAY) for the last such day before today.
     */
    data class Dated(
        val day: Int? = null, val month: Int? = null, val year: Int? = null, val daysBack: Int? = null,
        val monthsBack: Int? = null, val yearsBack: Int? = null, val weekday: Int? = null
    )

    /**
     * The months' names: Unicode CLDR, as JDK 21 gives them (java.time.Month.getDisplayName, FULL and FULL_STANDALONE, for en,
     * ur, hi, ar, fa, bn, id and sd; read 16 Nov 2026), Hindi's second spelling with the half म (सितम्बर, नवम्बर, दिसम्बर) and
     * अक्टूबर beside CLDR's अक्तूबर. The second list of each month is only a month beside a day, a year, a word for "month" or
     * a word of "of" ([AMBIG_NEXT]): English "may" (a modal, and Roman Urdu's "mein"), the short forms ("jan" is also "jaan"),
     * Arabic and Farsi مارس (also "he practised"), Farsi مه (also "fog") and اوت, Sindhi جون (also Sindhi's "of"), Bengali মে.
     */
    private val MONTH_WORDS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("january", "جنوری", "जनवरी", "يناير", "ژانویه", "জানুয়ারী", "januari", "جنوري") to listOf("jan"),
        listOf("february", "فروری", "फ़रवरी", "فبراير", "فوریه", "ফেব্রুয়ারী", "februari", "فيبروري") to listOf("feb"),
        listOf("march", "مارچ", "मार्च", "মার্চ", "maret") to listOf("مارس", "mar"),
        listOf("april", "اپریل", "अप्रैल", "أبريل", "آوریل", "এপ্রিল", "اپريل") to listOf("apr"),
        listOf("مئی", "मई", "مايو", "mei", "مئي") to listOf("may", "مه", "মে"),
        listOf("june", "जून", "يونيو", "ژوئن", "জুন", "juni") to listOf("jun", "جون"),
        listOf("july", "جولائی", "जुलाई", "يوليو", "ژوئیه", "জুলাই", "juli", "جولاءِ") to listOf("jul"),
        listOf("august", "اگست", "अगस्त", "أغسطس", "আগস্ট", "agustus", "آگسٽ") to listOf("aug", "اوت"),
        listOf("september", "ستمبر", "सितंबर", "सितम्बर", "سبتمبر", "سپتامبر", "সেপ্টেম্বর", "سيپٽمبر") to listOf("sep", "sept"),
        listOf("october", "اکتوبر", "अक्तूबर", "अक्टूबर", "أكتوبر", "اکتبر", "অক্টোবর", "oktober", "آڪٽوبر") to listOf("oct"),
        listOf("november", "نومبر", "नवंबर", "नवम्बर", "نوفمبر", "نوامبر", "নভেম্বর") to listOf("nov"),
        listOf("december", "دسمبر", "दिसंबर", "दिसम्बर", "ديسمبر", "دسامبر", "ডিসেম্বর", "desember", "ڊسمبر") to listOf("dec")
    )

    /** A word for "of" after a month ("may ki sale", "May's sales"): it makes a doubtful month name a month. */
    private val AMBIG_NEXT: Set<String> by lazy {
        listOf("ki", "ka", "ke", "کی", "کا", "کے", "की", "का", "के", "s").map { normalise(it) }.toSet()
    }

    /** The word for a date beside its day ("15 tareekh", "tanggal 15", "15 date ko"). */
    private val DATE_WORDS = listOf("tareekh", "tarikh", "tareek", "tarik", "tarekh", "date", "تاریخ", "तारीख", "তারিখ", "tanggal")

    /** The future of the verb: "parson" is then the day after tomorrow, never a day of the book. */
    private val FUTURE_WORDS = listOf("will", "tomorrow", "گا", "گی", "گے", "گیں", "akan", "besok", "lusa", "سوف", "خواهد",
        "হবে", "দেবে", "পাবে", "আসবে",
        // Marhala 1 (25 Nov 2026, BC-323): the shop's own "I will" in the languages whose future the list did not hold — "Kal Ali ko 500
        // dunga" opened "I gave 500" dated yesterday. English "I'll"/"we'll" (the apostrophe is a space by then); Bengali's first person
        // ("দেব", "নেব"); Arabic's "I/we will pay, give" (سأ…/سن…, the hamza goes in normalise) and "tomorrow"; Farsi's "I/we will" and
        // "tomorrow"; Sindhi's "tomorrow"; Indonesian's "later". Each needs a native read (sd, bn, ar, fa).
        "i ll", "we ll", "দেব", "দেবো", "দিব", "দিবো", "নেব", "নেবো", "পাঠাব", "পাঠাবো", "আগামীকাল",
        "سأدفع", "سأعطي", "سأسدد", "سأرسل", "سندفع", "سنعطي", "سنسدد", "سيدفع", "سيعطي", "غدا", "غداً", "بكرة",
        "خواهم", "خواهیم", "فردا", "سڀاڻي", "nanti")
    // "-unga" / "-ungi" (Marhala 1, BC-323): the first person of the Roman Urdu future ("dunga", "doongi", "lunga", "bhejunga"), which the
    // third person's "-ega" did not reach. Sindhi's first person ("ڏيندس", "ڏينداسين": I / we will give) by its ending too.
    private val FUTURE_ENDS = listOf("ega", "egi", "enge", "oga", "ogi", "onge", "unga", "ungi", "oonga", "oongi", "गा", "गी", "गे", "ندس", "نداسين").map { normalise(it) }

    /** The weekdays a "last" may stand before ([WEEKDAYS]' own words). */
    private val WEEKDAY_OF: Map<String, Int> by lazy {
        val out = HashMap<String, Int>()
        for (w in listOf("jumma", "jumme", "jume", "jummah", "juma", "friday", "جمعہ", "جمعے", "جمعه", "الجمعة")) out[normalise(w)] = java.util.Calendar.FRIDAY
        for (w in listOf("somwar", "monday")) out[normalise(w)] = java.util.Calendar.MONDAY
        for (w in listOf("itwar", "sunday", "इतवार")) out[normalise(w)] = java.util.Calendar.SUNDAY
        out
    }

    private val ORDINAL = Regex("(\\d{1,2})(st|nd|rd|th)")

    /** "12/11", "12-11", "12/11/2026", "12/11/26": a day and a month, and maybe a year, written with figures ([dateFound]). */
    private val SLASH_DATE = Regex("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}|\\d{2}))?")

    /** What a date found in a sentence is, and which of its words (by their place in the sentence's raw words) are figures of it. */
    private class DateFound(val dated: Dated, val figures: Set<Int>)

    /**
     * The day or the month [heard] names outright ([Dated]), or null. A word the book knows as a name ([isName]) is never a
     * month ("June Traders"). A date said beside a span it cannot stand with ("aaj 15 tareekh") is not read.
     */
    fun datedOf(heard: String, isName: (String) -> Boolean = { false }): Dated? = dateFound(heard, isName)?.dated

    /**
     * [heard] without the figures of the date it names (16 Nov 2026): "Ali Raza ko 12 November ko 500 diye" — the amount is 500,
     * never 12. The sentence is otherwise kept as it was said.
     */
    fun withoutDate(heard: String, isName: (String) -> Boolean = { false }): String {
        val found = dateFound(heard, isName) ?: return heard
        if (found.figures.isEmpty()) return heard
        val raw = heard.trim().split(SPACES)
        return raw.filterIndexed { i, _ -> i !in found.figures }.joinToString(" ")
    }

    private fun dateFound(heard: String, isName: (String) -> Boolean): DateFound? {
        val raw = heard.trim().split(SPACES).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return null
        // Each raw word's normalised words, each remembering the raw word it came from.
        val word = ArrayList<String>()
        val from = ArrayList<Int>()
        for ((i, r) in raw.withIndex()) for (p in normalise(Digits.toLatin(r)).split(' ')) if (p.isNotEmpty()) { word += p; from += i }
        if (word.isEmpty()) return null
        val t = word.joinToString(" ")
        val window = windowOf(heard)
        fun dayAt(j: Int): Int? {
            val s = word.getOrNull(j) ?: return null
            val digits = ORDINAL.matchEntire(s)?.groupValues?.get(1) ?: s.takeIf { it.length <= 2 && it.all { c -> c in '0'..'9' } } ?: return null
            return digits.toInt().takeIf { it in 1..31 }
        }
        fun yearAt(j: Int): Int? {
            val s = word.getOrNull(j) ?: return null
            return s.takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it in 2000..2099 }
        }
        fun monthAt(j: Int): Pair<Int, Boolean>? {
            val s = word[j]
            if (isName(s)) return null
            for ((m, lists) in MONTH_WORDS.withIndex()) {
                if (lists.first.any { isWord(s, spelt.getOrPut(it) { normalise(it) }) }) return (m + 1) to true
                if (lists.second.any { isWord(s, spelt.getOrPut(it) { normalise(it) }) }) return (m + 1) to false
            }
            return null
        }
        fun monthWord(j: Int) = word.getOrNull(j)?.let { saysAny(it, MONTH) } == true
        // ---- a day written with figures and a slash or a dash: "12/11", "12-11", "12/11/2026" (16 Nov 2026). Day first, the way
        // the app's countries write a date; only "11/25", whose second figure can be no month, is read month first. A dot is never a
        // date's mark ("2.5" is two and a half), and only one or two figures each side ("0300-1234567" is a phone number).
        for ((i, r) in raw.withIndex()) {
            val m = SLASH_DATE.matchEntire(Digits.toLatin(r).trimEnd(',', '.', '?', '!', '،', '؟', '۔')) ?: continue
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val (day, month) = when {
                a in 1..31 && b in 1..12 -> a to b
                a in 1..12 && b in 13..31 -> b to a
                else -> continue
            }
            val y = m.groupValues[3]
            val year = when (y.length) {
                0 -> null
                2 -> 2000 + y.toInt()
                else -> y.toInt().takeIf { it in 2000..2099 } ?: continue
            }
            val yearsBack = when {
                year != null -> null
                window == Window.THIS_YEAR -> 0
                window == Window.LAST_YEAR -> 1
                else -> null
            }
            return DateFound(Dated(day = day, month = month, year = year, yearsBack = yearsBack), setOf(i))
        }
        // ---- a month, with its day and year beside it
        for (j in word.indices) {
            val (m, sure) = monthAt(j) ?: continue
            val dayBefore = dayAt(j - 1)
            val dayAfter = if (dayBefore == null) dayAt(j + 1) else null
            val day = dayBefore ?: dayAfter
            val yearIdx = when {
                yearAt(j + 1) != null -> j + 1
                dayAfter != null && yearAt(j + 2) != null -> j + 2
                else -> -1
            }
            val year = if (yearIdx >= 0) yearAt(yearIdx) else null
            if (!sure && day == null && year == null && !monthWord(j - 1) && !monthWord(j + 1) && word.getOrNull(j + 1) !in AMBIG_NEXT) continue
            val yearsBack = when {
                year != null -> null
                window == null || window == Window.THIS_MONTH -> null
                window == Window.THIS_YEAR -> 0
                window == Window.LAST_YEAR -> 1
                else -> return null
            }
            val figures = HashSet<Int>()
            if (dayBefore != null) figures += from[j - 1]
            if (dayAfter != null) figures += from[j + 1]
            if (yearIdx >= 0) figures += from[yearIdx]
            return DateFound(Dated(day = day, month = m, year = year, yearsBack = yearsBack), figures)
        }
        // ---- a day of the month alone: "15 tareekh", "tanggal 15", "the 15th"
        for (j in word.indices) {
            val ordinal = ORDINAL.matches(word[j]) && (word.getOrNull(j - 1) == "the" || word.getOrNull(j - 1) == "on")
            val dateWord = saysAny(word.getOrNull(j + 1) ?: "", DATE_WORDS) || saysAny(word.getOrNull(j - 1) ?: "", DATE_WORDS)
            val day = dayAt(j) ?: continue
            if (!ordinal && !dateWord) continue
            val monthsBack = when (window) {
                null -> null
                Window.THIS_MONTH -> 0
                Window.LAST_MONTH -> 1
                else -> return null
            }
            return DateFound(Dated(day = day, monthsBack = monthsBack), setOf(from[j]))
        }
        if (window != null) return null
        val future = saysAny(t, FUTURE_WORDS) || word.any { w -> w.length >= 3 && FUTURE_ENDS.any { e -> w.endsWith(e) && w != e } }
        // ---- the day before yesterday ("parson" is the day after tomorrow too: never with the future of a verb)
        if (saysAny(t, DAY_BEFORE)) return if (future) null else DateFound(Dated(daysBack = 2), emptySet())
        // ---- the last Friday, Monday or Sunday: a "last" straight before the day, and no "since" (that is a span to now)
        for (j in 1 until word.size) {
            val day = WEEKDAY_OF[word[j]] ?: continue
            if (isName(word[j]) || !saysAny(word[j - 1], LAST) || saysAny(t, SINCE) || future) continue
            return DateFound(Dated(weekday = day), emptySet())
        }
        return null
    }

    /**
     * The day an entry said as a fact names (16 Nov 2026): "Ali ko kal 500 diye" is yesterday's, "parson Bashir se 300 liye" the
     * day before, "12/11 ko …" and "12 November ko …" that day, "pichle jumme" the last Friday. Today, a month without its day, and
     * a sentence in the future of the verb ("kal dega" is a promise, and "kal" is tomorrow there) name no day: the form opens on
     * now, as before. Read by the very date reader the questions use ([datedOf]); the app turns it into a moment that is never in
     * the future ([Hudhud.entryTime]).
     */
    fun entryDay(heard: String, isName: (String) -> Boolean = { false }): Dated? {
        if (saysFuture(heard)) return null
        dateFound(heard, isName)?.dated?.let { d -> return if (d.day == null && d.daysBack == null && d.weekday == null) null else d }
        return if (windowOf(heard) == Window.YESTERDAY) Dated(daysBack = 1) else null
    }

    /**
     * Whether the verb of [heard] is in the future ("dega", "denge", "will pay", "देगा", "দেবে", "سوف"): the date reader's own words
     * ([FUTURE_WORDS], [FUTURE_ENDS]). Such a sentence is a promise, never an entry of money that moved.
     */
    fun saysFuture(heard: String): Boolean {
        val t = normalise(Digits.toLatin(heard))
        return saysAny(t, FUTURE_WORDS) || t.split(' ').any { w -> w.length >= 3 && FUTURE_ENDS.any { e -> w.endsWith(e) && w != e } }
    }

    /**
     * Money that has NOT moved, though the sentence carries an amount (Marhala 1, 25 Nov 2026): never an entry of money that came
     * or went, whoever reads it.
     *
     * - A giving or taking that is denied (BC-321): "Ali ne 500 nahi diye", "Ali Raza did not pay 500", "Ali Raza 500 نداد",
     *   "Ali Raza tidak bayar 500" — each opened "I got 500" (and the Farsi "I gave 500"): a payment that never came written as one.
     * - A sum still owed, one way or the other (BC-322): "Ali se 500 lene hain" (he owes the shop), "Ali ko 500 dene hain" (the shop
     *   owes him), "I owe Ali 500", "Ali owes me 500", "از Ali 500 طلب دارم", "Ali Raza কে 500 দিতে হবে". They opened "I got 500" or
     *   "I gave 500" — a balance turned into a movement, and for "lene hain" on the side that LOWERS what he owes.
     *
     * The words of a balance are the side lists' own ([TO_GIVE], [TO_GET]) with the few that state a debt with its person; the denial
     * is a word of "not" in the nine languages or a verb that carries it (Farsi's "ن…", Bengali's "…নি").
     */
    fun moneyNotMoved(heard: String): Boolean {
        val t = normalise(Digits.toLatin(heard))
        return t.isNotEmpty() && (moveDenied(t) || stillOwed(t))
    }

    /**
     * The shop itself will pay or give (Marhala 1, BC-323): "I will pay Ali tomorrow", "main kal Ali ko dunga", "میں دوں گا",
     * "मैं दूँगा", Sindhi "ڏيندس", Bengali "দেব", Arabic "سأدفع", Farsi "می‌دهم" / "خواهم داد", Indonesian "saya akan bayar". The shop's
     * own payment still to make, never the day a customer pays it (the Radar's question). Sindhi, Bengali, Farsi, Arabic: native read.
     */
    fun shopWillPay(heard: String): Boolean {
        val t = normalise(Digits.toLatin(heard))
        val said = t.split(' ')
        // "He keeps saying 'I'll pay next week' — when will he pay?": a customer's own words quoted beside his third person are his promise.
        if (t.isEmpty() || saysAny(t, HE_WILL_GIVE)) return false
        return saysAny(t, SHOP_WILL_PAY) || said.any { it in SHOP_WILL_WORDS } ||
            said.any { w -> SHOP_WILL_ENDS.any { e -> w.endsWith(e) && w.length > e.length } }
    }

    private val SHOP_WILL_PAY = listOf(
        "i will pay", "i will give", "i ll pay", "i ll give", "we will pay", "we will give", "we ll pay", "we ll give", "i shall pay",
        "will i pay", "will i give", "will we pay", "will we give", "shall i pay", "should i pay",
        "dunga", "dungi", "doonga", "doongi", "dun ga", "doon ga", "dun gi", "doon gi", "chukaunga", "chukaungi", "bhejunga", "bhejungi",
        "دوں گا", "دوں گی", "دونگا", "دونگی", "दूंगा", "दूंगी", "दूँगा", "दूँगी", "दे दूंगा",
        "سأدفع", "سأعطي", "سأسدد", "سندفع", "سنعطي", "سنسدد",
        "میدهم", "می دهم", "می‌دهم", "خواهم داد", "خواهم پرداخت", "میپردازم", "می پردازم", "می‌پردازم",
        "saya akan bayar", "saya akan membayar", "saya akan kasih", "akan saya bayar", "kami akan bayar"
    )

    /**
     * Bengali's "I will give", matched as the whole word: [saysAny] lets a Bengali word carry its endings, and "দেবে" (he will give,
     * "কবে দেবে?") is "দেব" with one — the customer's promise, not the shop's.
     */
    private val SHOP_WILL_WORDS = setOf("দেব", "দেবো", "দিব", "দিবো").map { normalise(it) }.toSet()

    /** The customer's "he / she will give" ([shopWillPay] gives way to it). */
    private val HE_WILL_GIVE = listOf("dega", "degi", "de ga", "de gi", "देगा", "देगी", "دے گا", "دیگا", "دے گی", "دیگی", "will he pay",
        "will she pay", "will he give", "he will pay", "she will pay", "ڏيندو", "দেবে", "سيدفع", "سيعطي", "میدهد", "می دهد", "می‌دهد")

    /** Sindhi's "I / we will give, pay, send" by its ending (ڏيندس, ڏينداسين, موڪليندس). */
    private val SHOP_WILL_ENDS = listOf("يندس", "ينداسين").map { normalise(it) }

    /** A giving or taking denied ([moneyNotMoved], BC-321). */
    internal fun moveDenied(t: String): Boolean = saysAny(t, NOT_PAID) || saysAny(t, NOT_MOVED)

    /** A sum still owed, either way ([moneyNotMoved], BC-322). */
    internal fun stillOwed(t: String): Boolean =
        saysAny(t, TO_GIVE) || saysAny(t, TO_GET) || saysAny(t, OWED_STILL) || saysAny(t, OWES_ME)

    /**
     * "Not", as it stands beside a verb of giving or taking, and the verbs that carry it (Farsi, Bengali). Not "nai" (Roman Urdu's "new")
     * nor "tak" (Indonesian's "not", Roman Urdu's "until": "aaj tak 500 diye" moved money). Sindhi, Bengali, Farsi, Arabic: native read.
     */
    private val NOT_MOVED = listOf(
        "nahi", "nahin", "nhi", "na diye", "na diya", "na di", "na liye", "na liya", "na li", "never", "not", "didn t", "didnt",
        "hasn t", "hasnt", "haven t", "havent", "doesn t", "won t",
        "نہیں", "نہ", "نه", "ڪونه", "नहीं", "नही", "ना", "না", "tidak", "belum", "gak", "nggak", "لم", "لا",
        "نداد", "ندادم", "ندادند", "ندادیم", "نداده", "نگرفت", "نگرفتم", "نگرفتند", "نگرفته", "نپرداخت", "نپرداختم", "نپرداختند", "نپرداخته",
        "দেয়নি", "দেননি", "দিইনি", "দিনি", "নেয়নি", "নেননি", "নিইনি", "পাইনি", "পায়নি", "করেনি"
    )

    /** A debt said with its person, beyond the side lists' words (BC-322). Sindhi, Bengali, Farsi and Arabic need a native read. */
    private val OWED_STILL = listOf(
        "owes", "owe", "owed", "have to pay", "have to give", "have to get", "have to receive", "has to pay", "has to give",
        "chukana", "chukane", "chukani",
        "بدهکارم", "بدهکاریم", "بدهکاره", "طلب دارم", "طلب داریم", "طلبکارم", "طلبکاریم",
        "harus bayar", "harus membayar", "harus terima", "harus menerima", "berutang", "berhutang",
        "দিতে হবে", "নিতে হবে", "পেতে হবে"
    )

    /** Whether [w] (normalised) is a weekday a "last" may stand before ("jumme" of "pichle jumme"). */
    internal fun isWeekdayWord(w: String): Boolean = w in WEEKDAY_OF

    private class Spans(val window: Window?, val unsupported: Boolean)

    private val NO_SPAN = Spans(null, false)
    private val NOT_OURS = Spans(null, true)

    private fun spans(heard: String): Spans {
        val t = normalise(heard)
        if (t.isEmpty()) return NO_SPAN
        // A festival framed as a span first: "Eid se aaj tak" holds "aaj tak" and is still no span he tells (17 Nov 2026).
        if (framedEvent(t)) return NOT_OURS
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
            // Not paid TO somebody (16 Nov 2026, audit T10): "kis ko aaj tak nahi diya", "kis supplier ko nahi diya" are whom
            // the shop has not paid — its payables, never who has not paid the shop.
            saysAny(t, NOT_PAID) -> if (unpaidByTheShop(t)) Kind.TO_GIVE else Kind.NOT_PAID
            // The smallest first (15 Nov 2026): Indonesian's "paling sedikit" holds the word "paling" of "most", and the balance, the debtor
            // and "-nya" are the debt's own words beside a least.
            saysAny(t, LEAST) && (owedToUs(t) || saysAny(t, LEAST_DEBT)) && !owedByTheShop(t) -> Kind.WHO_OWES_LEAST
            // "Sab se zyada kis ko dena hai" is whom the shop owes most: the shop's payables, not who owes it most.
            saysAny(t, MOST) && saysAny(t, TO_GIVE) && !owedToTheShop(t) -> Kind.TO_GIVE
            // "Kisi ne sabse zyada paise dene hain" says "dene" and somebody else gives: who owes the shop most (14 Nov 2026).
            saysAny(t, MOST) && owedToUs(t) && !supplierOwed(t) -> Kind.WHO_OWES_MOST
            // "Whose money is with me" (16 Nov 2026, audit T10): the shop's own debt said without a word of giving.
            saysAny(t, MY_DEBT) && !owedToTheShop(t) && !saysAny(t, TO_GET) -> Kind.TO_GIVE
            saysAny(t, SALES) -> if (saysAny(t, TODAY) || windowOf(heard) == Window.TODAY) Kind.SALES_TODAY else Kind.SALES_MONTH
            // Which way the money is owed, by who gives (14 Nov 2026, the owner's sentences): "kis kis ne mera paisa dena
            // hai" says "dena" but somebody else gives — the shop's to get; "mera qarz", "supplier ka baqaya" are what the
            // shop owes — its to give.
            // Said by its whole shape (17 Nov 2026): somebody will give "to me" ("log mujhe kitna denge"), the shop's own payment still
            // to make ("kul adaigi kitni baqi hai"). Before the words of giving, which "denge" and "adaigi" are not.
            owedToMe(t) -> Kind.TO_GET
            payDue(t) -> Kind.TO_GIVE
            saysAny(t, TO_GIVE) -> if (owedToTheShop(t)) Kind.TO_GET else Kind.TO_GIVE
            saysAny(t, TO_GET) || saysAny(t, DEBT) -> if (owedByTheShop(t)) Kind.TO_GIVE else Kind.TO_GET
            else -> null
        }
    }

    /**
     * Somebody else is the one who gives: "kis ne dena hai", "logon ne dene hain", "who owes me", and a name before "ne"
     * ("Zaid ne dene hain": Zaid gives, the shop gets).
     */
    private fun owedToTheShop(t: String): Boolean = saysAny(t, OTHERS_GIVE) || otherDoer(t) || owedToMe(t)

    /**
     * Somebody will give the money to the shop (17 Nov 2026): "to me" beside a giving verb in the future — "log mujhe kitna denge",
     * "लोग मुझे कुल कितना देंगे", "how much will people pay me", "berapa yang akan dibayar orang ke saya", "مردم چقدر به من می‌دهند".
     * The future is needed: "mujhe kitna dena hai" is what the shop has to give.
     */
    internal fun owedToMe(t: String): Boolean =
        (saysAny(t, TO_ME) && saysAny(t, WILL_GIVE)) || saysAny(t, OWED_TO_ME) ||
            (saysAny(t, listOf("pay me", "pay us", "give me money")) && saysAny(t, listOf("will")))
            || othersHoldMine(t)
            || (saysAny(t, MY_SHARE) && !saysAny(t, MY_DEBT) && !saysAny(t, TO_GIVE))

    /**
     * My money held by others (23 Nov 2026, BC-240): "logon ke paas mere kitne paise hain", "mera kitna paisa logon ke paas phansa hua
     * hai", "لوگوں کے پاس میرے کتنے پیسے ہیں" — what the shop is owed, said by who holds it. Until then only Hudhud's learnt sense
     * placed it, and another shuffle of the same bank read two such sentences as what the shop owes. The money must be "mine" and the
     * holder somebody else; "mere paas logon ke paise" (theirs with me) is the shop's own debt ([MY_DEBT]) and is never this.
     */
    private fun othersHoldMine(t: String): Boolean =
        saysAny(t, OTHERS_HOLD) && saysAny(t, MINE) && saysAny(t, MONEY_WORDS) && !saysAny(t, MY_DEBT)

    /**
     * "Mere hisse ka kitna baqi hai": what is left of my share is money owed to the shop (25 Nov 2026, H4: a retrained sense put it on the
     * other side). The side is what the sentence says (Marhala 1, 25 Nov 2026, BC-320): with a word of giving ("mere hisse ka kitna dena
     * baqi hai", "how much of my share do I still have to pay") it is the shop's to give — the share read it as to get, whatever the
     * sentence said ([owedToMe]). Sindhi, Bengali, Arabic, Farsi and Indonesian said the same bare share with the sense alone, and two
     * of them the other way; their words need a native read.
     */
    private val MY_SHARE = listOf("mere hisse", "mera hissa", "my share", "میرے حصے", "میرا حصہ", "मेरे हिस्से", "मेरा हिस्सा",
        "منهنجي حصي", "منهنجو حصو", "আমার ভাগের", "আমার ভাগ", "আমার অংশের", "আমার অংশ", "حصتي", "نصيبي", "سهم من", "سهمم",
        "bagian saya", "bagianku", "jatah saya")

    private val OTHERS_HOLD = listOf(
        "logon ke paas", "logo ke paas", "logon ke pas", "logon par", "logo par", "logon pe", "logo pe", "logon ki taraf",
        "customers ke paas", "customer ke paas", "gahakon ke paas", "grahakon ke paas", "bazaar mein", "bazar mein", "baazar mein",
        "market mein", "market me",
        "لوگوں کے پاس", "لوگوں پر", "لوگوں کی طرف", "گاہکوں کے پاس", "بازار میں", "مارکیٹ میں",
        "लोगों के पास", "लोगों पर", "लोगों की तरफ", "ग्राहकों के पास", "बाजार में", "बाज़ार में", "मार्केट में",
        // 25 Nov 2026: "my money is stuck outside" — the holder is "outside", somebody else.
        "bahar phansa", "bahar phansi", "bahar laga", "bahar lagi", "باہر پھنسا", "باہر پھنسی", "باہر لگا", "باہر لگی", "बाहर फंसा", "बाहर फंसी", "बाहर लगा", "बाहर लगी"
    )
    private val MINE = listOf("mera", "mere", "meri", "میرا", "میرے", "میری", "मेरा", "मेरे", "मेरी")
    private val MONEY_WORDS = listOf("paisa", "paise", "pese", "paisay", "raqam", "rakam", "پیسہ", "پیسے", "رقم", "पैसा", "पैसे", "रकम", "रक़म")

    private val TO_ME = listOf("mujhe", "mujhay", "mjhe", "mujhko", "hamein", "hamen", "humein", "humen", "hume", "مجھے", "ہمیں", "আমাকে", "আমাদের", "मुझे",
        "हमें", "मुझको", "مون کي", "اسان کي", "ke saya", "kepada saya", "ke kami", "kepada kami", "به من", "به ما")
    private val WILL_GIVE = listOf("denge", "dega", "degi", "dengay", "dein ge", "dain ge", "de ga", "de gi", "dein gi", "دیں گے", "دینگے",
        "دے گا", "دیگا", "دے گی", "دیگی", "देंगे", "देगा", "देगी", "দেবে", "দেবেন", "দিবে", "ڏيندا", "ڏيندو", "ڏيندي", "akan dibayar", "akan membayar",
        "akan bayar", "akan diberikan", "می‌دهند", "میدهند", "می دهند", "می‌دهد", "میدهد", "می دهد")
    private val OWED_TO_ME = listOf("আমাকে দেবে", "আমাকে দেবেন", "আমাকে দিবে", "আমাকে কত দেবে", "سيدفع لي", "سيدفعون لي", "يدفعون لي",
        "سيعطونني", "يعطونني")

    /**
     * The shop's own payment still to make (17 Nov 2026): a word of paying out ("adaigi", the shop's paying) beside a word of what is
     * left ("baqi", Sindhi "رهيل"), with nobody asked about — "kul adaigi kitni baqi hai", "ڪل ادائيگي ڪيتري رهيل آهي". "Kis ki
     * adaigi baqi hai" asks who has not paid, and is not here.
     */
    internal fun payDue(t: String): Boolean =
        saysAny(t, ADAIGI) && saysAny(t, STILL_LEFT) && !saysAny(t, WHOSE) && !saysAny(t, OTHERS_GIVE) && !otherDoer(t)

    private val ADAIGI = listOf("adaigi", "adaygi", "adaigiyan", "ادائیگی", "ادائیگیاں", "ادائيگي", "ادائيگيون", "अदायगी")
    private val STILL_LEFT = listOf("baqi", "baaqi", "baki", "bakaya", "reh gayi", "rehti", "باقی", "بقایا", "رهيل", "بچيل", "बाकी", "बकाया")
    private val WHOSE = listOf("kis", "kis ki", "kaun", "kon", "کس", "کون", "ڪنهن", "ڪير", "किस", "कौन", "who", "whose")

    /**
     * The side is stated by the sentence's whole shape ([owedToMe], [payDue]). [classify] reads the side from it and [sideSaid] holds it
     * against the sense's side readings (lesson 148); Hudhud also reads such a how-much as the total, not the everyday "who owes most".
     */
    fun sideStated(heard: String): Boolean {
        val t = normalise(heard)
        return t.isNotEmpty() && (owedToMe(t) || payDue(t))
    }

    /** Money is the shop's to get: its debtors' words, "lena", or somebody else who has to give. */
    private fun owedToUs(t: String): Boolean =
        saysAny(t, DEBT) || saysAny(t, TO_GET) || (saysAny(t, TO_GIVE) && owedToTheShop(t)) || owedToMe(t) || saysAny(t, OWES_ME)

    /**
     * Arabic "who owes me" (24 Nov 2026, BC-256): "من يدين لي", "مدين لي", "يدينون لي" — the debtors' side said with the debt's verb, which
     * [DEBT]'s noun ("دين", "ديون") does not match as a whole word. Beside a [MOST] word it is who owes the shop most.
     */
    /** The sentence says "who owes me" with Arabic's verb of debt ([OWES_ME]); Hudhud keeps a reading made from it against the sense. */
    fun owesMeSaid(heard: String): Boolean = saysAny(normalise(heard), OWES_ME)

    private val OWES_ME = listOf("يدين لي", "يدينون لي", "مدين لي", "مدينون لي", "مدينين لي", "مديون لي", "يديننا", "يدينوننا")

    /** A supplier's side with nobody else giving and nothing to take: what is owed there is the shop's to pay. */
    private fun supplierOwed(t: String): Boolean =
        saysAny(t, SUPPLIER_SIDE) && !saysAny(t, OTHERS_GIVE) && !saysAny(t, TO_GET) && !otherDoer(t)

    /** "Not paid" said of whom the shop pays: a supplier, or "kis ko" with no other doer and no word of credit given. */
    private fun unpaidByTheShop(t: String): Boolean =
        supplierOwed(t) || (saysAny(t, TO_WHOM) && !otherDoer(t) && !saysAny(t, OTHERS_GIVE) && !saysAny(t, DEBT))

    /** "To whom", the one who receives (Urdu, Roman Urdu, Hindi). */
    private val TO_WHOM = listOf("kis ko", "kisko", "kis kis ko", "kin ko", "kinko", "کس کو", "کس کس کو", "किस को", "किसको", "किन को")

    /**
     * The sentence says the money is the shop's to pay (16 Nov 2026, audit T10): a supplier owed, the shop's own debt, or
     * "not paid" to whom. Hudhud keeps such a reading on the payable side whatever the sense learnt (his `decide`).
     */
    fun payableSaid(heard: String): Boolean {
        val t = normalise(heard)
        if (t.isEmpty()) return false
        return supplierOwed(t) || (saysAny(t, MY_DEBT) && !owedToTheShop(t)) || (saysAny(t, NOT_PAID) && unpaidByTheShop(t))
    }

    /** Words of paying, owing and money that make a supplier sentence a question of the shop's payables and not of its suppliers. */
    private val PAY_CUES = listOf("payable", "payables", "payment", "پول", "bayar", "dibayar", "membayar", "utang", "paisa", "paise", "pese", "raqam", "پیسے", "پیسہ", "رقم",
        "پے ایبل", "بدهم", "بدهیم", "بدهکارم", "بدهکار", "بدهی", "টাকা", "পয়সা", "बकाया", "पैसे", "पैसा", "ادا", "ادائیگی", "أدفع", "ادفع", "أسدد", "اسدد", "دفع", "سدد", "دینا", "دینے", "دیں", "ڏيڻا", "ڏيڻو")

    /** Money coming FROM a supplier or a company ("company se scheme ka paisa kab aayega") is the shop's to get, never its payable. */
    private val COMING_IN = listOf("aayega", "aaega", "ayega", "aayegi", "aaye", "aane", "milega", "milegi", "mile", "ملے گا", "ملے گی", "ملے", "آئے گا", "آئے گی", "آئے", "آنے",
        "आएगा", "आएगी", "मिलेगा", "मिलेगी", "receive", "get back", "refund", "ڏيندا", "ملندا")

    /**
     * A supplier's money asked outright (25 Nov 2026, BC-240): [payableSaid] with a word of paying, owing or money beside it. "Kitne suppliers hain" says
     * a supplier and nothing of money and stays a count; "چقدر باید به تامین کننده ها بدهم", "pemasok mana yang belum saya bayar", Bengali
     * "কোন সাপ্লায়ারকে টাকা দেওয়া হয়নি" and "سپلائر پے ایبل کتنا ہے" are the shop's payables whichever shuffle the sense was learnt with.
     */
    fun payableAsked(heard: String): Boolean {
        val t = normalise(heard)
        return payableSaid(heard) && saysAny(t, PAY_CUES) && !saysAny(t, COMING_IN)
    }

    /** The doer's mark in Urdu, Hindi and Roman Urdu ("ne"), normalised. */
    private val NE_WORDS = setOf("ne", "نے", "ने")

    /** The shop's own "I" and "we" before that mark ("main ne", "hum ne"), normalised: these give, the shop is the doer. */
    private val SHOP_DOERS = setOf("main", "mai", "mein", "me", "mujh", "hum", "ham", "میں", "ہم", "मैं", "मै", "हम")

    /**
     * The sentence says outright which side owes (16 Nov 2026, audit T11): a word of giving or getting, the shop's own debt, or
     * somebody else who gives. A bare word of debt ("ديون", "baqaya") does not: its side is the default, and Hudhud's sense may
     * still turn it ("كم علي من ديون" is the shop's debt).
     */
    fun sideSaid(heard: String): Boolean {
        val t = normalise(heard)
        return saysAny(t, TO_GIVE) || saysAny(t, TO_GET) || saysAny(t, MY_DEBT) || owedToTheShop(t) || payDue(t)
    }

    /**
     * Which way the money runs is not said (D15, Marhala 1, 25 Nov 2026; BC-318, BC-319): Hudhud asks "lene hain ya dene?" first.
     *
     * - A bare loan (BC-318): "Kitna qarz baqi hai", "qarz kitna hai", "کتنا قرض باقی ہے", "कितना क़र्ज़ बाकी है", "how much loan is
     *   left". A loan runs both ways in a shop (the owner's own lists: "Mera qarz kitna hai" is his to give, "Aaj kis ne mera qarz wapas
     *   kiya" money he lent) — unlike "udhaar", "baqaya", Arabic "ديون" or Indonesian "utang", the shop's credit, whose default the
     *   reviewers held as the shop's to get. Qarz-e-Hasna is its own question, and a debtor ("qarzdar") says his side.
     * - Whose money or payment is pending or late (BC-319): "Kis ki payment late ho gayi", "Kis ka maal ka paisa baqi hai", "whose
     *   payment is pending" — "kis ki payment" is the supplier's as often as the customer's ("supplier ki payment" is paid TO him).
     *   "Kaun si payment late hai" (which payment) and "kis ka paisa phansa hua hai" (stuck: the shop's money held outside) say no
     *   party or say the side, and keep their reading.
     *
     * Never when the sentence says the side: a word of giving or getting ([sideSaid]), the shop's payables ([payableSaid]), the shop's
     * credit, a customer or a supplier, a collection, money stuck, most or least, a payment denied, or "my share".
     */
    fun sideUnsure(heard: String): Boolean {
        val t = normalise(heard)
        if (t.isEmpty() || sideSaid(heard) || payableSaid(heard) || saysAny(t, OWES_ME) || saysAny(t, SIDE_CUES) || saysAny(t, SUPPLIER_SIDE)) return false
        if (saysAny(t, MOST) || saysAny(t, LEAST) || moveDenied(t) || saysAny(t, MY_SHARE) || saysAny(t, OTHERS_HOLD)) return false
        val loan = saysAny(t, LOAN) && !saysAny(t, LOAN_GOOD)
        val whosePending = saysAny(t, WHOSE_MONEY) && saysAny(t, MONEY_THING) && (saysAny(t, PENDING) || saysAny(t, LATE))
        return loan || whosePending
    }

    /** "Late" in the nine languages: a side asked of lateness offers who has not paid, not the total ([Hudhud]'s side ask). */
    fun saysLate(heard: String): Boolean = saysAny(normalise(heard), LATE)

    /** A loan, a word that runs both ways ([sideUnsure]). Sindhi, Bengali, Farsi, Arabic: native read. */
    private val LOAN = listOf("qarz", "qarza", "qarze", "qarzay", "karz", "karza", "karze", "loan", "loans", "قرض", "قرضہ", "قرضے",
        "क़र्ज़", "कर्ज", "कर्जा", "कर्ज़", "ঋণ", "وام", "pinjaman")

    /** Qarz-e-Hasna, the interest-free loan: its own question ([Hudhud.Ask.Qarz]). */
    private val LOAN_GOOD = listOf("hasna", "hasana", "hassana", "حسنہ", "حسنه", "الحسنة", "الحسنه", "हसना", "হাসানা", "interest free", "tanpa bunga")

    /** Words that say the side outright, beside the side lists: the shop's credit, a customer, a collection, money stuck outside. */
    private val SIDE_CUES = listOf(
        "udhar", "udhaar", "udhari", "ادھار", "उधार", "اڌار", "ধার", "نسیه", "آجل", "بالآجل",
        "customer", "customers", "gahak", "gahakon", "grahak", "grahakon", "گاہک", "گاہکوں", "ग्राहक", "ग्राहकों", "گراهڪ", "گراهڪن",
        "গ্রাহক", "عميل", "العميل", "العملاء", "مشتری", "مشتریان", "pelanggan",
        "wusool", "wasool", "wusooli", "wasooli", "wasuli", "collection", "collect", "وصول", "وصولی", "वसूल", "वसूली", "tagihan",
        "phansa", "phansi", "phanse", "atka", "atki", "atke", "stuck", "پھنسا", "پھنسی", "اٹکا", "اٹکی", "फंसा", "फंसी", "अटका", "अटकी",
        "ڦاٿل", "আটকে", "گیر", "tertahan", "macet", "عالق", "عالقة"
    )

    /** "Whose" (Arabic says it after the money: "دفعة من"). */
    private val WHOSE_MONEY = listOf("kis ka", "kis ki", "kis ke", "kiska", "kiski", "kiske", "whose", "کس کا", "کس کی", "کس کے",
        "किसका", "किसकी", "किसके", "किस का", "किस की", "किस के", "ڪنهن جو", "ڪنهن جي", "ڪنهن جا", "কার", "چه کسی", "siapa",
        "دفعة من", "دفعات من", "مبلغ من", "فلوس من")

    /** Money or a payment, as a thing someone has ([sideUnsure]). */
    private val MONEY_THING = listOf("payment", "payments", "paisa", "paise", "pese", "paisay", "raqam", "rakam", "money", "amount",
        "پیمنٹ", "پیسہ", "پیسے", "رقم", "ادائیگی", "पेमेंट", "पैसा", "पैसे", "रकम", "भुगतान", "پيمينٽ", "پئسا", "پئسو", "ادائيگي",
        "পেমেন্ট", "টাকা", "دفعة", "دفعات", "مبلغ", "فلوس", "پرداخت", "پول", "pembayaran", "uang", "bayaran")

    /** Still to come or late. */
    private val PENDING = listOf("baqi", "baaqi", "baki", "baaki", "pending", "remaining", "outstanding", "left", "باقی", "बाकी",
        "باقي", "বাকি", "বকেয়া", "مانده", "المتبقي", "متبقي", "sisa", "tersisa", "tertunda")

    private val LATE = listOf("late", "der", "dair", "deir", "overdue", "لیٹ", "دیر", "लेट", "देर", "دير", "দেরি", "تأخر", "تأخرت",
        "متأخر", "متأخرة", "المتأخرة", "terlambat", "telat")

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
        // "What customers still have to pay me" (15 Nov 2026, review O): the shop is the one paid.
        "pay me", "pay us",
        // "Somebody", "the one who" (14 Nov 2026): "kisi ne mujhe paise dene hain", "jisne sabse zyada dene hain".
        "kisi ne", "kisine", "jis ne", "jisne", "kisi kisi ne",
        "کس نے", "کس کس نے", "لوگوں نے", "انہوں نے", "اس نے", "گاہک نے", "किस ने", "किसने", "लोगों ने", "उसने", "ग्राहक ने",
        "کسی نے", "جس نے", "किसी ने", "जिसने", "जिस ने",
        "কে দেবে", "কারা দেবে", "আমাকে দেবে", "কে আমার কাছে", "من يدين لي", "يدينون لي", "به من بدهکار", "siapa yang berutang",
        // 16 Nov 2026 (audit T11): Farsi "the customers' debt (to the shop)" — بدهی alone is the shop's own.
        "بدهی مشتری", "بدهی مشتریان", "بدهی مشتریها", "به مغازه بدهکار",
        // 24 Nov 2026 (A1): Farsi "the debt to me" — the shop is the one paid.
        "بدهی به من", "بدهی را به من", "بدهکار به من",
        // 16 Nov 2026 (audit T10): "payment dene wala" is the one who pays the shop, not what the shop owes.
        // Only with "who": "dene wali raqam" is the sum the shop has to give.
        "dene wala kaun", "dene wale kaun", "dene waale kaun", "dene wala kon", "dene wale kon", "دینے والا کون", "دینے والے کون",
        "देने वाला कौन", "देने वाले कौन",
        // Farsi "who is" with a debt word: "بیشترین بدهکار کیست" is the shop's biggest debtor (review sets D and O).
        "کیست"
    )

    private val MY_DEBT = listOf(
        "mera qarz", "meri qarz", "mere qarz", "mera karz", "mere zimme", "mere zime", "mujh par", "mujhpar", "humara qarz",
        "my debt", "i owe", "we owe",
        // 16 Nov 2026 (audit T10): "kis kis ka paisa mere paas hai", whose money the shop holds.
        "paisa mere paas", "paise mere paas", "raqam mere paas", "پیسے میرے پاس", "پیسہ میرے پاس", "पैसा मेरे पास", "पैसे मेरे पास", "میرا قرض", "میرے ذمے", "مجھ پر", "ہمارا قرض", "मेरा कर्ज", "मेरा क़र्ज़", "मेरे जिम्मे",
        "मुझ पर", "আমার ঋণ", "আমার দেনা", "ديني", "علي دين", "بدهی من", "منهنجو قرض", "utang saya", "hutang saya",
        // 16 Nov 2026 (audit T11): Indonesian's other "I/we" and the joined "my"; Arabic "on us".
        "utang aku", "hutang aku", "utangku", "hutangku", "utang kami", "hutang kami", "علينا",
        // 24 Nov 2026 (A1): Arabic "I owe" (أدين / ادين) and Indonesian "to whom do I owe" (the payable side).
        "أدين", "ادين", "berutang kepada siapa", "berhutang kepada siapa", "utang ke siapa", "hutang ke siapa",
        // Marhala 1 (25 Nov 2026, BC-318): "on me" said as "upon me" ("mere upar kitna qarza hai", the held-out set's own): a loan with
        // its side said, never one to ask.
        "mere upar", "mere oopar", "meray upar", "hamare upar", "humare upar", "میرے اوپر", "ہمارے اوپر", "मेरे ऊपर", "हमारे ऊपर"
    )

    /** A supplier's side of the book: what is owed there is the shop's to pay. */
    private val SUPPLIER_SIDE = listOf(
        "supplier", "suppliers", "company", "kampani", "maal wale", "maal wala", "distributor", "wholesaler",
        "سپلائر", "کمپنی", "مال والے", "सप्लायर", "कंपनी", "সাপ্লায়ার", "কোম্পানি", "المورد", "الموردين", "تامین کننده",
        "تأمین‌کننده", "سپلائر", "pemasok", "supplier",
        // 24 Nov 2026 (A1): plural supplier words in Urdu/Sindhi script and Arabic's "suppliers" with the article.
        "سپلائرز", "سپلائرون", "سپلائرن", "سپلائرس", "کمپنیاں", "کمپنیوں", "मालवाले", "सप्लायर्स"
    )

    /** The first of the recogniser's guesses that is a question this app knows. */
    fun classify(candidates: List<String>): Pair<String, Kind>? =
        candidates.firstNotNullOfOrNull { c -> classify(c)?.let { c to it } }
}
