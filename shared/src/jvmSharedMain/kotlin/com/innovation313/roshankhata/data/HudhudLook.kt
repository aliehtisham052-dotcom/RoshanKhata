package com.innovation313.roshankhata.data

/** The look reader's word lists are the strict readers' common [StrictWords]. */
private typealias LookWords = StrictWords

/**
 * How the app looks, changed at the owner's word (14 Nov 2026, the owner's yes to the settings tiers: "text size aur theme
 * Hudhud badal sakta hai, Undo ke saath"): the **text size** (small, normal, large, largest) and the **theme** (light, dark,
 * as the phone). Nothing else about the app is changed by him — not the language, not a lock, not a backup, not a shop's
 * details: those are other tiers (he opens the screen, or he only says where it is).
 *
 * **A strict reader, like [HudhudNote], [HudhudFaith] and [HudhudStory] (lesson 121).** It reads a sentence only when every
 * word of it is one of its own words or a little word that asks nothing, and the sentence names the setting: the text
 * ("text", "font", "likhai", "تحریر", "अक्षर", "লেখা", "teks") or its size, or the theme ("theme", "mode" beside dark or light,
 * "تھیم", "थीम", "থিম", "tema"). Any other word — a name, a figure, "not", "don't", "how" — and it says nothing: a sentence it
 * does not know is read as before. It never takes a question as an order ("is dark mode on?", "kya font bara hai" tell where
 * the setting stands and offer the choices, nothing changes), and never a sentence about both settings.
 *
 * What a sentence says:
 * - **a level** — "text size large", "font normal karo", "largest text", "likhai sab se bara": that level of the four;
 * - **a step** — "bigger text", "font aur bara", "make the text smaller", and the Urdu way of saying it, an adjective with
 *   the doing word: "font bara karo", "font chhota karo" (a step up or down from where it stands);
 * - **a theme** — "dark mode", "theme roshan", "same as phone"; with "off" the other one of the two: "dark mode band karo" is
 *   the light theme;
 * - **nothing yet** — "theme badlo", "text size", "what is the theme": he tells where it stands and offers the other choices.
 *
 * [plan] says what a request does given where the setting stands (a change, the same, past the end, or only a look);
 * the app's side ([HudhudLookDoor] there) is the only place that writes the setting, and shows an Undo.
 *
 * It does not touch [HudhudMind], the bank or the learnt sense: [Hudhud.byRules] asks it once the sentence is known not to be
 * a voice change or the name he is to be called by, and [Hudhud.decide] never lets the sense turn its reading.
 */
object HudhudLook {

    /** The two settings he may change. */
    enum class Setting { SIZE, THEME }

    // The app's own numbers: TextSize.SMALL..LARGEST and ThemeMode.LIGHT, DARK, SYSTEM. The app's unit test holds them equal.
    const val SIZE_SMALL = 0
    const val SIZE_NORMAL = 1
    const val SIZE_LARGE = 2
    const val SIZE_LARGEST = 3
    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 2

    /** What a sentence asks. */
    sealed class Request {
        abstract val setting: Setting

        /** No value said: where the setting stands, and the choices ("theme badlo", "text size", "what is the theme"). */
        data class Show(override val setting: Setting) : Request()

        /** A text size level, [SIZE_SMALL]..[SIZE_LARGEST]. */
        data class SizeTo(val level: Int) : Request() {
            override val setting: Setting get() = Setting.SIZE
        }

        /** One step up ([by] = 1) or down ([by] = -1) from where the text size stands. */
        data class SizeStep(val by: Int) : Request() {
            override val setting: Setting get() = Setting.SIZE
        }

        /** A theme, [THEME_LIGHT], [THEME_DARK] or [THEME_SYSTEM]. */
        data class ThemeTo(val mode: Int) : Request() {
            override val setting: Setting get() = Setting.THEME
        }
    }

    /**
     * A change that was made, for the card that says so and for its Undo: [from] was the value before, [to] is the value now.
     * [undone]: the change was taken back ([to] is then the value that came back).
     */
    data class Made(val setting: Setting, val from: Int, val to: Int, val undone: Boolean = false) {
        /** Taking it back, or doing it again after it was taken back: the same two values the other way round. */
        fun reversed(): Made = Made(setting, from = to, to = from, undone = !undone)
    }

    /** What a request does, given where the setting stands. */
    sealed class Plan {
        /** The setting changes. */
        data class Change(val made: Made) : Plan()

