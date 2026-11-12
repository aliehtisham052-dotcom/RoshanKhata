package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch
import kotlin.math.ln

/**
 * Hudhud's ears (12 Nov 2026): what a question typed or spoken to him asks for, worked out on the phone from the
 * words alone, in the app's nine languages and in the scripts a recogniser writes them in.
 *
 * Like [VoiceQuestion], whose eight questions it keeps word for word, it is deliberately not a chat model: the book
 * stays on the phone, a fixed list can be tested, and every answer only READS. The screen always shows what was
 * understood beside the answer, so a misheard question shows as the wrong question, never as a wrong figure for the
 * right one; and when nothing fits it says so ([Ask.Unknown]) rather than guess.
 *
 * The order a question is read in: a "how" question asks for a help page first; then a customer or supplier the
 * question names (unless it asks one of the figures, which wins — "aaj ki sale" is today's sale even in a book with
 * a party called Sale); one of VoiceQuestion's figures; the backup; the drawer; a help page; a greeting. A figure's
 * word counts only as a whole word ([VoiceQuestion.classify]): "Saleem" and "Ali Wholesale" are names, not the sales.
 */
object Hudhud {

    /** What a question asks for. */
    sealed class Ask {
        /** One of VoiceQuestion's eight figures. */
        data class Figure(val kind: VoiceQuestion.Kind) : Ask()

        /** Customers or suppliers the question names, best first; several when it could be any of them. */
        data class Parties(val ids: List<Long>) : Ask()

        /** When the book was last backed up, and whether that is in order. */
        object BackupStatus : Ask()

        /** What should be in the drawer now, by the books. */
        object Drawer : Ask()

        /** A help page, by the key the screen gave it ([Topic.key]). */
        data class Help(val key: String) : Ask()

        /** "Assalam-o-Alaikum": answered with its reply. */
        object Salaam : Ask()

        /** "Hello": answered with what Hudhud can do. */
        object Hello : Ask()

        /** Nothing Hudhud knows; he says so and offers what he can answer. */
        object Unknown : Ask()
    }

    /** A customer or supplier of the book, as the question may name him. */
    data class Name(val id: Long, val name: String)

    /**
     * A help page: its [key], its [title] and the rest of its words, in every language the phone gives them.
     * [feature]: the page of one of the app's features ("Cheques", "Staff"), whose name is never taken for a
     * customer's; a question-and-answer page's title is a sentence, and its words (محفوظ, "keep") may well be one.
     */
    data class Topic(val key: String, val title: String, val text: String, val feature: Boolean = true)

    /** The first of the recogniser's guesses Hudhud knows, with that guess; else the first guess, not known. */
    fun understand(candidates: List<String>, names: List<Name>, topics: List<Topic>): Pair<String, Ask> {
        for (c in candidates) {
            val ask = understand(c, names, topics)
            if (ask != Ask.Unknown) return c to ask
        }
        return (candidates.firstOrNull() ?: "") to Ask.Unknown
    }

    fun understand(heard: String, names: List<Name>, topics: List<Topic>): Ask {
        val said = words(heard)
        if (said.isEmpty()) return Ask.Unknown
        val joined = said.joinToString(" ")
        val how = has(joined, HOW)
        if (how) helpOf(said, topics)?.let { return Ask.Help(it) }
        val figure = VoiceQuestion.classify(heard)
        val named = namedIn(said, names, topics)
        if (named.isNotEmpty() && (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE)) {
            return Ask.Parties(named)
        }
        if (figure != null) return Ask.Figure(figure)
        if (has(joined, BACKUP)) return Ask.BackupStatus
        if (has(joined, DRAWER)) return Ask.Drawer
        helpOf(said, topics)?.let { return Ask.Help(it) }
        if (has(joined, SALAAM)) return Ask.Salaam
        if (has(joined, HELLO)) return Ask.Hello
        return Ask.Unknown
    }

    // ------------------------------------------------------------------------------------------------ the words

    /**
     * Hudhud's spelling of a text is [VoiceQuestion.normalise]'s: one spelling for the figures' words, the names and
     * the help pages, so a word is never one thing to one of them and another to the next.
     */
    internal fun norm(s: String): String = VoiceQuestion.normalise(s)

