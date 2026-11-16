package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.XmlWords

/**
 * The app's words as the computer says them (17 Nov 2026): a word that has a computer's own twin — the same name with
 * "_pc" — gives the twin. Hudhud's answers and the Radar's reasons are shared with the phone and name the phone's words
 * ("Your book stays on this phone", "It is worked out on this phone"); read through this, the computer says "this computer"
 * wherever such a twin was written, with no change to the shared code and no list of keys to keep in step.
 *
 * Only for the words that are shared with the phone (Hudhud's env, the Radar's reasons): the computer's own screens name
 * their `_pc` words themselves. A twin must take the same arguments as the word it stands for (DeskWordsTest holds every
 * pair to that, in all nine languages).
 */
class DeskWords private constructor(private val base: Words, private val twins: Set<String>) : Words {

    override fun text(name: String, vararg args: Any): String = base.text(own(name), *args)

    override fun plural(name: String, count: Int, vararg args: Any): String = base.plural(name, count, *args)

    override fun list(name: String): List<String> = base.list(name)

    /** The computer's twin of [name] when it has one, else [name]. */
    fun own(name: String): String = if (!name.endsWith(PC) && (name + PC) in twins) name + PC else name

    companion object {
        const val PC = "_pc"

        /**
         * [words] as the computer says them. The twins are known only from the strings files ([XmlWords], what the computer
         * always reads); any other words (a test's stand-in) are handed back as they are.
         */
        fun of(words: Words): Words = when (words) {
            is DeskWords -> words
            is XmlWords -> DeskWords(words, words.names().filter { it.endsWith(PC) }.toSet())
            else -> words
        }
    }
}