        /** It is already what was asked. */
        data class Same(val setting: Setting, val value: Int) : Plan()

        /** A step past the end: the text is already the largest ([bigger]) or the smallest. */
        data class Limit(val setting: Setting, val value: Int, val bigger: Boolean) : Plan()

        /** Nothing is changed: where it stands, and the choices. */
        data class Show(val setting: Setting, val value: Int) : Plan()
    }

    /** The last value of a setting: the values are 0..[lastValue]. */
    fun lastValue(setting: Setting): Int = if (setting == Setting.SIZE) SIZE_LARGEST else THEME_SYSTEM

    /** The values of a setting other than [current], in their own order — the choices offered beside a card. */
    fun others(setting: Setting, current: Int): List<Int> = (0..lastValue(setting)).filter { it != current }

    /** What [request] does with the setting at [current]. Pure: nothing is written here. */
    fun plan(request: Request, current: Int): Plan {
        val setting = request.setting
        val last = lastValue(setting)
        val now = current.coerceIn(0, last)
        return when (request) {
            is Request.Show -> Plan.Show(setting, now)
            is Request.SizeTo -> toValue(setting, now, request.level.coerceIn(0, last))
            is Request.ThemeTo -> toValue(setting, now, request.mode.coerceIn(0, last))
            is Request.SizeStep -> {
                val target = now + request.by
                if (target < 0 || target > last) Plan.Limit(setting, now, bigger = request.by > 0)
                else toValue(setting, now, target)
            }
        }
    }

    private fun toValue(setting: Setting, now: Int, target: Int): Plan =
        if (target == now) Plan.Same(setting, now) else Plan.Change(Made(setting, now, target))

    // ------------------------------------------------------------------------------------------------ the reader

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 12

    /** The text: "text", "font", "likhai", "تحریر", "متن", "अक्षर", "লেখা", "teks". */
    private val TEXT = LookWords(
        "text texts font fonts letters writing likhai likhayi likhaayi likhaai likhawat likhaawat tehreer tahreer akshar akshor haroof huroof harf harfon",
        "تحریر لکھائی لکھاوٹ حروف حرف فونٹ فونٽ ٹیکسٹ ٹيڪسٽ",
        "لکت لکڻ اکر اکرن فانٽ فانٹ",
        "نص نصوص خط خطوط كتابة الكتابة حروف احرف أحرف فونت",
        "متن فونت نوشته نوشتار",
        "टेक्स्ट फ़ॉन्ट फॉन्ट फोंट फ़ोंट अक्षर अक्षरों लिखावट लिखाई",
        "লেখা টেক্সট ফন্ট হরফ অক্ষর",
        "teks tulisan huruf"
    )

    /** The size: "size", "سائز", "حجم", "اندازه", "आकार", "আকার", "ukuran". */
    private val SIZE = LookWords(
        "size sizes saiz saaiz",
        "سائز سایز سائيز ماپ",
        "حجم مقاس",
        "اندازه",
        "आकार साइज़ साइज",
        "আকার সাইজ",
        "ukuran"
    )

    /** The theme itself: "theme", "تھیم", "المظهر", "پوسته", "थीम", "থিম", "tema". */
    private val THEME = LookWords(
        "theme themes thim theem tema",
        "تھیم تھیمز ٿيم ٿیم",
        "مظهر السمة سمة ثيم",
        "پوسته",
        "थीम थिम",
        "থিম"
    )

    /**
     * Words that name the theme only beside dark or light ("dark mode", "night mode", "mode roshan", "screen dark"): a bare
     * "mode" is also a way of paying, and the Urdu "موڈ" is also a mood.
     */
    private val MODE = LookWords(
        "mode modes screen display background colour color rang layar tampilan latar warna",
        "موڈ موڊ",
        "وضع حالت شاشة",
        "मोड",
        "মোড"
    )

    /** The dark theme: "dark", "night", "tareek", "raat", "تاریک", "داكن", "डार्क", "ডার্ক", "gelap". */
    private val DARK = LookWords(
        "dark darkmode night nightmode tareek tariki andhera andhere andheri raat",
        "تاریک تاریکی اندھیرا اندھیری رات ڈارک دارک",
        "اونداهي اونداهو اونداهه رات ڊارڪ",
        "داكن داكنة مظلم ليلي ليل",
        "تیره تاریک شب شبانه",
        "डार्क अंधेरा अँधेरा रात",
        "ডার্ক অন্ধকার রাত",
        "gelap malam"
    )

