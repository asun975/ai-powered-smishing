package com.example.smishingdetection.data.network.explainer.model

sealed interface ExplainerApiResult {
    data class Success(val data: ExplainerResponse) : ExplainerApiResult
    data class ExceptionError(val exeception: String, val message: String): ExplainerApiResult
    data class ApiError(val statusCode: Int, val message: String): ExplainerApiResult
}