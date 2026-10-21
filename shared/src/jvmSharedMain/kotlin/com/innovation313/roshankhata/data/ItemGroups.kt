package com.innovation313.roshankhata.data

/**
 * The counter's item grid (21 Oct 2026, the critical review): the products as
 * tiles, each variant (schema 36's [Product.parentId]) under its main product
 * rather than a tile of its own, sorted by name; and the categories the grid
 * can be narrowed to. A variant is a product in its own right — its own
 * price, barcode and stock — only shown together.
 */
object ItemGroups {

    data class Group(val main: Product, val variants: List<Product>) {
        /** What a tap offers: the main product when it has a price of its own, then its variants. */
        val choices: List<Product>
            get() = (if (variants.isEmpty() || (main.salePrice ?: main.creditPrice) != null) listOf(main) else emptyList()) + variants
    }

    /** The product a variant belongs under: its top main product (a chain is followed, a loop is not). */
    fun rootOf(p: Product, byId: Map<Long, Product>): Product {
        var at = p
        val seen = HashSet<Long>()
        while (true) {
            val parent = at.parentId?.let { byId[it] } ?: return at
            if (!seen.add(at.id) || parent.id == at.id) return at
            at = parent
        }
    }

    fun of(products: List<Product>, category: String? = null): List<Group> {
        val live = products.filter { !it.isDeleted }
        val byId = live.associateBy { it.id }
        val grouped = live.groupBy { rootOf(it, byId).id }
        return grouped.map { (rootId, members) ->
            val main = byId.getValue(rootId)
            Group(main, members.filter { it.id != rootId }.sortedBy { label(it).lowercase() })
        }.filter { g -> category == null || g.main.category == category || g.variants.any { it.category == category } }
            .sortedBy { it.main.name.lowercase() }
    }

    fun categories(products: List<Product>): List<String> =
        products.filter { !it.isDeleted }.mapNotNull { it.category?.trim()?.ifBlank { null } }.distinct().sortedBy { it.lowercase() }

    /** A variant's own word (its size, colour, flavour), else the product's name. */
    fun label(p: Product): String = p.variant?.trim()?.takeIf { it.isNotEmpty() } ?: p.name
}
