package com.innovation313.roshankhata.desktop

import java.io.File

/**
 * A cloud folder this computer already has, where the daily copies could also be kept (9 Nov 2026, the owner's
 * phone-test round, point 7): OneDrive first, else Google Drive for Desktop. Only existence is checked — nothing is
 * read, signed in to or written here. [find] is pure (the environment and the disk are passed in) so a test can
 * stand in for Windows.
 */
object CloudFolder {
    enum class Kind { ONEDRIVE, GOOGLE_DRIVE }

    /** [root] is the cloud's own folder (OneDrive, or My Drive); the copies go in "Roshan Khata" under it. */
    class Found(val kind: Kind, val root: String) {
        fun target(): File = File(root, FOLDER_NAME)
    }

    const val FOLDER_NAME = "Roshan Khata"

    private val ONEDRIVE_VARS = listOf("OneDrive", "OneDriveConsumer", "OneDriveCommercial")

    /**
     * @param env an environment variable's value, or null
     * @param isDir whether a path (written with Windows' backslashes) is an existing folder
     */
    fun find(
        env: (String) -> String?,
        userHome: String,
        isDir: (String) -> Boolean
    ): Found? {
        ONEDRIVE_VARS.mapNotNull { env(it)?.trim()?.takeIf { v -> v.isNotEmpty() } }
            .firstOrNull { isDir(it) }
            ?.let { return Found(Kind.ONEDRIVE, it) }
        // Google Drive for Desktop: "My Drive" on a virtual drive letter (G: to Z:), or under the profile.
        for (letter in 'G'..'Z') {
            val p = "$letter:\\My Drive"
            if (isDir(p)) return Found(Kind.GOOGLE_DRIVE, p)
        }
        if (userHome.isNotBlank()) {
            val p = userHome.trimEnd('\\', '/') + "\\Google Drive\\My Drive"
            if (isDir(p)) return Found(Kind.GOOGLE_DRIVE, p)
        }
        return null
    }

    /** The real machine's answer. */
    fun findHere(): Found? = find(System::getenv, System.getProperty("user.home").orEmpty()) { File(it).isDirectory }
}
