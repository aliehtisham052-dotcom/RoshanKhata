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

    @Test fun sentToYouIsIncoming() {
        // "Successfully sent" alone is money going out (the review's P1); sent TO YOU is money coming in.
        assertEquals(500.0, p("Rs 500 has been successfully sent to you by Ahmed Ali")!!.amount, 0.001)
        assertEquals(750.0, p("Money sent to your account: Rs 750. TID 123123123123")!!.amount, 0.001)
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

    // ------------------------------------------------------------------ money that has not come in (the review's P1)

    @Test fun aRequestToPayIsNotAPayment() {
        assertNull(p("Ali Khan has sent you a request of Rs. 2,000. Tap to pay."))
        assertNull(p("You have received a payment request of Rs 3,000 from Bilal Ahmed"))
        assertNull(p("Request received: Bilal Ahmed requested Rs 3,000 from you. Tap to pay"))
    }

    @Test fun anAdvertIsNotAPayment() {
        assertNull(p("Receive up to Rs 5,000 instantly in your JazzCash account. Apply for a loan now!"))
        assertNull(p("Deposit Rs 1,000 in your Easypaisa account and win a free bike!"))
        assertNull(p("Get your salary credited in JazzCash and enjoy Rs 500 off on Daraz"))
    }

    @Test fun aFailedOrPendingTransferIsNotAPayment() {
        assertNull(p("Failed: Rs 5,000 could not be credited to your account."))
        assertNull(p("Transaction of Rs 500 from Ali could not be received. Please try again."))
        assertNull(p("Your transfer of Rs 5,000 failed. Amount will be credited back to your account."))
        assertNull(p("Rs 5,000 will be credited to your account within 2 working days"))
        assertNull(p("Rs 2,000 received from Ahmed is pending"))
        assertNull(p("Rs 700 from Asif couldn’t be credited to your account"))
        assertNull(p("Rs 900 credited from Asif was reversed"))
    }

    @Test fun aRefundOrALoanIsNotACustomersPayment() {
        assertNull(p("Refund of Rs 1,200 has been credited to your account"))
        assertNull(p("Your loan of Rs 10,000 has been credited to your account"))
    }

    @Test fun aBillALoadOrAnOrderIsNotAPayment() {
        assertNull(p("Your payment of Rs 1,500.00 for K-Electric bill has been received."))
        assertNull(p("Easyload of Rs 100 for 03001234567 received."))
        assertNull(p("Your order of Rs 2,000 has been received by Daraz"))
    }

    @Test fun urduCashbackAndAFutureAreNotAPayment() {
        assertNull(p("آپ کو 500 روپے کیش بیک ملے گا"))
        assertNull(p("اپنے اکاؤنٹ میں 1000 روپے جمع کروائیں اور انعام جیتیں"))
        assertNull(p("آپ کی 2000 روپے کی درخواست موصول ہوئی"))
    }

    @Test fun romanUrduFutureIsNotAPayment() {
        assertNull(p("Aap ko Rs 500 cashback mile ga"))
        assertNull(p("Aap ko Rs 500 mile ga"))
        assertNull(p("Aap ke account mein Rs 500 wusool ho jayenge"))
    }

    @Test fun aHiddenMarkDoesNotHideARequest() {
        assertNull(p("You have received a payment re\u200Bquest of Rs 3,000 from Bilal Ahmed"))
    }

    @Test fun urduWordsStandAlone() {
        // "جمعہ" (Friday) holds "جمع" (deposited) but is not it.
        assertNull(p("جمعہ کو 500 روپے"))
        assertEquals(3000.0, p("آپ کے اکاؤنٹ میں 3,000 روپے جمع ہو گئے")!!.amount, 0.001)
    }

    @Test fun moneySentOutIsNotAPayment() {
        assertNull(p("Money sent! Rs 1,000 to Ali Raza 0300****567 received successfully. TID 556677889900"))
        assertNull(p("PKR 5,000 transferred from your A/C ****1234 to ALI RAZA has been credited to the beneficiary. Ref FT26309XYZ12"))
        assertNull(p("Funds transfer of PKR 5,000 to ALI RAZA successful. Beneficiary credited."))
        assertNull(p("Your funds transfer of Rs 2,000 to 03001234567 has been received by the beneficiary"))
        assertNull(p("Debit card: -Rs 500 at Daraz. Payment received by merchant"))
    }

    // ------------------------------------------------------------------ the sender's number (the review's P1)

    @Test fun aNumberOutsideTheSendersPartIsNotHis() {
        val r = p("You have received Rs 500 from Ahmed Ali via Raast. Call 03111222333 for help")!!
        assertEquals("Ahmed Ali", r.sender)
        assertNull(r.phone)
    }

    @Test fun aNumberAfterToIsNotTheSenders() {
        assertNull(p("Rs 500 received from your A/C to 03001234567")!!.phone)
    }

    @Test fun romanUrduSendersNumberIsBeforeSe() {
        assertEquals("03001234567", p("Aap ko 03001234567 se Rs 1,500 mile")!!.phone)
    }

    @Test fun urduSendersNumberIsBeforeSe() {
        val r = p("آپ کو احمد 0300*234567 سے 5000 روپے موصول ہوئے")!!
        assertEquals("0300*234567", r.phone)
        assertEquals(5000.0, r.amount, 0.001)
    }

    @Test fun anInitialDoesNotEndTheSendersPart() {
        assertEquals("03001234567", p("Received Rs 500 from M. Aslam 03001234567 via IBFT")!!.phone)
    }

    // ------------------------------------------------------------------ figures and long texts (the review's P5)

    @Test fun aDotGroupedFigureIsNoAmount() {
        assertNull(p("You have received Rs 5.000 from Ahmed"))
    }

    @Test fun aFigureRunningIntoLettersIsNoAmount() {
        assertNull(p("Rs 1e12 received"))
    }

    @Test fun aMinusIsNeverAPayment() {
        assertNull(p("-Rs 500 received from Ahmed"))
        assertNull(p("-500 PKR received from Ahmed"))
        assertNull(p("\u2212Rs 500 received from Ahmed"))
    }

    @Test fun aCommaAfterAFigureStillEndsIt() {
        assertEquals(500.0, p("Received Rs 500, from Ahmed")!!.amount, 0.001)
    }

    @Test fun aLongRunOfSpacesIsReadQuickly() {
        val started = System.nanoTime()
        val r = p("You have received Rs 500 from Ahmed" + " ".repeat(20_000) + "#")
        val ms = (System.nanoTime() - started) / 1_000_000
        assertEquals(500.0, r!!.amount, 0.001)
        // Twelve seconds at 15,000 characters before: a generous bound, not a benchmark.
        assertTrue("took $ms ms", ms < 3_000)
    }

    @Test fun aLongChainOfGroupsDoesNotOverflow() {
        assertNull(p("received Rs 1" + ",000".repeat(5_000) + "x"))
        assertNull(p("received Rs " + "9".repeat(5_000)))
    }

    @Test fun manyFromsAreReadQuickly() {
        val started = System.nanoTime()
        p("received Rs 500 " + "from a ".repeat(6_000) + "#")
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test fun tidyCollapsesSpacesAndCutsTheText() {
        assertEquals("a b\nc", PaymentNotice.tidy("  a \t\u00A0 b\n\n \r\n c  "))
        assertEquals("request", PaymentNotice.tidy("re\u200Bquest"))
        assertEquals(PaymentNotice.MAX_TEXT, PaymentNotice.tidy("ab ".repeat(2_000)).length)
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

    @Test fun threeShownDigitsFindNobody() {
        // "0300****567": the network code and three digits, which thousands of numbers share (the review's P4).
        assertFalse(PaymentNotice.phoneMatches("0300****567", "03001234567"))
        val m = match(IncomingPayment(500.0, sender = "SOMEONE", phone = "0300****567"))
        assertNull(m.one)
        assertTrue(m.candidates.isEmpty())
    }

    @Test fun sixShownSubscriberDigitsFindTheOne() {
        assertEquals("Ahmed Ali", match(IncomingPayment(500.0, phone = "0300*234567")).one?.name)
    }

    @Test fun plusNinetyTwoStoredMatchesLocal() {
        assertEquals("Bilal Khan", match(IncomingPayment(500.0, phone = "03217654321")).one?.name)
    }

    @Test fun maskedDigitThatDisagreesIsNoMatch() {
        assertFalse(PaymentNotice.phoneMatches("0300****568", "03001234567"))
        assertFalse(PaymentNotice.phoneMatches("0300*234568", "03001234567"))
    }

    @Test fun tooFewKnownDigitsIsNoMatch() {
        assertFalse(PaymentNotice.phoneMatches("03********7", "03001234567"))
    }

    @Test fun theNetworkCodeIsNotCounted() {
        // Eight digits shown, but four of them are the code: four of the subscriber's own are not enough.
        assertFalse(PaymentNotice.phoneMatches("0300***4567", "03001234567"))
    }

    @Test fun sixShownSubscriberDigitsAreEnough() {
        assertTrue(PaymentNotice.phoneMatches("0300*234567", "03001234567"))
        assertTrue(PaymentNotice.phoneMatches("0300*234567", "923001234567"))
        assertTrue(PaymentNotice.phoneMatches("03001234567", "+92 300 1234567"))
    }

    @Test fun aWholeNumberWithAnotherCodeIsNoMatch() {
        assertFalse(PaymentNotice.phoneMatches("03451234567", "03001234567"))
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

    @Test fun aNumberAndANameThatDisagreeAreAQuestion() {
        // The notice names Bilal but the number is Ahmed's (the review's P4): the owner chooses between them.
        val m = match(IncomingPayment(500.0, sender = "Bilal", phone = "03001234567"))
        assertNull(m.one)
        assertEquals(setOf("Ahmed Ali", "Bilal Khan"), m.candidates.map { it.name }.toSet())
    }

    @Test fun aNumberAndItsOwnNameDecide() {
        assertEquals("Ahmed Ali", match(IncomingPayment(500.0, sender = "AHMED ALI", phone = "03001234567")).one?.name)
        assertEquals("Ahmed Ali", match(IncomingPayment(500.0, sender = "Muhammad Ahmed", phone = "03001234567")).one?.name)
    }

    @Test fun aNumberWithNoNameDecides() {
        assertEquals("Ahmed Ali", match(IncomingPayment(500.0, phone = "03001234567")).one?.name)
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
