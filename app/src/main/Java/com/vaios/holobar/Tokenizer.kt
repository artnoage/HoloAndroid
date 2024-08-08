package com.vaios.holobar

import org.apache.commons.lang3.tuple.ImmutablePair
import com.ibm.icu.text.RuleBasedNumberFormat
import android.content.Context
import java.io.IOException
import java.text.Normalizer
import java.util.*
import java.util.regex.Pattern
import java.util.Locale

class Tokenizer(context: Context) {
    private val textSymbols: String
    private val lowercase: Boolean
    private val tokenToIdx: Map<String, Int>
    private val phonemic: Phonemic
    private val abbreviations: List<ImmutablePair<Pattern, String>>
    private val languageCodeEn: String
    private val languageCodeDe: String
    private val padToken: String
    private val endToken: String
    private val numberFormat: RuleBasedNumberFormat

    companion object {
        private val WHITESPACE_PATTERN = Pattern.compile("\\s+")
        private val NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b")
    }

    init {
        val config = loadConfig(context)
        textSymbols = config.getProperty("text_symbols")
        lowercase = config.getProperty("lowercase").toBoolean()
        languageCodeEn = config.getProperty("language_code_en")
        languageCodeDe = config.getProperty("language_code_de")
        padToken = config.getProperty("pad_token")
        endToken = config.getProperty("end_token")
        tokenToIdx = createTokenToIdx()
        phonemic = Phonemic(config, tokenToIdx)
        abbreviations = createAbbreviations(config)
        numberFormat = RuleBasedNumberFormat(Locale.US, RuleBasedNumberFormat.SPELLOUT)
    }

    private fun loadConfig(context: Context): Properties {
        val prop = Properties()
        val resourceName = "tokenizer_config.properties"
        try {
            context.assets.open(resourceName).use { input ->
                    prop.load(input)
            }
        } catch (ex: IOException) {
            throw TokenizerException("Failed to load configuration", ex)
        }
        return prop
    }

    private fun createTokenToIdx(): Map<String, Int> {
        val tokenToIdx = LinkedHashMap<String, Int>()
        tokenToIdx[padToken] = 0
        tokenToIdx[languageCodeEn] = tokenToIdx.size
        tokenToIdx[languageCodeDe] = tokenToIdx.size
        tokenToIdx[endToken] = tokenToIdx.size

        ('a'..'z').forEach { c ->
            tokenToIdx[c.toString()] = tokenToIdx.size
        }
        ('A'..'Z').forEach { c ->
            tokenToIdx[c.toString()] = tokenToIdx.size
        }

        "äöüÄÖÜß".forEach { c ->
            tokenToIdx[c.toString()] = tokenToIdx.size
        }
        tokenToIdx["'"] = tokenToIdx.size

        return tokenToIdx
    }

    private fun createAbbreviations(config: Properties): List<ImmutablePair<Pattern, String>> {
        return config.stringPropertyNames()
                .filter { it.startsWith("abbr.") }
            .map { key ->
                val abbreviation = key.substring(5)
            val expansion = config.getProperty(key)
            val pattern = Pattern.compile("\\b$abbreviation\\.", Pattern.CASE_INSENSITIVE)
            ImmutablePair.of(pattern, expansion)
        }
    }

    private fun convertToAscii(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("[^\\p{ASCII}]".toRegex(), "")
    }

    private fun expandAbbreviations(text: String): String {
        var result = text
        for ((pattern, replacement) in abbreviations) {
            result = pattern.matcher(result).replaceAll(replacement)
        }
        return result
    }

    private fun convertNumbersToWords(text: String): String {
        val matcher = NUMBER_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val number = matcher.group().toInt()
            matcher.appendReplacement(sb, numberFormat.format(number))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun cleanText(text: String): String {
        val sb = StringBuilder(text.length)
        sb.append(convertToAscii(text))
        if (lowercase) {
            sb.toString().lowercase()
        }
        var expanded = expandAbbreviations(sb.toString())
        expanded = convertNumbersToWords(expanded)
        return WHITESPACE_PATTERN.matcher(expanded).replaceAll(" ").trim()
    }

    fun textToIds(text: String): List<Int> {
        val cleanedText = cleanText(text)
        return phonemic.infer(cleanedText)
    }

    class TokenizerException(message: String, cause: Throwable) : RuntimeException(message, cause)
}