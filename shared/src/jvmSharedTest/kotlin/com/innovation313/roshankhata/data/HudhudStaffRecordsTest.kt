package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.RecordFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 15 Nov 2026, the catalog's group "staff, plans, cheques, audit" (importance 6-7: staff pay and a plan's instalments are
 * money, staff are the owner's alone). Each record is asked in the nine languages through the whole pipeline with the
 * learnt sense (lesson 145): a staff member's pay, what the staff were paid, who was away, who a person is; a customer's
 * plan, the plans that were closed, a customer's cheques; and what was changed today. The staff are not in the customers'
 * list: the caller passes their names apart ([Hudhud.understand]'s `staff`). Names and sentences are invented (lesson 80).
 */
class HudhudStaffRecordsTest {

    private val customers = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Tariq Butt"),
        Hudhud.Name(4, "Naeem Khan"), Hudhud.Name(5, "Saleem Bhai"), Hudhud.Name(6, "Kamran Traders")
    )
    private val staff = listOf(Hudhud.Name(11, "Imran Siddiqui"), Hudhud.Name(12, "Kashif Mehmood"), Hudhud.Name(13, "Zahid Hussain"))
    private val topics = listOf("intro_documents", "intro_cashbook", "screen_bills", "intro_cheques", "intro_staff", "intro_plans")
        .map { Hudhud.Topic(it, it, it) }

    private fun heard(s: String, lang: String, staffNames: List<Hudhud.Name> = staff) =
        Hudhud.understand(s, customers, topics, lang, emptyList(), null, staffNames)

    private fun rec(f: RecordFacet, ids: List<Long> = emptyList(), w: VoiceQuestion.Window? = null) = Ask.Record(f, ids, w)

    private fun every(facet: RecordFacet, ids: List<Long>, w: VoiceQuestion.Window?, rows: List<Pair<String, String>>) {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for ((lang, said) in rows) assertEquals("$lang: $said", rec(facet, ids, w), heard(said, lang))
    }

    @Test fun `a named staff member's pay is read in nine languages`() = every(RecordFacet.STAFF_PAY, listOf(11), null, listOf(
        "ur-Latn" to "Imran ki tankhwah kitni hai", "en" to "how much is Imran's salary", "ur" to "عمران کی تنخواہ کتنی ہے",
        "sd" to "عمران جي پگهار ڪيتري آهي", "ar" to "كم راتب عمران", "fa" to "حقوق عمران چقدر است",
        "hi" to "इमरान की तनख्वाह कितनी है", "bn" to "ইমরানের বেতন কত", "in" to "berapa gaji Imran"
    ))

    @Test fun `what the staff were paid is read in nine languages`() = every(RecordFacet.STAFF_PAID, emptyList(), VoiceQuestion.Window.THIS_MONTH, listOf(
        "ur-Latn" to "is mahine staff ko kitna diya", "en" to "how much did we pay the staff this month", "ur" to "اس مہینے سٹاف کو کتنا دیا",
        "sd" to "هن مهيني اسٽاف کي ڪيترو ڏنو", "ar" to "كم دفعنا للموظفين هذا الشهر", "fa" to "این ماه به کارکنان چقدر پرداخت کردیم",
        "hi" to "इस महीने स्टाफ को कितना दिया", "bn" to "এই মাসে স্টাফকে কত দিয়েছি", "in" to "bulan ini gaji karyawan dibayar berapa"
    ))

    @Test fun `who was away today is read in nine languages`() = every(RecordFacet.ATTENDANCE, emptyList(), VoiceQuestion.Window.TODAY, listOf(
        "ur-Latn" to "aaj kaun absent hai", "en" to "who is absent today", "ur" to "آج کون غیر حاضر ہے",
        "sd" to "اڄ ڪير غير حاضر آهي", "ar" to "من الغائب اليوم", "fa" to "امروز چه کسی غایب است",
        "hi" to "आज कौन अनुपस्थित है", "bn" to "আজ কে অনুপস্থিত", "in" to "siapa yang absen hari ini"
    ))

    @Test fun `who a staff member is, his role, is read in nine languages`() = every(RecordFacet.STAFF_PERSON, listOf(11), null, listOf(
        "ur-Latn" to "Imran ka role kya hai", "en" to "what is Imran's role", "ur" to "عمران کا رول کیا ہے",
        "sd" to "عمران جو عهدو ڇا آهي", "ar" to "ما هو دور عمران", "fa" to "نقش عمران چیست",
        "hi" to "इमरान का रोल क्या है", "bn" to "ইমরানের ভূমিকা কী", "in" to "apa jabatan Imran"
    ))

    @Test fun `a customer's next instalment is read in nine languages`() = every(RecordFacet.PLAN, listOf(1), null, listOf(
        "ur-Latn" to "Ali ki qist kab aani hai", "en" to "when is Ali's next installment due", "ur" to "علی کی قسط کب آنی ہے",
        "sd" to "علي جي قسط ڪڏهن اچڻي آهي", "ar" to "متى قسط علي القادمة", "fa" to "قسط علی کی سررسید است",
        "hi" to "अली की किस्त कब आनी है", "bn" to "আলীর কিস্তি কবে আসবে", "in" to "kapan cicilan Ali jatuh tempo"
    ))

    @Test fun `the plans that were closed are read in nine languages`() = every(RecordFacet.PLAN_CLOSED, emptyList(), null, listOf(
        "ur-Latn" to "plan kab band hua", "en" to "which plans are closed", "ur" to "پلان کب بند ہوا",
        "sd" to "رٿا ڪڏهن بند ٿي", "ar" to "ما هي الخطط المغلقة", "fa" to "کدام طرح ها بسته شد",
        "hi" to "प्लान कब बंद हुआ", "bn" to "প্ল্যান কবে বন্ধ হলো", "in" to "rencana cicilan mana yang sudah lunas"
    ))

    @Test fun `when a customer's cheque cleared is read in nine languages`() = every(RecordFacet.CHEQUE, listOf(1), null, listOf(
        "ur-Latn" to "Ali ka cheque kab clear hua", "en" to "when did Ali's cheque clear", "ur" to "علی کا چیک کب کلیئر ہوا",
        "sd" to "علي جو چيڪ ڪڏهن ڪلئر ٿيو", "ar" to "متى صُرف شيك علي", "fa" to "چک علی کی پاس شد",
        "hi" to "अली का चेक कब क्लियर हुआ", "bn" to "আলীর চেক কবে ক্লিয়ার হয়েছে", "in" to "kapan cek Ali cair"
    ))

    @Test fun `what changed today is the audit rows, in nine languages`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val want = Ask.Activity(VoiceQuestion.Window.TODAY)
        for ((lang, said) in listOf(
            "ur-Latn" to "aaj kis ne kya badla", "en" to "what changed today", "ur" to "آج کس نے کیا بدلا",
            "sd" to "اڄ ڪنهن ڇا بدلايو", "ar" to "ماذا تغير اليوم", "fa" to "امروز چه چیزی تغییر کرد",
            "hi" to "आज किसने क्या बदला", "bn" to "আজ কে কী বদলেছে", "in" to "apa yang diubah hari ini"
        )) assertEquals("$lang: $said", want, heard(said, lang))
    }

    @Test fun `a month can be named, and a day, for a named staff member`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(rec(RecordFacet.STAFF_PAY, listOf(11), VoiceQuestion.Window.LAST_MONTH), heard("Imran ki tankhwah pichhle mahine kitni thi", "ur-Latn"))
        assertEquals(rec(RecordFacet.ATTENDANCE, listOf(11), VoiceQuestion.Window.YESTERDAY), heard("Imran kal absent tha", "ur-Latn"))
    }

    // The staff are not customers: a question with no staff list given, or about nobody on it, falls back to what it was.
    @Test fun `with no staff names given the staff questions stay what they were`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Status(Hudhud.StatusOf.STAFF), heard("Imran ki tankhwah kitni hai", "ur-Latn", emptyList()))
        // Who was away needs no name: every person on the list is asked.
        assertEquals(rec(RecordFacet.ATTENDANCE, emptyList(), VoiceQuestion.Window.TODAY), heard("aaj kaun absent hai", "ur-Latn", emptyList()))
    }

    @Test fun `the questions the records must not take stay as they were`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Status(Hudhud.StatusOf.STAFF), heard("salary baqi", "ur-Latn"))
        assertEquals(Ask.Status(Hudhud.StatusOf.STAFF), heard("staff ki salary", "ur-Latn"))
        assertEquals(Ask.Count(Hudhud.Thing.STAFF), heard("kitne staff hain", "ur-Latn"))
        assertEquals(Ask.Status(Hudhud.StatusOf.CHEQUES), heard("cheque pending hain", "ur-Latn"))
        assertEquals(Ask.Status(Hudhud.StatusOf.CHEQUES, Hudhud.ChequeState.CLEARED), heard("cheque kab clear hua", "ur-Latn"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.NOT_PAID), heard("Kis ko phone karna hai", "ur-Latn"))
        assertNotEquals(rec(RecordFacet.STAFF_PAID), heard("Mulazim ko kitna diya", "ur-Latn"))
        assertTrue(heard("Mulazim ko kitna diya", "ur-Latn") !is Ask.Record)
        assertTrue(heard("who is slow to pay", "en") !is Ask.Record)
    }

    @Test fun `a customer's cheques or plan are told only for a customer named`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(rec(RecordFacet.CHEQUE, listOf(1)), heard("Ali ke cheque", "ur-Latn"))
        assertEquals(rec(RecordFacet.PLAN, listOf(1)), heard("Ali ki qist kitni baqi hai", "ur-Latn"))
        assertTrue(heard("qist kitni baqi hai", "ur-Latn") !is Ask.Record)
    }

    /** Each guard that only it can refuse (lesson 129): the mutation pass left these two alive among the tests above. */
    @Test fun `an ageing word beside a staff name is no pay question, and a plan that was paid is no closed plan`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertTrue(heard("Zahid ki pay overdue hai", "ur-Latn") !is Ask.Record)
        assertEquals(rec(RecordFacet.PLAN, listOf(1)), heard("Ali plan khatam hua di", "ur-Latn"))
        assertEquals(rec(RecordFacet.PLAN, listOf(1)), heard("Ali ne plan poori di", "ur-Latn"))
        assertEquals(rec(RecordFacet.PLAN_CLOSED, listOf(1)), heard("Ali paid the plan completed", "en"))
    }

    @Test fun `a thing that did not come is no absence, and who the manager is is asked in Farsi without a staff word`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertTrue(heard("Ali ka bill nahi aaya", "ur-Latn") !is Ask.Record)
        assertTrue(heard("supplier ka maal nahi aaya", "ur-Latn") !is Ask.Record)
        assertTrue(heard("invoice did not come", "en") !is Ask.Record)
        assertEquals(rec(RecordFacet.STAFF_PERSON), heard("منیجر کیست", "fa"))
        assertEquals(rec(RecordFacet.STAFF_PERSON), heard("مدیر کیه", "fa"))
    }

    @Test fun `a sentence that names a product is the product's, not a record`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val urea = listOf(Hudhud.Name(21, "Urea"))
        for (said in listOf("Imran ki Urea ki salary", "Imran ki Urea ki advance", "Urea kaun absent hai", "Urea ka role"))
            assertTrue(said, Hudhud.understand(said, customers, topics, "ur-Latn", urea, null, staff) !is Ask.Record)
    }

    @Test fun `a word of being present is no attendance without a staff word or a who with a day, and a customer's own name is no staff pay`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for (said in listOf("Ali ko hazir karo", "present the bill to Ali", "Ali hazir hai", "hazir hai"))
            assertTrue(said, heard(said, "ur-Latn") !is Ask.Record)
        // A staff member who shares a name with a customer: "pay" is then the customer's word, not a wage.
        val twin = listOf(Hudhud.Name(14, "Ali Raza"))
        assertTrue(Hudhud.understand("Ali Raza ki pay", customers, topics, "ur-Latn", emptyList(), null, twin) !is Ask.Record)
        assertTrue(Hudhud.understand("what is Ali Raza's pay", customers, topics, "en", emptyList(), null, twin) !is Ask.Record)
    }

    @Test fun `what is left of a salary is the pay, a leave word alone is no absence, and who edited an invoice is no audit`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(rec(RecordFacet.STAFF_PAY, listOf(11)), heard("Imran ki kitni salary baqi hai", "ur-Latn"))
        assertEquals(rec(RecordFacet.STAFF_PAY, listOf(12)), heard("how much of Kashif's salary is left", "en"))
        for (said in listOf("leave a message for Ali", "kal chutti ka din hai", "I will leave the shop early"))
            assertTrue(said, heard(said, "en") !is Ask.Record)
        assertEquals(Ask.Help("intro_documents"), heard("siapa yang mengedit invoice", "in"))
        assertEquals(Ask.Count(Hudhud.Thing.BILLS, VoiceQuestion.Window.TODAY), heard("siapa yang menghapus tagihan hari ini", "in"))
        assertEquals(Ask.Activity(VoiceQuestion.Window.TODAY), heard("siapa yang mengedit hari ini", "in"))
        assertEquals(rec(RecordFacet.STAFF_PAY, listOf(11)), heard("Imran ki salary mein se kitni di aur kitni baqi hai", "ur-Latn"))
        assertEquals(rec(RecordFacet.STAFF_PAY, listOf(12)), heard("how much of Kashif's salary was paid and how much is left", "en"))
    }

    @Test fun `check as a verb of doing is no cheque`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for ((lang, said) in listOf("hi" to "ज़रा अली रज़ा की लिमिट चेक करो", "ur" to "ذرا علی رضا کی لمٹ چیک کرو", "ur-Latn" to "Ali Raza ki limit check karo"))
            assertTrue("$lang: $said", heard(said, lang) !is Ask.Record)
        assertEquals(rec(RecordFacet.CHEQUE, listOf(1)), heard("Ali ke cheque", "ur-Latn"))
    }
}
