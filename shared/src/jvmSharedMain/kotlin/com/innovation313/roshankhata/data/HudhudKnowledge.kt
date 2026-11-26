package com.innovation313.roshankhata.data

/**
 * Business knowledge, lane B of the master plan (Marhala 9, D16; the owner moved it before the release on 26 Nov 2026): what a
 * shop's own words mean — margin, markup, contribution, break-even, discount against markup, and the book's own figures (to
 * get, to give, the net balance, concentration) — each told from a knowledge atom ([KnowledgeAtom], shared/hudhud/knowledge/
 * atoms.tsv) with its source and the day the source was read, and a worked margin or markup from the two figures a sentence
 * itself gives ("800 ki cheez 1000 mein bechi, margin kitna?"), worked by the Formula Book's own functions ([PriceMath],
 * F65–F69) and said to be the sentence's figures, never the book's.
 *
 * **The limits come first** ([limitOf], plan v1 "Never" 6, 7, 8): a pesticide's dose, its mixing or the days before harvest; a
 * medicine's dose or medical advice; a tax rate; a religious ruling. Such a sentence is refused calmly, naming who to ask (the
 * product's approved label and the agriculture department; a doctor or a pharmacist; the FBR, with the day it was read; a
 * qualified Hanafi scholar). The limits are read by cues, not by every word (a refusal must catch the sentence however it is
 * said), and they keep away from the book: a word of the book's own (khata, bill, stock, sale…) beside the cue, or a product
 * named with a tax word (the product's own tax rate is the book's), and the sentence is read as before — "Urea ka rate" is the
 * product's price.
 *
 * **The knowledge itself is a strict reader** (lesson 121, the [HudhudWhyAsk] pattern): every word must be the topic's own, a
 * cue (what is, how to work out, the difference), a figure with its role (the cost, the sale price) or a little word that asks
 * nothing. A name of the book, a span of days, a possessive beside a figure the book has ("mera margin"), or any other word and
 * it says nothing: the sentence is read as before. It never touches [HudhudMind], the bank or the sense; [Hudhud.decide] never
 * lets the sense turn its reading.
 */
enum class KnowKind {
    /** An atom a person has checked against its source ([KnowledgeAtom.reviewed]). */
    VERIFIED,
    /** An atom written from its source and not yet checked by a person: said so on the card. */
    GENERAL_GUIDANCE,
    /** A margin or markup worked out from the sentence's own two figures by the Formula Book. */
    CALCULATED,
    /** A worked figure whose cost and sale price the sentence did not say apart: asked, never guessed. */
    NEEDS_CLARIFICATION,
    /** One of the plan's limits (dose, medicine, tax rate, ruling): refused, naming who to ask. */
    UNSUPPORTED
}

/** Which limit a refused sentence met ([KnowKind.UNSUPPORTED]). */
enum class KnowLimit { PESTICIDE, MEDICINE, DOSE, TAX_RATE, RULING }

/** What a worked example asks for ([KnowKind.CALCULATED]). */
enum class KnowMeasure { MARGIN, MARKUP, PROFIT }

/**
 * One knowledge atom (a row of shared/hudhud/knowledge/atoms.tsv): its [id] (K1xx: the numbers of the book, K2xx: price and
 * profit), [module], the [concept] in English, the word keys of its [title], [definition], [example] and [exception] (null:
 * none), the Formula Book ids it rests on ([formulas]), its [sources] (by name, with a tier 1–5 each: the addresses stay in
 * atoms.tsv, where scripts/check_knowledge_atoms.py holds them, so the app holds no address it could be thought to contact) and
 * the day they were read, and who reviewed it ("pending" until a person has: then the card says "general guidance", never "verified").
 */
data class KnowledgeAtom(
    val id: String,
    val module: Int,
    val concept: String,
    val title: String,
    val definition: String,
    val example: String?,
    val exception: String?,
    val formulas: List<String>,
    val sources: List<String>,
    val tiers: List<Int>,
    val dateRead: String,
    val reviewedBy: String
) {
    /** A person has read it against its source ("pending" is nobody yet). */
    val reviewed: Boolean get() = reviewedBy != PENDING

    /** The card's kind for this atom. */
    val kind: KnowKind get() = if (reviewed) KnowKind.VERIFIED else KnowKind.GENERAL_GUIDANCE

    companion object {
        const val PENDING = "pending"

        /** The columns of atoms.tsv, in order. */
        val COLUMNS = listOf(
            "id", "module", "concept", "title", "definition", "example", "exception", "formula_id", "source", "source_url",
            "source_tier", "date_read", "reviewed_by"
        )

        /**
         * The loader: atoms.tsv's lines (a '#' line is a comment; the first other line is the header) as atoms. Several sources
         * in one atom are written " | " between them in source, source_url and source_tier alike; formulas ";" between them;
         * "-" is none. A malformed row throws: the file is the app's own and a bad one must fail its test, not be passed over.
         */
        fun parse(lines: List<String>): List<KnowledgeAtom> {
            val rows = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            require(rows.isNotEmpty() && rows[0].split('\t') == COLUMNS) { "atoms.tsv: the header is not ${COLUMNS.joinToString(" / ")}" }
            return rows.drop(1).map { line ->
                val c = line.split('\t')
                require(c.size == COLUMNS.size) { "atoms.tsv: ${c.size} columns in: $line" }
                fun opt(s: String) = s.takeIf { it != "-" && it.isNotBlank() }
                fun many(s: String) = s.split(" | ").map { it.trim() }
                KnowledgeAtom(
                    id = c[0], module = c[1].toInt(), concept = c[2], title = c[3], definition = c[4], example = opt(c[5]),
                    exception = opt(c[6]), formulas = opt(c[7])?.split(';')?.map { it.trim() } ?: emptyList(),
                    sources = many(c[8]), tiers = many(c[10]).map { it.toInt() }, dateRead = c[11],
                    reviewedBy = c[12]
                )
            }
        }
    }
}

