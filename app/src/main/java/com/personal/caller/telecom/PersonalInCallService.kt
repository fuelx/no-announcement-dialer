package com.personal.caller.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.personal.caller.recording.CallRecordingManager

class PersonalInCallService : InCallService() {

    private lateinit var recordingManager: CallRecordingManager

    override fun onCreate() {
        super.onCreate()
        recordingManager = CallRecordingManager(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("PersonalInCallService", "Call added: ${call.details.handle}")
        call.registerCallback(callCallback)
        
        // TODO: Start UI or recording depending on call state
        if (call.state == Call.STATE_RINGING) {
            Log.d("PersonalInCallService", "Incoming Call")
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("PersonalInCallService", "Call removed")
        call.unregisterCallback(callCallback)
        
        recordingManager.stopRecording()
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d("PersonalInCallService", "Call state changed to $state")
            if (state == Call.STATE_ACTIVE) {
                val callId = call.details?.handle?.schemeSpecificPart ?: "unknown"
                Log.d("PersonalInCallService", "Call Active - Trigger Recording")
                recordingManager.startRecording(callId)
            }
        }
    }
}
