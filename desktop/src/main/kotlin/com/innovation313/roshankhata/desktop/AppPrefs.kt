package com.innovation313.roshankhata.desktop

import java.io.File
import java.util.Properties

/**
 * The Windows app's own choices, for every shop on the computer: for now the
 * language. A properties file next to the book, written in full on each
 * change through a temporary file, so a crash mid-write leaves the old one.
 */
class AppPrefs(private val file: File) {
    private val props = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) }
    }

    /** The chosen language's tag ("ur", "ur-Latn"...), or null when never chosen. */
    fun language(): String? = props.getProperty(KEY_LANGUAGE)?.takeIf { it.isNotBlank() }

    @Synchronized
    fun setLanguage(tag: String) {
        props.setProperty(KEY_LANGUAGE, tag)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata") }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private companion object {
        const val KEY_LANGUAGE = "app_language"
    }
}
