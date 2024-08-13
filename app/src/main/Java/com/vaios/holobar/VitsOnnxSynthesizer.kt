package com.vaios.holobar

import ai.onnxruntime.*
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max

class VitsOnnxSynthesizer(context: Context, modelFilePath: String) : AutoCloseable {
    companion object {
        private const val TAG = "VitsOnnxSynthesizer"

        private fun calculateOptimalThreads(): Int {
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            return max(1, (availableProcessors * 0.75).toInt().coerceAtMost(4))
        }
    }

    private val tokenizer: Tokenizer = Tokenizer(context)
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = File(modelFilePath).readBytes()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(calculateOptimalThreads())
        }
        session = env.createSession(modelBytes, sessionOptions)
    }

    fun tts(text: String, speakerId: Long): FloatArray {
        val inputs = tokenizer.textToIds(text)
        val inputArray = inputs.map { it.toLong() }.toLongArray()
        val inputLengths = longArrayOf(inputArray.size.toLong())
        val scales = floatArrayOf(0.667f, 1.3f, 0.8f)
        val sid = longArrayOf(speakerId)

        val tensors = mapOf(
            "input" to OnnxTensor.createTensor(env, LongBuffer.wrap(inputArray), longArrayOf(1, inputArray.size.toLong())),
            "input_lengths" to OnnxTensor.createTensor(env, LongBuffer.wrap(inputLengths), longArrayOf(1)),
            "scales" to OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3)),
            "sid" to OnnxTensor.createTensor(env, LongBuffer.wrap(sid), longArrayOf(1))
        )

        return session.run(tensors).use { result ->
            val outputTensor = result.get(0)
            when (val value = outputTensor.value) {
                is Array<*> -> {
                    if (value.isNotEmpty() && value[0] is Array<*>) {
                        val innerArray = value[0] as? Array<*>
                        if (innerArray != null && innerArray.isNotEmpty() && innerArray[0] is FloatArray) {
                            (innerArray[0] as FloatArray)
                        } else {
                            throw IllegalStateException("Unexpected inner array type")
                        }
                    } else {
                        throw IllegalStateException("Unexpected array structure")
                    }
                }
                else -> throw IllegalStateException("Unexpected output type: ${value?.javaClass?.name}")
            }
        }
    }

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: OrtException) {
            Log.e(TAG, "Error closing ONNX session or environment", e)
        }
    }
}