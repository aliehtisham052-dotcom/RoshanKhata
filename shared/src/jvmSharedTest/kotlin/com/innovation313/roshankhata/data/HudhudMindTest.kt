package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.AboutTopic
import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.Hudhud.ShopFacet
import com.innovation313.roshankhata.data.Hudhud.StatusOf
import com.innovation313.roshankhata.data.Hudhud.Talk
import com.innovation313.roshankhata.data.Hudhud.Thing
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hudhud's Brain v2 (13 Nov 2026, the owner: "kya aa gaya to nahi pata laga, se aa gaya, main aa gaya, ko aa gaya…
 * tamam languages mein samjhe"): one question asked the many ways a shop asks it — any order, any little words, a
 * spelling a letter off — in all nine of the app's languages, each taken for the one thing it asks. Every table
 * collects every miss before it fails, so one run shows them all. Made-up names and goods only (lesson 80).
 */
class HudhudMindTest {

    // understand() reads with the sense the app ships (the rules alone: byRules).
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(
        Hudhud.Name(1, "Abid Ali"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Naeem Khan"),
        Hudhud.Name(4, "Malik Riaz"), Hudhud.Name(5, "Kamal Din"), Hudhud.Name(6, "Rehmat Store"),
        Hudhud.Name(7, "Zafar Iqbal")
    )

    private val goods = listOf(
        Hudhud.Name(101, "Urea"), Hudhud.Name(102, "DAP"), Hudhud.Name(103, "Confidor"), Hudhud.Name(104, "Sugar")
    )

    private val topics = listOf(
        Hudhud.Topic("intro_backup", "Backup", "Keep a copy of your book on Google Drive or the phone."),
        Hudhud.Topic("intro_cheques", "Cheques", "Note every cheque you get or give and the day it is due."),
        Hudhud.Topic("intro_staff", "Staff", "Salaries, advances and attendance of the people who work for you.")
    )

    // The rules' own reading: HudhudSenseTest measures the rules and the sense together on the held-out set.
    private fun ask(s: String, before: Ask? = null) = Hudhud.byRules(s, names, topics, goods = goods, before = before)

    /** Every sentence of [said] names nobody, and he tells what he can without a name ([Ask.NeedName]). */
    private fun needs(said: List<String>) {
        val wrong = said.mapNotNull { s -> ask(s).takeIf { it !is Ask.NeedName }?.let { "\"$s\" -> $it" } }
        assertEquals("expected a question about somebody unnamed:\n" + wrong.joinToString("\n"), 0, wrong.size)
    }

    /** Every sentence of [said] must be taken for [expected]; all misses are listed together. */
    private fun all(expected: Ask, said: List<String>, before: Ask? = null) {
        val wrong = said.mapNotNull { s -> ask(s, before).takeIf { it != expected }?.let { "\"$s\" -> $it" } }
        assertEquals("expected $expected:\n" + wrong.joinToString("\n"), 0, wrong.size)
    }

    /** As [all], for a question whose answer may be a choice that holds [id] (a name written in another script). */
    private fun holds(id: Long, said: List<String>) {
        val wrong = said.mapNotNull { s ->
            val a = ask(s)
            val ids = when (a) { is Ask.Parties -> a.ids; is Ask.Person -> a.ids; else -> emptyList() }
            if (id in ids) null else "\"$s\" -> $a"
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    // ------------------------------------------------------------------------------------------------ himself

    @Test fun `his name, asked any way, in all nine languages`() = all(
        Ask.Chat(Talk.WHO_ARE_YOU), listOf(
            // Roman Urdu: the owner's own sentence first (13 Nov 2026: "I can't answer this yet").
            "aap ka kya naam hai", "aap ka naam kya hai", "aapka naam kya hai", "ap ka naam kia hai", "apka nam kya hai",
            "tumhara naam kya hai", "tumhara kya naam hai", "tera naam kya hai", "naam kya hai aap ka", "naam batao apna",
            "apna naam batao", "aap ka shubh naam", "aap kaun hain", "tum ho kaun", "ye kaun hai", "aap ka naam?",
            "aap kya ho", "Hudhud kya hai", "bhai aap ka naam kya hai", "jee aap ka naam kya hai please", "app ka naam kya hai",
            // English
            "what is your name", "what's your name", "whats ur name", "your good name please", "may i know your name",
            "tell me your name", "who are you",
            // Urdu
            "آپ کا نام کیا ہے", "آپ کا کیا نام ہے", "نام کیا ہے آپ کا", "آپکا نام کیا ہے", "تمہارا کیا نام ہے", "اپنا نام بتائیں",
            "آپ کون ہیں",
            // Hindi
            "आपका नाम क्या है", "आपका क्या नाम है", "आप का नाम क्या है", "तुम्हारा क्या नाम है", "नाम क्या है आपका", "आप कौन हैं",
            // Bengali
            "তোমার নাম কি", "আপনার নাম কী", "আপনি কে",
            // Arabic
            "ما اسمك", "ما هو اسمك", "من أنت",
            // Farsi
            "اسم شما چیست", "نام شما چیست", "شما کی هستید",
            // Sindhi
            "توهان جو نالو ڇا آهي", "تنهنجو نالو ڇا آهي",
            // Indonesian
            "siapa nama kamu", "nama kamu siapa", "siapa namamu", "nama anda apa"
        )
    )

    @Test fun `his name with a salaam is answered with the salaam`() {
        assertEquals(Ask.Chat(Talk.WHO_ARE_YOU, salaam = true), ask("Assalam o alaikum aap ka naam kya hai"))
    }

    @Test fun `the owner's own name`() = all(
        Ask.About(AboutTopic.OWNER_NAME), listOf(
            "mera naam kya hai", "mera kya naam hai", "main kaun hoon", "aap ko mera naam pata hai", "what is my name",
            "do you know my name", "میرا نام کیا ہے", "मेरा नाम क्या है", "আমার নাম কি", "ما اسمي", "اسم من چیست",
            "منهنجو نالو ڇا آهي", "siapa nama saya", "nama saya siapa"
        )
    )

    @Test fun `how he is, in any order`() = all(
        Ask.Chat(Talk.HOW_ARE_YOU), listOf(
            "aap ka haal kya hai", "haal kya hai aap ka", "aap kaise ho", "kaise ho aap", "tum kaise ho", "kya haal hai",
            "آپ کا حال کیا ہے", "आपका हाल क्या है", "আপনি কেমন আছেন", "كيف حالك", "حال شما چطور است", "apa kabar"
        )
    )

    @Test fun `what he can tell`() = all(
        Ask.Abilities, listOf(
            "aap kya kya bata sakte ho", "bata sakte ho kya kya", "tumhe kya kya aata hai", "kya kar sakte ho aap",
            "what can you do", "what do you know"
        )
    )

    @Test fun `who made him, what the app is, the languages, the version, where the book stays`() {
        all(
            Ask.About(AboutTopic.MAKER), listOf(
                "tumhe kis ne banaya", "aap ko kisne banaya", "who made you", "who created this app", "app kis ne banai",
                "Roshan Khata kis ne banaya", "developer kaun hai", "آپ کو کس نے بنایا", "आपको किसने बनाया",
                "توهان کي ڪنهن ٺاهيو", "siapa yang membuat kamu", "siapa pembuat aplikasi ini"
            )
        )
        all(Ask.About(AboutTopic.APP), listOf("Roshan Khata kya hai", "ye app kya hai", "app ke baare mein batao"))
        all(
            Ask.About(AboutTopic.LANGUAGES), listOf(
                "aap kaunsi zuban samajhte ho", "kitni zubanein aati hain", "which languages do you speak",
                "آپ کون سی زبان سمجھتے ہیں", "आप कौन सी भाषा समझते हो", "তুমি কোন ভাষা বোঝো", "bahasa apa yang kamu mengerti"
            )
        )
        all(Ask.About(AboutTopic.VERSION), listOf("app ka version kya hai", "kaunsa version hai", "which version", "versi aplikasi"))
        all(
            Ask.About(AboutTopic.PRIVACY), listOf(
                "mera data safe hai", "data kahan jata hai", "kya mera data mehfooz hai", "is my data safe",
                "kya aap mera data bahar bhejte ho", "میرا ڈیٹا محفوظ ہے", "मेरा डेटा सुरक्षित है"
            )
        )
    }

    @Test fun `today's date, the day and the time`() = all(
        Ask.Clock, listOf(
            "aaj kya tareekh hai", "aaj ki tareekh", "tareekh kya hai", "aaj kaunsa din hai", "aaj kya din hai",
            "kitne baje hain", "what is the date today", "today's date", "what day is it today", "islami tareekh kya hai",
            "آج کیا تاریخ ہے", "आज क्या तारीख है", "আজ কত তারিখ", "ما تاريخ اليوم", "امروز چندم است", "اڄ ڪهڙي تاريخ آهي",
            "tanggal berapa hari ini", "hari ini hari apa"
        )
    )

    @Test fun `a lone name could be anyone's, so all three are offered`() {
        val expected = Ask.DidYouMean(listOf(Ask.Chat(Talk.WHO_ARE_YOU), Ask.About(AboutTopic.OWNER_NAME), Ask.Shop(ShopFacet.NAME)))
        all(expected, listOf("naam", "naam kya hai", "name?", "نام"))
    }

    // ------------------------------------------------------------------------------------------------ the people

    @Test fun `a customer's balance, asked the many ways a shop asks it`() = all(
        Ask.Parties(listOf(1L)), listOf(
            // The owner's own example (13 Nov 2026) and the ways it is said around it.
            "Abid Ali ke kitne paise baqaya hain", "abid ali ka kitna baqaya hai", "Abid Ali ne kitne paise dene hain",
            "Abid Ali se kitne lene hain", "Abid Ali ka hisaab", "Abid Ali ka balance", "Aabid Ali ka baqaya",
            "abid ali ke zimme kitne paise hain", "Abid Ali par kitna udhar hai", "abid ali ka khata dikhao",
            "Abid Ali ki raqam", "kitna baqaya hai Abid Ali ka", "baqaya Abid Ali", "Abid Ali",
            "abid ali ka bakaya", "Abid Ali ka baqaaya", "saldo Abid Ali", "berapa utang Abid Ali"
        )
    )

    @Test fun `a customer's balance asked in another script`() = holds(
        1L, listOf(
            "عابد علی کا بقایا", "عابد علی کے کتنے پیسے باقی ہیں", "आबिद अली का बकाया", "आबिद अली के कितने पैसे बाकी हैं",
            "আবিদ আলীর বাকি কত", "حساب عابد علی"
        )
    )

    @Test fun `his number`() = all(
        Ask.Person(listOf(1L), Facet.PHONE), listOf(
            "Abid Ali ka number", "Abid Ali ka number kya hai", "abid ali ka phone number", "Abid Ali ka mobile",
            "Abid ka contact", "number do Abid Ali ka", "Abid Ali ka nambar", "Abid Ali ka mobail number",
            "phone number of Abid Ali", "nomor telepon Abid Ali", "nomor Abid Ali"
        )
    )

    @Test fun `his number in another script`() {
        val wrong = listOf(
            "عابد علی کا نمبر", "आबिद अली का नंबर", "আবিদ আলীর নম্বর", "رقم هاتف عابد علي", "شماره تلفن عابد علی",
            "عابد علي جو نمبر"
        ).mapNotNull { s -> val a = ask(s); if (a is Ask.Person && a.facet == Facet.PHONE && 1L in a.ids) null else "\"$s\" -> $a" }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    @Test fun `his last payment, his last entry`() {
        all(
            Ask.Person(listOf(1L), Facet.LAST_PAYMENT), listOf(
                "Abid Ali ne aakhri payment kab ki", "abid ali ki last payment", "Abid Ali ne akhri dafa paise kab diye",
                "Abid Ali ne paise kab diye", "abid ali ki aakhri wasooli", "when did Abid Ali last pay",
                "last payment of Abid Ali", "kapan pembayaran terakhir Abid Ali"
            )
        )
        all(
            Ask.Person(listOf(1L), Facet.LAST_ENTRY), listOf(
                "Abid Ali ki aakhri entry", "Abid Ali ka aakhri sauda kab hua", "Abid Ali ki last entry kya thi",
                "last entry of Abid Ali"
            )
        )
    }

    @Test fun `his limit, his promise, who he is, since when, how many entries`() {
        all(
            Ask.Person(listOf(1L), Facet.LIMIT), listOf(
                "Abid Ali ki limit", "Abid Ali ki udhaar ki hadd kitni hai", "Abid Ali ka credit limit", "batas utang Abid Ali"
            )
        )
        all(Ask.Person(listOf(1L), Facet.PROMISE), listOf("Abid Ali ka wada", "Abid Ali ne kab ka wada kiya", "Abid Ali ki due date", "janji Abid Ali"))
        all(
            Ask.Person(listOf(1L), Facet.DETAILS), listOf(
                "Abid Ali kaun hai", "Abid Ali ke walid ka naam", "Abid Ali ka gaon", "Abid Ali kahan rehta hai",
                "Abid Ali ka address", "Abid Ali ki maloomat", "Abid Ali ki details", "alamat Abid Ali", "desa Abid Ali"
            )
        )
        all(Ask.Person(listOf(1L), Facet.SINCE), listOf("Abid Ali kab se customer hai", "Abid Ali ki pehli entry", "since when is Abid Ali a customer"))
        all(
            Ask.Person(listOf(1L), Facet.ENTRIES), listOf(
                "Abid Ali ki kitni entries hain", "Abid Ali ke saath kitni dafa len den hua", "how many entries of Abid Ali"
            )
        )
    }

    @Test fun `a name is never read as a word, and a word that is also a name part needs the name`() {
        // Naeem is a man, not "naam" heard a letter off.
        assertEquals(Ask.Parties(listOf(3L)), ask("Naeem ka baqaya"))
        assertEquals(Ask.Person(listOf(3L), Facet.PHONE), ask("Naeem ka number"))
        // "Malik", "Store", "Din" are name parts and words: with the rest of the name they name him.
        assertEquals(Ask.Person(listOf(4L), Facet.PHONE), ask("Malik Riaz ka number"))
        assertEquals(Ask.Person(listOf(6L), Facet.PHONE), ask("Rehmat Store ka number"))
        assertEquals(Ask.Parties(listOf(5L)), ask("Kamal Din ka baqaya"))
        // Alone, about the shop, "malik" is the owner.
        assertEquals(Ask.About(AboutTopic.OWNER_NAME), ask("dukaan ka malik kaun hai"))
    }

    // ------------------------------------------------------------------------------------------------ goods

    @Test fun `a product's price, stock, cost, suppliers, deliveries and buyers`() {
        all(Ask.Goods(listOf(101L), GoodsFacet.OVERVIEW), listOf("Urea", "urea ke baare mein batao", "Urea kya hai"))
        all(
            Ask.Goods(listOf(101L), GoodsFacet.PRICE), listOf(
                "Urea ka rate", "urea ka rate kya hai", "Urea ki qeemat", "Urea ki keemat batao", "price of Urea", "harga Urea"
            )
        )
        all(Ask.Goods(listOf(102L), GoodsFacet.PRICE), listOf("DAP ka bhaao", "DAP ka rate kya chal raha hai"))
        all(
            Ask.Goods(listOf(101L), GoodsFacet.STOCK), listOf(
                "Urea kitni bachi hai", "Urea ka stock", "urea kitni baqi hai", "is Urea available", "Urea ka stock kitna hai",
                "stok Urea"
            )
        )
        all(Ask.Goods(listOf(102L), GoodsFacet.STOCK), listOf("DAP maujood hai"))
        all(Ask.Goods(listOf(101L), GoodsFacet.COST), listOf("Urea ka khareed rate", "Urea kitne mein khareedi", "purchase rate of Urea"))
        all(
            Ask.Goods(listOf(101L), GoodsFacet.SUPPLIERS), listOf(
                "Urea kis supplier ka sasta hai", "urea kahan se sasti milti hai", "Urea sab se mehenga kis ka hai",
                "Urea kis kis supplier se aata hai", "cheapest supplier for Urea", "Urea kis company ki sasti hai"
            )
        )
        all(Ask.Goods(listOf(102L), GoodsFacet.LAST_IN), listOf("DAP aakhri dafa kab aaya", "DAP kab aayi thi", "last delivery of DAP"))
        all(
            Ask.Goods(listOf(101L), GoodsFacet.BUYERS), listOf(
                "Urea kis ne khareeda", "Urea kis kis ne liya", "who bought Urea", "Urea kis customer ne liya"
            )
        )
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.SOLD, Window.TODAY), ask("aaj Urea kitni biki"))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.SOLD, Window.THIS_MONTH), ask("Urea ki is mahine ki sale"))
        assertEquals(Ask.Goods(listOf(102L), GoodsFacet.SOLD, Window.YESTERDAY), ask("kal DAP kitna bika"))
    }

