package com.innovation313.roshankhata.data

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The book seal's three witnesses asked for real, once each (L014) — only by hand, from
 * .github/workflows/tsa-check.yml (RK_TSA_LIVE=1); every other run skips it, so no build depends on a time service.
 *
 * For each witness: the answer as the phone reads it ([Rfc3161.stamp] with the app's own road), every certificate
 * its token carries (subject, issuer, validity, SHA-256 of the DER), and whether [Rfc3161.chain] builds it to a root —
 * the roots of this JDK's own trust store and any file the workflow fetched from a service's own page (RK_TSA_ROOTS).
 * The report is what a pin in BookSeals.ROOT_PINS is checked against; the tokens and the digest are written beside it
 * for OpenSSL's own reading. Only a random 32-byte digest is sent.
 */
class TsaLiveCheckTest {

    @Test
    fun eachWitnessStampsOnceAndItsChainIsRead() {
        assumeTrue("by hand only (tsa-check.yml)", System.getenv("RK_TSA_LIVE") == "1")
        val out = File(System.getenv("RK_TSA_OUT") ?: "build/tsa-check").apply { mkdirs() }
        val digest = SealMath.sha256(("rk tsa-check " + System.nanoTime()).toByteArray())
        File(out, "digest.hex").writeText(hex(digest))
        val roots = systemRoots() + fetchedRoots()
        val report = StringBuilder("## Time-stamp services, asked once each\n\n")
        report.append("Roots tried: ${roots.size} (this JDK's trust store and ${fetchedRoots().size} fetched file(s)).\n\n")
        var granted = 0
        for (w in BookSeals.WITNESSES) {
            report.append("### ${w.name} (${w.url})\n\n")
            when (val r = Rfc3161.stamp(Rfc3161.plain, w.url, digest)) {
                is Rfc3161.Reply.Refused -> report.append("- Answer: refused or unreachable (${r.why})\n\n")
                is Rfc3161.Reply.Granted -> {
                    granted++
                    File(out, "${w.name}.token.der").writeBytes(r.token)
                    report.append("- Answer: granted (status ${r.status}), genTime ${Rfc3161Words.utc(r.genTime)}\n")
                    for (c in carried(r.token)) {
                        report.append(
                            "- Carries: ${c.subjectX500Principal.name}\n  - issuer ${c.issuerX500Principal.name}\n" +
                                "  - valid ${Rfc3161Words.utc(c.notBefore.time)} to ${Rfc3161Words.utc(c.notAfter.time)}\n" +
                                "  - SHA-256 ${hex(TestTsa.sha256(c.encoded))}\n"
                        )
                    }
                    when (val ch = Rfc3161.chain(r.token, digest, roots)) {
                        is Rfc3161.Chain.Holds -> report.append(
                            "- Chain: holds, to ${ch.root.subjectX500Principal.name} (SHA-256 ${hex(TestTsa.sha256(ch.root.encoded))})\n\n"
                        )
                        is Rfc3161.Chain.Fails -> report.append("- Chain: does not hold (${ch.why})\n\n")
                        Rfc3161.Chain.NotPinned -> report.append("- Chain: no root to try\n\n")
                    }
                }
            }
        }
        File(out, "report.md").writeText(report.toString())
        println(report)
        assertTrue("no witness answered:\n$report", granted > 0)
    }

    /** The certificates a token carries, as X.509 reads them from its SignedData. */
    private fun carried(token: ByteArray): List<X509Certificate> = runCatching {
        CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(token)).map { it as X509Certificate }
    }.getOrDefault(emptyList())

    private fun systemRoots(): List<X509Certificate> {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.toList() }
    }

    private fun fetchedRoots(): List<X509Certificate> {
        val dir = System.getenv("RK_TSA_ROOTS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return emptyList()
        val factory = CertificateFactory.getInstance("X.509")
        return dir.listFiles().orEmpty().sortedBy { it.name }.flatMap { f ->
            runCatching { f.inputStream().use { s -> factory.generateCertificates(s).map { it as X509Certificate } } }.getOrDefault(emptyList())
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private object Rfc3161Words {
        fun utc(ms: Long): String =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.ROOT)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))
    }
}
