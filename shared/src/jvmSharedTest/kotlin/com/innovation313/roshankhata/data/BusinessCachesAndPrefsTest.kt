package com.innovation313.roshankhata.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * BC-250 (24 Nov 2026): the keys of caches and of the computer's shared settings carry the shop; the files that cannot be
 * compiled here are held to it by reading them (a source scan proves the line is there, NOT that a device runs it).
 */
class BusinessCachesAndPrefsTest {
    @Test fun aCacheKeyNamesTheShopAndItsBook() {
        assertEquals("1|roshan_khata.db", BusinessKeys.cacheKey(1, "roshan_khata.db"))
        assertNotEquals(BusinessKeys.cacheKey(1, "a.db"), BusinessKeys.cacheKey(2, "a.db"))
        assertNotEquals("the same shop under another book file", BusinessKeys.cacheKey(2, "a.db"), BusinessKeys.cacheKey(2, "b.db"))
        // "1|23" and "12|3" are different shops: the separator keeps the two fields apart.
        assertNotEquals(BusinessKeys.cacheKey(1, "23"), BusinessKeys.cacheKey(12, "3"))
    }

    @Test fun aDeletedShopsDesktopKeysAreFoundAndNoNeighboursAre() {
        val keys = listOf(
            "invoice_tax_b3", "invoice_discount_b3", "auto_said_cheques_b3", "last_saved_backup_b3", "bin_kinds_since_b3",
            "invoice_tax_b13", "last_saved_backup_b33", "invoice_tax", "auto_said_cheques", "last_saved_backup_b1", "app_language", "app_lock_hash"
        )
        assertEquals(listOf("invoice_tax_b3", "invoice_discount_b3", "auto_said_cheques_b3", "last_saved_backup_b3", "bin_kinds_since_b3"),
            BusinessKeys.ownedDesktopKeys(keys, 3))
        assertEquals(listOf("invoice_tax_b13"), BusinessKeys.ownedDesktopKeys(keys, 13))
        assertEquals("Business 1 has no suffix and is never matched", emptyList<String>(), BusinessKeys.ownedDesktopKeys(keys, 1))
    }

    @Test fun theDesktopRuleIsTheSharedSuffixRule() {
        for (id in listOf(2L, 3L, 13L, 33L)) for (k in listOf("x_b3", "x_b13", "x_b33", "x_b2", "x")) {
            assertEquals("$k for $id", BusinessKeys.endsWithBusinessSuffix(k, id), k in BusinessKeys.ownedDesktopKeys(listOf(k), id))
        }
    }

    // ---- the files that cannot be compiled here ----

    private val root: File? = generateSequence(File(".").absoluteFile) { it.parentFile }.firstOrNull { File(it, "app/src/main").isDirectory }
    private fun text(path: String): String {
        assumeTrue("the repository's sources are not beside this module", root != null)
        return File(root, path).readText().replace("\r\n", "\n")
    }
    private val app = "app/src/main/java/com/innovation313/roshankhata"
    private val desk = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop"

    @Test fun everyPartyPhotoPutGoesThroughTheEpoch() {
        val s = text("$app/data/PartyPhoto.kt")
        assertTrue("dropCaches bumps the epoch", Regex("""fun dropCaches\(\) \{\s*epoch\.drop \{""").containsMatchIn(s))
        // No cache or onDisk write outside a putIfCurrent block: strip those blocks, then none may remain.
        val outside = stripBlocks(s, "epoch.putIfCurrent(seen)")
            .replace(Regex("""(?s)fun dropCaches\(\) \{.*?\n    \}\n"""), "")
        assertFalse("a cache.put outside the epoch", outside.contains("cache.put("))
        assertFalse("a cache.remove outside the epoch", outside.contains("cache.remove("))
        assertFalse("an onDisk write outside the epoch", Regex("""onDisk\[[^\]]+\]\s*=(?!=)""").containsMatchIn(outside))
        // The epoch is read before the shop's folder is resolved in load.
        val load = s.substring(s.indexOf("fun load("), s.indexOf("fun save("))
        assertTrue(load.indexOf("epoch.now()") in 0 until load.indexOf("file(context, partyId)"))
    }

    @Test fun theRadarCacheIsKeyedByShopAndBook() {
        val s = text("$app/ui/RadarUi.kt")
        assertFalse("no identity hash", s.contains("identityHashCode"))
        assertTrue(s.contains("BusinessKeys.cacheKey(open.id, open.file)"))
        val fn = s.substring(s.indexOf("suspend fun shopDays("), s.indexOf("suspend fun read("))
        assertTrue("the key is taken before the read", fn.indexOf("cacheKey(") in 0 until fn.indexOf("withContext"))
    }

    @Test fun deletingADesktopShopForgetsItsSettings() {
        val b = text("$desk/Businesses.kt")
        val delete = b.substring(b.indexOf("fun delete("), b.indexOf("private fun save()"))
        assertTrue(delete.contains("AppPrefsLive.forgetBusiness(File(home, \"app.properties\"), id)"))
        val p = text("$desk/AppPrefs.kt")
        assertTrue(p.contains("BusinessKeys.ownedDesktopKeys(props.stringPropertyNames(), id)"))
    }

    /** [s] with each `<marker> { ... }` block (balanced braces) taken out. */
    private fun stripBlocks(s: String, marker: String): String {
        val out = StringBuilder(); var i = 0
        while (true) {
            val at = s.indexOf(marker, i)
            if (at < 0) { out.append(s.substring(i)); break }
            out.append(s, i, at)
            var j = s.indexOf('{', at); var depth = 0
            do { if (s[j] == '{') depth++ else if (s[j] == '}') depth--; j++ } while (depth > 0)
            i = j
        }
        return out.toString()
    }
}
