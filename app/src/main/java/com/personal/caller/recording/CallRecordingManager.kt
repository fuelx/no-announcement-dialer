package com.personal.caller.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.personal.caller.data.AppDatabase
import com.personal.caller.data.RecordingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CallRecordingManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null
    private var currentStartTime: Long = 0
    private var currentCallId: String? = null

    fun startRecording(callId: String) {
        if (isRecording) return
        try {
            currentCallId = callId
            currentStartTime = System.currentTimeMillis()
            
            val dir = File(context.getExternalFilesDir(null), "recordings")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, "${UUID.randomUUID()}.m4a")
            currentFilePath = file.absolutePath

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder?.apply {
                // AudioSource.VOICE_CALL is the only way to get both sides without speakerphone hack.
                // It requires CAPTURE_AUDIO_OUTPUT permission which must be granted via Shizuku/ADB.
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            Log.d("CallRecordingManager", "Recording started: $currentFilePath")

        } catch (e: Exception) {
            Log.e("CallRecordingManager", "Failed to start recording", e)
            isRecording = false
            recorder?.release()
            recorder = null
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.apply {
                stop()
                release()
            }
            isRecording = false
            
            val duration = System.currentTimeMillis() - currentStartTime
            val filePath = currentFilePath
            
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    saveRecordingToDatabase(
                        callId = currentCallId ?: UUID.randomUUID().toString(),
                        filePath = filePath,
                        duration = duration,
                        fileSize = file.length()
                    )
                } else {
                    file.delete()
                }
            }
            
            Log.d("CallRecordingManager", "Recording stopped and saved")
        } catch (e: Exception) {
            Log.e("CallRecordingManager", "Failed to stop recording", e)
        } finally {
            recorder = null
        }
    }

    private fun saveRecordingToDatabase(callId: String, filePath: String, duration: Long, fileSize: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val entity = RecordingEntity(
                id = UUID.randomUUID().toString(),
                callId = callId,
                contactId = null,
                phoneNumberHash = "hash",
                filePath = filePath,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000), // 90 Days
                duration = duration,
                fileSize = fileSize,
                codec = "AAC",
                sampleRate = 44100,
                channels = 2,
                status = "SUCCESS",
                notes = ""
            )
            db.recordingDao().insertRecording(entity)
        }
    }
}
