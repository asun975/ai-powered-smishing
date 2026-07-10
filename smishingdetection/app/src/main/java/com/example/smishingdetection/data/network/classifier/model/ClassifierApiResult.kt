package com.example.smishingdetection.data.network.classifier.model

sealed interface ClassifierApiResult {
    data class Success(val data: ClassifierResponse) : ClassifierApiResult
    data class ApiError(val statusCode: Int, val message: String): ClassifierApiResult
    data class ExceptionError(val exception: String, val message: String): ClassifierApiResult
}