package com.personal.caller.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.caller.data.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingCleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        RecordingRepository(applicationContext).deleteExpired()
        Result.success()
    }
}
