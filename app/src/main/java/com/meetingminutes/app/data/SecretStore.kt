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
    val kimiApiKey: String,
    val kimiModel: String,
    val aliyunAccessKeyId: String,
    val aliyunAccessKeySecret: String,
    val aliyunAppKey: String,
    val aliyunRegion: String
) {
    val hasKimi: Boolean get() = kimiApiKey.isNotBlank()
    val hasAliyun: Boolean get() = aliyunAccessKeyId.isNotBlank() && aliyunAccessKeySecret.isNotBlank() && aliyunAppKey.isNotBlank()
}

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meeting_minutes_secrets", Context.MODE_PRIVATE)

    fun load(): CloudSettings {
        return CloudSettings(
            kimiApiKey = decryptPref("kimi_api_key"),
            kimiModel = prefs.getString("kimi_model", "kimi-k2.6") ?: "kimi-k2.6",
            aliyunAccessKeyId = decryptPref("aliyun_access_key_id"),
            aliyunAccessKeySecret = decryptPref("aliyun_access_key_secret"),
            aliyunAppKey = decryptPref("aliyun_app_key"),
            aliyunRegion = prefs.getString("aliyun_region", "cn-shanghai") ?: "cn-shanghai"
        )
    }

    fun save(settings: CloudSettings) {
        prefs.edit()
            .putString("kimi_api_key", encrypt(settings.kimiApiKey))
            .putString("kimi_model", settings.kimiModel.ifBlank { "kimi-k2.6" })
            .putString("aliyun_access_key_id", encrypt(settings.aliyunAccessKeyId))
            .putString("aliyun_access_key_secret", encrypt(settings.aliyunAccessKeySecret))
            .putString("aliyun_app_key", encrypt(settings.aliyunAppKey))
            .putString("aliyun_region", settings.aliyunRegion.ifBlank { "cn-shanghai" })
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

