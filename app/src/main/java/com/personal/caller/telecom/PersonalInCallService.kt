package com.personal.caller.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.personal.caller.MainActivity

/**
 * The system binds this service only after the app has been selected as the
 * default dialer. Keep it deliberately small and never do audio work on a
 * Telecom callback: blocking or crashing here can make the system disconnect
 * an otherwise valid call.
 */
class PersonalInCallService : InCallService() {
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val notificationCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming and active phone calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = CallController.add(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                showCallNotification(id)
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                showCallNotification(id)
            }
        }
        notificationCallbacks[call] = callback
        call.registerCallback(callback)
        showCallNotification(id)
        Log.i(TAG, "Call added: $id")
    }

    override fun onCallRemoved(call: Call) {
        notificationCallbacks.remove(call)?.let { callback ->
            runCatching { call.unregisterCallback(callback) }
        }
        val id = System.identityHashCode(call).toString()
        CallController.remove(call)
        notificationManager.cancel(notificationId(id))
        super.onCallRemoved(call)
        Log.i(TAG, "Call removed: $id")
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        val destination = if (showDialpad) MainActivity::class.java else InCallActivity::class.java
        val intent = Intent(this, destination).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            CallController.latestCall()?.id?.let { putExtra(CallActionReceiver.EXTRA_CALL_ID, it) }
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not bring call UI to foreground", it) }
    }

    private fun showCallNotification(id: String) {
        val call = CallController.calls.value.firstOrNull { it.id == id } ?: return
        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            putExtra(CallActionReceiver.EXTRA_CALL_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this, notificationId(id), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(call.number)
            .setContentText(if (call.isRinging) "Incoming call" else if (call.isOngoing) "In call" else "Calling…")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setOngoing(!call.isRinging)
            .setAutoCancel(call.isRinging)

        if (call.isRinging) {
            builder
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", actionIntent(CallActionReceiver.ACTION_REJECT, id))
                .addAction(android.R.drawable.sym_action_call, "Answer", actionIntent(CallActionReceiver.ACTION_ANSWER, id))
                .setFullScreenIntent(contentIntent, true)
        } else {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End call",
                actionIntent(CallActionReceiver.ACTION_DISCONNECT, id)
            )
        }
        notificationManager.notify(notificationId(id), builder.build())
    }

    private fun actionIntent(action: String, id: String): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).apply {
            this.action = action
            putExtra(CallActionReceiver.EXTRA_CALL_ID, id)
        }
        val requestCode = 31 * notificationId(id) + action.hashCode()
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(id: String): Int = id.hashCode() and 0x7fffffff

    companion object {
        private const val TAG = "PersonalInCallService"
        private const val CHANNEL_ID = "active_calls"
    }
}
