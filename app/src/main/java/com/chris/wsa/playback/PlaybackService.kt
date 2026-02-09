package com.chris.wsa.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chris.wsa.data.PlaylistItem
import com.chris.wsa.data.PlaybackPositionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    val playlistManager = PlaylistManager()
    private lateinit var positionManager: PlaybackPositionManager
    private lateinit var notificationHelper: NotificationHelper
    private var audioBecomingNoisyReceiver: BroadcastReceiver? = null

    companion object {
        private const val POSITION_SAVE_INTERVAL_MS = 5000L
    }

    private val binder = PlaybackBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicSaveJob: Job? = null

    inner class PlaybackBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()

        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()

        positionManager = PlaybackPositionManager(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notificationHelper.updateNotification(player, playlistManager)

                if (isPlaying) {
                    startPeriodicPositionSave()
                } else {
                    stopPeriodicPositionSave()
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playlistManager.getCurrentItem()?.let {
                        positionManager.clearPosition(it.mp3Url)
                    }

                    if (playlistManager.currentIndex.value < playlistManager.playlist.value.size - 1) {
                        playlistManager.next()
                        playlistManager.getCurrentItem()?.let { item ->
                            loadAndPlayTrack(item, resumePosition = true)
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    saveCurrentPosition()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setAvailablePlayerCommands(playerCommands)
                        .build()
                }
            })
            .build()

        registerAudioBecomingNoisyReceiver()
    }

    private fun registerAudioBecomingNoisyReceiver() {
        audioBecomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        android.util.Log.d("PlaybackService", "Audio becoming noisy - pausing playback")
                        player.pause()
                        saveCurrentPosition()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        android.util.Log.d("PlaybackService", "Bluetooth disconnected - pausing playback")
                        player.pause()
                        saveCurrentPosition()
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            audioBecomingNoisyReceiver,
            intentFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterAudioBecomingNoisyReceiver() {
        audioBecomingNoisyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered, ignore
            }
            audioBecomingNoisyReceiver = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == MediaSessionService.SERVICE_INTERFACE) {
            super.onBind(intent)
        } else {
            binder
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        player.pause()
        saveCurrentPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun startForegroundNotification() {
        notificationHelper.startForegroundNotification(player, playlistManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> player.play()
            "PAUSE" -> player.pause()
            "STOP" -> {
                player.pause()
                saveCurrentPosition()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "PREVIOUS" -> {
                if (playlistManager.currentIndex.value > 0) {
                    playlistManager.previous()
                    playlistManager.getCurrentItem()?.let { loadAndPlayTrack(it, resumePosition = true) }
                }
            }
            "NEXT" -> {
                if (playlistManager.currentIndex.value < playlistManager.playlist.value.size - 1) {
                    playlistManager.next()
                    playlistManager.getCurrentItem()?.let { loadAndPlayTrack(it, resumePosition = true) }
                }
            }
        }
        return START_STICKY
    }

    fun loadAndPlayTrack(item: PlaylistItem, resumePosition: Boolean = true) {
        if (player.mediaItemCount == 0) {
            startForegroundNotification()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.author)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(item.mp3Url)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        if (resumePosition) {
            val savedPosition = positionManager.getPosition(item.mp3Url)
            if (savedPosition > 0) {
                player.seekTo(savedPosition)
                android.util.Log.d("PlaybackService", "Resuming from position: $savedPosition")
            }
        }

        player.play()
    }

    private fun saveCurrentPosition() {
        playlistManager.getCurrentItem()?.let { item ->
            val position = player.currentPosition
            if (position > 0 && position < player.duration - 5000) {
                positionManager.savePosition(item.mp3Url, position)
                android.util.Log.d("PlaybackService", "Saved position: $position for ${item.title}")
            }
        }
    }

    private fun startPeriodicPositionSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = serviceScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }

    private fun stopPeriodicPositionSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    fun getPositionManager(): PlaybackPositionManager = positionManager

    fun getPlayer(): ExoPlayer = player

    override fun onDestroy() {
        saveCurrentPosition()
        serviceScope.cancel()
        unregisterAudioBecomingNoisyReceiver()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player.release()
        super.onDestroy()
    }
}
