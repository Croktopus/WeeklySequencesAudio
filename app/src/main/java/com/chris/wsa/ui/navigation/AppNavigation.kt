package com.chris.wsa.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chris.wsa.playback.PlaybackService
import com.chris.wsa.playback.PlaylistManager
import com.chris.wsa.ui.component.MiniPlayer
import com.chris.wsa.ui.screen.CreatePlaylistScreen
import com.chris.wsa.ui.screen.MainMenuScreen
import com.chris.wsa.ui.screen.PlayerScreen
import com.chris.wsa.viewmodel.MainViewModel
import com.chris.wsa.viewmodel.PlayerViewModel
import com.chris.wsa.viewmodel.QuickAddState

@Composable
fun AppNavigation(
    player: ExoPlayer,
    playlistManager: PlaylistManager,
    playbackService: PlaybackService,
    initialUrl: String?
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()

    // Attach player to PlayerViewModel once
    LaunchedEffect(player) {
        playerViewModel.attachPlayer(player, playbackService)
    }

    val playlists by mainViewModel.playlists.collectAsState()
    val quickAddState by mainViewModel.quickAddState.collectAsState()

    // Track the current playlist name for display in PlayerScreen
    var currentPlaylistName by remember { mutableStateOf<String?>(null) }

    // If there's a shared URL, go directly to playlist creator
    val startDestination = if (initialUrl != null) "create_playlist" else "main_menu"

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("main_menu") {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars),
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                MiniPlayer(
                                    player = player,
                                    playlistManager = playlistManager,
                                    onClick = { navController.navigate("player") }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        MainMenuScreen(
                            playlists = playlists,
                            onCreateNew = {
                                navController.navigate("create_playlist")
                            },
                            onPlayPlaylist = { savedPlaylist ->
                                playlistManager.clear()
                                savedPlaylist.items.forEach { item ->
                                    playlistManager.addItem(item)
                                }
                                currentPlaylistName = savedPlaylist.name
                                navController.navigate("player")
                            },
                            onDeletePlaylist = { playlist ->
                                mainViewModel.deletePlaylist(playlist.id)

                                // If the deleted playlist is currently playing, stop and clear it
                                val currentPlaylist = playlistManager.playlist.value
                                if (currentPlaylist.isNotEmpty()) {
                                    val playlistItems = playlist.items.map { it.mp3Url }.toSet()
                                    val currentItems = currentPlaylist.map { it.mp3Url }.toSet()

                                    if (playlistItems == currentItems) {
                                        player.pause()
                                        player.stop()
                                        playlistManager.clear()
                                        currentPlaylistName = null
                                    }
                                }
                            },
                            onQuickAddLatest = {
                                mainViewModel.quickAddLatest()
                            }
                        )
                    }
                }
            }

            composable("create_playlist") {
                CreatePlaylistScreen(
                    initialUrl = initialUrl,
                    onPlaylistCreated = { name, items ->
                        mainViewModel.savePlaylist(name, items)

                        navController.navigate("main_menu") {
                            popUpTo("main_menu") { inclusive = true }
                        }
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }

            composable("player") {
                PlayerScreen(
                    player = player,
                    playlistManager = playlistManager,
                    playbackService = playbackService,
                    playerViewModel = playerViewModel,
                    playlistName = currentPlaylistName,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Quick add loading dialog
        val currentQuickAddState = quickAddState
        if (currentQuickAddState is QuickAddState.Loading) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Quick Add Latest") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentQuickAddState.status)
                    }
                },
                confirmButton = { }
            )
        }

        // Quick add status card
        if (currentQuickAddState is QuickAddState.Success || currentQuickAddState is QuickAddState.Error) {
            val context = LocalContext.current
            val isSuccess = currentQuickAddState is QuickAddState.Success
            val message = when (currentQuickAddState) {
                is QuickAddState.Success -> currentQuickAddState.message
                is QuickAddState.Error -> currentQuickAddState.message
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.systemBars),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSuccess) "✓ $message" else message,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                mainViewModel.dismissQuickAddStatus()
                            }) {
                                Text("OK")
                            }
                        }

                        if (currentQuickAddState is QuickAddState.Success) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    val eventUrl = currentQuickAddState.eventUrl
                                    if (eventUrl.startsWith("https://www.lesswrong.com/") || eventUrl.startsWith("https://lesswrong.com/")) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(eventUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔗 View Event Post")
                            }
                        }
                    }
                }
            }
        }
    }
}
