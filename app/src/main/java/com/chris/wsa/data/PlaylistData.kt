package com.chris.wsa.data

data class PlaylistItem(
    val url: String,
    val title: String,
    val author: String,
    val mp3Url: String? = null,
    val source: String = "",
    val durationMs: Long? = null
) {
    val hasAudio: Boolean get() = mp3Url != null
}

data class SavedPlaylist(
    val id: String,
    val name: String,
    val items: List<PlaylistItem>,
    val createdAt: Long = System.currentTimeMillis(),
    val eventUrl: String? = null,
    val postedAt: Long? = null
)
