package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert
    fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL ORDER BY timestamp DESC")
    fun getParsedTransactions(): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications")
    fun getCount(): Int

    // Step 7: Update correlation results
    @Query("UPDATE notifications SET category = :category, correlatedApp = :correlatedApp, confidence = :confidence WHERE id = :id")
    fun updateCorrelation(id: Int, category: String, correlatedApp: String?, confidence: String)

    // Step 8: Update AI insight
    @Query("UPDATE notifications SET aiInsight = :aiInsight WHERE id = :id")
    fun updateAiInsight(id: Int, aiInsight: String)

    // ── Step 9: Queries for Insight Dashboard ──

    // Get today's payments (amount is not null + timestamp is after midnight today)
    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayTransactions(startOfDay: Long): List<NotificationEntity>

    // Get this week's payments
    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL AND timestamp >= :startOfWeek ORDER BY timestamp DESC")
    fun getWeekTransactions(startOfWeek: Long): List<NotificationEntity>
}
