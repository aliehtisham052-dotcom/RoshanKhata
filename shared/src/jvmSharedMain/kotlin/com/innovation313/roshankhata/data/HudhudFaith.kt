package com.innovation313.roshankhata.data

/** The faith reader's word lists are the strict readers' common [StrictWords]. */
private typealias Words = StrictWords

/**
 * Hudhud on Tawheed, on hamd and sana, and on the Prophet's risalat (14 Nov 2026, the owner: "Allah ki wahdaniyat, hamd
 * o sana aur Nabi-e-Pak ﷺ ki nabuwwat ka bhi zikr ho — lekin har sawal ke jawab mein nahi, sirf us ke specific sawal par").
 *
 * **Only when asked.** This reader is separate from [HudhudMind] on purpose: it answers a sentence only when every word
 * of it is one of the faith's own words or a little word that asks nothing ("kya hai", "batao", "tell me about"), and
 * never when the sentence says anything of the book. So "Allah kaun hai" is Tawheed, "Nabi kareem kaun hain" is the
 * Prophet ﷺ, "hamd o sana" is hamd — and "Allah Ditta ka hisaab" and "Muhammad Ali ka number" stay customers, and a
 * thanks or a "Alhamdulillah" said to him is not turned into a lecture. A word that could also be somebody's name
 * (Muhammad, Nabi, Rasool) is read as the faith's only when the book holds nobody called that.
 *
 * **The Arabic** of every verse below was taken from quran.com, quranworld.net, alim.org and tohed.com on 14 Nov 2026
 * and checked against a second site; the shahada from imam-us.org and falkirkislamiccentre.org. It is shown as text and
 * is never said aloud: a synthetic voice is not the Quran's recitation. The meaning is told in the app's language in
 * Hudhud's own words (strings `hudhud_faith_*`), and the card says it is a brief meaning, not a translation.
 */
object HudhudFaith {

    enum class Topic { TAWHEED, HAMD, RISALAT }

    /** A verse as the card shows it: the surah's Arabic name, where it is, and its Arabic — one line to a verse. */
    class Verse(val surah: String, val ref: String, val lines: List<String>) {
        val arabic: String get() = lines.joinToString("\n")
    }

    // ------------------------------------------------------------------------------------------------ the verses

    /** Al-Ikhlas, the surah of Tawheed (also named At-Tawhid). */
    val IKHLAS = Verse(
        "الإخلاص", "112:1-4", listOf(
            "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
            "ٱللَّهُ ٱلصَّمَدُ",
            "لَمْ يَلِدْ وَلَمْ يُولَدْ",
            "وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ"
        )
    )

    /** The proof from the order of the world: two gods and both would fall into disorder. */
    val ANBIYA_22 = Verse(
        "الأنبياء", "21:22", listOf(
            "لَوْ كَانَ فِيهِمَآ ءَالِهَةٌ إِلَّا ٱللَّهُ لَفَسَدَتَا ۚ فَسُبْحَـٰنَ ٱللَّهِ رَبِّ ٱلْعَرْشِ عَمَّا يَصِفُونَ"
        )
    )

    /** The hoopoe's own declaration to Prophet Sulaiman (peace be upon him). */
    val NAML_26 = Verse(
        "النمل", "27:26", listOf("ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ رَبُّ ٱلْعَرْشِ ٱلْعَظِيمِ")
    )

    /** "All praise belongs to Allah, Lord of the worlds." */
    val FATIHA_2 = Verse("الفاتحة", "1:2", listOf("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ"))

    /** Everything glorifies Him with His praise, though we do not understand how. */
    val ISRA_44 = Verse(
        "الإسراء", "17:44", listOf(
            "تُسَبِّحُ لَهُ ٱلسَّمَـٰوَٰتُ ٱلسَّبْعُ وَٱلْأَرْضُ وَمَن فِيهِنَّ ۚ وَإِن مِّن شَىْءٍ إِلَّا يُسَبِّحُ بِحَمْدِهِۦ وَلَـٰكِن لَّا تَفْقَهُونَ تَسْبِيحَهُمْ ۗ إِنَّهُۥ كَانَ حَلِيمًا غَفُورًا"
        )
    )

