package com.innovation313.roshankhata.data

/** The advice readers' word lists are the strict readers' common [StrictWords]. */
private typealias AdviceWords = StrictWords

/**
 * The three questions of Hudhud the business advisor (25 Nov 2026, plan H5, first part): "is my sale going up or down", "whom should
 * I chase first", "what will come in the next 30 days". By rules, never guessed — each is read only when it says exactly what it
 * asks, and answered from figures the screens already hold ([AnswerAdvisor]).
 *
 * **Strict readers, like [HudhudWhyAsk] (lesson 121).** A sentence is read only when every word is one of the reader's own words,
 * a span of days, or a little word that asks nothing — and the words that make the question are all there: for the trend the sales
 * and a way (up, down, or "trend"); for the chase a word of collecting and "first"; for the next thirty days something coming in,
 * "next" and the thirty days or the month. A name of the book, a figure written in digits (but the thirty), a product, a second
 * figure or any other word and it says nothing: the sentence is read as before. It never touches [HudhudMind], the bank or the sense;
 * [Hudhud.decide] never lets the sense turn these readings.
 *
 * "Why" is [HudhudWhyAsk]'s: it is read first, so a sentence with a "why" word never gets here.
 */
object HudhudAdviceAsk {

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 14

    // ------------------------------------------------------------------------------------------------ the words

    /** The sales. */
    private val SALES = AdviceWords(
        "sale sales selling revenue turnover bikri bikree bikry farokht farokhat",
        "سیل سیلز بکری فروخت",
        "وڪرو وڪري سيل",
        "مبيعات بيع البيع مبيعاتي مبيعاتنا",
        "فروش",
        "बिक्री सेल बिकरी",
        "বিক্রি বিক্রয় সেল বিক্রী",
        "penjualan jualan omzet",
        // 25 Nov 2026 (H5, the everyday ways): the verb of selling itself, as a comparison says it ("pichle hafte se zyada bika?",
        // "did I sell more than last week"): VoiceQuestion's own selling words in each script.
        "sell sold bik bika biki bike bikta bikti bikte becha bechi beche",
        "بکا بکی بکے بکتا بکتی بیچا بیچی",
        "وڪاميو وڪاميا",
        "بعت بعنا",
        "فروختم فروختیم",
        "बिका बिकी बिके बिकता बिकती बेचा बेची",
        "terjual menjual jual"
    )

    /**
     * The shop's business as a whole (25 Nov 2026, H5: "is my business growing", "karobar barh raha hai"): a way said of it is the
     * sales' trend, the one figure of growth the book holds. The words the app's own strings use for a business in each language
     * (businesses_title and the help pages): کاروبار, कारोबार / व्यवसाय, ব্যবসা, ڪاروبار, کسب‌وکار, نشاط, usaha / bisnis; with the
     * everyday dhanda, व्यापार and تجارة. Never "shop" or "dukaan": "kaun si dukan zyada chal rahi hai" is the other shops' page.
     */
    private val BUSINESS = AdviceWords(
        "business",
        "karobar kaarobar karobaar kaarobaar dhanda dhandha",
        "کاروبار دھندا دھندہ بزنس",
        "ڪاروبار ڌنڌو ڌنڌي",
        "نشاط نشاطي نشاطنا تجارة تجارتي تجارتنا تجاري",
        "کسب کار کارم وکار وکارم",
        "कारोबार व्यवसाय व्यापार धंधा बिजनेस बिज़नेस",
        "ব্যবসা ব্যবসায়",
        "usaha usahaku bisnis bisnisku"
    )

