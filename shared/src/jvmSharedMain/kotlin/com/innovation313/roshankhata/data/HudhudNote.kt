package com.innovation313.roshankhata.data

/** The note reader's word lists are the strict readers' common [StrictWords]. */
private typealias NoteWords = StrictWords

/**
 * The owner's private note on a customer or a supplier, asked for (14 Nov 2026, the owner: "customer ka zaati note bol
 * kar bhi bataana chahiye" — told in full, spoken and written, to anyone who is using the phone).
 *
 * **A strict reader, like [HudhudFaith] and [HudhudStory] (lesson 121).** It answers a sentence only when the sentence
 * holds the word "note" (in the app's own spelling and the ways it is said: "note", "yaad dasht", "نوٹ", "یادداشت",
 * "ملاحظة", "नोट", "নোট"; Indonesian "catatan" only beside "pribadi", since a bare "catatan" is the ledger's record too)
 * and every other word is one of its little words, a word that says whose or which
 * ("uska", "kis kis", "mera", "private") or somebody's name in the book. So "Ali ka niji note batao" and "private note"
 * are read, and "Ali ko 500 ka note do" (a banknote), "Ali ne note diya", "note likho", "Ali ki entry ka note", a
 * credit note, a notice ("نوٹس") and an Indonesian "nota" (a bill) are never: any word it does not know, or any figure,
 * and it says nothing — it never says a wrong thing in place of "I do not know".
 *
 * It does not touch [HudhudMind], the bank or the learnt sense: [Hudhud.byRules] asks it once the people the sentence
 * names are known, and [Hudhud.decide] never lets the sense turn its reading (the sense has no label for a note and
 * would answer the customer's details).
 *
 * The reader only says what was asked of the note, never what it holds: that is [HudhudFacts]'s, and the note is the
 * owner's own words about somebody — shown and said only when asked for, never beside a customer's balance.
 */
object HudhudNote {

    /**
     * What the sentence asked.
     * [his]: a person who is not named — "uska note", "the customer's note", "this note".
     * [anyone]: all of them — "kis kis ka note hai", "who has a note", "customers' notes".
     * [priv]: the word private was said — "niji note", "ذاتی نوٹ".
     * [mine]: "mera note", "my notes" — the notes the owner wrote.
     * A sentence with none of them ("note dikhao") is read but not answered: it could be about any note in the app.
     */
    internal class Read(val his: Boolean, val anyone: Boolean, val priv: Boolean, val mine: Boolean) {
        /** Whether the sentence says anything of which note, or whose. */
        val specific: Boolean get() = his || anyone || priv || mine
    }

    /** More words than this is a sentence about something else. */
    private const val MAX_WORDS = 16

    /** More names than this in one sentence is a list of people, not one person's note. */
    private const val MAX_NAME_WORDS = 4

    /**
     * The note itself. Never "nota" (a bill, in Indonesian) and never "نوٹس"/"نوٽس" (a notice, and the plural of a banknote).
     * Plurals and the Persian "ها" are the lists' own words; Bengali's endings (নোটের, নোটে) and Arabic's article are
     * [StrictWords]'.
     */
    private val NOTE = NoteWords(
        "note notes memo memos remark remarks yaaddasht yaaddaasht yaddasht yaddaasht yaaddashtein yaaddashten yaddashtein yaddashten",
        "نوٹ نوٹوں نوٽ نوٽن نوٽون یادداشت یادداشتیں یادداشتوں یادداشتها یادداشتهای ياداشت ياداشتون ملاحظة ملاحظات ملحوظة ملحوظات",
        "नोट नोट्स", "নোট নোটগুলো নোটগুলি নোটসমূহ"
    )

    /** The note with "his/her" joined to it: "ملاحظته", "یادداشتش". */
    private val NOTE_HIS = NoteWords("ملاحظته ملاحظتها ملاحظاته ملاحظاتها یادداشتش یادداشتشان یادداشتهایش")

    /** The note with "my" joined to it: "ملاحظتي", "یادداشتم". */
    private val NOTE_MINE = NoteWords("ملاحظتي ملاحظاتي یادداشتم یادداشتهایم")

