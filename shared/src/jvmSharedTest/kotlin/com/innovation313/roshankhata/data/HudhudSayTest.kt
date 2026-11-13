package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the phone's voice is given (13 Nov 2026, the owner: "apna naam bhi sahi tarah bolna nahi aata… professional
 * sound… gap kabhi kam kabhi zyada"): his name the way each language's voice says it best, calm full stops, the shop's
 * rupees in words and a range's dash as "to".
 */
class HudhudSayTest {

    @Test fun `his name is said his way in each language`() {
        assertEquals("میں ہُود ہُود ہوں۔", HudhudSay.forVoice("میں ہُدہُد ہوں۔", "ur"))
        assertEquals("میں ہُود ہُود ہوں۔", HudhudSay.forVoice("میں ہدہد ہوں۔", "ur"))
        assertEquals("I'm Hood-hood.", HudhudSay.forVoice("I'm Hudhud.", "en"))
        assertEquals("मैं हुद-हुद हूँ।", HudhudSay.forVoice("मैं हुदहुद हूँ।", "hi"))
        assertEquals("আমি হুদ-হুদ।", HudhudSay.forVoice("আমি হুদহুদ।", "bn"))
        assertEquals("Saya Hud-hud.", HudhudSay.forVoice("Saya Hudhud.", "in"))
        assertEquals("أنا هُدْهُد.", HudhudSay.forVoice("أنا هُدهُد.", "ar"))
    }

    @Test fun `the way the owner chose is the one said`() {
        assertEquals("میں ہُد ہُد ہوں۔", HudhudSay.forVoice("میں ہُدہُد ہوں۔", "ur", nameWay = 1))
        // A way the language does not have falls back to its first.
        assertEquals("I'm Hood-hood.", HudhudSay.forVoice("I'm Hudhud.", "en", nameWay = 9))
        assertEquals(3, HudhudSay.nameWays("ur").size)
    }

    @Test fun `a word that only holds his name is not his name`() {
        assertEquals("Hudhudi and xHudhud", HudhudSay.forVoice("Hudhudi and xHudhud", "en"))
    }

    @Test fun `exclamations are said as calm stops`() {
        assertEquals("Wa alaikum assalam. All well.", HudhudSay.forVoice("Wa alaikum assalam! All well!", "en"))
        assertEquals("Wait. And then.", HudhudSay.forVoice("Wait… And then.", "en"))
    }

    @Test fun `the shop's rupees are said in words, other money is left as written`() {
        val rupees: (String) -> String = { "$it rupees" }
        assertEquals("Today's sale: 12400 rupees.", HudhudSay.forVoice("Today's sale: Rs 12,400.", "en", rupees = rupees))
        assertEquals("Paid 1250.50 rupees", HudhudSay.forVoice("Paid ₹1,250.50", "en", rupees = rupees))
        assertEquals("Rp 5.000", HudhudSay.forVoice("Rp 5.000", "in", rupees = rupees))
        // The whole figure, never broken at its grouping marks (13 Nov 2026, the owner's phone said "Rs 141,768" as 141 and 768).
        assertEquals("آج کی بکری 159426 روپے", HudhudSay.forVoice("آج کی بکری Rs 159,426", "ur", rupees = { "$it روپے" }))
        assertEquals("141768 رپيا", HudhudSay.forVoice("Rs 141,768", "sd", rupees = { "$it رپيا" }))
        assertEquals("159426 rupees", HudhudSay.forVoice("Rs 1,59,426", "en", rupees = rupees))
        assertEquals("500 rupees", HudhudSay.forVoice("Rs 500.00", "en", rupees = rupees))
        assertEquals("1250.50 rupees", HudhudSay.forVoice("Rs 1.250,50", "in", rupees = rupees))
        assertEquals("1000000 rupees", HudhudSay.forVoice("Rs 1,000,000", "en", rupees = rupees))
        assertEquals("SAR 12400 and 1218 customers", HudhudSay.forVoice("SAR 12,400 and 1,218 customers", "en"))
        // "Rs" inside a word is no money.
        assertEquals("Mrs 5", HudhudSay.forVoice("Mrs 5", "en", rupees = rupees))
    }

    @Test fun `a range's dash is said as the language's to`() {
        assertEquals("Likely 8 Nov to 12 Nov.", HudhudSay.forVoice("Likely 8 Nov – 12 Nov.", "en"))
        assertEquals("8 نومبر سے 12 نومبر", HudhudSay.forVoice("8 نومبر – 12 نومبر", "ur"))
    }
}
