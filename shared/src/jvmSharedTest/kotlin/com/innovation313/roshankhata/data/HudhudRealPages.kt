package com.innovation313.roshankhata.data

import java.io.File

/**
 * The app's own help pages, as the tests read them (14 Nov 2026, #32): the key, the title and the body of every page in
 * `pages.tsv`, taken from the app's own strings.xml of each language — so a test asks Hudhud about the pages that ship,
 * not about pages made up for the test. The app joins a page's words in the phone's language, in English and, on an Urdu
 * phone, in both Urdu scripts (`HudhudBook.topics`); [forApp] joins them the same way. One reader for every test that
 * needs a real page ([HudhudOwnerSetTest], [HudhudHowToPagesTest]), so none of them can drift from the app.
 */
object HudhudRealPages {

    /** The app languages, by the name [forApp] and [folderOf] know: English, Roman Urdu, Urdu, Sindhi, Arabic, Farsi, Hindi, Bengali, Indonesian. */
    val LANGUAGES = listOf("en", "ur-Latn", "ur", "sd", "ar", "fa", "hi", "bn", "in")

    /** The repository's root: the folder that holds `app/src/main/res`, found from the test's own working folder. */
    val root: File by lazy {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null && !File(dir, "app/src/main/res").isDirectory) dir = dir.parentFile
        dir
    }

    private val STRING = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    /** The resource folder of an app language ("ur-Latn" is Roman Urdu's `values-b+ur+Latn`, English is the base `values`). */
    fun folderOf(language: String): String = when (language) {
        "en" -> "values"
        "ur-Latn" -> "values-b+ur+Latn"
        else -> "values-$language"
    }

    /**
     * One folder's strings.xml as the app reads a string: the escaped quote and ampersand undone, tags and line breaks
     * gone. A folder or a string that is not there is an empty map or no key — never a made-up word.
     */
    fun strings(folder: String): Map<String, String> {
        val f = File(root, "app/src/main/res/$folder/strings.xml")
        if (!f.exists()) return emptyMap()
        return STRING.findAll(f.readText()).associate { m ->
            m.groupValues[1] to m.groupValues[2].replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&")
                .replace(Regex("<[^>]+>"), "").replace("\\n", " ")
        }
    }

    /** The pages of `pages.tsv`: a page's key, then the names of the strings that are its title and its body. */
    fun rows(): List<Triple<String, String, String>> =
        HudhudRealPages::class.java.getResourceAsStream("/hudhud/pages.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { l -> val (key, title, body) = l.split('\t'); Triple(key, title, body) }

    /** The pages as topics, each one's words joined across [folders] in that order. */
    fun topics(folders: List<String>): List<Hudhud.Topic> {
        val tongues = folders.map { strings(it) }
        return rows().map { (key, title, body) ->
            Hudhud.Topic(
                key,
                tongues.joinToString(" ") { it[title] ?: "" },
                tongues.joinToString(" ") { it[body] ?: "" },
                feature = Hudhud.isFeaturePage(key)
            )
        }
    }

    /** What the app joins on a phone that speaks [language]: its own words, English, and on an Urdu phone both Urdu scripts. */
    fun foldersFor(language: String): List<String> = buildList {
        add(folderOf(language))
        add("values")
        if (language == "ur" || language == "ur-Latn") { add("values-ur"); add("values-b+ur+Latn") }
    }

    /** The pages as the app hands them to Hudhud on a phone that speaks [language]. */
    fun forApp(language: String): List<Hudhud.Topic> = topics(foldersFor(language))
}
