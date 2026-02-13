package com.example.myapplication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores learned merchant aliases.
 * When user manually categorizes "Aayush Raj" as "Food → Street Food",
 * we save it here so next time we auto-apply it.
 *
 * EXAMPLE:
 *   User pays ₹50 to "RAMESH CHOWMEIN" (offline)
 *   → Popup appears → User selects "Food > Street Food"
 *   → We save: merchantName="RAMESH CHOWMEIN", category="Food", subcategory="Street Food"
 *   → Next time user pays to "RAMESH CHOWMEIN", we auto-categorize it!
 */
@Entity(
    tableName = "merchant_aliases",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val merchantName: String,         // Original: "RAMESH CHOWMEIN", "Aayush Raj"
    val normalizedName: String,       // Lowercase + trimmed for matching: "ramesh chowmein"
    val category: String,             // "Food", "Transport", "Shopping"
    val subcategory: String? = null,  // "Street Food", "Auto", "Groceries"
    val userNote: String? = null,     // Optional user note: "College canteen guy"
    val timesUsed: Int = 1,           // Increment each time auto-applied
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

