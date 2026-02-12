package com.example.myapplication

import android.util.Log
import com.example.myapplication.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Generates spending insights from stored transaction data.
 *
 * WHAT IT DOES:
 * 1. Daily Summary  → Groups today's payments by category, totals them
 * 2. Weekly Summary → Sends all week's transactions to AI for pattern detection
 *
 * Think of it as:
 *   Raw data (DB rows) → Smart summary (human-readable text)
 */
object InsightGenerator {

    private val TAG = "INSIGHT_GEN"

    /**
     * A single category with its total spend and individual transactions.
     * e.g., category="Food Delivery", total=433.0, items=[₹250 Swiggy, ₹183 Zomato]
     */
    data class CategoryBreakdown(
        val category: String,
        val total: Double,
        val items: List<TransactionItem>
    )

    data class TransactionItem(
        val amount: Double,
        val merchant: String,
        val aiInsight: String?,
        val time: String,
        val timestamp: Long = 0L,       // raw epoch millis — for proper sorting
        val necessity: String? = null   // "need" or "want" — from AI insight
    )

    /**
     * The full daily summary — total spent + breakdown by category.
     */
    data class DailySummary(
        val totalSpent: Double,
        val categories: List<CategoryBreakdown>,
        val transactionCount: Int
    )

    /**
     * Generates today's spending summary.
     *
     * ALGORITHM:
     * 1. Get start-of-day timestamp (today at midnight)
     * 2. Query all payments since midnight
     * 3. Group them by category
     * 4. For each category: sum the amounts, collect transaction details
     * 5. Sort categories by total (highest first)
     */
    fun generateDailySummary(dao: NotificationDao): DailySummary {
        // Get midnight today (start of day)
        val startOfDay = getStartOfDay()
        Log.d(TAG, "Generating daily summary from ${formatTime(startOfDay)}")

        val todayTransactions = dao.getTodayTransactions(startOfDay)
        Log.d(TAG, "Found ${todayTransactions.size} transactions today")

        if (todayTransactions.isEmpty()) {
            return DailySummary(totalSpent = 0.0, categories = emptyList(), transactionCount = 0)
        }

        // Group transactions by category — normalize bad/null categories
        val grouped = todayTransactions.groupBy { tx ->
            when {
                tx.category.isNullOrBlank() -> "Other"
                tx.category.equals("Unknown", ignoreCase = true) -> "Other"
                tx.category.equals("Uncategorized", ignoreCase = true) -> "Other"
                else -> tx.category
            }
        }

        val categories = grouped.map { (category, transactions) ->
            val items = transactions.map { tx ->
                val amt = tx.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                // Try to infer necessity from category name
                val necessity = guessNecessity(tx.category, tx.merchant)
                TransactionItem(
                    amount = amt,
                    merchant = tx.merchant ?: "Unknown",
                    aiInsight = tx.aiInsight,
                    time = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(Date(tx.timestamp)),
                    timestamp = tx.timestamp,
                    necessity = necessity
                )
            }
            CategoryBreakdown(
                category = category,
                total = items.sumOf { it.amount },
                items = items
            )
        }.sortedByDescending { it.total }  // biggest spend first

        val totalSpent = categories.sumOf { it.total }

        Log.d(TAG, "Daily total: ₹$totalSpent across ${categories.size} categories")

        return DailySummary(
            totalSpent = totalSpent,
            categories = categories,
            transactionCount = todayTransactions.size
        )
    }

    /**
     * Generates a weekly AI-powered behavioral summary.
     *
     * Instead of just numbers, we send the entire week's data to AI
     * and ask it to find PATTERNS:
     *   - "You order food mostly at 9 PM"
     *   - "You travel most on Monday mornings"
     *   - "60% of your spending is on food delivery"
     */
    fun generateWeeklySummary(dao: NotificationDao): String? {
        val startOfWeek = getStartOfWeek()
        val weekTransactions = dao.getWeekTransactions(startOfWeek)

        Log.d(TAG, "Generating weekly summary from ${weekTransactions.size} transactions")

        if (weekTransactions.size < 2) {
            return "Not enough transactions this week to generate insights. Keep using the app!"
        }

        // Build a text summary of all transactions for the AI
        val transactionLines = weekTransactions.mapNotNull { tx ->
            if (tx.amount == null) return@mapNotNull null
            val time = SimpleDateFormat("EEEE hh:mm a", Locale.getDefault())
                .format(Date(tx.timestamp))
            "₹${tx.amount} to ${tx.merchant ?: "unknown"} | Category: ${tx.category ?: "unknown"} | Time: $time | AI Note: ${tx.aiInsight ?: "none"}"
        }.joinToString("\n")

        val prompt = """
You are a personal finance behavioral analyst for an Indian user. Analyze their spending data from this week and generate 3-5 short, insightful observations about their spending PATTERNS and HABITS.

This week's transactions:
$transactionLines

Rules:
- Focus on PATTERNS (timing, frequency, categories), not just totals
- Be specific about days and times (e.g., "You tend to order food on weekday evenings around 9 PM")
- Mention if any category dominates spending
- Keep each insight to 1 line
- Be friendly and helpful, not judgmental
- If data is limited, say what you CAN observe

Respond with ONLY a JSON object (no markdown, no backticks):
{"insights": ["insight 1", "insight 2", "insight 3"]}
        """.trim()

        // Check API key before making the call
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_GROQ_KEY_HERE") {
            Log.w(TAG, "⚠️ No Groq API key — skipping weekly insight")
            return "AI insights unavailable. Set your GROQ_API_KEY in local.properties."
        }

