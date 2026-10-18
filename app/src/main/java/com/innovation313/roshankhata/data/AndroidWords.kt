package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.words.Words

/**
 * The phone's [Words]: its own resources, in the activity's language and
 * text size, with figures in 0-9 through Digits as every XML screen does.
 */
class AndroidWords(context: Context) : Words {
    private val res = context.resources

    override fun text(name: String, vararg args: Any): String {
        val id = WordIds.string(name)
        return if (args.isEmpty()) res.getText(id).toString() else Digits.string(res, id, *args)
    }

    override fun plural(name: String, count: Int, vararg args: Any): String =
        Digits.quantity(res, WordIds.plurals(name), count, *args)

    override fun list(name: String): List<String> = res.getStringArray(WordIds.array(name)).toList()
}
