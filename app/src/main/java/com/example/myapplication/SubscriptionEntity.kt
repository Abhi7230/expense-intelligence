package com.example.myapplication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores detected recurring subscriptions.
 *
 * EXAMPLES:
 *   - Netflix ₹649/month
 *   - Spotify ₹119/month
 *   - Airtel Recharge ₹299/month
 *
 * The SubscriptionDetector analyzes transaction history to find
 * recurring payments and saves them here.
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val merchantName: String,         // "Netflix", "Spotify", "Airtel"
    val normalizedName: String,       // Lowercase for matching
    val amount: Double,               // Typical amount (average)
    val frequency: String,            // "weekly", "monthly", "yearly"
    val category: String = "Subscription",
    val lastChargedAt: Long,          // Last payment timestamp
    val nextExpectedAt: Long,         // Predicted next charge
    val isActive: Boolean = true,     // User can disable tracking
    val timesDetected: Int = 1,       // How many times we've seen this
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

