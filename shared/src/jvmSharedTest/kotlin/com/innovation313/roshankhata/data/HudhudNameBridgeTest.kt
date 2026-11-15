package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HudhudNameBridge], one rule at a time (15 Nov 2026). The matcher is a pure function of two strings, so each test names
 * the rule it holds and is built so that only that rule can fail it; the long lists at the foot are common given names in
 * the spellings a shop writes and says, and hold the rules together. Every name here is a common given name: none is
 * anybody's record (lesson 80).
 *
 * The tests were run against deliberate breakages of the matcher (lesson 126): the table below the first test restates
 * the consonants once more, so a letter that goes missing from the matcher's own table, or a Latin spelling added to
 * one, is seen.
 *
 * 143 breakages were tried; eight change no answer and so no test can fail them, each for a reason that was read in the
 * code and checked on five million random pairs: the early outs of `meetsLetters` ask what the matcher asks again (so
 * does the `i > 0` before a "و" or a "ی", and the `i == 0 && k > 0` before the first consonant); no spelling of any
 * letter starts with a vowel and the gap before a consonant has already taken every vowel, so a vowel added to "و" or
 * "ی" is never met; and one Latin letter cannot be three Arabic letters. The mask in the memo's key matters in one
 * place only, a Sindhi "ڃ" before a "ی" (two spellings that end on the same Latin "y").
 */
class HudhudNameBridgeTest {

    private fun meets(latin: String, arabic: String) = HudhudNameBridge.meets(arabic, latin)

    private fun yes(latin: String, arabic: String) = assertTrue("$latin should meet $arabic", meets(latin, arabic))

    private fun no(latin: String, arabic: String) = assertFalse("$latin should not meet $arabic", meets(latin, arabic))

    /** The Latin spelling of "بم" twelve times (b, m, b, m...) with an "a" after each of the first [vowels] consonants. */
    private fun latinOf(vowels: Int): String =
        (0 until 24).joinToString("") { k -> (if (k % 2 == 0) "b" else "m") + (if (k < vowels) "a" else "") }

    /** What each consonant letter of the scripts may be written with in Latin, restated here from the letters' sounds. */
    private val consonants: Map<Char, List<String>> = mapOf(
        'ب' to listOf("b"), 'پ' to listOf("p"), 'ت' to listOf("t"), 'ٹ' to listOf("t"), 'ط' to listOf("t"),
        'ث' to listOf("s", "th"), 'ج' to listOf("j"), 'چ' to listOf("ch"), 'ح' to listOf("h"),
        'خ' to listOf("kh", "k"), 'د' to listOf("d"), 'ڈ' to listOf("d"), 'ذ' to listOf("z", "dh"),
        'ر' to listOf("r"), 'ڑ' to listOf("r"), 'ز' to listOf("z"), 'ژ' to listOf("zh", "z", "j"),
        'س' to listOf("s"), 'ص' to listOf("s"), 'ش' to listOf("sh"), 'ض' to listOf("z", "d"), 'ظ' to listOf("z"),
        'غ' to listOf("gh", "g"), 'ف' to listOf("f"), 'ق' to listOf("q", "k"), 'ک' to listOf("k", "c"),
        'گ' to listOf("g"), 'ل' to listOf("l"), 'م' to listOf("m"), 'ن' to listOf("n"),
        // Sindhi's own letters.
        'ٻ' to listOf("b"), 'ڀ' to listOf("bh", "b"), 'ٽ' to listOf("t"), 'ٿ' to listOf("th", "t"),
        'ڊ' to listOf("d"), 'ڏ' to listOf("d"), 'ڌ' to listOf("dh", "d"), 'ڍ' to listOf("dh", "d"),
        'ڙ' to listOf("r"), 'ڳ' to listOf("g"), 'ڱ' to listOf("ng", "n"), 'ڄ' to listOf("j"),
        'ڃ' to listOf("ny", "n"), 'ڇ' to listOf("chh", "ch"), 'ڦ' to listOf("ph", "f")
    )

    // ---- the consonants ----

