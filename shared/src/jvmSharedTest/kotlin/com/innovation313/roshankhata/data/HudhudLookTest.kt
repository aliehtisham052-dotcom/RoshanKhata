package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudLook.Plan
import com.innovation313.roshankhata.data.HudhudLook.Request
import com.innovation313.roshankhata.data.HudhudLook.Setting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The look reader (14 Nov 2026, lesson 121): what it reads in nine languages and — as much — what it never reads. A sentence is
 * its own only when a word for the text, its size or the theme is in it and every other word is one of its own or a little word
 * that asks nothing; a figure, a name, a "don't", a "how", an unknown word, or both settings in one sentence is somebody else's.
 * Names are made up (lesson 80).
 */
class HudhudLookTest {

    private val calling = setOf("hudhud", "ہدہد", "bhai", "jaan", "ji")

    private fun read(s: String) = HudhudLook.read(s, calling = { it in calling })

    private fun size(level: Int): Request = Request.SizeTo(level)
    private fun step(by: Int): Request = Request.SizeStep(by)
    private fun theme(mode: Int): Request = Request.ThemeTo(mode)
    private val showSize: Request = Request.Show(Setting.SIZE)
    private val showTheme: Request = Request.Show(Setting.THEME)

    private fun all(expected: Request, vararg sentences: String) {
        for (q in sentences) assertEquals("\"$q\"", expected, read(q))
    }

    private fun never(vararg sentences: String) {
        for (q in sentences) assertNull("\"$q\" -> ${read(q)}", read(q))
    }

    // ------------------------------------------------------------------------------------------------ the levels

    @Test fun `English levels`() {
        all(size(0), "text size small", "small text", "small font", "smallest font", "set the font to small", "font size small")
        all(size(1), "text size normal", "normal font size", "set text size to normal", "default text size", "normal text", "font normal")
        all(
            size(2), "text size large", "large text", "big font", "set the font to large", "make the text size large", "text size big",
            "make the font big", "make the font large please", "font large"
        )
        all(
            size(3), "text size largest", "largest text", "biggest font", "maximum font size", "very large text", "extra large font",
            "huge font", "make the text largest"
        )
    }

    @Test fun `English steps`() {
        all(
            step(1), "bigger text", "make the text bigger", "increase font size", "larger font", "font bigger", "enlarge the text",
            "make my font larger please", "make the font a bit bigger", "make the text size bigger", "much bigger font", "more big text"
        )
        all(
            step(-1), "smaller text", "make the font smaller", "decrease text size", "reduce the font size", "text smaller",
            "make the text a little smaller", "shrink the font", "lower the text size"
        )
    }

    @Test fun `English themes`() {
        all(
            theme(1), "dark mode", "dark theme", "turn on dark mode", "enable dark theme", "switch to dark mode", "night mode",
            "change theme to dark", "set theme to dark", "dark mode on", "use dark mode", "screen dark", "make the theme dark"
        )
        all(
            theme(0), "light mode", "light theme", "switch to light theme", "day mode", "turn off dark mode", "disable dark mode",
            "dark mode off", "turn dark mode off", "bright theme", "turn on light mode", "theme light"
        )
        all(
            theme(2), "same as phone theme", "follow system theme", "theme same as the phone", "automatic theme", "auto theme",
            "theme same as device", "set theme to system"
        )
    }

    @Test fun `English shows where it stands, changes nothing`() {
        all(
            showSize, "text size", "font size", "font", "what is the text size", "what's the font size", "is the font large",
            "text size?", "change text size", "change the font size", "size of text", "show me the text size", "which font size is on"
        )
        all(
            showTheme, "theme", "change theme", "what is the theme", "which theme is on", "is dark mode on", "is dark mode on?",
            "what's the theme", "show me the theme", "is the theme dark", "dark mode?"
        )
    }

    // ------------------------------------------------------------------------------------------------ Roman Urdu and Hindi

