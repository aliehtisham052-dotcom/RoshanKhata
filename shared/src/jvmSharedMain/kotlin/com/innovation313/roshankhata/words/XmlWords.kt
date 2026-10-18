package com.innovation313.roshankhata.words

import com.innovation313.roshankhata.data.Digits
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Words] read straight from the app's strings.xml files, as Android's
 * resource compiler reads them (the escapes, the quotes, the whitespace).
 * The computer's words; on the phone, XmlWordsOnDeviceTest holds every
 * string in every language to what Android itself returns.
 *
 * [open] gives a file by its folder and name ("values-ur", "strings.xml"),
 * or null when there is none. Folders are tried most specific first:
 * a script (values-b+ur+Latn for Roman Urdu), the language (values-ur),
 * then the default (values) — word by word, as Android falls back.
 */
class XmlWords(private val locale: Locale, private val open: (folder: String, file: String) -> InputStream?) : Words {

    private class Table(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val lists: Map<String, List<String>>
    )

    private val tables: List<Table> = folders(locale).mapNotNull { folder ->
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val lists = HashMap<String, List<String>>()
        var any = false
        for (file in listOf("strings.xml", "arrays.xml")) {
            val input = open(folder, file) ?: continue
            any = true
            input.use { read(it, strings, plurals, lists) }
        }
        if (any) Table(strings, plurals, lists) else null
    }

    override fun text(name: String, vararg args: Any): String {
        val raw = tables.firstNotNullOfOrNull { it.strings[name] } ?: throw NoSuchElementException("no string $name")
        return if (args.isEmpty()) raw else raw.format(Digits.FIGURES, *args)
    }

    override fun plural(name: String, count: Int, vararg args: Any): String =
        pluralText(name, count).format(Digits.FIGURES, *args)

    /** The plural form [count] picks, unformatted (getQuantityText's twin). */
    fun pluralText(name: String, count: Int): String {
        val forms = tables.firstNotNullOfOrNull { it.plurals[name] } ?: throw NoSuchElementException("no plural $name")
        return forms[pluralCategory(locale, count)] ?: forms["other"] ?: forms.values.first()
    }

    /** The string [name] unformatted (getText's twin). */
    fun textRaw(name: String): String = text(name)

    /** Every plural and list name this language's files and their fallbacks hold. */
    fun pluralNames(): Set<String> = tables.flatMap { it.plurals.keys }.toSet()
    fun listNames(): Set<String> = tables.flatMap { it.lists.keys }.toSet()

    override fun list(name: String): List<String> =
        tables.firstNotNullOfOrNull { it.lists[name] } ?: throw NoSuchElementException("no list $name")

    /** Every string name this language's files and their fallbacks hold. */
    fun names(): Set<String> = tables.flatMap { it.strings.keys }.toSet()

    companion object {
        /** values-b+ur+Latn, values-ur, values: the folders for [locale], most specific first. */
        fun folders(locale: Locale): List<String> {
            // Android names Indonesian "in" (its folder too); newer JDKs say "id".
            val lang = when (locale.language) { "id" -> "in"; "iw" -> "he"; else -> locale.language }
            val out = ArrayList<String>()
            if (locale.script.isNotEmpty()) out += "values-b+$lang+${locale.script}"
            if (locale.country.isNotEmpty()) out += "values-$lang-r${locale.country}"
            if (lang.isNotEmpty()) out += "values-$lang"
            out += "values"
            return out
        }

        private fun read(
            input: InputStream,
            strings: MutableMap<String, String>,
            plurals: MutableMap<String, Map<String, String>>,
            lists: MutableMap<String, List<String>>
        ) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> strings[name] = unescape(e.textContent)
                    "plurals" -> plurals[name] = items(e).associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    "string-array" -> lists[name] = items(e).map { unescape(it.textContent) }
                }
            }
        }

        private fun items(e: Element): List<Element> {
            val n = e.childNodes
            return (0 until n.length).mapNotNull { n.item(it) as? Element }.filter { it.tagName == "item" }
        }

        /**
         * A resource string as Android's compiler reads it: outside double
         * quotes, runs of white space become one space and the ends are
         * trimmed; an unescaped " opens or closes a quoted run and is not
         * kept; \n \t \' \" \\ \@ \? and \uXXXX are their characters.
         */
        fun unescape(raw: String): String {
            val out = StringBuilder(raw.length)
            var quoted = false
            var pendingSpace = false
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (!quoted && ch.isWhitespace()) {
                    pendingSpace = out.isNotEmpty()
                    i++
                    continue
                }
                if (pendingSpace) { out.append(' '); pendingSpace = false }
                when {
                    ch == '"' -> quoted = !quoted
                    ch == '\\' && i + 1 < raw.length -> {
                        val next = raw[++i]
                        when (next) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'u' -> {
                                val hex = raw.substring(i + 1, minOf(i + 5, raw.length))
                                val code = hex.toIntOrNull(16)
                                if (hex.length == 4 && code != null) { out.append(code.toChar()); i += 4 } else out.append('u')
                            }
                            else -> out.append(next)
                        }
                    }
                    else -> out.append(ch)
                }
                i++
            }
            return out.toString()
        }
    }
}

/** The CLDR plural category ("one", "few", "other"...) of [n] in [locale]'s language. */
expect fun pluralCategory(locale: Locale, n: Int): String
