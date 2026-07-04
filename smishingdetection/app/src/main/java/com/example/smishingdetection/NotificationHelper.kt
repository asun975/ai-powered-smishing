package com.example.smishingdetection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smishingdetection.data.AnalyzedMessage

object NotificationHelper {

    private const val CHANNEL_ID = "smishing_alerts"
    private const val CHANNEL_NAME = "Smishing Alerts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected smishing messages"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendSmishingNotification(
        context: Context,
        analyzedMessage: AnalyzedMessage,
        riskCategory: String,
        riskScorePercent: Float
    ) {
        val intent = Intent(context, MessageDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("phone", analyzedMessage.phoneNumber)
            putExtra("date", analyzedMessage.date)
            putExtra("message", analyzedMessage.message)
            putExtra("risk_score", analyzedMessage.riskScore)
            putExtra("status", analyzedMessage.status)
            putExtra("explanation", analyzedMessage.explanation)
            putExtra("id", analyzedMessage.id)
            putExtra("url_scan_result", analyzedMessage.urlScanResult)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            analyzedMessage.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $riskCategory Risk Smishing Detected")
            .setContentText("From: ${analyzedMessage.phoneNumber} — Tap to view details")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: ${analyzedMessage.phoneNumber}\nRisk: $riskCategory (${String.format("%.0f", riskScorePercent)}%)\n\n${analyzedMessage.explanation}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(analyzedMessage.id.toInt(), notification)
    }
}