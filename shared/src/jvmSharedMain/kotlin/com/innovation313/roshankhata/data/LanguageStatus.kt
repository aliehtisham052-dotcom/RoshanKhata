package com.innovation313.roshankhata.data

/**
 * Which of the app's nine languages are still "beta" (decision D17, 25 Nov 2026): a language whose words no native speaker
 * has read yet is shown as such wherever a language is chosen — the phone's language page (the setup's first step and
 * More → Language, LanguageActivity) and the computer's language list (Main's PickDialog). One rule for both platforms:
 * when a native read of one of them is done, its tag leaves [BETA] here and nowhere else.
 *
 * Tags are the app's own (`AppLanguages.ALL`): "ur-Latn" is Roman Urdu, "id" Indonesian.
 */
object LanguageStatus {
    /** Sindhi, Bengali, Farsi and Arabic: no native read yet (D17). */
    val BETA: Set<String> = setOf("sd", "bn", "fa", "ar")

    /**
     * Whether [tag] is a beta language. Takes the app's tags and a phone's or computer's locale tags alike: the region
     * ("ar-SA", "bn_BD") is not part of the language; Roman Urdu ("ur-Latn") is Urdu, which is not beta.
     */
    fun isBeta(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return false
        val language = tag.trim().replace('_', '-').substringBefore('-').lowercase()
        return language in BETA
    }
}