    // ------------------------------------------------------------------------------------------------ the shop

    @Test fun `the shop's own profile, in all nine languages`() {
        all(
            Ask.Shop(ShopFacet.NAME), listOf(
                "meri dukaan ka naam kya hai", "dukaan ka naam", "shop ka naam kya hai", "دکان کا نام کیا ہے",
                "दुकान का नाम क्या है", "দোকানের নাম কি", "ما اسم المحل", "نام مغازه چیست", "دڪان جو نالو ڇا آهي", "nama toko apa"
            )
        )
        all(Ask.Shop(ShopFacet.ADDRESS), listOf("shop ka address", "dukaan ka pata kya hai", "دکان کا پتہ", "alamat toko"))
        all(Ask.Shop(ShopFacet.PHONE), listOf("mera number kya hai", "dukaan ka phone number", "shop ka number"))
        all(Ask.Shop(ShopFacet.EMAIL), listOf("email kya hai", "dukaan ki email"))
        all(Ask.Shop(ShopFacet.TAX), listOf("mera tax number", "ntn number kya hai", "dukaan ka ntn"))
        all(Ask.Shop(ShopFacet.BANK), listOf("bank account kya hai", "mera bank", "iban kya hai"))
        all(Ask.Shop(ShopFacet.CURRENCY), listOf("currency kya hai", "kaunsi currency hai"))
        all(Ask.Shop(ShopFacet.COUNTRY), listOf("kis mulk ki dukaan hai", "country kya hai"))
        all(Ask.Shop(ShopFacet.TRADE), listOf("dukaan ki qisam", "meri dukaan kis type ki hai"))
        all(Ask.Shop(ShopFacet.ALL), listOf("dukaan ki maloomat", "shop details", "meri dukaan ki tafseel"))
    }

