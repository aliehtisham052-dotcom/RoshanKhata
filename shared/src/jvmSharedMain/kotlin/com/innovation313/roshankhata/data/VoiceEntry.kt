package com.innovation313.roshankhata.data

/**
 * Reads a spoken sentence and works out what entry it describes.
 *
 * "Bilal ko paanch hazaar diye" has to become a party, an amount, and a
 * direction. All three are guessed here and none is acted on: the owner is
 * shown what was understood and presses Save themselves. This is money, and a
 * microphone in a noisy shop is not a witness worth trusting on its own.
 *
 * Everything below runs on the phone. Only the audio leaves it, and only as
 * far as the system's own speech service — this code never sees the customer
 * list travel anywhere.
 */
object VoiceEntry {

    /** What a sentence turned out to mean. Any field may be missing. */
    data class Parsed(
        val partyName: String?,
        val amount: Double?,
        /** true = the shop gave, false = the shop received, null = unclear. */
        val isGiven: Boolean?
    )

    // Both scripts, because the recogniser returns whichever the speaker used.
    private val GAVE = listOf(
        "diye", "diya", "di", "dee", "dena", "dene", "de diye", "de dia",
        "دیے", "دیا", "دی", "دیں", "دینا", "دیئے",
        "gave", "give", "given", "paid", "pay", "lent",
        // Hindi and Bengali, as their recognisers write them.
        "दिए", "दिये", "दिया", "दी", "दीं", "देना", "दे दिए", "दे दिया",
        "দিলাম", "দিয়েছি", "দিল", "দিলো", "দিয়েছে", "দেওয়া",
        // Arabic, Persian, Sindhi and Indonesian (9 Oct audit): the app speaks
        // them, so the microphone must hear their "gave" too. "bayar" is
        // here for the reason "paid" is: the shop paid.
        "أعطيت", "اعطيت", "أعطينا", "دفعت", "سلمت", "سلّمت", "أقرضت",
        "دادم", "دادیم", "داده", "پرداختم", "قرض دادم",
        "ڏنا", "ڏنو", "ڏني", "ڏنيون", "ڏنم", "ڏيڻا",
        "kasih", "beri", "memberi", "berikan", "bayar", "membayar", "pinjamkan", "meminjamkan"
    )
    private val GOT = listOf(
        "liye", "liya", "li", "mile", "mila", "mili", "wapis", "wasool",
        "aaye", "aya",
        "لیے", "لیا", "لی", "لیں", "ملے", "ملا", "ملی", "واپس", "وصول", "آئے",
        "got", "received", "took", "taken", "collected", "returned", "repaid",
        "लिए", "लिये", "लिया", "ली", "लीं", "मिले", "मिला", "मिली", "वापस", "वसूल", "आए", "आया",
        "পেলাম", "পেয়েছি", "নিলাম", "নিয়েছি", "ফেরত", "জমা",
        "استلمت", "أخذت", "اخذت", "قبضت", "تسلمت", "حصلت", "وصلني",
        "گرفتم", "گرفتیم", "گرفته", "دریافت", "دریافت کردم",
        "ورتا", "ورتو", "ورتي", "ورتم", "مليا", "مليو", "ملي",
        "terima", "menerima", "diterima", "dapat", "mendapat", "ambil", "mengambil",
        // Deliberately here as well as in GAVE. "Gave back" and "paid back"
        // read as both directions at once, so the sentence is reported as
        // unclear and the owner is asked — which is the honest answer, since
        // neither phrase says who handed what to whom.
        "gave back", "paid back"
    )