    /** It is going up. */
    private val UP = AdviceWords(
        "up rising rise rose growing grow grew increasing increased improving improved climbing " +
            "barh barhi barha barhti barhta barhne badh badhti badhta badhna oopar upar",
        "بڑھ بڑھی بڑھا بڑھتی بڑھتا بڑھنے اوپر",
        "وڌ وڌي وڌندڙ مٿي",
        "ترتفع ارتفع ارتفاع تزداد زيادة زاد تتحسن تحسن",
        "بالا افزایش رشد",
        "बढ़ बढ़ा बढ़ी बढ़े बढ़ती बढ़ता ऊपर",
        "বাড়ছে বাড়ল বাড়ে বেড়েছে বেড়ে বাড়তি উপরে",
        "naik meningkat bertambah bertumbuh tumbuh membaik",
        // 25 Nov 2026 (H5): growing said of a business, and getting better ("are my sales getting better or worse").
        "better improve expanding behtar",
        "بہتر",
        "بهتر",
        "تنمو ينمو نمو ازدياد تزايد تتزايد",
        "بهتر",
        "बेहतर",
        "berkembang"
    )

    /** It is going down. */
    private val DOWN = AdviceWords(
        "down falling fall fell dropping drop dropped declining decline decreasing decreased slowing slower " +
            "kam ghat ghata ghati ghatti ghatta ghatne gir girti girta girna neeche niche kami",
        "کم گھٹ گھٹتی گھٹتا گر گرتی گرتا نیچے کمی",
        "گهٽ گھٽ گهٽجي گهٽجڻ هيٺ",
        "تنخفض انخفض تتراجع تراجع تقل نقص هبوط",
        "کم کاهش پایین افت",
        "कम घट घटती घटता गिर गिरती गिरता नीचे",
        "কমছে কমল কমেছে কমে কমে নিচে",
        "turun menurun berkurang anjlok melemah",
        // 25 Nov 2026 (H5): getting worse, shrinking.
        "worse shrinking kharab kharaab bura buri",
        "خراب بری برا",
        "خراب",
        "انخفاض تناقص تتناقص يتراجع يتناقص",
        "بدتر",
        "खराब ख़राब बुरी",
        "খারাপ",
        "memburuk menyusut"
    )

    /**
     * More and less (25 Nov 2026, H5): a way only in a comparison the sentence makes ([THAN] a span before, or "than before"),
     * never alone — "zyada sale" alone is much sale, and "sab se zyada" is the most ([SUPERLATIVE]).
     */
    private val MORE_LESS = AdviceWords(
        "more less higher lower fewer zyada ziada ziyada zyaada zaida kam kum",
        "زیادہ کم",
        "وڌيڪ گهٽ گھٽ",
        "أكثر اكثر أقل اقل",
        "بیشتر کمتر زیاد کم",
        "ज्यादा ज़्यादा अधिक कम",
        "বেশি কম",
        "lebih banyak sedikit kurang tinggi rendah"
    )

    /**
     * "Than" (25 Nov 2026, H5): the word that sets a span before as the one compared against ("pichle hafte se", "than last week",
     * "گذشته هفته از"...). The span the sentence names after it is the one before; the trend is told for the span after it.
     */
    private val THAN = AdviceWords(
        "than compared versus vs",
        "se sy say",
        "سے",
        "کان",
        "من مقارنة",
        "از نسبت",
        "से",
        "চেয়ে চাইতে তুলনায় থেকে",
        "dari daripada dibanding dibandingkan"
    )

    /** The most: "sab se zyada bika" is the best seller, never a comparison (the Sindhi سڀ is also a little word). */
    private val SUPERLATIVE = AdviceWords(
        "most best highest top least lowest sab sabse",
        "سب سبھی",
        "سڀ",
        "الأكثر الاكثر الأقل الاقل",
        "بیشترین کمترین",
        "सब सबसे",
        "সবচেয়ে সব",
        "paling terbanyak tertinggi terendah"
    )

    /**
     * "Than before" (25 Nov 2026, H5: "sale pehle se behtar hai?", "am I selling more than before"): a comparison with the span
     * before the asked one, the asked one this month when none is said. As phrases, after [VoiceQuestion.normalise], spaces around.
     */
    private val THAN_BEFORE: List<String> = listOf(
        "pehle se", "pahle se", "pehlay se", "than before", "compared to before", "پہلے سے", "पहले से", "আগের চেয়ে", "আগের থেকে",
        "اڳ کان", "من قبل", "از قبل", "نسبت به قبل", "dari sebelumnya", "daripada sebelumnya", "dibanding sebelumnya"
    ).map { " ${VoiceQuestion.normalise(it)} " }

