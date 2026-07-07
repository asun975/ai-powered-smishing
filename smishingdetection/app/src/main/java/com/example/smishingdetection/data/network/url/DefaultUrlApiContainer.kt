package com.example.smishingdetection.data.network.url

import com.example.smishingdetection.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface UrlApiContainer {
    val networkUrlApiRepository: NetworkUrlApiRepository
}

/**
 * Implementation for the Dependency Injection container at the application level.
 *
 * Variables are initialized lazily and the same instance is shared across the whole app.
 */
class DefaultUrlApiContainer : UrlApiContainer {
    private val baseUrl = BuildConfig.SCAN_API_URL

    // Use the Retrofit builder to build a retrofit object using a kotlinx.serialization converter
    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(baseUrl)
        .build()

    // Retrofit service object for creating api calls
    private val retrofitService: UrlApiService by lazy {
        retrofit.create(UrlApiService::class.java)
    }

    // Dependency injection for Url Api respository
    override val networkUrlApiRepository: NetworkUrlApiRepository by lazy {
        NetworkUrlApiRepository(retrofitService)
    }
}