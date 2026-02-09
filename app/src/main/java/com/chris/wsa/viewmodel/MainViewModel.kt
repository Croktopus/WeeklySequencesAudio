package com.chris.wsa.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.wsa.audio.LSRGFinder
import com.chris.wsa.audio.PlaylistBuilder
import com.chris.wsa.audio.WeeklyPostParser
import com.chris.wsa.data.PlaylistItem
import com.chris.wsa.data.PlaylistStorage
import com.chris.wsa.data.SavedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed class QuickAddState {
    object Idle : QuickAddState()
    data class Loading(val status: String) : QuickAddState()
    data class Success(val playlistId: String) : QuickAddState()
    data class Error(val message: String) : QuickAddState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val playlistStorage = PlaylistStorage(application)

    private val _playlists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val playlists: StateFlow<List<SavedPlaylist>> = _playlists.asStateFlow()

    private val _quickAddState = MutableStateFlow<QuickAddState>(QuickAddState.Idle)
    val quickAddState: StateFlow<QuickAddState> = _quickAddState.asStateFlow()

    init {
        refreshPlaylists()
    }

    fun refreshPlaylists() {
        _playlists.value = playlistStorage.getAllPlaylists()
    }

    fun savePlaylist(name: String, items: List<PlaylistItem>, eventUrl: String? = null, postedAt: Long? = null) {
        playlistStorage.savePlaylist(name, items, eventUrl, postedAt)
        refreshPlaylists()
    }

    fun deletePlaylist(id: String) {
        playlistStorage.deletePlaylist(id)
        refreshPlaylists()
    }

    fun dismissQuickAddStatus() {
        _quickAddState.value = QuickAddState.Idle
    }

    fun quickAddLatest() {
        viewModelScope.launch {
            _quickAddState.value = QuickAddState.Loading("Finding latest LSRG event...")

            val lsrgFinder = LSRGFinder()
            val eventUrl = lsrgFinder.findLatestEvent()

            if (eventUrl == null) {
                _quickAddState.value = QuickAddState.Error("Could not find latest event")
                return@launch
            }

            _quickAddState.value = QuickAddState.Loading("Parsing event...")
            val parser = WeeklyPostParser()
            val parsedEvent = parser.extractPostLinks(eventUrl)

            if (parsedEvent == null || parsedEvent.postLinks.isEmpty()) {
                _quickAddState.value = QuickAddState.Error("No posts found in event")
                return@launch
            }

            // Check if playlist already exists
            val alreadyExists = _playlists.value.any { it.name == parsedEvent.shortTitle }
            if (alreadyExists) {
                _quickAddState.value = QuickAddState.Error("Playlist '${parsedEvent.shortTitle}' already exists!")
                return@launch
            }

            _quickAddState.value = QuickAddState.Loading("Found ${parsedEvent.postLinks.size} posts, fetching audio...")

            val buildResult = PlaylistBuilder().buildFromPostUrls(parsedEvent.postLinks)

            if (buildResult.items.isNotEmpty()) {
                val postedAtMillis = parsedEvent.postedAt?.let { parseIsoDate(it) }
                val saved = playlistStorage.savePlaylist(parsedEvent.shortTitle, buildResult.items, eventUrl, postedAtMillis)
                refreshPlaylists()
                _quickAddState.value = QuickAddState.Success(playlistId = saved.id)
            } else {
                _quickAddState.value = QuickAddState.Error("No audio found for posts")
            }
        }
    }

    private fun parseIsoDate(iso: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(iso)?.time
        } catch (_: Exception) {
            null
        }
    }
}
