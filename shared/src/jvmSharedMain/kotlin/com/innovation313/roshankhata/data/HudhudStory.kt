package com.innovation313.roshankhata.data

/**
 * A calendar day of the app's own history: year, month (1-12) and day. Plain numbers, so every platform and every language
 * writes it in its own way (the phone through its date words).
 */
class HistoryDay(val year: Int, val month: Int, val day: Int)

/**
 * The app's own dates, each with where it comes from (14 Nov 2026, the owner: "app kab banayi, kab publish hui, Hudhud
 * ka feature kab add hua — Hudhud ko pata hona chahiye"). Said as facts of the project's own records, never rounded up.
 *
 * What is not known is not said: the day the app was first shown to everyone on Google Play is not in the records — only
 * that Google gave the production access on 16 Sep 2026 and that it was public by 28 Oct 2026 — so the answer says those
 * two days and nothing between them. The word is always "published", never "launched" (the owner's rule, every language).
 */
object AppHistory {
    /** The project's repository was made on GitHub on 13 Jul 2026 (17:55 UTC): the day the work began, as far as the records say. */
    val STARTED = HistoryDay(2026, 7, 13)

    /** Google Play gave the app its production access on 16 Sep 2026. */
    val PLAY_ACCESS = HistoryDay(2026, 9, 16)

    /** The owner confirmed on 28 Oct 2026 that the app was public on Google Play. */
    val PUBLIC_BY = HistoryDay(2026, 10, 28)

    /** The first work on Hudhud is of 12 Nov 2026 (his first commit, 19:13 local time). */
    val HUDHUD = HistoryDay(2026, 11, 12)
}

/**
 * Hudhud on the app's own history and on his own story (14 Nov 2026, the owner: "app ki history, kab banayi, kab publish
 * hui, Hudhud kab add hua" and "Hudhud apni aap-beeti sunaye — Hazrat Sulaiman ka Hudhud — poora waqia, aur uska natijah
 * bhi, jaisa Quran ne bayan kiya").
 *
 * **A strict reader, like [HudhudFaith], and for the same reason.** Putting these words into [HudhudMind] would change the
 * sense's features and the sense would have to be learnt again (lessons 106, 111, 112, 116); and a loose reader would take
 * "app ka version kya hai", "kis ne banayi yeh app", "Ali ki kahani" or "Roshan ka khata kab bana" for a history. This one
 * answers only a sentence whose every word is one of its own or a little word that asks nothing, that names the app, Hudhud
 * or "you", and that asks about a beginning, a publishing, an age, a history or a story — and never a sentence with a word of
 * the shop's book in it (a customer's name is no word of its own: "Ali ki kahani" is Ali's).
 *
 * **What is told.** Dates, from [AppHistory]. And Hudhud's own story as the Quran tells it (Surah An-Naml, 27:20-44): the
 * hoopoe of Prophet Sulaiman (peace be upon him), in the first person as far as the Quran speaks of him, the Quran's own
 * telling marked as such from where it goes on without him, and its outcome — the queen's submission to Allah (27:44) —
 * said as the Quran says it. Nothing is added that the Quran does not say: not the queen's name, not what became of the
 * hoopoe after the letter.
 */
object HudhudStory {

    enum class Topic {
        /** When the work on the app began. */
        STARTED,

        /** When the app came on Google Play. */
        PUBLISHED,

        /** When Hudhud was added; also how old he is. */
        HUDHUD,

        /** The whole timeline. */
        JOURNEY,

        /** The hoopoe's own story, with its outcome. */
        HOOPOE
    }

    /** The queen's own words at the end of the Quran's account: the outcome. Arabic as in the app's other verses; shown, never said. */
    val NAML_44 = HudhudFaith.Verse(
        "النمل", "27:44", listOf("قَالَتْ رَبِّ إِنِّى ظَلَمْتُ نَفْسِى وَأَسْلَمْتُ مَعَ سُلَيْمَـٰنَ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ")
    )

    /** The verses the story shows, in order: the hoopoe's own declaration (27:26) and the outcome (27:44). */
    val VERSES: List<HudhudFaith.Verse> = listOf(HudhudFaith.NAML_26, NAML_44)

    // -------------------------------------------------------------------------------------------------- the reader

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 18

    /** Anything that is not a letter or a mark: where the raw sentence is cut into words. */
    private val NOT_LETTER = Regex("[^\\p{L}\\p{M}]+")