    @Test
    fun everyConsonantIsMetByItsOwnLettersAndByNoOtherConsonant() {
        val alphabet = "bcdfghjklmnpqrstvwxyz"
        for ((c, own) in consonants) {
            // A frame that is never the letter itself, with no vowel in it: the bare skeleton of a three-letter word.
            val frame = if (c == 'م') 'ل' else 'م'
            val frameLatin = if (c == 'م') "l" else "m"
            val arabic = "$frame$c$frame"
            for (o in own) assertTrue("$c should be written $o", meets("$frameLatin$o$frameLatin", arabic))
            for (x in alphabet) {
                if (x.toString() in own) continue
                assertFalse("$c should not be written $x", meets("$frameLatin$x$frameLatin", arabic))
            }
        }
    }

    @Test
    fun theHeIsAnHAndNoOtherLetter() {
        yes("Mehr", "مہر")
        yes("Sohail", "سہیل")
        yes("Shahid", "شاہد")
        yes("Hajra", "ہاجرہ")
        for (x in "bcdfgjklmnpqrstvwxyz") no("m${x}m", "مہم")
        // ... nor a letter pair that has an h in it: that is what the other letters say ("kh" is "خ", "sh" is "ش").
        for (x in listOf("kh", "sh", "ch", "gh", "th", "dh", "ph")) no("m${x}m", "مہم")
        no("Khalid", "ہالد")
    }

    // ---- the vowels ----

    @Test
    fun aShortVowelHasNoLetterSoAnyVowelMayStandBetweenTwoConsonants() {
        yes("Muhammad", "محمد")
        yes("Mohammed", "محمد")
        yes("Mahmud", "محمد")
        yes("Mhmd", "محمد")
        yes("Mohamed", "محمد")
        yes("Kamal", "کمل")
    }

    @Test
    fun aWordThatStartsWithAConsonantDoesNotStartWithAVowelInLatin() {
        no("Amhmd", "محمد")
        no("Ahmad", "حمد")
        no("Umar", "مر")
        yes("Hamad", "حمد")
    }

    @Test
    fun aWordThatStartsWithAVowelLetterStartsWithAVowelInLatin() {
        yes("Ahmad", "احمد")
        yes("Umar", "عمر")
        yes("Imran", "عمران")
        yes("Irfan", "عرفان")
        yes("Ibrahim", "ابراہیم")
        yes("Asif", "آصف")
        yes("Ayesha", "عائشہ")
        yes("Iman", "ایمان")
        no("Hamad", "احمد")
        no("Mar", "عمر")
        no("Bahmad", "احمد")
    }

    @Test
    fun aWrittenLongVowelNeedsAVowelWhereItIsWritten() {
        // "بشار" has a long vowel after sh: a Latin spelling with nothing between sh and r does not have it.
        no("Bshr", "بشار")
        yes("Bashar", "بشار")
        no("Bshr", "بشیر")
        no("Nr", "نور")
        // The "ع" asks for a vowel and nothing more: "Saad" is "Sad" once the doubled letter is one.
        yes("Saad", "سعد")
        yes("Sud", "سعد")
        no("Sd", "سعد")
    }

    @Test
    fun aYehInTheMiddleIsALongIOrE() {
        yes("Bashir", "بشیر")
        yes("Basheer", "بشیر")
        yes("Rasheed", "رشید")
        no("Bashar", "بشیر")
        no("Bushra", "بشیر")
        no("Bashur", "بشیر")
        no("Bashor", "بشیر")
    }

    @Test
    fun aYehAtTheEndMayBeAnAAsWellAsAnIOrAnE() {
        yes("Mustafa", "مصطفی")
        yes("Mustafi", "مصطفی")
        yes("Musa", "موسی")
        yes("Bushra", "بشری")
        yes("Yahya", "یحییٰ")
        no("Mustafu", "مصطفی")
        no("Mustafo", "مصطفی")
        // An "a" does not do in the middle of a word.
        no("Bashar", "بشیر")
    }

    @Test
    fun aYehIsAConsonantYWhereTheLatinSpellingHasOne() {
        yes("Maryam", "مریم")
        yes("Mariam", "مریم")
        yes("Ilyas", "الیاس")
        yes("Zakariya", "زکریا")
        no("Marm", "مریم")
    }

