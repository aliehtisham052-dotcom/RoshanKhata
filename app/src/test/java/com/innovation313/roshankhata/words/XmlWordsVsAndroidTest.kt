package com.innovation313.roshankhata.words

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The computer reads the app's words with XmlWords, straight from the
 * strings.xml files; the phone reads them through Android's compiled
 * resources. Every string, plural form and list, in every language, must
 * come out the same — the escapes, the quotes, the white space, the
 * language fallbacks (Roman Urdu's script folder above all).
 */
@RunWith(AndroidJUnit4::class)
class XmlWordsVsAndroidTest {

    private val tags = listOf("en", "ur", "ur-Latn", "ar", "sd", "fa", "hi", "bn", "in")
    private val counts = (0..25) + listOf(100, 101, 102, 111, 1000, 1205)

    private fun localised(tag: String): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(conf)
    }

    private fun xml(tag: String) = XmlWords(Locale.forLanguageTag(tag)) { folder, file ->
        File("src/main/res/$folder/$file").takeIf { it.isFile }?.inputStream()
    }

    private fun show(s: String) = s.replace("\n", "\\n").replace("\t", "\\t")

    @Test
    fun `every word in every language reads the same as on the phone`() {
        val bad = mutableListOf<String>()
        var checked = 0
        for (tag in tags) {
            val ctx = localised(tag)
            val res = ctx.resources
            val words = xml(tag)
            for (name in words.names()) {
                val id = res.getIdentifier(name, "string", ctx.packageName)
                if (id == 0) continue
                val phone = res.getText(id).toString()
                val computer = words.textRaw(name)
                checked++
                if (phone != computer) bad += "$tag $name: phone '${show(phone)}' computer '${show(computer)}'"
            }
            for (name in words.pluralNames()) {
                val id = res.getIdentifier(name, "plurals", ctx.packageName)
                if (id == 0) continue
                for (n in counts) {
                    val phone = res.getQuantityText(id, n).toString()
                    val computer = words.pluralText(name, n)
                    checked++
                    if (phone != computer) bad += "$tag $name[$n]: phone '${show(phone)}' computer '${show(computer)}'"
                }
            }
            for (name in words.listNames()) {
                val id = res.getIdentifier(name, "array", ctx.packageName)
                if (id == 0) continue
                val phone = res.getStringArray(id).toList()
                val computer = words.list(name)
                checked++
                if (phone != computer) bad += "$tag $name: phone $phone computer $computer"
            }
        }
        assertTrue("checked only $checked", checked > 15_000)
        assertTrue("${bad.size} differences, first 30:\n" + bad.take(30).joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `Roman Urdu reads its own script, not Urdu's`() {
        val roman = xml("ur-Latn").textRaw("about_us")
        val urdu = xml("ur").textRaw("about_us")
        assertEquals(localised("ur-Latn").getString(com.innovation313.roshankhata.R.string.about_us), roman)
        assertTrue("Roman Urdu '$roman' should differ from Urdu '$urdu'", roman != urdu)
    }
}