    /** The span after each span before: "than last week" asks this week's trend. */
    private val AFTER: Map<VoiceQuestion.Window, VoiceQuestion.Window> = mapOf(
        VoiceQuestion.Window.YESTERDAY to VoiceQuestion.Window.TODAY,
        VoiceQuestion.Window.LAST_WEEK to VoiceQuestion.Window.THIS_WEEK,
        VoiceQuestion.Window.LAST_MONTH to VoiceQuestion.Window.THIS_MONTH,
        VoiceQuestion.Window.LAST_YEAR to VoiceQuestion.Window.THIS_YEAR
    )

    /**
     * Arabic's "increase" and Urdu's "more" are one spelling once the letters are folded (زيادة, زیادہ; lesson 92): it is a way only
     * in an Arabic sentence (no letter only Urdu, Sindhi or Persian writes), and Urdu's comparative otherwise.
     */
    private val ZIYADA = VoiceQuestion.normalise("زيادة")

    /**
     * A word that says "going down" only when something is going on: "kam" is also "little" ("aaj bohat kam bikri hui") and, once
     * the letters are folded, Arabic's "how much" ("كم المبيعات اليوم"). Such a word is a way only beside a word of motion
     * ([MOTION]) or the word "trend"; else the sentence is a figure's, as it was read before.
     */
    private val SOFT_DOWN = AdviceWords("kam", "کم", "گهٽ گھٽ", "كم", "کم", "कम", "কম", "kurang")

    /** The words of something going on: "is going", "ho rahi hai", "is getting". */
    private val MOTION = AdviceWords(
        "going getting trending moving heading",
        "rahi raha rahe rhi rha rahay",
        "رہی رہا رہے",
        "رهي رهيو رهيا",
        "مستمر",
        "حال",
        "रही रहा रहे",
        "চলছে",
        "sedang makin semakin terus"
    )

    /** "My", "our": the sales that are the asker's own. */
    private val MINE = AdviceWords(
        "my our mine",
        "meri mera mere hamari hamara hamare apni apna apne",
        "میری میرا میرے ہماری ہمارا ہمارے اپنی اپنا اپنے",
        "منهنجو منهنجي منهنجا اسان اسانجو",
        "مبيعاتي مبيعاتنا لدي عندي",
        "من ما مان",
        "मेरी मेरा मेरे हमारी हमारा हमारे अपनी अपना अपने",
        "আমার আমাদের",
        "saya aku kami kita"
    )

    /** "Trend" asked as a noun ("sale ka trend"): the way, with nothing said of which. */
    private val TREND = AdviceWords(
        "trend trends trending direction",
        "trend rujhan rujhaan rukh",
        "ٹرینڈ رجحان رخ",
        "رجحان ڏس",
        "اتجاه اتجاہ",
        "روند",
        "ट्रेंड रुझान रुख",
        "ট্রেন্ড প্রবণতা",
        "tren arah"
    )

    /** "Or" between the two ways ("up or down"). */
    private val OR = AdviceWords("or", "ya", "یا", "يا", "या", "বা", "atau", "او أو أم ام")

