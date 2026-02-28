package com.streetball.voicescore.validation

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.parser.NumberWordParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class AudioTranscriptRegressionTest {

    private val parser = NumberWordParser()
    private val validationEngine = ScoreValidationEngine()

    @Test
    fun transcriptMatchesExpectedSequence() {
        val transcriptPath = System.getenv("AUDIO_TRANSCRIPT_PATH")
        val expectedSequencePath = System.getenv("AUDIO_EXPECTED_SEQUENCE_PATH")
        assumeTrue(
            "Set AUDIO_TRANSCRIPT_PATH and AUDIO_EXPECTED_SEQUENCE_PATH to run audio regression.",
            !transcriptPath.isNullOrBlank() && !expectedSequencePath.isNullOrBlank(),
        )

        val transcriptFile = File(transcriptPath!!)
        val expectedFile = File(expectedSequencePath!!)
        assertTrue("Transcript file missing: $transcriptPath", transcriptFile.exists())
        assertTrue("Expected sequence file missing: $expectedSequencePath", expectedFile.exists())

        val expected = parseExpectedSequence(expectedFile.readText())
        val utterances = parseTranscriptUtterances(transcriptFile)
        val accepted = applyTranscriptToScoring(utterances)

        assertEquals(
            buildString {
                appendLine("Accepted score sequence mismatch.")
                appendLine("Expected: ${formatPairs(expected)}")
                appendLine("Actual:   ${formatPairs(accepted)}")
                appendLine("Utterances parsed (${utterances.size}):")
                utterances.take(30).forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
            },
            expected,
            accepted,
        )
    }

    private fun applyTranscriptToScoring(utterances: List<String>): List<Pair<Int, Int>> {
        val accepted = mutableListOf<Pair<Int, Int>>()
        var state = GameState(teamAScore = 0, teamBScore = 0, targetScore = 21, winByTwo = true, gameActive = true)
        var previousUtterance = ""

        utterances.forEach { utterance ->
            val normalized = utterance.trim().lowercase(Locale.US)
            if (normalized.isBlank()) return@forEach
            if (normalized == previousUtterance) return@forEach
            previousUtterance = normalized

            val parsed = parser.parseWithDebug(normalized).parsedScores ?: return@forEach
            val (scoreA, scoreB) = parsed

            if (scoreA == state.teamAScore && scoreB == state.teamBScore) return@forEach

            val validation = validationEngine.validateVoiceTransition(state, scoreA, scoreB)
            if (!validation.isValid) return@forEach

            accepted += parsed

            val winner = validationEngine.winnerFor(
                scoreA = scoreA,
                scoreB = scoreB,
                targetScore = state.targetScore,
                winByTwo = state.winByTwo,
            )
            state = state.copy(teamAScore = scoreA, teamBScore = scoreB, gameActive = winner == null)
        }

        return accepted
    }

    private fun parseExpectedSequence(text: String): List<Pair<Int, Int>> {
        return text
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split(Regex("\\s+"))
                require(parts.size == 2) { "Expected '<scoreA> <scoreB>' line, got '$line'" }
                parts[0].toInt() to parts[1].toInt()
            }
            .toList()
    }

    private fun parseTranscriptUtterances(file: File): List<String> {
        val extension = file.extension.lowercase(Locale.US)
        val lines = file.readLines()
        return when (extension) {
            "srt" -> lines.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { it.all(Char::isDigit) }
                .filterNot { it.contains("-->") }
                .toList()

            else -> lines.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()
        }
    }

    private fun formatPairs(values: List<Pair<Int, Int>>): String {
        return values.joinToString(separator = ", ") { "${it.first}-${it.second}" }
    }
}
