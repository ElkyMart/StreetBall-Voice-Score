package com.streetball.voicescore.validation

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreValidationEngineTest {

    private val engine = ScoreValidationEngine()
    private val sequenceFixtures = listOf(
        SequenceFixture(
            name = "Recording 1.flac",
            scores = parseSequence(
                """
                2 0
                2 1
                2 2
                4 2
                6 2
                6 5
                8 5
                10 5
                12 5
                12 7
                14 7
                14 9
                14 11
                16 11
                19 11
                19 13
                21 13
                """.trimIndent(),
            ),
            expectedWinner = Team.A,
        ),
    )

    @Test
    fun allowsThreePointJumpForSingleTeam() {
        val current = GameState(teamAScore = 8, teamBScore = 7)
        val result = engine.validateVoiceTransition(current = current, newScoreA = 11, newScoreB = 7)

        assertTrue(result.isValid)
        assertEquals(Team.A, result.scoringTeam)
    }

    @Test
    fun rejectsBothTeamsScoringAtOnce() {
        val current = GameState(teamAScore = 8, teamBScore = 7)
        val result = engine.validateVoiceTransition(current = current, newScoreA = 9, newScoreB = 8)

        assertFalse(result.isValid)
        assertEquals("Both teams cannot score at once", result.reason)
    }

    @Test
    fun rejectsJumpAboveThree() {
        val current = GameState(teamAScore = 8, teamBScore = 7)
        val result = engine.validateVoiceTransition(current = current, newScoreA = 12, newScoreB = 7)

        assertFalse(result.isValid)
        assertEquals("Jump too large", result.reason)
    }

    @Test
    fun rejectsImpossibleNoWinByTwoState() {
        val current = GameState(
            teamAScore = 21,
            teamBScore = 21,
            targetScore = 21,
            winByTwo = false,
            gameActive = true,
        )
        val result = engine.validateVoiceTransition(current = current, newScoreA = 22, newScoreB = 21)

        assertFalse(result.isValid)
        assertEquals("Impossible win state", result.reason)
    }

    @Test
    fun winnerRequiresTwoPointLeadWhenEnabled() {
        val winner = engine.winnerFor(scoreA = 21, scoreB = 20, targetScore = 21, winByTwo = true)
        assertEquals(null, winner)
    }

    @Test
    fun acceptsKnownVoiceScoreSequences() {
        sequenceFixtures.forEach { fixture ->
            assertValidSequence(fixture)
        }
    }

    private fun assertValidSequence(fixture: SequenceFixture) {
        var current = GameState(teamAScore = 0, teamBScore = 0, targetScore = 21, winByTwo = true)

        fixture.scores.forEachIndexed { index, (newA, newB) ->
            val validation = engine.validateVoiceTransition(current, newA, newB)
            assertTrue(
                "Fixture '${fixture.name}' failed at step ${index + 1}: ${current.teamAScore}-${current.teamBScore} -> $newA-$newB (${validation.reason})",
                validation.isValid,
            )
            current = current.copy(teamAScore = newA, teamBScore = newB)
        }

        val winner = engine.winnerFor(
            scoreA = current.teamAScore,
            scoreB = current.teamBScore,
            targetScore = current.targetScore,
            winByTwo = current.winByTwo,
        )
        assertEquals("Fixture '${fixture.name}' winner mismatch", fixture.expectedWinner, winner)
    }

    private fun parseSequence(text: String): List<Pair<Int, Int>> {
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

    private data class SequenceFixture(
        val name: String,
        val scores: List<Pair<Int, Int>>,
        val expectedWinner: Team,
    )
}