    /**
     * Urdu and Arabic-Indic digits, so ٥٠٠٠ and ۵۰۰۰ read as 5000 — and the
     * Hindi (५०००) and Bengali (৫০০০) ones, for the same reason.
     */
    private val EASTERN_DIGITS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9',
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
    )

    /** Spoken numbers, in the forms a shopkeeper actually uses. */
    private val UNITS = mapOf(
        "ek" to 1.0, "aik" to 1.0, "ایک" to 1.0,
        "do" to 2.0, "دو" to 2.0,
        "teen" to 3.0, "tin" to 3.0, "تین" to 3.0,
        "char" to 4.0, "chaar" to 4.0, "چار" to 4.0,
        "panch" to 5.0, "paanch" to 5.0, "پانچ" to 5.0,
        "che" to 6.0, "chay" to 6.0, "چھ" to 6.0,
        "saat" to 7.0, "سات" to 7.0,
        "aath" to 8.0, "ath" to 8.0, "آٹھ" to 8.0,
        "nau" to 9.0, "no" to 9.0, "نو" to 9.0,
        "das" to 10.0, "dus" to 10.0, "دس" to 10.0,
        "bees" to 20.0, "بیس" to 20.0,
        "pachas" to 50.0, "pachaas" to 50.0, "پچاس" to 50.0,
        // One and a half and two and a half are single words (16 Nov 2026, audit T30: "dedh hazar" was read as 1000,
        // the bare multiplier, a wrong figure with no warning).
        "dedh" to 1.5, "derh" to 1.5, "dairh" to 1.5, "ڈیڑھ" to 1.5, "ڈیڑہ" to 1.5,
        "dhai" to 2.5, "dhaai" to 2.5, "adhai" to 2.5, "arhai" to 2.5, "arhaai" to 2.5,
        "ڈھائی" to 2.5, "اڑھائی" to 2.5, "ڈھائ" to 2.5,

        // English, for an owner who has set the app to English. Written out to
        // nineteen and then by tens, because English says "twenty five" where
        // Urdu says "pachees" — the tens and the units arrive as two words and
        // are added together by the reader below.
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0,
        "fourteen" to 14.0, "fifteen" to 15.0, "sixteen" to 16.0,
        "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0,
        "sixty" to 60.0, "seventy" to 70.0, "eighty" to 80.0, "ninety" to 90.0
    )

    private val MULTIPLIERS = mapOf(
        "sau" to 100.0, "سو" to 100.0,
        "hazar" to 1000.0, "hazaar" to 1000.0, "hazār" to 1000.0, "ہزار" to 1000.0,
        "lakh" to 100_000.0, "lac" to 100_000.0, "لاکھ" to 100_000.0,
        // Crore was missing on both sides. A shop that deals in lakhs
        // eventually says the next word up.
        "crore" to 10_000_000.0, "karor" to 10_000_000.0, "کروڑ" to 10_000_000.0,
        "hundred" to 100.0, "thousand" to 1000.0, "million" to 1_000_000.0
    )

    /**
     * Words that change the number after them (16 Nov 2026, audit T30): "sawa sau" 125, "saade teen hazar" 3,500,
     * "paune do lakh" 1,75,000. Applied only when a number word or a scale follows straight after; alone they are
     * ignored (Urdu سوا also means "apart from"). Roman Urdu and Urdu only: other languages' words wait for a native
     * check (T35).
     */
    private val MODIFIERS = mapOf(
        "sawa" to 0.25, "sava" to 0.25, "سوا" to 0.25,
        "saade" to 0.5, "sade" to 0.5, "saadhe" to 0.5, "sadhe" to 0.5, "saarhe" to 0.5, "ساڑھے" to 0.5,
        "paune" to -0.25, "pone" to -0.25, "paunay" to -0.25, "پونے" to -0.25
    )

    /**
     * A scale written on to the digits (16 Nov 2026, audit T30): "5k", "1.5k", "2lac", "5rb". "5k" was read as 5 —
     * the entry form opened with Rs 5. Only letters joined to the figure with no space count ("500 k saman" is "500 ke").
     */
    private val JOINED_SCALE = mapOf("k" to 1000.0)

    private fun joinedScale(letters: String): Double? =
        if (letters.isEmpty()) null else JOINED_SCALE[letters] ?: MULTIPLIERS[letters] ?: DIGIT_MULTIPLIERS[letters]

    /** A figure with its scale joined on ("5k", "2lac"): a number, never part of a name. */
    private fun isJoinedFigure(word: String): Boolean {
        val i = word.indexOfFirst { !it.isDigit() && it != '.' && it != ',' }
        return i > 0 && word[0].isDigit() && joinedScale(word.substring(i)) != null
    }

    /**
     * Scale words in Persian, Arabic and Sindhi, honoured ONLY straight after
     * digits: "۵ هزار" and "5 ألف" are 5000 (1 Oct).
     *
     * Deliberately kept out of [MULTIPLIERS], which [fromWords] also reads.
     * The number words before them (پنج، خمسة، پنج) are not in [UNITS], so
     * "پنج هزار" would be read as a bare هزار — 1000 instead of 5000, a wrong
     * figure with no warning. Unread, that sentence gives no amount and the
     * owner types it, which is the safe outcome. Adding these languages'
     * number words is the real fix, and needs a native speaker to check.
     *
     * Persian and Sindhi write هزار with ه, not Urdu's ہ, so the Urdu entry
     * never matched them. Sindhi's crore is spelled with its own ڪ.
     */
    private val DIGIT_MULTIPLIERS = mapOf(
        "صد" to 100.0, "هزار" to 1000.0, "میلیون" to 1_000_000.0,   // Persian (هزار also Sindhi)
        "مئة" to 100.0, "مائة" to 100.0,                               // Arabic
        "ألف" to 1000.0, "الف" to 1000.0, "آلاف" to 1000.0, "الاف" to 1000.0,
        "مليون" to 1_000_000.0, "ملايين" to 1_000_000.0,
        "لک" to 100_000.0, "ڪروڙ" to 10_000_000.0,                     // Sindhi
        // Hindi, Bengali and Indonesian (21 Oct 2026, the nine-language
        // review): "राम को 5 हज़ार दिए" was read as 5. Speech recognisers in
        // these languages write the figure as digits and the scale as a word.
        "सौ" to 100.0, "हज़ार" to 1000.0, "हजार" to 1000.0, "लाख" to 100_000.0,
        "करोड़" to 10_000_000.0, "करोड" to 10_000_000.0,                // Hindi
        "শ" to 100.0, "শো" to 100.0, "হাজার" to 1000.0, "লাখ" to 100_000.0,
        "লক্ষ" to 100_000.0, "কোটি" to 10_000_000.0,                    // Bengali
        "ratus" to 100.0, "ribu" to 1000.0, "rb" to 1000.0, "juta" to 1_000_000.0,
        "jt" to 1_000_000.0, "miliar" to 1_000_000_000.0                // Indonesian
    ).mapKeys { nfc(it.key) }

    /**
     * Hindi number words in Devanagari (16 Nov 2026, audit T35): एक to दस, बीस, पचास, सौ, हज़ार, लाख, करोड़ as omniglot.com's
     * "Numbers in Hindi" gives them, and डेढ़ (1½), ढाई (2½), सवा (+¼), साढ़े (+½), पौने (−¼) as UNC's "A Door Into Hindi",
     * lesson 14, explains them (both read 16 Nov 2026); छः beside छह (VoiceQuestion's count words). Read only by [fromHindi],
     * which takes a figure only when a scale (सौ, हज़ार, लाख, करोड़) or a rupee word says it is money: दो is also "give", एक
     * also "a".
     */
    private val HINDI_UNITS = mapOf(
        "एक" to 1.0, "दो" to 2.0, "तीन" to 3.0, "चार" to 4.0, "पांच" to 5.0, "पाँच" to 5.0, "छह" to 6.0, "छः" to 6.0,
        "सात" to 7.0, "आठ" to 8.0, "नौ" to 9.0, "दस" to 10.0, "बीस" to 20.0, "पचास" to 50.0, "डेढ़" to 1.5, "ढाई" to 2.5
    ).mapKeys { nfc(it.key) }
    private val HINDI_SCALES = mapOf(
        "सौ" to 100.0, "हज़ार" to 1000.0, "हजार" to 1000.0, "लाख" to 100_000.0, "करोड़" to 10_000_000.0, "करोड" to 10_000_000.0
    ).mapKeys { nfc(it.key) }
    private val HINDI_MODIFIERS = mapOf("सवा" to 0.25, "साढ़े" to 0.5, "पौने" to -0.25).mapKeys { nfc(it.key) }
    private val HINDI_RUPEES = setOf("रुपये", "रुपए", "रुपया", "रुपयों", "रु").map { nfc(it) }.toSet()

    /**
     * Indonesian number words (16 Nov 2026, audit T35), as Northern Illinois University's SEAsite "Indonesian numbers made easy"
     * gives them (read 16 Nov 2026): satu to sembilan; se- for one of ten, eleven, a hundred, a thousand (sepuluh, sebelas,
     * seratus, seribu); belas after a unit for eleven to nineteen, puluh for the tens, ratus for the hundreds, ribu for the
     * thousands; juta (a million) as the digit scales already read it, sejuta by the same se- rule. Read only for an
     * Indonesian sentence ("dua" is also Urdu's prayer, "lima" a city), and only with a scale or a rupiah word beside it.
     */
    private val ID_UNITS = mapOf("satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5, "enam" to 6, "tujuh" to 7,
        "delapan" to 8, "sembilan" to 9)
    private val ID_WORDS = ID_UNITS.keys + setOf("sepuluh", "sebelas", "belas", "puluh", "seratus", "ratus", "seribu", "ribu",
        "sejuta", "juta")
    private val ID_RUPIAH = setOf("rupiah", "rp")

    /** One spelling of a word: Hindi's ज़ and ड़ arrive both as one letter and as letter + dot. */
    private fun nfc(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    /**
     * Persian number words (1 Oct), read ONLY when the microphone was set to
     * Persian ([parse]'s language starts with "fa"). Never mixed into
     * [UNITS]: سی is thirty in Persian but an everyday particle in Urdu
     * ("تھوڑی سی"), and would silently add 30 to an Urdu sentence.
     *
     * Persian joins parts with و ("پنج هزار و پانصد" = 5500); و is not a
     * number word, so the reader simply passes over it. Arabic and Sindhi
     * number words are still NOT read (gender and dual forms in Arabic;
     * Sindhi needs a native speaker) — only their digit + scale forms are.
     */
    private val PERSIAN_UNITS = mapOf(
        "یک" to 1.0, "يك" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0, "پنج" to 5.0,
        "شش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0, "ده" to 10.0,
        "یازده" to 11.0, "دوازده" to 12.0, "سیزده" to 13.0, "چهارده" to 14.0,
        "پانزده" to 15.0, "شانزده" to 16.0, "هفده" to 17.0, "هجده" to 18.0,
        "هیجده" to 18.0, "نوزده" to 19.0,
        "بیست" to 20.0, "سی" to 30.0, "چهل" to 40.0, "پنجاه" to 50.0,
        "شصت" to 60.0, "هفتاد" to 70.0, "هشتاد" to 80.0, "نود" to 90.0,
        // The hundreds are single words; each counts as a unit, the way
        // "pachees" does, so "پانصد هزار" is 500 thousands.
        "یکصد" to 100.0, "دویست" to 200.0, "سیصد" to 300.0,
        "چهارصد" to 400.0, "پانصد" to 500.0, "ششصد" to 600.0, "هفتصد" to 700.0,
        "هشتصد" to 800.0, "نهصد" to 900.0
    )

    private val PERSIAN_MULTIPLIERS = mapOf(
        // صد folds like "sau": "پنج صد" (written apart) is 500, not 105.
        "صد" to 100.0,
        "هزار" to 1000.0, "میلیون" to 1_000_000.0, "ميليون" to 1_000_000.0
    )

    /**
     * A count written as a word — "do", "teen", "دو", "three", "سه" — or null (3 Nov 2026, the WhatsApp order paste:
     * "do bori DAP"). The same words the spoken amounts are read with; scales ("sau", "hazaar") are not counts here.
     */
    fun countWord(word: String): Double? {
        val w = nfc(word.trim())
        return UNITS[w.lowercase()] ?: UNITS[w] ?: PERSIAN_UNITS[w]
    }

    /**
     * Read [spoken] against the shop's own customer names.
     *
     * [knownNames] is passed in rather than looked up, so this stays pure and
     * can be checked without a database behind it.
     */
    fun parse(spoken: String, knownNames: List<String>, language: String? = null): Parsed {
        val text = normalise(spoken)
        val lang = language?.lowercase().orEmpty()
        val persian = lang.startsWith("fa")
        // Indonesian writes thousands with a dot: "5.000" is five thousand (21 Oct).
        val dotGroups = lang.startsWith("id") || lang.startsWith("in")
        return Parsed(
            partyName = findParty(text, knownNames),
            amount = findAmount(text, persian, dotGroups),
            isGiven = findDirection(text)
        )
    }

    private fun normalise(s: String): String {
        val sb = StringBuilder()
        for (ch in nfc(s).lowercase()) sb.append(
            when (ch) {
                // Arabic and Persian figures group with ٬ and take ٫ as the
                // decimal point: "٥٬٠٠٠" was read as 5 (21 Oct).
                '\u066C' -> ','
                '\u066B' -> '.'
                else -> EASTERN_DIGITS[ch] ?: ch
            }
        )
        return sb.toString()
    }

    /**
     * Words that are grammar, not names.
     *
     * This list is the whole reason the matcher can be trusted. "میں نے"
     * reduces to the skeleton "mn" — and so do Moon, Amin, Meena, Mannan and
     * Munna. Because the old matcher poured the entire sentence into one run
     * of letters and looked for the name anywhere inside it, every sentence
     * beginning "میں نے" silently matched whichever of those names the shop
     * had on its books, and the owner was shown a stranger above their money.
     *
     * Grammar is stripped before any name is looked for, so it can never
     * stand in for one. Words that appear inside real names — جی، بھائی،
     * صاحب، چاچا — are deliberately absent: "Abu G" is a customer.
     */
    private val STOPWORDS = setOf(
        "میں", "مین", "نے", "کو", "کا", "کی", "کے", "سے", "پر", "کہ",
        "ہے", "ہیں", "ہوں", "ہوا", "ہوئے", "تھا", "تھی", "تھے",
        "اور", "بھی", "تو", "یہ", "وہ", "روپے", "روپیہ",
        // Hindi and Bengali grammar, for the same reason.
        "मैं", "मैंने", "ने", "को", "का", "की", "के", "से", "पर", "कि", "है", "हैं",
        "और", "भी", "तो", "यह", "वह", "रुपये", "रुपए", "रुपया",
        "আমি", "কে", "থেকে", "কাছ", "এর", "ও", "এবং", "টাকা",
        // Arabic, Persian, Sindhi and Indonesian grammar (9 Oct audit).
        "أنا", "انا", "إلى", "الى", "على", "عن", "و", "روبية", "ريال", "درهم",
        "من", "به", "از", "را", "که", "تومان", "روپیه",
        "مون", "کي", "کان", "جو", "جي", "۽", "آهي", "آهن", "رپيا",
        "saya", "aku", "kepada", "dari", "untuk", "dan", "sudah", "telah", "rupiah",
        "main", "mein", "ne", "ko", "ka", "ki", "ke", "se", "par", "keh",
        // The same pronouns as one word, which is how they are actually
        // transcribed. "Main ne" was covered; "Maine" was not, and that is
        // what comes back when the sentence is spoken at normal speed. Its
        // consonants are m-n — the same as Moon, Mona, Mani or Amna — so it
        // was matching customers by name. The rest of the family is here for
        // the same reason, before they are found the same way.
        "maine", "mainay", "mainey", "mene", "menay", "meine",
        "tumne", "tumnay", "usne", "usnay", "humne", "humnay",
        "unhone", "unhonay", "isne", "isnay",
        // Words that stand in for a name instead of being one. The logs
        // caught all of these being taken as customers: "Maine kisi ko 50000
        // diya" searched the book for a man called Kisi, and "Mere original
        // company" for one called Mere.
        "kisi", "kis", "kuch", "kuchh", "koi", "kissi",
        "mera", "meri", "mere", "apna", "apni", "apne",
        "کسی", "کچھ", "کوئی", "میرا", "میری", "میرے", "اپنا", "اپنی", "اپنے",
        "hai", "hain", "hun", "tha", "thi", "the", "aur", "bhi", "to",
        "rupay", "rupaye", "rupee", "rupees", "rs",
        // English grammar. Same reasoning as above: these are words a sentence
        // is built from, never a customer. Words that could be part of a name
        // are left out.
        "i", "you", "he", "she", "we", "they", "him", "her", "them",
        "a", "an", "of", "for", "from", "and", "is", "are", "was", "were",
        "has", "have", "had", "it", "this", "that", "on", "in", "at",
        "total", "amount", "money", "cash"
    )

    /** Split on anything that is not a letter or a digit, in either script. */
    /**
     * Words, with their vowel signs. \p{M} matters: Hindi and Bengali write
     * most vowels as combining marks (दिए is द + ि + ए), and splitting on
     * anything but \p{L} cut every such word in two - "दिए" became "दि" and
     * "ए" - so the Hindi and Bengali verbs, there since 4 Oct, never matched,
     * and a name like राम kept only its consonants. Found by the 9 Oct test
     * for those languages.
     */
    private fun tokenise(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{M}\\p{Nd}]+")).filter { it.isNotBlank() }

    /** Grammar, figures and verbs — everything a name is not. */
    /**
     * The words of [spoken] that could be part of a name.
     *
     * Grammar, figures and the giving and taking verbs are already known here
     * and thrown away, so what comes back is what the owner would have typed
     * into the search box if they had typed instead of spoken. Exposed so the
     * picker can search with it: when this reader cannot name the customer
     * with confidence, the next best thing is to search for them the way any
     * other screen would, not to hand back the whole book.
     */
    fun nameWords(spoken: String): List<String> =
        tokenise(normalise(spoken)).filterNot { isNoise(it) }

    /**
     * Which of the recogniser's answers to act on, by index.
     *
     * It offers up to five and this app took the first, on the assumption that
     * first means best. Three logs of real entries say otherwise. The list is
     * not even ordered by the recogniser's own confidence — one attempt acted
     * on a candidate scored 0.70 while the third in the list stood at 0.75 —
     * and the first is often the one that lost the end of the sentence:
     * "Maine Memorial School" was acted on and refused for having no amount,
     * while every other candidate carried the 5000. Another attempt heard
     * "Kripa", which answers nobody in the book, when three of its five
     * candidates said "khurpa", which fifty customers carry.
     *
     * So each candidate is asked what it would actually produce. An amount is
     * what makes an entry possible at all, so it outweighs everything else; a
     * direction is worth a little; and beyond that the candidate whose words
     * answer somebody in this book beats the one whose words answer nobody.
     *
     * Confidence is deliberately not consulted. The recogniser gives the
     * truncated variant the HIGHER score — 0.89 for "Maine Memorial School"
     * against 0.83 for the sentence with the figure in it — so trusting it
     * would sharpen exactly the wrong edge.
     *
     * Ties keep the recogniser's own order, so nothing changes for the many
     * sentences whose candidates differ only in how the verb was spelled.
     *
     * @param nameScore how well a set of spoken words answers the book. Passed
     *   in rather than reached for, so this rule can be checked without one.
     */
    fun bestCandidate(
        candidates: List<String>,
        knownNames: List<String>,
        nameScore: (List<String>) -> Double
    ): Int {
        if (candidates.size < 2) return 0

        // Most candidates differ only in the verb — "diye" against "di hai" —
        // and leave the name alone. Asking the book once per distinct set of
        // name words rather than once per candidate keeps this to a single
        // pass in the ordinary case, against a book of eleven hundred.
        val asked = HashMap<List<String>, Double>()

        var bestAt = 0
        var best = Double.NEGATIVE_INFINITY

        for ((i, candidate) in candidates.withIndex()) {
            val parsed = parse(candidate, knownNames)
            val amount = parsed.amount

            var score = 0.0
            if (amount != null && amount > 0.0) score += AMOUNT_IS_EVERYTHING
            if (parsed.isGiven != null) score += DIRECTION_HELPS

            val words = nameWords(candidate)
            if (words.isNotEmpty()) score += asked.getOrPut(words) { nameScore(words) }

            if (score > best) {
                best = score
                bestAt = i
            }
        }
        return bestAt
    }

    /** Without a figure there is no entry to make, whatever else was heard. */
    private const val AMOUNT_IS_EVERYTHING = 100.0

    /** Worth having, but the owner confirms it before anything is saved. */
    private const val DIRECTION_HELPS = 10.0

    private fun isNoise(word: String): Boolean =
        word in STOPWORDS ||
            UNITS.containsKey(word) ||
            MULTIPLIERS.containsKey(word) ||
            DIGIT_MULTIPLIERS.containsKey(word) ||
            MODIFIERS.containsKey(word) ||
            HINDI_UNITS.containsKey(word) || HINDI_SCALES.containsKey(word) || HINDI_MODIFIERS.containsKey(word) ||
            isJoinedFigure(word) ||
            word in GAVE ||
            word in GOT ||
            word.all { it.isDigit() }

    /**
     * Which customer the sentence is about, or null when it cannot be told.
     *
     * The sentence is cut into words, grammar and figures are thrown away, and
     * what remains is matched a **whole word at a time**. Matching runs of
     * words rather than a flat run of letters is what stops a name from being
     * found straddling the middle of two unrelated ones.
     *
     * Null is returned both when nothing matches and when two customers match
     * equally well. Guessing between two people would put the money on a
     * stranger's page; the caller opens the customer list instead, which costs
     * one tap and cannot be wrong.
     */
    private fun findParty(text: String, knownNames: List<String>): String? {
        val words = tokenise(text).filterNot { isNoise(it) }
        if (words.isEmpty()) return null

        // Every run of consecutive words, in both representations. A run is
        // whole words by construction, so boundaries are respected for free.
        val runsSkeleton = mutableListOf<String>()
        val runsSounds = mutableListOf<String>()
        for (i in words.indices) {
            val sk = StringBuilder()
            val so = StringBuilder()
            for (j in i until words.size) {
                sk.append(skeleton(words[j]))
                so.append(sounds(words[j]))
                runsSkeleton += sk.toString()
                runsSounds += so.toString()
            }
        }

        var best: String? = null
        var bestScore = 0
        var tied = false
        for (name in knownNames) {
            val s = scoreName(name, runsSkeleton, runsSounds)
            if (s <= 0) continue
            when {
                s > bestScore -> {
                    best = name
                    bestScore = s
                    tied = false
                }
                s == bestScore && !name.equals(best, ignoreCase = true) -> tied = true
            }
        }
        return if (tied) null else best
    }

    /**
     * How well a stored name fits what was said. Zero means it does not.
     *
     * A whole run equalling the name outranks everything; a name only partly
     * spoken scores by how much of it was heard, and needs three consonants
     * before it counts at all.
     *
     * A short skeleton is not the problem, and a guard against one does real
     * damage. Two consonants was briefly treated as too thin to trust unless
     * the vowels agreed too — which broke the very thing the skeleton is for.
     * A shop saves "Abu G" in Latin and says it in Urdu, where the short
     * vowels are not written at all; asking the vowels to agree across two
     * scripts asks for something neither script can give. The tests caught it.
     *
     * The name that went wrong was never a short name. It was a grammar word
     * being read as one: "Maine" reduces to m-n and so does Moon, and with
     * the pronoun still in play a whole match on it outscored a partial match
     * on the customer actually named. Take the pronoun out and there is
     * nothing left for Moon to match — "Ahmad Ali" wins on its own merit.
     * Grammar belongs in the stopword list. Nothing here needed changing.
     */
    private fun scoreName(
        name: String,
        runsSkeleton: List<String>,
        runsSounds: List<String>
    ): Int {
        val sk = skeleton(name)
        if (sk.length >= 2) {
            if (runsSkeleton.any { it == sk }) return 1000 + sk.length
            // Part of a long stored name was spoken: "Abdul Rahman" standing
            // in for "Abdul Rahman Chacha Kareem Mallay Wali".
            val partial = runsSkeleton
                .filter { it.length >= 3 && sk.startsWith(it) }
                .maxByOrNull { it.length }
            return partial?.length ?: 0
        }
        // Names too short to leave two consonants behind — Ali is only "l".
        // These were unmatchable before. They are compared with their vowels
        // kept and only on an exact whole run, which is safe where a bare
        // consonant would not be.
        val so = sounds(name)
        return if (so.isNotEmpty() && runsSounds.any { it == so }) 1000 + so.length else 0
    }

    /** Urdu letters to the sound a Latin spelling would use. */
    private val LETTERS = mapOf(
        'ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t",
        'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d",
        'ڈ' to "d", 'ذ' to "z", 'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z",
        'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ک' to "k", 'ك' to "k",
        'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n", 'و' to "u",
        'ہ' to "h", 'ھ' to "h", 'ة' to "h", 'ء' to "", 'ی' to "i", 'ي' to "i",
        'ے' to "e", 'أ' to "a", 'إ' to "a", 'ؤ' to "u", 'ئ' to "i"
    )

    /** Sounds a Pakistani ear treats as the same when spelling a name. */
    private val SAME_SOUND = mapOf('g' to 'j', 'q' to 'k', 'v' to 'b', 'c' to 'k')

    private const val VOWELS = "aeiou"

    /**
     * The consonants of a name, in whichever script it was written.
     *
     * Vowels go because Urdu does not write the short ones; doubles collapse
     * because Urdu marks them rather than repeating the letter; and g/j, q/k,
     * v/b, c/k fold together because a name spelled one way is routinely
     * spoken the other.
     */
    private fun skeleton(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val mapped = LETTERS[ch] ?: if (ch.isLetter()) ch.toString() else ""
            sb.append(mapped)
        }
        val folded = StringBuilder()
        for (ch in sb) {
            if (!ch.isLetter()) continue
            val f = SAME_SOUND[ch] ?: ch
            if (f in VOWELS) continue
            if (folded.isEmpty() || folded.last() != f) folded.append(f)
        }
        return folded.toString()
    }

    /**
     * The same transliteration as [skeleton], but with the vowels left in.
     *
     * Used only for names so short that dropping vowels leaves nothing to
     * match on. "Ali" and "علی" both become "ali"; the skeleton of either is
     * the single letter "l", which is far too little to risk a match on.
     */
    private fun sounds(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val mapped = LETTERS[ch] ?: if (ch.isLetter()) ch.toString() else ""
            sb.append(mapped)
        }
        val folded = StringBuilder()
        for (ch in sb) {
            if (!ch.isLetter()) continue
            val f = SAME_SOUND[ch] ?: ch
            if (folded.isEmpty() || folded.last() != f) folded.append(f)
        }
        return folded.toString()
    }

    /**
     * The amount, whether spoken as digits or as words.
     *
     * Digits win when present: someone who says "5000" means 5000, and no
     * word-reading can improve on that.
     */
    private fun findAmount(text: String, persian: Boolean = false, dotGroups: Boolean = false): Double? {
        val figure = if (dotGroups) Regex("\\d{1,3}(\\.\\d{3})+(,\\d+)?|\\d+(,\\d+)?") else Regex("\\d[\\d,]*(\\.\\d+)?")
        figure.find(text)?.let { m ->
            val cleaned = if (dotGroups) m.value.replace(".", "").replace(',', '.') else m.value.replace(",", "")
            cleaned.toDoubleOrNull()?.let { digits ->
                // "5k", "2lac": a scale joined to the figure, then a non-letter or the end.
                val rest = text.substring(m.range.last + 1)
                val joined = rest.takeWhile { it.isLetter() }
                joinedScale(joined)?.let { return digits * it }
                // "5 hazaar" — a digit followed by a multiplier word.
                val after = text.substring(m.range.last + 1).trim().split(" ").firstOrNull()
                    ?.trimEnd('.', ',', '।', '؟', '?', '!', '،')
                val mult = MULTIPLIERS[after] ?: DIGIT_MULTIPLIERS[after]
                return if (mult != null) digits * mult else digits
            }
        }
        // Number words in Hindi and Indonesian (16 Nov 2026, audit T35): their own readers, which say nothing when unsure.
        val ws = text.split(' ', ',', '،').map { it.trim('।', '.', '?', '!', '؟', '،') }.filter { it.isNotEmpty() }
        if (ws.any { it in HINDI_UNITS || it in HINDI_SCALES || it in HINDI_MODIFIERS }) return fromHindi(ws)
        if (dotGroups && ws.any { it in ID_WORDS }) return fromIndonesian(ws)
        return fromWords(text, persian)
    }

    /**
     * Hindi number words (16 Nov 2026, audit T35): "पांच सौ" 500, "डेढ़ हज़ार" 1,500, "सवा सौ" 125, "साढ़े तीन हज़ार" 3,500, "पौने दो
     * लाख" 1,75,000, "बीस रुपये" 20. The first run of number words is read; it is money only with a scale in it or a rupee word
     * straight after it, and a scale that starts the run must stand after a word that is grammar or a verb — a number word this
     * list does not have ("पच्चीस हज़ार") is never read as a bare thousand. Unsure: no amount (the owner types it).
     */
    private fun fromHindi(ws: List<String>): Double? {
        val start = ws.indexOfFirst { it in HINDI_UNITS || it in HINDI_SCALES || it in HINDI_MODIFIERS }
        if (start < 0) return null
        var total = 0.0
        var current = 0.0
        var pending = 0.0
        var scaled = false
        var counted = false
        var j = start
        while (j < ws.size) {
            val w = ws[j]
            val mod = HINDI_MODIFIERS[w]
            val unit = HINDI_UNITS[w]
            val scale = HINDI_SCALES[w]
            if (mod != null) {
                pending = mod
            } else if (unit != null) {
                if (counted && current != 0.0 && !scaled) return null // two numbers side by side: unsure
                current += unit + pending
                pending = 0.0
                counted = true
            } else if (scale != null) {
                if (j == start) {
                    val before = ws.getOrNull(start - 1)
                    if (before != null && before !in STOPWORDS && before !in GAVE && before !in GOT) return null
                }
                val take = if (current == 0.0) 1.0 + pending else current
                pending = 0.0
                scaled = true
                counted = true
                if (scale >= 1000.0) {
                    total += take * scale
                    current = 0.0
                } else {
                    current = take * scale
                }
            } else {
                break
            }
            j++
        }
        if (!counted) return null
        if (!scaled && ws.getOrNull(j) !in HINDI_RUPEES) return null
        return (total + current).takeIf { it > 0.0 }
    }

    /**
     * Indonesian number words (16 Nov 2026, audit T35): "lima ribu" 5,000, "dua puluh lima ribu" 25,000, "seratus lima puluh
     * ribu" 150,000, "satu juta" 1,000,000. The first run of number words is read by its own grammar — a unit before belas,
     * puluh or ratus, the group before ribu or juta — and is money only with ratus, ribu or juta in it or a rupiah word after.
     * Two units side by side, or a scale with nothing to count: unsure, no amount.
     */
    private fun fromIndonesian(ws: List<String>): Double? {
        val start = ws.indexOfFirst { it in ID_WORDS }
        if (start < 0) return null
        var total = 0.0
        var group = 0.0
        var unit: Int? = null
        var scaled = false
        var j = start
        loop@ while (j < ws.size) {
            val w = ws[j]
            val u = ID_UNITS[w]
            when {
                u != null -> {
                    if (unit != null) return null
                    unit = u
                }
                w == "sepuluh" || w == "sebelas" -> {
                    if (unit != null) return null
                    group += if (w == "sepuluh") 10.0 else 11.0
                }
                w == "belas" || w == "puluh" || w == "ratus" -> {
                    val n = unit ?: return null
                    group += when (w) { "belas" -> 10.0 + n; "puluh" -> 10.0 * n; else -> 100.0 * n }
                    unit = null
                    if (w == "ratus") scaled = true
                }
                w == "seratus" -> {
                    if (unit != null) return null
                    group += 100.0
                    scaled = true
                }
                w == "ribu" || w == "juta" -> {
                    val n = group + (unit ?: 0)
                    if (n <= 0.0) return null
                    total += n * (if (w == "ribu") 1000.0 else 1_000_000.0)
                    group = 0.0
                    unit = null
                    scaled = true
                }
                w == "seribu" || w == "sejuta" -> {
                    if (unit != null || group != 0.0) return null
                    total += if (w == "seribu") 1000.0 else 1_000_000.0
                    scaled = true
                }
                else -> break@loop
            }
            j++
        }
        if (!scaled && ws.getOrNull(j) !in ID_RUPIAH) return null
        return (total + group + (unit ?: 0)).takeIf { it > 0.0 }
    }

    /**
     * Spoken numbers: "paanch hazaar" is 5000, "do lakh" is 200000, and a
     * bare "hazaar" on its own is 1000.
     *
     * A multiplier of a thousand or more closes off what came before it and
     * banks the result; a smaller one — a hundred — folds into what is being
     * built. This is what lets English say a figure the way English says it.
     * "One hundred thousand" is a hundred thousands, not a hundred and then a
     * thousand: reading it as the latter, which the earlier version did, gave
     * 1,100 for a figure a shopkeeper meant as 100,000.
     */
    private fun fromWords(text: String, persian: Boolean = false): Double? {
        var total = 0.0
        var current = 0.0
        var seen = false
        // "sawa", "saade", "paune": waits for the very next word, which must be a number word or a scale.
        var pending = 0.0

        var before: String? = null
        for (w in text.split(" ", "،", ",")) {
            if (w.isEmpty()) continue
            val prev = before
            before = w
            val mod = MODIFIERS[w]
            if (mod != null) {
                pending = mod
                continue
            }
            val unit = UNITS[w] ?: if (persian) PERSIAN_UNITS[w] else null
            if (unit != null) {
                current += unit + pending
                pending = 0.0
                seen = true
                continue
            }
            val mult = MULTIPLIERS[w] ?: (if (persian) PERSIAN_MULTIPLIERS[w] else null)
            if (mult == null) {
                pending = 0.0
                continue
            }
            // A scale with no number before it, straight after a word that is no grammar and no verb (16 Nov 2026, audit T35):
            // "pachees hazar" — a number word this list does not have — was read as a bare thousand, 1,000 for 25,000. Unsure:
            // no amount, the owner types it.
            if (current == 0.0 && pending == 0.0 && prev != null && prev !in STOPWORDS && prev !in GAVE && prev !in GOT &&
                !prev.all { it.isDigit() } && !(persian && PERSIAN_UNITS.containsKey(prev))
            ) return null
            seen = true
            // A multiplier with nothing before it counts once: "hazaar" is one
            // thousand, not none; "sawa sau" is one and a quarter hundreds.
            val take = if (current == 0.0) 1.0 + pending else current
            pending = 0.0
            if (mult >= 1000.0) {
                total += take * mult
                current = 0.0
            } else {
                current = take * mult
            }
        }
        return if (seen) total + current else null
    }

    /**
     * Which way the money went.
     *
     * Matched a whole word at a time. The old test padded the sentence with
     * spaces and looked for " دیا " inside it, with an endsWith as a fallback —
     * which meant "میں نے احمد کو پانچ ہزار دی ہے" found nothing at all: the
     * verb was "دی", and the sentence ended on "ہے" rather than the verb.
     * Splitting into words removes the need for either trick.
     *
     * Null when the sentence does not say. A guess here would put the amount
     * on the wrong side of the ledger, which is worse than asking.
     */
    private fun findDirection(text: String): Boolean? {
        val words = tokenise(text)
        fun spoken(terms: List<String>) = terms.any { term ->
            if (term.contains(' ')) text.contains(term) else words.contains(term)
        }
        val gave = spoken(GAVE)
        val got = spoken(GOT)
        val said = when {
            gave && !got -> true
            got && !gave -> false
            else -> null
        } ?: return null
        // "Ali ne 500 diye" is Ali giving: the money came IN (12 Nov 2026). The verb says who handed money over only
        // together with who did it, and Urdu and Hindi mark the doer with ne; "main ne", "maine" and "ham ne" are the
        // shop, any other doer the customer. It read as "I gave" before, the wrong side of the book.
        return if (customerActs(words)) !said else said
    }

    /** The doer marked with ne is someone other than the shop: "Ali ne", "us ne", "usne", "उसने". */
    private fun customerActs(words: List<String>): Boolean {
        if (words.any { it in THEY_DID }) return true
        // "Ali gave me 750", "Ali kasih saya 500": the shop is the one handed the money (13 Nov 2026, the owner's test
        // read it as "I gave"). English and Indonesian say it with "me" or "us" right after the verb; Bengali and
        // Persian with "to me" anywhere in the sentence.
        if (words.indices.any { i -> words[i] in MOVED && words.getOrNull(i + 1) in TO_US }) return true
        if (words.any { it in TO_US_ANYWHERE }) return true
        // English has no mark like ne: the doer stands by the helping verb (15 Nov 2026: it read the verb alone, so a
        // customer's payment was asked about as the shop's).
        if (words.indices.any { i -> words[i] in HELPERS && customerIsDoer(words, i) }) return true
        // And with no helping verb: "Ali Raza paid 500", "Ali took 500", "Ali bayar 500" (15 Nov 2026).
        if (nameDoes(words)) return true
        val i = words.indexOfFirst { it in NE }
        return i > 0 && words[i - 1] !in WE_DID
    }

    /**
     * English's helping verbs: "did Ali pay", "has Bashir paid", "does he give". Not "do": in Hindi and Urdu "do hazar
     * diye" is "two thousand gave".
     */
    private val HELPERS = setOf("did", "does", "has", "have")

    /**
     * Who the shop is: I, we, you (the owner is spoken to as "you" by the shop's own assistant). Not "shop": a customer
     * is often "Ali Mobile Shop", and the doer of "how much did Ali Mobile Shop pay" is him.
     */
    private val SHOP_SELF = setOf("i", "we", "you", "u", "saya", "aku", "kami", "kita", "anda", "kamu")

    /** Words that stand after a helping verb and are no doer: "have to pay", "has been paid". */
    private val NO_DOER = setOf("to", "been")

    /**
     * Words beside a helping verb that name nobody: between it and its verb ("has not paid", "have already paid", "has not
     * yet paid") and, in a statement, between the doer and the helping verb ("Ali also has paid", "Ali still has not paid").
     */
    private val ADVERBS = setOf(
        "not", "never", "also", "already", "just", "still", "ever", "finally", "yet", "always", "again", "only", "even",
        "recently", "now", "really", "actually",
        // Indonesian: "Ali sudah bayar", "Ali belum bayar", "Ali juga bayar".
        "sudah", "telah", "belum", "juga", "baru", "tidak", "tak"
    )

    /**
     * Whether the doer of the money's move is somebody else, by the helping verb at [helper] (English). In a question the
     * doer stands between the helper and the verb, one word to four: "how much did Ali Raza pay", "has Bashir paid"; in
     * a statement before the helper: "Ali has not paid". The shop is the doer when it is "I", "we" or "you" ("did you
     * pay Ali", "I have paid Ali", "I did not pay Ali"); "have to pay" and "has been paid" name no doer.
     */
    private fun customerIsDoer(words: List<String>, helper: Int): Boolean {
        val verb = (helper + 2..minOf(helper + 5, words.lastIndex)).firstOrNull { words[it] in MOVED }
        if (verb != null) {
            val doer = words.subList(helper + 1, verb).filter { it !in ADVERBS }
            if (doer.isNotEmpty() && doer.none { it in NO_DOER }) return doer.none { it in SHOP_SELF }
        }
        var v = helper + 1
        while (words.getOrNull(v) in ADVERBS) v++
        if (words.getOrNull(v) in MOVED) {
            // A statement: the doer stands before the helping verb, past any adverb that stands between ("Ali also has paid").
            var d = helper - 1
            while (d >= 0 && words[d] in ADVERBS) d--
            if (d >= 0) return words[d] !in SHOP_SELF && words[d] !in NO_DOER
        }
        return false
    }

    /**
     * The verbs whose doer a statement without a helping verb names by standing before them, in English and Indonesian (the
     * two with the doer first): "Ali Raza paid 500", "Ali took 500", "Ali bayar 500". [GAVE] and [GOT] are the shop's own
     * giving and taking, so a doer who is somebody else turns the way round. Not "returned" and "repaid" (already a customer's
     * giving, and listed as the shop receiving), not "dapat" (it is also "can").
     */
    private val DOER_FIRST = setOf(
        "paid", "gave", "lent", "got", "took", "received",
        "bayar", "membayar", "kasih", "beri", "memberi", "berikan", "pinjamkan", "meminjamkan", "terima", "menerima", "ambil", "mengambil"
    )

    /** Who stands for a customer by himself: "he paid 500", "dia bayar". */
    private val THIRD_PERSON = setOf("he", "she", "they", "dia", "mereka")

    /** A question's word or a joining word: nobody's name, and nobody's verb to be next to. */
    private val NOT_A_DOER = setOf("who", "whom", "what", "how", "much", "many", "when", "where", "which", "why", "that", "if", "or", "but", "so", "because")

    /** A time: nobody's name, and it may stand before the sentence or between the doer and the verb ("Ali Raza last month paid"). */
    private val TIME_WORDS = setOf(
        "today", "yesterday", "tomorrow", "tonight", "morning", "afternoon", "evening", "day", "week", "month", "year",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "then", "later", "earlier", "ago", "last", "this", "next",
        "kal", "aaj", "parso", "abhi", "phir", "pehle", "ab", "subah", "shaam", "raat",
        "kemarin", "hari", "minggu", "bulan", "tahun", "besok"
    )

    /**
     * A statement with no helping verb whose doer is somebody else: the word right before the verb (past any adverb) is a
     * name, "he" or "she" ("Ali Raza paid 500", "Ali also took 500", "he paid"). The shop stands there as "I", "we" or "you";
     * a verb that opens the sentence, or follows a figure, a "to", a question or a time ("paid Ali 500", "500 paid to Ali",
     * "yesterday paid Ali 500", "who paid") names no doer, and the shop is the one who gave or took, as before. The passive
     * ("500 paid by Ali") is not read: "paid by cheque" is as common, and a way of paying is no name.
     */
    private fun nameDoes(words: List<String>): Boolean {
        val v = words.indexOfFirst { it in DOER_FIRST }
        if (v < 0) return false
        var d = v - 1
        while (d >= 0 && (words[d] in ADVERBS || words[d] in TIME_WORDS)) d--
        return d >= 0 && doerWord(words[d])
    }

    private fun doerWord(w: String): Boolean =
        w in THIRD_PERSON || (w !in SHOP_SELF && w !in NOT_A_DOER && w !in NO_DOER && !isNoise(w))

    /** The one-word verbs of [GAVE] and [GOT]. */
    private val MOVED: Set<String> by lazy { (GAVE + GOT).filter { ' ' !in it }.toSet() }

    /** "Me" and "us" as the one handed something, right after the verb. */
    private val TO_US = setOf("me", "us", "saya", "aku", "kami", "kita")

    /** "To me", "to us": Bengali's own words for it, which say so wherever they stand. */
    private val TO_US_ANYWHERE = setOf("আমাকে", "আমায়", "আমাদেরকে")

    /** The doer's mark, as the recognisers write it. */
    private val NE = setOf("ne", "nay", "ney", "نے", "ने")

    /** Before ne, the shop itself: I, we (and "aap", the owner's own polite "you", as a recogniser hears it). */
    private val WE_DID = setOf("main", "mein", "mai", "mae", "hum", "ham", "aap", "ap", "میں", "مین", "ہم", "آپ", "मैं", "मै", "हम", "आप")

    /** A third person and ne written as one word. */
    private val THEY_DID = setOf("usne", "usnay", "isne", "isnay", "unhone", "unhonay", "inhone", "inhonay", "उसने", "इसने", "उन्होंने", "इन्होंने")

    /**
     * Every word that says money moved, either way: the words a customer's name is never made of (Hudhud sets them
     * aside before looking for one, 12 Nov 2026).
     */
    fun directionWords(): List<String> = GAVE + GOT
}
