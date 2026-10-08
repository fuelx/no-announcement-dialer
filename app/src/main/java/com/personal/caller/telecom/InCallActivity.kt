package com.personal.caller.telecom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.caller.ui.theme.PersonalCallerTheme

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            PersonalCallerTheme {
                val calls by CallController.calls.collectAsState()
                val requestedId = intent.getStringExtra(CallActionReceiver.EXTRA_CALL_ID)
                val call = calls.firstOrNull { it.id == requestedId } ?: calls.firstOrNull()

                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) finish()
                }

                if (call != null) {
                    InCallScreen(call)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun InCallScreen(call: CallController.CallUiState) {
    val stateText = when {
        call.isRinging -> "Incoming call"
        call.isOngoing -> "In call"
        else -> "Calling…"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(call.number, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stateText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(56.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (call.isRinging) {
                CallButton("Decline", Color(0xFFB3261E)) { CallController.reject(call.id) }
                CallButton("Answer", Color(0xFF146C2E)) { CallController.answer(call.id) }
            } else {
                CallButton("End call", Color(0xFFB3261E)) { CallController.disconnect(call.id) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CallButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.size(132.dp, 56.dp)
    ) { Text(label) }
}
