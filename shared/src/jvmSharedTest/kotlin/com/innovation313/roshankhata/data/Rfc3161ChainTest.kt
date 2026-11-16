package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whose answer a time stamp is (L014): [Rfc3161.chain] builds the signer's certificate into a chain to a pinned root
 * with the JDK's own PKIX, and [Rfc3161.pinned] trusts a root file only by its fingerprint. Every certificate here is
 * made in the test ([TestCa], [TestTsa]); made-up names, nobody's real CA.
 */
class Rfc3161ChainTest {

    private val digest = TestTsa.sha256("rk chain test".toByteArray())

    private companion object {
        val root = TestCa("Test Root (made in the test)")
        val middle = TestCa("Test Time Stamping CA (made in the test)", parent = root)
        /** Issued by [middle]; its token carries [middle] too, as a TSA that sends its chain. */
        val carrying = TestTsa(issuer = middle, carry = listOf(middle.cert))
        /** Issued by [middle]; its token carries only its own certificate. */
        val bare = TestTsa(issuer = middle)
        val stranger = TestCa("Another Root (made in the test)")
    }

    private fun token(tsa: TestTsa) = tsa.token(digest, null, System.currentTimeMillis())

    @Test
    fun `a token whose chain reaches the pinned root holds, and names that root`() {
        val r = Rfc3161.chain(token(carrying), digest, listOf(root.x509))
        assertTrue("$r", r is Rfc3161.Chain.Holds)
        assertEquals(root.x509, (r as Rfc3161.Chain.Holds).root)
    }

    @Test
    fun `a middle certificate the token leaves out may be supplied, and without it there is no chain`() {
        assertTrue(Rfc3161.chain(token(bare), digest, listOf(root.x509), listOf(middle.x509)) is Rfc3161.Chain.Holds)
        val r = Rfc3161.chain(token(bare), digest, listOf(root.x509))
        assertTrue("$r", r is Rfc3161.Chain.Fails)
    }

    @Test
    fun `a chain to a root nobody pinned for this service fails`() {
        val r = Rfc3161.chain(token(carrying), digest, listOf(stranger.x509))
        assertTrue("$r", r is Rfc3161.Chain.Fails)
    }

    @Test
    fun `with no root pinned it is said as not pinned, never as holding`() {
        assertEquals(Rfc3161.Chain.NotPinned, Rfc3161.chain(token(carrying), digest, emptyList()))
    }

    @Test
    fun `a token that does not add up fails before any chain is looked at, pinned or not`() {
        val broken = carrying.token(digest, null, System.currentTimeMillis(), TestTsa.Fault.OTHER_KEY)
        assertTrue(Rfc3161.chain(broken, digest, emptyList()) is Rfc3161.Chain.Fails)
        assertTrue(Rfc3161.chain(broken, digest, listOf(root.x509)) is Rfc3161.Chain.Fails)
        // For another digest: not this seal's answer.
        val other = TestTsa.sha256("something else".toByteArray())
        assertTrue(Rfc3161.chain(token(carrying), other, listOf(root.x509)) is Rfc3161.Chain.Fails)
    }

    @Test
    fun `a middle certificate that is not a CA breaks the chain`() {
        val notCa = TestCa("Not a CA (made in the test)", parent = root, ca = false)
        val tsa = TestTsa(issuer = notCa, carry = listOf(notCa.cert))
        val r = Rfc3161.chain(token(tsa), digest, listOf(root.x509))
        assertTrue("$r", r is Rfc3161.Chain.Fails)
    }

    @Test
    fun `a middle certificate expired before the stamp's time breaks the chain`() {
        val now = System.currentTimeMillis()
        val old = TestCa("Expired CA (made in the test)", parent = root, validFrom = now - 30 * TestTsa.DAY, validTo = now - TestTsa.DAY)
        val tsa = TestTsa(issuer = old, carry = listOf(old.cert))
        val r = Rfc3161.chain(token(tsa), digest, listOf(root.x509))
        assertTrue("$r", r is Rfc3161.Chain.Fails)
    }

    @Test
    fun `a root file is trusted only by its pinned fingerprint`() {
        val pin = TestTsa.sha256(root.cert).joinToString("") { "%02x".format(it) }
        assertEquals(listOf(root.x509), Rfc3161.pinned(listOf(root.cert, stranger.cert), listOf(pin)))
        // The fingerprint as a tool prints it: upper case, colons.
        val printed = pin.uppercase().chunked(2).joinToString(":")
        assertEquals(listOf(root.x509), Rfc3161.pinned(listOf(stranger.cert, root.cert), listOf(printed)))
        // A file swapped without its pin: nothing trusted.
        assertEquals(emptyList<Any>(), Rfc3161.pinned(listOf(stranger.cert), listOf(pin)))
        assertEquals(emptyList<Any>(), Rfc3161.pinned(listOf(root.cert), emptyList()))
        // Bytes that hash to the pin but are no certificate are passed over, not thrown on.
        val junk = "not a certificate".toByteArray()
        val junkPin = TestTsa.sha256(junk).joinToString("") { "%02x".format(it) }
        assertEquals(emptyList<Any>(), Rfc3161.pinned(listOf(junk), listOf(junkPin)))
    }
}
