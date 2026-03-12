package com.chris.wsa.audio

import com.chris.wsa.api.ApiClient
import com.chris.wsa.api.EventApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedLink(
    val url: String,
    val anchorText: String,
    val isLwPost: Boolean
)

data class ParsedEvent(
    val title: String,
    val shortTitle: String,
    val author: String,
    val links: List<ExtractedLink>,
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

            // Extract all links from the HTML
            val links = extractLinks(html)

            // Parse postedAt for year suffix
            val postedAtMillis = result?.postedAt?.let {
                try {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    fmt.parse(it)?.time
                } catch (_: Exception) { null }
            }

            // Generate short title
            val shortTitle = generateShortTitle(title, author, postedAtMillis)

            ParsedEvent(
                title = title,
                shortTitle = shortTitle,
                author = author,
                links = links,
                postedAt = result?.postedAt
            )

        } catch (_: Exception) {
            null
        }
    }

    private fun extractLinks(html: String): List<ExtractedLink> {
        val links = mutableListOf<ExtractedLink>()
        val seenUrls = mutableSetOf<String>()

        // Match <a href="url">text</a> — capture href and inner content
        val linkPattern = Regex("""<a\s[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        // LW post URL patterns
        val lwPostPattern = Regex("""(?:https://www\.lesswrong\.com)?/(?:posts/([a-zA-Z0-9]{17})|s/[a-zA-Z0-9]+/p/([a-zA-Z0-9]{17}))""")

        // Skip patterns: internal LW pages that aren't posts
        val skipPattern = Regex("""^(?:https://www\.lesswrong\.com)?/(?:groups|events|users|tags|tag|sequences|about|posts/[a-zA-Z0-9]{17}#)""")

        linkPattern.findAll(html).forEach { match ->
            val rawHref = match.groupValues[1]
            val rawAnchorText = match.groupValues[2]
                .replace(Regex("<[^>]+>"), "") // strip HTML tags
                .trim()

            // Skip empty anchors, mailto, javascript, fragment-only
            if (rawAnchorText.isBlank()) return@forEach
            if (rawHref.startsWith("mailto:")) return@forEach
            if (rawHref.startsWith("javascript:")) return@forEach
            if (rawHref.startsWith("#")) return@forEach

            // Check if it's a LW post link
            val lwMatch = lwPostPattern.find(rawHref)
            if (lwMatch != null) {
                val postId = lwMatch.groupValues[1].ifEmpty { lwMatch.groupValues[2] }
                val normalizedUrl = "https://www.lesswrong.com/posts/$postId"

                if (normalizedUrl !in seenUrls) {
                    seenUrls.add(normalizedUrl)
                    links.add(ExtractedLink(
                        url = normalizedUrl,
                        anchorText = rawAnchorText,
                        isLwPost = true
                    ))
                }
                return@forEach
            }

            // Skip non-post LW internal pages
            if (skipPattern.containsMatchIn(rawHref)) return@forEach

            // Resolve relative LW links that aren't posts — skip them
            if (rawHref.startsWith("/")) return@forEach

            // External https links
            if (rawHref.startsWith("https://") || rawHref.startsWith("http://")) {
                // Skip links back to lesswrong.com that aren't posts (already handled above)
                if (rawHref.contains("lesswrong.com")) return@forEach

                if (rawHref !in seenUrls) {
                    seenUrls.add(rawHref)
                    links.add(ExtractedLink(
                        url = rawHref,
                        anchorText = rawAnchorText,
                        isLwPost = false
                    ))
                }
            }
        }

        return links
    }

    private fun generateShortTitle(title: String, author: String, postedAt: Long?): String {
        val yearSuffix = postedAt?.let {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it
            "/${cal.get(java.util.Calendar.YEAR) % 100}"
        } ?: ""

        // Special case: first event has no number
        if (title.contains("First Lighthaven Sequences Reading Group")) {
            return "LSRG01 09/05$yearSuffix • $author"
        }

        // Pattern: "Lighthaven Sequences Reading Group #XX (Day M/D)"
        val lighthaven = Regex("""Lighthaven Sequences Reading Group #(\d+) \((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) (\d+/\d+)\)""")
        val match = lighthaven.find(title)

        if (match != null) {
            val number = match.groupValues[1].padStart(2, '0')
            val date = match.groupValues[2]
            return "LSRG$number $date$yearSuffix • $author"
        }

        // If it doesn't match the pattern, include author with original title
        return "$title • $author"
    }
}
