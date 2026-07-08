package com.example.smishingdetection.data.network.classifier

import com.example.smishingdetection.BuildConfig
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ClassifierApiContainer {
    val networkClassifierApiRepository: NetworkClassifierApiRepository
}

class DefaultClassifierApiContainer : ClassifierApiContainer {
    private val baseUrl = BuildConfig.CLASSIFIER_API_URL

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(baseUrl)
        .build()

    private val retrofitService: ClassifierApiService by lazy {
        retrofit.create(ClassifierApiService::class.java)
    }

    // Dependency injection
    override val networkClassifierApiRepository: NetworkClassifierApiRepository by lazy {
        NetworkClassifierApiRepository(
            retrofitService,
            ClassifierApiSanitizer())
    }
}