package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Each row = one app session.
 * Example: User opened Zomato at 8:00 PM, closed at 8:06 PM → one row.
 *
 * Think of it as a log entry: "app X was in the foreground from time A to time B"
 */
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val app: String,        // package name, e.g. "com.zomato.order"
    val startTime: Long,    // epoch millis — when app came to foreground
    val endTime: Long,      // epoch millis — when app left foreground
    val duration: Long      // endTime - startTime (in milliseconds)
)