    /** The app: "app", "ایپ", "تطبيق", "ऐप", "অ্যাপ", "aplikasi". The Urdu "اپ" is also "you" and is not here. */
    private val APP = StrictWords(
        "app apps application applications aplikasi apk software roshankhata",
        "ایپ ایپس ایپلیکیشن ایپلی کیشن اپلیکیشن اپلی کیشن تطبيق تطبيقات تطبيقنا برنامج برنامہ برنامه ايپ ايپس ايپليڪيشن ايپليكيشن",
        "ऐप ऐप्प एप एप्प ऐप्स एप्स ऐप्लिकेशन एप्लिकेशन ऐप्लीकेशन एप्लीकेशन ऍप",
        "অ্যাপ অ্যাপস এপ এ্যাপ অ্যাপ্লিকেশন অ্যাপলিকেশন"
    )

    /** The app's name, in two words: Roshan Khata. */
    private val ROSHAN = StrictWords("roshan roshon روشن रोशन রোশন")
    private val KHATA = StrictWords("khata khaata khaatah کھاتہ کھاتا کھاتے खाता খাতা")

    /** Hudhud: his name, and "hoopoe". */
    private val HUD = StrictWords(
        "hudhud hudhood hudhod hoodhood hudhad hodhod hoopoe hoopoo hoopoes",
        "ہدہد هدهد ھدھد", "हुदहुद", "হুদহুদ"
    )

    /** "You" and what is yours: the bare words, in every language that is not Persian (whose "تو" is Urdu's "then"). */
    private val YOU = StrictWords(
        "aap tum tumhein tumhain tumhari tumhara tumhare tumse tumne tumko tera teri tere tu aapka aapki aapke aapko aapse aapne yours you your yourself u ur " +
            "kamu anda kau engkau dirimu",
        "تم تمہیں تمہارا تمہاری تمہارے تجھے تیرا تیری تیرے",
        "आप आपका आपकी आपके आपको आपने तुम तुम्हें तुम्हारा तुम्हारी तुम्हारे तू तेरा तेरी तेरे",
        "তুমি তোমার তোমাকে আপনি আপনার আপনাকে তুই তোর",
        "أنت انت أنتم انتم أنتِ",
        "توهان توهانجي تون تنهنجي تنهنجو تنهنجا"
    )

    /** Persian's own "you" — "تو" is Urdu's "then", so it counts only in a Persian app. */
    private val YOU_FA = StrictWords("تو شما خودت خودتان")

    /** "Aap" in Urdu script and "ap" in Roman: you or the app — the sentence has to say which (a madd on the alef says "you"). */
    private val AMBIGUOUS = StrictWords("ap", "اپ")

    /** One's own: "apni", "your own". */
    private val OWN = StrictWords(
        "apni apna apne apnay apnee khud own sendiri",
        "اپنی اپنا اپنے خود", "अपनी अपना अपने खुद", "নিজের নিজ নিজে", "پنهنجي پنهنجو پنهنجا", "خودت نفسك"
    )

    /** Prophet Sulaiman, and the honours said with him. */
    private val SULAIMAN = StrictWords(
        "sulaiman sulaimaan suleman sulemaan sulayman suleiman sulaymaan solomon salomon",
        "سلیمان سليمان", "सुलैमान सुलेमान सुलयमान", "সুলায়মান সোলায়মান সুলাইমান"
    )
    private val HONOR = StrictWords(
        "hazrat hazrath huzoor nabi paighambar payghambar prophet alaihissalam alayhissalam alaihissalaam alaihis alayhis salam as s pbuh nabiullah",
        "حضرت نبی پیغمبر علیہ السلام", "हज़रत हजरत नबी पैगंबर पैगम्बर अलैहिस्सलाम अलैहि सलाम", "হযরত নবী আলাইহিস সালাম",
        "نبي سيدنا عليه", "پیامبر علیه"
    )

    /** A story: "kahani", "qissa", "waqia", "story", "قصة", "داستان", "कहानी", "গল্প", "kisah". */
    private val STORY = StrictWords(
        "kahani kahaani kahanee kahni kissa qissa kisa qissah qissay waqia waqiya waqiah sargusht dastan dastaan daastaan story stories tale " +
            "autobiography narration kisah cerita riwayat",
        "کہانی قصہ قصے واقعہ سرگزشت داستان قصة حكاية رواية ڪهاڻي قصو قصي واقعو سرگذشت قصه ماجرا",
        "कहानी किस्सा क्सिसा वाकया वाक़या दास्तान", "গল্প কাহিনী কাহিনি ঘটনা আত্মকথা আত্মজীবনী"
    )

