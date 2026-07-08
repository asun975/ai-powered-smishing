package com.example.smishingdetection.data.sms

class SmsRepository(
    private val smsProvider: DefaultSmsProvider
) {

    suspend fun getLatestSms(): SmsMessage? {
        return smsProvider.getLatestSms()
    }

    suspend fun getRecentSms(count: Int): List<SmsMessage> {
        return smsProvider.getRecentSms(count)
    }
    suspend fun getNewSmsSince(lastTimestamp: Long): List<SmsMessage> {
        return smsProvider.getNewSmsSince(lastTimestamp)
    }

}