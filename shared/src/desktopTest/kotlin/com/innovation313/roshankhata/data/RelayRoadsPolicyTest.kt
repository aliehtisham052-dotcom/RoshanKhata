package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BC-266 (25 Nov 2026): the Supabase relay's host is a constant in the shipped code but is out of the road list, and the
 * privacy policy names no Supabase. That is honest only while the app never contacts it. This holds it: the roads a build
 * can reach are Cloudflare and the owner's hosting; Supabase may be added back only in the commit that also names it in
 * both privacy policies' section 24 and in docs/data-flows.tsv.
 */
class RelayRoadsPolicyTest {

    private fun doc(name: String): String =
        listOf(File("../docs/$name"), File("docs/$name")).first { it.isFile }.readText()

    @Test
    fun `the app contacts the road list's hosts only, and Supabase is not among them unless the policies say so`() {
        val hosts = LiveCloud.roads().map { it.base }
        val supabase = hosts.any { it.contains("supabase", ignoreCase = true) }
        if (supabase) {
            assertTrue("Supabase is on the road list but privacy-policy.html does not name it", doc("privacy-policy.html").contains("Supabase"))
            assertTrue("Supabase is on the road list but privacy-policy-ur.html does not name it", doc("privacy-policy-ur.html").contains("Supabase"))
        }
        assertFalse("no road may be the constant's host while it is unnamed", !supabase && hosts.contains(LiveCloud.SUPABASE))
        // Every road the app contacts by default is one the two policies name (section 24): Cloudflare's Worker, then the hosting.
        assertEquals(listOf(LiveCloud.RELAY, LiveCloud.HOSTING), hosts)
        for (name in listOf("privacy-policy.html", "privacy-policy-ur.html")) {
            val text = doc(name)
            assertTrue("$name names Cloudflare", text.contains("Cloudflare"))
            assertTrue("$name names the hosting", text.contains("ehtisham.shop"))
        }
    }
}