    /** "Your story": the word and the "you" joined — Indonesian "-mu", Arabic "-ka", Persian "-at". */
    private val STORY_YOURS = StrictWords("kisahmu ceritamu قصتك قصتكم سيرتك داستانت")

    /** "Aap-beeti": one's own story told by oneself — with Urdu's "beeti" alone, so "آپ بیتی" said as two words reads the same. */
    private val AUTOBIO = StrictWords(
        "aapbeeti aapbiti aabeeti apbeeti apbiti beeti biti",
        "آپبیتی بیتی", "आपबीती बीती", "আত্মকথা"
    )

    /** History and background. */
    private val HIST = StrictWords(
        "history historical background pasmanzar safar journey timeline sejarah perjalanan latar belakang",
        "تاریخچه پس منظر سفر رحلة خلفية مسیر", "इतिहास पृष्ठभूमि यात्रा सफर", "ইতিহাস পটভূমি সফর যাত্রা"
    )

    /** "Your history": the word and the "you" joined. */
    private val HIST_YOURS = StrictWords("sejarahmu تاريخك")

    /** A date. "Tareekh" asks for the timeline when nothing else is asked, and for a day with "when". */
    private val DATEW = StrictWords(
        "tareekh tarikh date tanggal", "تاریخ تاريخ", "तारीख तिथि", "তারিখ"
    )

    /** Age: how old. */
    private val AGE = StrictWords(
        "umar umr age old purani purana puranee puraani umur usia tua",
        "عمر قدیم پرانی پرانا", "उम्र पुरानी पुराना", "বয়স পুরনো পুরোনো পুরানো"
    )

    /** "Your age". */
    private val AGE_YOURS = StrictWords("usiamu umurmu عمرك")

    /** When: "kab", "when", "which year", "since when", "how long ago". */
    private val WHEN = StrictWords(
        "kab kabse kitne kitna kitni din dino saal mahina mahine arsa muddat pehle pahle when since year ago long " +
            "kapan sejak tahun bulan hari berapa lama",
        "کب کبسے کتنے کتنا کتنی دن سال مہینہ مہینے عرصہ مدت پہلے",
        "कब कबसे कितने कितना कितनी दिन साल महीना महीने समय पहले",
        "কখন কবে কোন সালে সাল কত দিন কতদিন মাস থেকে আগে",
        "متى منذ أي اي سنة عام كم يوم شهر ڪڏهن ڪڏھن ڪهڙي ڪيترا ڏينهن"
    )

    /** Persian's "when" — "کی" is Urdu's "of", so it counts only in a Persian app. */
    private val WHEN_FA = StrictWords("کی چند وقت")

    /** Made, built, born, worked on. */
    private val MADE = StrictWords(
        "bani bana banai banayi banaya banaye bane banee banna banay bannay banne tayyar tayar taiyar tayyari ijad ijaad " +
            "kaam work works worked made make built build create created creation developed develop development origin founded born " +
            "dibuat dibangun pembuatan diciptakan lahir didirikan dikembangkan",
        "بنی بنا بنایا بنائی بنایی بنے بنائے تیار تیاری ایجاد تخلیق پیدا پیدائش جنم کام",
        "बनी बना बनाई बनाया बने तैयार जन्म पैदा पैदाइश काम",
        "তৈরি বানানো জন্ম",
        "صنع صنعت بني بنيت أنشئ انشئ أنشئت إنشاء تأسس تأسيس ولد ولدت تطوير طور",
        "ساخته ساخت ایجاد تولد ٺهيو ٺهي ٺاهيو ٺاهي جنم"
    )

    /** Begun: the work's start. */
    private val BEGIN = StrictWords(
        "shuru shuruaat shuruat aghaz agaz start started starts starting begin began begun beginning mulai dimulai awal",
        "شروع شروعات آغاز", "शुरू शुरुआत आरंभ", "শুরু শুরুতে", "بدأ بدأت بداية بدء", "ابتدا آغاز"
    )

    /** Published: on the store, public. "Launch" is understood here — and never said anywhere. */
    private val PUBLISH = StrictWords(
        "publish published publishing publication released release launch launched launching lanch live public publik available " +
            "dipublikasikan publikasi terbit diterbitkan dirilis rilis diluncurkan peluncuran tayang tersedia",
        "پبلش شائع اشاعت پبلک ریلیز لانچ لائیو",
        "पब्लिश प्रकाशित रिलीज़ रिलीज लॉन्च लांच लाइव",
        "প্রকাশ প্রকাশিত পাবলিশ রিলিজ লঞ্চ",
        "نشر نشرت منشور إصدار اصدار صدر أطلق أطلقت إطلاق اطلاق",
        "منتشر انتشار عرضه شايع"
    )

