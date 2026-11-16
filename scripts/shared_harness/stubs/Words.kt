// Stand-in (shared harness only): the interface of shared/src/jvmSharedMain/.../words/Words.kt without its Compose parts.
package com.innovation313.roshankhata.words


/**
 * The app's words for a shared screen, in the language the app is in
 * (KMP Phase 5a). Names come from [W], [WP] and [WA], generated from
 * app/src/main/res/values, so a name that does not exist does not compile.
 *
 * Figures in a sentence are written in 0-9 on both platforms, as
 * Digits.string / Digits.quantity have always done on the phone.
 *
 * The phone: AndroidWords (its own resources). The computer: [XmlWords]
 * (the same strings.xml files).
 */
interface Words {
    /** A string; with [args], formatted like getString(id, args) but in 0-9. */
    fun text(name: String, vararg args: Any): String

    /** A plural chosen by [count]; [args] formatted as in [text]. */
    fun plural(name: String, count: Int, vararg args: Any): String

    /** A list (string-array). */
    fun list(name: String): List<String>
}

