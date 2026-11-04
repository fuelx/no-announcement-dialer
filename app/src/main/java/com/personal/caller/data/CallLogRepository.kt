package com.personal.caller.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

enum class CallKind { INCOMING, OUTGOING, MISSED, OTHER }

data class CallLogEntry(
    val id: Long,
    val phoneNumber: String?,
    val displayName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val kind: CallKind
)

class CallLogRepository(private val context: Context) {
    fun getRecentCalls(limit: Int = 200): List<CallLogEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CallLogEntry(
                                id = cursor.getLong(0),
                                phoneNumber = cursor.getString(1),
                                displayName = cursor.getString(2),
                                timestamp = cursor.getLong(4),
                                durationSeconds = cursor.getLong(5),
                                kind = callKind(cursor.getInt(3))
                            )
                        )
                    }
                }
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun callKind(type: Int): CallKind = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallKind.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE,
        CallLog.Calls.REJECTED_TYPE,
        CallLog.Calls.BLOCKED_TYPE -> CallKind.MISSED
        else -> CallKind.OTHER
    }
}
