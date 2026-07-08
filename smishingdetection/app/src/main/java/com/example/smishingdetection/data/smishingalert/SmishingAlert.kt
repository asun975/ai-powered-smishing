package com.example.smishingdetection.data.smishingalert

data class SmishingAlert(
    val id: Long,
    val phone: String,
    val date: String,
    val message: String,
    val riskScore: Double,
    val riskLevel: String,
    val riskScorePercent: Float,
    val explanation: String,
    val urlScanResult: String
)