    private fun words(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    /**
     * True when one of [list] is said, by the figures' own rule ([VoiceQuestion.saysAny]): a single word as a whole
     * word or with its language's joined pieces (so سلام is not found inside اسلام, but ড্রয়ার is in ড্রয়ারে), a phrase
     * as a run of whole words.
     */
    private fun has(joined: String, list: List<String>): Boolean = VoiceQuestion.saysAny(joined, list)

    /**
     * "How": a question that asks how to do something asks for a help page first. English "how" only as "how to",
     * "how do"…: "how much" and "how many" ask for a figure.
     */
    private val HOW = listOf(
        "kaise", "kese", "kaisay", "kesay", "kaisy", "kesy", "tareeqa", "tarika", "tariqa",
        "how to", "how do", "how does", "how can", "how should", "how i",
        "کیسے", "کیسی", "طریقہ", "ڪيئن", "كيف", "چگونه", "چطور", "कैसे", "तरीका",
        "কিভাবে", "কীভাবে", "bagaimana", "gimana", "cara"
    )

    /** The backup, asked about by name. */
    private val BACKUP = listOf(
        "backup", "back up", "bakup", "bekap", "بیک اپ", "بیک‌اپ", "بيڪ اپ", "بیک‌آپ", "پشتیبان", "پشتیبان‌گیری",
        "نسخ احتياطي", "النسخ الاحتياطي", "نسخة احتياطية", "बैकअप", "ব্যাকআপ", "cadangan"
    )

    /** The drawer — the shop's galla — and the cash in hand. Bare "cash" is left out: it is half of Cash flow. */
    private val DRAWER = listOf(
        "galla", "gala", "galle", "gallay", "galley", "galy", "drawer", "cash in hand", "naqdi", "naqd",
        "گلہ", "گلا", "گلے", "نقدی", "نقد", "الدرج", "صندوق النقد", "دخل",
        "गल्ला", "गल्ले", "नकद", "नकदी", "हाथ में नकद", "ক্যাশ বাক্স", "ড্রয়ার", "নগদ", "laci", "uang tunai"
    )

    /** "Assalam-o-Alaikum", in the ways it is written and heard. */
    private val SALAAM = listOf(
        "assalam", "asalam", "assalamu", "assalamualaikum", "asalamualaikum", "salam", "salaam", "aoa", "slam",
        "السلام", "سلام", "अस्सलाम", "सलाम", "আসসালামু", "আসসালামুয়ালাইকুম", "সালাম"
    )

    /** "Hello", "hi". */
    private val HELLO = listOf("hello", "helo", "hi", "hey", "ہیلو", "हेलो", "हैलो", "হ্যালো", "halo", "مرحبا")

    /**
     * Words a question is made of that name nobody: question words, the little words between them and the words
     * for a balance, in the nine languages. A name is looked for only among what is left, so "Kabir" is not found
     * in "kab", nor anyone in "kitna".
     */
    private val STOP = listOf(
        // Roman Urdu and Hindi in Latin letters
        "kitna", "kitni", "kitne", "kya", "kia", "kyaa", "kaun", "kon", "kis", "kisi", "kisne", "kab", "hai", "hain",
        "he", "ha", "ka", "ki", "ke", "ko", "se", "ne", "ho", "hua", "hoa", "huwa", "mera", "meri", "mere", "mujhe",
        "muje", "mjhe", "main", "mein", "me", "mai", "batao", "batain", "bataen", "bataiye", "btao", "dikhao",
        "dikhaen", "bhai", "jee", "ji", "sir", "please", "plz", "aap", "ap", "tum", "yeh", "ye", "wo", "woh", "is",
        "us", "abhi", "sab", "sabse", "zara", "hisaab", "hisab", "khata", "khaata", "account", "balance", "total",
        "kul", "raqam", "paise", "paisa", "rupay", "rupees", "rs", "hudhud", "hoopoe", "wala", "wali",
        // English
        "how", "much", "many", "what", "who", "whose", "when", "where", "which", "are", "was", "the", "a", "an",
        "my", "i", "of", "for", "from", "to", "does", "did", "do", "owe", "owes", "show", "tell", "can", "you", "your",
        "all", "and", "or", "today", "now",
        // Urdu
        "کتنا", "کتنی", "کتنے", "کیا", "کون", "کس", "کسی", "کب", "ہے", "ہیں", "کا", "کی", "کے", "کو", "سے", "نے",
        "ہو", "ہوا", "میرا", "میری", "میرے", "مجھے", "میں", "بتاؤ", "بتائیں", "بتائیے", "دکھاؤ", "دکھائیں", "حساب",
        "کھاتہ", "کھاتے", "کھاتا", "بیلنس", "کل", "رقم", "پیسے", "روپے", "آپ", "یہ", "وہ", "اس", "اب", "سب", "ہدہد",
        "والا", "والی",
        // Hindi
        "कितना", "कितनी", "कितने", "क्या", "कौन", "किस", "किसी", "कब", "है", "हैं", "का", "की", "के", "को", "से",
        "ने", "हो", "हुआ", "मेरा", "मेरी", "मेरे", "मुझे", "में", "बताओ", "बताइए", "दिखाओ", "हिसाब", "खाता", "बैलेंस",
        "कुल", "रकम", "पैसे", "रुपये", "आप", "यह", "वह", "इस", "अब", "सब", "हुदहुद", "वाला", "वाली",
        // Bengali
        "কত", "কি", "কী", "কে", "কার", "কাকে", "কবে", "আছে", "এর", "আমার", "আমাকে", "বলো", "বলুন", "দেখাও",
        "হিসাব", "খাতা", "ব্যালেন্স", "মোট", "টাকা", "আপনি", "এই", "সব", "হুদহুদ",
        // Indonesian
        "berapa", "apa", "siapa", "kapan", "ada", "saya", "aku", "punya", "tunjukkan", "saldo", "uang", "rupiah",
        "yang", "di", "dari", "ini", "itu", "semua",
        // Arabic
        "كم", "ما", "من", "متى", "هل", "رصيد", "حساب", "عند", "لي", "المبلغ", "اعرض", "الهدهد", "هدهد",
        // Farsi
        "چقدر", "چند", "چه", "کی", "کدام", "است", "مانده", "مرا", "بگو", "همه", "این", "آن", "پول", "تومان",
        // Sindhi
        "ڪيترو", "ڪيترا", "ڪيتري", "ڇا", "ڪير", "ڪڏهن", "آهي", "آهن", "جو", "جي", "کي", "کان", "منهنجو", "کاتو", "سڀ"
    )

    private val STOP_SET: Set<String> by lazy { STOP.map { norm(it) }.toSet() }

    /** The single words of the intents above: never taken for a name either. */
    private val INTENT_SET: Set<String> by lazy {
        listOf(HOW, BACKUP, DRAWER, SALAAM, HELLO).flatten().flatMap { words(it) }.toSet()
    }

    // ------------------------------------------------------------------------------------------------ the names

    /**
     * The customers or suppliers the question names: one when [NameSearch] is confident, else every one the said
     * words recognise (not merely come near) and that scores close to the best, at most five — a choice for the
     * owner, never a guess. Only words left once the question's own words are set aside are looked at, and only
     * words of three letters or more.
     */
    private fun namedIn(said: List<String>, names: List<Name>, topics: List<Topic>): List<Long> {
        if (names.isEmpty()) return emptyList()
        val features = topics.filter { it.feature }.flatMap { words(it.title) }.toSet()
        val asked = VoiceQuestion.keywordsIn(said.joinToString(" "))
        val left = said.filter { w ->
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET &&
                w !in asked && w !in features
        }
        if (left.isEmpty()) return emptyList()
        NameSearch.confidentMatch(names, left) { it.name }?.let { return listOf(it.id) }
        val scored = NameSearch.scoreSpoken(names, left) { it.name }.filter { it.strongHits > 0 }
        val best = scored.firstOrNull()?.score ?: return emptyList()
        if (best <= 0.0) return emptyList()
        return scored.takeWhile { it.score >= best * NEAR_BEST }.take(MAX_NAMES).map { it.item.id }
    }

    private const val NEAR_BEST = 0.6
    private const val MAX_NAMES = 5

    // ------------------------------------------------------------------------------------------------ help pages

    /**
     * The help page the question is about, or null. Each said word is worth what it narrows across the pages
     * (as NameSearch weighs a name's words): a word on one page counts for a lot, a word on every page for almost
     * nothing; a word in a page's title counts twice. A word of four letters or more meets its plural or stem
     * ("cheque", "cheques"). A page that scores less than one word found on two pages is not chosen.
     */
    private fun helpOf(said: List<String>, topics: List<Topic>): String? {
        if (topics.isEmpty()) return null
        val q = said.filter { it.length >= 3 && it !in STOP_SET && norm(it) !in HOW_SET }.distinct()
        if (q.isEmpty()) return null
        val docs = topics.map { t -> words(t.title).toSet() to words(t.text).toSet() }
        fun hit(w: String, set: Set<String>) = set.any { d ->
            d == w || (minOf(d.length, w.length) >= 4 && (d.startsWith(w) || w.startsWith(d)))
        }
        val n = topics.size.toDouble()
        val worth = q.associateWith { w ->
            val found = docs.count { (title, text) -> hit(w, title) || hit(w, text) }
            if (found == 0) 0.0 else ln(1.0 + n / found)
        }
        val scores = docs.map { (title, text) ->
            q.sumOf { w ->
                when {
                    hit(w, title) -> 2.0 * worth.getValue(w)
                    hit(w, text) -> worth.getValue(w)
                    else -> 0.0
                }
            }
        }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return null
        // The least a page must score: one word found on at most two pages, or a word in its title.
        return if (scores[best] >= ln(1.0 + n / 2.0)) topics[best].key else null
    }

    private val HOW_SET: Set<String> by lazy { HOW.flatMap { words(it) }.toSet() }
}
