package com.chris.wsa.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val lwRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://www.lesswrong.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val type3Retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.type3.audio/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val lwApi: LessWrongApi = lwRetrofit.create(LessWrongApi::class.java)
    val type3Api: Type3Api = type3Retrofit.create(Type3Api::class.java)
    val eventApi: EventApi = lwRetrofit.create(EventApi::class.java)
}
