package com.innovation313.roshankhata.data

/**
 * Words Hudhud's voice reads wrongly, put right for the ear (14 Nov 2026, the owner: "Alhamdulillah ki pronunciation sahi
 * nahi, us par strong grip honi chahiye — ye bahut commonly use hoga", and "bikri (sale) ko bakri (goat) parh deta hai").
 * Only what is SAID changes: the card keeps its own words, as with [HudhudSay].
 *
 *  - **Alhamdulillah** is respelt for each language's voice. Which respelling a phone's voice reads best cannot be known
 *    here (every phone's voice is its own), so there are [ways] to choose from — the first is used until the owner hears the
 *    others in Hudhud's voice settings and keeps the one that sounds right, as with his name's ways.
 *  - **ﷺ** is said in full ("sallallahu alayhi wa sallam") in the language's own letters: a voice would skip the sign.
 *  - **بکری** in Urdu is two words, بِکری (sale) and بَکری (goat), and a voice reads the commoner, the goat. Hudhud's own
 *    sentences use it only as the shop's sale ("کل کی بکری", "بکری اور منافع"), so there it is said as "فروخت", a word that
 *    has one reading. A customer or an item that happens to be called so is not in those two frames and is left alone.
 *  - **Ehtisham**, the developer's name, is said "Ihtishaam" by an English voice.
 */
object HudhudPronounce {

    /** The words whose reading the owner can choose by ear. */
    enum class Word { ALHAMDULILLAH }

    /**
     * The ways [language]'s voice can be given [word]; the first is used unless another was chosen. A blank way leaves the
     * text as written (Sindhi has no voice of its own on phones; its text is never read aloud).
     */
    fun ways(word: Word, language: String): List<String> = when (word) {
        Word.ALHAMDULILLAH -> when (language) {
            "en" -> listOf("Al-ham-doo-lil-laah", "Alhamdu lillaah", "Al hamdulillah")
            "ur" -> listOf("الحمدُو لِل لاہ", "اَلحَمدُ لِلّٰہ", "الحمد لِل لاہ")
            "ar" -> listOf("ٱلْحَمْدُ لِلَّهِ", "الحمد لله", "الحمدُ لِلّاه")
            "fa" -> listOf("اَلْحَمْدُ لِلّٰه", "الحمد لِل لاه", "الحمدو لِلّاه")
            "hi" -> listOf("अल्हम्दुलिल्लाह", "अल हम्दु लिल्लाह", "अलहम दुलिल्लाह")
            "bn" -> listOf("আলহামদুলিল্লাহ", "আল হামদু লিল্লাহ", "আলহামদু লিল্লাহ")
            "in", "id" -> listOf("Alhamdulillah", "Alhamdu lillaah", "Al-hamdu-lil-laah")
            else -> listOf("")
        }
    }

    /** ﷺ said in full, in [language]'s letters. */
    private fun salawat(language: String): String = when (language) {
        "ur" -> "صلی اللہ علیہ وسلم"
        "sd" -> "صلي الله عليه وسلم"
        "ar" -> "صلى الله عليه وسلم"
        "fa" -> "صلی الله علیه و سلم"
        "hi" -> "सल्लल्लाहु अलैहि व सल्लम"
        "bn" -> "সাল্লাল্লাহু আলাইহি ওয়াসাল্লাম"
        else -> "sallallahu alaihi wasallam"
    }

    /** الحمد لله / الحمدللہ in any Arabic-script spelling, with or without a Quran-style spelling's marks. */
    private val ALHAMDULILLAH_ARABIC = Regex(
        "(?<![\\p{L}\\p{M}])[ٱا]\\p{M}*ل\\p{M}*ح\\p{M}*م\\p{M}*د\\p{M}*[\\s‌]?" +
            "ل\\p{M}*ل\\p{M}*[هہۃة]\\p{M}*(?![\\p{L}])"
    )
    private val ALHAMDULILLAH_LATIN = Regex(
        "(?i)(?<![\\p{L}])al[\\s-]?ham(?:d|du|do|dou)?[\\s-]?l{1,2}i?l+a{1,2}hi?(?![\\p{L}])"
    )
    private val ALHAMDULILLAH_HINDI = Regex(
        "(?<![\\p{L}\\p{M}])अल्?हम्?दुलिल्?लाह(?![\\p{L}\\p{M}])"
    )
    private val ALHAMDULILLAH_BENGALI = Regex(
        "(?<![\\p{L}\\p{M}])আলহামদুলিল্লাহি?(?![\\p{L}\\p{M}])"
    )

    /** "کل کی بکری", "اس ہفتے کی بکری": the sale, in the frame the sale is named in. */
    private val BIKRI_AFTER_KI = Regex("(?<=کی )بِ?کری(?![\\p{L}\\p{M}])")

    /** "بکری اور منافع": the sale and the profit. */
    private val BIKRI_BEFORE_AUR = Regex("(?<![\\p{L}\\p{M}])بِ?کری(?= اور )")

    private val EHTISHAM = Regex("(?<![\\p{L}])Ehtisham(?![\\p{L}])")

    /**
     * [text] as [language]'s voice should get it. [wayOf] says which of [ways] to use for each word (the first by default).
     */
    fun apply(text: String, language: String, wayOf: (Word) -> Int = { 0 }): String {
        var out = text
        val ways = ways(Word.ALHAMDULILLAH, language)
        val way = ways.getOrNull(wayOf(Word.ALHAMDULILLAH)) ?: ways.first()
        if (way.isNotEmpty()) {
            for (re in listOf(ALHAMDULILLAH_ARABIC, ALHAMDULILLAH_LATIN, ALHAMDULILLAH_HINDI, ALHAMDULILLAH_BENGALI)) {
                out = re.replace(out) { way }
            }
        }
        if (out.contains('ﷺ')) out = out.replace("ﷺ", salawat(language))
        if (language == "ur") out = BIKRI_BEFORE_AUR.replace(BIKRI_AFTER_KI.replace(out, "فروخت"), "فروخت")
        if (language == "en") out = EHTISHAM.replace(out, "Ihtishaam")
        return out
    }
}
