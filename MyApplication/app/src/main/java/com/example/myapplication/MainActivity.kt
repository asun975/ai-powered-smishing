package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private var classifier: SmishingClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)

        resultTextView.text = "Loading model..."

        // Load model in background so app doesn't crash
        thread {
            classifier = SmishingClassifier(this)
            runOnUiThread {
                resultTextView.text = "✅ Model ready! Waiting for SMS..."
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECEIVE_SMS), 111)
        } else {
            receiveMSG()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            receiveMSG()
        }
    }

    fun receiveMSG() {
        val br = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                for (sms in Telephony.Sms.Intents.getMessagesFromIntent(p1)) {
                    val body = sms.displayMessageBody
                    val sender = sms.displayOriginatingAddress

                    smsTextView.text = "From: $sender\n\nMessage: $body"
                    resultTextView.text = "Analyzing..."

                    thread {
                        val (label, confidence) = classifier!!.classify(body)
                        val percentage = (confidence * 100).toInt()

                        runOnUiThread {
                            if (label == "SPAM") {
                                resultTextView.text = "⚠️ SMISHING DETECTED! ($percentage% confidence)"
                                resultTextView.setTextColor(getColor(android.R.color.holo_red_light))
                            } else {
                                resultTextView.text = "✅ Message appears safe ($percentage% confidence)"
                                resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                            }
                        }
                    }
                }
            }
        }
        registerReceiver(br, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }
}