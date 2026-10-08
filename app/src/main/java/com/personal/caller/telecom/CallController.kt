package com.personal.caller.telecom

import android.telecom.Call
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

    private data class ManagedCall(val call: Call, val callback: Call.Callback)

    private val managedCalls = ConcurrentHashMap<String, ManagedCall>()
    private val _calls = MutableStateFlow<List<CallUiState>>(emptyList())
    val calls: StateFlow<List<CallUiState>> = _calls.asStateFlow()

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
