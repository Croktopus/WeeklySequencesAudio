package com.chris.wsa.data

import android.content.Context
import android.content.SharedPreferences

class PlaybackPositionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "playback_positions",
        Context.MODE_PRIVATE
    )

    fun savePosition(trackUrl: String, positionMs: Long) {
        prefs.edit().putLong(trackUrl, positionMs).apply()
    }

    fun getPosition(trackUrl: String): Long {
        return prefs.getLong(trackUrl, 0L)
    }

    fun clearPosition(trackUrl: String) {
        prefs.edit().remove(trackUrl).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
