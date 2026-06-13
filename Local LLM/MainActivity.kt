package com.example.localllm


import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        // Request SMS permissions

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ),
            100
        )


        // Check if opened from SMSReceiver

        val warning =
            intent.getStringExtra("WARNING")


        setContent {

            SMSDetectorUI(
                warning
            )

        }

    }




    @Composable
    fun SMSDetectorUI(
        warningMessage: String?
    ) {


        var inputText by remember {

            mutableStateOf("")

        }


        var resultText by remember {

            mutableStateOf(
                "Waiting for analysis..."
            )

        }


        var showWarning by remember {

            mutableStateOf(
                warningMessage != null
            )

        }




        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)

        ) {



            Text(

                text = "PhishGuard",

                style =
                    MaterialTheme.typography.headlineLarge

            )


            Text(

                text =
                    "AI SMS phishing detector",

                color =
                    MaterialTheme.colorScheme.onSurfaceVariant

            )



            Spacer(
                modifier =
                    Modifier.height(25.dp)
            )





            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                elevation =
                    CardDefaults.cardElevation(
                        6.dp
                    )

            ) {


                Column(

                    modifier =
                        Modifier.padding(20.dp)

                ) {


                    Text(
                        text =
                            "Protection Status",

                        style =
                            MaterialTheme.typography.titleMedium
                    )


                    Text(

                        text =
                            "ACTIVE",

                        color =
                            MaterialTheme.colorScheme.primary,

                        style =
                            MaterialTheme.typography.headlineMedium

                    )


                    Text(
                        text =
                            "SMS monitoring enabled"
                    )


                }

            }





            Spacer(
                modifier =
                    Modifier.height(25.dp)
            )




            Text(

                text =
                    "Test SMS",

                style =
                    MaterialTheme.typography.titleLarge

            )




            OutlinedTextField(

                value = inputText,

                onValueChange = {

                    inputText = it

                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),

                label = {

                    Text(
                        "Enter SMS message"
                    )

                }

            )




            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )




            Button(

                modifier =
                    Modifier.fillMaxWidth(),

                onClick = {


                    lifecycleScope.launch {


                        resultText =
                            "Analyzing..."


                        val result =
                            AIAnalyzer.analyze(
                                inputText
                            )


                        resultText =
                            result


                    }


                }

            ) {

                Text(
                    "Analyze Message"
                )

            }




            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )





            if (resultText.contains("Classification")) {
                PhishingResultCard(
                    resultText = resultText,
                    onDismiss = { resultText = "Waiting for analysis..." }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(5.dp)
                ) {
                    Text(
                        text = resultText,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // POPUP WARNING
            if (showWarning && warningMessage != null) {
                Dialog(onDismissRequest = { showWarning = false }) {
                    PhishingResultCard(
                        resultText = warningMessage,
                        onDismiss = { showWarning = false }
                    )
                }
            }



        }
    }


    @Composable
    fun PhishingResultCard(
        resultText: String,
        onDismiss: () -> Unit
    ) {
        val lines = resultText.lines()
        val classification = lines.find { it.contains("Classification", true) }?.substringAfter(":")?.trim() ?: "Unknown"
        val confidence = lines.find { it.contains("Confidence", true) }?.substringAfter(":")?.trim() ?: "N/A"
        val reason = lines.find { it.contains("Reason", true) }?.substringAfter(":")?.trim() ?: "No details"

        val isMalicious = classification.contains("MALICIOUS", true)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(25.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚠️", fontSize = 35.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Suspicious SMS Detected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Description
                Text(
                    text = "This message may be a phishing attempt.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 20.dp)
                )

                // Divider
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 20.dp),
                    thickness = 1.dp,
                    color = Color(0xFFDDDDDD)
                )

                // Classification
                Text(
                    text = "Classification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = classification.uppercase(),
                    color = if (isMalicious) Color(0xFFD32F2F) else Color(0xFF388E3C),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp)
                )

                // Confidence
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Confidence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = confidence,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 5.dp)
                )

                // Reason
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Reason",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 25.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("View Details")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }


}