    @Test fun `Roman Urdu steps`() {
        all(
            step(1), "font bara karo", "font ko bara karo", "font bara kar do", "likhai bari karo", "font ka size barhao",
            "font aur bara karo", "font thoda bara karo", "text bara karo", "font size bara karo", "font zara bara karo",
            "likhai ka size barha do", "font badha do", "text size zyada karo", "font bade karo"
        )
        all(
            step(-1), "font chhota karo", "font ko chhota kar do", "likhai chhoti karo", "font ka size kam karo", "font ghatao",
            "font thoda chhota karo", "font aur chhota karo", "text chota karo", "font size ghata do"
        )
    }

    @Test fun `Roman Urdu levels`() {
        all(size(2), "text size bara", "likhai ka size bara", "font bara rakho", "text size large karo", "font large karo")
        all(size(3), "likhai ka size sab se bara", "font sab se bara", "font bohat bara karo", "text size sab se bara rakho", "font sabse bara")
        all(size(0), "likhai ka size chhota", "font chhota rakho", "font bohat chhota karo", "font small karo")
        all(size(1), "likhai ka size aam", "font aam karo", "font normal karo", "font aam rakho", "text size normal rakho")
    }

    @Test fun `Roman Urdu themes`() {
        all(
            theme(1), "dark mode lagao", "dark mode on karo", "theme tareek karo", "raat wala mode", "theme tareek", "dark theme lagao",
            "mujhe dark mode chahiye", "dark mode chalu karo", "theme dark karo", "raat ka mode lagao"
        )
        all(
            theme(0), "dark mode band karo", "dark mode hatao", "theme roshan karo", "din wala theme", "light theme lagao", "theme roshan",
            "dark mode off karo", "dark mode hata do"
        )
        all(theme(2), "phone jaisa theme", "theme phone ke mutabiq karo", "theme phone ke mutabiq", "phone ke hisaab se theme")
        all(showTheme, "theme badlo", "theme kaisa hai", "kaun sa theme laga hai", "theme batao", "theme dikhao", "mera theme kya hai", "theme change karo")
        all(showSize, "text size badlo", "font ka size", "font ka size batao", "font ka size kya hai", "likhai ka size", "likhai ka size badlo", "font badlo")
    }

    @Test fun `Hindi`() {
        all(step(1), "फॉन्ट बड़ा करो", "फ़ॉन्ट बड़ा करो", "फॉन्ट का आकार बढ़ाओ", "फॉन्ट और बड़ा करो", "टेक्स्ट बड़ा करें", "अक्षर बड़े करो")
        all(step(-1), "फॉन्ट छोटा करो", "फॉन्ट का आकार घटाओ", "फॉन्ट और छोटा करो", "अक्षर छोटे करो")
        all(size(2), "अक्षरों का आकार बड़ा", "फॉन्ट बड़ा रखो")
        all(size(3), "अक्षरों का आकार सबसे बड़ा", "फॉन्ट सबसे बड़ा करो")
        all(size(0), "अक्षरों का आकार छोटा")
        all(size(1), "अक्षरों का आकार सामान्य", "फॉन्ट सामान्य करो")
        all(theme(1), "डार्क मोड", "डार्क मोड चालू करो", "डार्क थीम", "थीम डार्क करो", "रात वाला मोड")
        all(theme(0), "डार्क मोड बंद करो", "लाइट थीम", "थीम लाइट", "लाइट मोड")
        all(theme(2), "थीम फ़ोन जैसा", "थीम फोन के अनुसार", "थीम फ़ोन जैसा करो")
        all(showTheme, "थीम", "थीम बदलो", "थीम क्या है", "थीम बदलें")
        all(showSize, "अक्षरों का आकार", "फॉन्ट का आकार बदलो")
    }

    // ------------------------------------------------------------------------------------------------ the Arabic script

