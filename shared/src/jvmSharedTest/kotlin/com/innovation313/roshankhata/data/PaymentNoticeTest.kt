package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a payment notification may be read as. The texts are invented in the common shapes of wallet and bank
 * notices — not the owner's real JazzCash/Easypaisa screenshots yet, which are to be added here as they come.
 * Every name and number below is made up.
 */
class PaymentNoticeTest {

    private fun p(text: String, title: String? = null, big: String? = null) = PaymentNotice.parse(title, text, big)

    // ------------------------------------------------------------------ incoming, English

    @Test fun walletReceivedFromNameWithMaskedNumberAndTid() {
        val r = p("You have received Rs. 2,500.00 from MUHAMMAD ASLAM 0300****567 via JazzCash. TID: 012345678901")
        assertNotNull(r)
        assertEquals(2500.0, r!!.amount, 0.001)
        assertEquals("Muhammad Aslam", r.sender)
        assertEquals("0300****567", r.phone)
        assertEquals("012345678901", r.reference)
    }

    @Test fun receivedPkrFromNameStopsAtOn() {
        val r = p("Received PKR 5,000 from Ahmed Ali on 05-11-2026. Trx ID 8761234567")!!
        assertEquals(5000.0, r.amount, 0.001)
        assertEquals("Ahmed Ali", r.sender)
        assertEquals("8761234567", r.reference)
    }

    @Test fun titleAndTextAreReadTogether() {
        val r = p(title = "Money Received", text = "Rs 1,200 from Bilal Khan. Ref 99887766")!!
        assertEquals(1200.0, r.amount, 0.001)
        assertEquals("Bilal Khan", r.sender)
        assertEquals("99887766", r.reference)
    }

    @Test fun bigTextThatRepeatsTheShortOneIsTheWhole() {
        val r = p(text = "You received Rs 750", big = "You received Rs 750 from Sana Traders. TID 445566778899")!!
        assertEquals(750.0, r.amount, 0.001)
        assertEquals("Sana Traders", r.sender)
        assertEquals("445566778899", r.reference)
    }

    @Test fun bankCreditedToYourAccount() {
        val r = p("Your A/C ****1234 has been credited with PKR 12,000.00 from USMAN GHANI. Ref No. FT26309XYZ12")!!
        assertEquals(12000.0, r.amount, 0.001)
        assertEquals("Usman Ghani", r.sender)
        assertEquals("FT26309XYZ12", r.reference)
    }

    @Test fun transferredToYourAccountIsIncoming() {
        val r = p("Rs 3,000 has been transferred to your account from Zahid Iqbal. Transaction ID 7766554433")!!
        assertEquals(3000.0, r.amount, 0.001)
        assertEquals("Zahid Iqbal", r.sender)
    }

    @Test fun sentYouIsIncoming() {
        val r = p("Kamran sent you Rs 900. TID 123123123123")!!
        assertEquals(900.0, r.amount, 0.001)
        assertNull(r.sender) // no "from" and no "se": the name is not guessed from the sentence's start
    }

    @Test fun paidYouIsIncoming() {
        assertEquals(450.0, p("Imran paid you Rs 450 via Easypaisa")!!.amount, 0.001)
    }

    @Test fun depositIsIncoming() {
        assertEquals(20000.0, p("Cash deposit of Rs 20,000 in your account. Ref 55443322")!!.amount, 0.001)
    }

    @Test fun recievedMisspeltIsIncoming() {
        assertEquals(600.0, p("Recieved Rs600 from Ali Raza")!!.amount, 0.001)
    }

    @Test fun currencyAfterTheFigure() {
        val r = p("You received 3,500 Rupees from Faisal Mehmood")!!
        assertEquals(3500.0, r.amount, 0.001)
        assertEquals("Faisal Mehmood", r.sender)
    }

    @Test fun pkrAfterTheFigureWithDecimals() {
        assertEquals(1250.5, p("1,250.50 PKR received from Nadeem")!!.amount, 0.001)
    }

    @Test fun lakhGroupingIsRead() {
        assertEquals(125000.0, p("Received Rs 1,25,000 from Haji Saleem")!!.amount, 0.001)
    }

    @Test fun balanceIsNeverTheAmount() {
        val r = p("You have received Rs 5,000 from Ahmed. Your new balance is Rs 18,250.00")!!
        assertEquals(5000.0, r.amount, 0.001)
    }

    @Test fun feeIsNeverTheAmount() {
        assertEquals(2000.0, p("Received Rs 2,000 from Asif. Fee Rs 0.00")!!.amount, 0.001)
    }

    @Test fun twoDifferentPaymentFiguresGiveNothing() {
        assertNull(p("Received Rs 2,000 from Asif and Rs 3,000 from Tariq"))
    }