    /** The light theme: "light", "day", "roshan", "روشن", "فاتح", "लाइट", "লাইট", "terang". */
    private val LIGHT = LookWords(
        "light lightmode day daymode daylight bright roshan ujala din",
        "روشن لائٹ دن اجالا",
        "لائيٽ ڏينهن",
        "فاتح فاتحة نهاري نهار",
        "لایت روز",
        "लाइट रोशन उजला दिन",
        "লাইট আলো দিন",
        "terang siang cerah"
    )

    /** The phone's own: "system", "auto", and (with the phrases below) "same as phone". */
    private val SYSTEM = LookWords(
        "system auto automatic otomatis sistem sistim",
        "سسٹم سسٽم آٹو خودکار آٽو",
        "نظام النظام تلقائي",
        "سیستم",
        "सिस्टम ऑटो ऑटोमैटिक",
        "সিস্টেম অটো স্বয়ংক্রিয়"
    )

    /** A step up: "bigger", "increase", "barhao", "بڑھاو", "बढ़ाओ", "বাড়াও", "perbesar" (the phrases below add "aur bara"). */
    private val BIGGER = LookWords(
        "bigger larger greater increase increased enlarge enlarged magnify grow barhao barhaao barha barhana barhaiye barhayen barhain badhao badhaao badha badhana badhaiye badhayen badhain zyada ziada ziyada zyaada",
        "بڑھاو بڑھاؤ بڑھا بڑھائیں بڑھائیے بڑھانا زیادہ",
        "وڌايو وڌ وڌيڪ",
        "أكبر اكبر كبّر كبر كبري تكبير زيادة زد",
        "بزرگتر افزایش بیشتر",
        "बढ़ाओ बढ़ाइए बढ़ा बढ़ाएं ज़्यादा ज्यादा",
        "বাড়াও বাড়ান বাড়িয়ে বেশি",
        "perbesar besarkan tambah naikkan"
    )

    /** A step down: "smaller", "reduce", "kam", "ghatao", "گھٹاو", "घटाओ", "কমাও", "perkecil". */
    private val SMALLER = LookWords(
        "smaller reduce reduced decrease decreased shrink lessen lower kam kamm ghatao ghataao ghata ghatana ghataiye ghatayen ghatain",
        "کم کمی گھٹاو گھٹاؤ گھٹا گھٹائیں گھٹائیے",
        "گھٽايو گھٽ",
        "أصغر اصغر صغّر صغر تصغير نقص قلل انقص أنقص",
        "کوچکتر کمتر کاهش",
        "घटाओ घटाइए घटा घटाएं कम",
        "কমাও কমান কমিয়ে কম",
        "perkecil kecilkan kurangi turunkan"
    )

    /** "Big" as the level's own word: "large", "bara", "بڑا", "وڏو", "كبير", "بزرگ", "बड़ा", "বড়", "besar". */
    private val BIG = LookWords(
        "big large bara bada barra badi bari bade barey barae",
        "بڑا بڑی بڑے",
        "وڏو وڏي وڏا",
        "كبير كبيرة كبيرا",
        "بزرگ",
        "बड़ा बड़ी बड़े",
        "বড়",
        "besar"
    )

    /** The English adjectives only: the app's own words for the levels ("Large"), kept as such beside a doing word. */
    private val BIG_EN = LookWords("big large")
    private val SMALL_EN = LookWords("small smallest tiny")

    /** "Small": "small", "chhota", "چھوٹا", "ننڍو", "صغير", "کوچک", "छोटा", "ছোট", "kecil". */
    private val SMALL = LookWords(
        "small smallest tiny chhota chota chhoti choti chhote chote chotay chhotay",
        "چھوٹا چھوٹی چھوٹے",
        "ننڍو ننڍي ننڍا",
        "صغير صغيرة صغيرا",
        "کوچک",
        "छोटा छोटी छोटे",
        "ছোট",
        "kecil"
    )

    /** The middle one: "normal", "aam", "عام", "معمولی", "सामान्य", "স্বাভাবিক", "biasa". */
    private val NORMAL = LookWords(
        "normal regular standard usual default original aam aaam amm",
        "عام نارمل معمولی",
        "عادي عاديه عادية طبيعي",
        "نرمال عادی",
        "सामान्य नॉर्मल आम",
        "স্বাভাবিক নরমাল",
        "biasa standar"
    )

