package com.example.smishingdetection.data.network.explainer.model

data class ExplainerRequest(
    val text: String,
    val classification: String,
    val risk_score: Float
)
