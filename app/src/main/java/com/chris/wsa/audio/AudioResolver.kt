package com.chris.wsa.audio

import android.media.MediaMetadataRetriever
import com.chris.wsa.api.ApiClient
import com.chris.wsa.api.LessWrongApi
import com.chris.wsa.api.Type3Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AudioResolver(
    private val lwApi: LessWrongApi = ApiClient.lwApi,
    private val type3Api: Type3Api = ApiClient.type3Api
) {

    suspend fun getAudioUrl(postUrl: String): AudioResult = withContext(Dispatchers.IO) {
        val messages = mutableListOf<String>()
        var metaTitle: String? = null
        var metaAuthor: String? = null

        // Extract post ID
        val postId = LwParser.extractPostId(postUrl)
        messages.add("Original URL: $postUrl")
        messages.add("Extracted Post ID: $postId")

        if (postId == null) {
            return@withContext AudioResult.Error("Invalid LessWrong URL\n\n${messages.joinToString("\n")}")
        }

        // Try GraphQL first
        try {
            messages.add("\n--- Trying GraphQL ---")
            val graphQLResult = tryGraphQL(postId)
            if (graphQLResult is AudioResult.Success) {
                messages.add("GraphQL: SUCCESS!")
                return@withContext graphQLResult
            }
            // Capture metadata even when no podcast episode
            if (graphQLResult is AudioResult.Error) {
                metaTitle = graphQLResult.title
                metaAuthor = graphQLResult.author
            }
            messages.add("GraphQL: No podcast episode found")
        } catch (e: Exception) {
            messages.add("GraphQL Error: ${e.message}")
        }

        // Try TYPE III Audio - construct clean URL
        try {
            val cleanUrl = "https://www.lesswrong.com/posts/$postId"
            messages.add("\n--- Trying TYPE III ---")
            messages.add("Clean URL: $cleanUrl")

            val type3Result = tryType3(cleanUrl)
            if (type3Result != null) {
                messages.add("TYPE III: SUCCESS!")
                return@withContext type3Result
            }
            messages.add("TYPE III: Narration not found or not ready")
        } catch (e: Exception) {
            messages.add("TYPE III Error: ${e.message}")
        }

        return@withContext AudioResult.Error(
            message = "No audio available\n\n${messages.joinToString("\n")}",
            title = metaTitle,
            author = metaAuthor
        )
    }

    private suspend fun tryGraphQL(postId: String): AudioResult? {
        val query = """
            {
              post(input: { selector: { _id: "$postId" } }) {
                result {
                  _id
                  title
                  slug
                  user {
                    displayName
                  }
                  podcastEpisode {
                    externalEpisodeId
                  }
                  forceAllowType3Audio
                }
              }
            }
        """.trimIndent()

        val response = lwApi.queryPost(mapOf("query" to query))

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!

            val result = body.data.post?.result
            val author = result?.user?.displayName ?: "Unknown Author"

            val episodeId = result?.podcastEpisode?.externalEpisodeId

            if (episodeId != null) {
                val mp3Url = "https://www.buzzsprout.com/2036194/$episodeId.mp3"
                val durationMs = probeDuration(mp3Url)

                return AudioResult.Success(
                    mp3Url = mp3Url,
                    title = result.title,
                    author = author,
                    source = "LessWrong Podcast",
                    durationMs = durationMs
                )
            }

            // No podcast episode, but return metadata for fallback use
            if (result != null) {
                return AudioResult.Error(
                    message = "No podcast episode",
                    title = result.title,
                    author = author
                )
            }
        }

        return null
    }

    private suspend fun tryType3(postUrl: String): AudioResult? {
        val response = type3Api.findNarration(postUrl)

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!

            if (body.status == "Succeeded") {
                val durationMs = body.duration.takeIf { it in 1..36000 }?.let { it * 1000L }

                return AudioResult.Success(
                    mp3Url = body.mp3Url,
                    title = body.title,
                    author = body.author,
                    source = "TYPE III Audio",
                    durationMs = durationMs
                )
            }
        }

        return null
    }

    private suspend fun probeDuration(mp3Url: String): Long? = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(mp3Url, emptyMap())
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationStr?.toLongOrNull()?.takeIf { it > 0 }
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        // Ignore release failures
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

sealed class AudioResult {
    data class Success(
        val mp3Url: String,
        val title: String,
        val author: String,
        val source: String,
        val durationMs: Long? = null
    ) : AudioResult()

    data class Error(
        val message: String,
        val title: String? = null,
        val author: String? = null
    ) : AudioResult()
}