    /** The biggest, in one word. */
    private val LARGEST = LookWords(
        "largest biggest maximum max huge",
        "الأكبر الاكبر أقصى اقصى",
        "अधिकतम",
        "সর্বোচ্চ",
        "terbesar maksimum",
        "بزرگترین بیشترین حداکثر"
    )

    /** "Most", "very": the biggest or the smallest — "sab se bara", "bohat bara", "كبير جدًا", "خیلی بزرگ", "sangat besar". */
    private val MOST = LookWords(
        "most sabse most very extra bohat bahut boht bht bahot kaafi",
        "سبسے بہت بوہت",
        "ڏاڍو تمام",
        "جدا",
        "خیلی بسیار",
        "सबसे बहुत",
        "সবচেয়ে অনেক",
        "sangat sekali banget"
    )

    /** The doing words that turn "bara" into "bigger": "font bara karo" is a step, "text size large" is the level. */
    private val MAKE = LookWords(
        "make karo kar kardo kro krdo karein karen karna krna kijiye kijiyega kijiyay karain",
        "کرو کر کردو کریں کرنا کیجیے کیجئے",
        "ڪريو ڪر ڪرڻ",
        "اجعل اجعلها اجعله",
        "کن بکن کنید کردن",
        "करो कर करें करना कीजिए कीजिये",
        "করো করুন কর",
        "buat jadikan bikin"
    )

    /** Setting a level: "set", "put", "rakho" — a level said with these stays that level, even beside a doing word. */
    private val SET = LookWords(
        "set put select choose pick rakho rakh rakhein rakhna",
        "رکھو رکھیں",
        "رکو",
        "रखो रखें",
        "রাখো"
    )

    /** Off: "dark mode band karo" is the light theme. */
    private val OFF = LookWords(
        "off disable disabled deactivate band hatao hata hatado hatadein hatayen hatana",
        "بند ہٹاو ہٹاؤ ہٹا ہٹانا ختم آف",
        "هٽايو",
        "ايقاف إيقاف أوقف اوقف الغاء ألغ الغ",
        "خاموش غیرفعال",
        "बंद हटाओ हटा हटाना ऑफ",
        "বন্ধ সরাও অফ",
        "matikan nonaktifkan mati"
    )

    /** On: only to see "on" and "off" in one sentence. */
    private val ON = LookWords(
        "on enable enabled activate activated chalu shuru chalao chalaiye lagao lagado laga lagana lagaiye lagayen lagain",
        "آن چالو شروع لگاو لگاؤ لگا لگانا لگائیں چلاو چلاؤ چلائیں",
        "لڳايو هلايو",
        "شغل فعل فعّل",
        "فعال",
        "चालू शुरू लगाओ लगा लगाना लगाइए चलाओ",
        "অন চালু শুরু লাগাও",
        "aktifkan nyalakan hidupkan"
    )

    /** Questions: they are never orders — a question about the setting tells where it stands. */
    private val QUESTION = LookWords(
        "what whats which is are does kya kia kyaa kaunsa konsa kaun kon kitna kitni kitne kaisa kaisi",
        "کیا کونسا کون کتنا کیسا",
        "ڇا ڪهڙو ڪهڙي ڪهڙا",
        "ما ماذا هل أي اي",
        "چیست چه چی آیا کدام",
        "क्या कौन कौनसा कितना कैसा",
        "কী কি কত কোন",
        "apa apakah berapa"
    )

