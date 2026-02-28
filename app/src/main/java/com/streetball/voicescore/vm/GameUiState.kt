package com.streetball.voicescore.vm

import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.Team

enum class VoiceLoopTone {
    READY,
    SUCCESS,
    WARNING,
    ERROR,
}

data class GameUiState(
    val gameState: GameState = GameState(),
    val micPermissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
    val isListening: Boolean = false,
    val showSettings: Boolean = false,
    val loudMode: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val videoCaptureMode: Boolean = false,
    val showVoiceDebug: Boolean = true,
    val hasSavedPreset: Boolean = false,
    val presetStatusMessage: String? = null,
    val highlightTeam: Team? = null,
    val gamePointTeam: Team? = null,
    val winner: Team? = null,
    val invalidFlash: Boolean = false,
    val lastError: String? = null,
    val voiceDebugLines: List<String> = emptyList(),
    val exportDebugMessage: String? = null,
    val lastHeardText: String? = null,
    val lastInterpretedText: String? = null,
    val voiceLoopStatus: String = "Ready. Say both scores.",
    val voiceLoopHint: String = "Try: one zero",
    val voiceLoopTone: VoiceLoopTone = VoiceLoopTone.READY,
)
