package com.innovation313.roshankhata.data

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

    enum class Kind { PROFIT_MONTH, EXPIRING, NOT_PAID, WHO_OWES_MOST, SALES_TODAY, SALES_MONTH, TO_GIVE, TO_GET }

    private val PROFIT = listOf("munafa", "munafah", "profit", "laba", "untung", "منافع", "نفعو", "ربح", "أرباح", "ارباح",
        "سود", "मुनाफ़ा", "मुनाफा", "लाभ", "লাভ", "মুনাফা",
        // 12 Nov 2026 (Hudhud): "is mahine kitna faida hua", as most owners say it.
        "faida", "fayda", "faaida", "فائدہ", "फायदा")
    private val EXPIRING = listOf("expire", "expiry", "mudat", "kedaluwarsa", "kadaluarsa", "ایکسپائر", "میعاد", "مدت",
        "انقضا", "انتهاء", "صلاحيت", "एक्सपायर", "मियाद", "মেয়াদ", "এক্সপায়ার",
        // 12 Nov 2026 (Hudhud): the app's own chip words, so a chip typed as it reads is understood.
        "expiring", "expired", "ختم ٿيندڙ")
    private val NOT_PAID = listOf("nahi diya", "nahin diya", "paisa nahi", "not paid", "hasn't paid", "havent paid", "belum bayar",
        "belum membayar", "نہیں دیا", "نہیں دیے", "نہیں دئیے", "ادا نہیں", "نه ڏنو", "نه ڏنا", "لم يدفع", "لم يسدد", "پرداخت نکرده",
        "नहीं दिया", "नहीं दिए", "দেয়নি", "দেননি", "পরিশোধ করেনি")
    private val MOST = listOf("sab se zyada", "sabse zyada", "sab se ziada", "most", "highest", "biggest", "terbanyak", "paling",
        "terbesar",
        "سب سے زیادہ", "سڀ کان وڌيڪ", "الأكثر", "أكثر", "اكثر", "بیشترین", "सबसे ज़्यादा", "सबसे ज्यादा",
        "সবচেয়ে বেশি")
    private val DEBT = listOf("baqaya", "bakaya", "udhar", "udhaar", "qarz", "owe", "owes", "due", "utang", "piutang",
        "بقایا", "ادھار", "قرض", "اڌار", "دين", "ديون", "طلب", "बकाया", "उधार", "क़र्ज़", "कर्ज", "বাকি", "ধার", "পাওনা",
        // 12 Nov 2026 (Hudhud): "Ali ka kitna baqi hai"; "dues", once the words count only whole.
        "baqi", "baaqi", "baki", "baaki", "باقی", "बाकी", "dues")
    private val SALES = listOf("bikri", "bechi", "becha", "bechay", "farokht", "sale", "sales", "sold", "penjualan", "jualan",
        "terjual", "بکری", "فروخت", "بیچا", "بیچی", "وڪرو", "وڪري", "مبيعات", "بيع", "فروش", "बिक्री", "बेचा", "बेची",
        "বিক্রি", "বিক্রয়",
        // 12 Nov 2026 (Hudhud): "aaj kitna bika", "how much did I sell".
        "sell", "selling", "bika", "biki", "bikay", "بکا", "بکی", "बिका", "बिकी")
    private val TODAY = listOf("aaj", "today", "hari ini", "آج", "اڄ", "اليوم", "امروز", "आज", "আজ")
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
    private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val PUNCT = Regex("[\\p{Punct}،؟۔!?]")
    private val SPACES = Regex("\\s+")

    /** Each list word as it is spelt for matching, worked out once rather than at every question. */
    private val spelt = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Lower case, one spelling for letters Urdu, Persian and Arabic write differently. Arabic-script vowel marks go
     * (a recogniser or keyboard may write them, a list never does), the joiners Persian writes inside words are a
     * space, and Bengali's ya, rra and rha are one way however a keyboard writes them (12 Nov 2026, Hudhud: his own
     * spelling until then, now the one both use).
     */
    internal fun normalise(s: String): String = s
        .replace(MARKS, "").replace('\u200C', ' ').replace('\u200D', ' ')
        .replace("\u09DF", "\u09AF\u09BC").replace("\u09DC", "\u09A1\u09BC").replace("\u09DD", "\u09A2\u09BC")
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ه', 'ہ').replace('ة', 'ہ')
        .replace("\u093C", "") // Devanagari nukta: ज़ and ज are one word to a listener
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
        listOf(PROFIT, EXPIRING, NOT_PAID, MOST, DEBT, SALES, TODAY, TO_GIVE, TO_GET).flatten()
    }

    fun classify(heard: String): Kind? {
        val t = normalise(heard)
        if (t.isEmpty()) return null
        return when {
            saysAny(t, PROFIT) -> Kind.PROFIT_MONTH
            saysAny(t, EXPIRING) -> Kind.EXPIRING
            saysAny(t, NOT_PAID) -> Kind.NOT_PAID
            saysAny(t, MOST) && (saysAny(t, DEBT) || saysAny(t, TO_GET)) -> Kind.WHO_OWES_MOST
            saysAny(t, SALES) -> if (saysAny(t, TODAY)) Kind.SALES_TODAY else Kind.SALES_MONTH
            saysAny(t, TO_GIVE) -> Kind.TO_GIVE
            saysAny(t, TO_GET) || saysAny(t, DEBT) -> Kind.TO_GET
            else -> null
        }
    }

    /** The first of the recogniser's guesses that is a question this app knows. */
    fun classify(candidates: List<String>): Pair<String, Kind>? =
        candidates.firstNotNullOfOrNull { c -> classify(c)?.let { c to it } }
}
