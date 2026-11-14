package com.innovation313.roshankhata.data

/**
 * A list of words for the strict readers ([HudhudFaith], [HudhudStory]) — those that answer a sentence only when every
 * word of it is one of their own words or a little word that asks nothing (14 Nov 2026).
 *
 * A word is matched by the figures' own spelling ([VoiceQuestion.normalise]) and by the little pieces a language joins to it:
 * Arabic's "the", "and the", "with the", "for the", "and", "with", "for" in front (الرسول, والله, بالتطبيق); Bengali's case
 * endings behind (আল্লাহর, নবীকে); an English plural of five letters or more ("birds", "stories"). Nothing is matched by
 * its sound: a strict reader says "I do not know" long before it says a wrong thing.
 *
 * Kept in one place because both readers need exactly this and a second copy would drift (lesson 65: two rows that must agree
 * are one thing).
 */
internal class StrictWords(vararg lists: String) {
    private val exact: Set<String> = lists.flatMap { it.split(' ', ',').filter { w -> w.isNotBlank() } }
        .map { VoiceQuestion.normalise(it.replace('ٱ', 'ا')) }.filter { it.isNotEmpty() }.toSet()

    fun has(w: String): Boolean {
        if (w in exact) return true
        val first = w.firstOrNull() ?: return false
        return when {
            // Arabic's "the", "and the", "with the", "for the", "and", "with", "for" joined in front.
            first in '؀'..'ۿ' -> FRONT.any { w.length > it.length + 1 && w.startsWith(it) && w.substring(it.length) in exact }
            // Bengali's case endings joined behind: আল্লাহর, নবীকে.
            first in 'ঀ'..'৿' -> ENDS.any { w.length > it.length + 1 && w.endsWith(it) && w.dropLast(it.length) in exact }
            // An English plural.
            first in 'a'..'z' -> w.length >= 5 && (w.endsWith("s") && w.dropLast(1) in exact || w.endsWith("es") && w.dropLast(2) in exact)
            else -> false
        }
    }

    companion object {
        private val FRONT = listOf("ال", "وال", "بال", "فال", "لل", "و", "ب", "ل", "ف").map { VoiceQuestion.normalise(it) }
        private val ENDS = listOf("ের", "র", "কে", "কের", "ে", "টা", "টি", "ও").map { VoiceQuestion.normalise(it) }

        /**
         * Words of the book: any of them and the sentence is about the shop, not the faith and not the app's history.
         * Both strict readers refuse a sentence that holds one (kept here once: lesson 65).
         */
        val BOOK = StrictWords(
            "hisaab hisab khata khaata khate account balance paise paisa raqam rupay rupees rs payment udhaar udhar sale sales bikri munafa profit stock maal product products customer customers supplier suppliers invoice bill bills cheque staff salary entry entries ledger cash collect wasooli number phone address village dukaan shop today kal aaj tomorrow",
            "حساب کھاتہ کھاتے پیسے رقم روپے ادھار بکری منافع گاہک مال دکان آج کل نمبر پتہ گاؤں دفتر دين ديون",
            "हिसाब खाता पैसे रकम बिक्री मुनाफा ग्राहक माल दुकान आज कल नंबर पता गाँव", "মুনাফা বিক্রি হিসাব খাতা টাকা গ্রাহক মাল দোকান আজ কাল"
        )
    }
}
