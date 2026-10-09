package com.innovation313.roshankhata.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The backup's own date, read without building the whole book as a tree (9 Oct). */
@RunWith(AndroidJUnit4::class)
class ViewerSyncTest {

    @Test
    fun `the date is found after the tables, which are skipped`() {
        val text = """{"format":"RoshanKhata","version":30,"parties":[{"id":1,"name":"A"},{"id":2,"name":"B"}],""" +
            """"entries":[{"id":1,"amount":5000.5,"note":null}],"exportedAt":1791515300123}"""
        assertEquals(1791515300123L, ViewerSync.exportedAt(text))
    }

    @Test
    fun `no date, or not a backup, is zero`() {
        assertEquals(0L, ViewerSync.exportedAt("""{"format":"RoshanKhata","parties":[]}"""))
        assertEquals(0L, ViewerSync.exportedAt("not json"))
        assertEquals(0L, ViewerSync.exportedAt(""))
    }
}
