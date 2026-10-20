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

    /** Which optional invoice fields the shop uses (the phone's InvoiceFeatureSettings); all on until turned off. */
    fun invoiceFields(): InvoiceFields = InvoiceFields(
        discount = flag(KEY_INV_DISCOUNT), tax = flag(KEY_INV_TAX), charge = flag(KEY_INV_CHARGE),
        received = flag(KEY_INV_RECEIVED), note = flag(KEY_INV_NOTE), dueDate = flag(KEY_INV_DUE)
    )

    @Synchronized
    fun setInvoiceFields(f: InvoiceFields) {
        listOf(KEY_INV_DISCOUNT to f.discount, KEY_INV_TAX to f.tax, KEY_INV_CHARGE to f.charge,
            KEY_INV_RECEIVED to f.received, KEY_INV_NOTE to f.note, KEY_INV_DUE to f.dueDate)
            .forEach { (k, on) -> props.setProperty(k, on.toString()) }
        save()
    }

    private fun flag(key: String): Boolean = props.getProperty(key)?.toBooleanStrictOrNull() ?: true

    /** The first-run walkthrough has been seen (or skipped) on this computer. */
    fun tourSeen(): Boolean = props.getProperty(KEY_TOUR) == "true"

    @Synchronized
    fun setTourSeen() {
        props.setProperty(KEY_TOUR, "true")
        save()
    }

    /** The day (yyyy-MM-dd) the daily reminder was last shown (Reminders). */
    fun remindedDay(): String? = props.getProperty(KEY_REMINDED)

    @Synchronized
    fun setRemindedDay(day: String) { props.setProperty(KEY_REMINDED, day); save() }

    /** The update check's last answer and when it was asked (UpdateCheck). */
    fun updateLatest(): String? = props.getProperty(KEY_UPDATE_LATEST)?.takeIf { it.isNotBlank() }
    fun updateCheckedAt(): Long = props.getProperty(KEY_UPDATE_AT)?.toLongOrNull() ?: 0L

    @Synchronized
    fun setUpdateLatest(version: String, at: Long) {
        props.setProperty(KEY_UPDATE_LATEST, version); props.setProperty(KEY_UPDATE_AT, at.toString()); save()
    }

    /** True when the owner has set an App Lock PIN on this computer. */
    fun hasPin(): Boolean = !props.getProperty(KEY_PIN_HASH).isNullOrBlank() && !props.getProperty(KEY_PIN_SALT).isNullOrBlank()

    /** The typed PIN against the kept hash; false when there is none. */
    fun pinMatches(pin: String): Boolean {
        val hash = props.getProperty(KEY_PIN_HASH) ?: return false
        val salt = props.getProperty(KEY_PIN_SALT) ?: return false
        return runCatching { PinHash.matches(pin, salt, hash) }.getOrDefault(false)
    }

    /** Keeps a salted PBKDF2 hash of [pin], never the PIN itself. */
    @Synchronized
    fun setPin(pin: String) {
        val salt = PinHash.salt()
        props.setProperty(KEY_PIN_SALT, salt)
        props.setProperty(KEY_PIN_HASH, PinHash.hash(pin, salt))
        save()
    }

    @Synchronized
    fun clearPin() {
        props.remove(KEY_PIN_HASH); props.remove(KEY_PIN_SALT)
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
        const val KEY_TOUR = "tour_seen"
        const val KEY_PIN_HASH = "app_lock_hash"
        const val KEY_PIN_SALT = "app_lock_salt"
        const val KEY_INV_DISCOUNT = "invoice_discount"
        const val KEY_INV_TAX = "invoice_tax"
        const val KEY_INV_CHARGE = "invoice_extra_charge"
        const val KEY_INV_RECEIVED = "invoice_received"
        const val KEY_INV_NOTE = "invoice_note"
        const val KEY_INV_DUE = "invoice_due_date"
        const val KEY_REMINDED = "reminded_day"
        const val KEY_UPDATE_LATEST = "update_latest"
        const val KEY_UPDATE_AT = "update_checked_at"
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

/** The invoice form's optional fields, each on or off (the phone's Invoice settings screen). */
data class InvoiceFields(
    val discount: Boolean = true,
    val tax: Boolean = true,
    val charge: Boolean = true,
    val received: Boolean = true,
    val note: Boolean = true,
    val dueDate: Boolean = true
)
