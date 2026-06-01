package com.meetingminutes.app.network

import com.meetingminutes.app.data.CloudSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String
)

class UpdateCheckService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun check(settings: CloudSettings, currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!settings.updateChecksEnabled || settings.updateApiUrl.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(settings.updateApiUrl.trim())
                .get()
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "MeetingMinutesAndroid")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body.string())
                val update = if (json.has("versionCode")) parsePlainUpdate(json) else parseGithubRelease(json)
                update?.takeIf { it.versionCode > currentVersionCode }
            }
        }.getOrNull()
    }

    private fun parsePlainUpdate(json: JSONObject): UpdateInfo? {
        val versionCode = json.optInt("versionCode", 0)
        if (versionCode <= 0) return null
        val releaseUrl = json.optString("releaseUrl")
        val apkUrl = json.optString("apkUrl").ifBlank { releaseUrl }
        if (apkUrl.isBlank()) return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = json.optString("versionName").ifBlank { "v$versionCode" },
            releaseNotes = json.optString("notes").ifBlank { "发现新版本，可以下载更新。" },
            downloadUrl = apkUrl,
            releaseUrl = releaseUrl.ifBlank { apkUrl }
        )
    }

    private fun parseGithubRelease(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name")
        val body = json.optString("body")
        val versionCode = parseVersionCode(body) ?: parseTagVersionCode(tag) ?: return null
        val assets = json.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkUrl = url
                    break
                }
            }
        }
        val releaseUrl = json.optString("html_url")
        val downloadUrl = apkUrl.ifBlank { releaseUrl }
        if (downloadUrl.isBlank()) return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = json.optString("name").ifBlank { tag.ifBlank { "v$versionCode" } },
            releaseNotes = body.ifBlank { "发现新版本，可以下载更新。" },
            downloadUrl = downloadUrl,
            releaseUrl = releaseUrl.ifBlank { downloadUrl }
        )
    }

    private fun parseVersionCode(text: String): Int? {
        return Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun parseTagVersionCode(tag: String): Int? {
        return Regex("""^v?(\d+)$""", RegexOption.IGNORE_CASE)
            .find(tag.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