    /** The store: "play store", "Google Play". */
    private val STORE = StrictWords(
        "play store playstore google market",
        "پلے اسٹور پلے سٹور اسٹور سٹور گوگل", "प्ले स्टोर प्लेस्टोर स्टोर गूगल", "প্লে স্টোর স্টোর গুগল",
        "متجر بلاي جوجل", "پلی بازار گوگل", "پلي اسٽور"
    )

    /** Added to the app; came; joined. */
    private val ADDED = StrictWords(
        "add added joda shamil shaamil dakhil aya aaya aaye aayi aai mila jura joined join feature ditambahkan ditambah bergabung hadir muncul datang",
        "شامل اضافہ جوڑا آیا آئے آئی داخل", "शामिल जोड़ा जुड़ा जुड़े जुड़ी आया आए आई जोड़ी फीचर", "যোগ যুক্ত এসেছে ফিচার",
        "أضيف اضيف أضفت إضافة اضافة انضم انضمام جاء", "اضافه افزوده آمد اومد", "شامل ڪيو آيو"
    )

    /** Little words of a request: they ask or tell, they say nothing of the book. */
    private val ASK = StrictWords(
        "kya kia kyaa batao batain bataen bataiye bataye btao sunao sunaiye sunaen sunain sunaye suna sunana suno bolo boliye kaho kahiye bayan kijiye karo " +
            "zara poori pori puri pura saari sari mukammal tell me tells telling narrate recount share say speak about complete whole full entire " +
            "whats how apa apakah ceritakan ceritakanlah kisahkan lengkap seluruh tolong",
        "کیا بتاو بتائیں بتائیے بتایئے بتا سناو سنائیں سنائیے سنا بولو بیان کریں کیجیے کیجئے پوری پورا مکمل ساری",
        "क्या बताओ बताइए बता सुनाओ सुनाइए सुना सुनो बोलो पूरी पूरा सारी",
        "কী কি বলো বলুন বল শোনাও শোনান পুরো সম্পূর্ণ",
        "ما ماذا اخبرني أخبرني احك احكي حدثني قص اروِ ارو اسرد اشرح عن",
        "چه چیست بگو بگویید تعریف کن بازگو کن کامل", "ڇا ٻڌايو ٻڌاءِ ٻڌاء ٻڌائي"
    )

    /** "of", "is", "the": the joining words, in every language, that carry nothing. */
    private val GLUE = StrictWords(
        "ka ki ke ko se ne mein me par per pe hai hain hay hy hun hoon ho hue hui hua tha thi the hona hone hota hoti hote raha rahi rahe gaya gayi gaye gai " +
            "aur or o ye yeh woh wo is us iss uss bhi hi to na ab abhi liye taraf baare bare baray baaray " +
            "a an of to in on for are was were be been did does do can could please pls plz my and it its this that there here from by with us go went goes what which kis " +
            "ini itu yang dan di ke dari untuk adalah ialah pada dengan oleh kah",
        "کا کی کے کو سے نے میں پر ہے ہیں ہوں ہو ہوا ہوئی ہوئے تھا تھی تھے اور یہ وہ اس ایک بھی ہی تو نہ",
        "का की के को से ने में पर है हैं हूँ हो हुआ हुई हुए था थी थे और यह ये वह वो इस उस एक भी ही तो ना",
        "এর ও এবং এই সেই একটি হয় হলো হল হয়েছে হয়েছিল ছিল আছে",
        "في من على إلى عن هذا هذه ذلك هو هي كان كانت تم أن هل ما لي لنا يا",
        "این آن از به در که را است بود شد شده می", "جو جي جا کي ۾ تي مان آهي آهن هو هئي ۽ ٿيو ٿي ٿيا"
    )

    /**
     * What [heard] asks about the app's history or Hudhud's own story, or null — nothing here is guessed. [language]: the app's
     * language tag; Persian's "تو" and "کی" count only in a Persian app. [known]: whether a word is, in this book, somebody's
     * name — "Roshan" is a name, and "Roshan ka khata kab bana" is a customer's.
     */
    fun read(heard: String, language: String? = null, known: (String) -> Boolean = { false }): Topic? {
        val said = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > MAX_WORDS) return null
        val persian = language?.startsWith("fa") == true
        // Written with its madd, "آپ" is "you" and never the app; without it, "اپ" is either.
        val maddYou = !persian && heard.split(NOT_LETTER).any { it == "آپ" }
        val roshanKhata = said.any { ROSHAN.has(it) && !known(it) } && said.any { KHATA.has(it) }

