package com.chris.wsa.audio

import com.chris.wsa.data.PlaylistItem

class PlaylistBuilder(private val resolver: AudioResolver = AudioResolver()) {

    data class BuildResult(val items: List<PlaylistItem>, val failedUrls: List<String>)

    suspend fun buildFromPostUrls(postUrls: List<String>): BuildResult {
        val items = mutableListOf<PlaylistItem>()
        val failedUrls = mutableListOf<String>()

        postUrls.forEach { postUrl ->
            when (val result = resolver.getAudioUrl(postUrl)) {
                is AudioResult.Success -> {
                    items.add(
                        PlaylistItem(
                            url = postUrl,
                            title = result.title,
                            author = result.author,
                            mp3Url = result.mp3Url,
                            source = result.source
                        )
                    )
                }
                is AudioResult.Error -> {
                    failedUrls.add(postUrl)
                    println("Failed: $postUrl")
                }
            }
        }

        return BuildResult(items, failedUrls)
    }
}