    @Test fun sameFigureTwiceIsOneAmount() {
        assertEquals(800.0, p("Received Rs 800 from Waqas. Amount: Rs. 800")!!.amount, 0.001)
    }

    @Test fun plusNinetyTwoNumberIsRead() {
        val r = p("Received Rs 700 from +92 321 7654321 via Raast")!!
        assertEquals("03217654321", r.phone)
        assertNull(r.sender)
    }

    @Test fun plainLocalNumberIsRead() {
        assertEquals("03451112223", p("Rs 400 received from 03451112223")!!.phone)
    }

    @Test fun nameStopsAtVia() {
        assertEquals("Shahid Mahmood", p("Received Rs 100 from Shahid Mahmood via IBFT")!!.sender)
    }

    // ------------------------------------------------------------------ incoming, Roman Urdu and Urdu

    @Test fun romanUrduSeAaGaye() {
        val r = p("Ahmed Ali se 5000 rupay aa gaye")!!
        assertEquals(5000.0, r.amount, 0.001)
        assertEquals("Ahmed Ali", r.sender)
    }

    @Test fun romanUrduAapKoSeMile() {
        val r = p("Aap ko Bilal se Rs 1,500 mile. TID 667788990011")!!
        assertEquals(1500.0, r.amount, 0.001)
        assertEquals("Bilal", r.sender)
    }

    @Test fun urduWusoolWithName() {
        val r = p("آپ کو احمد علی سے 5000 روپے موصول ہوئے")!!
        assertEquals(5000.0, r.amount, 0.001)
        assertEquals("احمد علی", r.sender)
    }

    @Test fun urduDigitsAreRead() {
        val r = p("آپ کو اسلم سے ۲۵۰۰ روپے وصول ہوئے")!!
        assertEquals(2500.0, r.amount, 0.001)
        assertEquals("اسلم", r.sender)
    }

    @Test fun urduJamaWithoutName() {
        val r = p("آپ کے اکاؤنٹ میں 3,000 روپے جمع ہو گئے")!!
        assertEquals(3000.0, r.amount, 0.001)
        assertNull(r.sender)
    }

    // ------------------------------------------------------------------ outgoing: never read

    @Test fun sentToIsOutgoing() {
        assertNull(p("You have sent Rs 1,000 to Ahmed Ali 0300****567. TID 012345678901"))
    }

    @Test fun sentToPlainIsOutgoing() {
        assertNull(p("Rs 1,000 sent to Bilal Khan successfully"))
    }

    @Test fun paidIsOutgoing() {
        assertNull(p("You paid Rs 2,300 to Al-Noor Traders. Ref 11223344"))
    }

    @Test fun debitedIsOutgoing() {
        assertNull(p("Your account has been debited with PKR 4,000.00. Ref FT2630999"))
    }

    @Test fun transferredToSomeoneIsOutgoing() {
        assertNull(p("Rs 6,000 transferred to Zahid Iqbal from your account"))
    }

    @Test fun billPaymentIsOutgoing() {
        assertNull(p("Bill payment of Rs 3,450 received by LESCO"))
    }

    @Test fun purchaseIsOutgoing() {
        assertNull(p("Purchase of Rs 1,200 at Main Bazar Store on your card"))
    }

    @Test fun withdrawalIsOutgoing() {
        assertNull(p("Cash withdrawal of Rs 10,000 from ATM"))
    }

    @Test fun bhejeIsOutgoing() {
        assertNull(p("Aap ne Ahmed ko 2000 rupay bheje"))
    }

    @Test fun urduAdaIsOutgoing() {
        assertNull(p("آپ نے 2000 روپے ادا کیے"))
    }

    @Test fun urduBhejeIsOutgoing() {
        assertNull(p("آپ نے احمد کو 2000 روپے بھیجے"))
    }

    // ------------------------------------------------------------------ codes and noise: never read

    @Test fun otpIsNeverAPayment() {
        assertNull(p("Your OTP to receive Rs 5,000 is 482913. Do not share it."))
    }

    @Test fun verificationCodeIsNeverAPayment() {
        assertNull(p("Verification code 557711 for your JazzCash account"))
    }

    @Test fun pinIsNeverAPayment() {
        assertNull(p("Your PIN has been changed. Received request at 10:15"))
    }

    @Test fun urduCodeIsNeverAPayment() {
        assertNull(p("آپ کا کوڈ 123456 ہے، 500 روپے وصول کرنے کے لیے"))
    }

    @Test fun noCurrencyMeansNoAmount() {
        assertNull(p("You have received 5000 points from Daraz"))
    }

    @Test fun noIncomingWordMeansNothing() {
        assertNull(p("Rs 5,000 Ahmed Ali 0300****567"))
    }

    @Test fun promotionIsNotAPayment() {
        assertNull(p("Get Rs 100 cashback on your next purchase!"))
    }

