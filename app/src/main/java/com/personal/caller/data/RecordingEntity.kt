package com.personal.caller.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val callId: String,
    val contactId: String?,
    val phoneNumberHash: String,
    val filePath: String,
    val createdAt: Long,
    val expiresAt: Long,
    val duration: Long,
    val fileSize: Long,
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val status: String,
    val notes: String
)
