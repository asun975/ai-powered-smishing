package com.example.smishingdetection

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.ViewModel

/*
 * TODO
 * uncouple quarantine db actions
 * add viewModels
 */

class MainActivity : AppCompatActivity() {
    private lateinit var urlAnalyzerTextView: TextView
    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var explanationTextView: TextView
    private val CHANNEL_ID = "smishing_alerts"
    private val CHANNEL_NAME = "Smishing Alerts"
    private val smsViewModel : ViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "========== APP STARTED ===========")

        // Set up main UI
        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)
        explanationTextView = findViewById(R.id.explanationTextView)
        urlAnalyzerTextView = findViewById(R.id.urlAnalyzerTextView)
        resultTextView.text = "⏳ Setting up..."

        // FAB opens the suspicious messages inbox
        findViewById<FloatingActionButton>(R.id.fabInbox).setOnClickListener {
            startActivity(Intent(this, SuspiciousMessagesActivity::class.java))
        }

        requestPermissions()
        createNotificationChannel(applicationContext, CHANNEL_ID, CHANNEL_NAME)
    }

    override fun onResume() {
        super.onResume()
        // TODO
    }
    private fun requestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 999)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 999) {
            if (grantResults.all { it != PackageManager.PERMISSION_GRANTED }) {
                resultTextView.text = "❌ Permissions needed!"
            }
        }
    }

    fun createNotificationChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected smishing messages"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}