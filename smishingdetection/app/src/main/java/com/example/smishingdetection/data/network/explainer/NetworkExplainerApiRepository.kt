package com.example.smishingdetection.data.network.explainer

import android.util.Log
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.sanitizer.ExplainerApiSanitizer
import retrofit2.HttpException

// TODO add error data models for API responses
interface ExplainerApiRepository {
    suspend fun explain(request: ExplainerRequest) : ExplainerApiResult
}

class NetworkExplainerApiRepository(
    private val explainerApiService: ExplainerApiService,
    private val explainerApiSanitizer: ExplainerApiSanitizer
) : ExplainerApiRepository {
    override suspend fun explain(request: ExplainerRequest): ExplainerApiResult {
        try {
            val santizedInput = explainerApiSanitizer.sanitize(request.text)
            val response = explainerApiService.explain(
                ExplainerRequest(
                santizedInput,
                request.classification,
                request.risk_score
                )
            )
            return ExplainerApiResult.Success(response)

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