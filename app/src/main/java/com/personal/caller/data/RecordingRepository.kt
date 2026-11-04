package com.personal.caller.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/** Manages files owned by this app; no broad storage permission is needed. */
class RecordingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val recordingsDirectory = appContext.getExternalFilesDir("recordings")
        ?: File(appContext.filesDir, "recordings")

    fun list(): List<RecordingEntry> = recordingsDirectory
        .listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension.equals("m4a", ignoreCase = true) }
        .map(::RecordingEntry)
        .sortedByDescending(RecordingEntry::createdAt)
        .toList()

    fun deleteExpired(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - RETENTION_MILLIS
        return recordingsDirectory.listFiles().orEmpty().count { file ->
            file.isFile && file.lastModified() <= cutoff && file.delete()
        }
    }

    fun delete(recording: RecordingEntry): Boolean = recording.file.delete()

    companion object {
        val RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(90)
    }
}
