package com.meetingminutes.app.data

import android.content.Context
import android.content.SharedPreferences

data class CloudSettings(
    val transcriptionEngine: String,
    val summaryEngine: String,
    val keepAudioAfterSuccess: Boolean
) {
    val isFreeMode: Boolean get() = true
}

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meeting_minutes_settings", Context.MODE_PRIVATE)

    fun load(): CloudSettings {
        return CloudSettings(
            transcriptionEngine = prefs.getString("transcription_engine", "离线中文识别") ?: "离线中文识别",
            summaryEngine = prefs.getString("summary_engine", "本地规则纪要") ?: "本地规则纪要",
            keepAudioAfterSuccess = prefs.getBoolean("keep_audio_after_success", false)
        )
    }

    fun save(settings: CloudSettings) {
        prefs.edit()
            .putString("transcription_engine", settings.transcriptionEngine.ifBlank { "离线中文识别" })
            .putString("summary_engine", settings.summaryEngine.ifBlank { "本地规则纪要" })
            .putBoolean("keep_audio_after_success", settings.keepAudioAfterSuccess)
            .apply()
    }
}
