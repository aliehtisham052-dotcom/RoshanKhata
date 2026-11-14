package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The private note's strict reader (14 Nov 2026, lesson 121): what it reads in nine languages and — as much — what it never
 * reads. A sentence is the note's only when the word for a note is in it and every other word is a little word, a word that
 * says whose, or somebody's name in the book; a figure, a word it does not know, a note given, written or removed, a banknote,
 * a notice or a bill is somebody else's sentence. Names are made up (lesson 80).
 */
class HudhudNoteTest {

    /** The made-up book's names, as [VoiceQuestion.normalise] writes them: whole words, in the scripts a customer may be asked in. */
    private val book = listOf("Ali", "Bashir", "Raza", "علی", "بشیر", "رضا", "আলী", "अली", "बशीर").map { VoiceQuestion.normalise(it) }.toSet()
    private val calling = setOf("hudhud", "ہدہد", "bhai", "jaan", "ji")

    /** The book's own test, with Bengali's "র" (of) taken off a name as the app does (`bengaliStem`). */
    private fun isName(w: String) = w in book || w.removeSuffix("র") in book

    private fun read(s: String) = HudhudNote.read(s, calling = { it in calling }, known = { isName(it) })

    /** h his, a anyone, p private, m mine — what the sentence says of which note. */
    private fun flags(q: String): String? = read(q)?.let { r ->
        (if (r.his) "h" else "") + (if (r.anyone) "a" else "") + (if (r.priv) "p" else "") + (if (r.mine) "m" else "")
    }

    private fun all(expected: String, vararg sentences: String) {
        for (q in sentences) assertEquals("\"$q\"", expected, flags(q))
    }

    private fun never(vararg sentences: String) {
        for (q in sentences) assertNull("\"$q\" -> ${flags(q)}", read(q))
    }

    @Test fun `English`() {
        all("p", "Ali's private note", "show me Ali's private note", "private note")
        all("", "what is the note for Ali", "read me Ali Raza's note", "can you show me the note on Bashir", "note of Ali")
        all("hp", "show me the private note of this customer", "tell me his private note")
        all("ap", "who has a private note")
        all("hap", "which customers have a private note")
        all("ha", "which customers have notes")
        all("m", "my notes", "show me my notes")
        all("a", "list notes", "all notes")
        all("h", "tell me his note", "what is her note", "the customer's note")
    }

    @Test fun `Roman Urdu`() {
        all("p", "Ali ka niji note batao", "Ali ka zaati note dikhao", "niji note", "Ali ki niji note kya hai")
        all("", "Ali ka note bata do", "Ali ka note padho", "Ali ka note sunao", "Ali Raza ka note", "Ali ki yaad dasht", "Ali ki yaaddasht")
        all("", "Hudhud Ali ka note batao", "bhai jaan Ali ka note bata do", "Ali ka note bataiye ji")
        all("h", "uska note kya hai", "iska note kya hai", "is ka note", "ye note", "customer ka note", "gahak ka note")
        all("a", "kis kis ka note hai", "kaun kaun sa note hai", "kon kon se notes hain", "kiska note hai", "sab ke notes", "tamam notes")
        all("m", "mere notes dikhao", "mera note", "hamare notes")
    }

    @Test fun `Urdu`() {
        all("p", "علی کا نجی نوٹ بتاؤ", "نجی نوٹ", "علی کا ذاتی نوٹ دکھاؤ")
        all("h", "اس کا نوٹ کیا ہے", "گاہک کا نوٹ")
        all("a", "کس کس کا نوٹ ہے", "کون کون سے نوٹ ہیں", "سب کے نوٹ")
        all("m", "میرے نوٹ دکھاؤ", "میرا نوٹ")
        all("", "علی کا نوٹ", "علی کی یادداشت", "یادداشت")
    }

