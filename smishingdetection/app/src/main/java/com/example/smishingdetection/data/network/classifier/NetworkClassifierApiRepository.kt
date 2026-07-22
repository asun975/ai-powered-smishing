package com.example.smishingdetection.data.network.classifier

import android.util.Log
import com.example.smishingdetection.data.network.classifier.model.ClassifierRequest
import com.example.smishingdetection.data.network.classifier.model.ClassifyResult
import com.example.smishingdetection.data.sanitizer.ClassifierApiSanitizer

interface ClassifierApiRepository {
    suspend fun classify(message:String?) : ClassifyResult
}

class NetworkClassifierApiRepository(
    private val classifierApiService: ClassifierApiService,
    private val classifierApiSanitizer: ClassifierApiSanitizer
) : ClassifierApiRepository {
    override suspend fun classify(message: String?): ClassifyResult {

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
        return ClassifyResult(
            response.label,
            riskScore,
            riskCategory
        )

    }

}

