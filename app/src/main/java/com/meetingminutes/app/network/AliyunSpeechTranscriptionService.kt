package com.meetingminutes.app.network

import com.meetingminutes.app.data.SecretStore
import com.meetingminutes.app.data.TranscriptLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AliyunSpeechTranscriptionService(private val secretStore: SecretStore) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val tokenClient = AliyunTokenClient()

    fun hasCredentials(): Boolean = secretStore.load().hasAliyun

    suspend fun openRealtimeSession(
        onText: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ): RealtimeSpeechSession = withContext(Dispatchers.IO) {
        val settings = secretStore.load()
        if (!settings.hasAliyun) error("请先在设置里填写阿里云语音识别密钥。")
        val token = tokenClient.createToken(settings)
        val taskId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("wss://nls-gateway-${settings.aliyunRegion.ifBlank { "cn-shanghai" }}.aliyuncs.com/ws/v1?token=$token")
            .addHeader("X-NLS-Token", token)
            .build()
        val listener = SpeechWebSocketListener(
            taskId = taskId,
            appKey = settings.aliyunAppKey,
            onText = onText,
            onError = onError
        )
        val socket = client.newWebSocket(request, listener)
        listener.awaitOpen()
        RealtimeSpeechSession(socket, taskId)
    }

    suspend fun transcribePcmFile(file: File, onSegment: suspend (TranscriptLine) -> Unit): List<TranscriptLine> =
        withContext(Dispatchers.IO) {
            val settings = secretStore.load()
            if (!settings.hasAliyun) error("请先在设置里填写阿里云语音识别密钥。")
            val token = tokenClient.createToken(settings)
            val taskId = UUID.randomUUID().toString()
            val done = CountDownLatch(1)
            val errorRef = AtomicReference<String?>(null)
            val lines = mutableListOf<TranscriptLine>()
            val listener = SpeechWebSocketListener(
                taskId = taskId,
                appKey = settings.aliyunAppKey,
                onText = { text, finalSegment ->
                    if (finalSegment && text.isNotBlank()) {
                        val line = TranscriptLine(0, lines.size * 10_000L, (lines.size + 1) * 10_000L, "发言人", text, true)
                        lines += line
                    }
                },
                onError = {
                    errorRef.set(it)
                    done.countDown()
                },
                onCompleted = { done.countDown() }
            )
            val request = Request.Builder()
                .url("wss://nls-gateway-${settings.aliyunRegion.ifBlank { "cn-shanghai" }}.aliyuncs.com/ws/v1?token=$token")
                .addHeader("X-NLS-Token", token)
                .build()
            val socket = client.newWebSocket(request, listener)
            listener.awaitOpen()
            file.inputStream().use { input ->
                val buffer = ByteArray(6400)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    socket.send(buffer.copyOf(read).toByteString())
                    Thread.sleep(180)
                }
            }
            socket.send(stopCommand(taskId).toString())
            done.await(90, TimeUnit.SECONDS)
            socket.close(1000, "completed")
            errorRef.get()?.let { error(it) }
            lines.forEach { onSegment(it) }
            lines
        }

    class RealtimeSpeechSession(private val socket: WebSocket, private val taskId: String) {
        fun sendPcm(bytes: ByteArray, size: Int) {
            if (size > 0) socket.send(bytes.copyOf(size).toByteString())
        }

        fun close() {
            socket.send(stopCommand(taskId).toString())
            socket.close(1000, "stop")
        }
    }

    private class SpeechWebSocketListener(
        private val taskId: String,
        private val appKey: String,
        private val onText: (String, Boolean) -> Unit,
        private val onError: (String) -> Unit,
        private val onCompleted: () -> Unit = {}
    ) : WebSocketListener() {
        private val opened = CountDownLatch(1)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(startCommand(taskId, appKey).toString())
            opened.countDown()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            val header = json.optJSONObject("header")
            val name = header?.optString("name").orEmpty()
            val status = header?.optInt("status", 20000000) ?: 20000000
            val payload = json.optJSONObject("payload")
            val result = payload?.optString("result").orEmpty()
            when {
                status != 20000000 -> onError(header?.optString("status_text").orEmpty().ifBlank { "阿里云识别失败：$text" })
                name == "TranscriptionResultChanged" && result.isNotBlank() -> onText(result, false)
                name == "SentenceEnd" && result.isNotBlank() -> onText(result, true)
                name == "TranscriptionCompleted" -> onCompleted()
                name == "TaskFailed" -> onError(payload?.optString("status_text").orEmpty().ifBlank { "阿里云识别任务失败" })
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onError(t.message ?: "阿里云 WebSocket 连接失败")
            opened.countDown()
        }

        fun awaitOpen() {
            if (!opened.await(12, TimeUnit.SECONDS)) error("阿里云 WebSocket 打开超时")
        }
    }

    companion object {
        private fun startCommand(taskId: String, appKey: String): JSONObject {
            return JSONObject()
                .put("header", JSONObject()
                    .put("appkey", appKey)
                    .put("namespace", "SpeechTranscriber")
                    .put("name", "StartTranscription")
                    .put("task_id", taskId)
                    .put("message_id", UUID.randomUUID().toString()))
                .put("payload", JSONObject()
                    .put("format", "pcm")
                    .put("sample_rate", 16000)
                    .put("enable_intermediate_result", true)
                    .put("enable_punctuation_prediction", true)
                    .put("enable_inverse_text_normalization", true))
        }

        private fun stopCommand(taskId: String): JSONObject {
            return JSONObject()
                .put("header", JSONObject()
                    .put("namespace", "SpeechTranscriber")
                    .put("name", "StopTranscription")
                    .put("task_id", taskId)
                    .put("message_id", UUID.randomUUID().toString()))
                .put("payload", JSONObject())
        }
    }
}

