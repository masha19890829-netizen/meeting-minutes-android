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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SummaryBundle(
    val summary: MeetingSummary,
    val actions: List<ActionItem>,
    val tags: String
)

class KimiMeetingSummaryService(private val secretStore: SecretStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(meeting: MeetingCard, transcript: String): SummaryBundle = withContext(Dispatchers.IO) {
        val settings = secretStore.load()
        if (!settings.hasKimi || transcript.isBlank()) {
            return@withContext heuristicSummary(meeting, transcript)
        }

        val payload = JSONObject()
            .put("model", settings.kimiModel.ifBlank { "kimi-k2.6" })
            .put("temperature", 0.2)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put("content", "你是专业会议纪要助手。请只输出合法 JSON，不要使用 Markdown 代码块。"))
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", buildPrompt(meeting, transcript)))
            )

        val request = Request.Builder()
            .url("https://api.moonshot.cn/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${settings.kimiApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Kimi 返回 ${response.code}: ${response.body.string()}")
                val body = JSONObject(response.body.string())
                val content = body.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                parseSummaryJson(meeting, transcript, content)
            }
        }.getOrElse { heuristicSummary(meeting, transcript, "Kimi 调用失败：${it.message}") }
    }

    fun heuristicSummary(meeting: MeetingCard, transcript: String, note: String = ""): SummaryBundle {
        val clean = transcript.ifBlank { "尚未获得真实转写内容。请在设置页填写阿里云和 Kimi 密钥后再录制会议。" }
        val brief = clean.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.take(3).joinToString(" ")
            .ifBlank { clean.take(120) }
        val markdown = buildMarkdown(
            title = meeting.title,
            summary = if (note.isBlank()) brief else "$brief\n\n$note",
            decisions = "未识别到明确决策，请在真实转写完成后重新生成。",
            risks = "当前结果可能来自本地兜底摘要，需要云端模型确认。",
            openQuestions = "是否需要补充参会人、项目名和截止日期？",
            actions = listOf("补充云服务密钥并重新生成会议纪要。"),
            transcript = clean
        )
        return SummaryBundle(
            summary = MeetingSummary(
                summary = if (note.isBlank()) brief else "$brief\n$note",
                decisions = "未识别到明确决策。",
                risks = "需要真实云端总结来降低遗漏风险。",
                openQuestions = "是否需要补充参会人、项目名和截止日期？",
                markdown = markdown
            ),
            actions = listOf(ActionItem(owner = "我", content = "补充云服务密钥并重新生成会议纪要")),
            tags = "待完善,本地摘要"
        )
    }

    private fun buildPrompt(meeting: MeetingCard, transcript: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(meeting.startedAt))
        return """
            请把下面会议转写整理成中文会议纪要。

            会议标题：${meeting.title}
            会议时间：$time

            输出 JSON 字段：
            {
              "summary": "150字内摘要",
              "decisions": ["关键决策"],
              "risks": ["风险点"],
              "openQuestions": ["后续问题"],
              "actions": [{"owner":"负责人或我","content":"待办","dueAt":0}],
              "tags": ["标签"],
              "markdown": "完整 Markdown 会议纪要"
            }

            转写内容：
            $transcript
        """.trimIndent()
    }

    private fun parseSummaryJson(meeting: MeetingCard, transcript: String, raw: String): SummaryBundle {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(trimmed)
        val decisions = json.optJSONArray("decisions").toStringList()
        val risks = json.optJSONArray("risks").toStringList()
        val questions = json.optJSONArray("openQuestions").toStringList()
        val actionItems = json.optJSONArray("actions")
        val actions = mutableListOf<ActionItem>()
        if (actionItems != null) {
            for (index in 0 until actionItems.length()) {
                val item = actionItems.optJSONObject(index) ?: continue
                actions += ActionItem(
                    owner = item.optString("owner", "我"),
                    content = item.optString("content", "").ifBlank { "跟进会议待办" },
                    dueAt = item.optLong("dueAt", 0L)
                )
            }
        }
        val tags = json.optJSONArray("tags").toStringList().joinToString(",")
        val markdown = json.optString("markdown").ifBlank {
            buildMarkdown(
                title = meeting.title,
                summary = json.optString("summary"),
                decisions = decisions.joinToString("\n") { "- $it" },
                risks = risks.joinToString("\n") { "- $it" },
                openQuestions = questions.joinToString("\n") { "- $it" },
                actions = actions.map { it.content },
                transcript = transcript
            )
        }
        return SummaryBundle(
            summary = MeetingSummary(
                summary = json.optString("summary"),
                decisions = decisions.joinToString("\n") { "- $it" },
                risks = risks.joinToString("\n") { "- $it" },
                openQuestions = questions.joinToString("\n") { "- $it" },
                markdown = markdown
            ),
            actions = actions.ifEmpty { listOf(ActionItem(owner = "我", content = "复核会议纪要并补充待办负责人")) },
            tags = tags.ifBlank { "会议纪要" }
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let { result += it }
        }
        return result
    }

    companion object {
        fun buildMarkdown(
            title: String,
            summary: String,
            decisions: String,
            risks: String,
            openQuestions: String,
            actions: List<String>,
            transcript: String
        ): String {
            return buildString {
                appendLine("# $title")
                appendLine()
                appendLine("## 摘要")
                appendLine(summary.ifBlank { "暂无摘要。" })
                appendLine()
                appendLine("## 关键决策")
                appendLine(decisions.ifBlank { "暂无明确决策。" })
                appendLine()
                appendLine("## 待办事项")
                if (actions.isEmpty()) appendLine("- 暂无待办。") else actions.forEach { appendLine("- $it") }
                appendLine()
                appendLine("## 风险点")
                appendLine(risks.ifBlank { "暂无风险点。" })
                appendLine()
                appendLine("## 后续问题")
                appendLine(openQuestions.ifBlank { "暂无后续问题。" })
                appendLine()
                appendLine("## 原始转写")
                appendLine(transcript.ifBlank { "暂无转写。" })
            }
        }
    }
}

