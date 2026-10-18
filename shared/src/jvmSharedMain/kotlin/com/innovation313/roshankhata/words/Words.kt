package com.innovation313.roshankhata.words

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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

val LocalWords = staticCompositionLocalOf<Words> { error("No Words provided: wrap the screen in ProvideWords") }

/** The string [name] in the app's language. */
@Composable
@ReadOnlyComposable
fun word(name: String, vararg args: Any): String = LocalWords.current.text(name, *args)

/** The plural [name] for [count] in the app's language. */
@Composable
@ReadOnlyComposable
fun plural(name: String, count: Int, vararg args: Any): String = LocalWords.current.plural(name, count, *args)

/** Hands [words] to every shared screen inside [content]. */
@Composable
fun ProvideWords(words: Words, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWords provides words, content = content)
}
