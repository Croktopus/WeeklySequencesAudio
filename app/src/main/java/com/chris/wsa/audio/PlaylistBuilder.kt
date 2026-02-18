package com.chris.wsa.audio

import com.chris.wsa.data.PlaylistItem

class PlaylistBuilder(private val resolver: AudioResolver = AudioResolver()) {

    data class BuildResult(val items: List<PlaylistItem>)

    suspend fun buildFromLinks(links: List<ExtractedLink>): BuildResult {
        val items = mutableListOf<PlaylistItem>()

        links.forEach { link ->
            if (link.isLwPost) {
                when (val result = resolver.getAudioUrl(link.url)) {
                    is AudioResult.Success -> {
                        items.add(
                            PlaylistItem(
                                url = link.url,
                                title = result.title,
                                author = result.author,
                                mp3Url = result.mp3Url,
                                source = result.source,
                                durationMs = result.durationMs?.takeIf { it > 0 }
                            )
                        )
                    }
                    is AudioResult.Error -> {
                        items.add(
                            PlaylistItem(
                                url = link.url,
                                title = result.title ?: link.anchorText,
                                author = result.author ?: ""
                            )
                        )
                    }
                }
            } else {
                // External link — no audio resolution
                items.add(
                    PlaylistItem(
                        url = link.url,
                        title = link.anchorText,
                        author = ""
                    )
                )
            }
        }

        return BuildResult(items)
    }
}