    @Test
    fun aYehThatStartsTheWordIsAYAndNothingElse() {
        yes("Younas", "یونس")
        yes("Yaqoob", "یعقوب")
        yes("Yusuf", "یوسف")
        no("Unus", "یونس")
        no("Ounas", "یونس")
    }

    @Test
    fun aWawInTheMiddleIsALongUOrO() {
        yes("Noor", "نور")
        yes("Nur", "نور")
        yes("Hassoon", "حسون")
        no("Nar", "نور")
        no("Nir", "نور")
        no("Nr", "نور")
    }

    @Test
    fun aWawIsAConsonantWOrVWhereTheLatinSpellingHasOne() {
        yes("Anwar", "انور")
        yes("Naveed", "نوید")
        yes("Rizwan", "رضوان")
        yes("Dawood", "داود")
        yes("Owais", "اویس")
        no("Anar", "انور")
    }

    @Test
    fun aWawThatStartsTheWordIsAWOrAVAndNothingElse() {
        yes("Waseem", "وسیم")
        yes("Wasim", "وسیم")
        yes("Waheed", "وحید")
        yes("Vasim", "وسیم")
        no("Asim", "وسیم")
        no("Usim", "وسیم")
    }

    @Test
    fun anAlifInsideAWordIsAnA() {
        yes("Bashar", "بشار")
        yes("Mehtab", "مہتاب")
        yes("Zahid", "زاہد")
        no("Bashir", "بشار")
        no("Mehtib", "مہتاب")
        no("Mehtob", "مہتاب")
        no("Zuhid", "زاہد")
    }

    @Test
    fun theSilentAlifOfTheArticleSaysNothing() {
        yes("Abdullah", "عبداللہ")
        yes("Abdulkareem", "عبدالکریم")
        yes("Abdulrahman", "عبدالرحمن")
        yes("Asadullah", "اسداللہ")
        yes("Rahmatullah", "رحمتاللہ")
        // The same place in a word that is not such a name is still an "a": the exemption is for these words only.
        no("Mehtib", "مہتاب")
        no("Zuhid", "زاہد")
    }

    @Test
    fun abdurIsNotMetAsOneWordWithTheNameAfterIt() {
        // Known gap, said on purpose: "Abdur Rahman" is written "عبدالرحمن" and its lam is not heard.
        no("Abdurrahman", "عبدالرحمن")
    }

    // ---- the final hah ----

    @Test
    fun aHehAtTheEndIsAnHOrAVowelEnding() {
        yes("Sarah", "سارہ")
        yes("Sara", "سارہ")
        yes("Shazia", "شازیہ")
        yes("Fatima", "فاطمہ")
        yes("Aamna", "آمنہ")
        yes("Amina", "امینہ")
        yes("Rashida", "راشدہ")
        yes("Nasira", "ناصرہ")
        yes("Hamza", "حمزہ")
        yes("Zahra", "زہرا")
    }

    @Test
    fun aNameThatEndsOnAConsonantIsNotAHehEndingNoSilentLetterSaysIt() {
        no("Aman", "آمنہ")
        no("Aman", "امنہ")
        no("Amin", "امینہ")
        no("Rashid", "راشدہ")
        no("Bashir", "بشیرہ")
        no("Nasir", "ناصرہ")
        no("Fatim", "فاطمہ")
    }

    @Test
    fun aHehInTheMiddleIsNeverSilent() {
        no("Sail", "سہیل")
        no("Mer", "مہر")
        no("Zara", "زہرا")
    }

    // ---- doubled letters, lengths, unknown letters ----

    @Test
    fun doubledLettersAreOne() {
        yes("Abdullah", "عبداللہ")
        yes("Hussain", "حسین")
        yes("Muhammad", "محمد")
        yes("Mohammmad", "محمد")
        assertEquals("ب", HudhudNameBridge.arabicLetters("ببب"))
        assertEquals("abdulah", HudhudNameBridge.latinLetters("Abdullah"))
        assertEquals("husain", HudhudNameBridge.latinLetters("Hussain"))
    }

