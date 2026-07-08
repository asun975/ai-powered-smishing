package com.example.smishingdetection.data.network.explainer

import com.example.smishingdetection.BuildConfig
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.exp

interface ExplainerApiContainer {
    val networkExplainerApiRepository: NetworkExplainerApiRepository
}

class DefaultExplainerApiContainer : ExplainerApiContainer {
    private val baseUrl = BuildConfig.LLM_API_URL

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(baseUrl)
        .build()

    private val retrofitService: ExplainerApiService by lazy {
        retrofit.create(ExplainerApiService::class.java)
    }

    // Dependency injection
    override val networkExplainerApiRepository: NetworkExplainerApiRepository by lazy {
        NetworkExplainerApiRepository(
            retrofitService,
            ExplainerApiSanitizer()
        )
    }
}