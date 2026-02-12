package com.example.myapplication

import android.util.Log

/**
 * THE CORRELATION ENGINE — the brain of your expense tracker.
 *
 * For each payment, it answers: "WHY did this payment happen?"
 *
 * ALGORITHM (in plain English):
 *   1. A payment notification arrives at time T
 *   2. Look at all apps used between (T - 10 minutes) and T
 *   3. Filter out system junk (keyboard, launcher, settings)
 *   4. Score each remaining app:
 *      - Known commerce app (Zomato, Uber)? → +80 points
 *      - Used for a long time? → up to +20 points
 *      - Used right before the payment? → up to +20 points
 *   5. Highest score wins → that app caused the payment
 *   6. No apps found? → offline purchase, try to guess from merchant name
 */
object CorrelationEngine {

    private val TAG = "CORRELATION"

    /**
     * The result of correlating a payment with app usage.
     */
    data class CorrelationResult(
        val correlatedApp: String?,       // friendly name: "Zomato" (null if offline)
        val correlatedPackage: String?,   // package name: "com.application.zomato" (null if offline)
        val category: String,             // "Food Delivery", "Transport", "Offline Purchase", etc.
        val confidence: String,           // "high", "medium", "low"
        val reason: String                // human-readable: "User used Zomato for 480s before payment"
    )

    // 10 minutes in milliseconds — this is our "lookback window"
    private const val WINDOW_MS = 10 * 60 * 1000L

    /**
     * Main function: correlates a payment notification with recent app usage.
     *
     * @param transaction  The payment notification (must have amount != null)
     * @param recentUsages All app usage sessions from the database
     *                     (we'll filter to the relevant window ourselves)
     */
    fun correlate(
        transaction: NotificationEntity,
        recentUsages: List<AppUsageEntity>
    ): CorrelationResult {

        val paymentTime = transaction.timestamp
        val windowStart = paymentTime - WINDOW_MS

        Log.d(TAG, "━━━ Correlating payment: ₹${transaction.amount} to ${transaction.merchant} ━━━")

        // ── STEP 1: Filter to 10-minute window + remove junk apps ──
        val relevantUsages = recentUsages.filter { usage ->
            // App must have been active within the window
            // (started OR ended within the 10 minutes before payment)
            val overlaps = usage.endTime >= windowStart && usage.startTime <= paymentTime
            val isRelevant = AppKnowledgeBase.isRelevantApp(usage.app)

            overlaps && isRelevant
        }

        Log.d(TAG, "Found ${relevantUsages.size} relevant app(s) in 10-min window")

        // ── STEP 2: No apps found → offline purchase ──
        if (relevantUsages.isEmpty()) {
            val category = guessCategoryFromText(transaction)
            Log.d(TAG, "No app activity → offline purchase (guessed: $category)")

            return CorrelationResult(
                correlatedApp = null,
                correlatedPackage = null,
                category = category,
                confidence = "low",
                reason = "No app activity found in 10-min window → likely offline purchase"
            )
        }

        // ── STEP 3: Score each app ──
        val scored = relevantUsages.map { usage ->
            val appInfo = AppKnowledgeBase.getAppInfo(usage.app)
            val score = calculateScore(usage, appInfo, paymentTime)
            Log.d(TAG, "  ${appInfo?.friendlyName ?: usage.app} → score: $score (duration: ${usage.duration / 1000}s)")
            Triple(usage, appInfo, score)
        }.sortedByDescending { it.third }  // highest score first

        // ── STEP 4: Pick the winner ──
        val bestMatch = scored.first()
        val winnerUsage = bestMatch.first
        val winnerInfo = bestMatch.second
        val winnerScore = bestMatch.third

        val friendlyName = winnerInfo?.friendlyName
            ?: winnerUsage.app.split(".").lastOrNull()
            ?: "Unknown"

        val category = winnerInfo?.category ?: "Unknown"

        val confidence = when {
            winnerScore >= 80 -> "high"
            winnerScore >= 40 -> "medium"
            else -> "low"
        }

        val durationSec = winnerUsage.duration / 1000
        val reason = "User used $friendlyName for ${durationSec}s before payment (score: $winnerScore)"

        Log.d(TAG, "✅ Winner: $friendlyName ($category) — confidence: $confidence")
        Log.d(TAG, "   Reason: $reason")

        return CorrelationResult(
            correlatedApp = friendlyName,
            correlatedPackage = winnerUsage.app,
            category = category,
            confidence = confidence,
            reason = reason
        )
    }