    @Test
    fun fewerThanThreeLettersIsAWordNotAName() {
        no("Am", "ام")
        no("Ali", "عل")
        no("Ama", "ام")
        no("Gul", "گل")
        yes("Amr", "امر")
        yes("Gulb", "گلب")
    }

    @Test
    fun aTwoLetterLatinNameIsMetButASingleLetterNeverIs() {
        yes("Om", "اوم")
        no("O", "اوم")
        no("M", "اوم")
    }

    @Test
    fun aWordOfOnlyLongVowelsIsNotAName() {
        no("Aoi", "اوی")
        no("Aaoi", "آوی")
    }

    @Test
    fun anArabicWordOfMoreThanTwentyFourLettersIsNotAName() {
        val twentyFour = "بم".repeat(12)
        val twentySix = "بم".repeat(13)
        // Twenty-four consonants and sixteen vowels between them: forty Latin letters, the longest a name may have.
        val forty = latinOf(vowels = 16)
        assertEquals(40, HudhudNameBridge.latinLetters(forty).length)
        yes("bm".repeat(12), twentyFour)
        no("bm".repeat(13), twentySix)
        yes(forty, twentyFour)
    }

    @Test
    fun aLatinSpellingOfMoreThanFortyLettersIsNotAName() {
        val twentyFour = "بم".repeat(12)
        val fortyOne = latinOf(vowels = 17)
        assertEquals(41, HudhudNameBridge.latinLetters(fortyOne).length)
        no(fortyOne, twentyFour)
    }

    @Test
    fun aLetterThatIsNotKnownFailsTheWholeWord() {
        no("Banu", "بݨو")
        no("Nabu", "ݨبو")
        no("Banu", "بنݨ")
    }

    @Test
    fun aLongWordIsAnsweredAtOnce() {
        val started = System.nanoTime()
        val a = "بم".repeat(12)
        val l = latinOf(vowels = 16)
        repeat(200) { assertTrue(HudhudNameBridge.meets(a, l)) }
        repeat(200) { assertFalse(HudhudNameBridge.meets(a, l.replace('m', 'n'))) }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue("took $seconds s", seconds < 10)
    }

    // ---- a spelling that fails later does not hide the other spelling of the same letter ----

    @Test
    fun aSpellingThatFailsLaterDoesNotHideTheOtherSpellingOfTheSameLetter() {
        // "خ" is "kh" first. "Sheikh" then has no h left for the "ہ" that ends "شیخہ"; the second spelling, "k", leaves it one.
        yes("Sheikh", "شیخہ")
        yes("Akh", "اخہ")
        yes("Zkh", "زخہ")
        // The same with "غ" ("gh", then "g").
        yes("Bgh", "بغہ")
        // Sindhi's "ڃ" is "ny" first; the "ی" that follows may be the "y" of "n" + "y", and then it is a letter and asks nothing.
        yes("Nyn", "ڃین")
    }

    // ---- how the letters are read ----

    @Test
    fun theMarksAreTakenOffAnArabicWord() {
        assertEquals("محمد", HudhudNameBridge.arabicLetters("مُحَمَّد"))
        assertEquals("احمد", HudhudNameBridge.arabicLetters("اَحْمَد"))
        assertEquals("کریم", HudhudNameBridge.arabicLetters("كـريم"))
        assertEquals("علی", HudhudNameBridge.arabicLetters("علي٣"))
    }

    @Test
    fun everyFormOfALetterIsMadeTheOneUrduForm() {
        assertEquals("علی", HudhudNameBridge.arabicLetters("علي"))
        assertEquals("مصطفی", HudhudNameBridge.arabicLetters("مصطفى"))
        assertEquals("کریم", HudhudNameBridge.arabicLetters("كريم"))
        assertEquals("فاطمہ", HudhudNameBridge.arabicLetters("فاطمة"))
        assertEquals("فاطمہ", HudhudNameBridge.arabicLetters("فاطمۃ"))
        assertEquals("احمد", HudhudNameBridge.arabicLetters("أحمد"))
        assertEquals("ابراہیم", HudhudNameBridge.arabicLetters("إبراهيم"))
        assertEquals("ایمان", HudhudNameBridge.arabicLetters("ایمان"))
        assertEquals("اصف", HudhudNameBridge.arabicLetters("آصف"))
        assertEquals("مومن", HudhudNameBridge.arabicLetters("مؤمن"))
        assertEquals("جان", HudhudNameBridge.arabicLetters("جاں"))
        assertEquals("عبدالہ", HudhudNameBridge.arabicLetters("عبدالله"))
    }

