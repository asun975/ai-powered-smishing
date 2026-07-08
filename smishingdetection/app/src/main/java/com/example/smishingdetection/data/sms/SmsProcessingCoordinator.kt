package com.example.smishingdetection.data.sms

import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.network.url.UrlApiRepository

class SmsProcessingCoordinator(
    private val smsRepository: SmsRepository,
    private val quarantineRepository: QuarantineRepository,
    private val classifierRepository: ClassifierRepository,
    private val explainerRepository: ExplainerRepository,
    private val urlApiRepository: UrlApiRepository
) {
    suspend fun processMessage(message: SmsMessage) {
        val classifier = classifierRepository.classify(message)
        val explainer = explainerRepository.explain(message, )
    }
}