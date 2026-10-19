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

    /** The owner's second place for the daily copies (USB, Google Drive folder), or null. */
    fun backupFolder(): File? = props.getProperty(KEY_BACKUP_FOLDER)?.takeIf { it.isNotBlank() }?.let(::File)

    @Synchronized
    fun setBackupFolder(folder: File?) {
        if (folder == null) props.remove(KEY_BACKUP_FOLDER) else props.setProperty(KEY_BACKUP_FOLDER, folder.absolutePath)
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
    }
}
