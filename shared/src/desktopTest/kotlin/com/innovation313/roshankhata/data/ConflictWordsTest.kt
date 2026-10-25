package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * What changed on two devices at once, said in the owner's words (Marhala 4, part 10): the book's own column
 * names and stored values ("timestamp 1761212345678", "isGiven 1") were shown as they were saved.
 */
class ConflictWordsTest {
    private val words = desktopWords(Locale.ENGLISH)

    @Test
    fun `known fields get the owner's words and their values read as they mean`() {
        assertEquals("Amount", ConflictWords.field(words, "amount"))
        assertEquals("Note", ConflictWords.field(words, "note"))
        assertEquals("Date", ConflictWords.field(words, "timestamp"))
        assertEquals(Format.money(12_500.0), ConflictWords.value(words, "amount", "12500.0"))
        assertEquals(Format.dateTime(1_761_212_345_678L), ConflictWords.value(words, "timestamp", "1761212345678"))
        assertEquals("I gave", ConflictWords.value(words, "isGiven", "1"))
        assertEquals("I got", ConflictWords.value(words, "isGiven", "0"))
    }

    @Test
    fun `anything else stays as it was saved`() {
        assertEquals("uid", ConflictWords.field(words, "uid"))
        assertEquals("abc", ConflictWords.value(words, "uid", "abc"))
        assertEquals("not a figure", ConflictWords.value(words, "amount", "not a figure"))
        assertNull(ConflictWords.value(words, "amount", null))
    }
}
