package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.HudhudLook.Request
import com.innovation313.roshankhata.data.HudhudLook.Setting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The look reader through the whole of Hudhud (14 Nov 2026): the rules, the learnt sense and the how-to pages, not only the strict
 * reader ([HudhudLookTest]). An order for the text size or the theme reaches the card as it was read; a customer whose own name holds
 * one of its words is still found by his name; and a question about the way, a refusal, a figure or a sentence that only holds one
 * of the words in another sense is never an order. Names are made up (lesson 80).
 */
class HudhudLookWayTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Malik Store"),
        Hudhud.Name(8, "আলী"), Hudhud.Name(10, "Imran Khan")
    )

    /** A book with customers whose own names hold the look words. */
    private val looked = names + listOf(
        Hudhud.Name(12, "Theme House"), Hudhud.Name(13, "Font Traders"), Hudhud.Name(14, "Dark Store"), Hudhud.Name(15, "Light Centre"),
        Hudhud.Name(16, "Roshan Gul"), Hudhud.Name(17, "Size Mart")
    )

    private val topics = listOf(
        Hudhud.Topic("faq_invoice", "Invoice انوائس bill", "Make an invoice for a customer and share it"),
        Hudhud.Topic("intro_app", "App ایپ application", "What the app is and how it began, how to use it and how old it is"),
        Hudhud.Topic("intro_staff", "Staff and salary سٹاف تنخواہ", "Salary, advances and attendance of the staff")
    )

    private fun read(s: String, who: List<Hudhud.Name> = names, before: Ask? = null) =
        Hudhud.understand(s, who, topics, null, before = before)

    private fun look(r: Request) = Ask.Look(r)

    private fun isLook(a: Ask) = a is Ask.Look

    private fun person(id: Long, facet: Hudhud.Facet) = Ask.Person(listOf(id), facet)

    private fun step(by: Int): Request = Request.SizeStep(by)
    private fun size(level: Int): Request = Request.SizeTo(level)
    private fun theme(mode: Int): Request = Request.ThemeTo(mode)
    private val showSize: Request = Request.Show(Setting.SIZE)
    private val showTheme: Request = Request.Show(Setting.THEME)

    /** Each sentence, in the book it is asked in, reaches the card as exactly the reading the strict reader gave it. */
    private fun all(expected: Request, vararg sentences: String, who: List<Hudhud.Name> = names, before: Ask? = null) {
        for (q in sentences) assertEquals("\"$q\"", look(expected), read(q, who, before))
    }

    private fun never(vararg sentences: String, who: List<Hudhud.Name> = names, before: Ask? = null) {
        for (q in sentences) {
            val a = read(q, who, before)
            assertTrue("\"$q\" -> $a", !isLook(a))
        }
    }

    // ------------------------------------------------------------------------------------------------ the nine languages

    @Test fun `English and Roman Urdu orders reach the card`() {
        all(step(1), "make the text bigger", "bigger text", "font bara karo", "font ko bara karo", "likhai bari karo")
        all(step(-1), "make the font smaller", "smaller text", "font chhota karo", "font ghatao")
        all(size(2), "text size large", "large text", "text size large karo", "font bara rakho")
        all(size(3), "text size largest", "biggest font", "font sab se bara")
        all(size(1), "text size normal", "font aam karo")
        all(size(0), "small text", "font chhota rakho")
        all(theme(1), "dark mode", "turn on dark mode", "dark mode lagao", "raat wala mode", "night mode")
        all(theme(0), "light mode", "turn off dark mode", "dark mode band karo", "theme roshan karo", "din wala theme")
        all(theme(2), "follow system theme", "phone jaisa theme", "theme phone ke mutabiq")
        all(showTheme, "theme", "what is the theme", "is dark mode on", "theme badlo", "theme kaisa hai")
        all(showSize, "text size", "font size", "what is the font size", "font ka size", "text size badlo")
    }

    @Test fun `Hindi and Urdu orders reach the card`() {
        all(step(1), "फॉन्ट बड़ा करो", "फॉन्ट और बड़ा करो", "فونٹ بڑا کریں", "فونٹ اور بڑا کریں", "لکھائی بڑی کریں")
        all(step(-1), "फॉन्ट छोटा करो", "فونٹ چھوٹا کریں")
        all(size(2), "अक्षरों का आकार बड़ा", "تحریر کا سائز بڑا")
        all(theme(1), "डार्क मोड", "डार्क मोड चालू करो", "ڈارک موڈ", "ڈارک موڈ آن کریں", "رات والا موڈ")
        all(theme(0), "लाइट थीम", "डार्क मोड बंद करो", "لائٹ موڈ", "ڈارک موڈ بند کریں")
        all(theme(2), "थीम फोन के अनुसार", "تھیم فون کے مطابق")
        all(showTheme, "थीम बदलो", "थीम क्या है", "تھیم بدلیں", "تھیم کیا ہے")
        all(showSize, "अक्षरों का आकार", "تحریر کا سائز")
    }

    @Test fun `Sindhi, Arabic, Farsi, Bengali and Indonesian orders reach the card`() {
        all(step(1), "فانٽ وڏو ڪريو", "كبّر الخط", "اجعل الخط أكبر", "فونت را بزرگتر کن", "ফন্ট বড় করো", "perbesar teks", "teks lebih besar")
        all(step(-1), "فانٽ ننڍو ڪريو", "صغّر الخط", "فونت را کوچکتر کن", "ফন্ট ছোট করো", "perkecil teks")
        all(size(2), "حجم النص كبير", "اندازهٔ متن بزرگ", "লেখার আকার বড়", "ukuran teks besar")
        all(theme(1), "اونداهي ٿيم", "الوضع الداكن", "حالت تاریک", "ডার্ক মোড", "mode gelap", "aktifkan mode gelap")
        all(theme(0), "روشن ٿيم", "الوضع الفاتح", "پوسته روشن", "লাইট থিম", "tema terang", "matikan mode gelap")
        all(theme(2), "ٿيم فون مطابق", "المظهر حسب الهاتف", "پوسته مطابق گوشی", "থিম ফোনের মতো", "tema ikuti ponsel")
        all(showTheme, "ٿيم", "ما هو المظهر", "پوسته", "থিম", "ubah tema", "apa tema ini")
        all(showSize, "لکت جي ماپ", "حجم الخط", "اندازه فونت", "লেখার আকার", "ukuran teks")
    }

    @Test fun `the owner's own two sentences are read as orders`() {
        // The two sentences of his own lists that name these settings (K0731, K0735); they were read as help pages before.
        assertEquals(look(step(1)), read("Font bara karo"))
        assertEquals(look(showTheme), read("Theme badlo"))
    }

    // ------------------------------------------------------------------------------------------------ his name, a calling and the book

    @Test fun `his name or a calling in front or behind is not in the way`() {
        all(step(1), "Hudhud font bara karo", "bhai jaan font bara karo", "font bara karo ji", "yaar font bara karo")
        all(theme(1), "Hudhud dark mode lagao", "bhai dark mode", "sir dark mode")
        all(showTheme, "Hudhud theme badlo", "ہدہد تھیم بدلیں")
    }

    @Test fun `the same orders are read when the book holds customers whose names hold the words`() {
        all(step(1), "font bara karo", "make the text bigger", who = looked)
        all(theme(1), "dark mode", "dark mode lagao", "night mode", who = looked)
        all(theme(0), "light theme", "theme roshan karo", who = looked)
        all(showTheme, "theme", "theme badlo", who = looked)
        all(showSize, "font size", "text size badlo", who = looked)
    }

    @Test fun `a customer whose own name holds a word is found by his name`() {
        assertEquals(Ask.Parties(listOf(12)), read("Theme House ka hisaab", who = looked))
        assertEquals(Ask.Parties(listOf(13)), read("Font Traders ka hisaab", who = looked))
        assertEquals(Ask.Parties(listOf(14)), read("Dark Store ka hisaab", who = looked))
        assertEquals(Ask.Parties(listOf(15)), read("Light Centre ka hisaab", who = looked))
        assertEquals(Ask.Parties(listOf(16)), read("Roshan Gul ka hisaab", who = looked))
        assertEquals(Ask.Parties(listOf(17)), read("Size Mart ka hisaab", who = looked))
        assertEquals(person(12, Hudhud.Facet.PHONE), read("Theme House ka number", who = looked))
        assertEquals(person(14, Hudhud.Facet.PHONE), read("Dark Store ka number", who = looked))
        never("Theme House", "Dark Store ka font bara karo", "Font Traders ka dark mode", who = looked)
    }

    // ------------------------------------------------------------------------------------------------ what is never an order

    @Test fun `a question about the way is a help page or nothing, never an order`() {
        never(
            "how to change font size", "how do I turn on dark mode", "font size kaise badlein", "theme kaise badlte hain",
            "how can I make the text bigger", "dark mode kaise lagaun", "फॉन्ट का आकार कैसे बदलें", "فونٹ کا سائز کیسے بدلیں",
            "كيف أغير حجم الخط", "bagaimana ubah tema"
        )
    }

    @Test fun `a refusal and a figure are never an order`() {
        never(
            "don't make the font bigger", "do not use dark mode", "font bara mat karo", "dark mode nahi chahiye", "no dark mode",
            "فونٹ بڑا نہ کریں", "تھیم مت بدلیں", "jangan ubah tema", "font size 20", "text size 150", "dark mode 2", "text 150%"
        )
    }

    @Test fun `a word that is something else in the shop is not an order`() {
        never(
            "payment mode", "mode of payment", "light bill", "light chali gayi", "volume bara karo", "awaaz bara karo", "pack size",
            "product size", "shoe size 9", "invoice ka font bara karo", "make the font bigger in the invoice", "Ali ka font bara karo",
            "Bashir dark mode"
        )
    }

    @Test fun `both settings in one sentence are not an order`() {
        never("dark mode and bigger text", "theme and font size", "font bara karo aur dark mode", "dark font", "light font")
    }

    // ------------------------------------------------------------------------------------------------ the talk before

    @Test fun `a person before does not change an order, and a bare size word is not about the settings`() {
        val before = person(6, Hudhud.Facet.PHONE)
        all(step(1), "font bara karo", before = before)
        all(theme(1), "dark mode", before = before)
        never("bara karo", "aur bara", "bigger", "isko bara karo", "chhota karo", before = before)
        assertEquals(person(6, Hudhud.Facet.PHONE), read("uska number", before = before))
    }

    @Test fun `an order after an order is read on its own words`() {
        val before = look(step(1))
        all(step(-1), "font chhota karo", before = before)
        all(theme(1), "dark mode", before = before)
        never("aur bara", "thoda aur", "wapas", before = before)
    }

    // ------------------------------------------------------------------------------------------------ the card

    @Test fun `an order is no label of the bank and not a word of the sense`() {
        assertNull(Hudhud.labelOf(look(step(1))))
        assertNull(Hudhud.labelOf(look(showTheme)))
        assertNull(Hudhud.labelOf(Ask.LookDone(HudhudLook.Made(Setting.SIZE, 1, 2))))
    }

    @Test fun `an order is a value, the same twice, so a chip typed back is the same as the chip`() {
        assertEquals(look(step(1)), look(step(1)))
        assertEquals(look(step(1)).hashCode(), look(step(1)).hashCode())
        assertTrue(look(step(1)) != look(step(-1)))
        assertTrue(look(showSize) != look(showTheme))
        assertEquals(Ask.LookDone(HudhudLook.Made(Setting.SIZE, 1, 2)), Ask.LookDone(HudhudLook.Made(Setting.SIZE, 1, 2)))
        assertTrue(Ask.LookDone(HudhudLook.Made(Setting.SIZE, 1, 2)) != Ask.LookDone(HudhudLook.Made(Setting.SIZE, 1, 2).reversed()))
    }

    @Test fun `the rules' own reading of an order is never turned by the sense`() {
        // Measured 14 Nov 2026: with `Ask.Look` taken out of the protection in `decide`, seven of this file's tests fail,
        // so the sense does turn an order it is sure of into something else. Keep the protection.
        for (q in listOf("font bara karo", "dark mode", "theme badlo", "text size large", "phone jaisa theme", "ڈارک موڈ", "डार्क मोड"))
            assertTrue("\"$q\"", isLook(read(q)))
    }
}
