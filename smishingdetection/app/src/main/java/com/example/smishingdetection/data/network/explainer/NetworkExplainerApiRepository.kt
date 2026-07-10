package com.example.smishingdetection.data.network.explainer

import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer

// TODO add error data models for API responses
interface ExplainerApiRepository {
    suspend fun explain(input: String, classification: String, riskScore: Float) : ExplainerResponse
}

class NetworkExplainerApiRepository(
    private val explainerApiService: ExplainerApiService,
    private val explainerApiSanitizer: ExplainerApiSanitizer
) : ExplainerApiRepository {
    override suspend fun explain(input: String, classification: String, riskScore: Float): ExplainerResponse {
        val santizedInput = explainerApiSanitizer.sanitize(input)
        val request = ExplainerRequest(
            santizedInput,
            classification,
            riskScore
        )
        return explainerApiService.explain(request)
    }
}