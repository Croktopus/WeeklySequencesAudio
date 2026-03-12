package com.chris.wsa.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = onBack) {
                Text("← Back")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current

        if (currentItem != null) {
            TrackInfoCard(
                item = currentItem,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.url)))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Previous / Next side-by-side
            val hasPrev = currentIndex > 0
            val hasNext = currentIndex < playlist.size - 1

            if (hasPrev || hasNext) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasPrev) {
                        val prevItem = playlist[currentIndex - 1]
                        val prevPosition = remember(prevItem.mp3Url) {
                            prevItem.mp3Url?.let { positionManager.getPosition(it) } ?: 0L
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { playerViewModel.selectAndPlay(currentIndex - 1) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Previous",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = prevItem.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "by ${prevItem.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (prevPosition > 0) {
                                    Text(
                                        text = "Resume at ${formatTime(prevPosition)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (hasNext) {
                        val nextItem = playlist[currentIndex + 1]
                        val nextPosition = remember(nextItem.mp3Url) {
                            nextItem.mp3Url?.let { positionManager.getPosition(it) } ?: 0L
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { playerViewModel.selectAndPlay(currentIndex + 1) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Up Next",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = nextItem.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "by ${nextItem.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (nextPosition > 0) {
                                    Text(
                                        text = "Resume at ${formatTime(nextPosition)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