    /** Little words that ask nothing: "please", "my", "the", "karo" is in [MAKE]. */
    private val LITTLE = LookWords(
        // English
        "the a an my me our i you it its this that these those to of for into in at from with and please pls plz can could would will " +
            "shall kindly just now want need like prefer wish let lets s change changes changing changed use using keep apply go give " +
            "turn switch toggle flip " +
            "get have has had be do show tell display open see check look know bit little slightly somewhat one app application " +
            "setting settings option options",
        // Roman Urdu and Hindi
        "do den dein dijiye dijiyega dena de badlo badal badalna badalni badalne badalein badalen badaliye badliye tabdeel tabdili " +
            "chahiye chahta chahti chahtay chahtain mujhe mjhe humen humein hamein meri mera mere apni apna apne is iska iski iske " +
            "isko usko ko ka ki ke se mein me main mai par pe per ab abhi zara zra jara jaldi hai hain hy ho hoga hogi tha thi wala " +
            "wali wale sa si bata batao dikhao dikha dekho dekhna ek aap app ap tum tu ye yeh wo woh",
        // Urdu
        "دو دیں دیجیے دیجئے دینا بدلو بدل بدلیں بدلنا بدلنی بدلنے تبدیل تبدیلی چاہیے چاہئے چاہتا چاہتی مجھے میرا میری میرے اپنی اپنا " +
            "اپنے اس کا کی کے کو سے میں پر پہ ہے ہیں ہو گا گی ذرا ابھی اب ایپ بتاو دکھاو دکھا دیکھو ایک آپ تم یہ وہ والا والی والے",
        // Sindhi
        "ڏيو ڏي بدلايو بدلاءِ تبديل چاهيان چاهيو مون کي منهنجو منهنجي منهنجا هي هن جو جي جا ۾ تي آهي آهن هجي اپ ايپ ڏيکار ٻڌاءِ هڪ",
        // Arabic
        "غير غيّر بدل بدّل ضع اضبط استخدم أريد اريد أبغى ابغى لو سمحت من فضلك الى إلى على في هذا هذه التطبيق اظهر اعرض اخبرني قل ارني لي",
        // Farsi
        "تغییر عوض بده بدهید بگذار قرار می خواهم میخواهم میخوام لطفا لطفاً را به در از این آن یک برنامه اپ نشان بگو",
        // Hindi
        "दो दें दीजिए दीजिये देना बदलो बदल बदलें बदलना तब्दील चाहिए चाहता चाहती मुझे मेरा मेरी मेरे अपना अपनी अपने इस का की के को से " +
            "में पर है हैं हो ज़रा ज़रा अभी अब ऐप बताओ दिखाओ दिखा देखो एक आप तुम यह ये वह वो वाला वाली वाले",
        // Bengali
        "দাও দিন বদলাও বদলান পরিবর্তন চাই আমার আমাকে এই এটা এর তার কে তে আছে হোক ও অ্যাপ অ্যাপের বলো দেখাও একটু",
        // Indonesian
        "tolong ubah ganti atur pakai gunakan saya aku ingin mau minta pada ke di dari untuk yang dong ya deh saja aplikasi aplikasinya " +
            "ini itu adalah " +
            "tampilkan tunjukkan lihat"
    )