object HudhudKnowledge {

    /** The atom of [id], from the table the app ships ([HudhudKnowledgeAtoms.ALL]). */
    fun atom(id: String?): KnowledgeAtom? = id?.let { k -> HudhudKnowledgeAtoms.ALL.firstOrNull { it.id == k } }

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 20

    // ------------------------------------------------------------------------------------------------ the limits

    /**
     * Which of the plan's limits [heard] meets, or null. [isPerson]: a word the book knows as a customer's or a supplier's name;
     * [isGoods]: a product's.
     */
    fun limitOf(heard: String, isPerson: (String) -> Boolean = { false }, isGoods: (String) -> Boolean = { false }): KnowLimit? {
        val said = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty()) return null
        // A word of a name is no cue: "Halal Traders ka balance" is a supplier, "Doz Agro" a company.
        val bare = said.filterNot { isPerson(it) || isGoods(it) }.joinToString(" ")
        val goods = said.any(isGoods)
        val person = said.any(isPerson)
        fun has(t: String, list: List<String>) = VoiceQuestion.saysAny(t, list)
        val book = has(bare, BOOK)

        // A religious ruling: a word that asks what is allowed, whatever else the sentence holds.
        // Zakat itself is the app's Zakat page ("zakat kis par farz hai" is the owner's own sentence for it, K0805): the mainstream
        // rule of who owes it is no dispute; only a word that asks what is allowed, or a fatwa, goes to a scholar.
        if (has(bare, RULING)) return KnowLimit.RULING
        if (has(bare, HALAL_HARAM) && !book && VoiceQuestion.classify(heard) == null && FIGURE.find(heard) == null) return KnowLimit.RULING

        // A tax rate: a tax word with a word of rate, never with a product (its own tax rate is the book's) nor a past sum.
        if (has(bare, TAX) && has(bare, RATE) && !goods && !has(bare, TAX_PAST)) return KnowLimit.TAX_RATE

