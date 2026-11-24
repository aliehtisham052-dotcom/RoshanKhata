package com.innovation313.roshankhata.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Stage 4 (24 Nov 2026): the phone's files that cannot be compiled here are held to the rule by reading them. A source
 * scan proves the line is there, not that Android runs it (NOT TESTED on a device); it stops the lines being taken out.
 */
class BusinessApartSourceTest {
    private val app: File? = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/innovation313/roshankhata") }.firstOrNull { it.isDirectory }

    private fun text(path: String): String {
        assumeTrue("the phone's sources are not beside this module", app != null)
        return File(app, path).readText()
    }

    @Test fun aLongFormsDraftIsKeptPerShop() {
        val s = text("ui/PrefsDraftKeep.kt")
        assertTrue(s.contains("BusinessKeys.keyOf("))
        // Every read, write and removal goes through the per-shop key, never the bare name or a literal.
        assertTrue(s.contains("prefs.getString(key, null)"))
        assertTrue(s.contains("remove(key) else putString(key,"))
        assertTrue(!Regex("""getString\(\s*(name\b|")""").containsMatchIn(s))
    }

    @Test fun aBackgroundRunCapturesItsShopBeforeTheBookOpens() {
        val s = text("AutomationWorker.kt")
        val work = s.substring(s.indexOf("override suspend fun doWork"), s.indexOf("private fun untilDayEnds"))
        val captured = work.indexOf("val shop = ")
        val opened = work.indexOf("KhataDatabase.get(ctx)")
        assertTrue(captured in 0 until opened)
        assertTrue("settings take the captured shop", work.contains("AndroidAutomationSettings(ctx, shop)"))
        assertTrue("a switch during the reading ends the run", Regex("""active\(ctx\)\.id != shop""").findAll(work).count() >= 2)
        val post = s.substring(s.indexOf("private fun post("), s.indexOf("val id = ID_BASE"))
        assertTrue("the notice names the captured shop", post.contains("EXTRA_OPEN_BUSINESS, shop)"))
    }

    @Test fun deletingAShopForgetsWhatItLeftInSharedFiles() {
        val s = text("data/Businesses.kt")
        val delete = s.substring(s.indexOf("fun delete("), s.indexOf("internal fun forgetLeftovers"))
        assertTrue(delete.contains("forgetLeftovers(context, id, target.file)"))
        val purge = s.substring(s.indexOf("internal fun forgetLeftovers"))
        assertTrue(purge.contains("BusinessKeys.SHARED_FILES"))
        assertTrue(purge.contains("bin_kinds"))
    }

    @Test fun everySharedPrefsFileWithAShopKeyIsInTheList() {
        val dir = app ?: return
        assumeTrue(dir.isDirectory)
        // A shared preferences file whose keys carry a shop (the file name known in advance): the list must name it.
        val named = mapOf("poster" to "PosterFields.kt", "rate_list" to "RateListActivity.kt", "seal_offer" to "ui/SealOffer.kt",
            "book_seal" to "BookSealsAndroid.kt", "snapshots" to "Snapshots.kt", "live_sync" to "LiveRunner.kt",
            "customer_link" to "CustomerLinkSync.kt", "automations" to "AutomationWorker.kt", "form_drafts" to "ui/PrefsDraftKeep.kt")
        for (name in named.keys) assertTrue("$name is in BusinessKeys.SHARED_FILES", name in BusinessKeys.SHARED_FILES)
    }
}
