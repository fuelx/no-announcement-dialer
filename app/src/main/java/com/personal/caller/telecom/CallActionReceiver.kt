package com.personal.caller.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_ANSWER -> CallController.answer(id)
            ACTION_REJECT -> CallController.reject(id)
            ACTION_DISCONNECT -> CallController.disconnect(id)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.personal.caller.action.ANSWER"
        const val ACTION_REJECT = "com.personal.caller.action.REJECT"
        const val ACTION_DISCONNECT = "com.personal.caller.action.DISCONNECT"
        const val EXTRA_CALL_ID = "call_id"
    }
}
