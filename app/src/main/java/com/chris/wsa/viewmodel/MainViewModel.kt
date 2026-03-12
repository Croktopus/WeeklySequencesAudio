package com.chris.wsa.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chris.wsa.audio.LSRGFinder
import com.chris.wsa.audio.PlaylistBuilder
import com.chris.wsa.audio.WeeklyPostParser
import com.chris.wsa.data.ArchiveLoader
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

sealed class FetchState {
    object Idle : FetchState()
    data class Loading(val status: String) : FetchState()
    object Success : FetchState()
    data class Error(val message: String) : FetchState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val playlistStorage = PlaylistStorage(application)

    private val archivePlaylists: List<SavedPlaylist> = ArchiveLoader.load(application)

    private val _savedPlaylists = MutableStateFlow<List<SavedPlaylist>>(emptyList())

    // All LSRG events: archive + user-added, deduped by eventUrl, sorted by postedAt desc
    private val _allEvents = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val allEvents: StateFlow<List<SavedPlaylist>> = _allEvents.asStateFlow()

    // Custom (non-LSRG) playlists: those without eventUrl
    private val _customPlaylists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val customPlaylists: StateFlow<List<SavedPlaylist>> = _customPlaylists.asStateFlow()

    private val _quickAddState = MutableStateFlow<QuickAddState>(QuickAddState.Idle)
    val quickAddState: StateFlow<QuickAddState> = _quickAddState.asStateFlow()

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    init {
        refreshPlaylists()
    }

    fun refreshPlaylists() {
        val saved = playlistStorage.getAllPlaylists()
        _savedPlaylists.value = saved

        // Split saved playlists into LSRG (has eventUrl) and custom (no eventUrl)
        val savedLsrg = saved.filter { it.eventUrl != null }
        val savedCustom = saved.filter { it.eventUrl == null }

        // Merge archive + saved LSRG playlists, dedup by eventUrl (saved takes priority)
        val savedEventUrls = savedLsrg.map { it.eventUrl }.toSet()
        val mergedEvents = savedLsrg + archivePlaylists.filter { it.eventUrl !in savedEventUrls }
        // Sort by LSRG number descending, fall back to postedAt for non-numbered entries
        _allEvents.value = mergedEvents.sortedWith(
            compareByDescending<SavedPlaylist> { parseLsrgNumber(it.name) ?: 0 }
                .thenByDescending { it.postedAt ?: 0L }
        )

        _customPlaylists.value = savedCustom.sortedByDescending { it.createdAt }
    }

    fun savePlaylist(name: String, items: List<PlaylistItem>, eventUrl: String? = null, postedAt: Long? = null) {
        playlistStorage.savePlaylist(name, items, eventUrl, postedAt)
        refreshPlaylists()
    }

    fun deletePlaylist(id: String) {
        playlistStorage.deletePlaylist(id)
        refreshPlaylists()
    }

    fun clearAllLsrg() {
        val saved = playlistStorage.getAllPlaylists()
        saved.filter { it.eventUrl != null }.forEach { playlistStorage.deletePlaylist(it.id) }
        refreshPlaylists()
    }

    fun getPlaylist(id: String): SavedPlaylist? {
        return _savedPlaylists.value.find { it.id == id }
            ?: archivePlaylists.find { it.id == id }
    }

    fun dismissQuickAddStatus() {
        _quickAddState.value = QuickAddState.Idle
    }

    fun dismissFetchState() {
        _fetchState.value = FetchState.Idle
    }

    fun fetchPlaylist(playlist: SavedPlaylist) {
        val eventUrl = playlist.eventUrl ?: return
        viewModelScope.launch {
            _fetchState.value = FetchState.Loading("Parsing event...")

            val parser = WeeklyPostParser()
            val parsedEvent = parser.extractPostLinks(eventUrl)

            if (parsedEvent == null || parsedEvent.links.isEmpty()) {
                _fetchState.value = FetchState.Error("No links found in event")
                return@launch
            }

            _fetchState.value = FetchState.Loading("Found ${parsedEvent.links.size} links, fetching audio...")

            val buildResult = PlaylistBuilder().buildFromLinks(parsedEvent.links)

            if (buildResult.items.isNotEmpty()) {
                playlistStorage.savePlaylistWithId(
                    id = playlist.id,
                    name = playlist.name,
                    items = buildResult.items,
                    eventUrl = eventUrl,
                    postedAt = playlist.postedAt
                )
                refreshPlaylists()
                _fetchState.value = FetchState.Success
            } else {
                _fetchState.value = FetchState.Error("No links resolved")
            }
        }
    }

    fun clearPlaylistItems(id: String) {
        playlistStorage.deletePlaylist(id)
        refreshPlaylists()
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

            if (parsedEvent == null || parsedEvent.links.isEmpty()) {
                _quickAddState.value = QuickAddState.Error("No links found in event")
                return@launch
            }

            // Check if playlist already exists with items (allow re-fetching empty ones)
            val existing = _allEvents.value.find { it.name == parsedEvent.shortTitle }
            if (existing != null && existing.items.isNotEmpty()) {
                _quickAddState.value = QuickAddState.Error("'${parsedEvent.shortTitle}' already exists")
                return@launch
            }

            _quickAddState.value = QuickAddState.Loading("Found ${parsedEvent.links.size} links, fetching audio...")

            val buildResult = PlaylistBuilder().buildFromLinks(parsedEvent.links)

            if (buildResult.items.isNotEmpty()) {
                val postedAtMillis = parsedEvent.postedAt?.let { parseIsoDate(it) }
                // Use existing ID if re-fetching an empty playlist, otherwise generate new
                val playlistId = existing?.id ?: java.util.UUID.randomUUID().toString()
                val saved = playlistStorage.savePlaylistWithId(playlistId, parsedEvent.shortTitle, buildResult.items, eventUrl, postedAtMillis)
                refreshPlaylists()
                _quickAddState.value = QuickAddState.Success(playlistId = saved.id)
            } else {
                _quickAddState.value = QuickAddState.Error("No links resolved")
            }
        }
    }

    private fun parseLsrgNumber(name: String): Int? {
        val match = Regex("""LSRG(\d+)\s""").find(name)
        return match?.groupValues?.get(1)?.toIntOrNull()
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
