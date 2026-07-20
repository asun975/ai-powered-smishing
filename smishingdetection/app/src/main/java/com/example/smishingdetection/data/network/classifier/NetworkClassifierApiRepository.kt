package com.example.smishingdetection.data.network.classifier

import android.util.Log
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.classifier.model.ClassifierRequest
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.classifier.model.ClassifierResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.url.model.UrlApiResult
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer
import retrofit2.HttpException

interface ClassifierApiRepository {
    suspend fun classify(message:String?) : ClassifierApiResult
}

class NetworkClassifierApiRepository(
    private val classifierApiService: ClassifierApiService,
    private val classifierApiSanitizer: ClassifierApiSanitizer
) : ClassifierApiRepository {
    override suspend fun classify(message: String?): ClassifierApiResult {
        try {
            val sanitizedInput = classifierApiSanitizer.sanitize(message)
            Log.d("SmishingClassifier", "Sanitized string: $sanitizedInput")
            val request = ClassifierRequest(sanitizedInput)
            Log.d("SmishingClassifier", "API Request: $classifierApiService.")

            val response = classifierApiService.classify(request)
            val riskScore =  if (response.label == "SPAM") {
                response.confidence
            } else {
                1 - response.confidence
            }
            val riskCategory: String = when {
                riskScore >= 0.70 -> {"HIGH" }
                riskScore >= 0.30 -> {"MEDIUM"}
                else -> {"LOW"}
            }
            Log.d("SmishingClassifier", "Response: $response")
            Log.d("SmishingClassifier", "Processed result: $riskCategory, $riskScore")
            return ClassifierApiResult.Success(ClassifierResult(
                response.label,
                riskScore,
                riskCategory
            ))

        } catch(e: HttpException) {
            val statusCode = e.code()
            val errorBody = e.response()?.errorBody()?.string()
            Log.d("SmishingClassifier", "${e.javaClass.simpleName} ${e.javaClass.fields}")
            return ClassifierApiResult.ApiError(statusCode, errorBody.toString())

        } catch(e: Exception) {
            val exception = e.javaClass.simpleName.toString()
            val message = e.message.toString()
            Log.e("SmishingClassifier", "Exception: $exception - $message")
            return ClassifierApiResult.ExceptionError(exception, message)
        }
    }

}