    // ------------------------------------------------------------------------------------------------ counts

    @Test fun `how many customers, suppliers, goods and staff`() {
        all(
            Ask.Count(Thing.CUSTOMERS), listOf(
                "kitne customer hain", "mere kitne gahak hain", "how many customers", "total customers kitne hain",
                "کتنے گاہک ہیں", "कितने ग्राहक हैं", "কতজন গ্রাহক আছে", "كم عدد العملاء", "چند مشتری دارم",
                "ڪيترا گراهڪ آهن", "berapa pelanggan"
            )
        )
        all(Ask.Count(Thing.SUPPLIERS), listOf("kitne supplier hain", "how many suppliers", "کتنے سپلائر ہیں", "कितने सप्लायर हैं"))
        all(Ask.Count(Thing.PRODUCTS), listOf("kitne items hain", "how many products", "कितने प्रोडक्ट हैं", "berapa barang"))
        all(Ask.Count(Thing.STAFF), listOf("kitne mulazim hain", "staff kitna hai", "how many employees", "کتنے ملازم ہیں", "कितने कर्मचारी हैं", "berapa karyawan"))
    }

    @Test fun `what was made and done today, the owner's own questions`() {
        all(
            Ask.Count(Thing.INVOICES, Window.TODAY), listOf(
                "aaj kitni invoices bani", "aaj ke din ki kitni invoices bani hain", "aaj kitne invoice banaye",
                "how many invoices today", "آج کتنی انوائس بنیں", "आज कितनी इनवॉइस बनीं", "আজ কয়টি ইনভয়েস তৈরি হয়েছে",
                "كم فاتورة اليوم", "امروز چند فاکتور صادر شد", "اڄ ڪيترا انوائس ٺهيا", "berapa faktur hari ini"
            )
        )
        all(Ask.Count(Thing.INVOICES, Window.THIS_MONTH), listOf("is mahine kitni invoices bani"))
        all(Ask.Count(Thing.ENTRIES, Window.TODAY), listOf("aaj kitni entries hui", "aaj kitne indiraj hue", "how many entries today", "आज कितनी एंट्री हुई"))
        all(Ask.Count(Thing.BILLS, Window.THIS_MONTH), listOf("is mahine kitne bill aaye"))
        all(
            Ask.Count(Thing.CALCULATIONS, Window.TODAY), listOf(
                "aaj calculator par kitni martaba calculation hui", "aaj calculator kitni dafa use hua",
                "how many calculations today", "آج کیلکولیٹر کتنی بار استعمال ہوا", "आज कैलकुलेटर कितनी बार इस्तेमाल हुआ",
                "kalkulator dipakai berapa kali hari ini"
            )
        )
        all(Ask.Count(Thing.CALCULATIONS), listOf("calculator kitni dafa use hua"))
        all(
            Ask.Count(Thing.APP_OPENS), listOf(
                "app kitni martaba open hui", "app kitni dafa khuli", "how many times was the app opened",
                "ایپ کتنی بار کھلی", "ऐप कितनी बार खुला", "অ্যাপ কতবার খোলা হয়েছে", "كم مرة فتح التطبيق",
                "برنامه چند بار باز شد", "ايپ ڪيترا ڀيرا کليو", "berapa kali aplikasi dibuka"
            )
        )
        all(Ask.Count(Thing.APP_OPENS, Window.TODAY), listOf("app aaj kitni dafa khuli", "aaj kitni baar app khola"))
    }

