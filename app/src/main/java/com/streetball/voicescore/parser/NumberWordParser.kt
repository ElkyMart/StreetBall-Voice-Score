package com.streetball.voicescore.parser

import java.util.Locale

/**
 * Converts free-form speech like "score is ten nine" to exactly two numbers.
 * Returns null when the phrase does not clearly contain exactly two values.
 */
data class NumberParseDebug(
    val normalizedTokens: List<String>,
    val detectedNumbers: List<Int>,
    val parsedScores: Pair<Int, Int>?,
    val reason: String? = null,
    val heuristic: String? = null,
)

class NumberWordParser {

    private val wordToNumber: Map<String, Int> = mapOf(
        "zero" to 0,
        "oh" to 0,
        "o" to 0,
        "one" to 1,
        "won" to 1,
        "two" to 2,
        "to" to 2,
        "too" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "fourteen" to 14,
        "fifteen" to 15,
        "sixteen" to 16,
        "seventeen" to 17,
        "eighteen" to 18,
        "nineteen" to 19,
        "twenty" to 20,
        "twentyone" to 21,
        "twenty-one" to 21,
    )

    private val oneToNine = mapOf(
        "one" to 1,
        "two" to 2,
        "to" to 2,
        "too" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
    )
    private val mergedWordTokenToNumber = wordToNumber
        .mapKeys { entry ->
            entry.key.replace("-", "")
        }

    fun extractTwoScores(text: String): Pair<Int, Int>? {
        return parseWithDebug(text).parsedScores
    }

    fun parseWithDebug(text: String): NumberParseDebug {
        if (text.isBlank()) {
            return NumberParseDebug(
                normalizedTokens = emptyList(),
                detectedNumbers = emptyList(),
                parsedScores = null,
                reason = "blank input",
            )
        }

        val tokens = text
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9-]+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return NumberParseDebug(
                normalizedTokens = emptyList(),
                detectedNumbers = emptyList(),
                parsedScores = null,
                reason = "no tokens",
            )
        }

        val detectedNumbers = mutableListOf<Int>()
        var heuristic: String? = null
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]

            val numericValue = token.toIntOrNull()
            if (numericValue != null) {
                val compactPair = if (tokens.size == 1) {
                    compactDigitTokenToScores(token)
                } else {
                    null
                }

                if (compactPair != null) {
                    detectedNumbers += compactPair.first
                    detectedNumbers += compactPair.second
                    heuristic = heuristic ?: "split compact digit token"
                } else if (numericValue in 0..21) {
                    detectedNumbers += numericValue
                }
                index += 1
                continue
            }

            if (token == "twenty" && index + 1 < tokens.size) {
                val maybeOnes = oneToNine[tokens[index + 1]]
                if (maybeOnes != null) {
                    detectedNumbers += 20 + maybeOnes
                    index += 2
                    continue
                }
            }

            val mapped = wordToNumber[token]
            if (mapped != null) {
                detectedNumbers += mapped
                index += 1
                continue
            }

            val mergedPair = splitMergedScoreToken(token)
            if (mergedPair != null) {
                detectedNumbers += mergedPair.first
                detectedNumbers += mergedPair.second
                heuristic = heuristic ?: "split merged score token"
                index += 1
                continue
            }

            index += 1
        }

        val normalizedNumbers = if (detectedNumbers.size == 4 && detectedNumbers.all { it in 0..9 }) {
            heuristic = heuristic ?: "grouped four single digits into two scores"
            listOf(
                detectedNumbers[0] * 10 + detectedNumbers[1],
                detectedNumbers[2] * 10 + detectedNumbers[3],
            )
        } else {
            detectedNumbers.toList()
        }

        if (normalizedNumbers.size != 2) {
            return NumberParseDebug(
                normalizedTokens = tokens,
                detectedNumbers = normalizedNumbers,
                parsedScores = null,
                reason = "expected 2 numbers, found ${normalizedNumbers.size}",
                heuristic = heuristic,
            )
        }

        return NumberParseDebug(
            normalizedTokens = tokens,
            detectedNumbers = normalizedNumbers,
            parsedScores = normalizedNumbers[0] to normalizedNumbers[1],
            heuristic = heuristic,
        )
    }

    private fun compactDigitTokenToScores(token: String): Pair<Int, Int>? {
        if (!token.all(Char::isDigit)) return null

        return when (token.length) {
            2 -> token[0].digitToInt() to token[1].digitToInt()
            4 -> token.substring(0, 2).toInt() to token.substring(2, 4).toInt()
            else -> null
        }
    }

    private fun splitMergedScoreToken(token: String): Pair<Int, Int>? {
        if (token.any { !it.isLetter() && it != '-' }) return null
        val normalized = token.replace("-", "")
        if (normalized.length < 2) return null

        mergedWordTokenToNumber.forEach { (leftWord, leftScore) ->
            if (!normalized.startsWith(leftWord)) return@forEach
            val rightWord = normalized.removePrefix(leftWord)
            val rightScore = mergedWordTokenToNumber[rightWord] ?: return@forEach
            return leftScore to rightScore
        }
        return null
    }
}