    /** The birds with wings spread: each knows its own prayer and glorification. */
    val NUR_41 = Verse(
        "النور", "24:41", listOf(
            "أَلَمْ تَرَ أَنَّ ٱللَّهَ يُسَبِّحُ لَهُۥ مَن فِى ٱلسَّمَـٰوَٰتِ وَٱلْأَرْضِ وَٱلطَّيْرُ صَـٰٓفَّـٰتٍ ۖ كُلٌّ قَدْ عَلِمَ صَلَاتَهُۥ وَتَسْبِيحَهُۥ ۗ وَٱللَّهُ عَلِيمٌۢ بِمَا يَفْعَلُونَ"
        )
    )

    /** Prophet Shuaib's words: success is only through Allah (the end of the verse). */
    val HUD_88 = Verse(
        "هود", "11:88", listOf("وَمَا تَوْفِيقِىٓ إِلَّا بِٱللَّهِ ۚ عَلَيْهِ تَوَكَّلْتُ وَإِلَيْهِ أُنِيبُ")
    )

    /** The two testimonies, the Kalima (not a verse: no surah, the card shows its name instead). */
    val SHAHADA = Verse(
        "الشهادتان", "", listOf("أَشْهَدُ أَنْ لَا إِلَـٰهَ إِلَّا ٱللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ ٱللَّهِ")
    )

    /** "Muhammad is the Messenger of Allah and the Seal of the Prophets." */
    val AHZAB_40 = Verse(
        "الأحزاب", "33:40", listOf(
            "مَّا كَانَ مُحَمَّدٌ أَبَآ أَحَدٍ مِّن رِّجَالِكُمْ وَلَـٰكِن رَّسُولَ ٱللَّهِ وَخَاتَمَ ٱلنَّبِيِّـۧنَ ۗ وَكَانَ ٱللَّهُ بِكُلِّ شَىْءٍ عَلِيمًا"
        )
    )

    /** Allah and His angels send blessings on the Prophet; the believers are told to send blessings and peace. */
    val AHZAB_56 = Verse(
        "الأحزاب", "33:56", listOf(
            "إِنَّ ٱللَّهَ وَمَلَـٰٓئِكَتَهُۥ يُصَلُّونَ عَلَى ٱلنَّبِىِّ ۚ يَـٰٓأَيُّهَا ٱلَّذِينَ ءَامَنُوا۟ صَلُّوا۟ عَلَيْهِ وَسَلِّمُوا۟ تَسْلِيمًا"
        )
    )

    /** The verses a topic shows, in order. */
    fun versesOf(topic: Topic): List<Verse> = when (topic) {
        Topic.TAWHEED -> listOf(IKHLAS, ANBIYA_22, NAML_26)
        Topic.HAMD -> listOf(FATIHA_2, ISRA_44, NUR_41, HUD_88)
        Topic.RISALAT -> listOf(SHAHADA, AHZAB_40, AHZAB_56)
    }

    // -------------------------------------------------------------------------------------------------- the reader

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 14

    /** Allah, and the words for God: "Allah", "Khuda", "God", "الله", "अल्लाह", "আল্লাহ", "Tuhan". */
    private val ALLAH = Words(
        "allah allahu allahh alah khuda khudaa khoda khudawand god tuhan rab rabb ilah",
        "الله لله خدا خداوند رب اله", "अल्लाह खुदा रब", "আল্লাহ আল্লাহ্ খোদা রব", "ٱلله"
    )

    /** One, alone, only. */
    private val ONE = Words(
        "ek aik ik eik wahid waahid ahad akela akaila akele tanha yakta one only single sole esa tunggal satu",
        "ایک اکیلا اکیلے تنہا یکتا واحد احد وحده یگانه یک یکی هڪ اڪيلو", "एक इकलौता अकेला", "এক একমাত্র একক অদ্বিতীয়"
    )

