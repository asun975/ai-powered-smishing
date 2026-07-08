package com.example.smishingdetection.data.network.explainer.model

data class ExplainerRequest(
    val input: String,
    val classification: String,
    val riskScore: Float
)
