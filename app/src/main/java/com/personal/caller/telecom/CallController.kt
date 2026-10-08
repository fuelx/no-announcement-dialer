package com.personal.caller.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the live Telecom [Call] objects in the process that owns the
 * InCallService. Activities and notification actions must never try to put a
 * Call in an Intent because it is not parcelable.
 */
object CallController {
    data class CallUiState(
        val id: String,
        val number: String,
        val state: Int,
        val isIncoming: Boolean
    ) {
        val isRinging: Boolean get() = state == Call.STATE_RINGING
        val isOngoing: Boolean get() = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
    }

    data class AudioUiState(
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val canUseSpeaker: Boolean = false
    )

    private data class ManagedCall(val call: Call, val callback: Call.Callback)

    private val managedCalls = ConcurrentHashMap<String, ManagedCall>()
    private val _calls = MutableStateFlow<List<CallUiState>>(emptyList())
    val calls: StateFlow<List<CallUiState>> = _calls.asStateFlow()
    private val _audio = MutableStateFlow(AudioUiState())
    val audio: StateFlow<AudioUiState> = _audio.asStateFlow()
    @Volatile private var inCallService: InCallService? = null

    fun attach(service: InCallService) {
        inCallService = service
    }

    fun detach(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
            _audio.value = AudioUiState()
        }
    }

    fun add(call: Call): String {
        val id = callId(call)
        if (managedCalls.containsKey(id)) return id

        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                publish()
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                publish()
            }
        }
        managedCalls[id] = ManagedCall(call, callback)
        call.registerCallback(callback)
        publish()
        return id
    }

    fun remove(call: Call) {
        val id = callId(call)
        managedCalls.remove(id)?.let { managed ->
            runCatching { managed.call.unregisterCallback(managed.callback) }
        }
        publish()
    }

    fun answer(id: String) {
        managedCalls[id]?.call?.answer(0)
    }

    fun reject(id: String) {
        managedCalls[id]?.call?.reject(false, null)
    }

    fun disconnect(id: String) {
        managedCalls[id]?.call?.disconnect()
    }

    fun updateAudioState(audioState: CallAudioState) {
        _audio.value = AudioUiState(
            isMuted = audioState.isMuted,
            isSpeakerOn = audioState.route and CallAudioState.ROUTE_SPEAKER != 0,
            canUseSpeaker = audioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0
        )
    }

    fun toggleMute() {
        val state = _audio.value
        inCallService?.setMuted(!state.isMuted)
    }

    fun toggleSpeaker() {
        val state = _audio.value
        if (state.canUseSpeaker) {
            inCallService?.setAudioRoute(
                if (state.isSpeakerOn) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER
            )
        }
    }

    fun latestCall(): CallUiState? = calls.value.firstOrNull()

    private fun publish() {
        _calls.value = managedCalls.map { (id, managed) ->
            val details = managed.call.details
            CallUiState(
                id = id,
                number = details.handle?.schemeSpecificPart ?: "Unknown number",
                state = managed.call.state,
                isIncoming = details.callDirection == Call.Details.DIRECTION_INCOMING
            )
        }.sortedByDescending { it.isRinging }
    }

    private fun callId(call: Call): String = System.identityHashCode(call).toString()
}
