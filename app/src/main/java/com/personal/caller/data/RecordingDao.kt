package com.personal.caller.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE expiresAt < :currentTime")
    suspend fun getExpiredRecordings(currentTime: Long): List<RecordingEntity>

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAllRecordings(): List<RecordingEntity>
}
