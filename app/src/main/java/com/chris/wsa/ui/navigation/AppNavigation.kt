package com.chris.wsa.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.chris.wsa.viewmodel.FetchState
import com.chris.wsa.viewmodel.QuickAddState
import kotlinx.coroutines.launch

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

    val allEvents by mainViewModel.allEvents.collectAsState()
    val customPlaylists by mainViewModel.customPlaylists.collectAsState()
    val quickAddState by mainViewModel.quickAddState.collectAsState()
    val fetchState by mainViewModel.fetchState.collectAsState()
    val currentPlaylist by playlistManager.playlist.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // Drawer header
                Text(
                    text = "Weekly Sequences Audio",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                // Create New Playlist
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("Create New Playlist") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("create_playlist")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Custom playlists section
                if (customPlaylists.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Custom Playlists",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    customPlaylists.forEach { playlist ->
                        NavigationDrawerItem(
                            label = { Text(playlist.name) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("playlist_detail/${playlist.id}")
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    ) {
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
                            allEvents = allEvents,
                            onOpenPlaylist = { savedPlaylist ->
                                navController.navigate("playlist_detail/${savedPlaylist.id}")
                            },
                            onDeletePlaylist = { playlist ->
                                mainViewModel.deletePlaylist(playlist.id)

                                // If the deleted playlist is currently playing, stop and clear it
                                val currentItems = playlistManager.playlist.value
                                if (currentItems.isNotEmpty()) {
                                    val playlistUrls = playlist.items.mapNotNull { it.mp3Url }.toSet()
                                    val currentUrls = currentItems.mapNotNull { it.mp3Url }.toSet()

                                    if (playlistUrls.isNotEmpty() && playlistUrls == currentUrls) {
                                        player.pause()
                                        player.stop()
                                        playlistManager.clear()
                                    }
                                }
                            },
                            onClearAllLsrg = {
                                mainViewModel.clearAllLsrg()
                                player.pause()
                                player.stop()
                                playlistManager.clear()
                            },
                            onQuickAddLatest = {
                                mainViewModel.quickAddLatest()
                            },
                            onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            }
                        )
                    }

                    composable(
                        "playlist_detail/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                        // Derive from reactive allEvents so recomposition happens after fetch/clear
                        val playlist = remember(allEvents, customPlaylists, playlistId) {
                            mainViewModel.getPlaylist(playlistId)
                        }

                        if (playlist != null) {
                            // Reset fetch state when navigating away
                            DisposableEffect(playlistId) {
                                onDispose { mainViewModel.dismissFetchState() }
                            }

                            PlaylistDetailScreen(
                                playlist = playlist,
                                player = player,
                                playlistManager = playlistManager,
                                positionManager = playbackService.getPositionManager(),
                                fetchState = fetchState,
                                onBack = { navController.popBackStack() },
                                onPlayAll = { startIndex ->
                                    val audioItems = playlist.items.filter { it.hasAudio }
                                    if (audioItems.isNotEmpty()) {
                                        playerViewModel.startPlaylist(audioItems, startIndex)
                                        navController.navigate("player")
                                    }
                                },
                                onPlayTrack = { index ->
                                    val clickedItem = playlist.items[index]
                                    if (clickedItem.hasAudio) {
                                        val audioItems = playlist.items.filter { it.hasAudio }
                                        val audioIndex = audioItems.indexOf(clickedItem)
                                        if (audioIndex >= 0) {
                                            playerViewModel.startPlaylist(audioItems, audioIndex)
                                        }
                                    }
                                },
                                onFetchPlaylist = { mainViewModel.fetchPlaylist(playlist) },
                                onClearPlaylist = { mainViewModel.clearPlaylistItems(playlist.id) },
                                onDismissFetchState = { mainViewModel.dismissFetchState() }
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
                        title = { Text("Add Latest LSRG") },
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
}
