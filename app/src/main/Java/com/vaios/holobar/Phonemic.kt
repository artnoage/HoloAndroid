package com.vaios.holobar

import ai.onnxruntime.*
import android.content.res.AssetManager
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Phonemic(config: Properties, private val tokenToIdx: Map<String, Int>) {
    private val maxSeqLen: Int = config.getProperty("max_seq_len").toInt()
    private val lowercase: Boolean = config.getProperty("lowercase").toBoolean()
    private val languageCodeEn: String = config.getProperty("language_code_en")
    private val endToken: String = config.getProperty("end_token")
    private val cache: MutableMap<String, List<Int>> = ConcurrentHashMap()

    companion object {
        private lateinit var env: OrtEnvironment
        private lateinit var session: OrtSession

        @JvmStatic
        fun initialize(assetManager: AssetManager, modelFileName: String) {
            env = OrtEnvironment.getEnvironment()
            assetManager.open(modelFileName).use { modelStream ->
                val modelBytes = readInputStream(modelStream)
                session = env.createSession(modelBytes, OrtSession.SessionOptions())
            }
        }

        private fun readInputStream(inputStream: InputStream): ByteArray {
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            return buffer.toByteArray()
        }

    }

    private fun tokenize(word: String): List<Int> {
        val tokens = mutableListOf<Int>()
        tokens.add(tokenToIdx[languageCodeEn]!!)

        val processedWord = if (lowercase) word.lowercase() else word
        for (ch in processedWord) {
            tokenToIdx[ch.toString()]?.let { tokenId ->
                repeat(3) { tokens.add(tokenId) }
            }
        }

        tokens.add(tokenToIdx[endToken]!!)
        return tokens
    }

    private fun addThrees(phonemeList: List<Int>): List<Int> {
        return phonemeList.flatMap { listOf(3, it) } + 3
    }

    fun infer(sentence: String): List<Int> {
        val words = sentence.split("\\s+".toRegex())
        val wordOutputs = words.map { inferWord(it) }
        return combineWordOutputs(wordOutputs)
    }

    private fun inferWord(word: String): List<Int> {
        return cache.getOrPut(word) {
            val tokenizedWord = tokenize(word)
            runOnnxInference(tokenizedWord)
        }
    }

    private fun runOnnxInference(tokenizedWord: List<Int>): List<Int> {
        val paddedInput = Array(1) { LongArray(maxSeqLen) }
        tokenizedWord.forEachIndexed { index, token ->
            paddedInput[0][index] = token.toLong()
        }

        try {
            val inputTensor = OnnxTensor.createTensor(env, paddedInput)
            val result = session.run(mapOf("input" to inputTensor))
            when (val rawOutput = result.get(0).value) {
                is Array<*> -> {
                    if (rawOutput.isNotEmpty() && rawOutput[0] is Array<*>) {
                        val firstElement = rawOutput[0] as? Array<*>
                        if (firstElement != null && firstElement.isNotEmpty() && firstElement[0] is FloatArray) {
                            val typedOutput = rawOutput as Array<Array<FloatArray>>
                            return typedOutput[0].drop(1)
                                .takeWhile { it.maxOrNull()!! > it[3] }
                                .map { it.indices.maxByOrNull { i -> it[i] }!! }
                                .filter { it != 0 }
                        }
                    }
                }
            }
            throw PhonemizerInferenceException("Unexpected output type from ONNX model")
        } catch (e: OrtException) {
            throw PhonemizerInferenceException("Failed to run ONNX inference", e)
        }
    }

    private fun combineWordOutputs(wordOutputs: List<List<Int>>): List<Int> {
        val combinedOutput = wordOutputs.flatMapIndexed { index, output ->
            if (index > 0) listOf(2) + output else output
        }

        val outputWithoutDuplicates = combinedOutput.fold(mutableListOf<Int>()) { acc, value ->
            if (acc.isEmpty() || value != acc.last() || value == 2) {
                acc.add(value)
            }
            acc
        }

        return addThrees(outputWithoutDuplicates)
    }

    class PhonemizerInferenceException : RuntimeException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}