    @Test fun `Sindhi`() {
        all("p", "علي جو ذاتي نوٽ ٻڌاءِ", "ذاتي نوٽ")
        all("a", "ڪنهن ڪنهن جو نوٽ آهي", "سڀني جا نوٽ")
        all("h", "هن جو نوٽ ڇا آهي")
    }

    @Test fun `Arabic`() {
        all("p", "اعرض ملاحظة علي الخاصة", "ملاحظة خاصة", "الملاحظة الخاصة")
        all("a", "من لديه ملاحظة", "ملاحظات العملاء")
        all("m", "ما هي ملاحظتي")
        all("h", "ما هي ملاحظته")
    }

    @Test fun `Farsi`() {
        all("p", "یادداشت خصوصی علی را نشان بده", "یادداشت خصوصی", "یادداشت‌های خصوصی")
        all("m", "یادداشت‌هایم")
        all("a", "چه کسانی یادداشت دارند", "کدام مشتریان یادداشت دارند")
        all("h", "یادداشت‌اش چیست", "یادداشتش چیست")
    }

    @Test fun `Hindi`() {
        all("p", "अली का निजी नोट बताओ", "निजी नोट", "अली का पर्सनल नोट दिखाओ")
        all("h", "उसका नोट क्या है", "ग्राहक का नोट")
        all("a", "किस किस का नोट है", "कौन कौन से नोट हैं", "सब के नोट")
        all("m", "मेरे नोट दिखाओ", "मेरा नोट")
    }

    @Test fun `Bengali`() {
        all("p", "আলীর ব্যক্তিগত নোট বলো", "ব্যক্তিগত নোট", "আলীর ব্যক্তিগত নোটটি দেখাও")
        all("a", "কার নোট আছে", "সবার নোট")
        all("m", "আমার নোট")
        all("h", "তার নোট কী")
        all("", "আলীর নোট")
    }

    @Test fun `Indonesian`() {
        all("p", "catatan pribadi", "tampilkan catatan pribadi Ali", "catatan pribadi Ali apa", "catatan pribadinya")
        all("ap", "siapa saja yang punya catatan pribadi")
        all("pm", "catatan pribadi saya")
    }

    /** The app's own word for it, said in each language (`party_note_label`): the chip is its own question. */
    @Test fun `the app's own label in every language is the private note`() {
        for (label in listOf(
            "Private note", "Niji note", "نجی نوٹ", "ذاتي نوٽ", "ملاحظة خاصة", "یادداشت خصوصی", "निजी नोट", "ব্যক্তিগত নোট", "Catatan pribadi"
        )) {
            val r = read(label)
            assertNotNull("\"$label\"", r)
            assertTrue("\"$label\"", r!!.priv)
            assertTrue("\"$label\"", r.specific)
        }
    }

    @Test fun `a note alone, or a name with it, says nothing of which note`() {
        assertFalse(read("note")!!.specific)
        assertFalse(read("Ali ka note")!!.specific)
        assertFalse(read("note dikhao")!!.specific)
        assertTrue(read("niji note")!!.specific)
        assertTrue(read("uska note")!!.specific)
    }

    // ---- what it never reads: lesson 121, every word is the note's own or it is somebody else's sentence ----

    @Test fun `a note given, written, sent, added or removed is not a note asked for`() {
        never(
            "Ali ko note do", "Ali ko 500 ka note do", "Ali ne note diya", "Ali ka note likho", "note likho", "note save karo",
            "Ali ka note hata do", "Ali ka note delete karo", "Ali ka note badlo",
            "give Ali a note", "send Ali a note", "delete Ali's note", "add note for Ali", "edit Ali's note", "write a note for Ali",
            "علی کو نوٹ دو", "علی کا نوٹ لکھو", "علی کا نوٹ مٹاؤ", "अली को नोट दो", "अली का नोट लिखो", "আলীকে নোট দাও", "আলীর নোট লেখো",
            "أضف ملاحظة لعلي", "احذف ملاحظة علي", "یادداشت برای علی بنویس"
        )
    }

