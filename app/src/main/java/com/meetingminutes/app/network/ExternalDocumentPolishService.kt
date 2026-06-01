package com.meetingminutes.app.network

import com.meetingminutes.app.data.ActionItem
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingSummary
import com.meetingminutes.app.data.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ExternalDocumentPolishService(private val secretStore: SecretStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun polish(meeting: MeetingCard, transcript: String): SummaryBundle? = withContext(Dispatchers.IO) {
        val settings = secretStore.load()
        if (!settings.canUseExternalAi || transcript.isBlank()) return@withContext null

        runCatching {
            val content = requestPolishedDocument(
                baseUrl = settings.aiBaseUrl,
                apiKey = settings.aiApiKey,
                model = settings.aiModel,
                title = meeting.title,
                transcript = transcript
            )
            parseSummary(meeting, transcript, content)
        }.getOrNull()
    }

    private fun requestPolishedDocument(
        baseUrl: String,
        apiKey: String,
        model: String,
        title: String,
        transcript: String
    ): String {
        val endpoint = "${baseUrl.trim().trimEnd('/')}/chat/completions"
        val payload = JSONObject()
            .put("model", model.trim())
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是专业会议纪要助手。只输出合法 JSON，不要输出 Markdown 代码块。"
                            )
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", buildPrompt(title, transcript))
                    )
            )

        val body = payload.toString().toRequestBody(JSON)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)
            .addHeader("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) throw IOException("外部模型整理失败：${response.code} $raw")
            val json = JSONObject(raw)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    private fun buildPrompt(title: String, transcript: String): String {
        return """
            请把下面的会议转写整理成一份中文会议文档，返回 JSON，字段必须是：
            summary: 一段 80-180 字摘要
            decisions: 字符串数组，关键决策
            risks: 字符串数组，风险点
            openQuestions: 字符串数组，后续问题
            actions: 对象数组，每项包含 owner 和 content
            tags: 字符串数组，3-6 个标签
            markdown: 完整 Markdown 会议纪要

            会议标题：$title

            会议转写：
            $transcript
        """.trimIndent()
    }

    private fun parseSummary(meeting: MeetingCard, transcript: String, content: String): SummaryBundle {
        val cleaned = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return fallbackSummary(meeting, transcript, content)

        val decisions = json.optJSONArray("decisions").toBullets("暂无明确决策。")
        val risks = json.optJSONArray("risks").toBullets("暂无明显风险。")
        val questions = json.optJSONArray("openQuestions").toBullets("暂无后续问题。")
        val actionObjects = json.optJSONArray("actions")
        val actions = buildList {
            if (actionObjects != null) {
                for (index in 0 until actionObjects.length()) {
                    val item = actionObjects.optJSONObject(index)
                    if (item != null) {
                        val contentText = item.optString("content").trim()
                        if (contentText.isNotBlank()) {
                            add(ActionItem(owner = item.optString("owner", "我").ifBlank { "我" }, content = contentText))
                        }
                    } else {
                        actionObjects.optString(index).trim().takeIf { it.isNotBlank() }?.let {
                            add(ActionItem(owner = "我", content = it))
                        }
                    }
                }
            }
        }
        val markdown = json.optString("markdown").ifBlank {
            LocalMeetingSummaryService.buildMarkdown(
                title = meeting.title,
                summary = json.optString("summary"),
                decisions = decisions,
                risks = risks,
                openQuestions = questions,
                actions = actions.map { it.content },
                transcript = transcript
            )
        }
        val tags = json.optJSONArray("tags")
            ?.toStringList()
            ?.joinToString(",")
            .orEmpty()
            .ifBlank { "外部模型整理,免费本地转写" }

        return SummaryBundle(
            summary = MeetingSummary(
                summary = json.optString("summary").ifBlank { content.take(180) },
                decisions = decisions,
                risks = risks,
                openQuestions = questions,
                markdown = markdown
            ),
            actions = actions,
            tags = tags
        )
    }

    private fun fallbackSummary(meeting: MeetingCard, transcript: String, content: String): SummaryBundle {
        val markdown = LocalMeetingSummaryService.buildMarkdown(
            title = meeting.title,
            summary = content.take(220),
            decisions = "外部模型返回了非 JSON 内容，已按普通文档保存。",
            risks = "暂无明显风险。",
            openQuestions = "暂无后续问题。",
            actions = emptyList(),
            transcript = transcript
        )
        return SummaryBundle(
            summary = MeetingSummary(
                summary = content.take(220),
                decisions = "外部模型返回了非 JSON 内容，已按普通文档保存。",
                risks = "暂无明显风险。",
                openQuestions = "暂无后续问题。",
                markdown = markdown
            ),
            actions = emptyList(),
            tags = "外部模型整理,免费本地转写"
        )
    }

    private fun JSONArray?.toBullets(emptyText: String): String {
        val items = this.toStringList()
        return items.joinToString("\n") { "- $it" }.ifBlank { emptyText }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
