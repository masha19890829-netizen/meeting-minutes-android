package com.meetingminutes.app.network

import com.meetingminutes.app.data.CloudSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class AliyunTokenClient {
    private val client = OkHttpClient()

    fun createToken(settings: CloudSettings): String {
        val params = sortedMapOf(
            "AccessKeyId" to settings.aliyunAccessKeyId,
            "Action" to "CreateToken",
            "Format" to "JSON",
            "RegionId" to settings.aliyunRegion.ifBlank { "cn-shanghai" },
            "SignatureMethod" to "HMAC-SHA1",
            "SignatureNonce" to UUID.randomUUID().toString(),
            "SignatureVersion" to "1.0",
            "Timestamp" to utcTimestamp(),
            "Version" to "2019-02-28"
        )
        val canonical = params.entries.joinToString("&") { "${percentEncode(it.key)}=${percentEncode(it.value)}" }
        val stringToSign = "GET&%2F&${percentEncode(canonical)}"
        val signature = sign(stringToSign, settings.aliyunAccessKeySecret + "&")
        val urlBuilder = "https://nls-meta.${settings.aliyunRegion.ifBlank { "cn-shanghai" }}.aliyuncs.com/".toHttpUrl().newBuilder()
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        urlBuilder.addQueryParameter("Signature", signature)
        val request = Request.Builder().url(urlBuilder.build()).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("阿里云 Token 返回 ${response.code}: ${response.body.string()}")
            val body = JSONObject(response.body.string())
            return body.getJSONObject("Token").getString("Id")
        }
    }

    private fun utcTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun percentEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun sign(value: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}

