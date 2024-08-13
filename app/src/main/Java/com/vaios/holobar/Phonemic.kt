package com.vaios.holobar

import ai.onnxruntime.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class Phonemic(config: Properties, private val tokenToIdx: Map<String, Int>) {
    private val maxSeqLen: Int = config.getProperty("max_seq_len").toInt()
    private val lowercase: Boolean = config.getProperty("lowercase").toBoolean()
    private val languageCodeEn: String = config.getProperty("language_code_en")
    private val endToken: String = config.getProperty("end_token")
    private val cache: MutableMap<String, List<Int>> = ConcurrentHashMap()
    private val paddedInput = Array(1) { LongArray(maxSeqLen) }

    companion object {
        private lateinit var env: OrtEnvironment
        private lateinit var session: OrtSession

        @JvmStatic
        fun initialize(modelFilePath: String) {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = File(modelFilePath).readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(calculateOptimalThreads())
            }
            session = env.createSession(modelBytes, sessionOptions)
        }

        private fun calculateOptimalThreads(): Int {
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            return max(1, (availableProcessors * 0.75).toInt())
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

    fun infer(sentence: String): List<Int> {
        val words = sentence.split("\\s+".toRegex())
        val wordOutputs = words.map { inferWord(it) }
        return addThrees(combineWordOutputs(wordOutputs))
    }

    private fun inferWord(word: String): List<Int> {
        return cache.getOrPut(word) {
            val tokenizedWord = tokenize(word)
            runOnnxInference(tokenizedWord)
        }
    }

    private fun runOnnxInference(tokenizedWord: List<Int>): List<Int> {
        synchronized(paddedInput) {
            paddedInput[0].fill(0) // Clear previous data
            tokenizedWord.forEachIndexed { index, token ->
                paddedInput[0][index] = token.toLong()
            }
        }

        try {
            val inputTensor = OnnxTensor.createTensor(env, paddedInput)
            val result = session.run(mapOf("input" to inputTensor))
            val outputTensor = result.get(0)

            return when (val value = outputTensor.value) {
                is Array<*> -> processOutputArray(value)
                else -> throw PhonemizerInferenceException("Unexpected output type from ONNX model: ${value?.javaClass?.name}")
            }
        } catch (e: OrtException) {
            throw PhonemizerInferenceException("Failed to run ONNX inference", e)
        }
    }

    private fun processOutputArray(outputArray: Array<*>): List<Int> {
        if (outputArray.isEmpty() || outputArray[0] !is Array<*>) {
            throw PhonemizerInferenceException("Unexpected output array structure")
        }

        val firstElement = outputArray[0] as Array<*>
        if (firstElement.isEmpty() || firstElement[0] !is FloatArray) {
            throw PhonemizerInferenceException("Unexpected inner array type")
        }

        return firstElement.asSequence()
            .drop(1)
            .map { it as FloatArray }
            .takeWhile { it.maxOrNull()!! > it[3] }
            .map { it.indices.maxByOrNull { i -> it[i] }!! }
            .filter { it != 0 }
            .toList()
    }

    private fun combineWordOutputs(wordOutputs: List<List<Int>>): List<Int> {
        val combinedOutput = wordOutputs.flatMapIndexed { index, output ->
            if (index > 0) listOf(2) + output else output
        }

        return combinedOutput.fold(mutableListOf()) { acc, value ->
            if (acc.isEmpty() || value != acc.last() || value == 2) {
                acc.add(value)
            }
            acc
        }
    }

    private fun addThrees(phonemeList: List<Int>): List<Int> {
        return phonemeList.flatMap { listOf(3, it) } + 3
    }

    class PhonemizerInferenceException : RuntimeException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}