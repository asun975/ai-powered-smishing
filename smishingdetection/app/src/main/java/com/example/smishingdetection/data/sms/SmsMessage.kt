package com.example.smishingdetection.data.sms

data class SmsMessage(
    val id: Long,
    val body: String,
    val address: String?,
    val date: String
)
