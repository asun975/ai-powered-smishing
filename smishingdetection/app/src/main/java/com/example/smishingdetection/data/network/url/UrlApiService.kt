package com.example.smishingdetection.data.network.url

import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Returns data class UrlVerdict and this method is asynchronous
 * requested with POST
 * HTTP method
 */
interface UrlApiService {
    @POST("/analyze")
    suspend fun getVerdict(
        @Body request: UrlAnalyzerRequest
    ): UrlAnalyzerResponse
}