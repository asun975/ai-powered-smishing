package com.example.smishingdetection.data.network.classifier

import android.util.Log
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.classifier.model.ClassifierRequest
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.url.model.UrlApiResult
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer
import retrofit2.HttpException

// TODO add error data models for API responses
interface ClassifierApiRepository {
    suspend fun classify(message:String) : ClassifierApiResult
}

class NetworkClassifierApiRepository(
    private val classifierApiService: ClassifierApiService,
    private val classifierApiSanitizer: ClassifierApiSanitizer
) : ClassifierApiRepository {
    override suspend fun classify(message: String): ClassifierApiResult {
        try {
            val request = ClassifierRequest(
                classifierApiSanitizer.sanitize(message)
            )
            return ClassifierApiResult.Success(classifierApiService.classify(request))

        } catch(e: HttpException) {
            val statusCode = e.code()
            val errorBody = e.response()?.errorBody() ?: e.message()
            return ClassifierApiResult.ApiError(statusCode, errorBody.toString())

        } catch(e: Exception) {
            val exception = e.javaClass.simpleName.toString()
            val message = e.message.toString()
            Log.e("LlmExplainer", "Exception: $exception - $message")
            return ClassifierApiResult.ExceptionError(exception, message)
        }
    }

}

