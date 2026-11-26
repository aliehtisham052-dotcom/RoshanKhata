package com.innovation313.roshankhata.data

/**
 * The stock of one name that is several products ("Urea" for Urea Sona, Urea Fatima…), added up only when it can be (BC-408):
 * every product has a shelf figure and every figure is in one unit. A product never traded, or a bag beside a kilogram,
 * leaves no total — a wrong sum is worse than none, and each row still says its own.
 */
object HudhudGoodsTotal {
    /** Each product's shelf figure and its unit; the total and its unit, or null. */
    fun of(each: List<Pair<Double?, String?>>): Pair<Double, String?>? {
        if (each.size < 2 || each.any { it.first == null }) return null
        if (each.map { it.second.orEmpty() }.distinct().size != 1) return null
        return each.sumOf { it.first ?: 0.0 } to each.first().second
    }
}
