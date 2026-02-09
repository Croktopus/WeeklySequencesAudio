package com.chris.wsa.api

import com.google.gson.annotations.SerializedName

// LessWrong GraphQL Response (used by AudioResolver)
data class LwGraphQLResponse(
    val data: LwData
)

data class LwData(
    val post: LwPost?
)

data class LwPost(
    val result: LwPostResult?
)

data class LwPostResult(
    val _id: String,
    val title: String,
    val slug: String,
    val user: LwUser?,
    val podcastEpisode: PodcastEpisode?,
    val forceAllowType3Audio: Boolean?
)

data class LwUser(
    val displayName: String?
)

data class PodcastEpisode(
    val externalEpisodeId: String
)

// TYPE III Audio Response
data class Type3Response(
    val id: String,
    val status: String,
    val title: String,
    val author: String,
    val duration: Int,
    @SerializedName("mp3_url")
    val mp3Url: String,
    @SerializedName("source_url")
    val sourceUrl: String
)

// Event/Post GraphQL Response (used by WeeklyPostParser)
data class EventResponse(
    val data: EventData
)

data class EventData(
    val post: EventPost?
)

data class EventPost(
    val result: EventPostResult?
)

data class EventPostResult(
    val title: String?,
    val postedAt: String?,
    val user: EventUser?,
    val contents: EventContents?
)

data class EventUser(
    val displayName: String?
)

data class EventContents(
    val html: String?
)
