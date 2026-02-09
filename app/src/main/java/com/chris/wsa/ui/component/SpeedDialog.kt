package com.chris.wsa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpeedDialog(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                Text("${String.format("%.2f", playbackSpeed)}x")
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3f,
                    steps = 49
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x", style = MaterialTheme.typography.bodySmall)
                    Text("3.0x", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Reset")
            }
        }
    )
}
