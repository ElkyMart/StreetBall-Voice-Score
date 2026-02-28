package com.streetball.voicescore.validation

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreValidationEngineTest {

    private val engine = ScoreValidationEngine()

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
}
