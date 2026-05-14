package com.facemorphapp.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MorphResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(entity: MorphResultEntity): Long

    @Query("SELECT * FROM morph_results ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentResults(limit: Int): Flow<List<MorphResultEntity>>

    @Query("DELETE FROM morph_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM morph_results")
    suspend fun deleteAll()
}