    @Test fun `Urdu`() {
        all(step(1), "فونٹ بڑا کریں", "فونٹ بڑا کرو", "فونٹ کا سائز بڑھائیں", "فونٹ اور بڑا کریں", "لکھائی بڑی کریں", "فونٹ بڑھاؤ")
        all(step(-1), "فونٹ چھوٹا کریں", "فونٹ کا سائز کم کریں", "فونٹ اور چھوٹا کریں", "لکھائی چھوٹی کریں", "فونٹ گھٹاؤ")
        all(size(2), "تحریر کا سائز بڑا", "فونٹ بڑا رکھیں")
        all(size(3), "تحریر کا سائز سب سے بڑا", "فونٹ سب سے بڑا کریں")
        all(size(0), "تحریر کا سائز چھوٹا")
        all(size(1), "تحریر کا سائز عام", "فونٹ عام کریں")
        all(theme(1), "ڈارک موڈ", "ڈارک تھیم", "تھیم تاریک کریں", "تاریک تھیم", "رات والا موڈ", "ڈارک موڈ آن کریں")
        all(theme(0), "لائٹ موڈ", "روشن تھیم", "ڈارک موڈ بند کریں", "ڈارک موڈ ہٹاؤ", "تھیم روشن")
        all(theme(2), "تھیم فون کے مطابق", "فون جیسا تھیم", "تھیم فون کے ساتھ")
        all(showTheme, "تھیم", "تھیم بدلیں", "تھیم کیا ہے")
        all(showSize, "تحریر کا سائز", "فونٹ کا سائز بدلیں", "فونٹ")
    }

    @Test fun `Sindhi`() {
        all(step(1), "فانٽ وڏو ڪريو", "فانٽ وڌايو")
        all(step(-1), "فانٽ ننڍو ڪريو", "فانٽ گھٽايو")
        all(size(2), "لکت جي ماپ وڏو")
        all(size(3), "لکت جي ماپ تمام وڏو")
        all(size(0), "لکت جي ماپ ننڍو")
        all(size(1), "لکت جي ماپ عام")
        all(theme(1), "اونداهي ٿيم", "ٿيم اونداهي ڪريو")
        all(theme(0), "روشن ٿيم", "ٿيم روشن")
        all(theme(2), "ٿيم فون مطابق")
        all(showTheme, "ٿيم", "ٿيم بدلايو")
        all(showSize, "لکت جي ماپ", "لکت جي ماپ بدلايو")
    }

    @Test fun `Arabic`() {
        all(step(1), "كبّر الخط", "اجعل الخط أكبر", "زد حجم الخط", "اجعل الخط كبيرا", "كبر النص")
        all(step(-1), "صغّر الخط", "أنقص حجم الخط", "اجعل الخط أصغر", "صغر النص")
        all(size(2), "حجم النص كبير")
        all(size(3), "حجم النص كبير جدًا", "حجم الخط كبير جدا")
        all(size(0), "حجم النص صغير")
        all(size(1), "حجم النص عادي")
        all(theme(1), "الوضع الداكن", "فعّل الوضع الداكن", "الوضع الليلي", "المظهر داكن")
        all(theme(0), "أوقف الوضع الداكن", "الوضع الفاتح", "المظهر فاتح")
        all(theme(2), "المظهر حسب الهاتف")
        all(showTheme, "المظهر", "غيّر المظهر", "ما هو المظهر")
        all(showSize, "حجم النص", "حجم الخط", "غيّر حجم الخط")
    }

    @Test fun `Farsi`() {
        all(step(1), "فونت را بزرگتر کن", "فونت را بزرگ کن", "فونت بزرگ‌تر", "بزرگ کردن متن")
        all(step(-1), "فونت را کوچکتر کن", "فونت کوچک‌تر")
        all(size(2), "اندازهٔ متن بزرگ")
        all(size(3), "اندازهٔ متن خیلی بزرگ")
        all(size(0), "اندازهٔ متن کوچک")
        all(size(1), "اندازهٔ متن معمولی")
        all(theme(1), "حالت تاریک", "حالت شب", "پوسته تیره")
        all(theme(0), "پوسته روشن", "حالت روشن")
        all(theme(2), "پوسته مطابق گوشی")
        all(showTheme, "پوسته", "پوسته را عوض کن")
        all(showSize, "اندازهٔ متن", "اندازه فونت")
    }

