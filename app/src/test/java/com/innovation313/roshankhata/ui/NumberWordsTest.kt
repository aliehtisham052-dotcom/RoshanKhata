package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {

    @Test
    fun `persian words, joined with and, in hezar, milyun and milyard`() {
        assertEquals("صفر روپیه", NumberWords.inWordsPersian(0.0, "روپیه"))
        assertEquals("هفت روپیه", NumberWords.inWordsPersian(7.0, "روپیه"))
        assertEquals("نوزده روپیه", NumberWords.inWordsPersian(19.0, "روپیه"))
        assertEquals("بیست و یک روپیه", NumberWords.inWordsPersian(21.0, "روپیه"))
        assertEquals("صد روپیه", NumberWords.inWordsPersian(100.0, "روپیه"))
        assertEquals("صد و پانزده روپیه", NumberWords.inWordsPersian(115.0, "روپیه"))
        assertEquals("هزار روپیه", NumberWords.inWordsPersian(1000.0, "روپیه"))
        assertEquals("هزار و یک روپیه", NumberWords.inWordsPersian(1001.0, "روپیه"))
        assertEquals("دو هزار و پانصد روپیه", NumberWords.inWordsPersian(2500.0, "روپیه"))
        assertEquals("بیست و یک هزار و پانصد روپیه", NumberWords.inWordsPersian(21500.0, "روپیه"))
        assertEquals("صد هزار روپیه", NumberWords.inWordsPersian(100000.0, "روپیه"))
        assertEquals("یک میلیون روپیه", NumberWords.inWordsPersian(1000000.0, "روپیه"))
        assertEquals("دو میلیون و پانصد هزار روپیه", NumberWords.inWordsPersian(2500000.0, "روپیه"))
        assertEquals("یک میلیون و دویست و سی و چهار هزار و پانصد و شصت و هفت روپیه", NumberWords.inWordsPersian(1234567.0, "روپیه"))
        assertEquals("یک میلیارد روپیه", NumberWords.inWordsPersian(1000000000.0, "روپیه"))
        assertEquals("پنج هزار و دویست USD", NumberWords.inWordsPersian(5200.9, "USD"))
        assertEquals("صفر روپیه", NumberWords.inWordsPersian(-5.0, "روپیه"))
    }

    @Test
    fun `indonesian words, the se- forms and the regular ones`() {
        assertEquals("Nol rupiah", NumberWords.inWordsIndonesian(0.0, "rupiah"))
        assertEquals("Sepuluh rupiah", NumberWords.inWordsIndonesian(10.0, "rupiah"))
        assertEquals("Sebelas rupiah", NumberWords.inWordsIndonesian(11.0, "rupiah"))
        assertEquals("Sembilan belas rupiah", NumberWords.inWordsIndonesian(19.0, "rupiah"))
        assertEquals("Dua puluh satu rupiah", NumberWords.inWordsIndonesian(21.0, "rupiah"))
        assertEquals("Seratus sebelas rupiah", NumberWords.inWordsIndonesian(111.0, "rupiah"))
        assertEquals("Seribu seratus rupiah", NumberWords.inWordsIndonesian(1100.0, "rupiah"))
        assertEquals("Dua puluh satu ribu lima ratus rupiah", NumberWords.inWordsIndonesian(21500.0, "rupiah"))
    }

    @Test
    fun `indonesian counts in ribu, juta and miliar, never lakh and crore`() {
        assertEquals("Seratus ribu rupiah", NumberWords.inWordsIndonesian(100000.0, "rupiah"))
        assertEquals("Satu juta rupiah", NumberWords.inWordsIndonesian(1000000.0, "rupiah"))
        assertEquals("Dua juta lima ratus ribu rupiah", NumberWords.inWordsIndonesian(2500000.0, "rupiah"))
        assertEquals(
            "Satu juta dua ratus tiga puluh empat ribu lima ratus enam puluh tujuh rupiah",
            NumberWords.inWordsIndonesian(1234567.0, "rupiah")
        )
        assertEquals("Satu miliar rupiah", NumberWords.inWordsIndonesian(1000000000.0, "rupiah"))
    }

    @Test
    fun `indonesian drops the fraction, treats a negative as zero, and names any unit`() {
        assertEquals("Lima ribu dua ratus USD", NumberWords.inWordsIndonesian(5200.9, "USD"))
        assertEquals("Nol rupiah", NumberWords.inWordsIndonesian(-5.0, "rupiah"))
    }

    @Test
    fun `hindi words, lakh and crore, irregular tens`() {
        assertEquals("शून्य रुपये", NumberWords.rupeesInWordsHindi(0.0))
        assertEquals("उन्नीस हज़ार दो सौ पचासी रुपये", NumberWords.rupeesInWordsHindi(19285.0))
        assertEquals("एक करोड़ पाँच लाख रुपये", NumberWords.rupeesInWordsHindi(10500000.0))
        assertEquals("निन्यानवे रुपये", NumberWords.rupeesInWordsHindi(99.0))
    }

    @Test
    fun `bengali words, in taka and in rupees`() {
        assertEquals("শূন্য টাকা", NumberWords.inWordsBengali(0.0, "টাকা"))
        assertEquals("উনিশ হাজার দুই শত পঁচাশি টাকা", NumberWords.inWordsBengali(19285.0, "টাকা"))
        assertEquals("এক কোটি পাঁচ লক্ষ রুপি", NumberWords.inWordsBengali(10500000.0, "রুপি"))
        assertEquals("নিরানব্বই টাকা", NumberWords.inWordsBengali(99.0, "টাকা"))
    }

    @Test
    fun `zero`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWordsRomanUrdu(0.0))
    }

    @Test
    fun `single digit and teen`() {
        assertEquals("Saat Rupay", NumberWords.rupeesInWordsRomanUrdu(7.0))
        assertEquals("Unnees Rupay", NumberWords.rupeesInWordsRomanUrdu(19.0))
    }

    @Test
    fun `the exact figure from the finalised invoice mockup`() {
        // T1 (Teal Corporate) shows this as the worked example for Rs 19,285.
        assertEquals("Unnees Hazar Do Sou Pichhasi Rupay", NumberWords.rupeesInWordsRomanUrdu(19285.0))
    }

    @Test
    fun `hundred alone, no hazar or sau below it`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWordsRomanUrdu(100.0))
    }

    @Test
    fun `thousand exactly, no sau or remainder`() {
        assertEquals("Paanch Hazar Rupay", NumberWords.rupeesInWordsRomanUrdu(5000.0))
    }

    @Test
    fun `lakh and crore scale`() {
        assertEquals("Ek Lakh Rupay", NumberWords.rupeesInWordsRomanUrdu(100000.0))
        assertEquals("Ek Crore Rupay", NumberWords.rupeesInWordsRomanUrdu(10000000.0))
    }

    @Test
    fun `every group present at once`() {
        // 1,23,456 -> Ek Lakh Teis Hazar Chaar Sou Chhappan
        assertEquals(
            "Ek Lakh Teis Hazar Chaar Sou Chhappan Rupay",
            NumberWords.rupeesInWordsRomanUrdu(123456.0)
        )
    }

    @Test
    fun `only whole rupees, fractional part dropped`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWordsRomanUrdu(100.75))
    }

    @Test
    fun `negative is treated as zero rather than crashing`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWordsRomanUrdu(-500.0))
    }

    // ---- English wording, which composes rather than using a lookup ----

    @Test
    fun `english composes the irregular-looking twenties`() {
        assertEquals("Twenty-One Rupees", NumberWords.rupeesInWordsEnglish(21.0))
        assertEquals("Forty Rupees", NumberWords.rupeesInWordsEnglish(40.0))
        assertEquals("Ninety-Nine Rupees", NumberWords.rupeesInWordsEnglish(99.0))
    }

    @Test
    fun `english keeps south asian grouping, not millions`() {
        // Pakistani English on an invoice says Lakh, not Hundred Thousand.
        assertEquals("One Lakh Rupees", NumberWords.rupeesInWordsEnglish(100000.0))
        assertEquals(
            "One Lakh Twenty-Three Thousand Four Hundred Fifty-Six Rupees",
            NumberWords.rupeesInWordsEnglish(123456.0)
        )
    }

    @Test
    fun `english zero and negative behave like the roman urdu version`() {
        assertEquals("Zero Rupees", NumberWords.rupeesInWordsEnglish(0.0))
        assertEquals("Zero Rupees", NumberWords.rupeesInWordsEnglish(-500.0))
    }
    @Test
    fun westernGroupsForOtherCurrencies() {
        assertEquals("Five Thousand Two Hundred AED", NumberWords.spellWestern(5200.0, "AED"))
        assertEquals("One Million Two Hundred Thirty-Four Thousand Five Hundred Sixty-Seven USD",
            NumberWords.spellWestern(1234567.0, "USD"))
        assertEquals("Zero EUR", NumberWords.spellWestern(0.0, "EUR"))
        assertEquals("Ninety-Nine SAR", NumberWords.spellWestern(99.9, "SAR"))
    }
}