    /**
     * Indonesian's "catatan" is a note and also a record of the ledger ("catatan hari ini" is today's entries, "catatan utang Ali"
     * is Ali's account — the bank asks both). It is read as the note only when the sentence also says private ("catatan pribadi",
     * the app's own words), so the ledger's old answers stay.
     */
    private val NOTE_WEAK = NoteWords("catatan")

    /** Private: the word the app's own chip says in every language ("Niji note", "ملاحظة خاصة", "Catatan pribadi"). */
    private val PRIV = NoteWords(
        "private personal niji nijji nijee zaati zati zaatee zaaty pribadi pribadinya privat",
        "نجی نجي ذاتی ذاتي پرائیویٹ پرسنل خاص خاصة خاصه خصوصی شخصی شخصية شخصي",
        "निजी प्राइवेट पर्सनल व्यक्तिगत", "ব্যক্তিগত প্রাইভেট পার্সোনাল"
    )

    /**
     * Somebody who is not named, said once: "his", "uska", "اس", "उसका", "তার", "او", "له", "dia". A "this"/"that" is
     * the same ("ye note", "this note"): the person the talk was about.
     */
    private val HIS = NoteWords(
        "his her hers its their him this that uska uski uske iska iski iske unka unki unke inka inki inke wo woh ye yeh us uss usi un dia nya ini itu",
        "اسکا اسکی اسکے یہ وہ اس ان هن اش هایش او وی ایشان آن این هذا هذه ذلك تلك له لها",
        "उसका उसकी उसके इसका इसकी इसके उनका उनकी उनके इनका इनकी इनके वह वो ये यह उस इस उन इन",
        "তার তাঁর তাদের ওর ওদের এর এটি ওটি এই ঐ"
    )

    /**
     * "Is", "in" and "this" are words of English too, so these four mean "his" only when a "ka/ki/ke" follows ("is ka note",
     * "iska" said in two words); alone they are little words ([LITTLE]).
     */
    private val PAIR_STEM = NoteWords("is iss isi in")

    /** "Ka", "ki", "ke" and their kin: Urdu, Hindi and Sindhi's "of". */
    private val OF = NoteWords("ka ki ke", "کا کی کے جو جي جا", "का की के")

    /** A customer or a supplier, one of them: "the customer's note", "party ka note", "گاہک کا نوٹ". */
    private val PERSON = NoteWords(
        "customer client party supplier vendor gahak grahak graahak gaahak pelanggan pemasok klien",
        "گاہک کسٹمر سپلائر پارٹی کلائنٹ فریق گراهڪ ڪسٽمر پارٽي عميل زبون مورد مشتری تامین کننده فروشنده",
        "ग्राहक कस्टमर सप्लायर पार्टी", "গ্রাহক কাস্টমার সরবরাহকারী সাপ্লায়ার পার্টি"
    )

    /**
     * Which of them, or all of them: "who has a note", "kis kis ka note hai", "customers' notes", "sab ke notes",
     * "من لديه ملاحظة", "siapa saja yang punya catatan". "Kaun kaun" is said twice, not listed ([read]).
     */
    private val ANYONE = NoteWords(
        "who whom whose which all every everyone everybody list customers clients parties suppliers vendors " +
            "kis kin kiska kiski kiske kinka kinki kinke sab sabhi saare sare saray tamam gahakon grahakon fehrist " +
            "siapa semua seluruh setiap para daftar mana",
        "کس کن کسی کسے سب تمام ہر فہرست گاہکوں پارٹیوں جن جس جنکا جنکے ڪنهن ڪير سڀ سڀني فهرست گراهڪن من أي اي كل جميع قائمة لمن عملاء زبائن كسانی کدام همه هر لیست فهرست مشتریان",
        "किस किन किसका किसकी किसके किनका किनकी किनके सब सभी सारे सबके सूची लिस्ट ग्राहकों जिन जिस जिनके जिनका जिसका जिसके",
        "কার কাদের কাকে সব সকল সবার তালিকা গ্রাহকদের"
    )

    /** "Doubled" question words: "kaun kaun", "kon kon", "کون کون", "कौन कौन", "কে কে" — each of them is all of them. */
    private val WHO = NoteWords("kaun kon", "کون", "कौन", "কে")

