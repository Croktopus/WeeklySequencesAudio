package com.chris.wsa.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chris.wsa.playback.PlaybackService
import com.chris.wsa.playback.PlaylistManager
import com.chris.wsa.ui.component.MiniPlayer
import com.chris.wsa.ui.screen.CreatePlaylistScreen
import com.chris.wsa.ui.screen.MainMenuScreen
import com.chris.wsa.ui.screen.PlayerScreen
import com.chris.wsa.ui.screen.PlaylistDetailScreen
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
    val currentPlaylist by playlistManager.playlist.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // If there's a shared URL, go directly to playlist creator
    val startDestination = if (initialUrl != null) "create_playlist" else "main_menu"

    // Track current route for mini player visibility
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show mini player when playlist is loaded and not on the full player screen
    val showMiniPlayer = currentPlaylist.isNotEmpty() && currentRoute != "player"

    // Quick Add Success → navigate to playlist detail
    LaunchedEffect(quickAddState) {
        val state = quickAddState
        if (state is QuickAddState.Success) {
            navController.navigate("playlist_detail/${state.playlistId}")
            mainViewModel.dismissQuickAddStatus()
        } else if (state is QuickAddState.Error) {
            snackbarHostState.showSnackbar(state.message)
            mainViewModel.dismissQuickAddStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showMiniPlayer) {
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable("main_menu") {
                    MainMenuScreen(
                        playlists = playlists,
                        onCreateNew = {
                            navController.navigate("create_playlist")
                        },
                        onOpenPlaylist = { savedPlaylist ->
                            navController.navigate("playlist_detail/${savedPlaylist.id}")
                        },
                        onDeletePlaylist = { playlist ->
                            mainViewModel.deletePlaylist(playlist.id)

                            // If the deleted playlist is currently playing, stop and clear it
                            val currentItems = playlistManager.playlist.value
                            if (currentItems.isNotEmpty()) {
                                val playlistUrls = playlist.items.map { it.mp3Url }.toSet()
                                val currentUrls = currentItems.map { it.mp3Url }.toSet()

                                if (playlistUrls == currentUrls) {
                                    player.pause()
                                    player.stop()
                                    playlistManager.clear()
                                }
                            }
                        },
                        onQuickAddLatest = {
                            mainViewModel.quickAddLatest()
                        }
                    )
                }

                composable(
                    "playlist_detail/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    val playlist = playlists.find { it.id == playlistId }

                    if (playlist != null) {
                        PlaylistDetailScreen(
                            playlist = playlist,
                            player = player,
                            playlistManager = playlistManager,
                            positionManager = playbackService.getPositionManager(),
                            onBack = { navController.popBackStack() },
                            onPlayAll = { startIndex ->
                                playerViewModel.startPlaylist(playlist.items, startIndex)
                                navController.navigate("player")
                            },
                            onPlayTrack = { index ->
                                playerViewModel.startPlaylist(playlist.items, index)
                                navController.navigate("player")
                            }
                        )
                    } else {
                        // Playlist was deleted, go back
                        LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    }
                }

                composable("create_playlist") {
                    CreatePlaylistScreen(
                        initialUrl = initialUrl,
                        onPlaylistCreated = { name, items, eventUrl, postedAt ->
                            mainViewModel.savePlaylist(name, items, eventUrl, postedAt)

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
        }
    }
}
