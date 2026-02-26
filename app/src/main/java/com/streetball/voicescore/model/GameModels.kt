package com.streetball.voicescore.model

enum class UpdateSource {
    VOICE,
    MANUAL,
}

enum class Team {
    A,
    B,
}

data class ScoreEvent(
    val timestamp: Long,
    val oldScoreA: Int,
    val oldScoreB: Int,
    val newScoreA: Int,
    val newScoreB: Int,
    val source: UpdateSource,
)

data class GameState(
    val teamAScore: Int = 0,
    val teamBScore: Int = 0,
    val targetScore: Int = 21,
    val winByTwo: Boolean = true,
    val threePointMode: Boolean = false,
    val gameActive: Boolean = true,
    val lastUpdateSource: UpdateSource = UpdateSource.MANUAL,
    val history: List<ScoreEvent> = emptyList(),
)
