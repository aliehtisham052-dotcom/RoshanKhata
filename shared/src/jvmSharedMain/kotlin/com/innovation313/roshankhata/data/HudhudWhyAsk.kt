package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.TimeZone

/** The why reader's word lists are the strict readers' common [StrictWords]. */
private typealias WhyWords = StrictWords

/**
 * "Why" about a figure of the book (16 Nov 2026, audit T8, the owner's decision H-10: by rules, never guessed): "is mahine sale kam
 * kyun hui", "profit kyun gira", "why are expenses up", "udhaar kyun barh gaya". These were answered with the figure alone, as if the
 * "why" had not been said.
 *
 * **What the answer may say.** Only facts the book holds: this span's figure beside the span before it (the same days of it while
 * the span is still running, [spans]), the signed difference, and the parts that moved most ([movers]: customers, goods, expense
 * categories, products' profit, sale lines with no cost price). Never a cause the book cannot show — the weather, the market, a
 * competitor — and never a cause guessed from the parts: the card says what moved, the owner knows why.
 *
 * **A strict reader, like [HudhudLook] and [HudhudNote] (lesson 121).** A sentence is read only when it says "why" (or "the
 * reason"), names exactly one figure the book has — the sales, the profit, the expenses, the money that came in, the customers'
 * credit — and every other word is a word of change ("kam", "barh", "down", "up"), a span of days, or a little word that asks
 * nothing. Any other word — a name, a figure written in digits, a product, a second figure, a span he has no window for — and it
 * says nothing: the sentence is read as before. It never touches [HudhudMind], the bank or the sense; [Hudhud.decide] never lets
 * the sense turn its reading.
 */
object HudhudWhyAsk {

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 16

    /** "Why", and the reason asked as a noun ("wajah", "reason", "sabab"): one of them must be said. */
    private val WHY = WhyWords(
        "why kyun kyon kyu kyoon kiyon kiun kiyun wajah waja wajeh wajha sabab reason reasons cause",
        "کیوں کیونکر وجہ وجوہات سبب",
        "ڇو ڇالاءِ ڇالاء سبب ڪارڻ",
        "لماذا لم سبب السبب اسباب",
        "چرا دلیل علت",
        "क्यों क्यूं क्यो वजह कारण",
        "কেন কারণ",
        "kenapa mengapa sebab alasan penyebab"
    )

    /** Whether [w] (as [VoiceQuestion.normalise] spells it) is a word for "why" or "the reason": never somebody's name (BC-110). */
    internal fun isWhyWord(w: String): Boolean = WHY.has(w)

    /** The sales. */
    private val SALES = WhyWords(
        "sale sales bikri bikree bikry selling sell sold farokht farokhat",
        "سیل سیلز بکری فروخت",
        "وڪرو وڪري سيل",
        "مبيعات بيع البيع",
        "فروش",
        "बिक्री सेल बिकरी",
        "বিক্রি বিক্রয় সেল",
        "penjualan jualan"
    )

    /** The profit. */
    private val PROFIT = WhyWords(
        "profit profits munafa munafah munafe munafay nafa faida fayda faaida margin",
        "منافع نفع فائدہ مارجن",
        "منافعو نفعو فائدو",
        "ربح أرباح ارباح الربح",
        "سود",
        "मुनाफा मुनाफ़ा मुनाफे लाभ फायदा",
        "লাভ মুনাফা",
        "laba untung keuntungan"
    )

    /** The expenses: the cashbook's money out and the staff's pay, Profit's own rule. */
    private val EXPENSES = WhyWords(
        "expense expenses expenditure spending kharcha kharch kharche kharchay kharchey",
        "خرچ خرچہ خرچے خرچا اخراجات",
        "خرچ خرچو",
        "مصاريف مصروفات نفقات",
        "هزینه هزینہ هزینہ ها مخارج خرج",
        "खर्च खर्चा खर्चे",
        "খরচ ব্যয়",
        "pengeluaran biaya"
    )

    /** The money that came in from customers (Insights' "received"). */
    private val COLLECTED = WhyWords(
        "wasooli wusooli wasuli wasoli collection collections recovery recoveries",
        "وصولی وصولیاں",
        "وصولي",
        "تحصيل التحصيل",
        "وصول دریافتی",
        "वसूली",
        "আদায়",
        "penagihan"
    )

