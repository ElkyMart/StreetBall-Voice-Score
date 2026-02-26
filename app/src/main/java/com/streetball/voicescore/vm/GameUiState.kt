package com.streetball.voicescore.vm

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.Team

data class GameUiState(
    val gameState: GameState = GameState(),
    val micPermissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
    val isListening: Boolean = false,
    val showSettings: Boolean = false,
    val loudMode: Boolean = false,
    val highlightTeam: Team? = null,
    val gamePointTeam: Team? = null,
    val winner: Team? = null,
    val invalidFlash: Boolean = false,
    val lastError: String? = null,
)
