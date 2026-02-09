package com.chris.wsa.audio

import com.chris.wsa.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LSRGFinder(
    private val client: OkHttpClient = ApiClient.okHttpClient
) {

    suspend fun findLatestEvent(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.lesswrong.com/groups/LK6GNnKp8PDCkqcxx")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            // Find event links in the HTML - get the full path
            // Pattern: /events/[eventId]/slug
            val eventPattern = Regex("""href="(/events/[a-zA-Z0-9]+/[^"]+)"""")
            val matches = eventPattern.findAll(html)

            val eventPaths = matches.map { it.groupValues[1] }.distinct().toList()

            if (eventPaths.isEmpty()) {
                return@withContext null
            }

            // Return the first event found on the page (assumed most recent)
            val eventPath = eventPaths.first()

            "https://www.lesswrong.com$eventPath"

        } catch (_: Exception) {
            null
        }
    }
}
