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
     */
    enum class Window { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH }

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
        "paling sedikit",
        "سب سے کم", "سڀ کان گھٽ", "سڀ کان گهٽ", "الأقل", "أقل", "اقل", "کمترین", "کم‌ترین", "सबसे कम", "সবচেয়ে কম")
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
        "महीना", "महीने", "माह", "مهينو", "مهيني", "شهر", "ماه", "মাস", "bulan")
    private val LAST = listOf("pichle", "pichhle", "pichlay", "pichhlay", "pichla", "pichhla", "guzishta", "guzashta",
        "last", "previous", "پچھلے", "پچھلا", "پچھلی", "گزشتہ", "گذشتہ", "पिछले", "पिछला", "पिछली", "गत", "গত", "আগের",
        "پوئين", "گذريل", "الماضي", "الماضية", "السابق", "الفائت", "گذشته", "قبل", "پیش", "lalu", "kemarin", "sebelumnya")
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
    internal fun keywordsIn(text: String): Set<String> {
        val said = text.split(' ').filter { it.isNotEmpty() }
        val out = HashSet<String>()
        for (w in ALL) {
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
        listOf(PROFIT, EXPIRING, NOT_PAID, MOST, LEAST, DEBT, SALES, TODAY, TO_GIVE, TO_GET, YESTERDAY, WEEK, MONTH, LAST).flatten()
    }

    /**
     * The span of days [heard] names, or null when it names none (12 Nov 2026): last month and last week (a "last"
     * word beside the month's or the week's), this week, today, yesterday, this month. A sentence naming none keeps
     * the figure's own span; one naming a span this list does not know ("parson", "this year") keeps it too, and the
     * card's heading says which span its figure is for.
     */
    fun windowOf(heard: String): Window? {
        val t = normalise(heard)
        if (t.isEmpty()) return null
        val last = saysAny(t, LAST)
        return when {
            last && saysAny(t, MONTH) -> Window.LAST_MONTH
            last && saysAny(t, WEEK) -> Window.LAST_WEEK
            saysAny(t, WEEK) -> Window.THIS_WEEK
            saysAny(t, TODAY) -> Window.TODAY
            saysAny(t, YESTERDAY) -> Window.YESTERDAY
            saysAny(t, MONTH) -> Window.THIS_MONTH
            else -> null
        }
    }

    fun classify(heard: String): Kind? {
        val t = normalise(heard)
        if (t.isEmpty()) return null
        return when {
            saysAny(t, PROFIT) -> Kind.PROFIT_MONTH
            saysAny(t, EXPIRING) -> Kind.EXPIRING
            saysAny(t, NOT_PAID) -> Kind.NOT_PAID
            // "Sab se zyada kis ko dena hai" is whom the shop owes most: the shop's payables, not who owes it most.
            saysAny(t, MOST) && saysAny(t, TO_GIVE) && !owedToTheShop(t) -> Kind.TO_GIVE
            // "Kisi ne sabse zyada paise dene hain" says "dene" and somebody else gives: who owes the shop most (14 Nov 2026).
            saysAny(t, MOST) && owedToUs(t) -> Kind.WHO_OWES_MOST
            saysAny(t, LEAST) && owedToUs(t) && !owedByTheShop(t) -> Kind.WHO_OWES_LEAST
            saysAny(t, SALES) -> if (saysAny(t, TODAY)) Kind.SALES_TODAY else Kind.SALES_MONTH
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