    /** Little words that ask nothing: the verbs of being and going, "my", "this", "so much", "tell me", "is it"… */
    private val LITTLE = AdviceWords(
        // English
        "the a an my our is are was were do does did has have been be being so much very this that it its in on of for to from than " +
            "compared what whats how hows tell me us please show going go keep keeps keeping getting get gets looking look ok okay now over within during " +
            "lately really and not no i should shall can could would will need must want we you am",
        // Roman Urdu and Hindi
        "is iss us ki ka ke ko se mein me main mai meri mera mere hamari hamara hamare apni apna apne hui hua hue ho hoga hogi " +
            "gayi gai gaya gaye rahi raha rahe rhi rha thi tha the hai hain hy he itni itna itne bohat bahut kaafi kafi ab abhi " +
            "batao bataiye bata btao dikhao kya kia kiya hone hona tak par pe wali wala wale ye yeh wo woh sab mujhe humein hamein " +
            "ji nahi nahin na ya kar kara kyaa karun karoon karon karu karna karni karein karen chahiye chahiyen chahie lena leni lun loon",
        // Urdu
        "اس میں کی کا کے کو سے میری میرا میرے ہماری ہمارا ہمارے اپنی اپنا ہوئی ہوا ہوئے ہو ہوگا ہوگی گئی گیا گئے رہی رہا رہے " +
            "تھی تھا تھے ہے ہیں اتنی اتنا اتنے بہت کافی اب ابھی بتاؤ بتاو بتائیں بتائیے دکھاؤ کیا ہونے ہونا تک پر یہ وہ سب مجھے ہمیں " +
            "جی نہیں نہ کروں کرنا کرنی چاہیے چاہئے چاہئیں چاہییں لوں گے گی گا",
        // Sindhi
        "هن ۾ جو جي جا کي کان منهنجو منهنجي اسان ٿيو ٿي ٿيا ويو وئي ويا رهيو رهي آهي آهن هو هئي ايترو ايتري تمام گهڻو هاڻي " +
            "ٻڌايو ٻڌاءِ ڇا هي اهو سڀ نه ناهي ڪريان ڪرڻ گهرجي",
        // Arabic
        "هذا هذه في من على عن الى إلى لي قل اخبرني أخبرني كثيرا جدا قد ما هل كان كانت هو هي لا لم يجب ينبغي علي ان أن خلال الذي التي",
        // Farsi
        "این آن در از به را که شد شده است بود بوده چه ما من خیلی بگو بده هم مان نه نمی می باید بکنم کنم آیا حال و کند رو",
        // Hindi
        "इस उस में की का के को से मेरी मेरा मेरे हमारी हमारा हमारे अपनी अपना हुई हुआ हुए हो होगा गई गया गए रही रहा रहे थी था थे है " +
            "हैं इतनी इतना इतने बहुत काफी अब अभी बताओ बताइए क्या होने होना तक पर यह ये वह वो सब मुझे हमें जी नहीं न करूं करना करनी चाहिए चाहिये लूं होगी",
        // Bengali
        "এই এ এর আমার আমাদের হল হলো হয়েছে হচ্ছে হয়ে গেল গেছে ছিল আছে কি কী এত এতো অনেক এখন বলো বলুন টা টি না নাকি করব করবো উচিত থেকে",
        // Indonesian
        "ini itu di ke dari yang saya kami kita jadi menjadi sangat begitu ya tolong apa apakah nya tidak tak harus sebaiknya lebih dalam selama"
    )

    /** "First". */
    private val FIRST = AdviceWords(
        "first priority urgent urgently",
        "pehle pehla pehlay pahle pahla awwal avval",
        "پہلے پہلا اول",
        "پهريان پهريون پهرين",
        "اولا أولا اولاً أول اول أولى",
        "اول اولین ابتدا",
        "पहले पहला",
        "আগে প্রথমে প্রথম",
        "dulu pertama dahulu duluan"
    )

    /** To chase money: ask for it, remind, collect. Not "call" or "phone" alone: they are a customer's number. */
    private val CHASE = AdviceWords(
        "chase chasing collect collecting pursue recover recovery",
        "wasooli wusooli wasuli wasoli mangna mangun mangu mango mangon tagaza taqaza",
        "وصولی مانگوں مانگنا مانگو تقاضا",
        "وصولي گهران گهرڻ تقاضا",
        "اطالب أطالب مطالبة تحصيل احصل أحصل",
        "مطالبه وصول پیگیری بگیرم",
        "वसूली मांगू मांगना तकाज़ा तकाजा",
        "আদায় চাইব চাইবো তাগাদা",
        "tagih menagih penagihan",
        // 25 Nov 2026 (H5, the everyday ways): "pehle kis se paise maangun", "sab se pehle kis ka udhaar wasool karun", Hindi's
        // मांगूं with its nasal, Arabic "أطالبه" (ask him), Farsi "طلب کنم" (claim it).
        "maangun maangoon maangu maango maangna mangoon wasool wusool wasul",
        "وصول",
        "أطالبه اطالبه أطالبهم اطالبهم",
        "طلب",
        "मांगूं मांगूँ माँगूं माँगूँ माँगू वसूल"
    )

