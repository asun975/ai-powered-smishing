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

    /**
     * Registers the notification channel this app posts to. Required on
     * Android 8.0 (Oreo, API 26)+ — without calling this, sendSmishingNotification()
     * would silently do nothing, since the OS won't display a notification on
     * a channel that was never created. Must be called once at app startup
     * (e.g. from a custom Application class), before any notification can fire.
     */
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

    /**
     * Builds and posts a system notification for a risky message. Tapping it
     * opens MessageDetailActivity with the message's full data attached, the
     * same way the in-app dialog's "View Details" button does. Formats the
     * URL scan result for display — showing the real result only if the scan
     * actually succeeded, otherwise a fallback string that includes whatever
     * status/error info came back. Uses IMPORTANCE_HIGH/PRIORITY_HIGH so this
     * shows as a heads-up banner, not just a silent status bar icon.
     */
    fun sendSmishingNotification(
        context: Context,
        sender: String,
        riskCategory: String,
        riskScorePercent: Float,
        explanation: String,
        messageId: Long,
        originalBody: String,
        timestamp: String,
        scanResult: Pair<ScanStatus,String>,
        status: String
    ) {
        val urlScanResult = if(scanResult.first == ScanStatus.SUCCESS) {
            scanResult.second
        } else {
                "No scan result saved: ${scanResult.second}"
        }
        val intent = Intent(context, MessageDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("phone", sender)
            putExtra("date", timestamp)
            putExtra("message", originalBody)
            putExtra("risk_score", riskScorePercent.toString())
            putExtra("status", status)
            putExtra("explanation", explanation)
            putExtra("id", messageId.toString())
            putExtra("url_scan_result", urlScanResult)
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
