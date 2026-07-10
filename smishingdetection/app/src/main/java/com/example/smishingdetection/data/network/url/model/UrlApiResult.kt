package com.example.smishingdetection.data.network.url.model

/**
 * Define API response for success and error responses
 */
sealed interface UrlApiResult {
    data class Success(val data: UrlAnalyzerResponse): UrlApiResult
    data class ApiError(val statusCode: Int, val message: String): UrlApiResult
    data class ValidationError(val error: String): UrlApiResult
    data class ExceptionError(val exception: String, val message: String): UrlApiResult
}