    /**
     * Asking or taking, which is the chase only beside a word of money ([MONEY]): "who do I ask for money first", "kis se pehle
     * paise lene chahiye", "আগে কার কাছ থেকে টাকা নেব". "Who should I ask first" alone asks nothing of the dues.
     */
    private val ASK_TAKE = AdviceWords(
        "ask request demand take",
        "lun loon lu lena lene leni",
        "لوں لینا لینے لینی",
        "وٺان وٺڻ",
        "آخذ اخذ",
        "بخواهم",
        "लूं लूँ लेना लेने लेनी",
        "নেব নেবো নিব",
        "minta ambil"
    )

    /** "Who" and "whom", the customers and the money: words around the question that add nothing. */
    private val WHO = AdviceWords(
        "who whom whose which customer customers people one ones money payment payments dues udhaar",
        "kis kisse kisko kiski kaun kaunsa kon kise paise paisa paisay rupay raqam udhaar udhar baqaya gahak customer log",
        "کس کسے کون کونسا کیا پیسے رقم ادھار بقایا گاہک لوگ",
        "ڪنهن ڪير ڪنھن پيسا رقم اڌار گراهڪ",
        "من مَن مع ممن اموال مال ديون عميل زبون",
        "کی کیست کسی پول مبلغ بدهی مشتری",
        "किस किससे किसको कौन किसे पैसे रकम उधार बकाया ग्राहक लोग",
        "কাকে কার কে কাছ টাকা পয়সা বাকি গ্রাহক",
        "siapa pelanggan uang tagihan utang piutang"
    )

    /** Something coming in, and what a shop says of money to come. */
    private val INCOMING = AdviceWords(
        "come comes coming incoming expect expected expecting receive receiving receivable arriving arrive due promised how much money cash payments payment",
        "aayega aayegi aayenge aaenge aaega aaye aaenge aane aana milega milegi milenge milna milne kitna kitni kitne kitna paise paisa " +
            "paisay rupay raqam wasooli wusooli payment udhaar wala wale wali mojood",
        "آئے آئیں آنے ملے ملیں ملنے والے والا والی کتنا کتنی کتنے پیسے رقم وصولی ادھار",
        "ايندا ايندي ملندا ملندي ملڻ ڪيترو ڪيترا پيسا رقم",
        "سيأتي سياتي ستأتي ستاتي يأتي تأتي قادم كم اموال أموال مبلغ سأستلم ساستلم متوقع",
        "می‌رسد میرسد می‌آید میاید آید چقدر پول مبلغ دریافت",
        "आएगा आएगी आएंगे आने मिलेगा मिलेगी मिलेंगे मिलने कितना कितनी कितने पैसे रकम वसूली वाले वाला वाली",
        "আসবে আসছে পাব পাবো কত টাকা পয়সা",
        "masuk datang akan diterima berapa uang"
    )

    /** Money, said: "how much" alone is anything that comes (stock, goods), so the thirty days need one of these. */
    private val MONEY = AdviceWords(
        "money cash payment payments receivable receivables receipts income",
        "paise paisa paisay rupay rupaye raqam wasooli wusooli payment udhaar",
        "پیسے پیسہ رقم وصولی ادھار",
        "پيسا پيسن رقم وصولي اڌار",
        "مبلغ مبالغ اموال أموال نقود مدفوعات",
        "پول مبلغ پرداخت",
        "पैसे पैसा रकम वसूली उधार",
        "টাকা পয়সা",
        "uang dana pembayaran",
        // 25 Nov 2026 (H5): "expected collections", Arabic "المال" (the money) and "الدفع" (paying), "dues".
        "collection collections dues",
        "مال دفع"
    )

