package com.example.smishingdetection.data.network.classifier.model

data class ClassifyResult(
    val label: String,
    val riskScore: Float,
    val riskCategory: String
)