        return try {
            val response = callGroqForInsight(prompt)
            Log.d(TAG, "Weekly AI response: $response")
            parseWeeklyResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Weekly insight failed: ${e.message}")
            "Could not generate weekly insights. Try again later."
        }
    }

    /**
     * Calls Groq AI — reuses the same API infrastructure as AiInsightEngine.
     */
    private fun callGroqForInsight(prompt: String): String {
        val apiKey = BuildConfig.GROQ_API_KEY
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 20000

        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.5)
            put("max_tokens", 500)
        }

        val writer = OutputStreamWriter(connection.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        if (connection.responseCode != 200) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown"
            throw Exception("HTTP ${connection.responseCode}: $error")
        }

        val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
        val json = JSONObject(response)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun parseWeeklyResponse(response: String): String {
        return try {
            val cleaned = response.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleaned)
            val insights = json.getJSONArray("insights")
            val lines = (0 until insights.length()).map { "• ${insights.getString(it)}" }
            lines.joinToString("\n")
        } catch (e: Exception) {
            response.take(500)  // fallback: just show raw text
        }
    }

    // ── Helper: Get midnight today ──
    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── Helper: Get start of this week (Monday) ──
    private fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // If today is before Monday (Sunday), go back one more week
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
    }

    // ═══════════════════════════════════════════════════════════════
    // TOP APPS BY SPENDING
    // ═══════════════════════════════════════════════════════════════

    data class AppSpending(
        val appName: String,
        val totalSpent: Double,
        val transactionCount: Int,
        val category: String
    )

    /**
     * Computes the top apps by total spending.
     * Groups all transactions by correlatedApp, sums amounts, and sorts descending.
     *
     * @param dao The notification DAO to query
     * @param limit Max number of apps to return (default 10)
     * @return List of AppSpending sorted by total spent
     */
    fun getTopAppsBySpending(dao: NotificationDao, limit: Int = 10): List<AppSpending> {
        val allTransactions = dao.getAllTransactionsWithAmount()

        // Filter to only transactions that have a valid correlated app
        // Exclude system apps (launcher, systemui) and "Unknown" entries
        val withApp = allTransactions.filter { tx ->
            val app = tx.correlatedApp
            !app.isNullOrBlank() &&
            !app.contains("launcher", ignoreCase = true) &&
            !app.equals("Unknown", ignoreCase = true) &&
            !app.contains("systemui", ignoreCase = true) &&
            !app.contains("settings", ignoreCase = true)
        }

        if (withApp.isEmpty()) {
            Log.d(TAG, "No correlated transactions found for top apps")
            return emptyList()
        }

        // Group by correlatedApp and compute totals
        val grouped = withApp.groupBy { it.correlatedApp!! }

        val appSpendings = grouped.map { (appName, transactions) ->
            val total = transactions.sumOf { tx ->
                tx.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            }
            val category = transactions.firstOrNull()?.category ?: "Uncategorized"
            AppSpending(
                appName = appName,
                totalSpent = total,
                transactionCount = transactions.size,
                category = category
            )
        }.sortedByDescending { it.totalSpent }
            .take(limit)

        Log.d(TAG, "Top apps: ${appSpendings.map { "${it.appName}: ₹${it.totalSpent}" }}")

        return appSpendings
    }

    /**
     * Infers whether a transaction is a "need" or "want" based on category/merchant.
     * This is a simple heuristic — the AI may override it if it provides necessity.
     */
    private fun guessNecessity(category: String?, merchant: String?): String? {
        val cat = (category ?: "").lowercase()
        val merch = (merchant ?: "").lowercase()
        return when {
            // Needs — essential spending
            cat.contains("transport") -> "need"
            cat.contains("grocery") || cat.contains("medicine") || cat.contains("health") -> "need"
            cat.contains("bill") || cat.contains("recharge") || cat.contains("utility") -> "need"
            cat.contains("rent") || cat.contains("housing") -> "need"
            cat.contains("education") -> "need"

            // Wants — discretionary spending
            cat.contains("food delivery") || merch.contains("zomato") || merch.contains("swiggy") -> "want"
            cat.contains("shopping") || cat.contains("entertainment") -> "want"
            cat.contains("personal") || cat.contains("salon") -> "want"
            cat.contains("travel") -> "want"  // Vacations are wants, commute is need (covered by transport)

            else -> null
        }
    }
}