    /** "Counted": how many gods, two or one. */
    private val COUNT = Words(
        "kitne kitna kitni do teen two three many number count tadad berapa dua jumlah",
        "کتنے کتنی کتنا دو تین تعداد كم عدد چند ڪيترا", "कितने कितना दो तीन गिनती", "কয়টি কতজন দুই তিন কত"
    )

    /** The word Tawheed itself, and oneness. */
    private val TAWHEED_W = Words(
        "tawheed tauheed tawhid tauhid tohid touheed tawheedi wahdaniyat wahdaniyyat wahdaniat wahdaniyah wahdat oneness monotheism unity keesaan ketauhidan",
        "وحدانیت وحدانیہ وحدانية وحدت توحید توحيد", "तौहीद तौहिद वहदानियत तवहीद", "তাওহীদ তাওহিদ তৌহিদ তওহীদ ওয়াহদানিয়াত"
    )

    /** A partner with Him. */
    private val SHIRK = Words(
        "shareek sharik shirk partner partners associate associates equal humsar hamsar barabar sekutu",
        "شریک شرک شريك ہمسر برابر نظیر مثل", "साझी शरीक शिर्क", "শরিক শিরক"
    )

    /** Hamd, sana and tasbeeh named as the topic. */
    private val HAMD_NOUN = Words(
        "hamd hamad sana sanaa sanah tasbeeh tasbih tasbeehat tasbihat tahmeed tamjeed tamjid praise praises glorify glorifies glorification glorifying glorified pujian puji memuji bertasbih",
        "حمد ثنا ثناء تسبیح تسبيح تمجید ستایش سپاس", "हम्द सना तस्बीह", "হামদ সানা তাসবীহ তাসবিহ"
    )

    /** The phrases of praise, a topic only when their meaning is asked for. */
    private val DHIKR = Words(
        "alhamdulillah alhamdulilah alhamdullilah alhamdolillah alhamdu alhamd alhamdullah subhanallah subhanullah subhanalla subhan subhaan",
        "سبحان الحمد الحمدللہ الحمدلله", "अल्हम्दुलिल्लाह अलहम्दुलिल्लाह सुब्हानल्लाह सुबहानअल्लाह", "আলহামদুলিল্লাহ সুবহানাল্লাহ"
    )

    /** What may be said with hamd: everything, every creature, the trees, the stones, the birds. */
    private val HAMD_CONTEXT = Words(
        "har cheez cheezein chiz sab saari sari kainat makhluq makhlooq shajar hajar shajro hajro darakht darakhat patthar parinde parindey parindon chidiyan birds bird trees tree stones stone everything all creation universe things thing every karti karte karta kartay kartey kartein bayan bayaan nature setiap semua segala benda pohon batu burung makhluk alam",
        "ہر چیز چیزیں کائنات مخلوق شجر حجر درخت پتھر پرندے پرندوں كل شيء شئ مخلوقات الكون الأشجار الطيور الحجارة همه سنگ پرندگان کرتی کرتے کرتا بیان",
        "हर चीज़ चीज कायनात सब पेड़ पत्थर परिंदे सारी करती करते", "প্রতিটি সব জিনিস গাছ পাথর পাখি সৃষ্টি করে"
    )

    /** The Prophet, a prophet, a messenger. */
    private val PROPHET = Words(
        "nabi nabee rasool rasul rasoolullah rasulullah rasoolallah payghambar paighambar paygambar payambar prophet messenger prophets anbiya anbia nabiyyin nabiyeen nabiyyeen nabiji",
        "نبی نبي رسول پیغمبر پیامبر انبیاء نبیین النبي نبیﷺ", "नबी रसूल पैगंबर पैगम्बर", "নবী রাসূল রাসুল পয়গম্বর নবিজি"
    )

