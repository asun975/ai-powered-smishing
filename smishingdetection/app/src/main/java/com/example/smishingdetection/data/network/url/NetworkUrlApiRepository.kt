package com.example.smishingdetection.data.network.url

import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse

/**
 * Repository that fetch url verdict from scanApi.
 */
interface UrlApiRepository {
    suspend fun getVerdict(request: UrlAnalyzerRequest): UrlAnalyzerResponse
}

/**
 * Network Implementation of Repository that gets url verdict from scanApi.
 */
class NetworkUrlApiRepository(
    private val urlApiService: UrlApiService
) : UrlApiRepository {
    override suspend fun getVerdict(request: UrlAnalyzerRequest): UrlAnalyzerResponse = urlApiService.getVerdict(request)
}