    // ------------------------------------------------------------------------------------------------ Bengali and Indonesian

    @Test fun `Bengali`() {
        all(step(1), "ফন্ট বড় করো", "ফন্ট আরও বড় করো", "টেক্সট বড় করুন")
        all(step(-1), "ফন্ট ছোট করো", "ফন্ট আরও ছোট করো")
        all(size(2), "লেখার আকার বড়")
        all(size(3), "লেখার আকার সবচেয়ে বড়")
        all(size(0), "লেখার আকার ছোট")
        all(size(1), "লেখার আকার স্বাভাবিক")
        all(theme(1), "ডার্ক মোড", "ডার্ক মোড চালু করো", "ডার্ক থিম")
        all(theme(0), "ডার্ক মোড বন্ধ করো", "লাইট থিম", "থিম লাইট")
        all(theme(2), "থিম ফোনের মতো")
        all(showTheme, "থিম", "থিম পরিবর্তন করো")
        all(showSize, "লেখার আকার", "লেখার আকার বদলাও")
    }

    @Test fun `Indonesian`() {
        all(step(1), "perbesar teks", "buat teks besar", "ukuran huruf lebih besar", "teks lebih besar", "besarkan teks")
        all(step(-1), "perkecil teks", "teks lebih kecil", "kecilkan teks", "kurangi ukuran teks")
        all(size(2), "ukuran teks besar")
        all(size(3), "ukuran teks terbesar", "ukuran teks sangat besar")
        all(size(0), "ukuran teks kecil")
        all(size(1), "ukuran teks normal", "ukuran teks biasa")
        all(theme(1), "mode gelap", "tema gelap", "aktifkan mode gelap", "tolong mode gelap")
        all(theme(0), "matikan mode gelap", "tema terang", "mode terang")
        all(theme(2), "tema ikuti ponsel", "tema sama dengan ponsel")
        all(showTheme, "tema", "ubah tema", "apa tema ini")
        all(showSize, "ukuran teks", "ubah ukuran teks")
    }

    // ------------------------------------------------------------------------------------------------ the app's own words

    @Test fun `his name or a calling is not a word of the sentence`() {
        all(step(1), "Hudhud font bara karo", "bhai jaan font bara karo", "ji font bara karo", "font bara karo ji")
        all(theme(1), "Hudhud dark mode lagao", "bhai dark mode")
        all(showTheme, "Hudhud theme badlo", "ہدہد تھیم بدلیں")
    }

    // ------------------------------------------------------------------------------------------------ what it never reads

    @Test fun `both settings in one sentence are not read`() {
        never(
            "dark mode and bigger text", "theme and font size", "font bara karo aur dark mode", "text size large theme dark",
            "dark mode ke saath font bara karo", "تھیم اور فونٹ", "dark font", "light font", "dark text size"
        )
    }

    @Test fun `a question about the way is not an order`() {
        never(
            "how to change font size", "how do I turn on dark mode", "font size kaise badlein", "theme kaise badlte hain",
            "how can I make the text bigger", "فونٹ کا سائز کیسے بدلیں", "كيف أغير حجم الخط", "bagaimana ubah tema"
        )
    }

    @Test fun `a figure is not read`() {
        never("font size 20", "text size 150", "font 2 size bara karo", "dark mode 2", "تھیم 3", "फॉन्ट 18", "text 150%", "ukuran teks 14")
    }

    @Test fun `a name or a word the reader does not know is not read`() {
        never(
            "Ali ka font bara karo", "bill ka font bara karo", "invoice font bigger", "make the font bigger in the invoice",
            "Bashir dark mode", "font aur dark mode lagao ali", "theme khata", "text message bhejo", "context font",
            "textile ka rate", "font bara karo ya chhota", "font kyun chhota hai"
        )
    }

