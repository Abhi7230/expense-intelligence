package com.example.myapplication

import androidx.room.*

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(subscription: SubscriptionEntity): Long

    @Query("SELECT * FROM subscriptions WHERE normalizedName = :normalizedName LIMIT 1")
    fun findByName(normalizedName: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE isActive = 1 ORDER BY nextExpectedAt ASC")
    fun getActiveSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions ORDER BY lastChargedAt DESC")
    fun getAllSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT SUM(amount) FROM subscriptions WHERE isActive = 1 AND frequency = 'monthly'")
    fun getMonthlyTotal(): Double?

    @Query("UPDATE subscriptions SET isActive = :isActive, updatedAt = :now WHERE id = :id")
    fun setActive(id: Int, isActive: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET lastChargedAt = :lastCharged, nextExpectedAt = :nextExpected, timesDetected = timesDetected + 1, updatedAt = :now WHERE id = :id")
    fun updateCharge(id: Int, lastCharged: Long, nextExpected: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM subscriptions WHERE id = :id")
    fun deleteById(id: Int)

    @Query("DELETE FROM subscriptions")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM subscriptions WHERE isActive = 1")
    fun getActiveCount(): Int
}

