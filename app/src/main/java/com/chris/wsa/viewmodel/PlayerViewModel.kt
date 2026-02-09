package com.chris.wsa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.chris.wsa.data.PlaylistItem
import com.chris.wsa.playback.PlaybackService
import com.chris.wsa.playback.PlaylistManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1f
)

class PlayerViewModel : ViewModel() {

    private var player: ExoPlayer? = null
    private var playbackService: PlaybackService? = null
    private var playlistManager: PlaylistManager? = null

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    fun attachPlayer(player: ExoPlayer, service: PlaybackService) {
        this.player = player
        this.playbackService = service
        this.playlistManager = service.playlistManager
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    _playerState.value = _playerState.value.copy(
                        isPlaying = p.isPlaying,
                        currentPosition = p.currentPosition,
                        duration = p.duration.coerceAtLeast(0),
                        isBuffering = p.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    )
                }
                delay(100)
            }
        }
    }

    fun playPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun playTrack(item: PlaylistItem, resumePosition: Boolean = true) {
        playbackService?.loadAndPlayTrack(item, resumePosition)
    }

    fun previous() {
        playlistManager?.let { pm ->
            pm.previous()
            pm.getCurrentItem()?.let { item ->
                playbackService?.loadAndPlayTrack(item)
            }
        }
    }

    fun next() {
        playlistManager?.let { pm ->
            pm.next()
            pm.getCurrentItem()?.let { item ->
                playbackService?.loadAndPlayTrack(item)
            }
        }
    }

    fun selectAndPlay(index: Int) {
        playlistManager?.let { pm ->
            pm.selectItem(index)
            pm.getCurrentItem()?.let { item ->
                playbackService?.loadAndPlayTrack(item, resumePosition = true)
            }
        }
    }
}