    // ------------------------------------------------------------------------------------------------ the pages

    @Test fun `where cheques, plans, bills and stock stand`() {
        all(
            Ask.Status(StatusOf.CHEQUES), listOf(
                "kitne cheque pending hain", "cheque kab clear honge", "pending cheques", "kaunse cheque baqi hain",
                "کتنے چیک باقی ہیں", "कितने चेक पेंडिंग हैं", "কতগুলো চেক বাকি", "ڪيترا چيڪ باقي آهن", "berapa cek tertunda"
            )
        )
        all(Ask.Status(StatusOf.PLANS), listOf("kitni qistein baqi hain", "aaj kis ki qist hai", "installments due", "کتنی قسطیں باقی ہیں", "कितनी किस्तें बाकी हैं", "berapa cicilan"))
        all(Ask.Status(StatusOf.BILLS), listOf("kaunse bill baqi hain", "supplier ke kitne bill pending hain"))
        all(
            Ask.Status(StatusOf.LOW_STOCK), listOf(
                "kya maal kam hai", "kaunsa maal khatam ho raha hai", "low stock", "stock kam hai", "stock kitna hai",
                "کون سا مال کم ہے", "कौन सा माल कम है", "stok habis"
            )
        )
        all(
            Ask.Status(StatusOf.BOOK), listOf(
                "jitna data hai uski information", "khate ki maloomat do", "sara data batao", "mere data ki tafseel",
                "kitna data hai", "how much data is there", "ڈیٹا کی معلومات", "डेटा की जानकारी", "informasi data"
            )
        )
    }

