package com.chris.wsa.data

data class PlaylistItem(
    val url: String,
    val title: String,
    val author: String,
    val mp3Url: String,
    val source: String
)

data class SavedPlaylist(
    val id: String,
    val name: String,
    val items: List<PlaylistItem>,
    val createdAt: Long = System.currentTimeMillis()
)
