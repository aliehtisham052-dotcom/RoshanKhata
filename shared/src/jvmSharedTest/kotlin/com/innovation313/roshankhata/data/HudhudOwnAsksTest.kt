package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Five asks that no test named until 16 Nov 2026 (audit T15): his acts ([Hudhud.Ask.Act]), how he speaks ([Hudhud.Ask.Voice]),
 * the Qarz-e-Hasna ([Hudhud.Ask.Qarz]), the automations ([Hudhud.Ask.Automations]) and the name the owner asks to be called by
 * ([Hudhud.Ask.CallMe]) — each in the languages its words exist in, and each with the sentences that must NOT become it.
 * Through the whole pipeline with the shipped sense (lesson 145); invented names, and a book whose names could be taken for
 * the words (Bashir Ahmad for "mera naam Bashir hai", Tariq Butt for "Tariq Butt ko awaaz do").
 *
 * CallMe: only which sentences become it is held here — its card is the app's and is being changed elsewhere.
 */
class HudhudOwnAsksTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Note House").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun holds(expected: Hudhud.Ask, cases: List<Pair<String, String>>) {
        for ((lang, said) in cases) assertEquals("[$lang] $said", expected, read(lang, said))
    }

    private fun never(what: String, test: (Hudhud.Ask) -> Boolean, cases: List<Pair<String, String>>) {
        for ((lang, said) in cases) {
            val a = read(lang, said)
            assertTrue("[$lang] $said read as $a, never $what", !test(a))
        }
    }

    // ------------------------------------------------------------------------------------------------ his acts

    @Test fun `he flies when asked in eight languages`() = holds(Hudhud.Ask.Act(Hudhud.Move.FLY), listOf(
        "en" to "fly around the screen", "ur" to "ذرا اڑ کر دکھاؤ", "hi" to "ज़रा उड़ कर दिखाओ", "bn" to "একবার উড়ে দেখাও তো",
        "ar" to "حلق يا هدهد", "fa" to "پر بزن", "sd" to "اڏام هدهد"
    ))

    @Test fun `he dances when asked in nine languages`() = holds(Hudhud.Ask.Act(Hudhud.Move.DANCE), listOf(
        "en" to "do a little dance please", "ur" to "ایک ڈانس ہو جائے", "ur-Latn" to "ek dance ho jaye", "hi" to "एक डांस हो जाए",
        "bn" to "একটু নেচে দেখাও প্লিজ", "ar" to "ارقص شوية لو سمحت", "fa" to "لطفا کمی برقص", "sd" to "نچ هدهد", "in" to "coba menari"
    ))

    @Test fun `he hops when asked`() = holds(Hudhud.Ask.Act(Hudhud.Move.JUMP), listOf(
        "en" to "do one hop", "hi" to "हुदहुद एक बार उछलो", "bn" to "একবার লাফাও তো", "ar" to "يلا نط وورني",
        "fa" to "بپر ببینم هدهد جان", "sd" to "ادا هڪ ٽپ ته ڏي", "in" to "lompat sekali"
    ))

    @Test fun `a question of the book is never an act`() = never("an act", { it is Hudhud.Ask.Act }, listOf(
        "ur-Latn" to "Ali Raza ka hisaab dikhao", "ur-Latn" to "Urea kitni bik gayi", "en" to "how much did Tariq Butt pay",
        "ur-Latn" to "aaj ki sale dikhao", "en" to "show me today's sales"
    ))

    // ------------------------------------------------------------------------------------------------ how he speaks

    @Test fun `say it again in nine languages`() = holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.AGAIN), listOf(
        "ur-Latn" to "phir se batao", "en" to "say it again", "ur" to "دوبارہ بتاؤ", "hi" to "फिर से बताओ", "bn" to "আবার বলো",
        "ar" to "مرة أخرى", "fa" to "دوباره بگو", "sd" to "ٻيهر ٻڌايو", "in" to "ulangi"
    ))

    @Test fun `slower in nine languages`() = holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.SLOWER), listOf(
        "ur-Latn" to "ahista bolo", "en" to "speak slowly", "ur" to "آہستہ بولو", "hi" to "धीरे बोलो", "bn" to "আস্তে বলো",
        "ar" to "تكلم ببطء", "fa" to "آهسته بگو", "in" to "pelan pelan"
    ))

    @Test fun `be quiet in nine languages`() = holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.QUIET), listOf(
        "ur-Latn" to "chup ho jao", "en" to "be quiet", "ur" to "چپ ہو جاؤ", "hi" to "चुप हो जाओ", "bn" to "চুপ", "ar" to "اسكت",
        "fa" to "ساکت", "sd" to "خاموش ٿي وڃو", "in" to "diam"
    ))

    @Test fun `his voice off in nine languages`() = holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.OFF), listOf(
        "ur-Latn" to "awaaz band karo", "en" to "mute", "ur" to "آواز بند کرو", "hi" to "आवाज़ बंद करो", "bn" to "আওয়াজ বন্ধ করো",
        "ar" to "اكتم الصوت", "sd" to "آواز بند ڪريو", "in" to "matikan suara"
    ))

    @Test fun `faster, on and louder`() {
        holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.FASTER), listOf("ur-Latn" to "tez bolo", "en" to "speak faster"))
        holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.ON), listOf("ur-Latn" to "awaaz chalu karo", "en" to "unmute"))
        // "Awaaz tez karo" is louder (the phone's own buttons), never faster.
        holds(Hudhud.Ask.Voice(Hudhud.VoiceChange.VOLUME), listOf("ur-Latn" to "awaaz tez karo"))
    }

    @Test fun `a question with a speaking word is still the question`() = never("a change of his voice", { it is Hudhud.Ask.Voice }, listOf(
        "ur-Latn" to "phir se Ali Raza ka hisaab batao", "ur-Latn" to "aaj ki sale dobara batao", "en" to "stop Bashir Ahmad's credit",
        "ur-Latn" to "Tariq Butt ko awaaz do"
    ))

    // ------------------------------------------------------------------------------------------------ Qarz-e-Hasna

    @Test fun `the Qarz-e-Hasna in nine languages`() = holds(Hudhud.Ask.Qarz, listOf(
        "ur-Latn" to "qarz e hasna kitna hai", "ur" to "قرض حسنہ کتنا ہے", "en" to "how much interest free loan is out",
        "hi" to "क़र्ज़ ए हसना कितना है", "bn" to "করজে হাসানা কত", "ar" to "كم قرض حسن عندي", "fa" to "قرض‌الحسنه چقدر است",
        "sd" to "قرض حسنو ڪيترو آهي", "in" to "berapa pinjaman kebajikan"
    ))

    @Test fun `a plain loan of somebody is his account, not the Qarz-e-Hasna`() = never("the Qarz-e-Hasna", { it == Hudhud.Ask.Qarz }, listOf(
        "ur-Latn" to "Ali Raza ka qarz kitna hai", "en" to "how much loan did Bashir Ahmad take"
    ))

    // ------------------------------------------------------------------------------------------------ the automations

    @Test fun `the automations in nine languages`() = holds(Hudhud.Ask.Automations, listOf(
        "ur-Latn" to "kaun si automation on hai", "en" to "which automations are on", "ur" to "آٹومیشن کون سی چل رہی ہیں",
        "hi" to "कौन सा ऑटोमेशन चालू है", "bn" to "কোন অটোমেশন চালু আছে", "ar" to "ما هي الأتمتة المفعلة", "fa" to "کدام خودکار روشن است",
        "sd" to "ڪهڙي آٽوميشن هلي رهي آهي", "in" to "otomasi mana yang aktif"
    ))

    @Test fun `a figure is never the automations`() = never("the automations", { it == Hudhud.Ask.Automations }, listOf(
        "ur-Latn" to "aaj ki sale kitni hui", "en" to "how much does Ali Raza owe", "ur-Latn" to "Urea ka rate"
    ))

    // ------------------------------------------------------------------------------------------------ what to call the owner

    @Test fun `my name is in nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "mera naam Bashir hai", "ur-Latn" to "mujhe Kamran bulao", "en" to "my name is Tariq", "en" to "call me Ali",
            "ur" to "میرا نام بشیر ہے", "hi" to "मेरा नाम बशीर है", "bn" to "আমার নাম বশির", "ar" to "اسمي بشير", "fa" to "اسم من بشیر است",
            "in" to "nama saya Bashir", "sd" to "منهنجو نالو بشير آهي"
        )) {
            val a = read(lang, said)
            assertTrue("[$lang] $said read as $a", a is Hudhud.Ask.CallMe)
        }
    }

    @Test fun `asking the name, a question mark, somebody else's name or a calling by another are no CallMe`() =
        never("a name to be called by", { it is Hudhud.Ask.CallMe }, listOf(
            "ur-Latn" to "mera naam kya hai", "ur-Latn" to "mera naam bata do", "en" to "do you know my name", "en" to "what is my name",
            "en" to "I am tired", "ur-Latn" to "mera naam Bashir hai?", "ur-Latn" to "Bashir Ahmad ka naam", "ur-Latn" to "mujhe Ali ne bulaya"
        ))
}
