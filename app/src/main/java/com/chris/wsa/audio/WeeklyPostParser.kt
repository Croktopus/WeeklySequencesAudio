package com.chris.wsa.audio

import com.chris.wsa.api.ApiClient
import com.chris.wsa.api.EventApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedEvent(
    val title: String,
    val shortTitle: String,
    val author: String,
    val postLinks: List<String>,
    val postedAt: String? = null
)

class WeeklyPostParser(
    private val api: EventApi = ApiClient.eventApi
) {

    suspend fun extractPostLinks(eventUrl: String): ParsedEvent? = withContext(Dispatchers.IO) {
        try {
            // Extract event ID from URL
            val eventIdPattern = Regex("""/events/([a-zA-Z0-9]+)/""")
            val eventIdMatch = eventIdPattern.find(eventUrl)
            val eventId = eventIdMatch?.groupValues?.get(1) ?: return@withContext null

            // Query GraphQL for the event content
            val query = """
                {
                  post(input: { selector: { _id: "$eventId" } }) {
                    result {
                      title
                      postedAt
                      user {
                        displayName
                      }
                      contents {
                        html
                      }
                    }
                  }
                }
            """.trimIndent()

            val response = api.queryPost(mapOf("query" to query))

            if (!response.isSuccessful || response.body() == null) {
                return@withContext null
            }

            val result = response.body()!!.data.post?.result
            val title = result?.title ?: "Unnamed Event"
            val author = result?.user?.displayName ?: "Unknown"
            val html = result?.contents?.html

            if (html == null) {
                return@withContext null
            }

            // Find all LessWrong post links in the HTML in the order they appear
            val postLinks = mutableListOf<String>()
            val seenIds = mutableSetOf<String>()

            // Combined pattern that matches both formats
            val combinedPattern = Regex("""<a\s+href="https://www\.lesswrong\.com/(?:posts/([a-zA-Z0-9]{17})|s/[a-zA-Z0-9]+/p/([a-zA-Z0-9]{17}))[^"]*"""")

            combinedPattern.findAll(html).forEach { match ->
                // Get whichever group matched (posts/ is group 1, s/.../p/ is group 2)
                val postId = match.groupValues[1].ifEmpty { match.groupValues[2] }

                if (postId.isNotEmpty() && !seenIds.contains(postId)) {
                    seenIds.add(postId)
                    postLinks.add("https://www.lesswrong.com/posts/$postId")
                }
            }

            // Generate short title
            val shortTitle = generateShortTitle(title, author)

            ParsedEvent(
                title = title,
                shortTitle = shortTitle,
                author = author,
                postLinks = postLinks,
                postedAt = result?.postedAt
            )

        } catch (_: Exception) {
            null
        }
    }

    private fun generateShortTitle(title: String, author: String): String {
        // Pattern: "Lighthaven Sequences Reading Group #XX (Day M/D)"
        val lighthaven = Regex("""Lighthaven Sequences Reading Group #(\d+) \((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) (\d+/\d+)\)""")
        val match = lighthaven.find(title)

        if (match != null) {
            val number = match.groupValues[1]
            val date = match.groupValues[2]
            return "LSRG$number $date • $author"
        }

        // If it doesn't match the pattern, include author with original title
        return "$title • $author"
    }
}
