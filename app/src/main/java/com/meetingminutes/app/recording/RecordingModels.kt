package com.meetingminutes.app.recording

data class RecordingUiState(
    val isRecording: Boolean = false,
    val realtime: Boolean = true,
    val title: String = "",
    val durationMs: Long = 0,
    val level: Float = 0f,
    val liveTranscript: String = "",
    val message: String = "准备记录下一场会议",
    val currentMeetingId: Long? = null
)

