package com.example.smishingdetection.data.network.classifier.model

sealed interface ClassifierApiResult {
    data class Success(val data: ClassifierResult) : ClassifierApiResult
    data class ApiError(val statusCode: Int, val message: String): ClassifierApiResult
    data class ExceptionError(val exception: String, val message: String): ClassifierApiResult
}

data class ClassifierResult(
    val label: String,
    val riskScore: Float,
    val riskCategory: String
)