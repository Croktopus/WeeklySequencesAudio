package com.chris.wsa.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Headers

interface LessWrongApi {
    @POST("graphql")
    @Headers("Content-Type: application/json")
    suspend fun queryPost(@Body body: Map<String, String>): Response<LwGraphQLResponse>
}

interface Type3Api {
    @GET("narration/find")
    suspend fun findNarration(
        @Query("url") url: String,
        @Query("request_source") requestSource: String = "embed"
    ): Response<Type3Response>
}

interface EventApi {
    @POST("graphql")
    @Headers("Content-Type: application/json")
    suspend fun queryPost(@Body body: Map<String, String>): Response<EventResponse>
}
