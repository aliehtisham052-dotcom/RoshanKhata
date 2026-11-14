package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudStory.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's own history and Hudhud's own story are answered only to a question that asks for them (14 Nov 2026, the owner: "app
 * kab bani, kab publish hui, Hudhud kab add hua" and "Hudhud apni aap-beeti sunaye"): every word of the sentence must be the
 * reader's own or a little word that asks nothing, in the nine languages — and a version, a maker, a customer's story or
 * "Roshan ka khata kab bana" (somebody called Roshan) stays what it was.
 */
class HudhudStoryTest {

    private fun read(s: String, language: String? = null, known: Set<String> = emptySet()) =
        HudhudStory.read(s, language) { w -> w in known }

    private fun all(topic: Topic, vararg sentences: String) {
        for (s in sentences) assertEquals("\"$s\"", topic, read(s))
    }

    @Test fun `his own story is asked in nine languages`() = all(
        Topic.HOOPOE,
        "Hudhud apni aap beeti sunao", "Hudhud ki aap beeti", "Hudhud ki kahani sunao", "apni kahani sunao", "tell me your story",
        "Hudhud tell your story", "tell us the story of the hoopoe", "hoopoe ki kahani", "Hudhud ka qissa sunao", "Hudhud ki aapbeeti",
        "Hudhud ka waqia batao", "Hudhud ki puri kahani", "Sulaiman alaihissalam ka Hudhud ki kahani", "ceritakan kisahmu",
        "ceritakan kisah Hudhud", "ceritakan kisahmu hudhud", "Hudhud ceritakan kisahmu", "Hudhud apni kahani sunaye",
        "ہدہد اپنی آپ بیتی سناؤ", "ہدہد کی کہانی سناؤ", "اپنی کہانی سناؤ ہدہد", "ہدہد کا قصہ", "ہدہد کی آپ بیتی",
        "احكي لي قصتك يا هدهد", "قصة الهدهد", "اخبرني قصتك هدهد", "هدهد قصتك",
        "داستان هدهد را بگو", "داستان خودت را بگو هدهد", "هدهد داستانت را بگو",
        "هدهد پنهنجي ڪهاڻي ٻڌاءِ", "هدهد جو قصو",
        "हुदहुद अपनी कहानी सुनाओ", "हुदहुद की आपबीती", "हुदहुद का किस्सा सुनाओ", "अपनी कहानी सुनाओ हुदहुद",
        "হুদহুদ তোমার গল্প বলো", "হুদহুদের গল্প", "হুদহুদ নিজের কাহিনী বলো"
    )

    @Test fun `the app's own history is asked in nine languages`() = all(
        Topic.JOURNEY,
        "app ki history batao", "app ki tareekh", "app ki tareekh batao", "ye app kab bani", "app kab banayi gayi", "app ka safar",
        "when was the app made", "history of the app", "when was this app built", "app history", "app timeline", "how old is the app",
        "Roshan Khata kab bana", "Roshan Khata ki history", "sejarah aplikasi", "kapan aplikasi dibuat",
        "تاریخ ایپ", "ایپ کی تاریخ بتاؤ", "ایپ کب بنی", "ایپ کی تاریخ", "روشن کھاتہ کب بنا",
        "تاريخ التطبيق", "متى صنع التطبيق", "متى بني التطبيق", "تاریخچه برنامه", "ایپ جي تاريخ", "ايپ ڪڏهن ٺهي",
        "ऐप का इतिहास", "ऐप कब बनी", "ऐप कब बना", "এই অ্যাপ কবে তৈরি হয়েছে", "অ্যাপের ইতিহাস", "অ্যাপের যাত্রা", "ऐप का सफर", "ऐप की यात्रा"
    )

    @Test fun `the day it was published is asked in nine languages and never as launched`() = all(
        Topic.PUBLISHED,
        "app kab publish hui", "app Play Store par kab aayi", "when was the app published", "when did the app go live",
        "when was the app released", "when was the app launched", "app kab launch hui", "app kab release hui",
        "kapan aplikasi dipublikasikan", "kapan aplikasi rilis", "kapan aplikasi tayang di Play Store",
        "ایپ کب پبلش ہوئی", "ایپ پلے اسٹور پر کب آئی", "ایپ کب لانچ ہوئی", "متى نشر التطبيق", "متى صدر التطبيق", "متى أطلق التطبيق",
        "ऐप कब पब्लिश हुई", "ऐप कब लॉन्च हुई", "অ্যাপ কবে প্রকাশিত হয়েছে", "অ্যাপ কবে প্রকাশ হয়েছে", "অ্যাপ কবে প্রকাশিত হলো?"
    )

    @Test fun `the day Hudhud was added is asked in nine languages`() = all(
        Topic.HUDHUD,
        "Hudhud kab add hua", "Hudhud kab aaya", "Hudhud kab bana", "Hudhud kab banaya gaya", "Hudhud ki umar kya hai",
        "Hudhud kitna purana hai", "when was Hudhud added", "when was Hudhud made", "when did Hudhud join", "how old is Hudhud",
        "Hudhud history", "history of Hudhud", "kapan Hudhud ditambahkan", "usia Hudhud",
        "ہدہد کب شامل ہوا", "ہدہد کب آیا", "ہدہد کی عمر کیا ہے", "ہدہد کب بنا", "متى أضيف الهدهد", "متى صنع الهدهد",
        "هدهد ڪڏهن شامل ٿيو", "हुदहुद कब जुड़ा", "हुदहुद कब बना", "हुदहुद की उम्र क्या है", "হুদহুদ কবে যোগ হয়েছে", "হুদহুদের বয়স কত", "হুদহুদ কবে যোগ হলো?"
    )

    @Test fun `when the work began is asked`() {
        assertEquals(Topic.STARTED, read("app pe kaam kab shuru hua"))
        assertEquals(Topic.STARTED, read("when did the work on the app start"))
        assertEquals(Topic.STARTED, read("kapan aplikasi mulai dibuat"))
        assertEquals(Topic.STARTED, read("Roshan Khata kab shuru hua"))
        assertEquals(Topic.STARTED, read("app ki shuruaat kab hui"))
    }

    @Test fun `your is you and not the app in Urdu script, and the sentence says which`() {
        // "اپ" is "you" in Urdu and "app" in English, written alike once the marks are taken off: a "when" and no more is the journey.
        assertEquals(Topic.JOURNEY, read("اپ کب بنی"))
        assertEquals(Topic.HOOPOE, read("آپ کی کہانی"))
        assertEquals(Topic.HOOPOE, read("اپنی کہانی سناؤ"))
        assertEquals(Topic.HUDHUD, read("آپ کی عمر کیا ہے"))
        assertEquals(Topic.HUDHUD, read("आप कब जुड़े"))
    }

    @Test fun `Persian's ki and to are read only in a Persian app`() {
        assertEquals(Topic.JOURNEY, read("برنامه کی ساخته شد", language = "fa"))
        assertEquals(Topic.HUDHUD, read("هدهد کی اضافه شد", language = "fa"))
        assertEquals(Topic.PUBLISHED, read("برنامه کی منتشر شد؟", language = "fa"))
        // In an Urdu app "کی" is "of": no "when" is asked.
        assertNull(read("برنامہ کی", language = "ur"))
        assertNull(read("برنامه کی ساخته شد", language = "ur"))
    }

    @Test fun `a version, a maker, what the app is or a name's story keep their own answers`() {
        for (s in listOf(
            "Roshan Khata kya hai", "ye app kya hai", "app ke baare mein batao", "app ka version kya hai", "kaunsa version hai",
            "which version", "versi aplikasi", "kis ne banayi yeh app", "who made this app", "app kisne banayi", "app kis ne banai hai",
            "Hudhud ka naam kyun hai", "tumhara naam Hudhud kyun hai", "why are you called Hudhud", "ye naam kyun rakha",
            "اپ کس نے بنائی", "من صنع التطبيق", "التطبيق ما هو", "version of the app", "app ka size",
            "ایپ کا ورژن", "ऐप का वर्ज़न", "অ্যাপের ভার্সন"
        )) assertNull("\"$s\"", read(s))
    }

    @Test fun `a customer's story or a question about the book is never a history`() {
        for (s in listOf(
            "Ali ki kahani", "Ali ki kahani sunao", "Ali kab aaya", "Ali ka khata kab bana", "Ali ne kab diya", "Ali ka hisaab kab shuru hua",
            "app mein Ali kab aaya", "Hudhud Ali ka hisaab batao", "Hudhud aaj ki sale", "Hudhud mera balance batao", "Hudhud ka number",
            "Hudhud ki sale kab hui", "kab bana khata", "mera khata kab bana", "kal ka hisaab", "aaj kab publish hua invoice",
            "invoice kab bana", "bill kab bana", "cheque kab aaya", "stock kab aaya", "maal kab aaya", "sale kab hui",
            "Hudhud Hudhud Hudhud", "Hudhud kaise ho", "kya app kaam kar rahi hai"
        )) assertNull("\"$s\"", read(s))
    }

    @Test fun `Roshan is a customer when the book holds one`() {
        assertEquals(Topic.JOURNEY, read("Roshan Khata kab bana"))
        assertNull(read("Roshan Khata kab bana", known = setOf("roshan")))
        assertNull(read("Roshan ka khata kab bana", known = setOf("roshan")))
        assertNull(read("Roshan ki kahani", known = setOf("roshan")))
    }

    @Test fun `a name found in the book makes the sentence the customer's`() {
        assertNull(read("Bashir ki kahani sunao", known = setOf("bashir")))
        assertNull(read("Hudhud Bashir ki kahani", known = setOf("bashir")))
        assertNull(read("kab aaya Bashir", known = setOf("bashir")))
    }

    @Test fun `a long sentence is never the reader's`() {
        assertNull(read("Hudhud apni kahani sunao aur phir mujhe bata do ke kal kitni sale hui thi aur kitna udhaar baaki hai"))
        assertNull(read("app kab banayi gayi thi aur kis ne banayi thi aur kis zaban mein banayi gayi thi aur kitne log istemal karte hain"))
    }

    @Test fun `bare words and thanks are no question`() {
        for (s in listOf("kahani", "story", "history", "kab", "when", "app", "Hudhud", "tareekh", "ہدہد", "app ka", "")) {
            assertNull("\"$s\"", read(s))
        }
    }

    @Test fun `the dates are the project's own, in order, and the first work on Hudhud is the latest`() {
        fun n(d: HistoryDay) = d.year * 10000 + d.month * 100 + d.day
        assertTrue(n(AppHistory.STARTED) < n(AppHistory.PLAY_ACCESS))
        assertTrue(n(AppHistory.PLAY_ACCESS) < n(AppHistory.PUBLIC_BY))
        assertTrue(n(AppHistory.PUBLIC_BY) < n(AppHistory.HUDHUD))
        for (d in listOf(AppHistory.STARTED, AppHistory.PLAY_ACCESS, AppHistory.PUBLIC_BY, AppHistory.HUDHUD)) {
            assertTrue(d.month in 1..12 && d.day in 1..31 && d.year == 2026)
        }
        assertEquals(13, AppHistory.STARTED.day)
        assertEquals(16, AppHistory.PLAY_ACCESS.day)
        assertEquals(28, AppHistory.PUBLIC_BY.day)
    }

    @Test fun `the story shows the hoopoe's own verse and the outcome, each with its Arabic`() {
        assertEquals(listOf("27:26", "27:44"), HudhudStory.VERSES.map { it.ref })
        for (v in HudhudStory.VERSES) {
            assertNotNull(v.surah)
            assertTrue("${v.ref} has its Arabic", v.arabic.any { it in '؀'..'ۿ' })
        }
    }

    @Test fun `every topic is reachable from some sentence`() {
        val seen = setOf(
            read("app pe kaam kab shuru hua"), read("app kab publish hui"), read("Hudhud kab add hua"),
            read("app ki history"), read("Hudhud apni kahani sunao")
        )
        assertEquals(Topic.values().toSet(), seen)
    }
}
