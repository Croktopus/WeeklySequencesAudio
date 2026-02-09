package com.chris.wsa.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.ExoPlayer
import com.chris.wsa.MainActivity

class NotificationHelper(private val service: PlaybackService) {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "playback_channel"
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }

            val notificationManager = service.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startForegroundNotification(player: ExoPlayer, playlistManager: PlaylistManager) {
        try {
            val notification = createNotification(player, playlistManager)
            service.startForeground(NOTIFICATION_ID, notification)
            android.util.Log.d("PlaybackService", "Started foreground notification")
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Failed to start foreground", e)
        }
    }

    fun updateNotification(player: ExoPlayer, playlistManager: PlaylistManager) {
        if (player.currentMediaItem != null) {
            val notification = createNotification(player, playlistManager)
            val notificationManager = service.getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun createNotification(player: ExoPlayer, playlistManager: PlaylistManager): Notification {
        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentTitle = player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: playlistManager.getCurrentItem()?.title
            ?: "No track"

        val currentArtist = player.currentMediaItem?.mediaMetadata?.artist?.toString()
            ?: playlistManager.getCurrentItem()?.author
            ?: "LSRG Audio"

        val isPlaying = player.isPlaying

        // Play/Pause action
        val playPauseIntent = if (isPlaying) {
            PendingIntent.getService(
                service, 1,
                Intent(service, PlaybackService::class.java).apply {
                    action = "PAUSE"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                service, 1,
                Intent(service, PlaybackService::class.java).apply {
                    action = "PLAY"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Previous action
        val previousIntent = PendingIntent.getService(
            service, 2,
            Intent(service, PlaybackService::class.java).apply {
                action = "PREVIOUS"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next action
        val nextIntent = PendingIntent.getService(
            service, 3,
            Intent(service, PlaybackService::class.java).apply {
                action = "NEXT"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setDeleteIntent(createStopIntent())
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    fun createStopIntent(): PendingIntent {
        return PendingIntent.getService(
            service,
            99,
            Intent(service, PlaybackService::class.java).apply {
                action = "STOP"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
