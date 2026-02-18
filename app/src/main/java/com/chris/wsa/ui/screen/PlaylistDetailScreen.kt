package com.chris.wsa.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chris.wsa.ui.util.calculateTotalDuration
import com.chris.wsa.ui.util.formatDuration
import androidx.media3.exoplayer.ExoPlayer
import com.chris.wsa.data.PlaybackPositionManager
import com.chris.wsa.data.SavedPlaylist
import com.chris.wsa.playback.PlaylistManager
import com.chris.wsa.viewmodel.FetchState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: SavedPlaylist,
    player: ExoPlayer,
    playlistManager: PlaylistManager,
    positionManager: PlaybackPositionManager,
    fetchState: FetchState,
    onBack: () -> Unit,
    onPlayAll: (startIndex: Int) -> Unit,
    onPlayTrack: (index: Int) -> Unit,
    onFetchPlaylist: () -> Unit,
    onClearPlaylist: () -> Unit,
    onDismissFetchState: () -> Unit
) {
    val context = LocalContext.current
    val currentPlaylist by playlistManager.playlist.collectAsState()
    val currentIndex by playlistManager.currentIndex.collectAsState()
    val currentlyPlayingUrl = currentPlaylist.getOrNull(currentIndex)?.mp3Url
    val isEmpty = playlist.items.isEmpty()

    val audioItems = remember(playlist.items) { playlist.items.filter { it.hasAudio } }
    val audioCount = audioItems.size

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top bar with back button and optional overflow menu
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (!isEmpty) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Playlist") },
                                onClick = {
                                    showMenu = false
                                    onClearPlaylist()
                                }
                            )
                        }
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            // Header section
            item {
                Column {
                    // Playlist name card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (playlist.eventUrl != null) {
                                    Modifier.clickable {
                                        val url = playlist.eventUrl
                                        if (url.startsWith("https://")) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                } else Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val summaryText = if (isEmpty) {
                                "Not fetched \u00B7 Posted ${formatDate(playlist.postedAt ?: playlist.createdAt)}"
                            } else {
                                val durationText = formatDuration(calculateTotalDuration(playlist.items))
                                buildString {
                                    append("$audioCount audio \u00B7 ${playlist.items.size} total")
                                    if (durationText.isNotEmpty()) append(" \u00B7 $durationText")
                                    append(" \u00B7 Posted ${formatDate(playlist.postedAt ?: playlist.createdAt)}")
                                }
                            }
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (isEmpty) {
                // Fetch button and state display
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (fetchState) {
                            is FetchState.Loading -> {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = fetchState.status,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is FetchState.Error -> {
                                Text(
                                    text = fetchState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = onFetchPlaylist) {
                                    Text("Retry")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onFetchPlaylist,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Fetch Playlist")
                                }
                            }
                        }
                    }
                }
            } else {
                // Play All button (only if there are audio items)
                if (audioCount > 0) {
                    item {
                        Column {
                            Button(
                                onClick = {
                                    val resumeIndex = audioItems.indexOfFirst { item ->
                                        item.mp3Url?.let { positionManager.getPosition(it) > 0 } == true
                                    }.takeIf { it >= 0 } ?: 0
                                    onPlayAll(resumeIndex)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play All")
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Track list
                itemsIndexed(playlist.items) { index, item ->
                    if (item.hasAudio) {
                        // Audio item
                        val savedPosition = remember(item.mp3Url) {
                            item.mp3Url?.let { positionManager.getPosition(it) } ?: 0L
                        }
                        val hasProgress = savedPosition > 0
                        val isCurrentlyPlaying = item.mp3Url == currentlyPlayingUrl

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onPlayTrack(index) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentlyPlaying) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrentlyPlaying) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Now playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
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
                                    val trackDuration = formatDuration(item.durationMs)
                                    if (trackDuration.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = trackDuration,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = "Read post",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Non-audio item
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Article,
                                        contentDescription = "Read-only link",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (item.author.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "by ${item.author}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = "Read post",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacing for mini player
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
