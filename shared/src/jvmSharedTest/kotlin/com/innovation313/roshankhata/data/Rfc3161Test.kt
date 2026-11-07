package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * RFC 3161 by hand (the book seal, 4 Nov 2026), held to OpenSSL 3.0.13 itself. The request bytes are those of
 * `openssl ts -query -sha256 -cert` for the same digest and nonce; the replies were made by `openssl ts -reply` with a
 * throwaway self-signed TSA certificate (CN "Test TSA (throwaway)", made for this test and thrown away) answering a
 * request this code built; the token read out of the first was checked with `openssl ts -verify`. The digest is
 * SHA-256 of the text "rk test".
 *
 * Since 7 Nov 2026 (the security review's M1, M4, M8) a token is kept only when its signature holds on the device —
 * OpenSSL's own tokens above do, and so do tokens signed here with a key and certificate made with the JDK
 * ([TestTsa]); each broken part is refused for its own reason — its genTime must stand near the moment it was asked,
 * a request is cut at its whole deadline however slowly the bytes come, and it names only the app.
 */
class Rfc3161Test {

    private val digest = SealMath.sha256("rk test".toByteArray())
    private val nonce = BigInteger("7F12345678ABCDEF", 16)

    /** Granted, nonce 0x7F12345678ABCDEF, genTime 2026-11-05 11:25:09Z, accuracy 1.5 s. */
    private val granted = B64.decode(
            "MIIF4DADAgEAMIIF1wYJKoZIhvcNAQcCoIIFyDCCBcQCAQMxDzANBglghkgBZQMEAgEFADB2BgsqhkiG9w0BCRABBKBnBGUw" +
            "YwIBAQYEKgMEATAxMA0GCWCGSAFlAwQCAQUABCCGJTR6JzzMknD7zleBkFqUcr1pOT49x9unsAC+3HeDsAIBAhgPMjAyNjEx" +
            "MDUxMTI1MDlaMAcCAQGAAgH0Agh/EjRWeKvN76CCAycwggMjMIICC6ADAgECAhQhe32OaH9em/8+xjzg4g7iM/bVpTANBgkq" +
            "hkiG9w0BAQsFADAfMR0wGwYDVQQDDBRUZXN0IFRTQSAodGhyb3dhd2F5KTAeFw0yNjExMDUxMTI0MzNaFw0yNjEyMDUxMTI0" +
            "MzNaMB8xHTAbBgNVBAMMFFRlc3QgVFNBICh0aHJvd2F3YXkpMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyDAY" +
            "CxpRugxyCrZsLNN/h+KPp1bG8Y5CQwXi8hJ4g7kRZaT7dmXnwR27Ce0/pRSonw4t+2Jzt4JVZxsY5IY9QvhFl6Lc41naBj4l" +
            "x0EFxtWRBYQuJEq5SdicJoYYlrAJjgqHePwIRy8f4eqgPXBzJUARY1CMQtIfc/uIL4Psu5cITn4yBTomi/SWoosgeBTLph6N" +
            "RLxSzzfGcEH+X38cvY21c/3QtU6ZX4YKeeLrX5fIK3w+nyI1tjstgGnj5DxJ/+Tj7ATBWOVSS/gzxp8eDD2xEFS5hj9X1c8L" +
            "FZzBHgPAjfNYGzhdq5zphYcIGLOa++bKzMJWbA4Xhq63unh+6QIDAQABo1cwVTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQE" +
            "AwIHgDAWBgNVHSUBAf8EDDAKBggrBgEFBQcDCDAdBgNVHQ4EFgQUyW56PQYG+1mobgPZLB9VQCAm6XcwDQYJKoZIhvcNAQEL" +
            "BQADggEBADZpTZepGlXWd4o9LaPmM4bWoD4c9xzki/W6BTItqqGcI0PKBpQ0E2E6zMukG92oMDaSeziU54eknSC+7ACS8/OD" +
            "ukvBRoDfhWXtu0etp3tbACuvgQwXML0U/HE84XjPQeEtF2C237UCVWe3OkhkUp4hT30NDszbgC000RPW+BeTywiZHIJFaPZI" +
            "e2twl6yCKWrV0AoDAFMNuUtr1n8v0pIgJpM8YIVLcxI01SRnTK2Tf5N/AR84jh3Hd6BMnt7u6eG9BEGE2OR1db80qF+4Ufjv" +
            "e4jfHNXYzYaV56oKQZchOnP2yMAa8L1qSahuFG4LbMpFmn4rTg4Mzpi4qUTBGYMxggIJMIICBQIBATA3MB8xHTAbBgNVBAMM" +
            "FFRlc3QgVFNBICh0aHJvd2F3YXkpAhQhe32OaH9em/8+xjzg4g7iM/bVpTANBglghkgBZQMEAgEFAKCBpDAaBgkqhkiG9w0B" +
            "CQMxDQYLKoZIhvcNAQkQAQQwHAYJKoZIhvcNAQkFMQ8XDTI2MTEwNTExMjUwOVowLwYJKoZIhvcNAQkEMSIEIC2zUPOWaQ4H" +
            "OKER58o0Oax6Y60ypNw3/omPZht6qy2HMDcGCyqGSIb3DQEJEAIvMSgwJjAkMCIEIKViF8XRDrLJJTzfSVxDtdWN9j4or0O1" +
            "HPsqU4Lw2KUzMA0GCSqGSIb3DQEBAQUABIIBAELa5FChW+xh2qZTW/wD6ZD+55kJHYAn9pUGM1KS447dVJZ9lqPackO8Njeu" +
            "4sm5m7Gl2UeNxns5QLWi/K981FI7BQ0BMsAJGNnW+Tx8SUJN59axMyWMzR06+ljpU17EIZ+KwaiWS29B5L9TAliNy0q+Vskc" +
            "RpxCLhsVi74V64oyT/73NJ7LfdqONL7kcOpIJPvGwkYqJGbofeNice18I0HeGAkv9zXIygPYAmjOTkYqVksiFHfiSNrhElsF" +
            "15e+UHQhYtIoo5P/uMg2P/E7Tcot+rhWBv4lhJK4Bk7UrohYSnuJdYL5JePHFeH2pdVJaLh2BL1gZPHfDp2A5AkO1qk="
    )

    /** The same request answered with clock_precision_digits = 3: genTime 2026-11-05 11:25:16.899Z. */
    private val fraction = B64.decode(
            "MIIF5DADAgEAMIIF2wYJKoZIhvcNAQcCoIIFzDCCBcgCAQMxDzANBglghkgBZQMEAgEFADB6BgsqhkiG9w0BCRABBKBrBGkw" +
            "ZwIBAQYEKgMEATAxMA0GCWCGSAFlAwQCAQUABCCGJTR6JzzMknD7zleBkFqUcr1pOT49x9unsAC+3HeDsAIBBBgTMjAyNjEx" +
            "MDUxMTI1MTYuODk5WjAHAgEBgAIB9AIIfxI0Vnirze+gggMnMIIDIzCCAgugAwIBAgIUIXt9jmh/Xpv/PsY84OIO4jP21aUw" +
            "DQYJKoZIhvcNAQELBQAwHzEdMBsGA1UEAwwUVGVzdCBUU0EgKHRocm93YXdheSkwHhcNMjYxMTA1MTEyNDMzWhcNMjYxMjA1" +
            "MTEyNDMzWjAfMR0wGwYDVQQDDBRUZXN0IFRTQSAodGhyb3dhd2F5KTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB" +
            "AMgwGAsaUboMcgq2bCzTf4fij6dWxvGOQkMF4vISeIO5EWWk+3Zl58EduwntP6UUqJ8OLftic7eCVWcbGOSGPUL4RZei3ONZ" +
            "2gY+JcdBBcbVkQWELiRKuUnYnCaGGJawCY4Kh3j8CEcvH+HqoD1wcyVAEWNQjELSH3P7iC+D7LuXCE5+MgU6Jov0lqKLIHgU" +
            "y6YejUS8Us83xnBB/l9/HL2NtXP90LVOmV+GCnni61+XyCt8Pp8iNbY7LYBp4+Q8Sf/k4+wEwVjlUkv4M8afHgw9sRBUuYY/" +
            "V9XPCxWcwR4DwI3zWBs4Xauc6YWHCBizmvvmyszCVmwOF4aut7p4fukCAwEAAaNXMFUwDAYDVR0TAQH/BAIwADAOBgNVHQ8B" +
            "Af8EBAMCB4AwFgYDVR0lAQH/BAwwCgYIKwYBBQUHAwgwHQYDVR0OBBYEFMluej0GBvtZqG4D2SwfVUAgJul3MA0GCSqGSIb3" +
            "DQEBCwUAA4IBAQA2aU2XqRpV1neKPS2j5jOG1qA+HPcc5Iv1ugUyLaqhnCNDygaUNBNhOszLpBvdqDA2kns4lOeHpJ0gvuwA" +
            "kvPzg7pLwUaA34Vl7btHrad7WwArr4EMFzC9FPxxPOF4z0HhLRdgtt+1AlVntzpIZFKeIU99DQ7M24AtNNET1vgXk8sImRyC" +
            "RWj2SHtrcJesgilq1dAKAwBTDblLa9Z/L9KSICaTPGCFS3MSNNUkZ0ytk3+TfwEfOI4dx3egTJ7e7unhvQRBhNjkdXW/NKhf" +
            "uFH473uI3xzV2M2GleeqCkGXITpz9sjAGvC9akmobhRuC2zKRZp+K04ODM6YuKlEwRmDMYICCTCCAgUCAQEwNzAfMR0wGwYD" +
            "VQQDDBRUZXN0IFRTQSAodGhyb3dhd2F5KQIUIXt9jmh/Xpv/PsY84OIO4jP21aUwDQYJYIZIAWUDBAIBBQCggaQwGgYJKoZI" +
            "hvcNAQkDMQ0GCyqGSIb3DQEJEAEEMBwGCSqGSIb3DQEJBTEPFw0yNjExMDUxMTI1MTZaMC8GCSqGSIb3DQEJBDEiBCBcxTrV" +
            "dHB3UZ3J1Ccp++MpiUMC7UoUDA4BGrTxo/ipnDA3BgsqhkiG9w0BCRACLzEoMCYwJDAiBCClYhfF0Q6yySU830lcQ7XVjfY+" +
            "KK9DtRz7KlOC8NilMzANBgkqhkiG9w0BAQEFAASCAQBtM0MdkEbNKcWngO4xdjmprCDQRUnauMC7OH2byV+3MisbKSQOzoKf" +
            "iMsGcarETqLoGajnNu/MvNK29vxn8yb0z+92f8kcDBMfNQj1+h6/1gc0vIZ+R2e7F89w/yj2KSLIcjDvA21ZoCBYkfnsNaBB" +
            "0GFB508jAvSQNE4/tUKAZiWHicTao/bgsKBR6KbtkW1kgMNEgmOCCNY3qZtcBXh97voG3V6/Qs+AiFQbWlgT8r0llS9l7ktC" +
            "BjfvuY3W4Ot+m6NPKayQsWT/HTPAA88ACNy4IOjcAmCMNRc4MkOFf9FzOVBReLKsQRvz6Bl4CIel8K3Mw+ycY9o08+Lxn6lo"
    )

    /** A SHA-1 request refused by the same TSA: status 2 (rejection), badAlg. */
    private val rejected = B64.decode(
            "MDcwNQIBAjAsDCpNZXNzYWdlIGRpZ2VzdCBhbGdvcml0aG0gaXMgbm90IHN1cHBvcnRlZC4DAgeA"
    )

    @Test
    fun theRequestIsByteForByteWhatOpenSslWrites() {
        val openssl = SealMath.unhex(
            "30440201013031300d0609608648016503040201050004208625347a273ccc9270fbce5781905a9472bd69393e3dc7dba7b000" +
                "bedc7783b0020900923c486d1df2c2930101ff"
        )
        // A nonce whose top bit is set takes a leading zero byte (DER's positive INTEGER).
        assertArrayEquals(openssl, Rfc3161.request(digest, BigInteger("923C486D1DF2C293", 16)))
        // A small one is written short: 02 01 05.
        val small = Rfc3161.request(digest, BigInteger.valueOf(5))
        assertEquals(0x3C, small[1].toInt() and 0xff)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x05, 0x01, 0x01, 0xFF.toByte()), small.copyOfRange(small.size - 6, small.size))
    }

    @Test
    fun aGrantedReplyReadsItsTokenAndTime() {
        val r = Rfc3161.parseReply(granted, digest, nonce)
        assertTrue("$r", r is Rfc3161.Reply.Granted)
        r as Rfc3161.Reply.Granted
        assertEquals(0, r.status)
        assertEquals(Rfc3161.generalizedTime("20261105112509Z"), r.genTime)
        assertEquals(1_793_877_909_000L, r.genTime)
        // The token is the reply less its status: what `openssl ts -verify -token_in` takes.
        assertEquals(0x30, r.token[0].toInt())
        assertTrue(r.token.size in 1000 until granted.size)
        assertEquals(r.genTime, Rfc3161.tokenTime(r.token, digest))
        val f = Rfc3161.parseReply(fraction, digest, nonce) as Rfc3161.Reply.Granted
        assertEquals(1_793_877_916_899L, f.genTime)
    }

    @Test
    fun anythingNotOursIsRefused() {
        fun why(r: Rfc3161.Reply) = (r as Rfc3161.Reply.Refused).why
        assertEquals("nonce is not ours", why(Rfc3161.parseReply(granted, digest, BigInteger.TEN)))
        assertEquals("imprint is not our digest", why(Rfc3161.parseReply(granted, ByteArray(32), nonce)))
        assertEquals("status 2", why(Rfc3161.parseReply(rejected, digest, nonce)))
        assertTrue(Rfc3161.parseReply(granted.copyOf(granted.size - 10), digest, nonce) is Rfc3161.Reply.Refused)
        assertTrue(Rfc3161.parseReply(byteArrayOf(0x30, 0x05, 1), digest, nonce) is Rfc3161.Reply.Refused)
        assertTrue(Rfc3161.parseReply(ByteArray(0), digest, nonce) is Rfc3161.Reply.Refused)
        assertEquals(null, Rfc3161.tokenTime(granted, digest))
    }

    @Test
    fun generalizedTimeWithAndWithoutFractions() {
        assertEquals(1_793_783_520_000L, Rfc3161.generalizedTime("20261104091200Z"))
        assertEquals(1_793_783_520_500L, Rfc3161.generalizedTime("20261104091200.5Z"))
        assertEquals(1_793_783_520_123L, Rfc3161.generalizedTime("20261104091200.123456Z"))
        assertTrue(runCatching { Rfc3161.generalizedTime("20261104091200+0500") }.isFailure)
    }

    @Test
    fun aTsaThatCannotBeReachedOrSaysNoIsRefusedNeverThrown() {
        val down = Rfc3161.Http { _, _ -> throw java.io.IOException("no network") }
        assertTrue(Rfc3161.stamp(down, "http://example.invalid", digest) is Rfc3161.Reply.Refused)
        val busy = Rfc3161.Http { _, _ -> 503 to ByteArray(0) }
        assertEquals("http 503", (Rfc3161.stamp(busy, "http://example.invalid", digest) as Rfc3161.Reply.Refused).why)
    }

    // ---------- The signature, on the device (M1) ----------

    private fun why(v: Rfc3161.Verdict): String = (v as? Rfc3161.Verdict.Fails)?.why ?: "holds"

    @Test
    fun openSslsOwnTokenHoldsAndOneChangedByteInItsSignatureDoesNot() {
        val token = (Rfc3161.parseReply(granted, digest, nonce) as Rfc3161.Reply.Granted).token
        assertEquals("holds", why(Rfc3161.verify(token, digest, nonce)))
        assertEquals("holds", why(Rfc3161.verify(token, digest)))
        // The signature is the reply's last bytes: one bit of it changed, and the token is no longer kept.
        val bad = granted.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals("signature does not verify", (Rfc3161.parseReply(bad, digest, nonce) as Rfc3161.Reply.Refused).why)
        assertEquals(null, Rfc3161.tokenTime(bad.copyOfRange(granted.size - token.size, granted.size), digest))
    }

    @Test
    fun aTokenSignedHereHoldsWithRsaOrEcdsaAndByIssuerOrKeyIdentifier() {
        val now = System.currentTimeMillis()
        for (tsa in listOf(TestTsa.rsa, TestTsa.ecdsa)) {
            for (byKeyId in listOf(false, true)) {
                val token = tsa.token(digest, nonce, now, byKeyId = byKeyId)
                assertEquals("ec=${tsa.ec} keyId=$byKeyId", "holds", why(Rfc3161.verify(token, digest, nonce)))
                assertEquals(Rfc3161.generalizedTime(TestTsa.generalized(now)), Rfc3161.tokenTime(token, digest))
            }
            val r = Rfc3161.parseReply(tsa.reply(digest, nonce, now), digest, nonce)
            assertTrue("$r", r is Rfc3161.Reply.Granted)
        }
    }

    @Test
    fun eachCheckRefusesTheTokenThatFailsIt() {
        val now = System.currentTimeMillis()
        val day = TestTsa.DAY
        val tsa = TestTsa.rsa
        fun with(fault: TestTsa.Fault) = why(Rfc3161.verify(tsa.token(digest, nonce, now, fault), digest, nonce))
        assertEquals("no certificate for the signer", with(TestTsa.Fault.NO_CERT))
        // A SignedData that carries the digest and the nonce but no signature that holds: the review's forgery.
        assertEquals("signature does not verify", with(TestTsa.Fault.ZERO_SIGNATURE))
        assertEquals("signature does not verify", with(TestTsa.Fault.OTHER_KEY))
        assertEquals("message digest is not the TSTInfo's", with(TestTsa.Fault.WRONG_DIGEST))
        assertEquals("content type not signed", with(TestTsa.Fault.NO_CONTENT_TYPE))
        assertEquals("2 signers", with(TestTsa.Fault.TWO_SIGNERS))
        // The certificate: time-stamping, critical, and valid when the token was made.
        assertEquals("certificate not for time-stamping",
            why(Rfc3161.verify(TestTsa(ec = true, eku = false).token(digest, nonce, now), digest, nonce)))
        assertEquals("certificate not for time-stamping",
            why(Rfc3161.verify(TestTsa(ec = true, ekuCritical = false).token(digest, nonce, now), digest, nonce)))
        assertEquals("certificate not valid at genTime",
            why(Rfc3161.verify(TestTsa(ec = true, validFrom = now - 30 * day, validTo = now - 10 * day).token(digest, nonce, now), digest, nonce)))
        // A good token for another digest, or another nonce, is not this seal's.
        assertEquals("imprint is not our digest", why(Rfc3161.verify(tsa.token(ByteArray(32), nonce, now), digest, nonce)))
        assertEquals("nonce is not ours", why(Rfc3161.verify(tsa.token(digest, BigInteger.TEN, now), digest, nonce)))
        assertEquals(null, Rfc3161.tokenTime(tsa.token(digest, nonce, now, TestTsa.Fault.OTHER_KEY), digest))
    }

    @Test
    fun aGenTimeFarFromTheMomentItWasAskedIsRefused() {
        val now = System.currentTimeMillis()
        val day = TestTsa.DAY
        val tsa = TestTsa(ec = true, validFrom = now - 30 * day, validTo = now + 30 * day)
        fun at(genTime: Long) = Rfc3161.stamp(Rfc3161.Http { _, body -> 200 to tsa.answer(body, genTime) }, "http://tsa.invalid", digest, clock = { now })
        assertEquals("genTime outside the window", (at(now - 2 * day) as Rfc3161.Reply.Refused).why)
        assertEquals("genTime outside the window", (at(now + 2 * day) as Rfc3161.Reply.Refused).why)
        // A phone's clock some hours off is not refused for it.
        assertTrue(at(now - 5 * 60 * 60 * 1000) is Rfc3161.Reply.Granted)
        assertEquals(now + 60_000, (at(now + 60_000) as Rfc3161.Reply.Granted).genTime)
    }

    @Test
    fun oidsReadAsDottedText() {
        for (text in listOf("1.2.840.113549.1.9.4", "2.16.840.1.101.3.4.2.1", "1.3.6.1.5.5.7.3.8", "2.5.29.37", "2.999.3", "0.9.2342"))
            assertEquals(text, Rfc3161.oid(Rfc3161.read(TestTsa.oid(text), 0).value))
        assertTrue(runCatching { Rfc3161.oid(byteArrayOf(0x2A, 0x86.toByte())) }.isFailure)
        assertTrue(runCatching { Rfc3161.oid(byteArrayOf(0x2A, 0x80.toByte(), 0x01)) }.isFailure)
    }

    // ---------- The road (M4, M8) ----------

    /** A local HTTP server answering one request with [answer] of the request's body. */
    private fun serveOnce(answer: (ByteArray) -> ByteArray): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { s ->
                    val input = s.getInputStream()
                    val got = ByteArrayOutputStream()
                    while (!got.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                        val b = input.read(); if (b < 0) break; got.write(b)
                    }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(got.toString("ISO-8859-1"))!!.groupValues[1].toInt()
                    val body = ByteArray(length)
                    var at = 0
                    while (at < length) { val n = input.read(body, at, length - at); if (n < 0) break; at += n }
                    val reply = answer(body)
                    val out = s.getOutputStream()
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/timestamp-reply\r\nContent-Length: ${reply.size}\r\n" +
                        "Connection: close\r\n\r\n").toByteArray())
                    out.write(reply)
                    out.flush()
                }
            }
        }
        return server
    }

    @Test
    fun aTsaOnTheRealRoadIsAskedAndItsSignedAnswerKept() {
        val server = serveOnce { body -> TestTsa.ecdsa.answer(body) }
        val r = Rfc3161.stamp(Rfc3161.http(deadlineMs = 10_000, timeoutMs = 5_000), "http://127.0.0.1:${server.localPort}/tsr", digest)
        server.close()
        assertTrue("$r", r is Rfc3161.Reply.Granted)
        assertTrue(Rfc3161.tokenTime((r as Rfc3161.Reply.Granted).token, digest) != null)
    }

    @Test
    fun aReplyTricklingAByteAtATimeIsCutAtTheWholeDeadlineAndTheRequestNamesOnlyTheApp() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val head = StringBuilder()
        val heard = CountDownLatch(1)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { s ->
                    val input = s.getInputStream()
                    val got = ByteArrayOutputStream()
                    while (!got.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                        val b = input.read(); if (b < 0) break; got.write(b)
                    }
                    synchronized(head) { head.append(got.toString("ISO-8859-1")) }
                    heard.countDown()
                    // Never a whole status line and headers: a byte every 100 ms, each read well inside its timeout.
                    val out = s.getOutputStream()
                    for (b in "HTTP/1.1 200 OK\r\nX-Slow: ".toByteArray() + ByteArray(10_000) { 'a'.code.toByte() }) {
                        out.write(b.toInt()); out.flush(); Thread.sleep(100)
                    }
                }
            }
        }
        val post = Rfc3161.http(deadlineMs = 1_000, timeoutMs = 500)
        val started = System.nanoTime()
        val tried = runCatching { post.post("http://127.0.0.1:${server.localPort}/tsr", Rfc3161.request(digest, nonce)) }
        val took = (System.nanoTime() - started) / 1_000_000
        server.close()
        assertTrue("$tried", tried.isFailure)
        assertTrue("cut after $took ms", took in 900..5_000)
        assertTrue(heard.await(5, TimeUnit.SECONDS))
        val said = synchronized(head) { head.toString() }
        assertTrue(said, said.contains("User-Agent: RoshanKhata\r\n"))
        assertFalse(said, said.contains("Dalvik") || said.contains("Java/"))
    }
}
