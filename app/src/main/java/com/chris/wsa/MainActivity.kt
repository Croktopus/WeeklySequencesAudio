package com.chris.wsa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chris.wsa.playback.PlaybackService
import com.chris.wsa.playback.PlaylistManager
import com.chris.wsa.ui.navigation.AppNavigation
import com.chris.wsa.ui.theme.WeeklySequencesAudioTheme

class MainActivity : ComponentActivity() {
    private var player: androidx.media3.exoplayer.ExoPlayer? = null
    private var playlistManager: PlaylistManager? = null
    private val serviceBound = mutableStateOf(false)
    private var playbackService: PlaybackService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.PlaybackBinder
            val svc = binder.getService()
            playbackService = svc
            player = svc.getPlayer()
            playlistManager = svc.playlistManager
            serviceBound.value = true

            svc.startForegroundNotification()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound.value = false
            player = null
            playlistManager = null
            playbackService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val serviceIntent = Intent(this, PlaybackService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        val sharedUrl = when {
            intent?.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            intent?.action == Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }?.takeIf { url ->
            url.startsWith("https://www.lesswrong.com/") || url.startsWith("https://lesswrong.com/")
        }

        setContent {
            WeeklySequencesAudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isBound by serviceBound
                    val playerState = remember { mutableStateOf(player) }
                    val playlistManagerState = remember { mutableStateOf(playlistManager) }

                    LaunchedEffect(isBound) {
                        if (isBound) {
                            playerState.value = player
                            playlistManagerState.value = playlistManager
                        }
                    }

                    if (playerState.value != null && playlistManagerState.value != null && playbackService != null) {
                        AppNavigation(
                            player = playerState.value!!,
                            playlistManager = playlistManagerState.value!!,
                            playbackService = playbackService!!,
                            initialUrl = sharedUrl
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound.value) {
            unbindService(serviceConnection)
            serviceBound.value = false
        }
    }
}
