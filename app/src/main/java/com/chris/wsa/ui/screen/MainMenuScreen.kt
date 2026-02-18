package com.chris.wsa.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.wsa.data.SavedPlaylist
import com.chris.wsa.ui.util.calculateTotalDuration
import com.chris.wsa.ui.util.formatDuration
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    allEvents: List<SavedPlaylist>,
    onOpenPlaylist: (SavedPlaylist) -> Unit,
    onQuickAddLatest: () -> Unit,
    onDeletePlaylist: (SavedPlaylist) -> Unit,
    onClearAllLsrg: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<SavedPlaylist?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear All LSRG") },
                                onClick = {
                                    showMenu = false
                                    showClearAllDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Button(
                    onClick = onQuickAddLatest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Latest LSRG")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(allEvents, key = { it.id }) { playlist ->
                EventCard(
                    playlist = playlist,
                    isLsrg = playlist.eventUrl != null,
                    onOpen = { onOpenPlaylist(playlist) },
                    onDelete = { showDeleteDialog = playlist }
                )
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Playlist?") },
            text = { Text("Are you sure you want to delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(playlist)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear all LSRG confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All LSRG?") },
            text = { Text("This will delete all saved LSRG playlists. Archive entries will reappear but without fetched audio.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLsrg()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EventCard(
    playlist: SavedPlaylist,
    isLsrg: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    val infoText = if (playlist.items.isEmpty()) {
                        "Not fetched"
                    } else {
                        val postWord = if (playlist.items.size == 1) "post" else "posts"
                        val durationText = formatDuration(calculateTotalDuration(playlist.items))
                        buildString {
                            append("${playlist.items.size} $postWord")
                            if (durationText.isNotEmpty()) append(" · $durationText")
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    playlist.postedAt?.let { ts ->
                        Text(
                            text = "  •  ${formatDate(ts)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Hide delete for LSRG playlists (both archive and Quick Add)
            if (!isLsrg) {
                IconButton(onClick = onDelete) {
                    Text("\uD83D\uDDD1", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