    /** The customers' credit, what they owe the shop. */
    private val DUES = WhyWords(
        "udhaar udhar udhaaar udhari baqaya bakaya baqaaya dues receivable receivables",
        "ادھار بقایا بقایاجات",
        "اڌار",
        "مستحقات",
        "طلب طلبها مطالبات",
        "उधार उधारी बकाया",
        "বাকি বকেয়া",
        "piutang"
    )

    /** It went down. */
    private val DOWN = WhyWords(
        "down drop dropped drops dropping fell fall falling fallen low lower less lesser decreased decrease decreasing declined decline " +
            "reduced shrank shrunk kam km kum ghat ghata ghati ghate ghatt gir gira giri gire gir gayi girna neeche niche kami",
        "کم گھٹ گھٹا گھٹی گھٹے گرا گری گرے گر نیچے کمی",
        "گهٽ گھٽ گهٽيو گهٽي گهٽجي هيٺ",
        "انخفض انخفضت قل قلت نقص نقصت تراجع تراجعت هبط هبطت اقل أقل",
        "کم کاهش افت پایین",
        "कम घट घटा घटी घटे गिर गिरा गिरी गिरे नीचे कमी",
        "কম কমল কমলো কমেছে কমে কমছে পড়ল পড়েছে",
        "turun menurun berkurang anjlok sedikit rendah"
    )

    /** It went up. */
    private val UP = WhyWords(
        "up rose rise rising risen high higher more increased increase increasing grew grow growing grown jumped " +
            "zyada ziada ziyada zyda jyada barh barha barhi barhe badh badha badhi badhe oopar upar uper",
        "زیادہ بڑھ بڑھا بڑھی بڑھے اوپر",
        "وڌ وڌيو وڌي وڌيا وڌيڪ مٿي",
        "ارتفع ارتفعت زاد زادت ازداد ازدادت اكثر أكثر",
        "زیاد بیشتر افزایش بالا",
        "ज्यादा ज़्यादा बढ़ बढ़ा बढ़ी बढ़े ऊपर",
        "বেশি বাড়ল বাড়লো বেড়েছে বেড়ে বাড়ছে",
        "naik meningkat bertambah banyak tinggi"
    )

    /** "Not": "sale kyun nahi barhi" — the sales that did not go up are asked as down. */
    private val NOT = WhyWords(
        "not nahi nahin nai na didnt didn't doesnt",
        "نہیں نہ",
        "نه ناهي",
        "لم لا",
        "نه نمی نشد",
        "नहीं न",
        "না নি",
        "tidak tak belum"
    )

    /** Little words that ask nothing: the verbs of being and becoming, "my", "this", "so much", "tell me"… */
    private val LITTLE = WhyWords(
        // English
        "the a an my our is are was were did do does has have had been be being so much very this that it its in on of for to " +
            "from than compared what whats why's tell me us please explain show went gone go going get got become became suddenly " +
            "really lot bit little and",
        // Roman Urdu and Hindi
        "is iss us ki ka ke ko se mein me main mai meri mera mere hamari hamara hamare apni apna apne hui hua hue huwa hoi ho hoga " +
            "hogi hogaya hogayi gayi gai gaya gaye gya gyi rahi raha rahe rhi rha thi tha the hai hain hy he itni itna itne " +
            "bohat bahut bht boht kaafi kafi kuch thoda thora ab abhi batao bataiye bata btao samjhao samjhaiye dikhao kya kia " +
            "hone hona tak par pe wali wala wale ye yeh wo woh sab mujhe humein hamein zara ji please plz lagta laga lagi",
        // Urdu
        "اس میں کی کا کے کو سے میری میرا میرے ہماری ہمارا ہمارے اپنی اپنا ہوئی ہوا ہوئے ہو ہوگا ہوگی گئی گیا گئے رہی رہا رہے " +
            "تھی تھا تھے ہے ہیں اتنی اتنا اتنے بہت کافی کچھ تھوڑا اب ابھی بتاؤ بتاو بتائیں بتائیے سمجھاؤ دکھاؤ کیا ہونے ہونا تک پر " +
            "یہ وہ سب مجھے ہمیں ذرا جی والی والا",
        // Sindhi
        "هن ۾ جو جي جا کي کان منهنجو منهنجي اسان ٿيو ٿي ٿيا ويو وئي ويا رهيو آهي آهن هو هئي ايترو ايتري تمام گهڻو ڪجهه هاڻي " +
            "ٻڌايو ٻڌاءِ ڇا هي اهو سڀ",
        // Arabic
        "هذا هذه في من على عن الى إلى لي قل اخبرني أخبرني اشرح كثيرا جدا قد ما هل كان كانت لقد هو هي",
        // Farsi
        "این آن در از به را که شد شده است بود بوده چه ما من خیلی زیاد بگو توضیح بده هم مان",
        // Hindi
        "इस उस में की का के को से मेरी मेरा मेरे हमारी हमारा हमारे अपनी अपना हुई हुआ हुए हो होगा गई गया गए रही रहा रहे थी था थे है " +
            "हैं इतनी इतना इतने बहुत काफी कुछ थोड़ा अब अभी बताओ बताइए समझाओ क्या होने होना तक पर यह ये वह वो सब मुझे हमें ज़रा जी",
        // Bengali
        "এই এ এর আমার আমাদের হল হলো হয়েছে হয়ে গেল গেছে ছিল আছে কি কী এত এতো অনেক কিছু এখন বলো বলুন বুঝিয়ে দাও টা টি",
        // Indonesian
        "ini itu di ke dari yang saya kami kita jadi menjadi sangat begitu sih ya tolong jelaskan beritahu kok bisa apa nya"
    )

