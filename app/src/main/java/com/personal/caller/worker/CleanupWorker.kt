package com.personal.caller.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.caller.data.AppDatabase
import java.io.File

class CleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("CleanupWorker", "Starting 90-day recording cleanup")
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.recordingDao()
        
        val currentTime = System.currentTimeMillis()
        val expiredRecordings = dao.getExpiredRecordings(currentTime)
        
        var deletedCount = 0
        for (recording in expiredRecordings) {
            val file = File(recording.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    dao.deleteRecording(recording)
                    deletedCount++
                    Log.d("CleanupWorker", "Deleted recording: ${recording.id}")
                }
            } else {
                // File missing, just remove DB entry
                dao.deleteRecording(recording)
            }
        }
        
        Log.d("CleanupWorker", "Cleanup finished. Deleted $deletedCount recordings.")
        return Result.success()
    }
}