    /**
     * Phrases said as two or three words that mean one, applied to the sentence the reader normalised: "sab se bara" is "most
     * big", "aur bara" is "bigger", "same as phone" is "system". Longer phrases first.
     */
    private val PHRASES: List<Pair<String, String>> = listOf(
        // the biggest and the smallest: the words are joined and [MOST] says them
        "sab se" to "sabse", "sab say" to "sabse", "سب سے" to "سبسے", "सब से" to "सबसे", "সব চেয়ে" to "সবচেয়ে", "سڀ کان" to "most",
        "extra large" to "largest", "extra big" to "largest", "x large" to "largest",
        // a step up and a step down
        "aur bara" to "bigger", "aur bada" to "bigger", "zyada bara" to "bigger", "zyada bada" to "bigger",
        "thoda bara" to "bigger", "thora bara" to "bigger", "aur chhota" to "smaller", "aur chota" to "smaller",
        "zyada chhota" to "smaller", "zyada chota" to "smaller", "thoda chhota" to "smaller", "thora chhota" to "smaller",
        "اور بڑا" to "bigger", "زیادہ بڑا" to "bigger", "تھوڑا بڑا" to "bigger", "اور چھوٹا" to "smaller",
        "زیادہ چھوٹا" to "smaller", "تھوڑا چھوٹا" to "smaller",
        "और बड़ा" to "bigger", "ज़्यादा बड़ा" to "bigger", "थोड़ा बड़ा" to "bigger", "और छोटा" to "smaller",
        "ज़्यादा छोटा" to "smaller", "थोड़ा छोटा" to "smaller",
        "আরও বড়" to "bigger", "আরো বড়" to "bigger", "আরও ছোট" to "smaller", "আরো ছোট" to "smaller",
        "lebih besar" to "bigger", "lebih kecil" to "smaller", "وڌيڪ وڏو" to "bigger", "وڌيڪ ننڍو" to "smaller",
        "بزرگ تر" to "bigger", "کوچک تر" to "smaller", "more big" to "bigger", "more large" to "bigger",
        "more small" to "smaller", "much bigger" to "bigger", "much smaller" to "smaller",
        // the phone's own
        "same as the phone" to "system", "same as phone" to "system", "same as the device" to "system", "same as device" to "system",
        "same as the system" to "system", "same as system" to "system", "follow the phone" to "system", "follow phone" to "system",
        "follow the system" to "system", "follow system" to "system", "follow the device" to "system", "follow device" to "system",
        "like the phone" to "system", "like phone" to "system", "as per phone" to "system", "according to the phone" to "system",
        "phone ke mutabiq" to "system", "phone ke mutabik" to "system", "phone ke hisaab se" to "system", "phone ke hisab se" to "system",
        "phone ke jaisa" to "system", "phone jaisa" to "system", "phone jesa" to "system", "phone ke sath" to "system",
        "phone ke saath" to "system", "computer ke mutabiq" to "system", "computer jaisa" to "system",
        "فون کے مطابق" to "system", "فون جیسا" to "system", "فون کے ساتھ" to "system", "فون کے حساب سے" to "system",
        "کمپیوٹر کے مطابق" to "system", "فون مطابق" to "system", "فون جهڙو" to "system", "فون سان" to "system",
        "ڪمپيوٽر مطابق" to "system",
        "حسب الهاتف" to "system", "حسب الجهاز" to "system", "مثل الهاتف" to "system", "نفس الهاتف" to "system",
        "حسب الحاسوب" to "system", "مطابق گوشی" to "system", "مطابق موبایل" to "system", "مثل گوشی" to "system",
        "همانند گوشی" to "system", "مطابق رایانه" to "system",
        "फोन जैसा" to "system", "फ़ोन जैसा" to "system", "फोन के अनुसार" to "system", "फोन के मुताबिक" to "system",
        "फोन के साथ" to "system", "कंप्यूटर जैसा" to "system",
        "ফোনের মতো" to "system", "ফোনের মত" to "system", "ফোন অনুযায়ী" to "system", "কম্পিউটারের মতো" to "system",
        "ikuti ponsel" to "system", "ikuti hp" to "system", "ikuti sistem" to "system", "ikuti perangkat" to "system",
        "sama dengan ponsel" to "system", "ikuti komputer" to "system"
    ).map { (from, to) -> " ${VoiceQuestion.normalise(from)} " to " ${VoiceQuestion.normalise(to)} " }

    /**
     * What [heard] asks of the text size or the theme, or null — nothing is guessed. [calling]: whether a word only calls him
     * ("Hudhud", "bhai"). A sentence with a figure, a name or any word this reader does not know is read as before.
     */
    internal fun read(heard: String, calling: (String) -> Boolean = { false }): Request? {
        val first = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (first.isEmpty() || first.size > MAX_WORDS + 4) return null
        // Not a figure, in any script: "text 150" or "font 2 size bara" is another sentence.
        if (first.any { w -> w.any { it.isDigit() } }) return null
        var text = " " + first.joinToString(" ") + " "
        for ((from, to) in PHRASES) if (text.contains(from)) text = text.replace(from, to)
        val said = text.trim().split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > MAX_WORDS) return null

        // The cheap question first: no word of the settings, no answer (every question comes through here).
        if (said.none { TEXT.has(it) || SIZE.has(it) || THEME.has(it) || MODE.has(it) }) return null