    /** A figure of the book the sentence may ask "why" about, in the order a tie is never broken (two figures: not read). */
    private val TOPICS: List<Pair<Hudhud.WhyOf, WhyWords>> = listOf(
        Hudhud.WhyOf.SALES to SALES, Hudhud.WhyOf.PROFIT to PROFIT, Hudhud.WhyOf.EXPENSES to EXPENSES,
        Hudhud.WhyOf.COLLECTED to COLLECTED, Hudhud.WhyOf.DUES to DUES
    )

    /** Phrases said as two words that mean one, applied before the words are read. Longer first. */
    private val PHRASES: List<Pair<String, String>> = listOf(
        "kis liye" to "kyun", "kis wajah se" to "kyun", "kis wajah" to "kyun", "how come" to "why", "what is the reason" to "why",
        "کس لیے" to "کیوں", "کس وجہ سے" to "کیوں", "किस लिए" to "क्यों", "किस वजह से" to "क्यों", "ڇا لاءِ" to "ڇو",
        "برای چه" to "چرا", "به چه دلیل" to "چرا", "কী কারণে" to "কেন", "apa sebabnya" to "kenapa", "apa alasannya" to "kenapa",
        "money in" to "wasooli", "money came in" to "wasooli", "payments received" to "wasooli", "uang masuk" to "penagihan",
        "paise aaye" to "wasooli", "paise aane" to "wasooli", "پیسے آئے" to "وصولی", "पैसे आए" to "वसूली",
        "gone down" to "down", "went down" to "down", "gone up" to "up", "went up" to "up",
        "پایین آمد" to "کاهش", "بالا رفت" to "افزایش"
    ).map { (from, to) -> " ${VoiceQuestion.normalise(from)} " to " ${VoiceQuestion.normalise(to)} " }.sortedByDescending { it.first.length }