    @Test fun `a banknote, a notice, a bill or a credit note is not a private note`() {
        never(
            "mujhe 10 note chahiye", "paanch sau ka note", "ten rupee note", "fifty rupee note", "ye note jaali hai",
            "یہ پانچ سو کا نوٹ ہے", "सौ रुपये का नोट", "দশ টাকার নোট", "نوٹ گننے کی مشین",
            "credit note banao", "credit note Ali", "debit note",
            "نوٹس بھیجو", "علی کو نوٹس", "nota Ali", "nota pembelian Ali", "tampilkan nota Ali"
        )
    }

    @Test fun `a figure in any script is somebody else's sentence`() {
        never("Ali ka note 500", "Ali ka note ٥٠٠", "Ali ka note ५००", "Ali ka note ৫০০", "note 3", "Ali's note 12")
    }

    @Test fun `a figure stops it even where every other word would pass`() {
        // The book's search may take a figure for a word of a name; the reader never does.
        assertNull(HudhudNote.read("Ali ka note 500", known = { true }))
        assertNull(HudhudNote.read("Ali ka note ٥٠٠", known = { true }))
        assertNull(HudhudNote.read("note ३", known = { true }))
    }

    @Test fun `a single who is a little word, only a doubled one asks for everybody`() {
        all("", "Ali ka note kaun sa hai", "kaun sa note Ali ka hai", "کون سا نوٹ علی کا ہے", "कौन सा नोट अली का है")
        all("a", "kaun kaun sa note hai", "کون کون سا نوٹ ہے")
    }

    @Test fun `a word the reader does not know is somebody else's sentence`() {
        never(
            "Zaid ka note", "Ali ki entry ka note", "Ali ke bill ka note", "Ali ka balance aur note", "release notes",
            "Ali ka note kaise likhte hain", "is note ka matlab", "how do I add a note", "Ali ke note ke baad kya hua",
            "what did Ali say in his note and what did he pay"
        )
    }

    @Test fun `the other things Ali is asked stay the other readers`() {
        never(
            "Ali ka balance kitna hai", "Ali ka number", "Ali ka bill dikhao", "Ali ka khata", "aaj ki sale", "Ali kab dega", "mera hisaab",
            "علی کا فون نمبر", "अली का बैलेंस", "আলীর ফোন নম্বর", "رقم علی"
        )
    }

    @Test fun `the ledger's own catatan stays the ledger's`() {
        never(
            "berapa catatan hari ini", "catatan hari ini", "catatan utang Ali", "tampilkan catatan Ali", "catatan minggu ini",
            "siapa yang hapus catatan kemarin", "catatan saya disimpan di mana sih", "Bang, catatan saya gak bocor ke mana-mana kan",
            "ini aplikasi catatan utang atau apa", "catatannya", "catatan Ali", "catatan saya"
        )
    }

    @Test fun `a long sentence is not a question about a note`() {
        never("Ali ka note bataiye aur phir bataiye ki Ali ne kitna diya aur kitna baqi hai aur kab dega aur kya kya liya")
    }

    @Test fun `a calling word is little only when the book says so`() {
        assertNull(HudhudNote.read("Hudhud Ali ka note batao", known = { isName(it) }))
        assertNotNull(HudhudNote.read("Hudhud Ali ka note batao", calling = { it == "hudhud" }, known = { isName(it) }))
    }

    @Test fun `a sentence with more than four names is a list of people`() {
        assertNull(read("Ali Bashir Raza Ali Bashir ka note"))
        assertNotNull(read("Ali Bashir Raza Ali ka note"))
    }

    @Test fun `no name is looked up for a sentence that has no note in it`() {
        var asked = 0
        val r = HudhudNote.read("kal ki sale kitni hui", known = { asked++; false })
        assertNull(r)
        assertEquals(0, asked)
    }

    // ---- what of a note is shown but never said aloud, and how a note is cut into lines ----