        val agri = has(bare, AGRI)
        val medical = has(bare, MEDICAL) || has(bare, SYMPTOM)
        val either = has(bare, EITHER)
        fun which() = when {
            agri -> KnowLimit.PESTICIDE
            medical -> KnowLimit.MEDICINE
            else -> KnowLimit.DOSE
        }
        // A dose said outright: "dose", "per acre", "waiting period".
        if (has(bare, DOSE_STRONG) && !book) return which()
        // How much to put in, mix or take, of something a field, a person or a product of the shop takes.
        // Putting in and mixing go with a field, a product or "dawai"; taking and giving only with a person's medicine (a customer
        // named beside them is the book's: "Ali ke bachay ko kitna dena hai" is money).
        if (!book && has(bare, QUANTITY)) {
            if (has(bare, APPLY) && (agri || medical || either || goods)) return which()
            if (has(bare, TAKE) && (medical || either) && !person) return if (agri) KnowLimit.PESTICIDE else if (medical) KnowLimit.MEDICINE else KnowLimit.DOSE
        }
        // What to take for an illness: an illness with a medicine or a cure.
        if (has(bare, SYMPTOM) && (has(bare, MEDICAL) || either || has(bare, CURE)) && !book && !person) return KnowLimit.MEDICINE
        return null
    }

    private val FIGURE = Regex("\\p{Nd}")

    /** Words of the book: beside a dose or a rate cue, the sentence is about the shop ("Ali ke khate mein kitna dalna hai"). */
    private val BOOK = listOf(
        "khata", "khate", "khaate", "khaata", "hisaab", "hisab", "bill", "bills", "entry", "entries", "account", "balance", "stock",
        "sale", "sales", "invoice", "udhaar", "udhar", "paise", "paisa", "payment", "raqam", "rupay", "rupees", "rs", "bikri",
        "کھاتہ", "کھاتے", "حساب", "بل", "اسٹاک", "سٹاک", "بکری", "ادھار", "پیسے", "رقم", "روپے", "انٹری",
        "کاتو", "کاتي", "اسٽاڪ", "پئسا", "حساب", "حسابات", "الحساب", "الفاتورة", "فاتورة", "المخزون", "مخزون", "مبيعات", "الرصيد", "رصيد",
        "دفتر", "فاکتور", "موجودی", "فروش", "مانده", "پول",
        "खाता", "खाते", "हिसाब", "बिल", "स्टॉक", "बिक्री", "उधार", "पैसे", "रकम", "रुपये",
        "খাতা", "হিসাব", "বিল", "স্টক", "বিক্রি", "বাকি", "টাকা",
        "buku", "tagihan", "stok", "penjualan", "utang", "piutang", "uang", "saldo"
    )

    /** A word that asks what the faith allows or what a ruling is. */
    private val RULING = listOf(
        "jaiz", "jaaiz", "jayez", "najaiz", "na jaiz", "fatwa", "fatwah", "fatwe", "fatway", "makrooh", "makruh", "sharai",
        "shariat", "shariah", "sharia", "riba", "hukm", "islam mein", "deen mein", "islam me", "islamically", "permissible",
        "جائز", "ناجائز", "فتویٰ", "فتوی", "فتوے", "مکروہ", "شرعی", "شریعت", "ربا", "شرعاً", "شرعا", "اسلام میں",
        "جائز", "فتويٰ", "شرعي", "شريعت", "ربا", "مڪروہ",
        "يجوز", "جائز", "فتوى", "حكم الشرع", "شرعا", "الربا", "مكروه", "شرعي",
        "جایز", "فتوا", "شرعی", "ربا", "حکم شرعی",
        "जायज", "नाजायज", "फतवा", "शरीयत", "शरई", "रिबा", "मकरूह",
        "জায়েজ", "নাজায়েজ", "ফতোয়া", "শরিয়ত", "শরীয়ত", "রিবা", "মাকরুহ",
        "fatwa", "syariah", "syariat", "riba", "makruh", "boleh menurut islam", "hukum islam"
    )

    /** "Halal" and "haram" ask a ruling only when nothing of the book and no figure is beside them. */
    private val HALAL_HARAM = listOf("halal", "haram", "حلال", "حرام", "हलाल", "हराम", "হালাল", "হারাম")

    private val TAX = listOf(
        "tax", "taxes", "gst", "vat", "wht", "fed", "sales tax", "income tax", "withholding", "withholding tax", "advance tax",
        "excise", "customs duty", "ٹیکس", "سیلز ٹیکس", "انکم ٹیکس", "ٽيڪس", "ضريبة", "الضريبة", "ضرائب", "ضريبة القيمة المضافة",
        "مالیات", "مالیات بر ارزش افزوده", "टैक्स", "जीएसटी", "आयकर", "वैट", "ট্যাক্স", "ভ্যাট", "আয়কর", "শুল্ক", "pajak", "ppn", "pph"
    )

    private val RATE = listOf(
        "rate", "rates", "percent", "percentage", "fisad", "feesad", "fesad", "slab", "slabs", "kitna lagta", "kitna lagega",
        "kitna hota", "kitni hoti", "kitna lagana", "kitna lagaun", "kitna lagau", "kitna lagayen", "kitna lagaen", "kitna katna",
        "kitna katta", "ریٹ", "ريٽ", "شرح", "فیصد", "کتنا لگتا", "کتنا لگے گا", "کتنا ہوتا", "کتنا لگانا",
        "نسبة", "نسبہ", "معدل", "نرخ", "درصد", "سيڪڙو", "ڪيترو لڳندو", "दर", "प्रतिशत", "रेट",
        "कितना लगता", "कितना लगेगा", "कितना होता", "হার", "শতাংশ", "রেট", "কত লাগে", "কত লাগবে", "কত শতাংশ", "tarif", "persen",
        "berapa persen"
    )

    /** A sum of tax that went (the book's own figure: "is mahine kitna tax laga", "tax jama hua"), never a rate. */
    private val TAX_PAST = listOf(
        "laga", "lagaya", "lagi", "jama", "diya", "diye", "kata", "kati", "collected", "paid", "charged", "لگا", "جمع", "دیا", "کاٹا",
        "लगा", "जमा", "दिया", "কাটা", "দিলাম", "dibayar", "terkumpul"
    )

    /** A field or a crop: the dose asked is a pesticide's (or a fertiliser's). */
    private val AGRI = listOf(
        "spray", "sprey", "spary", "sprays", "pesticide", "pesticides", "insecticide", "fungicide", "herbicide", "weedicide",
        "zehar", "zeher", "keera", "keeray", "keere", "sundi", "keetnashak", "keet nashak", "fasal", "fasl", "crop", "crops",
        "kapas", "gandum", "chawal", "dhan", "makai", "makki", "ganna", "sabzi", "acre", "acres", "killa", "kille", "kheti", "khet",
        "kheton", "tanki", "tank", "ekar", "سپرے", "زہر", "کیڑے", "سنڈی", "فصل", "کپاس", "گندم", "چاول", "دھان", "مکئی", "گنا",
        "ایکڑ", "کھیت", "ٹینکی", "اسپري", "زهر", "ايڪڙ", "ڪپهه", "ڪڻڪ", "مبيد", "مبيدات", "رش", "الرش", "فدان", "هكتار",
        "هکتار", "زراعة", "المحاصيل", "محاصيل", "سم", "سمپاشی", "آفت کش", "مزرعہ", "छिडकाव", "स्प्रे", "कीटनाशक", "फसल", "एकड",
        "खेत", "गेहूं", "धान", "कपास", "टंकी", "স্প্রে", "কীটনাশক", "ফসল", "একর", "জমি", "ধান", "গম", "ট্যাংক", "semprot",
        "semprotan", "pestisida", "insektisida", "fungisida", "herbisida", "tanaman", "hektar", "sawah", "padi"
    )

    /** A person and a medicine: the dose asked is a medicine's. */
    private val MEDICAL = listOf(
        "medicine", "medicines", "tablet", "tablets", "capsule", "capsules", "syrup", "sirap", "injection", "drip", "goli", "golian",
        "goliyan", "goliyon", "bacha", "bachay", "bachey", "bache", "bachon", "baccha", "child", "kid", "baby", "patient", "mareez",
        "pregnant", "گولی", "گولیاں", "شربت", "سیرپ", "کیپسول", "انجکشن", "ٹیکہ", "بچے", "بچہ", "مریض", "گولي", "ٻار", "مريض",
        "حبوب", "حبة", "شراب", "كبسولة", "حقنة", "طفل", "الطفل", "مريض", "قرص", "کپسول", "آمپول", "بچہ", "کودک", "بیمار", "गोली",
        "गोलियां", "टैबलेट", "सिरप", "कैप्सूल", "इंजेक्शन", "बच्चे", "बच्चा", "मरीज", "ট্যাবলেট", "বড়ি", "সিরাপ", "ক্যাপসুল",
        "ইনজেকশন", "শিশু", "বাচ্চা", "রোগী", "tablet", "sirup", "kapsul", "suntik", "anak", "bayi", "pasien"
    )

    /** An illness. */
    private val SYMPTOM = listOf(
        "bukhar", "bukhaar", "fever", "dard", "pain", "headache", "khansi", "cough", "zukam", "nazla", "flu", "diarrhea",
        "بخار", "درد", "کھانسی", "زکام", "نزلہ", "سور", "کنگھ", "حمى", "ألم", "الم", "صداع", "سعال", "تب", "سرفہ", "बुखार", "दर्द",
        "खांसी", "জ্বর", "ব্যথা", "কাশি", "demam", "batuk", "pusing", "flu"
    )

    /** "Dawai" is a medicine and, in a pesticide shop, a spray: a dose of it is refused, naming both. */
    private val EITHER = listOf("dawa", "dawai", "dawaai", "dava", "davai", "دوا", "دوائی", "دوائي", "دواء", "الدواء", "دارو", "دارویی", "داروی", "दवा", "दवाई", "ওষুধ", "obat")

    private val CURE = listOf("ilaj", "ilaaj", "treatment", "cure", "علاج", "درمان", "इलाज", "চিকিৎসা", "pengobatan", "kya lein", "kya khayen")

    /** A dose said outright. */
    private val DOSE_STRONG = listOf(
        "dose", "doses", "doze", "doz", "dosage", "dosing", "per acre", "fi acre", "per ekar", "per killa", "fi killa", "per tanki",
        "fi tanki", "waiting period", "pre harvest", "harvest interval", "phi", "spray interval", "tank mix", "mixing ratio",
        "ڈوز", "فی ایکڑ", "فی کلہ", "فی ٹینکی", "ڊوز", "في ايڪڙ", "جرعة", "الجرعة", "جرعات", "للفدان", "لكل فدان", "للهكتار",
        "فترة الأمان", "فترة الامان", "دوز", "در هکتار", "هر هکتار", "مقدار مصرف", "डोज", "प्रति एकड", "खुराक", "ডোজ", "প্রতি একর",
        "dosis", "per hektar", "aturan pakai", "aturan minum"
    )

    /** How much. */
    private val QUANTITY = listOf(
        "kitna", "kitni", "kitne", "how much", "how many", "miqdar", "mikdar", "matra", "khurak", "کتنا", "کتنی", "کتنے", "مقدار",
        "خوراک", "ڪيترو", "ڪيتري", "ڪيتريون", "كم", "كمية", "چقدر", "چند", "कितना", "कितनी", "कितने", "मात्रा", "কত", "কতটা",
        "মাত্রা", "কয়টা", "berapa", "banyak", "takaran"
    )

    /** Putting in, mixing, taking or giving. */
    private val APPLY = listOf(
        "dalna", "daalna", "dalni", "daalni", "dalen", "dalein", "daalein", "dalun", "daalun", "dalte", "daalte", "milana", "milaen",
        "milayen", "milaun", "milani", "milane", "milaana", "mix", "mixing", "ghol", "gholna", "chirakna", "chhidakna", "apply", "spray karna", "spray", "put",
        "ڈالنا", "ڈالیں", "ڈالنی", "ملانا", "ملائیں", "چھڑکنا", "وجهجي", "ملائجي", "اضع", "أضع", "اخلط", "أخلط", "ارش", "أرش",
        "بریزم", "بریزیم", "مخلوط", "بپاشم", "डालना", "डालें", "डालनी", "मिलाना", "मिलाएं", "দেব", "মেশাতে", "ছিটাতে",
        "dicampur", "campur", "semprotkan", "taburkan"
    )

    /** Taking or giving: a medicine's, never on its own (money is taken and given too). */
    private val TAKE = listOf(
        "leni", "lena", "lein", "len", "khani", "khayen", "khaye", "khaen", "kha", "pilaye", "pilana", "pila", "deni", "dena",
        "take", "taken", "give", "لینی", "لیں", "کھانی", "کھائیں", "پلائیں", "پلانا", "دینی", "کائجي", "ڏجي", "اخذ", "آخذ",
        "يأخذ", "اعطي", "أعطي", "بخورم", "بخورد", "بدهم", "लेनी", "लें", "खानी", "खाएं", "पिलाएं", "देनी", "দিতে", "খাওয়াব",
        "খেতে", "খাব", "minum", "diminum", "diberikan", "kasih"
    )

    // ------------------------------------------------------------------------------------------------ the knowledge

    /** The tokens a phrase or word becomes; every other word must be a little word or a figure. */
    private const val MARGIN = "qqmargin"
    private const val MARKUP = "qqmarkup"
    private const val CONTRIB = "qqcontrib"
    private const val BREAK_EVEN = "qqbreakeven"
    private const val DISCOUNT = "qqdiscount"
    private const val RECEIVABLE = "qqreceivable"
    private const val PAYABLE = "qqpayable"
    private const val NET = "qqnetbal"
    private const val CONC = "qqconc"
    private const val PROFIT = "qqprofit"
    private const val DEFINE = "qqdef"
    private const val WHAT = "qqwhat"
    private const val DIFF = "qqdiff"
    private const val HOW = "qqhow"
    private const val COST_BEFORE = "qqcostb"
    private const val COST_AFTER = "qqcosta"
    private const val SALE_BEFORE = "qqsaleb"
    private const val SALE_AFTER = "qqsalea"
    private const val ASK = "qqask"
    private const val MONEY = "qqmoney"
    private const val MINE = "qqmine"
    private const val NUMBER = "qqfig"

    /** Phrases and words, in any of the nine languages, and the token each becomes. Longer phrases are matched first. */
    private val VOCAB: Map<String, List<String>> = mapOf(
        CONTRIB to listOf(
            "contribution margin", "contribution per unit", "contribution", "کنٹریبیوشن مارجن", "کنٹریبیوشن", "ڪنٽريبيوشن مارجن",
            "ڪنٽريبيوشن", "هامش المساهمة", "حاشیه مشارکت", "कंट्रीब्यूशन मार्जिन", "कंट्रीब्यूशन", "योगदान मार्जिन",
            "কন্ট্রিবিউশন মার্জিন", "কন্ট্রিবিউশন", "অবদান মার্জিন", "margin kontribusi", "kontribusi"
        ),
        MARGIN to listOf(
            "profit margin", "gross margin", "munafa margin", "munafe ka margin", "munafay ka margin", "margin", "margins", "marjin",
            "منافع کا مارجن", "مارجن", "منافعي جو مارجن", "هامش الربح", "هامش ربح", "هامش", "حاشیه سود", "حاشیه", "मुनाफे का मार्जिन",
            "मार्जिन", "মার্জিন", "মার্জিনের", "margin keuntungan", "margin laba"
        ),
        MARKUP to listOf(
            "markup", "markups", "mark up", "mark ups", "markap", "المارك اب", "الماركاب", "مارک اپ", "مارکاپ", "مارڪ اپ", "مارك اب", "نسبة الاضافة",
            "مارک آپ", "मार्कअप", "मार्क अप", "মার্কআপ", "মার্ক আপ", "মার্কআপের"
        ),
        BREAK_EVEN to listOf(
            "break even point", "break even", "breakeven point", "breakeven", "bep", "na nafa na nuqsan", "na nafa na nuksan",
            "no profit no loss", "بریک ایون", "نہ نفع نہ نقصان", "بريڪ ايون", "نه نفعو نه نقصان", "نقطة التعادل", "نقطه سر به سر",
            "سر به سر", "ब्रेक ईवन", "ब्रेक इवन", "ना लाभ ना हानि", "न लाभ न हानि", "ব্রেক ইভেন", "titik impas", "impas"
        ),
        DISCOUNT to listOf(
            "discount", "discounts", "chhoot", "chhut", "choot", "riayat", "rayat", "رعایت", "ڈسکاؤنٹ", "چھوٹ", "رعايت", "ڊسڪائونٽ",
            "خصم", "تخفيض", "تخفیف", "छूट", "डिस्काउंट", "ছাড়", "ডিসকাউন্ট", "diskon", "potongan"
        ),
        RECEIVABLE to listOf(
            "accounts receivable", "account receivable", "receivable", "receivables", "قابل وصول", "الذمم المدينة", "ذمم مدينة",
            "حساب های دریافتنی", "حسابهای دریافتنی", "دریافتنی", "प्राप्य", "প্রাপ্য", "piutang usaha", "piutang"
        ),
        PAYABLE to listOf(
            "accounts payable", "account payable", "payable", "payables", "قابل ادائیگی", "الذمم الدائنة", "ذمم دائنة",
            "حساب های پرداختنی", "پرداختنی", "देय", "প্রদেয়", "utang usaha", "hutang usaha"
        ),
        NET to listOf(
            "net balance", "khalis balance", "خالص بیلنس", "نیٹ بیلنس", "خالص بيلنس", "الرصيد الصافي", "صافي الرصيد", "مانده خالص",
            "शुद्ध बैलेंस", "नेट बैलेंस", "নিট ব্যালেন্স", "saldo bersih"
        ),
        CONC to listOf(
            "credit concentration", "concentration of credit", "concentration risk", "concentration", "ارتکاز", "کنسنٹریشن",
            "تركز الائتمان", "تركز", "تمرکز", "केंद्रीकरण", "কেন্দ্রীভবন", "konsentrasi piutang", "konsentrasi"
        ),
        PROFIT to listOf(
            "profit", "munafa", "munafah", "munafe", "nafa", "faida", "fayda", "faaida", "منافع", "نفع", "فائدہ", "منافعو", "ربح",
            "الربح", "मुनाफा", "लाभ", "फायदा", "লাভ", "মুনাফা", "untung", "laba", "keuntungan"
        ),
        DEFINE to listOf(
            "kya hota hai", "kya hoti hai", "kya hote hain", "kya hota he", "kise kehte hain", "kisay kehte hain", "kisko kehte hain",
            "kis ko kehte hain", "kise kahte hain", "kise kehte", "ka kya matlab", "ka matlab", "ki tareef", "matlab", "mutlab",
            "maani", "what does", "meaning of", "what is meant by", "meaning", "mean", "means", "define", "definition", "definition of",
            "کیا ہوتا ہے", "کیا ہوتی ہے", "کسے کہتے ہیں", "کس کو کہتے ہیں", "کا مطلب", "کی تعریف", "مطلب", "معنی", "مفہوم",
            "ڇا ٿيندو آهي", "ڇا ٿيندي آهي", "جو مطلب", "ڪنهن کي چئبو آهي", "ما معنى", "ما المقصود ب", "ما المقصود", "ماذا يعني",
            "ما تعريف", "تعريف", "ما هو", "ما هي", "معنى", "یعنی چه", "یعنی چی", "چه معنی", "چیست", "تعریف", "क्या होता है",
            "क्या होती है", "किसे कहते हैं", "का मतलब", "का अर्थ", "की परिभाषा", "मतलब", "अर्थ", "কাকে বলে", "মানে কী", "মানে কি",
            "বলতে কী বোঝায়", "বলতে কি বোঝায়", "কী বোঝায়", "কি বোঝায়", "মানে", "অর্থ", "apa itu", "apa yang dimaksud dengan",
            "apa yang dimaksud", "artinya", "pengertian", "definisi", "arti"
        ),
        WHAT to listOf("what is", "what s", "whats", "kya hai", "kya he", "کیا ہے", "ڇا آهي", "क्या है", "কী", "কি", "apa"),
        DIFF to listOf(
            "farq", "fark", "farak", "difference", "differences", "differ", "vs", "versus", "فرق", "تفاوت", "الفرق بين", "الفرق",
            "फर्क", "अंतर", "अन्तर", "পার্থক্য", "তফাত", "beda", "bedanya", "perbedaan"
        ),
        HOW to listOf(
            "kaise nikalein", "kaise nikalen", "kaise nikalte", "kaise nikale", "kaise nikalun", "kaise nikaalein", "kaise nikaalte",
            "kaise nikalta", "kaise calculate", "kaise hisaab", "kaise maloom", "kaise pata", "how to calculate", "how do i calculate",
            "how do you calculate", "how to find", "how to work out", "how do i find", "how to compute", "how is", "calculate",
            "formula", "کیسے نکالیں", "کیسے نکالتے", "کیسے نکالا", "کیسے نکالی", "کیسے معلوم", "فارمولا", "ڪيئن ڪڍجي", "ڪيئن ڳڻجي",
            "فارمولو", "كيف احسب", "كيف تحسب", "كيف يحسب", "كيفية حساب", "معادلة", "صيغة", "چطور حساب", "چگونه حساب",
            "چگونه محاسبه", "چطور محاسبه", "فرمول", "कैसे निकालें", "कैसे निकालते", "कैसे निकाले", "फॉर्मूला", "फार्मूला", "सूत्र",
            "কীভাবে বের করব", "কিভাবে বের করব", "কীভাবে বের", "কিভাবে বের", "কীভাবে হিসাব", "কিভাবে হিসাব", "সূত্র", "ফর্মুলা",
            "cara menghitung", "bagaimana menghitung", "cara hitung", "rumus"
        ),
        COST_BEFORE to listOf(
            "cost price", "buying price", "purchase price", "bought for", "bought at", "bought it for", "buy at", "buy for", "buy it for",
            "purchased for", "purchase at", "cost", "costs", "kharid", "khareed", "lagat", "laagat", "قیمت خرید", "لاگت", "خرید",
            "لاڳت", "خريد", "التكلفة", "تكلفة", "سعر الشراء", "लागत", "खरीद मूल्य", "खरीद", "কেনা দাম", "ক্রয়মূল্য", "কেনা",
            "harga beli", "modal"
        ),
        COST_AFTER to listOf(
            "ki cheez", "ki chiz", "ka maal", "ka mal", "wali cheez", "ka saman", "ki li", "mein li", "main li", "me li", "mein kharidi",
            "mein khareedi", "mein kharida", "mein khareeda", "main kharidi", "me kharidi", "mein aayi", "mein aai",
            "کی چیز", "کا مال", "میں لی", "میں خریدی", "میں خریدا", "की चीज", "का माल", "में ली", "में खरीदी", "में खरीदा"
        ),
        SALE_BEFORE to listOf(
            "sale price", "selling price", "sold for", "sold at", "sell for", "sell at", "sells for", "sells at", "sell it for",
            "sell it at", "sold it for", "sale", "price", "farokht", "قیمت فروخت", "فروخت", "وڪري", "وڪرو", "سعر البيع", "البيع",
            "قیمت فروش", "فروش", "बिक्री मूल्य", "বিক্রয়মূল্য", "বিক্রি", "harga jual", "jual", "dijual"
        ),
        SALE_AFTER to listOf(
            "mein bechi", "mein becha", "mein beche", "mein bechun", "mein bechu", "mein bechein", "mein bechen", "main bechi",
            "me bechi", "par bechi", "pe bechi", "mein biki", "mein bika", "mein sale ki", "میں بیچی", "میں بیچا", "میں بیچوں",
            "میں فروخت", "में बेची", "में बेचा", "में बेचूं"
        ),
        ASK to listOf(
            "kitna", "kitni", "kitne", "how much", "fisad", "feesad", "percent", "percentage", "کتنا", "کتنی", "فیصد", "ڪيترو",
            "ڪيتري", "سيڪڙو", "كم", "نسبة", "چقدر", "درصد", "कितना", "कितनी", "प्रतिशत", "কত", "শতাংশ", "berapa", "persen"
        ),
        MONEY to listOf("rs", "rupay", "rupaye", "rupees", "rupee", "rupe", "روپے", "روپيا", "रुपये", "रुपए", "টাকা", "rp", "rupiah"),
        MINE to listOf(
            "my", "mera", "meri", "mere", "hamara", "hamari", "hamare", "میرا", "میری", "میرے", "ہمارا", "منهنجو", "منهنجي", "مेरा",
            "मेरा", "मेरी", "हमारा", "আমার", "saya", "ku"
        )
    )

    /** Each phrase as normalised words, with its token, longest first. */
    private val PHRASES: List<Pair<List<String>, String>> = VOCAB.flatMap { (token, list) ->
        list.map { VoiceQuestion.normalise(it).split(' ').filter { w -> w.isNotEmpty() } to token }
    }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.size }

    /** Little words that ask nothing: being, joining, "the", "of", "and", "then", "tell me", "if". Never "my" (that is [MINE]). */
    private val LITTLE = StrictWords(
        // English
        "the a an is are was were be do does did it its in on of for to from and or then if i we you so what which between " +
            "tell me please explain show calculated worked found work out item thing per unit units one",
        // Roman Urdu and Hindi in Latin letters
        "ka ki ke ko se mein me main aur ya to agar hai hain he hy hota hoti hote hoga hogi honge hua hui banta banti bane banega " +
            "banegi niklega niklegi nikle bata batao bataiye btao samjhao samjha samjhaiye zara ji please plz kya kia is us ye yeh wo " +
            "woh dono kaun konsa konsi kis cheez chiz item ek jab",
        // Urdu
        "کا کی کے کو سے میں اور یا تو اگر ہے ہیں ہوتا ہوتی ہوگا ہوگی ہوا بنتا بنتی بنے گا بتاؤ بتائیں بتائیے سمجھاؤ ذرا جی کیا اس یہ وہ دونوں کون چیز ایک",
        // Sindhi
        "جو جي جا کي کان ۾ ۽ يا ته جيڪڏهن آهي آهن ٿيندو ٻڌايو ٻڌاءِ سمجهايو ڇا هي اهو ٻئي ڪهڙو شيء هڪ",
        // Arabic
        "بين و او في من على عن الى إلى هل هو هي لي قل اخبرني أخبرني اشرح ما ماذا كيف اذا إذا ثم هذا هذه",
        // Farsi
        "بین و یا در از به را که است چیست چه این آن بگو توضیح بده اگر پس",
        // Hindi
        "का की के को से में और या तो अगर है हैं होता होती होगा होगी हुआ बताओ बताइए समझाओ जरा जी क्या इस यह ये वह दोनों कौन चीज एक",
        // Bengali
        "এর এ ও আর বা তাহলে যদি হয় হল হলো হবে বলো বলুন বুঝিয়ে দাও কী কি এই এটা দুটো মধ্যে মধ্য একটা জিনিস টা টি",
        // Indonesian
        "dan atau dengan antara itu ini adalah yang di ke dari jika kalau maka tolong jelaskan beritahu apa bagaimana sebuah satu barang"
    )

    /** Topics that are also a figure the book has: a weak "what is" beside one could ask the book's own ("margin kya hai"). */
    private val BOOK_LIKE = setOf(MARGIN, RECEIVABLE, PAYABLE, NET, CONC)

    private val ATOM_OF = mapOf(
        RECEIVABLE to "K101", PAYABLE to "K102", NET to "K103", CONC to "K104", MARGIN to "K201", MARKUP to "K202",
        CONTRIB to "K204", BREAK_EVEN to "K205"
    )

    private val TOPICS = setOf(MARGIN, MARKUP, CONTRIB, BREAK_EVEN, DISCOUNT, RECEIVABLE, PAYABLE, NET, CONC)

    /** A figure as written: plain digits, thousands with commas (or a lakh's), or at most two decimals after a point. */
    private val AMOUNT = Regex("(?<![\\p{Nd},.])\\p{Nd}[\\p{Nd},.]*(?<![,.])")
    private val PLAIN = Regex("\\d+")
    private val COMMAS = Regex("\\d{1,3}(,\\d{3})+|\\d{1,2}(,\\d{2})*,\\d{3}")
    private val DECIMAL = Regex("\\d+\\.\\d{1,2}")

    /** [raw] as a figure, or null when it could be read two ways ("1.000" is a thousand to an Indonesian, one to others). */
    private fun amountOf(raw: String): Double? {
        val ascii = raw.map { ch -> if (Character.isDigit(ch)) ('0' + Character.digit(ch, 10)) else ch }.joinToString("")
        return when {
            PLAIN.matches(ascii) -> ascii.toDoubleOrNull()
            COMMAS.matches(ascii) -> ascii.replace(",", "").toDoubleOrNull()
            DECIMAL.matches(ascii) -> ascii.toDoubleOrNull()
            else -> null
        }
    }

    /**
     * The knowledge [heard] asks, or null: nothing is guessed. The limits first ([limitOf]); then a worked margin, markup or profit
     * from the sentence's own two figures; then an atom's definition, its formula or the difference of two. [isPerson] and
     * [isGoods]: words the book knows as a name; [calling]: a word that only calls him ("Hudhud", "bhai").
     */
    fun read(
        heard: String, isPerson: (String) -> Boolean = { false }, isGoods: (String) -> Boolean = { false },
        calling: (String) -> Boolean = { false }
    ): Hudhud.Ask.Knowledge? {
        limitOf(heard, isPerson, isGoods)?.let { return Hudhud.Ask.Knowledge(KnowKind.UNSUPPORTED, limit = it) }
        // The figures stand in by tokens before the words are spelt, so "1,000" stays one figure.
        val figures = ArrayList<Double?>()
        val marked = AMOUNT.replace(heard) { m -> figures += amountOf(m.value); " $NUMBER${figures.size - 1} " }
        val words = VoiceQuestion.normalise(marked).split(' ').filter { it.isNotEmpty() && !calling(it) }
        if (words.isEmpty() || words.size > MAX_WORDS + 6) return null
        if (VoiceQuestion.unsupportedSpan(heard) || VoiceQuestion.windowOf(heard) != null) return null
        // Words to tokens, longest phrase first.
        val tokens = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (w.startsWith(NUMBER) && w.drop(NUMBER.length).all { it.isDigit() }) { tokens += w; i++; continue }
            if (isPerson(w) || isGoods(w)) return null
            // Arabic writes "and" joined to the word after it ("والمارك اب", "وسعر البيع"): the phrase is read without it.
            val bare = if (w.length > 2 && w.startsWith("و") && w[1] in '؀'..'ۿ') w.substring(1) else null
            val hit = PHRASES.firstOrNull { (p, _) -> i + p.size <= words.size && (0 until p.size).all { k -> words[i + k] == p[k] } }
                ?: bare?.let { b -> PHRASES.firstOrNull { (p, _) -> i + p.size <= words.size && p[0] == b && (1 until p.size).all { k -> words[i + k] == p[k] } } }
            if (hit != null) { tokens += hit.second; i += hit.first.size; continue }
            if (!LITTLE.has(w)) return null
            i++
        }
        if (tokens.size > MAX_WORDS) return null
        val numbers = tokens.filter { it.startsWith(NUMBER) }
        return if (numbers.isEmpty()) definition(tokens) else worked(tokens, figures)
    }

    /** An atom's meaning, its formula or the difference between two: no figure in the sentence. */
    private fun definition(tokens: List<String>): Hudhud.Ask.Knowledge? {
        val topics = tokens.filter { it in TOPICS }.toSet()
        if (topics.isEmpty()) return null
        // Words that belong to a worked example (a cost, a sale price, "how much", "my") ask a figure, not a meaning.
        if (tokens.any { it == COST_BEFORE || it == COST_AFTER || it == SALE_BEFORE || it == SALE_AFTER || it == MINE || it == ASK || it == MONEY }) return null
        if (tokens.contains(PROFIT)) return null
        val define = DEFINE in tokens
        val weak = WHAT in tokens
        val how = HOW in tokens
        val diff = DIFF in tokens
        val atom: String = when {
            diff && topics == setOf(MARGIN, MARKUP) -> "K203"
            diff && (topics == setOf(DISCOUNT, MARKUP) || topics == setOf(DISCOUNT, MARGIN)) -> "K206"
            diff -> return null
            topics.size != 1 -> return null
            else -> ATOM_OF[topics.first()] ?: return null
        }
        if (!(define || weak || how || diff)) return null
        // "Margin kya hai" could ask the book's own margin: a weak "what is" only for a topic the book has no figure of.
        if (!define && !how && !diff && topics.any { it in BOOK_LIKE }) return null
        val a = atom(atom) ?: return null
        return Hudhud.Ask.Knowledge(a.kind, atom = atom)
    }

    /**
     * A worked margin, markup or profit: exactly two figures, one said as the cost and one as the sale price ("800 ki cheez 1000
     * mein bechi", "cost 800 sale 1000"), and the measure asked. Figures whose roles the sentence does not say apart are asked
     * about ([KnowKind.NEEDS_CLARIFICATION]), never guessed from which is larger: a sale at a loss is a real sale.
     */
    private fun worked(tokens: List<String>, figures: List<Double?>): Hudhud.Ask.Knowledge? {
        val measures = tokens.filter { it == MARGIN || it == MARKUP || it == PROFIT }.toSet()
        if (measures.size != 1) return null
        if (tokens.any { it in TOPICS && it != MARGIN && it != MARKUP }) return null
        if (DIFF in tokens || HOW in tokens) return null
        val asked = when (measures.first()) {
            MARGIN -> KnowMeasure.MARGIN
            MARKUP -> KnowMeasure.MARKUP
            else -> KnowMeasure.PROFIT
        }
        val at = tokens.indices.filter { tokens[it].startsWith(NUMBER) }
        if (at.size != 2) return null
        val values = at.map { figures.getOrNull(tokens[it].drop(NUMBER.length).toInt()) ?: return null }
        fun role(p: Int): String? {
            var j = p + 1
            while (j < tokens.size && tokens[j] == MONEY) j++
            val after = tokens.getOrNull(j)
            if (after == COST_AFTER) return COST_AFTER
            if (after == SALE_AFTER) return SALE_AFTER
            var k = p - 1
            while (k >= 0 && tokens[k] == MONEY) k--
            return when (tokens.getOrNull(k)) {
                COST_BEFORE -> COST_AFTER
                SALE_BEFORE -> SALE_AFTER
                else -> null
            }
        }
        val roles = at.map(::role)
        val cost = roles.indexOf(COST_AFTER)
        val sale = roles.indexOf(SALE_AFTER)
        if (cost < 0 || sale < 0 || cost == sale || roles.count { it == COST_AFTER } != 1 || roles.count { it == SALE_AFTER } != 1) {
            return Hudhud.Ask.Knowledge(KnowKind.NEEDS_CLARIFICATION, asked = asked)
        }
        val atom = if (asked == KnowMeasure.MARKUP) "K202" else "K201"
        return Hudhud.Ask.Knowledge(KnowKind.CALCULATED, atom = atom, cost = values[cost], sale = values[sale], asked = asked)
    }
}