        var textWord = false
        var sizeWord = false
        var themeWord = false
        var modeWord = false
        var dark = false
        var light = false
        var system = false
        var bigger = false
        var smaller = false
        var big = false
        var bigEn = false
        var small = false
        var smallEn = false
        var normal = false
        var largest = false
        var most = false
        var make = false
        var set = false
        var off = false
        var on = false
        var asks = '?' in heard || '؟' in heard
        for (w in said) {
            var ok = false
            if (TEXT.has(w)) { textWord = true; ok = true }
            if (SIZE.has(w)) { sizeWord = true; ok = true }
            if (THEME.has(w)) { themeWord = true; ok = true }
            if (MODE.has(w)) { modeWord = true; ok = true }
            if (DARK.has(w)) { dark = true; ok = true }
            if (LIGHT.has(w)) { light = true; ok = true }
            if (SYSTEM.has(w)) { system = true; ok = true }
            if (BIGGER.has(w)) { bigger = true; ok = true }
            if (SMALLER.has(w)) { smaller = true; ok = true }
            if (BIG.has(w)) { big = true; ok = true; if (BIG_EN.has(w)) bigEn = true }
            if (SMALL.has(w)) { small = true; ok = true; if (SMALL_EN.has(w)) smallEn = true }
            if (NORMAL.has(w)) { normal = true; ok = true }
            if (LARGEST.has(w)) { largest = true; ok = true }
            if (MOST.has(w)) { most = true; ok = true }
            if (MAKE.has(w)) { make = true; ok = true }
            if (SET.has(w)) { set = true; ok = true }
            if (OFF.has(w)) { off = true; ok = true }
            if (ON.has(w)) { on = true; ok = true }
            if (QUESTION.has(w)) { asks = true; ok = true }
            if (!ok && (LITTLE.has(w) || calling(w))) ok = true
            if (!ok) return null
        }
        if (on && off) return null

        val values = (if (dark) 1 else 0) + (if (light) 1 else 0) + (if (system) 1 else 0)
        // "Mode" and its kin name the theme only beside a theme's own word.
        val isTheme = themeWord || (modeWord && values > 0)
        val isSize = textWord || sizeWord
        if (isTheme == isSize) return null

        return if (isSize) sizeRequest(asks, values, bigger, smaller, big, bigEn, small, smallEn, normal, largest, most, make, set, off)
        else themeRequest(asks, dark, light, system, values, bigger || smaller || big || small || normal || largest, off)
    }

    /** The text size a sentence asks, [read]'s part for the text. */
    private fun sizeRequest(
        asks: Boolean, themeValues: Int, bigger: Boolean, smaller: Boolean, big: Boolean, bigEn: Boolean, small: Boolean,
        smallEn: Boolean, normal: Boolean, largest: Boolean, most: Boolean, make: Boolean, set: Boolean, off: Boolean
    ): Request? {
        // The theme's words, or "off", in a sentence about the text: two sentences in one.
        if (themeValues > 0 || off) return null
        if (bigger && smaller) return null
        if (asks) return Request.Show(Setting.SIZE)
        // The levels the words name: the biggest, the smallest, the middle, a plain "big" or "small".
        val levels = HashSet<Int>()
        if (largest || (big && most)) levels += SIZE_LARGEST
        if (small) levels += SIZE_SMALL
        if (normal) levels += SIZE_NORMAL
        val plainBig = big && !most
        if (plainBig) levels += SIZE_LARGE
        if (bigger || smaller) {
            // "Bigger" and "large" in one sentence is no sentence.
            return if (levels.isNotEmpty()) null else Request.SizeStep(if (bigger) 1 else -1)
        }
        if (levels.size > 1) return null
        // "Font bara karo", "font chhota karo": an Urdu, Hindi or Bengali adjective with a doing word is "make it bigger" — a
        // step; people say it again until the text suits them. An English "large", "big" or "small" is the app's own word for
        // a level ("text size large karo", "make the font big"), and so is any adjective with "set" ("font bara rakho").
        if (make && !set && levels.isNotEmpty()) {
            if (plainBig && !bigEn) return Request.SizeStep(1)
            // A plain "small" is the small level only with "most" or "very" ("bohat chhota"); with a doing word it is a step.
            if (small && !smallEn && !most) return Request.SizeStep(-1)
        }
        return levels.firstOrNull()?.let { Request.SizeTo(it) } ?: Request.Show(Setting.SIZE)
    }

    /** The theme a sentence asks, [read]'s part for the theme. */
    private fun themeRequest(
        asks: Boolean, dark: Boolean, light: Boolean, system: Boolean, values: Int, sizeWords: Boolean, off: Boolean
    ): Request? {
        // The text size's words in a sentence about the theme: two sentences in one.
        if (sizeWords || values > 1) return null
        if (asks) return Request.Show(Setting.THEME)
        if (off) return when {
            dark -> Request.ThemeTo(THEME_LIGHT)
            light -> Request.ThemeTo(THEME_DARK)
            else -> null
        }
        return when {
            dark -> Request.ThemeTo(THEME_DARK)
            light -> Request.ThemeTo(THEME_LIGHT)
            system -> Request.ThemeTo(THEME_SYSTEM)
            else -> Request.Show(Setting.THEME)
        }
    }
}