    /** What is NEEDED is the money going out, not coming in: "agle mahine kitna cash chahiye" is not this reader's. */
    private val NEED = AdviceWords(
        "need needs needed require required requires spend spending pay owe",
        "chahiye chahie chahiyen zaroorat zarurat darkar dena dene dun doon",
        "چاہیے چاہئے چاہییں ضرورت درکار دینا دینے",
        "گهرجي گهرجن ضرورت ڏيڻ",
        "احتاج نحتاج يلزم يجب ضرورة اسدد أسدد ادفع أدفع",
        "نیاز لازم ضروری بپردازم",
        "चाहिए चाहिये ज़रूरत जरूरत देना देने",
        "চাই লাগবে দরকার দিতে",
        "butuh perlu bayar"
    )

    /** "Next". */
    private val NEXT = AdviceWords(
        "next coming upcoming ahead",
        "agle agla agli aglay aane anay aanay",
        "اگلے اگلا اگلی آنے",
        "ايندڙ اڳين اڳيون",
        "القادمة القادم المقبلة المقبل التالية التالي",
        "آینده آتی بعدی",
        "अगले अगला अगली आने",
        "পরবর্তী আগামী আসছে",
        "depan berikutnya mendatang"
    )

    /** The thirty days, or the month: "30", in any of the figures a keyboard writes, and the words of the days. */
    private val THIRTY = AdviceWords(
        "30 thirty days day month months",
        "30 tees din mahine mahina month days",
        "۳۰ ٣٠ تیس دن مہینے مہینہ ماہ",
        "30 ٣٠ ۳۰ ڏينهن مهيني",
        "30 ٣٠ ۳۰ يوم ايام أيام يوما شهر الشهر ثلاثين ثلاثون",
        "30 ۳۰ ٣٠ روز ماه",
        "30 ३० तीस दिन दिनों महीने महीना",
        "30 ৩০ ত্রিশ দিন দিনে মাসে মাস",
        "30 hari bulan"
    )

    /** The words that say "thirty" itself, in figures or in words. "Next day" is tomorrow, not the thirty. */
    private val THIRTY_FIGURE = setOf("30", "۳۰", "٣٠", "३०", "৩০", "thirty", "tees", "تیس", "ثلاثين", "ثلاثون", "तीस", "ত্রিশ")
        .map { VoiceQuestion.normalise(it) }.toSet()

    /** A month: "next month" is the thirty days of the Cash flow page. */
    private val MONTH = AdviceWords(
        "month months", "mahine mahina month", "مہینے مہینہ ماہ", "مهيني", "شهر الشهر", "ماه", "महीने महीना", "মাসে মাস", "bulan"
    )

    /** Phrases said as two words that mean one, applied before the words are read. Longer first. */
    private val PHRASES: List<Pair<String, String>> = listOf(
        "sab se pehle" to "pehle", "sabse pehle" to "pehle", "سب سے پہلے" to "پہلے", "सबसे पहले" to "पहले", "सब से पहले" to "पहले",
        "up or down" to "up down", "اوپر یا نیچے" to "اوپر نیچے", "ऊपर या नीचे" to "ऊपर नीचे",
        "going up" to "up", "going down" to "down", "go up" to "up", "go down" to "down",
        "aane wale" to "aane", "آنے والے" to "آنے", "आने वाले" to "आने", "aane wala" to "aane", "aane wali" to "aane",
        // 25 Nov 2026 (H5): money paid TO the shop is money coming in ("how much will customers pay me next month"); "pay" alone
        // is the shop paying out ([NEED]). And "compared to" is one word of comparing ([THAN]).
        "pay me" to "payments receive", "pay us" to "payments receive",
        "compared to" to "than", "compared with" to "than", "ke muqable mein" to "se", "ke muqable" to "se", "ke muqabla" to "se",
        "کے مقابلے میں" to "سے", "کے مقابلے" to "سے", "की तुलना में" to "से", "के मुकाबले" to "से", "نسبت به" to "از"
    ).map { (from, to) -> " ${VoiceQuestion.normalise(from)} " to " ${VoiceQuestion.normalise(to)} " }.sortedByDescending { it.first.length }

