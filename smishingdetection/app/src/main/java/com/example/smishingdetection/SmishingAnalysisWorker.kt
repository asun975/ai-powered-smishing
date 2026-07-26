package com.example.smishingdetection

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SmishingAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SmishingWorker", "Starting background analysis for pending messages")
        
        // Wait a small bit to ensure DB is written if we were triggered immediately
        kotlinx.coroutines.delay(1000)

        val db = DatabaseHelper(applicationContext)
        val classifier = SmishingClassifier(BuildConfig.CLASSIFIER_API_URL)
        val explainer = LlmExplainer(BuildConfig.LLM_API_URL)
        val urlAnalyzer = UrlAnalyzer()

        val pendingMessages = db.getByStatus(DatabaseHelper.STATUS_PENDING)
        Log.d("SmishingWorker", "Found ${pendingMessages.size} pending messages")

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        var allSucceeded = true

        for (msg in pendingMessages) {
            val id = msg[DatabaseHelper.COL_ID]?.toLong() ?: continue
            val sender = msg[DatabaseHelper.COL_PHONE] ?: ""
            val body = msg[DatabaseHelper.COL_MESSAGE] ?: ""
            val timestamp = msg[DatabaseHelper.COL_DATE] ?: ""
            
            Log.d("SmishingWorker", "Processing message ID: $id from $sender")

            val classifierInput = Preprocessing.preprocessClassifierText(body)
            val llmInput = Preprocessing.preprocessLlmText(body)
            val urls = Preprocessing.extractUrl(body)

            try {
                val (label, confidence) = classifier.classify(classifierInput)
                if (label == "ERROR") {
                    Log.d("SmishingWorker", "Classifier returned ERROR for message $id, will retry later")
                    allSucceeded = false
                    continue
                }

                val riskScore = if (label == "SPAM") confidence else (1 - confidence)
                val riskScorePercent = riskScore * 100
                val riskCategory = when {
                    riskScore > 0.75 -> "HIGH"
                    riskScore >= 0.30 -> "MEDIUM"
                    else -> "LOW"
                }

                var explanation = ""
                if (riskCategory == "HIGH" || riskCategory == "MEDIUM") {
                    explanation = explainer.explain(llmInput, label, riskScore)
                }

                val scanResult = urlAnalyzer.analyzeUrl(urls.firstOrNull())

                val prediction = if (label == "SPAM") "SPAM" else "SAFE"
                db.updateAnalyzedMessage(
                    id = id,
                    riskScore = riskScorePercent.toDouble(),
                    prediction = prediction,
                    explanation = explanation,
                    urlScanResult = scanResult
                )

                val status = DatabaseHelper.statusFromScore(riskScorePercent.toDouble())
                Log.d("SmishingWorker", "Updated message $id to status $status")

                if (riskCategory == "HIGH" || riskCategory == "MEDIUM") {
                    NotificationHelper.sendSmishingNotification(
                        context = applicationContext,
                        sender = sender,
                        riskCategory = riskCategory,
                        riskScorePercent = riskScorePercent,
                        explanation = explanation,
                        messageId = id,
                        originalBody = body,
                        timestamp = timestamp,
                        scanResult = scanResult ?: "",
                        status = status
                    )
                }

            } catch (e: Exception) {
                Log.e("SmishingWorker", "Error processing message $id", e)
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}
