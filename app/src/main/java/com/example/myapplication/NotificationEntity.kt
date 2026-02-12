package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val app: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val amount: String? = null,
    val merchant: String? = null,
    val mode: String? = null,

    // Step 7: Correlation results
    val category: String? = null,
    val correlatedApp: String? = null,
    val confidence: String? = null,

    // Step 8: AI-generated "digital memory"
    val aiInsight: String? = null    // "Late evening street food dinner from a local stall"
)
