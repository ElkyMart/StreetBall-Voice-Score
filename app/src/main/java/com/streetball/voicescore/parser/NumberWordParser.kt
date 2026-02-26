package com.streetball.voicescore.parser

import java.util.Locale

/**
 * Converts free-form speech like "score is ten nine" to exactly two numbers.
 * Returns null when the phrase does not clearly contain exactly two values.
 */
class NumberWordParser {

    private val wordToNumber: Map<String, Int> = mapOf(
        "zero" to 0,
        "oh" to 0,
        "o" to 0,
        "one" to 1,
        "two" to 2,
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
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
    )

    fun extractTwoScores(text: String): Pair<Int, Int>? {
        if (text.isBlank()) return null

        val tokens = text
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9-]+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return null

        val detectedNumbers = mutableListOf<Int>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]

            val numericValue = token.toIntOrNull()
            if (numericValue != null) {
                if (numericValue in 0..99) {
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
            }

            index += 1
        }

        if (detectedNumbers.size != 2) return null
        return detectedNumbers[0] to detectedNumbers[1]
    }
}
