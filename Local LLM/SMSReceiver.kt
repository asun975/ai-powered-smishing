package com.example.localllm


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.*


class SMSReceiver : BroadcastReceiver() {


    override fun onReceive(
        context: Context,
        intent: Intent
    ) {


        if(intent.action ==
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION){


            val messages =
                Telephony.Sms.Intents.getMessagesFromIntent(intent)


            for(sms in messages){

                val body =
                    sms.messageBody


                analyzeIncomingSMS(
                    context,
                    body
                )

            }

        }

    }



    private fun analyzeIncomingSMS(
        context: Context,
        message:String
    ){


        val scope =
            CoroutineScope(Dispatchers.IO)


        scope.launch {


            val result =
                AIAnalyzer.analyze(
                    message
                )


            val confidence =
                extractConfidence(result)


            val malicious =
                result.contains(
                    "MALICIOUS",
                    true
                )



            if(
                malicious &&
                confidence >= 70
            ){

                withContext(
                    Dispatchers.Main
                ){

                    val intent =
                        Intent(
                            context,
                            MainActivity::class.java
                        )

                    intent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK


                    intent.putExtra(
                        "WARNING",
                        result
                    )


                    context.startActivity(intent)

                }

            }

        }

    }



    private fun extractConfidence(
        text:String
    ):Int{


        val regex =
            Regex("\\d+")


        return regex.find(
            text
        )?.value?.toInt()
            ?:0

    }

}