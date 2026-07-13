package com.example.smishingdetection.data.smishingalert

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.local.model.AnalyzedMessage

object NotificationHelper {
    private const val CHANNEL_ID = "smishing_alerts"
    private const  val CHANNEL_NAME = "Smishing Alerts"

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
        userAlert: AnalyzedMessage
    ) {
        val intent = Intent(context, MessageDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("phone", userAlert.phoneNumber)
            putExtra("date", userAlert.date)
            putExtra("message", userAlert.message)
            putExtra("risk_score", userAlert.riskScore)
            putExtra("risk_level", userAlert.status)
            putExtra("explanation", userAlert.explanation)
            putExtra("id", userAlert.id)
            putExtra("url_scan_result", userAlert.urlScanResult)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            userAlert.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ${userAlert.status} Risk Smishing Detected")
            .setContentText("From: ${userAlert.phoneNumber} — Tap to view details")
            .setStyle(
                NotificationCompat.BigTextStyle()
                .bigText("From: ${userAlert.phoneNumber}\nRisk: ${userAlert.status} (${String.format("%.0f", userAlert.riskScore)}%)\n\n${userAlert.explanation}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(userAlert.id.toInt(), notification)
    }
}