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
    fun `validateVoiceTransition accepts legal single-team jump`() {
        val current = GameState(teamAScore = 6, teamBScore = 5, threePointMode = true)

        val result = engine.validateVoiceTransition(current, newScoreA = 9, newScoreB = 5)

        assertTrue(result.isValid)
        assertEquals(Team.A, result.scoringTeam)
    }

    @Test
    fun `validateVoiceTransition rejects both teams scoring`() {
        val current = GameState(teamAScore = 6, teamBScore = 5)

        val result = engine.validateVoiceTransition(current, newScoreA = 7, newScoreB = 6)

        assertFalse(result.isValid)
    }

    @Test
    fun `validateVoiceTransition rejects jump too large for current mode`() {
        val current = GameState(teamAScore = 6, teamBScore = 5, threePointMode = false)

        val result = engine.validateVoiceTransition(current, newScoreA = 9, newScoreB = 5)

        assertFalse(result.isValid)
    }

    @Test
    fun `winnerFor resolves win-by-two correctly`() {
        assertEquals(Team.A, engine.winnerFor(scoreA = 21, scoreB = 19, targetScore = 21, winByTwo = true))
        assertEquals(null, engine.winnerFor(scoreA = 21, scoreB = 20, targetScore = 21, winByTwo = true))
    }
}