    @Test fun emptyIsNothing() {
        assertNull(PaymentNotice.parse(null, null, null))
        assertNull(p("   "))
    }

    @Test fun phoneNumberIsNeverTheReference() {
        assertNull(p("Received Rs 500 from 03001234567. Ref: 03001234567")!!.reference)
    }

    // ------------------------------------------------------------------ the customer in the book

    private data class P(val name: String, val phone: String?)

    private val book = listOf(
        P("Ahmed Ali", "0300-1234567"),
        P("Bilal Khan", "+92 321 7654321"),
        P("Hassan Raza", null),
        P("Asghar Spray Wala", "03451112223"),
        P("Asghar Kiryana", "03459998887")
    )

    private fun match(payment: IncomingPayment) = PaymentNotice.match(book, payment, { it.name }, { it.phone })

    @Test fun maskedNumberFindsTheOne() {
        val m = match(IncomingPayment(500.0, sender = "SOMEONE", phone = "0300****567"))
        assertEquals("Ahmed Ali", m.one?.name)
    }

    @Test fun plusNinetyTwoStoredMatchesLocal() {
        assertEquals("Bilal Khan", match(IncomingPayment(500.0, phone = "03217654321")).one?.name)
    }

    @Test fun maskedDigitThatDisagreesIsNoMatch() {
        assertFalse(PaymentNotice.phoneMatches("0300****568", "03001234567"))
    }

    @Test fun tooFewKnownDigitsIsNoMatch() {
        assertFalse(PaymentNotice.phoneMatches("03********7", "03001234567"))
    }

    @Test fun sevenKnownDigitsAreEnough() {
        assertTrue(PaymentNotice.phoneMatches("0300****567", "03001234567"))
        assertTrue(PaymentNotice.phoneMatches("0300****567", "923001234567"))
    }

    @Test fun nameFindsTheOneWhenNoNumber() {
        assertEquals("Hassan Raza", match(IncomingPayment(500.0, sender = "Hassan Raza")).one?.name)
    }

    @Test fun twoAsgharsAreAQuestion() {
        val m = match(IncomingPayment(500.0, sender = "Asghar"))
        assertNull(m.one)
        assertEquals(2, m.candidates.size)
    }

    @Test fun unknownNameIsNobody() {
        val m = match(IncomingPayment(500.0, sender = "Zubair Tahir"))
        assertNull(m.one)
        assertTrue(m.candidates.isEmpty())
    }

    @Test fun noNameNoNumberIsNobody() {
        val m = match(IncomingPayment(500.0))
        assertNull(m.one)
        assertTrue(m.candidates.isEmpty())
    }

    @Test fun numberWinsOverAName() {
        // The notice names Bilal but the number is Ahmed's: the number decides, it identifies a person.
        assertEquals("Ahmed Ali", match(IncomingPayment(500.0, sender = "Bilal", phone = "03001234567")).one?.name)
    }

    // ------------------------------------------------------------------ repeats

    @Test fun samePaymentTwiceWithinTenMinutesIsARepeat() {
        val r = PaymentNotice.Repeats()
        assertFalse(r.isRepeat("pk.app", 500.0, "TID1", "a", now = 1_000_000L))
        assertTrue(r.isRepeat("pk.app", 500.0, "TID1", "a changed text", now = 1_000_000L + 60_000L))
    }

    @Test fun samePaymentAfterTenMinutesIsNew() {
        val r = PaymentNotice.Repeats()
        assertFalse(r.isRepeat("pk.app", 500.0, "TID1", "a", now = 0L))
        assertFalse(r.isRepeat("pk.app", 500.0, "TID1", "a", now = 11 * 60_000L))
    }

    @Test fun anotherReferenceIsAnotherPayment() {
        val r = PaymentNotice.Repeats()
        assertFalse(r.isRepeat("pk.app", 500.0, "TID1", "a", now = 0L))
        assertFalse(r.isRepeat("pk.app", 500.0, "TID2", "a", now = 1L))
    }

    @Test fun withoutReferenceTheTextDecides() {
        val r = PaymentNotice.Repeats()
        assertFalse(r.isRepeat("pk.app", 500.0, null, "Rs 500 from Ali", now = 0L))
        assertTrue(r.isRepeat("pk.app", 500.0, null, "Rs 500 from Ali", now = 5_000L))
        assertFalse(r.isRepeat("pk.app", 500.0, null, "Rs 500 from Umar", now = 6_000L))
    }

    @Test fun anotherAppIsAnotherPayment() {
        val r = PaymentNotice.Repeats()
        assertFalse(r.isRepeat("pk.one", 500.0, "TID1", "a", now = 0L))
        assertFalse(r.isRepeat("pk.two", 500.0, "TID1", "a", now = 1L))
    }
}
