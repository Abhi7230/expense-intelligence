package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppUsageDao {

    @Insert
    fun insertUsage(usage: AppUsageEntity)

    @Query("SELECT * FROM app_usage ORDER BY startTime DESC")
    fun getAllUsage(): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage WHERE app = :packageName ORDER BY startTime DESC")
    fun getUsageForApp(packageName: String): List<AppUsageEntity>

    @Query("SELECT COUNT(*) FROM app_usage")
    fun getCount(): Int

    // Step 7: Get app usage within a time window
    // Used by the CorrelationEngine to find what apps were active before a payment
    @Query("SELECT * FROM app_usage WHERE endTime >= :fromTime AND startTime <= :toTime ORDER BY duration DESC")
    fun getUsageInWindow(fromTime: Long, toTime: Long): List<AppUsageEntity>
}
