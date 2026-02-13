package com.example.myapplication

import androidx.room.*

@Dao
interface MerchantAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alias: MerchantAliasEntity): Long

    @Query("SELECT * FROM merchant_aliases WHERE normalizedName = :normalizedName LIMIT 1")
    fun findByName(normalizedName: String): MerchantAliasEntity?

    @Query("SELECT * FROM merchant_aliases ORDER BY timesUsed DESC, lastUsedAt DESC")
    fun getAllSortedByUsage(): List<MerchantAliasEntity>

    @Query("SELECT * FROM merchant_aliases ORDER BY lastUsedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): List<MerchantAliasEntity>

    @Query("UPDATE merchant_aliases SET timesUsed = timesUsed + 1, lastUsedAt = :now WHERE id = :id")
    fun incrementUsage(id: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM merchant_aliases WHERE id = :id")
    fun deleteById(id: Int)

    @Query("DELETE FROM merchant_aliases")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM merchant_aliases")
    fun getCount(): Int
}