    @Test
    fun theSpellingsOfOtherScriptsMeetTheSameName() {
        yes("Ali", "علي")
        yes("Hossein", "حسين")
        yes("Mustafa", "مصطفى")
        yes("Fatima", "فاطمة")
        yes("Karim", "كريم")
        yes("Ibrahim", "إبراهيم")
        yes("Abdullah", "عبدالله")
    }

    @Test
    fun aLatinSpellingIsReadAsPlainLettersOnly() {
        assertEquals("jose", HudhudNameBridge.latinLetters("José"))
        assertEquals("zoe", HudhudNameBridge.latinLetters("Zoë"))
        assertEquals("aliraza", HudhudNameBridge.latinLetters("Ali-Raza"))
        assertEquals("oneil", HudhudNameBridge.latinLetters("O'Neil"))
        assertEquals("ali", HudhudNameBridge.latinLetters("ALI2"))
        yes("Zoe", "زوی")
        yes("Ali-Raza", "علیرضا")
    }

    @Test
    fun anArabicWordOfTwentyFiveLettersIsNotAName() {
        val twentyFive = "بم".repeat(12) + "ب"
        assertEquals(25, HudhudNameBridge.arabicLetters(twentyFive).length)
        no("bm".repeat(12) + "b", twentyFive)
    }

    @Test
    fun aLatinLetterLeftOverIsNotMetButAVowelAtTheEndIs() {
        no("Bashirs", "بشیر")
        no("Alis", "علی")
        no("Hamdan", "حمد")
        no("Hamds", "حمد")
        yes("Hamda", "حمد")
    }

    @Test
    fun everyOtherFormOfALetterIsMadeTheUrduFormOnItsOwn() {
        val forms = mapOf(
            'أ' to 'ا', 'إ' to 'ا', 'ٱ' to 'ا', 'آ' to 'ا', 'ؤ' to 'و',
            'ي' to 'ی', 'ى' to 'ی', 'ې' to 'ی', 'ۍ' to 'ی', 'ێ' to 'ی', 'ئ' to 'ی', 'ے' to 'ی', 'ۓ' to 'ی',
            'ك' to 'ک', 'ڪ' to 'ک',
            'ه' to 'ہ', 'ۀ' to 'ہ', 'ة' to 'ہ', 'ە' to 'ہ', 'ۃ' to 'ہ', 'ھ' to 'ہ',
            'ں' to 'ن', 'ڻ' to 'ن'
        )
        for ((from, to) in forms) {
            assertEquals("$from should be read as $to", to.toString(), HudhudNameBridge.arabicLetters(from.toString()))
        }
        // Not letters: a vowel sign, the joiner, a digit, a space and a stop are taken away.
        for (mark in listOf('\u064E', '\u0651', '\u0670', '\u200D', '\u0640', '3', '٣', ' ', '.', '-')) {
            assertEquals("", HudhudNameBridge.arabicLetters(mark.toString()))
        }
    }

    @Test
    fun aYIsNeverAVowelAndEveryLatinVowelIsOne() {
        yes("Ayub", "ایوب")
        yes("Ayaan", "ایان")
        yes("Umar", "عمر")
        yes("Omar", "عمر")
        yes("Imran", "عمران")
        yes("Ejaz", "اعجاز")
        no("Ymar", "عمر")
    }

    // ---- near names are told apart ----

