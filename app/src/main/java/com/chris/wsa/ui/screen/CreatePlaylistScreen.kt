package com.chris.wsa.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.wsa.audio.AudioResolver
import com.chris.wsa.audio.AudioResult
import com.chris.wsa.audio.PlaylistBuilder
import com.chris.wsa.audio.WeeklyPostParser
import com.chris.wsa.data.PlaylistItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun CreatePlaylistScreen(
    initialUrl: String?,
    onPlaylistCreated: (String, List<PlaylistItem>, String?, Long?) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var playlistName by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf(initialUrl ?: "") }
    var tempPlaylist by remember { mutableStateOf(listOf<PlaylistItem>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var parsedEventUrl by remember { mutableStateOf<String?>(null) }
    var parsedEventPostedAt by remember { mutableStateOf<Long?>(null) }

    val resolver = remember { AudioResolver() }
    val playlistBuilder = remember { PlaylistBuilder() }

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
                text = "Create Playlist",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            label = { Text("Playlist Name") },
            placeholder = { Text("e.g., Week #66 - Tuesday 1/21") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Event URL or Post URL") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = "Fetching audio..."

                        when (val result = resolver.getAudioUrl(urlInput)) {
                            is AudioResult.Success -> {
                                val item = PlaylistItem(
                                    url = urlInput,
                                    title = result.title,
                                    author = result.author,
                                    mp3Url = result.mp3Url,
                                    source = result.source
                                )
                                tempPlaylist = tempPlaylist + item
                                statusMessage = "Added: ${result.title}"
                                urlInput = ""
                            }
                            is AudioResult.Error -> {
                                statusMessage = "Error: ${result.message}"
                            }
                        }

                        isLoading = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading && urlInput.isNotBlank()
            ) {
                Text("Add Single Post")
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = "Parsing event..."

                        val parser = WeeklyPostParser()
                        val parsedEvent = parser.extractPostLinks(urlInput)

                        if (parsedEvent == null || parsedEvent.links.isEmpty()) {
                            statusMessage = "No links found in URL"
                            isLoading = false
                            return@launch
                        }

                        // Auto-fill the playlist name if empty
                        if (playlistName.isBlank()) {
                            playlistName = parsedEvent.shortTitle
                        }

                        statusMessage = "Found ${parsedEvent.links.size} links, fetching audio..."
                        parsedEventUrl = urlInput
                        parsedEventPostedAt = parsedEvent.postedAt?.let { iso ->
                            try {
                                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                format.timeZone = TimeZone.getTimeZone("UTC")
                                format.parse(iso)?.time
                            } catch (_: Exception) { null }
                        }

                        val buildResult = playlistBuilder.buildFromLinks(parsedEvent.links)
                        tempPlaylist = tempPlaylist + buildResult.items

                        val audioCount = buildResult.items.count { it.hasAudio }
                        statusMessage = "Added ${buildResult.items.size} items ($audioCount with audio)"
                        urlInput = ""
                        isLoading = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading && urlInput.isNotBlank()
            ) {
                Text("Load Event")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Posts (${tempPlaylist.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (tempPlaylist.isNotEmpty()) {
                Button(
                    onClick = {
                        if (playlistName.isBlank()) {
                            statusMessage = "Please enter a playlist name"
                        } else {
                            onPlaylistCreated(playlistName, tempPlaylist, parsedEventUrl, parsedEventPostedAt)
                        }
                    }
                ) {
                    Text("Save Playlist")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tempPlaylist) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. ${item.title}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (item.author.isNotBlank()) {
                                Text(
                                    text = "by ${item.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (item.hasAudio) item.source else "Read-only link",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        IconButton(
                            onClick = {
                                tempPlaylist = tempPlaylist.filterIndexed { i, _ -> i != index }
                            }
                        ) {
                            Text("🗑")
                        }
                    }
                }
            }
        }
    }
}