    /** The sentence as words, phrases applied; null when it is empty or longer than a sentence of these is. */
    private fun wordsOf(heard: String): List<String>? {
        val first = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (first.isEmpty() || first.size > MAX_WORDS + 4) return null
        var text = " " + first.joinToString(" ") + " "
        for ((from, to) in PHRASES) if (text.contains(from)) text = text.replace(from, to)
        val said = text.trim().split(' ').filter { it.isNotEmpty() }
        return said.takeIf { it.isNotEmpty() && it.size <= MAX_WORDS }
    }

    // ------------------------------------------------------------------------------------------------ the readers

    /**
     * What [heard] asks of the shop's advisor, or null: nothing is guessed. [isName]: a word the book knows as a customer's, a
     * supplier's or a product's name (then it is not read: "Ali ki sale barh rahi hai" is about Ali, not the shop). [calling]: a
     * word that only calls him ("Hudhud", "bhai").
     */
    fun read(heard: String, isName: (String) -> Boolean = { false }, calling: (String) -> Boolean = { false }): Hudhud.Ask? =
        trend(heard, isName, calling) ?: chase(heard, isName, calling) ?: nextDays(heard, isName, calling)

    /**
     * "Is my sale going up or down", "kya sale barh rahi hai", "sales trend this week": the sales, and a way — up, down or "trend" —
     * over the span said, this month when none is. The sales over the span against the span before it, by [AnswerAdvisor.trend].
     *
     * 25 Nov 2026 (H5, the everyday ways): the business growing ("is my business growing", "karobar barh raha hai") is the sales'
     * trend; more or less than ONE span before ("pichle hafte se zyada bika?") or than before ("pehle se behtar") is a comparison,
     * told for the span after the one named; better and worse are ways. Two spans the sentence names itself stay its own two
     * figures, and "sab se zyada" stays the most.
     */
    internal fun trend(heard: String, isName: (String) -> Boolean, calling: (String) -> Boolean): Hudhud.Ask.Trend? {
        val first = wordsOf(heard) ?: return null
        // The cheap question first: no sales word and no business, no answer (every question comes through here).
        if (first.none { SALES.has(it) || BUSINESS.has(it) }) return null
        // A span he has no window for ("pichle 3 mahine", "parson") is never read as another; the whole book has nothing before it.
        if (VoiceQuestion.unsupportedSpan(heard)) return null
        val spanSaid = VoiceQuestion.windowOf(heard)
        val asked = spanSaid ?: VoiceQuestion.Window.THIS_MONTH
        if (asked == VoiceQuestion.Window.ALL_TIME) return null
        // "Than before" ("pehle se zyada"): taken out of the words, it is the comparison's own.
        val text = " " + first.joinToString(" ") + " "
        val sinceBefore = THAN_BEFORE.any { text.contains(it) }
        val said = if (!sinceBefore) first else
            THAN_BEFORE.fold(text) { acc, p -> acc.replace(p, " ") }.split(' ').filter { it.isNotEmpty() }
        val spanWords = VoiceQuestion.spanWordsIn(said.joinToString(" "))
        // A comparison (25 Nov 2026, H5): "pichle hafte se zyada bika?" — more or less THAN one span before (the sentence names that
        // span alone), or than before. Two spans named ("is mahine pichle mahine se zyada bika") are the sentence's own two figures,
        // read as before; "sab se zyada" is the most, never a comparison.
        val than = said.any { THAN.has(it) }
        val named = VoiceQuestion.spansNamed(heard)
        val most = said.any { SUPERLATIVE.has(it) }
        val after = AFTER[asked]
        val comparing = !most && ((than && after != null && named == 1) || (sinceBefore && named <= 1))
        // Than a span before, the trend is the span after it's ("compared to last week" is this week against last).
        val window = if (than && after != null) after else asked
        val moving = said.any { MOTION.has(it) || TREND.has(it) }
        val arabic = said.any { w -> w.any { it in '\u0600'..'\u06FF' } } && said.none { w -> w.any { it in HudhudMind.NOT_ARABIC_LETTERS } }
        // Sales said to be MOVING one way ("sale gir rahi hai ya barh rahi hai", "sale barh rahi hai"), with nothing else, ask the
        // trend too (25 Nov 2026, the owner's yes, BC-317): the words ask which way it goes. A bare "sale zyada hai" (no motion)
        // is still the figure. Urdu's زیادہ is Arabic's increase only in an Arabic sentence.
        // The motion is read from the sentence as said: the phrases fold "going up" into "up" (BC-317's first run missed it).
        val goingOneWay = VoiceQuestion.normalise(heard).split(' ').any { MOTION.has(it) } &&
            said.any { (UP.has(it) && !(it == ZIYADA && !arabic)) || DOWN.has(it) }
        // Otherwise the trend is asked for the asker's own sales ("my"), the business, over a span said, in a comparison, or by
        // the word "trend".
        if (!(spanSaid != null || comparing || goingOneWay || said.any { MINE.has(it) || TREND.has(it) || BUSINESS.has(it) })) return null
        var way = false
        for (w in said) {
            if (calling(w)) continue
            // A word of the book's names is never part of the question — but the reader's own word for selling or the business is
            // not a name: Arabic "بعت" (I sold) meets a customer called Butt by its letters alone (the name search's own lesson).
            val own = SALES.has(w) || BUSINESS.has(w)
            if (!own && isName(w)) return null
            var ok = false
            if (own) ok = true
            // Urdu's "more" (زیادہ) is Arabic's "increase" only in an Arabic sentence.
            val up = UP.has(w) && !(w == ZIYADA && !arabic)
            if (up || TREND.has(w) || (DOWN.has(w) && !SOFT_DOWN.has(w))) { way = true; ok = true }
            if (SOFT_DOWN.has(w)) { if (moving) way = true; ok = true }
            if (MORE_LESS.has(w)) { if (comparing) way = true; ok = true }
            if (w in spanWords || OR.has(w) || MOTION.has(w) || THAN.has(w) || SUPERLATIVE.has(w) || LITTLE.has(w)) ok = true
            if (!ok) return null
        }
        return if (way) Hudhud.Ask.Trend(window) else null
    }

