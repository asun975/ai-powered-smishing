package com.example.smishingdetection.data.smishingalert

data class SmishingAlert(
    val id: Long,
    val phone: String,
    val date: String,
    val message: String,
    val riskScore: Float,
    val riskLevel: String,
    val explanation: String,
    val urlScanResult: String
)
