package com.example.smishingdetection

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The background retry worker for offline mode. Scheduled by MainActivity
 * (via scheduleAnalysisWorker()) whenever a message couldn't be classified
 * because the classifier API was unreachable — this worker runs later, once
 * WorkManager's network constraint is satisfied, and tries again for every
 * message still sitting in "pending" status.
 */
class SmishingAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Loads every message currently marked "pending" from the database and
     * re-runs the full classify -> explain -> URL-scan pipeline for each one
     * (mirroring what classifyMessage() does in MainActivity, since this
     * worker can't rely on MainActivity being alive to do it). For any risky
     * result, updates the database and sends a notification, same as the
     * live path. If any message still fails (classifier still unreachable,
     * or an unexpected exception), returns Result.retry() so WorkManager
     * schedules another attempt later using its backoff policy; only
     * returns Result.success() once every pending message has been handled.
     */
    override suspend fun doWork(): Result {
        Log.d("SmishingWorker", "Starting background analysis for pending messages")
        
        // Wait a small bit to ensure DB is written if we were triggered immediately
        kotlinx.coroutines.delay(1000)

        val db = DatabaseHelper(applicationContext)
        val classifier = SmishingClassifier(BuildConfig.CLASSIFIER_API_URL)
        val explainer = LlmExplainer(BuildConfig.LLM_API_URL)
        val urlAnalyzer = UrlAnalyzer(BuildConfig.SCAN_API_URL)

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
            val firstUrl = Preprocessing.extractFirstUrl(body)

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

                val (scanStatus, scanResult) = urlAnalyzer.analyzeUrl(firstUrl)

                val prediction = if (label == "SPAM") "SPAM" else "SAFE"
                db.updateAnalyzedMessage(
                    id = id,
                    riskScore = riskScorePercent.toDouble(),
                    prediction = prediction,
                    explanation = explanation,
                    urlScanResult = if(scanStatus == ScanStatus.SUCCESS) {
                        scanResult
                    } else {
                        "No scan result available."
                    }
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
                        scanResult = Pair(scanStatus, scanResult),
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
