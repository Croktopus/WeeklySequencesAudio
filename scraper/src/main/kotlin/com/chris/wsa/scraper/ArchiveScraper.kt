package com.chris.wsa.scraper

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.util.concurrent.TimeUnit

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
    val createdAt: Long,
    val eventUrl: String?,
    val postedAt: Long?
)

private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

private val gson = Gson()

fun main() {
    println("=== LSRG Archive Scraper (metadata-only) ===")

    val projectRoot = File(System.getProperty("user.dir"))
    val outputDir = File(projectRoot, "app/src/main/assets")
    outputDir.mkdirs()
    val outputFile = File(outputDir, "lsrg_archive.json")

    // Fetch all events via GraphQL
    println("Fetching event list via GraphQL...")
    val events = findAllEvents()
    println("Found ${events.size} events total")

    val lsrgEvents = events.filter { it.title.contains("Sequences Reading Group") }
    println("${lsrgEvents.size} are LSRG events")

    // Process each event for metadata only
    val playlists = mutableListOf<SavedPlaylist>()
    for ((index, event) in lsrgEvents.withIndex()) {
        println("[${index + 1}/${lsrgEvents.size}] ${event.title}")
        try {
            val playlist = processEvent(event)
            if (playlist != null) {
                playlists.add(playlist)
                println("  -> ${playlist.name}")
            } else {
                println("  -> Skipped")
            }
        } catch (e: Exception) {
            println("  -> ERROR: ${e.message}")
        }
        Thread.sleep(300)
    }

    // Sort by LSRG number descending
    val sorted = playlists.sortedWith(
        compareByDescending<SavedPlaylist> { parseLsrgNumber(it.name) ?: 0 }
            .thenByDescending { it.postedAt ?: 0L }
    )

    outputFile.writeText(gson.toJson(sorted))

    println("\n=== Done! ===")
    println("Written ${sorted.size} playlists to ${outputFile.absolutePath}")
}

data class EventInfo(val id: String, val title: String, val slug: String)

fun findAllEvents(): List<EventInfo> {
    val query = """
        {
          posts(input: { terms: { groupId: "LK6GNnKp8PDCkqcxx", limit: 200, sortedBy: "new" } }) {
            results {
              _id
              title
              slug
            }
          }
        }
    """.trimIndent()

    val response = graphqlQuery("https://www.lesswrong.com/graphql", query)
        ?: error("Failed to query group events")

    val results = response
        .getAsJsonObject("data")
        ?.getAsJsonObject("posts")
        ?.getAsJsonArray("results") ?: error("No results in response")

    return results.map { element ->
        val obj = element.asJsonObject
        EventInfo(
            id = obj.get("_id").asString,
            title = obj.get("title")?.asString ?: "",
            slug = obj.get("slug")?.asString ?: ""
        )
    }
}

fun processEvent(event: EventInfo): SavedPlaylist? {
    val eventUrl = "https://www.lesswrong.com/events/${event.id}/${event.slug}"

    val query = """
        {
          post(input: { selector: { _id: "${event.id}" } }) {
            result {
              title
              postedAt
              user {
                displayName
              }
            }
          }
        }
    """.trimIndent()

    val eventResult = graphqlQuery("https://www.lesswrong.com/graphql", query) ?: return null
    val result = eventResult
        .getAsJsonObject("data")
        ?.getAsJsonObject("post")
        ?.getAsJsonObject("result") ?: return null

    val title = result.get("title")?.asString ?: "Unnamed Event"
    val author = result.getAsJsonObject("user")?.get("displayName")?.asString ?: "Unknown"
    val postedAt = result.get("postedAt")?.asString
    val postedAtMillis = postedAt?.let { parseIsoDate(it) }

    val shortTitle = generateShortTitle(title, author, postedAtMillis)

    return SavedPlaylist(
        id = event.id,  // deterministic: use GraphQL _id
        name = shortTitle,
        items = emptyList(),
        createdAt = postedAtMillis ?: System.currentTimeMillis(),
        eventUrl = eventUrl,
        postedAt = postedAtMillis
    )
}

fun generateShortTitle(title: String, author: String, postedAt: Long?): String {
    val yearSuffix = postedAt?.let {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = it
        "/${cal.get(java.util.Calendar.YEAR) % 100}"
    } ?: ""

    if (title.contains("First Lighthaven Sequences Reading Group")) {
        return "LSRG01 09/05$yearSuffix \u2022 $author"
    }

    val lighthaven = Regex("""Lighthaven Sequences Reading Group #(\d+) \((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) (\d+/\d+)\)""")
    val match = lighthaven.find(title)

    if (match != null) {
        val number = match.groupValues[1].padStart(2, '0')
        val date = match.groupValues[2]
        return "LSRG$number $date$yearSuffix \u2022 $author"
    }

    return "$title \u2022 $author"
}

fun parseLsrgNumber(name: String): Int? {
    val match = Regex("""LSRG(\d+)\s""").find(name)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

fun graphqlQuery(url: String, query: String): com.google.gson.JsonObject? {
    val jsonBody = gson.toJson(mapOf("query" to query))
    val requestBody = RequestBody.create(null, jsonBody.toByteArray())

    val request = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(requestBody)
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        System.err.println("  GraphQL HTTP ${response.code}: ${response.message}")
        return null
    }

    val body = response.body?.string() ?: return null
    return try {
        JsonParser.parseString(body).asJsonObject
    } catch (e: Exception) {
        System.err.println("  GraphQL parse error: ${e.message}")
        null
    }
}

fun parseIsoDate(iso: String): Long? {
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(iso)?.time
    } catch (_: Exception) {
        null
    }
}
