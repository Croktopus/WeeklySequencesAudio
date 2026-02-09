package com.chris.wsa.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.chris.wsa.playback.PlaybackService
import com.chris.wsa.playback.PlaylistManager
import com.chris.wsa.ui.component.PlayerControls
import com.chris.wsa.ui.component.SpeedDialog
import com.chris.wsa.ui.component.TrackInfoCard
import com.chris.wsa.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    playlistManager: PlaylistManager,
    playbackService: PlaybackService,
    playerViewModel: PlayerViewModel,
    playlistName: String?,
    onBack: () -> Unit
) {
    val playlist by playlistManager.playlist.collectAsState()
    val currentIndex by playlistManager.currentIndex.collectAsState()
    val uiState by playerViewModel.playerState.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }

    val positionManager = remember { playbackService.getPositionManager() }

    // Listen to player state for buffering detection
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // state is polled by ViewModel
                }
            }
        }
        player.addListener(listener)

        // Auto-play first item if nothing is loaded
        if (playlist.isNotEmpty() && player.mediaItemCount == 0) {
            playbackService.loadAndPlayTrack(playlist[0])
        }

        onDispose {
            player.removeListener(listener)
        }
    }

    val currentItem = playlist.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlistName ?: "Now Playing",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (playlistName != null && currentItem != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "by ${currentItem.author}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            TextButton(onClick = onBack) {
                Text("← Back")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentItem != null) {
            TrackInfoCard(item = currentItem)

            Spacer(modifier = Modifier.height(24.dp))

            PlayerControls(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                playbackSpeed = uiState.playbackSpeed,
                currentIndex = currentIndex,
                playlistSize = playlist.size,
                onSeek = { playerViewModel.seekTo(it) },
                onPlayPause = { playerViewModel.playPause() },
                onPrevious = { playerViewModel.previous() },
                onNext = { playerViewModel.next() },
                onShowSpeedDialog = { showSpeedDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(playlist) { index, item ->
                    val savedPosition = remember(item.mp3Url) {
                        positionManager.getPosition(item.mp3Url)
                    }
                    val hasProgress = savedPosition > 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerViewModel.selectAndPlay(index)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == currentIndex) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${index + 1}. ${item.title}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "by ${item.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasProgress) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Resume at ${formatTime(savedPosition)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        SpeedDialog(
            playbackSpeed = uiState.playbackSpeed,
            onSpeedChange = { playerViewModel.setSpeed(it) },
            onReset = { playerViewModel.setSpeed(1f) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
