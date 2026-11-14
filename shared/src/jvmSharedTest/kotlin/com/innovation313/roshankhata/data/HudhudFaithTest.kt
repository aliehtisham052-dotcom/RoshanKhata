package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudFaith.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tawheed, hamd and the Prophet's ﷺ risalat are answered only to a question that asks for them (14 Nov 2026, the owner: "har
 * sawal ke jawab mein nahi, sirf us ke specific sawal par"): every word of the sentence must be the faith's own or a little
 * word that asks nothing, in the nine languages — and a customer called Allah Ditta or Muhammad Ali, a thanks, a goodbye or
 * an "Alhamdulillah" said in passing is never turned into a lecture.
 */
class HudhudFaithTest {

    private fun read(s: String, known: Set<String> = emptySet()) = HudhudFaith.read(s) { w -> w in known }

    private fun all(topic: Topic, vararg sentences: String) {
        for (s in sentences) assertEquals("\"$s\"", topic, read(s))
    }

    @Test fun `Tawheed is asked in nine languages`() = all(
        Topic.TAWHEED,
        "Who is Allah?", "Is Allah one?", "What is Tawheed", "Tell me about the oneness of Allah", "Is there only one God",
        "Does Allah have a partner",
        "Allah ek hai", "Allah kaun hai", "tawheed kya hai", "Allah ki wahdaniyat batao", "khuda ek hai ya do", "Allah ka koi shareek hai",
        "kitne khuda hain", "اللہ ایک ہے", "اللہ کون ہے", "توحید کیا ہے", "اللہ کی وحدانیت بتاؤ",
        "من هو الله", "الله واحد", "ما هو التوحيد", "الله کیست", "توحید چیست", "خدا یکی است",
        "الله ڪير آهي", "توحيد ڇا آهي", "الله هڪ آهي", "अल्लाह कौन है", "तौहीद क्या है", "अल्लाह एक है",
        "আল্লাহ কে", "তাওহীদ কী", "আল্লাহ এক", "Siapa Allah", "Apa itu tauhid", "Allah Maha Esa"
    )

    @Test fun `hamd and sana are asked in nine languages`() = all(
        Topic.HAMD,
        "hamd o sana", "Hamd and sana", "Allah ki hamd", "tasbeeh kya hai", "Alhamdulillah ka matlab kya hai",
        "har cheez Allah ki tasbeeh karti hai", "shajar o hajar Allah ki tasbeeh karte hain",
        "what is the meaning of Alhamdulillah", "حمد و ثنا", "الحمدللہ کا مطلب کیا ہے", "ما معنى الحمد لله", "الحمد والثناء",
        "حمد و ثنا چیست", "حمد ۽ ثنا", "हम्द और सना क्या है", "হামদ ও সানা", "আলহামদুলিল্লাহ মানে কী", "Apa arti alhamdulillah", "Hamd dan sana"
    )

    @Test fun `the Prophet's risalat is asked in nine languages`() = all(
        Topic.RISALAT,
        "Who is Prophet Muhammad", "Nabi kareem kaun hain", "Muhammad ﷺ kaun hain", "Rasool Allah kaun hain", "kalima kya hai",
        "nabuwwat kya hai", "darood sharif ka matlab", "khatam e nabuwwat kya hai", "Prophet Muhammad ﷺ",
        "نبی کریم کون ہیں", "حضرت محمد ﷺ کون ہیں", "ختم نبوت کیا ہے", "کلمہ کیا ہے", "درود شریف کیا ہے",
        "من هو النبي محمد", "النبي محمد ﷺ", "پیامبر محمد کیست", "نبي محمد ڪير آهي", "नबी मुहम्मद कौन हैं", "নবী মুহাম্মদ কে", "Siapa Nabi Muhammad"
    )

    @Test fun `a customer called Allah Ditta or Muhammad Ali is a customer`() {
        for (s in listOf(
            "Allah Ditta ka hisaab", "Allah Ditta kaun hai", "Allah Bakhsh ka number", "Muhammad Ali ka number", "Rasool Bakhsh kaun hai",
            "Nabi Bakhsh ne kitne paise dene hain", "Allah Rakha ki entry"
        )) assertNull("\"$s\"", read(s, known = setOf("muhammad", "nabi", "rasool")))
    }

    @Test fun `a bare Muhammad or Nabi is the Prophet's only when the book holds nobody called so`() {
        assertEquals(Topic.RISALAT, read("Muhammad kaun hai"))
        assertNull(read("Muhammad kaun hai", known = setOf("muhammad")))
        assertEquals(Topic.RISALAT, read("Nabi kaun hai"))
        assertNull(read("Nabi kaun hai", known = setOf("nabi")))
        // With his honour or "kareem" it is the Prophet's whoever the book holds.
        assertEquals(Topic.RISALAT, read("Nabi kareem kaun hain", known = setOf("nabi")))
        assertEquals(Topic.RISALAT, read("Muhammad ﷺ kaun hain", known = setOf("muhammad")))
        assertEquals(Topic.RISALAT, read("Rasool Allah kaun hain", known = setOf("rasool")))
    }

    @Test fun `a thanks, a goodbye or a praise said in passing is no question`() {
        for (s in listOf(
            "Alhamdulillah", "Allah", "Allah hafiz", "Allah ka shukar hai", "Alhamdulillah aaj achi sale hui", "SubhanAllah",
            "الحمدللہ", "اللہ", "الله", "Muhammad", "Nabi", "khatam", "ختم", "tasbeeh ke daane ka rate"
        )) assertNull("\"$s\"", read(s))
    }

    @Test fun `a sentence about the book or a long one is never the faith's`() {
        for (s in listOf(
            "Allah kaun hai aur mera balance kitna hai", "aaj ki sale batao", "hamd ka hisaab", "tawheed ki bikri",
            "Allah ek hai 1250 rupees", "who is Allah and what is my profit this month",
            "Allah ek hai ya do ya teen ya char ya paanch ya chhe ya saat ya aath ya nau"
        )) assertNull("\"$s\"", read(s))
    }

    @Test fun `every topic has its verses with their Arabic and reference`() {
        for (t in Topic.values()) {
            val verses = HudhudFaith.versesOf(t)
            assertTrue("$t has verses", verses.isNotEmpty())
            for (v in verses) {
                assertTrue("${v.ref} has its Arabic", v.arabic.isNotBlank() && v.arabic.any { it in '؀'..'ۿ' })
                assertTrue("${v.ref} names its surah", v.surah.isNotBlank())
            }
        }
        assertEquals(listOf("112:1-4", "21:22", "27:26"), HudhudFaith.versesOf(Topic.TAWHEED).map { it.ref })
        assertEquals(listOf("1:2", "17:44", "24:41", "11:88"), HudhudFaith.versesOf(Topic.HAMD).map { it.ref })
        assertEquals(listOf("", "33:40", "33:56"), HudhudFaith.versesOf(Topic.RISALAT).map { it.ref })
    }
}
