package com.streetball.voicescore.validation

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.Team
import kotlin.math.abs

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val scoringTeam: Team? = null,
)

/**
 * Enforces score transition rules for voice updates.
 */
class ScoreValidationEngine {

    fun validateVoiceTransition(current: GameState, newScoreA: Int, newScoreB: Int): ValidationResult {
        if (!current.gameActive) {
            return ValidationResult(isValid = false, reason = "Game is finished")
        }

        if (newScoreA < 0 || newScoreB < 0) {
            return ValidationResult(isValid = false, reason = "Negative scores are invalid")
        }

        val deltaA = newScoreA - current.teamAScore
        val deltaB = newScoreB - current.teamBScore

        if (deltaA == 0 && deltaB == 0) {
            return ValidationResult(isValid = false, reason = "No score change")
        }

        if (deltaA < 0 || deltaB < 0) {
            return ValidationResult(isValid = false, reason = "Score cannot decrease")
        }

        if (deltaA > 0 && deltaB > 0) {
            return ValidationResult(isValid = false, reason = "Both teams cannot score at once")
        }

        val maxJump = if (current.threePointMode) 3 else 2
        if (deltaA > maxJump || deltaB > maxJump) {
            return ValidationResult(isValid = false, reason = "Jump too large")
        }

        // Without win-by-two, only one side can legally be at/above target in active play.
        if (!current.winByTwo && newScoreA >= current.targetScore && newScoreB >= current.targetScore) {
            return ValidationResult(isValid = false, reason = "Impossible win state")
        }

        val scoringTeam = when {
            deltaA > 0 -> Team.A
            deltaB > 0 -> Team.B
            else -> null
        }

        return ValidationResult(isValid = true, scoringTeam = scoringTeam)
    }

    fun winnerFor(scoreA: Int, scoreB: Int, targetScore: Int, winByTwo: Boolean): Team? {
        if (!winByTwo) {
            if (scoreA < targetScore && scoreB < targetScore) return null
            if (scoreA == scoreB) return null
            return if (scoreA > scoreB) Team.A else Team.B
        }

        if (scoreA < targetScore && scoreB < targetScore) return null
        val diff = abs(scoreA - scoreB)
        if (diff < 2) return null
        return if (scoreA > scoreB) Team.A else Team.B
    }

    fun gamePointTeam(state: GameState): Team? {
        if (!state.gameActive) return null

        val threshold = if (state.winByTwo) state.targetScore - 1 else state.targetScore
        val aAtPoint = state.teamAScore >= threshold
        val bAtPoint = state.teamBScore >= threshold

        return when {
            aAtPoint && !bAtPoint -> Team.A
            bAtPoint && !aAtPoint -> Team.B
            aAtPoint && bAtPoint && state.teamAScore != state.teamBScore -> {
                if (state.teamAScore > state.teamBScore) Team.A else Team.B
            }
            else -> null
        }
    }
}