    /**
     * "Whom should I chase first", "pehle kis se wasooli karun", "who should I ask for money first": a word of collecting and
     * "first", the little words around them. No span: it is who owes now.
     */
    internal fun chase(heard: String, isName: (String) -> Boolean, calling: (String) -> Boolean): Hudhud.Ask.Advise? {
        val said = wordsOf(heard) ?: return null
        // Collecting is said by its own word, or by asking or taking beside a word of money ("who do I ask for money first").
        val collects = said.any { CHASE.has(it) } || (said.any { ASK_TAKE.has(it) } && said.any { MONEY.has(it) })
        if (said.none { FIRST.has(it) } || !collects) return null
        for (w in said) {
            if (calling(w)) continue
            if (isName(w)) return null
            if (!(FIRST.has(w) || CHASE.has(w) || ASK_TAKE.has(w) || MONEY.has(w) || WHO.has(w) || LITTLE.has(w))) return null
        }
        return Hudhud.Ask.Advise(Hudhud.AdviseOf.CHASE)
    }

    /**
     * "What will come in the next 30 days", "agle 30 din mein kitna aayega", "money expected next month": something coming in,
     * "next", and the thirty days or the month. Other numbers of days ("next 60 days") are not read: the figure asked for is the
     * Cash flow page's thirty days, and he does not answer it as another.
     */
    internal fun nextDays(heard: String, isName: (String) -> Boolean, calling: (String) -> Boolean): Hudhud.Ask.Advise? {
        val said = wordsOf(heard) ?: return null
        if (said.none { NEXT.has(it) } || said.none { it in THIRTY_FIGURE || MONTH.has(it) } || said.none { MONEY.has(it) }) return null
        // What is needed or to be paid is the money going out.
        if (said.any { NEED.has(it) }) return null
        // A figure other than the thirty is no word of the lists: the loop below refuses it.
        for (w in said) {
            if (calling(w)) continue
            if (isName(w)) return null
            if (!(NEXT.has(w) || THIRTY.has(w) || INCOMING.has(w) || MONEY.has(w) || WHO.has(w) || LITTLE.has(w))) return null
        }
        return Hudhud.Ask.Advise(Hudhud.AdviseOf.NEXT_30)
    }
}
