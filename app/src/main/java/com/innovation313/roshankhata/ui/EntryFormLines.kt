package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.LineDraft
import org.json.JSONArray
import org.json.JSONObject

/**
 * The add-entry form's goods list as text, for the screen's saved state (23 Oct 2026, the review,
 * R41): a form open when Android rebuilt the screen (turned, a scanner connecting, the camera in
 * front long enough for the screen to be reclaimed) comes back with every line it had. Small and
 * the form's own: nothing here is written to the book. A list that cannot be read comes back empty
 * rather than half.
 */
object EntryFormLines {

    fun toJson(lines: List<LineDraft>): String {
        val arr = JSONArray()
        for (d in lines) {
            val o = JSONObject()
            d.itemName?.let { o.put("name", it) }
            d.quantity?.let { o.put("qty", it) }
            d.unit?.let { o.put("unit", it) }
            d.rate?.let { o.put("rate", it) }
            d.productId?.let { o.put("product", it) }
            d.billItemId?.let { o.put("batch", it) }
            d.creditPrice?.let { o.put("credit", it) }
            d.cashPrice?.let { o.put("cash", it) }
            d.productUnit?.let { o.put("productUnit", it) }
            o.put("rateEdited", d.rateEdited)
            o.put("bonus", d.isBonus)
            o.put("updateRate", d.updateProductRate)
            o.put("addProduct", d.addToProducts)
            d.crop?.let { o.put("crop", it) }
            d.pest?.let { o.put("pest", it) }
            d.dose?.let { o.put("dose", it) }
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(text: String?): List<LineDraft> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LineDraft(
                    itemName = o.text("name"),
                    quantity = o.number("qty"),
                    unit = o.text("unit"),
                    rate = o.number("rate"),
                    productId = o.id("product"),
                    billItemId = o.id("batch"),
                    creditPrice = o.number("credit"),
                    cashPrice = o.number("cash"),
                    productUnit = o.text("productUnit"),
                    rateEdited = o.optBoolean("rateEdited", false),
                    isBonus = o.optBoolean("bonus", false),
                    updateProductRate = o.optBoolean("updateRate", false),
                    addToProducts = o.optBoolean("addProduct", false),
                    crop = o.text("crop"),
                    pest = o.text("pest"),
                    dose = o.text("dose")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.text(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.number(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.id(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
}
