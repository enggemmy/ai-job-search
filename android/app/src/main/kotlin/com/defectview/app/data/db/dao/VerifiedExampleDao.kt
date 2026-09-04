package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.defectview.app.data.db.entity.VerifiedExampleEntity
import com.defectview.domain.model.Trade
import kotlinx.coroutines.flow.Flow

@Dao
interface VerifiedExampleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(example: VerifiedExampleEntity): Long

    @Query("SELECT * FROM verified_examples ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VerifiedExampleEntity>>

    @Query("SELECT * FROM verified_examples")
    suspend fun getAllForSimilaritySearch(): List<VerifiedExampleEntity>

    @Query("SELECT * FROM verified_examples WHERE trade = :trade ORDER BY createdAt DESC")
    fun observeByTrade(trade: Trade): Flow<List<VerifiedExampleEntity>>

    @Query("SELECT COUNT(*) FROM verified_examples")
    fun observeCount(): Flow<Int>

    @Query("SELECT trade, COUNT(*) as count FROM verified_examples GROUP BY trade")
    fun observeCountByTrade(): Flow<List<TradeCount>>
}

data class TradeCount(val trade: Trade, val count: Int)
