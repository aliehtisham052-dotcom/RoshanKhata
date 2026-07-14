package com.innovation313.roshankhata.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    private val dateTimeFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dateOnlyFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

    fun money(value: Double): String {
        val rounded = abs(value)
        return if (rounded % 1.0 == 0.0) {
            "Rs %,.0f".format(rounded)
        } else {
            "Rs %,.2f".format(rounded)
        }
    }

    fun dateTime(millis: Long): String = dateTimeFmt.format(Date(millis))

    fun dateOnly(millis: Long): String = dateOnlyFmt.format(Date(millis))

    /**
     * "5 bag — Urea", or just "Urea" if no quantity was given.
     * Returns null when nothing was recorded, so the caller can hide the row.
     */
    fun goods(itemName: String?, quantity: Double?, unit: String?): String? {
        val item = itemName?.trim().orEmpty()
        if (item.isEmpty() && quantity == null) return null

        val qtyPart = quantity?.let { q ->
            val n = if (q % 1.0 == 0.0) "%,.0f".format(q) else "%,.2f".format(q)
            val u = unit?.trim().orEmpty()
            if (u.isEmpty()) n else "$n $u"
        }

        return when {
            qtyPart != null && item.isNotEmpty() -> "$qtyPart — $item"
            qtyPart != null -> qtyPart
            else -> item
        }
    }
}
