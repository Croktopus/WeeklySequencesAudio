package com.chris.wsa.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ArchiveLoader {

    private var cached: List<SavedPlaylist>? = null

    fun load(context: Context): List<SavedPlaylist> {
        cached?.let { return it }

        val json = context.assets.open("lsrg_archive.json")
            .bufferedReader()
            .use { it.readText() }

        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        val playlists: List<SavedPlaylist> = Gson().fromJson(json, type)
        cached = playlists
        return playlists
    }
}
