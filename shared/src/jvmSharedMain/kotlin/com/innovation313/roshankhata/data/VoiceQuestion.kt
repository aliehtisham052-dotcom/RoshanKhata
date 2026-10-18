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
        "سود", "मुनाफ़ा", "मुनाफा", "लाभ", "লাভ", "মুনাফা")
    private val EXPIRING = listOf("expire", "expiry", "mudat", "kedaluwarsa", "kadaluarsa", "ایکسپائر", "میعاد", "مدت",
        "انقضا", "انتهاء", "صلاحيت", "एक्सपायर", "मियाद", "মেয়াদ", "এক্সপায়ার")
    private val NOT_PAID = listOf("nahi diya", "nahin diya", "paisa nahi", "not paid", "hasn't paid", "havent paid", "belum bayar",
        "belum membayar", "نہیں دیا", "نہیں دیے", "نہیں دئیے", "ادا نہیں", "نه ڏنو", "نه ڏنا", "لم يدفع", "لم يسدد", "پرداخت نکرده",
        "नहीं दिया", "नहीं दिए", "দেয়নি", "দেননি", "পরিশোধ করেনি")
    private val MOST = listOf("sab se zyada", "sabse zyada", "sab se ziada", "most", "highest", "biggest", "terbanyak", "paling",
        "سب سے زیادہ", "سڀ کان وڌيڪ", "الأكثر", "أكثر", "اكثر", "بیشترین", "सबसे ज़्यादा", "सबसे ज्यादा",
        "সবচেয়ে বেশি")
    private val DEBT = listOf("baqaya", "bakaya", "udhar", "udhaar", "qarz", "owe", "owes", "due", "utang", "piutang",
        "بقایا", "ادھار", "قرض", "اڌار", "دين", "ديون", "بدهی", "طلب", "बकाया", "उधार", "क़र्ज़", "कर्ज", "বাকি", "ধার", "পাওনা")
    private val SALES = listOf("bikri", "bechi", "becha", "bechay", "farokht", "sale", "sales", "sold", "penjualan", "jualan",
        "terjual", "بکری", "فروخت", "بیچا", "بیچی", "وڪرو", "وڪري", "مبيعات", "بيع", "فروش", "बिक्री", "बेचा", "बेची",
        "বিক্রি", "বিক্রয়")
    private val TODAY = listOf("aaj", "today", "hari ini", "آج", "اڄ", "اليوم", "امروز", "आज", "আজ")
    private val TO_GIVE = listOf("dena", "dene", "to give", "i owe", "to pay", "utang saya", "دینا", "دینے", "ڏيڻا", "ڏيڻو",
        "علي دفع", "عليّ", "پرداختنی", "بدهکار", "देना", "देने", "দেনা", "দিতে")
    private val TO_GET = listOf("lena", "lene", "to get", "receive", "collect", "piutang", "tagih", "لینا", "لینے", "وٺڻا",
        "وٺڻو", "لي عند", "طلبکار", "دریافت", "लेना", "लेने", "পাওনা", "পেতে")

    /** Lower case, one spelling for letters Urdu, Persian and Arabic write differently. */
    internal fun normalise(s: String): String = s.lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ه', 'ہ').replace('ة', 'ہ')
        .replace("\u093C", "") // Devanagari nukta: ज़ and ज are one word to a listener
        .replace(Regex("[\\p{Punct}،؟۔!?]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun has(text: String, words: List<String>) = words.any { text.contains(normalise(it)) }

    fun classify(heard: String): Kind? {
        val t = normalise(heard)
        if (t.isEmpty()) return null
        return when {
            has(t, PROFIT) -> Kind.PROFIT_MONTH
            has(t, EXPIRING) -> Kind.EXPIRING
            has(t, NOT_PAID) -> Kind.NOT_PAID
            has(t, MOST) && (has(t, DEBT) || has(t, TO_GET)) -> Kind.WHO_OWES_MOST
            has(t, SALES) -> if (has(t, TODAY)) Kind.SALES_TODAY else Kind.SALES_MONTH
            has(t, TO_GIVE) -> Kind.TO_GIVE
            has(t, TO_GET) || has(t, DEBT) -> Kind.TO_GET
            else -> null
        }
    }

    /** The first of the recogniser's guesses that is a question this app knows. */
    fun classify(candidates: List<String>): Pair<String, Kind>? =
        candidates.firstNotNullOfOrNull { c -> classify(c)?.let { c to it } }
}
