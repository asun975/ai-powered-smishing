package com.example.smishingdetection

import android.app.Application
import android.content.Context
import com.example.smishingdetection.data.local.DefaultBlockRepository
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.database.SmishingDetectorDb
import com.example.smishingdetection.data.network.classifier.ClassifierApiService
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import com.example.smishingdetection.data.network.explainer.ExplainerApiService
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.UrlApiService
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer
import com.example.smishingdetection.data.sms.DefaultSmsProvider
import com.example.smishingdetection.data.sms.DefaultSmsRepository
import com.example.smishingdetection.data.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MyApplication: Application() {
    // SMS Provider
    private val ioDispatcher = Dispatchers.IO
    private val defaultSmsProvider = DefaultSmsProvider(this, ioDispatcher)
    // Block Repository
    private val blockDataSource by lazy {
        SmishingDetectorDb.Companion.getDatabase(this)
            .blockedPhoneNumberDao()
    }
    val defaultBlockRepository by lazy {
        DefaultBlockRepository(blockDataSource)
    }
    // Classifier API
    private val classifierRetrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(BuildConfig.CLASSIFIER_API_URL)
        .build()

    private val classifierRetrofitService: ClassifierApiService by lazy {
        classifierRetrofit.create(ClassifierApiService::class.java)
    }

    val networkClassifierApiRepository: NetworkClassifierApiRepository by lazy {
        NetworkClassifierApiRepository(
            classifierRetrofitService,
            ClassifierApiSanitizer())
    }

    // LLM Explainer API
    private val explainerRetrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(BuildConfig.LLM_API_URL)
        .build()

    private val explainerRetrofitService: ExplainerApiService by lazy {
        explainerRetrofit.create(ExplainerApiService::class.java)
    }

    // Dependency injection
    val networkExplainerApiRepository: NetworkExplainerApiRepository by lazy {
        NetworkExplainerApiRepository(
            explainerRetrofitService,
            ExplainerApiSanitizer()
        )
    }

    // URL sandbox API
    private val urlRetrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(BuildConfig.SCAN_API_URL)
        .build()

    private val urlRetrofitService: UrlApiService by lazy {
        urlRetrofit.create(UrlApiService::class.java)
    }
    val urlRepository =
        NetworkUrlApiRepository(urlRetrofitService)

    // Quarantine
    private val quarantineDataSource by lazy {
        SmishingDetectorDb.Companion.getDatabase(this as Context)
            .analyzedMessageDao()
    }
    val quarantineRepository by lazy {
        QuarantineRepository(quarantineDataSource)
    }

    val smsRepository by lazy {
        DefaultSmsRepository(
            defaultSmsProvider,
            networkClassifierApiRepository,
            networkExplainerApiRepository,
            urlRepository,
            quarantineRepository)

    }
}