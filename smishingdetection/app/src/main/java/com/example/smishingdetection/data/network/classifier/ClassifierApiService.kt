package com.example.smishingdetection.data.network.classifier

import com.example.smishingdetection.data.network.classifier.model.ClassifierRequest
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ClassifierApiService {
    @POST("/classify")
    suspend fun classify(
        @Body request: ClassifierRequest
    ) : ClassifierResponse
}