    @Test fun `goods that came in and what happened today`() {
        all(Ask.Deliveries(Window.TODAY), listOf("aaj kis supplier se maal aaya", "آج کس سپلائر سے مال آیا", "आज किस सप्लायर से माल आया", "আজ কোন মাল এসেছে", "ما البضائع التي وصلت اليوم"))
        all(Ask.Deliveries(), listOf("aakhri delivery kab aayi", "maal kab aaya", "last delivery"))
        all(Ask.Deliveries(Window.YESTERDAY), listOf("kal kaunsa maal aaya"))
        all(
            Ask.Activity(Window.TODAY), listOf(
                "aaj kya kya hua", "aaj kya hua dukaan mein", "aaj ki report", "aaj ki report do", "what happened today",
                "today's report", "آج کیا ہوا", "आज क्या हुआ", "আজ কী হয়েছে", "ماذا حدث اليوم", "اڄ ڇا ٿيو",
                "apa yang terjadi hari ini", "laporan hari ini"
            )
        )
        all(Ask.Activity(), listOf("kis ne kya delete kiya", "kya kya edit hua"))
    }

    @Test fun `money that came in, the staff's salary, the whole khata, the shop alone`() {
        all(
            Ask.Collected(Window.TODAY), listOf(
                "aaj kitne paise aaye", "aaj kitni wasooli hui", "aaj kitni payment aayi", "aaj kitne paise mile",
                "how much money came in today", "آج کتنے پیسے آئے", "आज कितने पैसे आए", "আজ কত টাকা এসেছে",
                "كم فلوس وصلت اليوم", "امروز چقدر پول آمد", "اڄ ڪيترا پئسا آيا", "berapa uang masuk hari ini"
            )
        )
        all(Ask.Collected(Window.THIS_WEEK), listOf("is hafte kitni payment aayi"))
        all(Ask.Status(StatusOf.STAFF), listOf("salary kitni deni hai", "kis ki tankhwah baqi hai", "staff ki kitni salary baqi hai"))
        all(Ask.Status(StatusOf.BOOK), listOf("mera hisaab", "hisaab dikhao", "mera khata"))
        all(Ask.Shop(ShopFacet.ALL), listOf("dukaan", "meri dukaan"))
        all(Ask.Figure(Kind.NOT_PAID), listOf("kaun nahi de raha", "kis ne paise nahi diye"))
    }