    /**
     * Scores how likely an app usage session caused the payment.
     *
     * SCORING BREAKDOWN:
     *   - Known commerce app?        → +50 points
     *   - Transactional category?    → +30 points  (food, transport, shopping, etc.)
     *   - Long usage?                → up to +20 points
     *   - Used close to payment?     → up to +20 points
     *
     * Max possible score = 120
     */
    private fun calculateScore(
        usage: AppUsageEntity,
        appInfo: AppKnowledgeBase.AppInfo?,
        paymentTime: Long
    ): Int {
        var score = 0

        // ── FACTOR 1: Is it a known app? ──
        if (appInfo != null) {
            score += 50  // recognized app = big bonus

            // Extra points for apps where money is typically spent
            val transactionalCategories = listOf(
                "Food Delivery", "Transport", "Shopping", "Groceries", "Travel"
            )
            if (appInfo.category in transactionalCategories) {
                score += 30
            }
        }

        // ── FACTOR 2: How long was the app used? ──
        val durationSec = usage.duration / 1000
        score += when {
            durationSec >= 60 -> 20    // 1+ minute → very likely browsing/ordering
            durationSec >= 30 -> 15
            durationSec >= 10 -> 10
            else -> 5                  // brief open
        }

        // ── FACTOR 3: How close to the payment? ──
        // If the app was closed 30 seconds before payment → very likely the cause
        // If it was closed 9 minutes ago → less likely
        val timeBetween = paymentTime - usage.endTime
        score += when {
            timeBetween < 60_000 -> 20        // within 1 min
            timeBetween < 180_000 -> 10       // within 3 min
            timeBetween < 600_000 -> 5        // within 10 min
            else -> 0
        }

        return score
    }

    /**
     * FALLBACK: When no app was found, try to guess the category
     * purely from the merchant name or notification text.
     *
     * This handles offline purchases like:
     *   "₹40 paid to RAMESH CHOWMEIN" → Food
     *   "₹120 paid to AUTO STAND" → Transport
     */
    private fun guessCategoryFromText(transaction: NotificationEntity): String {
        val merchant = transaction.merchant?.lowercase() ?: ""
        val text = transaction.text.lowercase()
        val combined = "$merchant $text"

        return when {
            listOf("zomato", "swiggy", "food", "restaurant", "cafe", "pizza", "burger",
                "chowmein", "biryani", "chai", "tea", "coffee", "bakery", "dhaba",
                "kitchen", "meals", "tiffin", "juice", "eat")
                .any { it in combined } -> "Food"

            listOf("uber", "ola", "rapido", "cab", "auto", "ride", "trip", "metro",
                "bus", "transport", "parking", "petrol", "diesel", "fuel")
                .any { it in combined } -> "Transport"

            listOf("amazon", "flipkart", "myntra", "shop", "store", "mart", "retail",
                "mall", "bazaar", "market")
                .any { it in combined } -> "Shopping"

            listOf("bigbasket", "zepto", "blinkit", "grocery", "vegetables", "fruits",
                "kirana", "supermarket")
                .any { it in combined } -> "Groceries"

            listOf("electricity", "water", "gas", "bill", "recharge", "airtel", "jio",
                "vodafone", "bsnl", "broadband", "wifi", "insurance", "emi")
                .any { it in combined } -> "Utilities / Bills"

            listOf("hospital", "medical", "pharmacy", "medicine", "doctor", "clinic",
                "health", "lab", "diagnostic")
                .any { it in combined } -> "Healthcare"

            listOf("movie", "cinema", "pvr", "inox", "netflix", "hotstar", "spotify",
                "subscription")
                .any { it in combined } -> "Entertainment"

            else -> "Offline Purchase"
        }
    }
}

