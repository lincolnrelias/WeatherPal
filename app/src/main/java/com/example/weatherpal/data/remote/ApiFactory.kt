package com.example.weatherpal.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiFactory {
    val json = Json {
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    fun client() =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            // Permit transport recovery (alternate IPs/stale pooled sockets) within the call
            // timeout.
            // The application performs no automatic request retry loops.
            .retryOnConnectionFailure(true)
            .build()

    fun retrofit(baseUrl: String, client: OkHttpClient) =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
