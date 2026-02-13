package com.example.myapplication

import android.util.Log

/**
 * Detects recurring subscription payments from transaction history.
 *
 * ALGORITHM:
 * 1. Look at all transactions for the past 90 days
 * 2. Group by merchant name (normalized)
 * 3. For each merchant, check if amounts are similar (within 15% variance)
 * 4. Check if dates are roughly periodic (weekly/monthly/yearly)
 * 5. If 2+ similar charges detected → mark as subscription
 *
 * EXAMPLES DETECTED:
 *   - Netflix ₹649 on 1st of every month
 *   - Spotify ₹119 on 15th of every month
 *   - YouTube Premium ₹129/month
 *   - Airtel Recharge ₹299 every 28 days
 */
object SubscriptionDetector {

    private val TAG = "SUBSCRIPTION"

    // Known subscription services — gives higher confidence
    private val KNOWN_SUBSCRIPTIONS = setOf(
        "netflix", "spotify", "youtube", "hotstar", "prime", "amazon prime",
        "zee5", "sonyliv", "jiocinema", "apple", "icloud", "google one",
        "linkedin", "medium", "notion", "figma", "canva", "adobe",
        "chatgpt", "openai", "github", "dropbox", "evernote",
        "airtel", "jio", "vi", "vodafone", "bsnl", "tatasky", "dth"
    )

    data class DetectedSubscription(
        val merchant: String,
        val averageAmount: Double,
        val frequency: String,           // "weekly", "monthly", "yearly"
        val confidence: String,          // "high", "medium", "low"
        val occurrences: Int,
        val lastChargedAt: Long,
        val nextExpectedAt: Long
    )

    /**
     * Analyzes transaction history and returns detected subscriptions.
     *
     * @param transactions All transactions with amounts
     * @return List of detected subscriptions sorted by occurrence count
     */
    fun detectSubscriptions(transactions: List<NotificationEntity>): List<DetectedSubscription> {
        // Filter to last 90 days
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val recent = transactions.filter {
            it.amount != null && it.timestamp >= ninetyDaysAgo
        }

        if (recent.size < 4) {
            Log.d(TAG, "Not enough transactions for subscription detection")
            return emptyList()
        }

        // Group by normalized merchant name
        val grouped = recent.groupBy { tx ->
            normalizeMerchantName(tx.merchant ?: "unknown")
        }.filter { it.key.isNotBlank() && it.key.length > 2 }

        val subscriptions = mutableListOf<DetectedSubscription>()

        grouped.forEach { (normalizedMerchant, txList) ->
            if (txList.size < 2) return@forEach  // need at least 2 occurrences

            val amounts = txList.mapNotNull {
                it.amount?.replace(",", "")?.toDoubleOrNull()
            }
            if (amounts.isEmpty()) return@forEach

            val avgAmount = amounts.average()
            val minAmount = amounts.minOrNull() ?: 0.0
            val maxAmount = amounts.maxOrNull() ?: 0.0

            // Check if amounts are similar (within 15% variance)
            val amountVariance = if (avgAmount > 0) (maxAmount - minAmount) / avgAmount else 1.0
            if (amountVariance > 0.15) {
                Log.d(TAG, "Skipping $normalizedMerchant: amount variance too high ($amountVariance)")
                return@forEach
            }

            // Check timing — calculate average days between charges
            val sortedTimestamps = txList.map { it.timestamp }.sorted()
            val gaps = sortedTimestamps.zipWithNext { a, b ->
                (b - a) / (24.0 * 60 * 60 * 1000)  // days
            }
            if (gaps.isEmpty()) return@forEach

            val avgGapDays = gaps.average()
            val frequency = when {
                avgGapDays in 5.0..9.0 -> "weekly"
                avgGapDays in 20.0..40.0 -> "monthly"
                avgGapDays in 340.0..400.0 -> "yearly"
                else -> {
                    Log.d(TAG, "Skipping $normalizedMerchant: irregular frequency (${avgGapDays} days)")
                    return@forEach
                }
            }

            // Calculate confidence
            val isKnown = KNOWN_SUBSCRIPTIONS.any { normalizedMerchant.contains(it) }
            val confidence = when {
                isKnown && txList.size >= 3 -> "high"
                isKnown || txList.size >= 3 -> "medium"
                else -> "low"
            }

            // Predict next charge
            val lastCharge = sortedTimestamps.last()
            val avgGapMs = (avgGapDays * 24 * 60 * 60 * 1000).toLong()
            val nextExpected = lastCharge + avgGapMs

            subscriptions.add(
                DetectedSubscription(
                    merchant = txList.first().merchant ?: normalizedMerchant,
                    averageAmount = avgAmount,
                    frequency = frequency,
                    confidence = confidence,
                    occurrences = txList.size,
                    lastChargedAt = lastCharge,
                    nextExpectedAt = nextExpected
                )
            )

            Log.d(
                TAG, "📅 Detected subscription: ${txList.first().merchant} " +
                        "→ ₹${avgAmount.toInt()} $frequency ($confidence confidence)"
            )
        }

        return subscriptions.sortedByDescending { it.occurrences }
    }

    /**
     * Saves detected subscriptions to the database.
     */
    fun saveDetectedSubscriptions(dao: SubscriptionDao, detected: List<DetectedSubscription>) {
        detected.forEach { sub ->
            val normalized = normalizeMerchantName(sub.merchant)
            val existing = dao.findByName(normalized)

            if (existing != null) {
                // Update existing subscription
                dao.updateCharge(
                    id = existing.id,
                    lastCharged = sub.lastChargedAt,
                    nextExpected = sub.nextExpectedAt
                )
            } else {
                // Insert new subscription
                dao.insert(
                    SubscriptionEntity(
                        merchantName = sub.merchant,
                        normalizedName = normalized,
                        amount = sub.averageAmount,
                        frequency = sub.frequency,
                        lastChargedAt = sub.lastChargedAt,
                        nextExpectedAt = sub.nextExpectedAt,
                        timesDetected = sub.occurrences
                    )
                )
            }
        }
    }

    /**
     * Normalizes merchant name for consistent matching.
     */
    private fun normalizeMerchantName(name: String): String {
        return name.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9]"), "")  // remove special chars
    }

    /**
     * Get total monthly subscription cost.
     */
    fun calculateMonthlyBurn(subscriptions: List<DetectedSubscription>): Double {
        return subscriptions.sumOf { sub ->
            when (sub.frequency) {
                "weekly" -> sub.averageAmount * 4.33  // ~4.33 weeks/month
                "monthly" -> sub.averageAmount
                "yearly" -> sub.averageAmount / 12
                else -> 0.0
            }
        }
    }
}

