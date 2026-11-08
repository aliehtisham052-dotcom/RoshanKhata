package com.innovation313.roshankhata.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPairGenerator
import java.security.Security
import java.time.Duration
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.crypto.KeyAgreement
import javax.net.ssl.SSLContext

/**
 * What the MSI's trimmed Java must be able to do (23 Oct 2026, R36). The Gradle tests run on
 * the runner's full JDK, so a module missing from desktop/build.gradle.kts's `modules(...)`
 * (java.logging for PDFBox, jdk.localedata for Urdu month names, jdk.crypto.ec for TLS) was
 * only ever found on the owner's laptop. desktop.yml starts the app image's own launcher with
 * `--runtime-self-check` (Main.kt hands over before anything else runs) after createDistributable and
 * fails the run on any FAIL line (jpackage strips java.exe from the runtime, so the launcher is the way in).
 * `--net <https url>` also makes one real HTTPS request (a TLS handshake the relay needs).
 */
object RuntimeSelfCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        // The installed app has no console and jpackage leaves no java.exe in its runtime: desktop.yml starts the
        // app's own launcher with --runtime-self-check --out <file> and reads the file.
        args.indexOf("--out").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.let { path ->
            System.setOut(java.io.PrintStream(java.io.FileOutputStream(path), true, "UTF-8"))
        }
        val failures = mutableListOf<String>()
        fun probe(name: String, block: () -> String) {
            val said = try {
                "OK   $name: " + block()
            } catch (t: Throwable) {
                failures += name
                "FAIL $name: " + t.javaClass.name + ": " + t.message
            }
            println(said)
        }

        probe("modules") {
            // The image's own modules (a provider module exports nothing, so it is not a boot root).
            val here = java.lang.module.ModuleFinder.ofSystem().findAll().map { it.descriptor().name() }.toSet()
            val need = listOf(
                "java.base", "java.desktop", "java.sql", "java.logging", "java.net.http",
                "jdk.unsupported", "jdk.localedata", "jdk.crypto.ec",
            )
            val missing = need.filter { it !in here }
            check(missing.isEmpty()) { "missing " + missing.joinToString() }
            here.sorted().joinToString()
        }
        probe("SunEC provider") {
            val p = Security.getProvider("SunEC") ?: error("no SunEC provider")
            p.name + " " + p.versionStr
        }
        probe("ECDH and X25519") {
            KeyAgreement.getInstance("ECDH")
            KeyPairGenerator.getInstance("X25519").generateKeyPair()
            "available"
        }
        probe("TLS ECDHE suites") {
            val suites = SSLContext.getDefault().createSSLEngine().enabledCipherSuites
            val ec = suites.count { it.contains("ECDHE") }
            val tls13 = suites.count { it.startsWith("TLS_AES_") || it.startsWith("TLS_CHACHA20_") }
            check(ec > 0) { "no ECDHE suite among ${suites.size}" }
            "$ec ECDHE of ${suites.size} ($tls13 TLS 1.3)"
        }
        probe("AES-GCM and PBKDF2") {
            javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "available"
        }
        probe("Urdu month names (jdk.localedata)") {
            val name = Month.JANUARY.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ur"))
            check(name != "January") { "Urdu January read \"$name\"" }
            name
        }
        probe("PDFBox logging (java.logging)") {
            // By name, as PDFBox finds it: commons-logging comes with PDFBox, its JDK logger needs java.logging.
            val factory = Class.forName("org.apache.commons.logging.LogFactory")
            val log = factory.getMethod("getLog", Class::class.java).invoke(null, RuntimeSelfCheck::class.java)
            log.javaClass.name
        }
        probe("SQLite native library (jdk.unsupported)") {
            val c = BundledSQLiteDriver().open(":memory:")
            try {
                val st = c.prepare("SELECT sqlite_version()")
                try {
                    check(st.step()) { "no row" }
                    st.getText(0)
                } finally {
                    st.close()
                }
            } finally {
                c.close()
            }
        }

        val net = args.indexOf("--net").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        if (net != null) {
            probe("HTTPS $net") {
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
                val req = HttpRequest.newBuilder(URI(net)).timeout(Duration.ofSeconds(30)).GET().build()
                // Any HTTP status means the TLS handshake succeeded; a refusal is a network failure.
                var last: Throwable? = null
                var status = -1
                for (attempt in 1..3) {
                    try {
                        status = client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode()
                        break
                    } catch (t: javax.net.ssl.SSLException) {
                        throw t
                    } catch (t: java.io.IOException) {
                        last = t
                        Thread.sleep(3000L * attempt)
                    }
                }
                if (status < 0) throw last ?: IllegalStateException("no answer")
                "HTTP $status"
            }
        }

        if (failures.isNotEmpty()) {
            println("RUNTIME CHECK FAILED: " + failures.joinToString())
            kotlin.system.exitProcess(1)
        }
        println("RUNTIME CHECK PASSED")
    }
}