    /** "My", "mine", "our": the owner's own — "mera note", "my notes", "میرے نوٹ", "मेरे नोट", "আমার নোট". */
    private val MINE = NoteWords(
        "my mine mera meri mere meray merey hamara hamari hamare saya aku",
        "میرا میری میرے ميرو ميري ميرا منهنجو منهنجي منهنجا ہمارا ہماری ہمارے هایم ام",
        "मेरा मेरी मेरे हमारा हमारी हमारे", "আমার আমাদের"
    )

    /**
     * Little words of a request to tell and of "what", "is", "of", "in": they ask or join, they say nothing of the book. Not
     * here, on purpose: "ko", "ne", "diya", "de", "likho", "likh", "add", "delete", "hata" — a note given, written or removed
     * is not a note asked for.
     */
    private val LITTLE = NoteWords(
        // English
        "what whats show tell read say see let me know check look open display get pull up out the a an of for to about " +
            "is are was were has have had does do there any some in on with and or please pls plz can could would will kindly s i you " +
            "written saved",
        // Roman Urdu and Hindi
        "kya kia kyaa kiya batao bataiye bataiyega batayen bataen batain batana dikhao dikhaiye dikhayen dikhaen dikhana " +
            "bata dikha hua hui hue sunao sunaiye sunayen suna parho padho parh padh bolo kaho likha likhi likhe darj mehfooz mehfuz dekho dekhein dekhen " +
            "dekh dekhna hai hain hy he tha thi thay hota hoti hote hon ho hoga hogi ka ki ke se mein me main mai maein par pe per " +
            "aur or o ya bhi hi to jo koi kisi kuch kaun kon kaunsa konsa kaisa sa si kitna kitne kitni naam paas liye baare bare baray " +
            "mujhe mjhe humen humein hamein zara zra jara wala wali wale",
        // Urdu
        "کیا بتاو بتایے بتائیں بتا دکھاو دکھایے دکھائیں دکھا سناو سنایے سنا پڑھو پڑھ بولو کہو لکھا لکھی لکھے ہے ہیں تھا تھی ہو ہوتا ہوتی " +
            "میں کا کی کے سے سا سی پر پہ اور یا بھی ہی تو جو کوئی کچھ کون کونسا کیسا کتنا کتنے کتنی نام پاس لیے لئے بارے مجھے ذرا والا والی والے محفوظ درج",
        // Sindhi
        "ڇا ڪهڙو ڪهڙي ڪهڙا ٻڌاءِ ٻڌايو ٻڌاي ڏيکار ڏيکاريو پڙهو آهي آهن هو هئي ۾ جو جي جا ۽ ڪو ڪا ڪي کي لکيل محفوظ",
        // Arabic
        "ما ماذا هل اظهر اعرض اخبرني قل اقرا ارني هي هو في عن ل لديه لديهم عند عنده عندهم الذي التي المكتوب مكتوب مكتوبة محفوظة اعطني",
        // Farsi
        "چیست چه چی آیا نشان بده بگو بخوان بگویید است هست هستند در از برای را با و یک دارد دارند ها های نوشته شده ذخیره",
        // Hindi
        "क्या सा सी बताओ बताइए बताएं दिखाओ दिखाइए दिखाएं सुनाओ सुनाइए पढ़ो बोलो कहो लिखा लिखी लिखे है हैं था थी में का की के से पर और या भी ही तो " +
            "जो कोई कुछ कौन कौनसा कैसा कितना कितने नाम पास लिए बारे मुझे ज़रा जरा वाला वाली वाले",
        // Bengali
        "কী কি কত কোন বলো বলুন দেখাও দেখান শোনাও শোনান পড়ো পড়ুন লেখা আছে আছেন হয় ছিল মধ্যে ও এবং বা কোনো কিছু নাম কাছে জন্য সম্পর্কে সম্বন্ধে আমাকে",
        // Indonesian
        "apa tolong beritahu katakan sebutkan tunjukkan tampilkan lihat baca bacakan isi isinya ada punya yang di dalam dari untuk " +
            "tentang dan atau ke kepada tersimpan ditulis tertulis saja adalah ialah"
    )