    /** "Hazrat", "Huzoor", "sallallahu alayhi wasallam", "saw", "PBUH": said with the Prophet's name. */
    private val HONOR = Words(
        "sallallahu salallahu sallallaho saw saws saww pbuh alayhi alaihi aleyhi wasallam sallam salla hazrat huzoor huzur hazoor sayyidna sayyiduna syedna kareem karim mustafa mujtaba",
        "صلی صلى علیہ علیه وسلم حضرت حضور سیدنا کریم مصطفی", "सल्लल्लाहु अलैहि वसल्लम हज़रत हजरत हुज़ूर सय्यदना करीम", "সাল্লাল্লাহু আলাইহি ওয়াসাল্লাম হযরত হুজুর সাঃ"
    )

    /** Muhammad. */
    private val MUHAMMAD = Words(
        "muhammad mohammad muhammed mohammed muhamad mohd muhd",
        "محمد", "मुहम्मद मोहम्मद", "মুহাম্মদ মোহাম্মদ মুহাম্মাদ"
    )

    /** Nabuwwat and risalat, the Prophet's office. */
    private val OFFICE = Words(
        "nabuwwat nabuwat nabuwwah nubuwwat nubuwwah nubuwat nabuat risalat risaalat risalah prophethood apostleship kenabian kerasulan",
        "نبوت نبوة رسالت رسالة", "नबूवत नुबूवत रिसालत", "নবুওয়াত নবুয়ত রিসালাত রেসালাত"
    )

    /** "Khatam" and "Khatam-un-Nabiyyin": the Seal; "khatam" alone also means "finished", so it needs the Prophet beside it. */
    private val SEAL = Words(
        "khatam khatim khatamun khataman khatame",
        "ختم خاتم خاتمیت", "", "খতম খাতাম"
    )

    /** The Kalima and the shahada. */
    private val KALIMA = Words(
        "kalima kalma kalimah kalimat kalimaa tayyiba tayyaba tayyibah shahada shahadah shahadatain shahadatayn syahadat",
        "کلمہ کلمة کلمه طیبہ شہادہ شہادتان شهاده", "शहादा कलिमा कलमा कलिमह", "কালিমা কলেমা শাহাদাহ শাহাদা"
    )

    /** Darood, the blessings on the Prophet. */
    private val DAROOD = Words(
        "darood durood dorood darud durud salawat salawaat shalawat",
        "درود صلوات دورد", "दरूद दुरूद", "দরূদ দুরূদ দরুদ"
    )

    /** Little words of a question or a request to tell: they ask something, they say nothing of the book. */
    private val ASK = Words(
        "kaun kon kaunsa kya kia kyaa kiya who what whats tell explain about baare bare baray batao batain bataen bataiye bataye btao samjhao samjhaiye matlab mtlb maana maani meaning means mean kyun kyon why siapa apa apakah jelaskan ceritakan tentang arti makna",
        "کون کیا کس بتاو بتائیں بتائیے سمجھاو مطلب معنی بارے کیوں من ما ماذا هل اخبرني اشرح عن معنى كيست چیست چه آیا درباره بگو توضیح چرا ڪير ڇا ٻڌايو بابت",
        "कौन क्या बताओ बताइए बारे समझाओ मतलब अर्थ क्यों", "কে কী কি বলো বলুন সম্পর্কে সম্বন্ধে মানে অর্থ কেন"
    )

    /** "and", "of", "is": the joining words, in every language, that carry nothing. */
    private val GLUE = Words(
        "o aur and or ka ki ke ko se ne mein me hai hain he ha hy ho hota hoti is are the a an of to in for pak paak taala tala taala tabarak sharif shareef mubarak " +
            "me my you your please pls plz can could do does will would kindly there have has koi kisi e un ul al ye yeh ya",
        "و اور کا کی کے کو سے نے میں ہے ہیں ہو پاک شریف مبارک تعالی تعالیٰ هو هي هذا هذه هل است هست هستند آهي آهن آهيو ۽ ۾ جو جي",
        "और का की के को से में है हैं पाक शरीफ मुबारक", "ও এবং এর এ ছে হয় আছে পাক শরীফ",
        "dan yang adalah ialah ini itu maha"
    )