    @Test fun `a refusal is not read`() {
        never(
            "don't make the font bigger", "do not use dark mode", "font bara mat karo", "font bara nahi chahiye", "dark mode nahi chahiye",
            "no dark mode", "dark mode kabhi nahi", "فونٹ بڑا نہ کریں", "تھیم مت بدلیں", "jangan ubah tema"
        )
    }

    @Test fun `the words that are also other things are not the settings alone`() {
        never(
            "mode", "payment mode", "mode of payment", "light", "light bill", "light chali gayi", "dark", "day", "night", "mood", "موڈ",
            "ڈارک", "screen", "colour", "background", "bara", "chhota", "volume bara karo", "awaaz bara karo", "speed", "normal",
            "large", "pack size", "product size", "shoe size 9"
        )
    }

    @Test fun `a bare size word is where the size stands, the same as the text size`() {
        // "size" alone is read as the app's text size; the shop's goods have their own sizes only beside a product's name.
        all(showSize, "size")
    }

    @Test fun `a long sentence or none at all is not read`() {
        never("", "   ", "?", "font bara karo font bara karo font bara karo font bara karo font bara karo font bara karo")
        assertNull(HudhudLook.read("one two three four five six seven eight nine ten eleven twelve thirteen font"))
    }

    @Test fun `two values are not read`() {
        never("theme dark light", "theme dark system", "theme light karo dark karo", "font small large", "font normal bigger", "font bigger smaller")
        never("turn dark mode on off", "dark mode on off", "font off")
        // A step and a level said together are two answers.
        never("font large bigger", "font bara bigger", "font largest smaller")
    }

    // ------------------------------------------------------------------------------------------------ the guards, each one pinned

    @Test fun `a figure is refused even when the caller would take any word`() {
        // The caller's own test of a calling word is not trusted with a figure: a digit never belongs to a look request.
        val anyWord = { _: String -> true }
        for (q in listOf("text size 150", "font 2 size bara karo", "dark mode 2", "تھیم 3", "फॉन्ट 18", "ukuran teks 14")) {
            assertNull("\"$q\"", HudhudLook.read(q, calling = anyWord))
        }
        // Without the figure the same generous caller reads it, so it is the figure that stops the others.
        assertEquals(showSize, HudhudLook.read("text size", calling = anyWord))
    }

    @Test fun `a phrase that shortens a long sentence lets it through`() {
        // Fourteen words said, twelve once "phone ke mutabiq" is one word: the length is judged after the phrases.
        all(theme(2), "hudhud bhai jaan ji theme ko phone ke mutabiq rakho ji ji ji ji")
        // Seventeen words said is another sentence, whatever the phrases make of it.
        never("hudhud bhai jaan ji theme ko phone ke mutabiq rakho ji ji ji ji ji ji ji")
    }

    @Test fun `an Arabic question mark is a question`() {
        // Only the sign says it: without it the same words are an order for the level.
        all(size(2), "حجم الخط كبير")
        all(showSize, "حجم الخط كبير؟")
    }

    @Test fun `a size word in a sentence about the theme is two sentences in one`() {
        never(
            "make the dark theme bigger", "dark theme smaller", "light mode large", "dark mode normal", "make the dark mode bigger please",
            "dark theme largest"
        )
    }

    @Test fun `off flips the theme it names, either way, and flips nothing else`() {
        all(theme(0), "dark mode off", "turn off dark mode", "switch off the dark theme")
        all(theme(1), "light mode off", "turn off light mode", "switch off the light theme", "light theme band karo")
        // Nothing to flip: off beside the theme alone, or beside the phone's own setting, is no order.
        never("theme off", "turn off the theme", "turn off the system theme", "theme band karo")
    }

    @Test fun `an adjective with set stays the level even beside a doing word`() {
        // "Font bara karo" is a step (people say it again); "font bara set karo" names the level it is to be.
        all(step(1), "font bara karo")
        all(size(2), "font bara set karo", "font bara rakho", "text bara rakh do")
        all(size(0), "font chhota set karo", "font chhota rakho")
    }