    /**
     * What [heard] asks "why" about, or null: nothing is guessed. [isName]: a word the book knows as a customer's, a supplier's
     * or a product's name (then it is not read: "Ali ki sale kyun kam hui" is about Ali, not the shop). [calling]: a word that
     * only calls him ("Hudhud", "bhai").
     */
    fun read(heard: String, isName: (String) -> Boolean = { false }, calling: (String) -> Boolean = { false }): Hudhud.Ask.Why? {
        val first = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (first.isEmpty() || first.size > MAX_WORDS + 4) return null
        // A figure in any script is an amount, a date or a count: another sentence.
        if (first.any { w -> w.any { it.isDigit() } }) return null
        var text = " " + first.joinToString(" ") + " "
        for ((from, to) in PHRASES) if (text.contains(from)) text = text.replace(from, to)
        val said = text.trim().split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > MAX_WORDS) return null
        // The cheap question first: no "why", no answer (every question comes through here).
        if (said.none { WHY.has(it) }) return null
        val joined = said.joinToString(" ")
        // A span he has no window for ("pichle 3 mahine", "parson") is never read as another; the whole book has nothing before it.
        if (VoiceQuestion.unsupportedSpan(heard)) return null
        val window = VoiceQuestion.windowOf(heard) ?: VoiceQuestion.Window.THIS_MONTH
        if (window == VoiceQuestion.Window.ALL_TIME) return null
        val spanWords = VoiceQuestion.spanWordsIn(joined)
        val topics = LinkedHashSet<Hudhud.WhyOf>()
        var down = false
        var up = false
        var not = false
        for (w in said) {
            if (calling(w)) continue
            if (isName(w)) return null
            var ok = false
            for ((of, list) in TOPICS) if (list.has(w)) { topics += of; ok = true }
            if (WHY.has(w)) ok = true
            if (DOWN.has(w)) { down = true; ok = true }
            if (UP.has(w)) { up = true; ok = true }
            if (NOT.has(w)) { not = true; ok = true }
            if (w in spanWords || LITTLE.has(w)) ok = true
            if (!ok) return null
        }
        if (topics.size != 1) return null
        // "Kam" and "zyada" together say nothing of the way it went; "not" turns the way asked ("kyun nahi barhi" asks down).
        val asked: Boolean? = when {
            down && up -> null
            down -> !not
            up -> not
            not -> true
            else -> null
        }
        return Hudhud.Ask.Why(topics.first(), window, asked)
    }

    // ------------------------------------------------------------------------------------------------ the two spans

    /**
     * The span asked and the span before it. [now] is [Hudhud.span] of [window]. While the span is still running (today, this week,
     * this month, this year, the six months up to today) the span before it is cut to the same length of time (Insights' own rule,
     * 8 Nov 2026 review M13: ten days of this month against all thirty of the last read "67% less" every month); a span already
     * over is set beside the whole span before it. [soFar]: the span before was cut. Null for the whole book (nothing before it).
     */
    data class Spans(val now: Pair<Long, Long>, val before: Pair<Long, Long>, val soFar: Boolean)

    fun spans(window: VoiceQuestion.Window, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Spans? {
        if (window == VoiceQuestion.Window.ALL_TIME) return null
        val cur = Hudhud.span(window, now, tz)
        val start = cur.first
        fun back(field: Int, by: Int): Long = Calendar.getInstance(tz).apply { timeInMillis = start; add(field, -by) }.timeInMillis
        val prevStart = when (window) {
            VoiceQuestion.Window.TODAY, VoiceQuestion.Window.YESTERDAY -> back(Calendar.DAY_OF_MONTH, 1)
            VoiceQuestion.Window.THIS_WEEK, VoiceQuestion.Window.LAST_WEEK -> back(Calendar.DAY_OF_MONTH, 7)
            VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_MONTH -> back(Calendar.MONTH, 1)
            VoiceQuestion.Window.LAST_6_MONTHS -> back(Calendar.MONTH, 6)
            VoiceQuestion.Window.THIS_YEAR, VoiceQuestion.Window.LAST_YEAR -> back(Calendar.YEAR, 1)
            VoiceQuestion.Window.ALL_TIME -> return null
        }
        val running = cur.second > now
        return if (running) {
            Spans(start to (now + 1), prevStart to minOf(prevStart + (now + 1 - start), start), soFar = true)
        } else {
            Spans(cur, prevStart to start, soFar = false)
        }
    }

    // ------------------------------------------------------------------------------------------------ what moved

    /** One part of a figure in the two spans: a customer, a product, an expense category. */
    data class Part(val name: String, val now: Double, val before: Double) {
        val change: Double get() = now - before
    }

    /**
     * The parts that moved most the way the whole moved ([whole] is the whole figure's change, now less before): the biggest
     * falls when it fell, the biggest rises when it rose, at most [limit], the biggest first. A part that did not move is never
     * listed, and nothing is listed when the whole did not move. A part present in only one span counts as nought in the other.
     */
    fun movers(
        now: Map<String, Double>, before: Map<String, Double>, whole: Double, limit: Int = 3,
        /** Nought in the shop's money: the app passes [Money.isZero] (half the smallest coin of the shop's currency). */
        isZero: (Double) -> Boolean = { kotlin.math.abs(it) < 1e-9 }
    ): List<Part> {
        if (isZero(whole)) return emptyList()
        val rising = whole > 0
        return (now.keys + before.keys).asSequence()
            .map { Part(it, now[it] ?: 0.0, before[it] ?: 0.0) }
            .filter { !isZero(it.change) && (it.change > 0) == rising }
            .sortedByDescending { kotlin.math.abs(it.change) }
            .take(limit)
            .toList()
    }

    /** Several rows of one part (a product sold in two units, a category written twice) added together, by their own name. */
    fun summed(rows: List<Pair<String, Double>>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for ((name, v) in rows) out[name] = (out[name] ?: 0.0) + v
        return out
    }
}
