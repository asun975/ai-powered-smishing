package com.example.smishingdetection.data.network.explainer

import android.util.Log
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer
import retrofit2.HttpException

// TODO add error data models for API responses
interface ExplainerApiRepository {
    suspend fun explain(input: String, classification: String, riskScore: Float) : ExplainerApiResult
}

class NetworkExplainerApiRepository(
    private val explainerApiService: ExplainerApiService,
    private val explainerApiSanitizer: ExplainerApiSanitizer
) : ExplainerApiRepository {
    override suspend fun explain(input: String, classification: String, riskScore: Float): ExplainerApiResult {
        try {
            val santizedInput = explainerApiSanitizer.sanitize(input)
            val request = ExplainerRequest(
                santizedInput,
                classification,
                riskScore
            )
            return ExplainerApiResult.Success(explainerApiService.explain(request))

        } catch (e: HttpException) {
            val statusCode = e.code()
            val errorBody = e.response()?.errorBody() ?: e.message()
            return ExplainerApiResult.ApiError(statusCode, errorBody.toString())

        } catch (e: Exception) {
            val exception = e.javaClass.simpleName.toString()
            val message = e.message.toString()
            Log.e("LlmExplainer", "Exception: $exception - $message")
            return ExplainerApiResult.ExceptionError(exception, message)
        }
    }
}