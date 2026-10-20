package com.innovation313.roshankhata.desktop

import java.io.File
import java.util.Properties

/**
 * The Windows app's own choices, for every shop on the computer: the
 * language, the theme and the second backup folder. A properties file next to the book, written in full on each
 * change through a temporary file, so a crash mid-write leaves the old one.
 */
class AppPrefs(private val file: File) {
    private val props = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) }
    }

    /** The chosen language's tag ("ur", "ur-Latn"...), or null when never chosen. */
    fun language(): String? = props.getProperty(KEY_LANGUAGE)?.takeIf { it.isNotBlank() }

    /** The owner's second place for the daily copies (USB, Google Drive folder), or null. */
    fun backupFolder(): File? = props.getProperty(KEY_BACKUP_FOLDER)?.takeIf { it.isNotBlank() }?.let(::File)

    @Synchronized
    fun setBackupFolder(folder: File?) {
        if (folder == null) props.remove(KEY_BACKUP_FOLDER) else props.setProperty(KEY_BACKUP_FOLDER, folder.absolutePath)
        save()
    }

    /** Light, dark, or as Windows is (the phone's Theme choice); SYSTEM when never chosen. */
    fun theme(): ThemeChoice = props.getProperty(KEY_THEME)
        ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.SYSTEM

    /** The owner's text size, 0..3 (the phone's TextSize: small, normal, large, largest); normal when never chosen. */
    fun textSize(): Int = props.getProperty(KEY_TEXT_SIZE)?.toIntOrNull()?.coerceIn(0, TEXT_FACTORS.lastIndex) ?: 1

    @Synchronized
    fun setTextSize(level: Int) {
        props.setProperty(KEY_TEXT_SIZE, level.coerceIn(0, TEXT_FACTORS.lastIndex).toString())
        save()
    }

    @Synchronized
    fun setTheme(choice: ThemeChoice) {
        props.setProperty(KEY_THEME, choice.name)
        save()
    }

    @Synchronized
    fun setLanguage(tag: String) {
        props.setProperty(KEY_LANGUAGE, tag)
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata") }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private companion object {
        const val KEY_LANGUAGE = "app_language"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_THEME = "theme"
        const val KEY_TEXT_SIZE = "text_size"
    }
}

/** The phone's three Theme choices (MoreSheet): as the computer is, always light, always dark. */
enum class ThemeChoice(val word: String) {
    SYSTEM(com.innovation313.roshankhata.words.W.theme_system_pc),
    LIGHT(com.innovation313.roshankhata.words.W.theme_light),
    DARK(com.innovation313.roshankhata.words.W.theme_dark)
}

/** The phone's text-size steps (TextSize.FACTORS), the same four on the computer. */
val TEXT_FACTORS = floatArrayOf(0.90f, 1.00f, 1.15f, 1.30f)

/** Their names, in the same order. */
val TEXT_SIZE_WORDS = listOf(
    com.innovation313.roshankhata.words.W.text_size_small,
    com.innovation313.roshankhata.words.W.text_size_normal,
    com.innovation313.roshankhata.words.W.text_size_large,
    com.innovation313.roshankhata.words.W.text_size_largest
)
