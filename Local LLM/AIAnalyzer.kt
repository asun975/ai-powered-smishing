package com.example.localllm


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject



object AIAnalyzer {


    private val client =
        OkHttpClient()



    suspend fun analyze(
        message:String
    ):String {


        return withContext(
            Dispatchers.IO
        ){


            val prompt =
                """
            You are a phishing detection AI.

            Analyze this SMS.

            Return ONLY:

            Classification: SAFE or MALICIOUS
            Confidence: number between 0 and 100
            Reason: short explanation

            Message:
            $message

            """.trimIndent()



            val json =
                JSONObject().apply {

                    put(
                        "model",
                        "gemma2:2b"
                    )


                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {

                                put(
                                    "role",
                                    "user"
                                )

                                put(
                                    "content",
                                    prompt
                                )

                            }
                        )
                    )


                    put(
                        "stream",
                        false
                    )
                }



            val body =
                RequestBody.create(
                    "application/json".toMediaType(),
                    json.toString()
                )



            val request =
                Request.Builder()
                    .url(
                        "http://10.0.2.2:11434/api/chat"
                    )
                    .post(body)
                    .build()



            val response =
                client.newCall(
                    request
                ).execute()



            val result =
                JSONObject(
                    response.body!!.string()
                )



            result
                .getJSONObject("message")
                .getString("content")

        }

    }


}