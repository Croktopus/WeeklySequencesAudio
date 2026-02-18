package com.chris.wsa.ui.util

import com.chris.wsa.data.PlaylistItem

fun formatDuration(millis: Long?): String {
    if (millis == null || millis <= 0) return ""

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun calculateTotalDuration(items: List<PlaylistItem>): Long? {
    val durations = items
        .filter { it.mp3Url != null }
        .mapNotNull { it.durationMs }

    return if (durations.isEmpty()) null else durations.sum()
}
