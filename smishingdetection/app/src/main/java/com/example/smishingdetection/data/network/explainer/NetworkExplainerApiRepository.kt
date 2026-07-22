package com.example.smishingdetection.data.network.explainer

import android.util.Log
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer
import retrofit2.HttpException

// TODO add error data models for API responses
interface ExplainerApiRepository {
    suspend fun explain(request: ExplainerRequest) : ExplainerResponse
}

class NetworkExplainerApiRepository(
    private val explainerApiService: ExplainerApiService,
    private val explainerApiSanitizer: ExplainerApiSanitizer
) : ExplainerApiRepository {
    override suspend fun explain(request: ExplainerRequest): ExplainerResponse{
        val santizedInput = explainerApiSanitizer.sanitize(request.text)
        val response = explainerApiService.explain(
            ExplainerRequest(
            santizedInput,
            request.classification,
            request.risk_score
            )
        )
        return response
    }
}