    @Test
    fun nearNamesAreTold() {
        no("Bushra", "بشیر")
        no("Bashir", "بشری")
        no("Bashar", "بشیر")
        no("Bashir", "بشار")
        no("Basheer", "بشار")
        no("Asim", "وسیم")
        no("Ahmad", "حمد")
        no("Hamad", "احمد")
        no("Salam", "اسلم")
        no("Kamil", "کریم")
        no("Noor", "نیر")
        no("Ali", "رضا")
        no("Hasan", "حسین")
    }

    // ---- what is let through on purpose: the bare skeleton says nothing of the vowels ----

    @Test
    fun aNameWhoseLongVowelsAreNotWrittenMeetsEverySpellingWithThatSkeleton() {
        // The Arabic script leaves out vowels, so these are the same word to the eye: the search then offers the choice.
        yes("Zainab", "زنب")
        yes("Nasir", "نصیر")
        yes("Naseer", "ناصر")
        yes("Hussain", "حسن")
        yes("Hassan", "حسن")
        yes("Aman", "امن")
        yes("Amna", "امن")
        yes("Aamna", "امن")
        // ... but one long vowel that is written must be heard: "حسین" is not Hassan.
        no("Hassan", "حسین")
    }

    // ---- common given names, one by one ----

    private val sameName = listOf(
        "Fatima" to "فاطمہ",
        "Khadija" to "خدیجہ",
        "Hamza" to "حمزہ",
        "Aamna" to "آمنہ",
        "Shazia" to "شازیہ",
        "Rukhsana" to "رخسانہ",
        "Ayesha" to "عائشہ",
        "Mustafa" to "مصطفی",
        "Musa" to "موسیٰ",
        "Musa" to "موسی",
        "Bushra" to "بشریٰ",
        "Bushra" to "بشری",
        "Yahya" to "یحییٰ",
        "Zainab" to "زینب",
        "Maryam" to "مریم",
        "Noor" to "نور",
        "Usman" to "عثمان",
        "Umar" to "عمر",
        "Imran" to "عمران",
        "Ibrahim" to "ابراہیم",
        "Ismail" to "اسماعیل",
        "Abdullah" to "عبداللہ",
        "Asif" to "آصف",
        "Arif" to "عارف",
        "Ehtisham" to "احتشام",
        "Tariq" to "طارق",
        "Waheed" to "وحید",
        "Naveed" to "نوید",
        "Javed" to "جاوید",
        "Saeed" to "سعید",
        "Kareem" to "کریم",
        "Rahim" to "رحیم",
        "Nasir" to "ناصر",
        "Shahid" to "شاہد",
        "Farooq" to "فاروق",
        "Haider" to "حیدر",
        "Hussain" to "حسین",
        "Hassan" to "حسن",
        "Ghulam" to "غلام",
        "Muhammad" to "محمد",
        "Sultan" to "سلطان",
        "Sikandar" to "سکندر",
        "Shakeel" to "شکیل",
        "Zubair" to "زبیر",
        "Sohail" to "سہیل",
        "Sajid" to "ساجد",
        "Rasheed" to "رشید",
        "Rafiq" to "رفیق",
        "Qasim" to "قاسم",
        "Faisal" to "فیصل",
        "Irfan" to "عرفان",
        "Salman" to "سلمان",
        "Sana" to "ثنا",
        "Hira" to "حرا",
        "Iqra" to "اقرا",
        "Asma" to "اسماء",
        "Sumaira" to "سمیرا",
        "Nazia" to "نازیہ",
        "Shabana" to "شبانہ",
        "Parveen" to "پروین",
        "Nasreen" to "نسرین",
        "Kausar" to "کوثر",
        "Ahmad" to "احمد",
        "Ali" to "علی",
        "Bilal" to "بلال",
        "Danish" to "دانش",
        "Adnan" to "عدنان",
        "Atif" to "عاطف",
        "Ameer" to "امیر",
        "Ejaz" to "اعجاز",
        "Akram" to "اکرم",
        "Anwar" to "انور",
        "Ashraf" to "اشرف",
        "Aslam" to "اسلم",
        "Ayub" to "ایوب",
        "Idrees" to "ادریس",
        "Ilyas" to "الیاس",
        "Ishaq" to "اسحاق",
        "Younas" to "یونس",
        "Yaqoob" to "یعقوب",
        "Zahid" to "زاہد",
        "Zafar" to "ظفر",
        "Waseem" to "وسیم",
        "Wasim" to "وسیم",
        "Hamad" to "حمد",
        "Ahmed" to "احمد",
        "Omar" to "عمر",
        "Iman" to "ایمان",
        "Mohammed" to "محمد",
        "Reza" to "رضا",
        "Raza" to "رضا",
        "Kazim" to "کاظم",
        "Zia" to "ضیاء",
        "Hossein" to "حسین",
        "Mohsen" to "محسن",
        "Abbas" to "عباس",
        "Ali" to "علي",
        "Rizwan" to "رضوان",
        "Owais" to "اویس",
        "Uwais" to "اویس",
        "Awais" to "اویس",
        "Saleha" to "صالحہ",
        "Athar" to "اطہر",
        "Mehmood" to "محمود",
        "Rehan" to "ریحان",
        "Sarah" to "سارہ",
        "Yusuf" to "یوسف",
        "Zainab" to "زنب",
        "Bashar" to "بشار",
        "Nawaz" to "نواز",
        "Dawood" to "داود",
        "Ayaan" to "ایان",
        "Mian" to "میاں",
        "Asadullah" to "اسداللہ",
        "Abdulkareem" to "عبدالکریم",
        "Saira" to "سائرہ",
        "Sana" to "سنا",
        "Hina" to "حنا",
        "Shaheen" to "شاہین",
        "Nasir" to "نصیر",
        "Naseer" to "نصیر",
        "Naseer" to "ناصر",
        "Zahra" to "زہرا",
        "Khuda" to "خدا",
        "Jawad" to "جواد",
        "Khawar" to "خاور",
        "Amna" to "امنہ",
        "Amina" to "امینہ",
        "Aisha" to "عائشہ",
        "Rabia" to "رابعہ",
        "Wajiha" to "وجیہہ",
        "Sara" to "سارہ",
        "Shah" to "شاہ",
        "Aman" to "امن",
        "Amin" to "امین",
        "Nida" to "ندا",
        "Huma" to "ہما",
        "Zohra" to "زہرہ",
        "Zuhra" to "زہرہ",
        "Mahira" to "ماہرہ",
        "Nimra" to "نمرہ",
        "Maria" to "ماریہ",
        "Tahira" to "طاہرہ",
        "Rubina" to "روبینہ",
        "Naila" to "نائلہ",
        "Hafsa" to "حفصہ",
        "Saima" to "صائمہ",
        "Hajra" to "ہاجرہ",
        "Alia" to "عالیہ",
        "Samina" to "ثمینہ",
        "Sumaira" to "سمیرہ",
        "Kainat" to "کائنات",
        "Farah" to "فرح",
        "Farha" to "فرحہ",
        "Rashida" to "راشدہ",
        "Bushra" to "بشرہ",
        "Nasira" to "ناصرہ"
    )

    private val otherName = listOf(
        "Bushra" to "بشیر",
        "Bashir" to "بشری",
        "Bashar" to "بشیر",
        "Asim" to "وسیم",
        "Ahmad" to "حمد",
        "Hamad" to "احمد",
        "Salam" to "اسلم",
        "Mar" to "عمر",
        "Kamil" to "کریم",
        "Bashir" to "بشار",
        "Ali" to "عل",
        "Noor" to "نیر",
        "Ali" to "رضا",
        "Hasan" to "حسین",
        "Basheer" to "بشار",
        "Aman" to "آمنہ",
        "Aman" to "امنہ",
        "Amin" to "امینہ",
        "Rashid" to "راشدہ",
        "Bashir" to "بشیرہ",
        "Nasir" to "ناصرہ"
    )

    @Test
    fun commonNamesMeetTheirUsualSpelling() {
        for ((latin, arabic) in sameName) yes(latin, arabic)
    }

    @Test
    fun aNameIsNotMetByItsNeighbours() {
        for ((latin, arabic) in otherName) no(latin, arabic)
    }
}
