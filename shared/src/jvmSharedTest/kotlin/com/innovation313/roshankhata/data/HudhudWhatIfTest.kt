package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A "what if" about how the app behaves is not a question of the book (16 Nov 2026, the owner's lists 2101-3100 and 6101-7100):
 * "if a negative price is typed, will the system stop it?" was this month's sales, "if I delete a chat ..." the day's activity.
 * Held through the whole pipeline (the sense attached, the app's pages; lesson 145). Real asks are kept: a sentence that does not
 * begin with "if", a short one, one that names somebody or something of the book, Arabic "لو سمحت" and Indonesian "kalau DAP".
 * Names and goods are invented.
 */
class HudhudWhatIfTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun shown(lang: String, said: String) =
        HudhudShown.of(Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null))

    @Test fun `a what if about the app is not answered with a figure`() {
        assertEquals("Unknown", shown("ur", "اگر فروخت قیمت منفی درج کی جائے تو کیا سسٹم اسے روکے گا؟"))
        assertEquals("Unknown", shown("ur", "اگر میں چیٹ ڈیلیٹ کرنے کا کہوں تو کیا آپ پہلے واضح کریں گے"))
        assertEquals("Unknown", shown("ur-Latn", "agar sale price minus likhi jaye to kya system rokega"))
        assertEquals("Unknown", shown("hi", "अगर बिक्री की कीमत माइनस लिखी जाए तो क्या सिस्टम रोकेगा"))
        assertEquals("Unknown", shown("bn", "যদি বিক্রির দাম মাইনাস লেখা হয় তাহলে কি সিস্টেম আটকাবে"))
        assertEquals("Unknown", shown("fa", "اگر قیمت فروش منفی وارد شود آیا سیستم جلوی آن را می‌گیرد"))
        assertEquals("Unknown", shown("en", "if a negative amount is typed will the system stop it"))
        assertEquals("Unknown", shown("en", "what if a negative amount is typed will the system stop it"))
    }

    @Test fun `real asks are kept`() {
        // not at the start, or short: an ordinary ask
        assertNotEquals("Unknown", shown("ur-Latn", "aaj ki sale kitni hui"))
        assertNotEquals("Unknown", shown("ur", "اگر ممکن ہو تو آج کی سیل بتائیں"))
        assertNotEquals("Unknown", shown("ur-Latn", "agar ho sake to aaj ki sale batao"))
        // somebody of the book named
        assertNotEquals("Unknown", shown("ur-Latn", "agar Ali Raza ne kal paise diye to uska hisaab batao"))
        // Arabic's "please" and Indonesian's "how about"
        assertNotEquals("Unknown", shown("ar", "لو سمحت قل لي كم على علي رضا"))
        assertNotEquals("Unknown", shown("in", "kalau DAP"))
        // a help page found by its words is still given
        assertTrue(shown("ur", "اگر میں اپنا بیک اپ بحال کرنا چاہوں تو کیسے کروں").let { it.startsWith("Help") || it == "Unknown" })
    }

    @Test fun `a question about what the app has is not answered with a figure`() {
        fun unknownOrPage(lang: String, said: String) = shown(lang, said).let { it == "Unknown" || it.startsWith("Help:") }
        assertTrue(unknownOrPage("ur", "کیا ڈیلیوری کا ریکارڈ Excel یا PDF میں Export ہو سکتا ہے؟"))
        assertTrue(unknownOrPage("ur", "کیا چھٹی کی درخواست درج کرنے کا آپشن موجود ہے؟"))
        assertTrue(unknownOrPage("ur-Latn", "kya delivery ka record Excel ya PDF mein export ho sakta hai"))
        assertTrue(unknownOrPage("hi", "क्या डिलीवरी का रिकॉर्ड एक्सेल में एक्सपोर्ट हो सकता है"))
        // "can you tell me": it asks him, and is kept
        assertNotEquals("Unknown", shown("ur", "کیا آپ آج کی سیل بتا سکتے ہیں؟"))
        assertNotEquals("Unknown", shown("ur-Latn", "kya aap aaj ki sale bata sakte hain"))
    }

    @Test fun `little words of a long sentence do not find a help page`() {
        // "ایک", "میں", "اگر", "بعد" are in every page; they found the heir's-key page in sixty sentences
        assertNotEquals("Help:screen_waris", shown("ur", "اگر ایک ہی سیل دو مرتبہ Sync ہو جائے تو KPI میں ڈبل سیلز سے کیسے بچا جائے گا"))
        assertNotEquals("Help:screen_waris", shown("ur", "کسٹمر ایک قیمت قبول کرے لیکن بعد میں مقدار کم کر دے تو ڈیل کیسے بدلے گی"))
    }

    @Test fun `a short sentence with if in front is an ask, a what-if that finds its page by words is given the page`() {
        // five words: too short to be a hypothetical
        assertNotEquals("Unknown", shown("ur-Latn", "agar aaj ki sale batao"))
        // a page found by words is given (two of its words), a page found by one word of a long what-if is not
        assertEquals("Help:faq_backup", shown("ur", "اگر میں اپنا بیک اپ بحال کرنا چاہوں تو کیسے کروں"))
        assertEquals("Unknown", shown("ur", "اگر کسٹمر نے Backup کے دوران ایک بار کچھ کہا تو آگے کیا ہوگا آخر"))
    }
}
