package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert
    fun insertNotification(notification: NotificationEntity): Long  // returns the auto-generated row ID

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun getById(id: Int): NotificationEntity?

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

    // Get ALL transactions with amounts (for top apps analysis)
    @Query("SELECT * FROM notifications WHERE amount IS NOT NULL ORDER BY timestamp DESC")
    fun getAllTransactionsWithAmount(): List<NotificationEntity>

    // Get transactions with bad/unknown correlation that need to be re-processed
    @Query("""
        SELECT * FROM notifications 
        WHERE amount IS NOT NULL 
        AND (category IS NULL 
             OR category = 'Unknown' 
             OR correlatedApp LIKE '%launcher%')
        ORDER BY timestamp DESC
    """)
    fun getBadlyCorrelatedTransactions(): List<NotificationEntity>

    // Smart dedup: find a recent transaction with the same amount (within a time window)
    // Used to check if a bank debit SMS duplicates an existing GPay/PhonePe notification
    @Query("SELECT * FROM notifications WHERE amount = :amount AND timestamp >= :since LIMIT 1")
    fun findRecentByAmount(amount: String, since: Long): NotificationEntity?

    // Delete ALL data (for privacy "Delete All" button)
    @Query("DELETE FROM notifications")
    fun deleteAll()
}