    /**
     * Phrases said as two or three words that mean one: "bata do" is "batao", "yaad dasht" is one word, "یاد داشت", "بتا دو"
     * (the "do" of "give" is no little word: "Ali ko note do" is never read). Applied to the sentence the reader normalised.
     */
    private val PHRASES: List<Pair<String, String>> = listOf(
        "yaad dasht" to "yaaddasht", "yaad daasht" to "yaaddasht", "yad dasht" to "yaaddasht", "yad daasht" to "yaaddasht",
        "bata do" to "batao", "bata den" to "batao", "bata dein" to "batao", "bata dijiye" to "batao", "bata dijiyega" to "batao",
        "bata dena" to "batao", "bata de" to "batao", "dikha do" to "dikhao", "dikha den" to "dikhao", "dikha dein" to "dikhao",
        "dikha dijiye" to "dikhao", "dikha dena" to "dikhao", "dikha de" to "dikhao", "suna do" to "sunao", "suna den" to "sunao",
        "suna dein" to "sunao", "suna dijiye" to "sunao", "suna de" to "sunao",
        "یاد داشت" to "یادداشت", "بتا دو" to "بتاو", "بتا دیں" to "بتاو", "بتا دیجیے" to "بتاو", "بتا دے" to "بتاو",
        "دکھا دو" to "دکھاو", "دکھا دیں" to "دکھاو", "دکھا دیجیے" to "دکھاو", "سنا دو" to "سناو", "سنا دیں" to "سناو",
        "बता दो" to "बताओ", "बता दें" to "बताओ", "बता दीजिए" to "बताओ", "बता दीजिये" to "बताओ", "बता दे" to "बताओ",
        "दिखा दो" to "दिखाओ", "दिखा दें" to "दिखाओ", "दिखा दीजिए" to "दिखाओ", "सुना दो" to "सुनाओ", "सुना दें" to "सुनाओ",
        "বলে দাও" to "বলো", "বলে দিন" to "বলো", "দেখিয়ে দাও" to "দেখাও", "দেখিয়ে দিন" to "দেখাও"
    ).map { (from, to) -> " ${VoiceQuestion.normalise(from)} " to " ${VoiceQuestion.normalise(to)} " }

    /**
     * What [heard] asks of the private note, or null — nothing is guessed. [calling]: whether a word only calls him ("Hudhud",
     * "bhai"). [known]: whether a word is, in this book, a word of somebody's name ("Ali", "علی", "আলীর"); at most four such words
     * are let through, and a word that is not any of these is a sentence about something else.
     */
    internal fun read(heard: String, calling: (String) -> Boolean = { false }, known: (String) -> Boolean = { false }): Read? {
        val first = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (first.isEmpty() || first.size > MAX_WORDS + 4) return null
        // Not a figure, in any script: "Ali ko 500 ka note do" is a banknote.
        if (first.any { w -> w.any { it.isDigit() } }) return null
        var text = " " + first.joinToString(" ") + " "
        for ((from, to) in PHRASES) if (text.contains(from)) text = text.replace(from, to)
        val said = text.trim().split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > MAX_WORDS) return null

        // The cheap question first: no word of a note, no answer — and no look through the book's names for a sentence that is
        // about something else (every question comes through here).
        if (said.none { NOTE.has(it) || NOTE_HIS.has(it) || NOTE_MINE.has(it) || NOTE_WEAK.has(it) }) return null

