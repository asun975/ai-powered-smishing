package com.example.smishingdetection.data.network.explainer

import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ExplainerApiService {
    @POST("/explain")
    suspend fun explain(
        @Body request: ExplainerRequest
    ) : ExplainerResponse
}