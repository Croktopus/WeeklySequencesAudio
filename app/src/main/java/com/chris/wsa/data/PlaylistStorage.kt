package com.chris.wsa.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "playlists",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun getAllPlaylists(): List<SavedPlaylist> {
        val playlistsJson = prefs.getString("all_playlists", null) ?: return emptyList()
        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        return try {
            gson.fromJson<List<SavedPlaylist>>(playlistsJson, type).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistStorage", "Error loading playlists", e)
            emptyList()
        }
    }

    fun savePlaylist(name: String, items: List<PlaylistItem>): SavedPlaylist {
        val playlists = getAllPlaylists().toMutableList()
        val playlist = SavedPlaylist(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            items = items
        )
        playlists.add(playlist)
        saveAllPlaylists(playlists)
        return playlist
    }

    fun deletePlaylist(id: String) {
        val playlists = getAllPlaylists().toMutableList()
        playlists.removeAll { it.id == id }
        saveAllPlaylists(playlists)
    }

    fun getPlaylist(id: String): SavedPlaylist? {
        return getAllPlaylists().find { it.id == id }
    }

    private fun saveAllPlaylists(playlists: List<SavedPlaylist>) {
        val json = gson.toJson(playlists)
        prefs.edit().putString("all_playlists", json).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