    /**
     * The faith question [heard] asks, or null (nothing here is guessed). [known]: whether a word is, in this book, a word of
     * somebody's or something's name — Muhammad and Nabi are names, so they are read as the faith's only when nobody is
     * called so ("Muhammad ﷺ" with its honour is the Prophet's whatever the book holds).
     */
    fun read(heard: String, known: (String) -> Boolean = { false }): Topic? {
        val said = VoiceQuestion.normalise(heard.replace("ﷺ", " sallallahu ").replace("﷽", " bismillah "))
            .split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > MAX_WORDS) return null
        val asked = heard.any { it == '?' || it == '؟' }

        var allah = false; var one = false; var count = false; var tawheed = false; var shirk = false
        var hamdNoun = false; var dhikr = false; var prophet = false; var honor = false; var muhammad = false
        var office = false; var seal = false; var kalima = false; var darood = false; var asks = asked
        var anchor = false
        var nameWord: String? = null
        for (w in said) {
            if (StrictWords.BOOK.has(w)) return null
            var ok = false
            if (ALLAH.has(w)) { allah = true; ok = true }
            if (ONE.has(w)) { one = true; ok = true }
            if (COUNT.has(w)) { count = true; ok = true }
            if (TAWHEED_W.has(w)) { tawheed = true; ok = true }
            if (SHIRK.has(w)) { shirk = true; ok = true }
            if (HAMD_NOUN.has(w)) { hamdNoun = true; ok = true }
            if (DHIKR.has(w)) { dhikr = true; ok = true }
            if (HAMD_CONTEXT.has(w)) ok = true
            if (PROPHET.has(w)) { prophet = true; ok = true; if (nameWord == null && known(w)) nameWord = w }
            if (HONOR.has(w)) { honor = true; ok = true }
            if (MUHAMMAD.has(w)) { muhammad = true; ok = true; if (nameWord == null && known(w)) nameWord = w }
            if (OFFICE.has(w)) { office = true; ok = true }
            if (SEAL.has(w)) { seal = true; ok = true }
            if (KALIMA.has(w)) { kalima = true; ok = true }
            if (DAROOD.has(w)) { darood = true; ok = true }
            if (ASK.has(w)) { asks = true; ok = true }
            if (GLUE.has(w)) ok = true
            if (!ok) return null
            if (ALLAH.has(w) || TAWHEED_W.has(w) || HAMD_NOUN.has(w) || DHIKR.has(w) || PROPHET.has(w) || MUHAMMAD.has(w) ||
                OFFICE.has(w) || KALIMA.has(w) || DAROOD.has(w) || SEAL.has(w)
            ) anchor = true
        }
        if (!anchor) return null

        // The Prophet ﷺ: his office, the Kalima, the darood; "Muhammad" with an honour or with "Nabi"/"Rasool"; "khatam" with him.
        val honoured = honor || (prophet && !(nameWord != null && !honor && !muhammad && !allah))
        val hers = office || kalima || darood || (seal && (prophet || muhammad)) ||
            (muhammad && (prophet || honor)) || (prophet && honoured && (asks || allah || muhammad || honor))
        // A bare "Muhammad kaun hai" asks about the Prophet only when the book holds no Muhammad.
        val bareMuhammad = muhammad && !prophet && !honor && !office && !kalima && !darood && nameWord == null && asks
        if (hers || bareMuhammad) return Topic.RISALAT
        if (tawheed || (allah && (one || shirk))) return Topic.TAWHEED
        if (hamdNoun || (dhikr && asks)) return Topic.HAMD
        if (allah && (asks || count)) return Topic.TAWHEED
        return null
    }
}
