package com.example.smishingdetection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

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
        sender: String,
        riskCategory: String,
        riskScorePercent: Float,
        explanation: String,
        messageId: Long,
        originalBody: String,
        timestamp: String,
        scanResult: String,
        status: String
    ) {
        val intent = Intent(context, MessageDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("phone", sender)
            putExtra("date", timestamp)
            putExtra("message", originalBody)
            putExtra("risk_score", riskScorePercent.toString())
            putExtra("status", status)
            putExtra("explanation", explanation)
            putExtra("id", messageId.toString())
            putExtra("url_scan_result", scanResult)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $riskCategory Risk Smishing Detected")
            .setContentText("From: $sender — Tap to view details")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: $sender\nRisk: $riskCategory (${String.format("%.0f", riskScorePercent)}%)\n\n$explanation"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(messageId.toInt(), notification)
    }
}