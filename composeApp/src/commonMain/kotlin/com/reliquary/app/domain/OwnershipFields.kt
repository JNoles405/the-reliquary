package com.reliquary.app.domain

/** Hidden extras key marking an item as wanted (on the wishlist) rather than owned. */
const val WANTED_KEY = "_wanted"

/** Editable purchase/value fields, stored in extras like the edition fields. */
val VALUE_FIELDS: List<String> = listOf(
    "Purchase Date",
    "Purchase Price",
    "Current Value",
    "Condition",
)

/** Parse a currency-ish string ("$12.99", "12,99") into a Double, or null. */
fun parseMoney(raw: String?): Double? =
    raw?.let { Regex("[0-9]+([.,][0-9]+)?").find(it.replace(",", "."))?.value?.toDoubleOrNull() }
