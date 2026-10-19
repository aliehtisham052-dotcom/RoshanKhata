package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.R

// NumberWords itself lives in :shared (the computer prints amounts in words
// too). The phone's calls keep their shape: the language comes from the
// number_words_language string Android resolved for the running locale.

fun NumberWords.rupeesInWords(context: Context, amount: Double): String =
    rupeesInWords(context.getString(R.string.number_words_language), amount)

fun NumberWords.amountInWords(context: Context, amount: Double): String =
    amountInWords(context.getString(R.string.number_words_language), amount)
