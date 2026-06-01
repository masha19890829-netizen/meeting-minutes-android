package com.meetingminutes.app.network

import android.content.Context
import android.content.res.AssetManager
import com.meetingminutes.app.data.ActionItem
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingSummary
import com.meetingminutes.app.data.TranscriptLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SummaryBundle(
    val summary: MeetingSummary,
    val actions: List<ActionItem>,
    val tags: String
)

class LocalSpeechTranscriptionService(private val context: Context) {
    suspend fun transcribePcmFile(file: File, onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        transcribePcmLines(file, onProgress).joinToString("\n") { "${it.speaker}：${it.text}" }.trim()
    }

    suspend fun transcribePcmLines(file: File, onProgress: (String) -> Unit = {}): List<TranscriptLine> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) error("录音文件为空，无法识别。")
        val modelDir = ensureModel()
        val bytes = file.readBytes()
        val speechSlices = findSpeechSlices(bytes)
        val rawSegments = mutableListOf<RawTranscriptSegment>()
        Model(modelDir.absolutePath).use { model ->
            speechSlices.forEach { slice ->
                val text = recognizeBytes(model, bytes, slice.startByte, slice.endByte)
                if (text.isNotBlank()) {
                    rawSegments += RawTranscriptSegment(
                        startMs = byteToMs(slice.startByte),
                        endMs = byteToMs(slice.endByte),
                        text = text,
                        feature = extractVoiceFeature(bytes, slice.startByte, slice.endByte)
                    )
                }
            }
        }
        assignSpeakers(rawSegments).also { lines ->
            lines.forEach { onProgress("${it.speaker}：${it.text}") }
        }
    }

    private fun recognizeBytes(model: Model, bytes: ByteArray, startByte: Int, endByte: Int): String {
        val texts = mutableListOf<String>()
        Recognizer(model, SAMPLE_RATE.toFloat()).use { recognizer ->
            var offset = startByte.coerceAtLeast(0)
            val safeEnd = endByte.coerceAtMost(bytes.size)
            while (offset < safeEnd) {
                val length = min(RECOGNIZER_BUFFER_BYTES, safeEnd - offset)
                val chunk = bytes.copyOfRange(offset, offset + length)
                if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                    extractText(recognizer.result)?.let { texts += it }
                }
                offset += length
            }
            extractText(recognizer.finalResult)?.let { texts += it }
        }
        return texts.joinToString(" ").trim()
    }

    private fun findSpeechSlices(bytes: ByteArray): List<AudioSlice> {
        if (bytes.size <= FRAME_BYTES) return listOf(AudioSlice(0, bytes.size))
        val frames = buildList {
            var start = 0
            while (start + BYTES_PER_SAMPLE <= bytes.size) {
                val end = min(start + FRAME_BYTES, bytes.size)
                add(FrameStats(start, end, rms(bytes, start, end)))
                start = end
            }
        }
        val sortedRms = frames.map { it.rms }.sorted()
        val noise = sortedRms[(sortedRms.lastIndex * 0.25).toInt().coerceIn(0, sortedRms.lastIndex)]
        val voice = sortedRms[(sortedRms.lastIndex * 0.80).toInt().coerceIn(0, sortedRms.lastIndex)]
        val threshold = max(450.0, noise + (voice - noise) * 0.35)
        val slices = mutableListOf<AudioSlice>()
        var activeStart: Int? = null
        var lastVoiceFrame = 0

        frames.forEachIndexed { index, frame ->
            if (frame.rms >= threshold) {
                if (activeStart == null) activeStart = index
                lastVoiceFrame = index
            } else if (activeStart != null && index - lastVoiceFrame > MAX_SILENCE_FRAMES) {
                addSlice(frames, activeStart ?: 0, lastVoiceFrame, slices)
                activeStart = null
            }
        }
        activeStart?.let { addSlice(frames, it, lastVoiceFrame, slices) }
        return mergeCloseSlices(slices).ifEmpty { listOf(AudioSlice(0, bytes.size)) }
    }

    private fun addSlice(frames: List<FrameStats>, startFrame: Int, endFrame: Int, slices: MutableList<AudioSlice>) {
        if (endFrame - startFrame + 1 < MIN_SPEECH_FRAMES) return
        val paddedStart = max(0, startFrame - PAD_FRAMES)
        val paddedEnd = min(frames.lastIndex, endFrame + PAD_FRAMES)
        slices += AudioSlice(frames[paddedStart].startByte, frames[paddedEnd].endByte)
    }

    private fun mergeCloseSlices(slices: List<AudioSlice>): List<AudioSlice> {
        if (slices.isEmpty()) return emptyList()
        val merged = mutableListOf<AudioSlice>()
        var current = slices.first()
        slices.drop(1).forEach { next ->
            if (next.startByte - current.endByte <= MERGE_GAP_BYTES) {
                current = AudioSlice(current.startByte, next.endByte)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun assignSpeakers(segments: List<RawTranscriptSegment>): List<TranscriptLine> {
        if (segments.isEmpty()) return emptyList()
        if (segments.size == 1) {
            val only = segments.first()
            return listOf(TranscriptLine(0, only.startMs, only.endMs, "角色A", only.text, true))
        }
        val points = normalizeFeatures(segments.map { it.feature })
        var centerA = points.first()
        var centerB = points.maxBy { distance(centerA, it) }
        var groups = IntArray(points.size)
        repeat(8) {
            groups = IntArray(points.size) { index ->
                if (distance(points[index], centerA) <= distance(points[index], centerB)) 0 else 1
            }
            centerA = average(points, groups, 0) ?: centerA
            centerB = average(points, groups, 1) ?: centerB
        }
        if (distance(centerA, centerB) < 0.28 || groups.distinct().size == 1) {
            return segments.map { TranscriptLine(0, it.startMs, it.endMs, "角色A", it.text, true) }
        }
        val firstGroup = groups.first()
        return segments.mapIndexed { index, segment ->
            val speaker = if (groups[index] == firstGroup) "角色A" else "角色B"
            TranscriptLine(0, segment.startMs, segment.endMs, speaker, segment.text, true)
        }
    }

    private fun normalizeFeatures(features: List<VoiceFeature>): List<DoubleArray> {
        val values = features.map {
            doubleArrayOf(ln(it.energy + 1.0), it.zeroCrossingRate, it.brightness)
        }
        val mins = DoubleArray(3) { axis -> values.minOf { it[axis] } }
        val maxes = DoubleArray(3) { axis -> values.maxOf { it[axis] } }
        return values.map { point ->
            DoubleArray(3) { axis ->
                val range = maxes[axis] - mins[axis]
                if (range <= 0.0001) 0.0 else (point[axis] - mins[axis]) / range
            }
        }
    }

    private fun average(points: List<DoubleArray>, groups: IntArray, group: Int): DoubleArray? {
        val selected = points.filterIndexed { index, _ -> groups[index] == group }
        if (selected.isEmpty()) return null
        return DoubleArray(3) { axis -> selected.sumOf { it[axis] } / selected.size }
    }

    private fun distance(left: DoubleArray, right: DoubleArray): Double {
        var sum = 0.0
        for (index in left.indices) {
            val diff = left[index] - right[index]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun extractVoiceFeature(bytes: ByteArray, startByte: Int, endByte: Int): VoiceFeature {
        var squareSum = 0.0
        var absSum = 0.0
        var diffSum = 0.0
        var crossings = 0
        var samples = 0
        var previous = 0
        var hasPrevious = false
        var index = startByte.coerceAtLeast(0)
        val safeEnd = endByte.coerceAtMost(bytes.size - 1)
        while (index + 1 <= safeEnd) {
            val sample = sampleAt(bytes, index)
            squareSum += sample.toDouble() * sample
            absSum += abs(sample)
            if (hasPrevious) {
                if ((sample >= 0) != (previous >= 0)) crossings += 1
                diffSum += abs(sample - previous)
            }
            previous = sample
            hasPrevious = true
            samples += 1
            index += BYTES_PER_SAMPLE
        }
        if (samples == 0) return VoiceFeature(0.0, 0.0, 0.0)
        val energy = sqrt(squareSum / samples)
        val zcr = crossings.toDouble() / samples
        val brightness = diffSum / max(1.0, absSum)
        return VoiceFeature(energy, zcr, brightness)
    }

    private fun rms(bytes: ByteArray, startByte: Int, endByte: Int): Double {
        var sum = 0.0
        var samples = 0
        var index = startByte
        val safeEnd = endByte.coerceAtMost(bytes.size - 1)
        while (index + 1 <= safeEnd) {
            val sample = sampleAt(bytes, index)
            sum += sample.toDouble() * sample
            samples += 1
            index += BYTES_PER_SAMPLE
        }
        return if (samples == 0) 0.0 else sqrt(sum / samples)
    }

    private fun sampleAt(bytes: ByteArray, index: Int): Int {
        val low = bytes[index].toInt() and 0xff
        val high = bytes[index + 1].toInt()
        return (low or (high shl 8)).toShort().toInt()
    }

    private fun byteToMs(byteOffset: Int): Long {
        return byteOffset * 1000L / (SAMPLE_RATE * BYTES_PER_SAMPLE)
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
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_MS = 30
        private const val FRAME_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * FRAME_MS / 1000
        private const val MIN_SPEECH_FRAMES = 20
        private const val MAX_SILENCE_FRAMES = 24
        private const val PAD_FRAMES = 5
        private const val RECOGNIZER_BUFFER_BYTES = 16_000
        private const val MERGE_GAP_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE
    }
}

private data class FrameStats(val startByte: Int, val endByte: Int, val rms: Double)
private data class AudioSlice(val startByte: Int, val endByte: Int)
private data class VoiceFeature(val energy: Double, val zeroCrossingRate: Double, val brightness: Double)
private data class RawTranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val feature: VoiceFeature
)

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