    // ------------------------------------------------------------------------------------------------ what a request does

    @Test fun `a level changes the setting or is already what it was`() {
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 1, 2)), HudhudLook.plan(size(2), 1))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 3, 0)), HudhudLook.plan(size(0), 3))
        assertEquals(Plan.Same(Setting.SIZE, 2), HudhudLook.plan(size(2), 2))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.THEME, 0, 1)), HudhudLook.plan(theme(1), 0))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.THEME, 2, 0)), HudhudLook.plan(theme(0), 2))
        assertEquals(Plan.Same(Setting.THEME, 2), HudhudLook.plan(theme(2), 2))
    }

    @Test fun `a step is one level, and past the end it says so`() {
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 1, 2)), HudhudLook.plan(step(1), 1))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 2, 3)), HudhudLook.plan(step(1), 2))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 1, 0)), HudhudLook.plan(step(-1), 1))
        assertEquals(Plan.Limit(Setting.SIZE, 3, bigger = true), HudhudLook.plan(step(1), 3))
        assertEquals(Plan.Limit(Setting.SIZE, 0, bigger = false), HudhudLook.plan(step(-1), 0))
    }

    @Test fun `a value the app does not hold is held to its own range`() {
        // The setting reads 9 where nothing could: treated as the last one, never an out-of-range write.
        assertEquals(Plan.Limit(Setting.SIZE, 3, bigger = true), HudhudLook.plan(step(1), 9))
        assertEquals(Plan.Limit(Setting.SIZE, 0, bigger = false), HudhudLook.plan(step(-1), -4))
        assertEquals(Plan.Change(HudhudLook.Made(Setting.SIZE, 3, 2)), HudhudLook.plan(step(-1), 12))
        assertEquals(Plan.Same(Setting.SIZE, 3), HudhudLook.plan(size(7), 3))
        assertEquals(Plan.Same(Setting.THEME, 2), HudhudLook.plan(theme(8), 2))
        assertEquals(Plan.Show(Setting.THEME, 2), HudhudLook.plan(showTheme, 40))
    }

    @Test fun `asking where it stands changes nothing`() {
        assertEquals(Plan.Show(Setting.SIZE, 1), HudhudLook.plan(showSize, 1))
        assertEquals(Plan.Show(Setting.THEME, 0), HudhudLook.plan(showTheme, 0))
    }

    @Test fun `a change taken back and done again`() {
        val made = HudhudLook.Made(Setting.SIZE, 1, 2)
        assertFalse(made.undone)
        val back = made.reversed()
        assertEquals(HudhudLook.Made(Setting.SIZE, 2, 1, undone = true), back)
        assertTrue(back.undone)
        assertEquals(made, back.reversed())
        assertEquals(1, back.to)
    }

    @Test fun `the choices beside a card are the other values`() {
        assertEquals(listOf(0, 2, 3), HudhudLook.others(Setting.SIZE, 1))
        assertEquals(listOf(1, 2, 3), HudhudLook.others(Setting.SIZE, 0))
        assertEquals(listOf(0, 1), HudhudLook.others(Setting.THEME, 2))
        assertEquals(listOf(0, 1, 2), HudhudLook.others(Setting.THEME, 9))
        assertEquals(3, HudhudLook.lastValue(Setting.SIZE))
        assertEquals(2, HudhudLook.lastValue(Setting.THEME))
    }

    @Test fun `the numbers are the app's own`() {
        // TextSize.SMALL..LARGEST and ThemeMode.LIGHT, DARK, SYSTEM; the app's own unit test holds them equal to these.
        assertEquals(listOf(0, 1, 2, 3), listOf(HudhudLook.SIZE_SMALL, HudhudLook.SIZE_NORMAL, HudhudLook.SIZE_LARGE, HudhudLook.SIZE_LARGEST))
        assertEquals(listOf(0, 1, 2), listOf(HudhudLook.THEME_LIGHT, HudhudLook.THEME_DARK, HudhudLook.THEME_SYSTEM))
    }
}