    private fun secrets(note: String) = HudhudNote.secretsIn(note)

    @Test fun `a phone number in a note is a secret to the voice`() {
        assertEquals(listOf("0300 1234567"), secrets("Ring him on 0300 1234567 after Asr"))
        assertEquals(listOf("+92 300 1234567"), secrets("call +92 300 1234567 please"))
        assertEquals(listOf("0300-1234567"), secrets("0300-1234567"))
        assertEquals(listOf("(0300) 1234567"), secrets("(0300) 1234567 is his wife's"))
        assertEquals(listOf("03001234567"), secrets("number 03001234567"))
    }

    @Test fun `a bracket round the number goes with it`() {
        assertEquals(listOf("(0300 1234567)"), secrets("his son (0300 1234567) knows"))
        assertEquals(listOf("(0300) 1234567"), secrets("(0300) 1234567"))
        // A bracket that is only the sentence's own stays out.
        assertEquals(listOf("0300 1234567"), secrets("(ask him) 0300 1234567 (after Asr)"))
    }

    @Test fun `a figure of seven digits cannot be told from a number and is not said`() {
        assertEquals(listOf("1234567"), secrets("owes Rs 1234567 in all"))
        assertTrue(secrets("owes Rs 123456 in all").isEmpty())
    }

    /** Lesson 100: the "nine all told" edge had no test, and a mutant that made it ten survived the others. */
    @Test fun `nine digits all told are a number, eight are a day`() {
        assertEquals(listOf("1234 5678 9"), secrets("account 1234 5678 9 at the bank"))
        assertTrue(secrets("account 1234 5678 at the bank").isEmpty())
    }

    @Test fun `an identity number in a note is a secret to the voice`() {
        assertEquals(listOf("35202-1234567-1"), secrets("CNIC 35202-1234567-1 copy is in the drawer"))
        assertEquals(listOf("35202 1234567 1"), secrets("35202 1234567 1"))
    }

    @Test fun `digits of any script count`() {
        assertEquals(listOf("۰۳۰۰ ۱۲۳۴۵۶۷"), secrets("نمبر ۰۳۰۰ ۱۲۳۴۵۶۷ ہے"))
        assertEquals(listOf("٠٣٠٠١٢٣٤٥٦٧"), secrets("رقمه ٠٣٠٠١٢٣٤٥٦٧"))
        assertEquals(listOf("०३०० १२३४५६७"), secrets("नंबर ०३०० १२३४५६७"))
        assertEquals(listOf("০৩০০ ১২৩৪৫৬৭"), secrets("নম্বর ০৩০০ ১২৩৪৫৬৭"))
    }

    @Test fun `a date, a time or a small sum is no secret`() {
        assertTrue(secrets("promised on 12-03-2025").isEmpty())
        assertTrue(secrets("comes at 4:30 on the 10th").isEmpty())
        assertTrue(secrets("Rs 500 now and Rs 1,200 on Friday").isEmpty())
        assertTrue(secrets("two bags, 50 kg each").isEmpty())
        assertTrue(secrets("1/2 acre, 3 killa").isEmpty())
        assertTrue(secrets("").isEmpty())
    }

    @Test fun `two numbers are two secrets, one number twice is one`() {
        assertEquals(listOf("0300 1234567", "0321 7654321"), secrets("0300 1234567 or 0321 7654321"))
        assertEquals(listOf("0300 1234567"), secrets("0300 1234567 / 0300 1234567"))
    }

    @Test fun `a note is cut into its lines`() {
        assertEquals(listOf("first", "second", "third"), HudhudNote.linesOf("first\nsecond\r\n\nthird\n"))
        assertEquals(listOf("only"), HudhudNote.linesOf("  only  "))
        assertTrue(HudhudNote.linesOf(" \n \r\n").isEmpty())
        assertTrue(HudhudNote.linesOf("").isEmpty())
    }
}
