package com.meetingminutes.app.network

import android.content.Context
import android.content.res.AssetManager
import com.meetingminutes.app.data.ActionItem
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SummaryBundle(
    val summary: MeetingSummary,
    val actions: List<ActionItem>,
    val tags: String
)

class LocalSpeechTranscriptionService(private val context: Context) {
    suspend fun transcribePcmFile(file: File, onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) error("录音文件为空，无法识别。")
        val modelDir = ensureModel()
        val texts = mutableListOf<String>()
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, 16000f).use { recognizer ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(16_000)
                    var bytes = input.read(buffer)
                    while (bytes > 0) {
                        if (recognizer.acceptWaveForm(buffer, bytes)) {
                            extractText(recognizer.result)?.let {
                                texts += it
                                onProgress(it)
                            }
                        }
                        bytes = input.read(buffer)
                    }
                    extractText(recognizer.finalResult)?.let { texts += it }
                }
            }
        }
        texts.joinToString("\n").trim()
    }

    private fun ensureModel(): File {
        val target = File(context.filesDir, MODEL_NAME)
        if (File(target, "conf/model.conf").exists()) return target
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        copyAssetDirectory(context.assets, MODEL_NAME, target)
        return target
    }

    private fun copyAssetDirectory(assets: AssetManager, assetPath: String, target: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.outputStream().use { output ->
                assets.open(assetPath).use { input -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child ->
            copyAssetDirectory(assets, "$assetPath/$child", File(target, child))
        }
    }

    private fun extractText(raw: String): String? {
        val text = runCatching { JSONObject(raw).optString("text") }.getOrDefault("")
        return text.trim().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val MODEL_NAME = "vosk-model-small-cn-0.22"
    }
}

class LocalMeetingSummaryService {
    fun summarize(meeting: MeetingCard, transcript: String): SummaryBundle {
        val clean = transcript.ifBlank { "本次录音没有识别到可用文字。你可以在安静环境下重试，或在会议详情里补充文字后重新整理。" }
        val sentences = clean
            .replace("！", "。")
            .replace("？", "。")
            .split("。", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val brief = sentences.take(3).joinToString("。").ifBlank { clean.take(160) }
        val decisions = pickLines(sentences, listOf("决定", "确认", "确定", "结论", "通过", "同意"))
        val risks = pickLines(sentences, listOf("风险", "问题", "阻塞", "延期", "不确定", "困难"))
        val actions = pickLines(sentences, listOf("待办", "跟进", "负责", "下周", "明天", "完成", "推进"))
            .ifEmpty { listOf("复核会议转写，补充负责人和截止时间。") }
        val questions = pickLines(sentences, listOf("是否", "怎么", "如何", "为什么", "？", "需要"))
        val markdown = buildMarkdown(
            title = meeting.title,
            summary = brief,
            decisions = decisions.joinToString("\n") { "- $it" }.ifBlank { "暂无明确决策。" },
            risks = risks.joinToString("\n") { "- $it" }.ifBlank { "暂无明显风险。" },
            openQuestions = questions.joinToString("\n") { "- $it" }.ifBlank { "暂无后续问题。" },
            actions = actions,
            transcript = clean
        )
        return SummaryBundle(
            summary = MeetingSummary(
                summary = brief,
                decisions = decisions.joinToString("\n") { "- $it" }.ifBlank { "暂无明确决策。" },
                risks = risks.joinToString("\n") { "- $it" }.ifBlank { "暂无明显风险。" },
                openQuestions = questions.joinToString("\n") { "- $it" }.ifBlank { "暂无后续问题。" },
                markdown = markdown
            ),
            actions = actions.map { ActionItem(owner = "我", content = it) },
            tags = "免费版,本地识别,${SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date(meeting.startedAt))}"
        )
    }

    private fun pickLines(lines: List<String>, keywords: List<String>): List<String> {
        return lines.filter { line -> keywords.any { key -> line.contains(key) } }.take(5)
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
        ): String = buildString {
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
