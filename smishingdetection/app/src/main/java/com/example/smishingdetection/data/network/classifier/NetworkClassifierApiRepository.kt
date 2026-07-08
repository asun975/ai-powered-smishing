package com.example.smishingdetection.data.network.classifier

import com.example.smishingdetection.data.network.classifier.model.ClassifierRequest
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer

// TODO add error data models for API responses
interface ClassifierApiRepository {
    suspend fun classify(message:String) : ClassifierResponse
}

class NetworkClassifierApiRepository(
    private val classifierApiService: ClassifierApiService,
    private val classifierApiSanitizer: ClassifierApiSanitizer
) : ClassifierApiRepository {
    override suspend fun classify(message: String): ClassifierResponse {
        val request = ClassifierRequest(
            classifierApiSanitizer.sanitize(message)
        )
        return classifierApiService.classify(request)
    }

}

