package com.example.smishingdetection.data.network.classifier

import com.example.smishingdetection.BuildConfig
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ClassifierApiContainer {
    val networkClassifierApiRepository: NetworkClassifierApiRepository
}

class DefaultClassifierApiContainer : ClassifierApiContainer {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()
    private val baseUrl = BuildConfig.CLASSIFIER_API_URL

    private val retrofit: Retrofit = Retrofit.Builder()
        .client(client)
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