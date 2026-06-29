package com.reliquary.app.domain

/** Hidden extras key for the user's own 1–5 star rating (distinct from provider rating). */
const val MY_RATING_KEY = "_myRating"

/** Hidden extras key for a wishlist item's priority. */
const val WISH_PRIORITY_KEY = "_wishPriority"
val WISH_PRIORITIES = listOf("High", "Medium", "Low")

/** Sort rank for a wishlist priority value (High first, unset last). */
fun wishPriorityRank(value: String?): Int = when (value) {
    "High" -> 0
    "Medium" -> 1
    "Low" -> 2
    else -> 3
}
