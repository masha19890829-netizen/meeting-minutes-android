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
    val openAiApiKey: String,
    val transcriptionModel: String,
    val summaryModel: String
) {
    val hasOpenAi: Boolean get() = openAiApiKey.isNotBlank()
}

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meeting_minutes_secrets", Context.MODE_PRIVATE)

    fun load(): CloudSettings {
        return CloudSettings(
            openAiApiKey = decryptPref("openai_api_key"),
            transcriptionModel = prefs.getString("openai_transcription_model", "gpt-4o-mini-transcribe")
                ?: "gpt-4o-mini-transcribe",
            summaryModel = prefs.getString("openai_summary_model", "gpt-4o-mini")
                ?: "gpt-4o-mini"
        )
    }

    fun save(settings: CloudSettings) {
        prefs.edit()
            .putString("openai_api_key", encrypt(settings.openAiApiKey))
            .putString("openai_transcription_model", settings.transcriptionModel.ifBlank { "gpt-4o-mini-transcribe" })
            .putString("openai_summary_model", settings.summaryModel.ifBlank { "gpt-4o-mini" })
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
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
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
        private const val KEY_ALIAS = "meeting_minutes_cloud_secret"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