        var his = false
        var anyone = false
        var priv = false
        var mine = false
        var names = 0
        var strong = false
        var weak = false
        for ((i, w) in said.withIndex()) {
            var ok = false
            if (NOTE.has(w)) { strong = true; ok = true }
            if (NOTE_HIS.has(w)) { strong = true; his = true; ok = true }
            if (NOTE_MINE.has(w)) { strong = true; mine = true; ok = true }
            if (NOTE_WEAK.has(w)) { weak = true; ok = true }
            if (PRIV.has(w)) { priv = true; ok = true }
            if (HIS.has(w) || PERSON.has(w)) { his = true; ok = true }
            if (PAIR_STEM.has(w) && i + 1 < said.size && OF.has(said[i + 1])) { his = true; ok = true }
            if (MINE.has(w)) { mine = true; ok = true }
            if (ANYONE.has(w)) { anyone = true; ok = true }
            // "Kaun kaun", "kon kon": each of them.
            if (WHO.has(w) && (i > 0 && said[i - 1] == w || i + 1 < said.size && said[i + 1] == w)) { anyone = true; ok = true }
            if (!ok && (LITTLE.has(w) || calling(w))) ok = true
            if (!ok && known(w)) { names++; ok = true }
            if (!ok) return null
        }
        if (names > MAX_NAME_WORDS) return null
        if (!strong && !(weak && priv)) return null
        return Read(his, anyone, priv, mine)
    }

    /**
     * Whether [w] (a word as [VoiceQuestion.normalise] writes it) is the note's own word: "note", "notes", "yaaddasht", "نوٹ",
     * "नोट", "ملاحظة". A customer's own name may hold one ("Note House"); the word then asks or names, not both for free
     * ([Hudhud]'s noteAsk counts them).
     */
    internal fun isNoteWord(w: String): Boolean = NOTE.has(w) || NOTE_HIS.has(w) || NOTE_MINE.has(w) || NOTE_WEAK.has(w)

    // ------------------------------------------------------------------------------------------------ the note itself

    /**
     * A note's own lines, as the owner wrote them: split at the line breaks, each trimmed, the empty ones left out. The card
     * shows each as a line of its own, so a note kept as a short list ("only cash" / "ask for his son") reads as one.
     */
    fun linesOf(note: String): List<String> =
        note.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }

    /** From this many digits in a row a figure is read as a number to dial or pay to, not as an amount of a few figures. */
    private const val SECRET_IN_A_ROW = 7

    /** Or from this many all told, when hyphens, dots, spaces or brackets split them into groups ("35202-1234567-1"). */
    private const val SECRET_TOTAL = 9

    /** Between two groups of one number: at most two of these ("(0300) 1234567"). */
    private const val JOINERS = " -.()"

    /**
     * What a note holds that is a number somebody could dial, pay to or be known by — a phone, an account, an ID card number
     * (14 Nov 2026): shown with the note and never said aloud, as a customer's number is never said ("on the screen") — a
     * customer may be standing at the counter. A run of digits in any script, with a "+" in front and one or two spaces,
     * hyphens, dots or brackets between its groups: seven or more in a row, or nine or more all told. An amount of a few
     * figures, a day ("12-03-2025": eight) and a time stay as they were. A figure of seven digits or more cannot be told
     * from a number, so it is withheld from the voice too (it is shown, only not said).
     */
    fun secretsIn(note: String): List<String> {
        val found = LinkedHashSet<String>()
        var i = 0
        while (i < note.length) {
            val start = i
            if (note[i] == '+' && i + 1 < note.length && note[i + 1].isDigit()) i++
            else if (!note[i].isDigit()) { i++; continue }
            var total = 0
            var inARow = 0
            var longest = 0
            var end = i
            var j = i
            while (j < note.length) {
                if (note[j].isDigit()) {
                    total++
                    inARow++
                    if (inARow > longest) longest = inARow
                    j++
                    end = j
                } else {
                    // One or two joiners between two groups of digits keep the number going; anything else ends it.
                    var k = j
                    while (k < note.length && k - j < 2 && note[k] in JOINERS) k++
                    if (k > j && k < note.length && note[k].isDigit()) {
                        inARow = 0
                        j = k
                    } else break
                }
            }
            if (longest >= SECRET_IN_A_ROW || total >= SECRET_TOTAL) {
                // A bracket that opens just before the number, or closes just after it, goes with it ("(0300) 1234567").
                var from = start
                if (from > 0 && note[from - 1] == '(') from--
                var to = end
                val piece = note.substring(from, to)
                if (to < note.length && note[to] == ')' && piece.count { it == '(' } > piece.count { it == ')' }) to++
                found += note.substring(from, to)
            }
            i = maxOf(j, start + 1)
        }
        return found.toList()
    }
}