    @Test fun `a question about somebody who is not named asks whose`() {
        needs(listOf("uska number", "limit kitni hai", "wada kab ka hai", "aakhri payment kab aayi", "walid ka naam"))
        assertEquals(Ask.DidYouMean(listOf(Ask.Shop(ShopFacet.PHONE), Ask.NeedName(Facet.PHONE))), ask("number"))
    }

    // ------------------------------------------------------------------------------------------------ follow-ups

    @Test fun `a question that carries on from the last answer`() {
        val abid = Ask.Parties(listOf(1L))
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), ask("uska number", abid))
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), ask("iska number kya hai", abid))
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), ask("उसका नंबर", abid))
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), ask("اس کا نمبر", abid))
        assertEquals(Ask.Person(listOf(1L), Facet.LAST_PAYMENT), ask("us ki aakhri payment", abid))
        assertEquals(Ask.Parties(listOf(1L)), ask("uska baqaya", abid))
        assertEquals(Ask.WhenPays(listOf(1L)), ask("wo kab dega", abid))
        // Someone else, the same question.
        val phone = Ask.Person(listOf(1L), Facet.PHONE)
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), ask("aur Bashir ka?", phone))
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), ask("Bashir ka?", phone))
        // The same figure over another span of days.
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.YESTERDAY), ask("aur kal?", Ask.Figure(Kind.SALES_TODAY)))
        assertEquals(Ask.Figure(Kind.SALES_MONTH), ask("aur is mahine?", Ask.Figure(Kind.SALES_TODAY)))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_WEEK), ask("pichle hafte ki?", Ask.Figure(Kind.SALES_TODAY)))
        assertEquals(Ask.Count(Thing.INVOICES, Window.YESTERDAY), ask("aur kal?", Ask.Count(Thing.INVOICES, Window.TODAY)))
        // Another product, the same question; and "iska" for the product just asked about.
        val urea = Ask.Goods(listOf(101L), GoodsFacet.PRICE)
        assertEquals(Ask.Goods(listOf(102L), GoodsFacet.PRICE), ask("aur DAP ka?", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.STOCK), ask("iska stock", urea))
        // With nothing before, a name alone is still his balance.
        assertEquals(Ask.Parties(listOf(2L)), ask("Bashir ka?"))
    }

    // ------------------------------------------------------------------------------------------------ robustness

    /** Sentences whose meaning does not hang on word order or on a phrase. */
    private val steady: List<Pair<String, Ask>> by lazy {
        listOf(
            "aap ka naam kya hai" to Ask.Chat(Talk.WHO_ARE_YOU),
            "mera naam kya hai" to Ask.About(AboutTopic.OWNER_NAME),
            "Abid Ali ka number kya hai" to Ask.Person(listOf(1L), Facet.PHONE),
            "Abid Ali ka baqaya" to Ask.Parties(listOf(1L)),
            "Abid Ali ki aakhri payment" to Ask.Person(listOf(1L), Facet.LAST_PAYMENT),
            "Urea ka rate" to Ask.Goods(listOf(101L), GoodsFacet.PRICE),
            "Urea ka stock" to Ask.Goods(listOf(101L), GoodsFacet.STOCK),
            "dukaan ka naam" to Ask.Shop(ShopFacet.NAME),
            "kitne customer hain" to Ask.Count(Thing.CUSTOMERS),
            "aaj kitni invoices bani" to Ask.Count(Thing.INVOICES, Window.TODAY),
            "aaj kya tareekh hai" to Ask.Clock,
            "kitne cheque pending hain" to Ask.Status(StatusOf.CHEQUES),
            "app kitni dafa khuli" to Ask.Count(Thing.APP_OPENS)
        )
    }

    @Test fun `little words anywhere never change what is asked`() {
        val fillers = listOf("ji", "zara", "bhai", "yaar", "please", "to", "na", "acha", "hudhud", "sir", "jee", "bhi")
        val rnd = java.util.Random(313)
        val wrong = ArrayList<String>()
        for ((s, expected) in steady) repeat(12) {
            val words = s.split(' ').toMutableList()
            repeat(1 + rnd.nextInt(3)) { words.add(rnd.nextInt(words.size + 1), fillers[rnd.nextInt(fillers.size)]) }
            val said = words.joinToString(" ")
            val got = ask(said)
            if (got != expected) wrong += "\"$said\" -> $got (expected $expected)"
        }
        assertEquals(wrong.distinct().joinToString("\n"), 0, wrong.size)
    }

    @Test fun `the words in any order mean the same`() {
        val rnd = java.util.Random(13)
        val wrong = ArrayList<String>()
        for ((s, expected) in steady) repeat(8) {
            val words = s.split(' ').toMutableList()
            // A name keeps its own two words together; the rest go anywhere.
            val name = listOf("Abid Ali").firstOrNull { s.contains(it) }
            val parts = if (name != null) (s.replace(name, "\u0000").split(' ').map { if (it == "\u0000") name else it }) else words
            val shuffled = parts.shuffled(rnd).joinToString(" ")
            val got = ask(shuffled)
            if (got != expected) wrong += "\"$shuffled\" -> $got (expected $expected)"
        }
        assertEquals(wrong.distinct().joinToString("\n"), 0, wrong.size)
    }

    @Test fun `a word a letter off is still understood`() {
        assertEquals(Ask.Count(Thing.INVOICES, Window.TODAY), ask("aaj kitni invoise bani"))
        assertEquals(Ask.Count(Thing.CALCULATIONS, Window.TODAY), ask("aaj calcuator kitni dafa use hua"))
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), ask("Abid Ali ka numbr"))
        assertEquals(Ask.Status(StatusOf.CHEQUES), ask("kitne cheques pendng hain"))
    }

    // ------------------------------------------------------------------------------- the chips' own words, carried on

    /** After a customer's answer, a bare question is about him: the chips' words typed or said, with no "uska". */
    private val him = Ask.Parties(listOf(2L))

    @Test fun `his number asked bare after his answer, in all nine languages`() = all(
        Ask.Person(listOf(2L), Facet.PHONE), listOf(
            "aur number?", "number", "phone number", "نمبر؟", "فون نمبر", "फ़ोन नंबर", "ফোন নম্বর", "رقم الهاتف", "شماره تلفن",
            "Nomor telepon", "aur iska number bhi"
        ), before = him
    )

    @Test fun `his last payment asked bare after his answer`() = all(
        Ask.Person(listOf(2L), Facet.LAST_PAYMENT), listOf(
            "aakhri adaigi kab hui", "last payment", "آخری ادائیگی", "آخري ادائيگي", "आखिरी भुगतान", "শেষ পরিশোধ", "آخر دفعة",
            "آخرین پرداخت", "Pembayaran terakhir"
        ), before = him
    )

    @Test fun `his credit limit asked bare, the udhaar in it not the shop's figure`() = all(
        Ask.Person(listOf(2L), Facet.LIMIT), listOf(
            "udhaar ki hadd", "credit limit", "ادھار کی حد", "اڌار جي حد", "उधार की सीमा", "বাকির সীমা", "حد الائتمان", "سقف نسیه",
            "Batas utang"
        ), before = him
    )

    @Test fun `when he will pay, asked bare after his answer`() = all(
        Ask.WhenPays(listOf(2L)), listOf(
            "kab dega", "کب دے گا؟", "ڪڏهن ڏيندو؟", "कब देगा?", "কবে দেবে?", "متى سيدفع؟", "کی می‌پردازد؟", "when will he pay"
        ), before = him
    )

    @Test fun `his balance asked bare after another of his facts`() = all(
        Ask.Parties(listOf(2L)), listOf("balance", "hisaab", "بیلنس", "بيلنس", "बैलेंस", "ব্যালেন্স", "الرصيد", "مانده", "Saldo"),
        before = Ask.Person(listOf(2L), Facet.PHONE)
    )

    @Test fun `his promise, entries and details asked bare`() {
        assertEquals(Ask.Person(listOf(2L), Facet.PROMISE), ask("waade ka din", him))
        assertEquals(Ask.Person(listOf(2L), Facet.PROMISE), ask("يوم الوعد", him))
        assertEquals(Ask.Person(listOf(2L), Facet.DETAILS), ask("tafseel", him))
        assertEquals(Ask.Person(listOf(2L), Facet.DETAILS), ask("বিস্তারিত", him))
        assertEquals(Ask.Person(listOf(2L), Facet.ENTRIES), ask("entries", him))
        assertEquals(Ask.Person(listOf(2L), Facet.ENTRIES), ask("اندراجات", him))
        assertEquals(Ask.Person(listOf(2L), Facet.SINCE), ask("pehli entry", him))
        assertEquals(Ask.Person(listOf(2L), Facet.LAST_ENTRY), ask("Catatan terakhir", him))
    }

    /** After a product's answer, a bare question is about it. */
    private val urea = Ask.Goods(listOf(101L), GoodsFacet.PRICE)

    @Test fun `a product's stock asked bare after its price, in all nine languages`() = all(
        Ask.Goods(listOf(101L), GoodsFacet.STOCK), listOf(
            "stock?", "in stock", "kitna stock hai", "اسٹاک میں", "اسٽاڪ ۾", "स्टॉक में", "স্টকে আছে", "في المخزون", "موجودی", "Stok"
        ), before = urea
    )

    @Test fun `who bought it, asked bare after its price`() = all(
        Ask.Goods(listOf(101L), GoodsFacet.BUYERS), listOf(
            "kis ne khareeda", "who bought it", "کس نے خریدا", "ڪنهن ورتو", "ڪنهن خريد ڪيو", "किसने खरीदा", "কে কিনেছে",
            "من اشتراه", "چه کسی خریده است؟", "Siapa yang membeli"
        ), before = urea
    )

    @Test fun `its cheaper supplier and last delivery asked bare`() {
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.SUPPLIERS), ask("sab se sasta kis ka hai", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.SUPPLIERS), ask("سپلائرن جا ريٽ", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.SUPPLIERS), ask("أسعار المورّدين", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.LAST_IN), ask("aakhri baar kab aaya", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.LAST_IN), ask("آخر توريد", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.LAST_IN), ask("آخرین ورود", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.COST), ask("ক্রয়মূল্য", urea))
        assertEquals(Ask.Goods(listOf(101L), GoodsFacet.OVERVIEW), ask("التفاصيل", urea))
    }

    @Test fun `a figure word after a customer or a product stays the shop's figure`() {
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("aaj ki sale", him))
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("aaj ki sale", urea))
        assertEquals(Ask.Figure(Kind.SALES_MONTH), ask("is mahine kitna bika", urea))
    }

    // ------------------------------------------------------------------------------- words that differ by language

    @Test fun `raqam is money in Urdu and Sindhi and a telephone number in Arabic`() {
        assertEquals(Ask.Collected(Window.TODAY), ask("آج کتنی رقم آئی"))
        assertEquals(Ask.Collected(Window.TODAY), ask("اڄ ڪيتري رقم آئي"))
        assertEquals(Ask.Parties(listOf(2L)), ask("بشیر کی رقم کتنی ہے"))
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), ask("رقم بشير"))
        assertEquals(Ask.Shop(ShopFacet.PHONE), ask("رقم المتجر"))
        needs(listOf("رقم عميل"))
    }

    @Test fun `Arabic's joined my and your`() {
        assertEquals(Ask.About(AboutTopic.MAKER), ask("من صنعك؟"))
        assertEquals(Ask.About(AboutTopic.PRIVACY), ask("هل بياناتي آمنة؟"))
        assertEquals(Ask.Status(StatusOf.BOOK), ask("دفتري"))
        assertEquals(Ask.Chat(Talk.WHO_ARE_YOU), ask("من أنت"))
    }

    @Test fun `Persian's my after its noun is not Arabic's who`() {
        assertEquals(Ask.Status(StatusOf.BOOK), ask("دفتر من"))
        assertEquals(Ask.About(AboutTopic.OWNER_NAME), ask("اسم من چیست"))
        assertEquals(Ask.About(AboutTopic.MAKER), ask("چه کسی تو را ساخت؟"))
        assertEquals(Ask.About(AboutTopic.PRIVACY), ask("آیا اطلاعات من امن است؟"))
    }

    @Test fun `a name saved in Roman letters is found when asked in Urdu or Arabic script`() {
        assertEquals(Ask.Parties(listOf(2L)), ask("بشیر کا حساب"))
        assertEquals(Ask.Parties(listOf(2L)), ask("بشير"))
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), ask("بشیر احمد کا نمبر"))
        // Only vowels may be put in: "Bushra" is not "بشیر".
        val withBushra = names + Hudhud.Name(8, "Bushra Bibi")
        assertEquals(Ask.Parties(listOf(2L)), Hudhud.understand("بشیر کا حساب", withBushra, topics, goods = goods))
    }

    @Test fun `somebody's number with no name asks whose`() {
        listOf("kisi gahak ka number", "supplier ka number", "mere supplier ka number", "کسی گاہک کا نمبر", "किसी ग्राहक का नंबर",
            "কোনো গ্রাহকের নম্বর", "شماره یک مشتری", "Nomor pelanggan", "A customer's number")
            .let { needs(it) }
        assertEquals(Ask.Shop(ShopFacet.PHONE), ask("dukaan ka number"))
        needs(listOf("udhaar ki hadd"))
        assertEquals(Ask.DidYouMean(listOf(Ask.Status(StatusOf.BOOK), Ask.NeedName(Facet.LAST_ENTRY))), ask("aakhri entry"))
    }

    @Test fun `money that came in, in all nine languages`() = all(
        Ask.Collected(Window.TODAY), listOf(
            "aaj kitne paise aaye", "money in today", "Aaj kitni raqam aayi", "آج کتنے پیسے آئے", "आज कितनी रकम आई", "আজ কত টাকা এল",
            "كم تحصّل اليوم", "امروز چقدر پول آمد", "Uang masuk hari ini"
        )
    )

    @Test fun `nothing he knows stays unknown`() {
        listOf("aaj mausam kaisa hai", "cricket ka score", "zzz qqq").forEach { assertEquals(it, Ask.Unknown, ask(it)) }
    }
}
