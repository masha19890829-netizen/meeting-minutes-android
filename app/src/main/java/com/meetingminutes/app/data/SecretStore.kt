package com.meetingminutes.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudSettings(
    val transcriptionEngine: String = "离线中文识别",
    val summaryEngine: String = "本地规则纪要",
    val keepAudioAfterSuccess: Boolean = false,
    val aiPolishEnabled: Boolean = false,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = ""
) {
    val isFreeMode: Boolean get() = true
    val canUseExternalAi: Boolean get() = aiPolishEnabled && aiBaseUrl.isNotBlank() && aiModel.isNotBlank()
}

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meeting_minutes_settings", Context.MODE_PRIVATE)

    fun load(): CloudSettings {
        return CloudSettings(
            transcriptionEngine = prefs.getString("transcription_engine", "离线中文识别") ?: "离线中文识别",
            summaryEngine = prefs.getString("summary_engine", "本地规则纪要") ?: "本地规则纪要",
            keepAudioAfterSuccess = prefs.getBoolean("keep_audio_after_success", false),
            aiPolishEnabled = prefs.getBoolean("ai_polish_enabled", false),
            aiBaseUrl = prefs.getString("ai_base_url", "") ?: "",
            aiApiKey = decryptPref("ai_api_key"),
            aiModel = prefs.getString("ai_model", "") ?: ""
        )
    }

    fun save(settings: CloudSettings) {
        prefs.edit()
            .putString("transcription_engine", settings.transcriptionEngine.ifBlank { "离线中文识别" })
            .putString("summary_engine", settings.summaryEngine.ifBlank { "本地规则纪要" })
            .putBoolean("keep_audio_after_success", settings.keepAudioAfterSuccess)
            .putBoolean("ai_polish_enabled", settings.aiPolishEnabled)
            .putString("ai_base_url", settings.aiBaseUrl.trim().trimEnd('/'))
            .putString("ai_api_key", encrypt(settings.aiApiKey))
            .putString("ai_model", settings.aiModel.trim())
            .apply()
    }

    private fun decryptPref(key: String): String {
        val encoded = prefs.getString(key, "") ?: ""
        return if (encoded.isBlank()) "" else runCatching { decrypt(encoded) }.getOrDefault("")
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        if (bytes.size <= IV_BYTES) return ""
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val payload = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), StandardCharsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "meeting_minutes_optional_ai_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
