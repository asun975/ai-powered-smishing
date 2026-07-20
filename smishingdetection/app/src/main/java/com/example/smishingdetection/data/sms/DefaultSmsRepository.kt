package com.example.smishingdetection.data.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SmsRespository {
    suspend fun getLatestSms(lastProcessedId: Long?): SmsMessage?
    suspend fun getRecentSms(count: Int): List<SmsMessage>
    suspend fun getNewSmsSince(lastTimestamp: Long): SmsMessage?
}

class DefaultSmsRespository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SmsRespository {
    /*
    * Get the most recent sms message
     */
    override suspend fun getLatestSms(lastProcessedId: Long?): SmsMessage? =
        withContext(ioDispatcher) {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                Log.d("SMS", "cursor = $cursor")
                if (cursor.moveToFirst() && cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)) != lastProcessedId) {
                    Log.d("Debug content provider", "returned row")
                    return@withContext SmsMessage(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown",
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                        date = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    )
                }
            }
            Log.d("Debug content provider", "last processed id $lastProcessedId")
            return@withContext null
        }

    /*
    * Get the newest N sms messages
     */
    override suspend fun getRecentSms(count: Int): List<SmsMessage> =
        withContext(ioDispatcher) {
            val messages = mutableListOf<SmsMessage>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && messages.size < count) {
                    messages += SmsMessage(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                        date = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    )
                }
            }

            return@withContext messages
        }

    /*
    * Get a list of sms messages since timestamp of last processed message.
     */
    override suspend fun getNewSmsSince(lastTimestamp: Long): SmsMessage? =
        withContext(ioDispatcher) {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            val selection = "${Telephony.Sms.DATE} > ?"
            val args = arrayOf(lastTimestamp.toString())

            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@use SmsMessage(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                        date = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    )
                }
            }
            return@withContext null
        }
}