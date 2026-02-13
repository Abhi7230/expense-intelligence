package com.example.myapplication

import java.util.Calendar

/**
 * Suggests categories based on time of day.
 *
 * LOGIC:
 * - 6 AM - 10 AM: Breakfast, Chai, Commute
 * - 11 AM - 2 PM: Lunch
 * - 3 PM - 6 PM: Snacks, Chai, Shopping
 * - 7 PM - 10 PM: Dinner, Entertainment
 * - 10 PM - 1 AM: Late night food, Auto
 *
 * This makes the category popup smarter — at 9 PM, "Dinner" appears first!
 */
object TimeSuggestionEngine {

    data class CategorySuggestion(
        val category: String,
        val subcategory: String,
        val emoji: String,
        val priority: Int  // lower = show first
    )

    fun getSuggestions(timestamp: Long = System.currentTimeMillis()): List<CategorySuggestion> {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        val timeBased = when (hour) {
            in 6..9 -> listOf(
                CategorySuggestion("Food", "Breakfast", "🍳", 1),
                CategorySuggestion("Food", "Chai/Coffee", "☕", 2),
                CategorySuggestion("Transport", "Commute", "🚇", 3)
            )
            in 10..11 -> listOf(
                CategorySuggestion("Food", "Snacks", "🥪", 1),
                CategorySuggestion("Food", "Chai/Coffee", "☕", 2),
                CategorySuggestion("Shopping", "General", "🛍️", 3)
            )
            in 12..14 -> listOf(
                CategorySuggestion("Food", "Lunch", "🍛", 1),
                CategorySuggestion("Food", "Restaurant", "🍽️", 2),
                CategorySuggestion("Food", "Chai/Coffee", "☕", 3)
            )
            in 15..17 -> listOf(
                CategorySuggestion("Food", "Snacks", "🍿", 1),
                CategorySuggestion("Food", "Chai/Coffee", "☕", 2),
                CategorySuggestion("Shopping", "General", "🛍️", 3)
            )
            in 18..21 -> listOf(
                CategorySuggestion("Food", "Dinner", "🍕", 1),
                CategorySuggestion("Food", "Street Food", "🌮", 2),
                CategorySuggestion("Entertainment", "Movies", "🎬", 3),
                CategorySuggestion("Transport", "Auto/Cab", "🚕", 4)
            )
            in 22..23 -> listOf(
                CategorySuggestion("Food", "Late Night Snack", "🌙", 1),
                CategorySuggestion("Transport", "Auto/Cab", "🚕", 2),
                CategorySuggestion("Food", "Street Food", "🌮", 3)
            )
            in 0..1 -> listOf(
                CategorySuggestion("Food", "Late Night Snack", "🌙", 1),
                CategorySuggestion("Transport", "Auto/Cab", "🚕", 2)
            )
            else -> listOf(
                CategorySuggestion("Food", "General", "🍔", 1)
            )
        }

        // Add weekend-specific suggestions
        val weekendBonus = if (isWeekend) listOf(
            CategorySuggestion("Entertainment", "Outing", "🎢", 5),
            CategorySuggestion("Shopping", "Weekend Shopping", "🛒", 6)
        ) else emptyList()

        // Always include these common categories
        val common = listOf(
            CategorySuggestion("Food", "General", "🍔", 10),
            CategorySuggestion("Transport", "Auto", "🛺", 11),
            CategorySuggestion("Shopping", "General", "🛍️", 12),
            CategorySuggestion("Personal", "Transfer to Friend", "👤", 13),
            CategorySuggestion("Bills", "Recharge", "📱", 14),
            CategorySuggestion("Health", "Medicine", "💊", 15),
            CategorySuggestion("Groceries", "General", "🥬", 16),
            CategorySuggestion("Entertainment", "General", "🎮", 17),
            CategorySuggestion("Other", "Miscellaneous", "📦", 20)
        )

        // Merge and deduplicate (time-based takes priority)
        val seen = mutableSetOf<String>()
        return (timeBased + weekendBonus + common).filter { suggestion ->
            val key = "${suggestion.category}:${suggestion.subcategory}"
            if (key in seen) false else { seen.add(key); true }
        }.sortedBy { it.priority }
    }

    /**
     * Get a simplified list of just the top suggestions (for compact UI)
     */
    fun getTopSuggestions(timestamp: Long = System.currentTimeMillis(), limit: Int = 9): List<CategorySuggestion> {
        return getSuggestions(timestamp).take(limit)
    }
}