        var app = false; var hud = false; var you = false; var ambiguous = false; var own = false
        var story = false; var autobio = false; var hist = false; var dateWord = false; var age = false
        var whenAsked = false; var made = false; var begin = false; var publish = false; var store = false
        var added = false
        var hudTokens = 0
        var anyKnown = false
        for (w in said) {
            // The shop's own words: a sentence about the book is the book's, never a history.
            if (StrictWords.BOOK.has(w) && !(roshanKhata && KHATA.has(w))) return null
            var ok = false
            if (APP.has(w)) { app = true; ok = true }
            if (roshanKhata && ROSHAN.has(w)) { app = true; ok = true }
            if (roshanKhata && KHATA.has(w)) ok = true
            if (HUD.has(w)) { hud = true; ok = true }
            // A name in two words: "hud hud".
            if (w == "hud") { hudTokens++; ok = true }
            if (YOU.has(w) || (persian && YOU_FA.has(w))) { you = true; ok = true }
            if (AMBIGUOUS.has(w)) {
                when {
                    persian -> app = true
                    maddYou -> you = true
                    else -> ambiguous = true
                }
                ok = true
            }
            if (OWN.has(w)) { own = true; ok = true }
            if (SULAIMAN.has(w) || HONOR.has(w)) ok = true
            if (STORY.has(w)) { story = true; ok = true }
            if (STORY_YOURS.has(w)) { story = true; you = true; ok = true }
            if (AUTOBIO.has(w)) { autobio = true; ok = true }
            if (HIST.has(w)) { hist = true; ok = true }
            if (HIST_YOURS.has(w)) { hist = true; you = true; ok = true }
            if (DATEW.has(w)) { dateWord = true; ok = true }
            if (AGE.has(w)) { age = true; ok = true }
            if (AGE_YOURS.has(w)) { age = true; you = true; ok = true }
            if (WHEN.has(w) || (persian && WHEN_FA.has(w))) { whenAsked = true; ok = true }
            if (MADE.has(w)) { made = true; ok = true }
            if (BEGIN.has(w)) { begin = true; ok = true }
            if (PUBLISH.has(w)) { publish = true; ok = true }
            if (STORE.has(w)) { store = true; ok = true }
            if (ADDED.has(w)) { added = true; ok = true }
            if (ASK.has(w) || GLUE.has(w)) ok = true
            if (known(w)) anyKnown = true
            if (!ok) return null
        }
        if (hudTokens >= 2) hud = true
        // A name found in this book that is not Hudhud's or the app's: the sentence is somebody's, not a history.
        if (anyKnown && !hud && !app && !roshanKhata) return null

        val him = hud || you
        val asksStory = story || autobio
        // A date alone asks for the timeline; with a verb it is the "when" of that verb.
        val whenish = whenAsked || dateWord
        val histAsked = hist || (dateWord && !made && !begin && !publish && !store && !added && !whenAsked)

        // His own story: "Hudhud apni aap-beeti sunao", "Hudhud ki kahani", "apni kahani sunao", "tell me your story".
        if (asksStory) {
            if (hud) return Topic.HOOPOE
            if (app) return Topic.JOURNEY
            if (autobio || you || own || ambiguous) return Topic.HOOPOE
            return null
        }
        // Published, on the store: only with a "when".
        if (whenish && (publish || store)) {
            if (app || ambiguous) return Topic.PUBLISHED
            if (him) return Topic.HUDHUD
            return null
        }
        // The app's history, or his.
        if (histAsked || age) {
            if (hud && !app) return Topic.HUDHUD
            if (app) return Topic.JOURNEY
            if (you) return Topic.HUDHUD
            if (ambiguous) return Topic.JOURNEY
            return null
        }
        // A beginning, a making, a joining: only with a "when".
        if (whenish) {
            if (added) {
                if (him) return Topic.HUDHUD
                if (app || ambiguous) return Topic.JOURNEY
                return null
            }
            if (made || begin) {
                if (him && !app) return Topic.HUDHUD
                if (him && app) return Topic.JOURNEY
                // "When did the work start" is the first day; "when was it made" is the whole timeline.
                if (app) return if (begin) Topic.STARTED else Topic.JOURNEY
                if (ambiguous) return Topic.JOURNEY
                return null
            }
            // "Aap kab se ho": no verb, only him and a "when".
            if (him && !app && !ambiguous) return Topic.HUDHUD
        }
        return null
    }
}
