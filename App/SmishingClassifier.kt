package com.example.myapplication

import android.content.Context
import ai.onnxruntime.*
import java.nio.LongBuffer

class SmishingClassifier(context: Context) {

    private val session: OrtSession
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val vocab = mutableMapOf<String, Int>()

    init {
        val modelBytes = context.assets.open("model_quantized.onnx").readBytes()
        session = env.createSession(modelBytes, OrtSession.SessionOptions())

        context.assets.open("vocab.txt").bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, word -> vocab[word.trim()] = index }
        }
    }

    fun classify(text: String): Pair<String, Float> {
        val tokens = tokenize(text)
        val attentionMask = LongArray(tokens.size) { if (tokens[it] != 0L) 1L else 0L }

        val shape = longArrayOf(1, tokens.size.toLong())

        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), shape)
        val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)

        val inputs = mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor)
        val output = session.run(inputs)

        val logits = (output[0].value as Array<FloatArray>)[0]
        val expSum = logits.map { Math.exp(it.toDouble()) }.sum()
        val probs = logits.map { (Math.exp(it.toDouble()) / expSum).toFloat() }

        return if (probs[1] > probs[0]) {
            Pair("SPAM", probs[1])
        } else {
            Pair("SAFE", probs[0])
        }
    }

    private fun tokenize(text: String, maxLength: Int = 128): LongArray {
        val tokens = mutableListOf<Long>()
        tokens.add((vocab["[CLS]"] ?: 101).toLong())

        text.lowercase().split(" ").forEach { word ->
            val id = vocab[word] ?: (vocab["[UNK]"] ?: 100)
            tokens.add(id.toLong())
        }

        tokens.add((vocab["[SEP]"] ?: 102).toLong())

        return LongArray(maxLength) { i ->
            if (i < tokens.size) tokens[i] else 0L
        }
    }
}
