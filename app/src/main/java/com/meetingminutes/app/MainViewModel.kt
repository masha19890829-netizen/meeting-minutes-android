package com.meetingminutes.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminutes.app.calendar.CalendarSyncService
import com.meetingminutes.app.data.ActionBoardItem
import com.meetingminutes.app.data.CloudSettings
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingDetail
import com.meetingminutes.app.data.MeetingRepository
import com.meetingminutes.app.data.SecretStore
import com.meetingminutes.app.network.ExternalDocumentPolishService
import com.meetingminutes.app.network.LocalMeetingSummaryService
import com.meetingminutes.app.network.LocalSpeechTranscriptionService
import com.meetingminutes.app.network.SummaryBundle
import com.meetingminutes.app.network.UpdateInfo
import com.meetingminutes.app.recording.RecordingForegroundService
import com.meetingminutes.app.recording.RecordingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

data class AppUiState(
    val meetings: List<MeetingCard> = emptyList(),
    val selectedMeeting: MeetingDetail? = null,
    val selectedDayMeetings: List<MeetingCard> = emptyList(),
    val actionBoard: List<ActionBoardItem> = emptyList(),
    val insights: List<com.meetingminutes.app.data.InsightReport> = emptyList(),
    val recording: RecordingUiState = RecordingUiState(),
    val settings: CloudSettings = CloudSettings(),
    val query: String = "",
    val selectedDay: Long = System.currentTimeMillis(),
    val busy: Boolean = false,
    val message: String = "",
    val updateInfo: UpdateInfo? = null
)

class MainViewModel(application: Application) : ViewModel() {
    private val app = application as MeetingMinutesApplication
    private val repository: MeetingRepository = app.appContainer.repository
    private val secretStore: SecretStore = app.appContainer.secretStore
    private val speechService: LocalSpeechTranscriptionService = app.appContainer.speechService
    private val summaryService: LocalMeetingSummaryService = app.appContainer.summaryService
    private val documentPolishService: ExternalDocumentPolishService = app.appContainer.documentPolishService
    private val updateCheckService = app.appContainer.updateCheckService
    private val calendarSyncService = CalendarSyncService(app, repository)

    private val _uiState = MutableStateFlow(AppUiState(settings = secretStore.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val stopRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDemoData()
            refreshAll()
            checkForUpdates(silent = true)
        }
    }

    fun setQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        viewModelScope.launch { refreshMeetings() }
    }

    fun selectDay(dayMillis: Long) {
        _uiState.value = _uiState.value.copy(selectedDay = dayMillis)
        viewModelScope.launch { refreshDayMeetings() }
    }

    fun selectMeeting(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedMeeting = repository.getMeetingDetail(id))
        }
    }

    fun saveSettings(settings: CloudSettings) {
        secretStore.save(settings)
        _uiState.value = _uiState.value.copy(settings = secretStore.load(), message = "设置已保存")
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) updateMessage("正在检查更新")
            val update = updateCheckService.check(secretStore.load(), BuildConfig.VERSION_CODE)
            when {
                update != null -> _uiState.value = _uiState.value.copy(updateInfo = update, message = "发现新版本 ${update.versionName}")
                !silent -> updateMessage("当前已是最新版本")
            }
        }
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(title: String, realtime: Boolean, agenda: String = "") {
        if (_uiState.value.recording.isRecording) return
        recordingJob = viewModelScope.launch {
            val cleanTitle = title.ifBlank { "会议 ${System.currentTimeMillis()}" }
            val startedAt = System.currentTimeMillis()
            val meetingId = repository.createMeeting(cleanTitle, startedAt, "recording")
            stopRecording.set(false)
            _uiState.value = _uiState.value.copy(
                recording = RecordingUiState(
                    isRecording = true,
                    realtime = realtime,
                    title = cleanTitle,
                    message = "正在录音",
                    currentMeetingId = meetingId
                ),
                message = "录音已开始"
            )
            runCatching { RecordingForegroundService.start(app) }
            withContext(Dispatchers.IO) {
                val audioFile = File(app.cacheDir, "recordings/$meetingId.pcm").apply {
                    parentFile?.mkdirs()
                    if (exists()) delete()
                    createNewFile()
                }
                val bufferSize = max(
                    AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                    SAMPLE_RATE
                )
                val buffer = ByteArray(bufferSize)
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
                val startMs = System.currentTimeMillis()
                try {
                    recorder.startRecording()
                    audioFile.outputStream().use { output ->
                        while (!stopRecording.get()) {
                            val read = recorder.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                val duration = System.currentTimeMillis() - startMs
                                _uiState.value = _uiState.value.copy(
                                    recording = _uiState.value.recording.copy(
                                        durationMs = duration,
                                        level = audioLevel(buffer, read),
                                        liveTranscript = "免费版会在停止录音后离线转写，无需充值或 API Key。"
                                    )
                                )
                            }
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    runCatching { RecordingForegroundService.stop(app) }
                }

                val endedAt = System.currentTimeMillis()
                processRecording(meetingId, cleanTitle, startedAt, endedAt, audioFile, agenda)
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.recording.isRecording) return
        _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(message = "正在收尾"))
        stopRecording.set(true)
    }

    fun exportMarkdown(context: Context, detail: MeetingDetail) {
        viewModelScope.launch {
            val file = app.appContainer.documentExportService.exportMarkdown(detail)
            context.startActivity(Intent.createChooser(app.appContainer.documentExportService.shareIntent(file), "分享 Markdown"))
        }
    }

    fun exportPdf(context: Context, detail: MeetingDetail) {
        viewModelScope.launch {
            val file = app.appContainer.documentExportService.exportPdf(detail)
            context.startActivity(Intent.createChooser(app.appContainer.documentExportService.shareIntent(file), "分享 PDF"))
        }
    }

    fun copyMarkdown(context: Context, detail: MeetingDetail) {
        copyText(context, "会议纪要", app.appContainer.documentExportService.markdownText(detail))
        updateMessage("会议纪要已复制")
    }

    fun copyActions(context: Context, detail: MeetingDetail) {
        val text = detail.actions
            .filterNot { it.done }
            .joinToString("\n") { "- ${it.owner}：${it.content}" }
            .ifBlank { "暂无未完成待办" }
        copyText(context, "会议待办", "${detail.meeting.title}\n$text")
        updateMessage("会议待办已复制")
    }

    fun copyFreeAiPrompt(context: Context, detail: MeetingDetail) {
        copyText(context, "免费大模型整理提示词", freeAiPrompt(detail))
        updateMessage("AI 整理提示词已复制")
    }

    fun shareFreeAiPrompt(context: Context, detail: MeetingDetail) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, freeAiPrompt(detail))
        context.startActivity(Intent.createChooser(intent, "发送给免费大模型"))
    }

    fun openKimi(context: Context, detail: MeetingDetail) {
        copyFreeAiPrompt(context, detail)
        openUrl(context, KIMI_URL)
    }

    fun openDoubao(context: Context, detail: MeetingDetail) {
        copyFreeAiPrompt(context, detail)
        openUrl(context, DOUBAO_URL)
    }

    fun syncCalendar() {
        val detail = _uiState.value.selectedMeeting ?: return
        viewModelScope.launch {
            runCatching {
                calendarSyncService.sync(detail)
                repository.getMeetingDetail(detail.meeting.id)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(selectedMeeting = it, message = "已同步到系统日历")
            }.onFailure {
                updateMessage(it.message ?: "系统日历同步失败")
            }
        }
    }

    fun unsyncCalendar() {
        val detail = _uiState.value.selectedMeeting ?: return
        viewModelScope.launch {
            runCatching {
                calendarSyncService.unsync(detail)
                repository.getMeetingDetail(detail.meeting.id)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(selectedMeeting = it, message = "已取消系统日历同步")
            }
        }
    }

    fun deleteMeeting(meetingId: Long) {
        viewModelScope.launch {
            val detail = repository.getMeetingDetail(meetingId)
            if (detail?.calendarEventId != null) {
                runCatching { calendarSyncService.unsync(detail) }
            }
            repository.deleteMeeting(meetingId)
            _uiState.value = _uiState.value.copy(selectedMeeting = null, message = "会议文档已删除")
            refreshAll()
        }
    }

    fun toggleActionDone(actionId: Long, done: Boolean) {
        viewModelScope.launch {
            repository.setActionDone(actionId, done)
            val selectedId = _uiState.value.selectedMeeting?.meeting?.id
            refreshActionBoard()
            if (selectedId != null) selectMeeting(selectedId)
        }
    }

    fun regenerateSummary(meetingId: Long) {
        viewModelScope.launch {
            val detail = repository.getMeetingDetail(meetingId) ?: return@launch
            val transcript = if (detail.meeting.audioPath.isNotBlank() && detail.meeting.status == "needs_retry") {
                retryTranscription(detail)
            } else {
                repository.buildTranscriptText(meetingId)
            }
            updateMessage("正在整理会议文档")
            val bundle = createSummary(detail.meeting, transcript)
            repository.saveSummary(meetingId, bundle.summary, bundle.actions)
            repository.updateMeetingCompleted(meetingId, detail.meeting.endedAt, "completed", "", bundle.tags)
            refreshAll()
            selectMeeting(meetingId)
            updateMessage("会议纪要已重新生成")
        }
    }

    private suspend fun processRecording(
        meetingId: Long,
        title: String,
        startedAt: Long,
        endedAt: Long,
        audioFile: File,
        agenda: String
    ) {
        _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(isRecording = false, message = "正在转写和总结"))
        val meetingCard = MeetingCard(meetingId, title, startedAt, endedAt, "processing", "", "")
        runCatching {
            var transcript = repository.buildTranscriptText(meetingId)
            if (transcript.isBlank()) {
                _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(message = "正在本地离线转写"))
                val lines = speechService.transcribePcmLines(audioFile) { partial ->
                    _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(liveTranscript = partial))
                }
                if (lines.isNotEmpty()) {
                    repository.replaceTranscript(meetingId, lines)
                    transcript = lines.joinToString("\n") { "${it.speaker}：${it.text}" }
                }
            }
            if (agenda.isNotBlank()) {
                repository.saveTranscriptSegment(
                    meetingId = meetingId,
                    text = agendaForTranscript(agenda),
                    startMs = -1,
                    endMs = -1,
                    speaker = "会前准备"
                )
                transcript = repository.buildTranscriptText(meetingId).ifBlank { transcript }
            }
            if (transcript.isBlank()) {
                transcript = "录音已保存，但本地识别没有得到可用文字。可以在会议详情里重试，或在更安静的环境下重新录制。"
                repository.saveTranscriptSegment(meetingId, transcript, 0, endedAt - startedAt)
            }
            _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(message = "正在整理会议文档"))
            val bundle = createSummary(meetingCard, transcript)
            repository.saveSummary(meetingId, bundle.summary, bundle.actions)
            val keepAudio = secretStore.load().keepAudioAfterSuccess
            if (!keepAudio) audioFile.delete()
            repository.updateMeetingCompleted(meetingId, endedAt, "completed", if (keepAudio) audioFile.absolutePath else "", bundle.tags)
            _uiState.value = _uiState.value.copy(
                recording = RecordingUiState(message = "会议已归档"),
                message = "会议已完成归档"
            )
        }.onFailure { error ->
            repository.updateMeetingCompleted(meetingId, endedAt, "needs_retry", audioFile.absolutePath, "")
            _uiState.value = _uiState.value.copy(
                recording = RecordingUiState(message = "处理失败，可在会议详情中重试"),
                message = error.message ?: "处理失败"
            )
        }
        refreshAll()
        selectMeeting(meetingId)
    }

    private suspend fun createSummary(meeting: MeetingCard, transcript: String): SummaryBundle {
        return documentPolishService.polish(meeting, transcript)
            ?: summaryService.summarize(meeting, transcript)
    }

    private suspend fun retryTranscription(detail: MeetingDetail): String {
        val audioFile = File(detail.meeting.audioPath)
        if (!audioFile.exists()) return repository.buildTranscriptText(detail.meeting.id)
        updateMessage("正在重新本地识别")
        val lines = speechService.transcribePcmLines(audioFile)
        val text = lines.joinToString("\n") { "${it.speaker}：${it.text}" }
        if (lines.isNotEmpty()) {
            repository.replaceTranscript(detail.meeting.id, lines)
        }
        return text.ifBlank { repository.buildTranscriptText(detail.meeting.id) }
    }

    private suspend fun refreshAll() {
        refreshMeetings()
        refreshDayMeetings()
        refreshActionBoard()
        _uiState.value = _uiState.value.copy(insights = repository.listInsights(), settings = secretStore.load())
    }

    private suspend fun refreshMeetings() {
        _uiState.value = _uiState.value.copy(meetings = repository.listMeetings(_uiState.value.query))
    }

    private suspend fun refreshDayMeetings() {
        _uiState.value = _uiState.value.copy(selectedDayMeetings = repository.listMeetingsForDay(_uiState.value.selectedDay))
    }

    private suspend fun refreshActionBoard() {
        _uiState.value = _uiState.value.copy(actionBoard = repository.listActionBoard())
    }

    private fun updateMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    private fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            updateMessage("无法打开浏览器，提示词已复制")
        }
    }

    private fun freeAiPrompt(detail: MeetingDetail): String {
        val transcript = detail.transcript.joinToString("\n") { "${it.speaker}：${it.text}" }
        val actions = detail.actions.joinToString("\n") { "- ${it.owner}：${it.content}" }.ifBlank { "暂无" }
        return """
            请你作为专业会议纪要助手，把下面这场会议整理成一份可直接发给团队的中文文档。

            输出格式请包含：
            1. 会议摘要
            2. 关键结论/决策
            3. 待办事项表格（负责人、事项、截止时间、状态）
            4. 风险点
            5. 后续需要确认的问题
            6. 可直接复制的 Markdown 版本

            会议标题：${detail.meeting.title}
            会议时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(detail.meeting.startedAt))}

            APP 已提取的待办：
            $actions

            会议转写：
            $transcript
        """.trimIndent()
    }

    private fun agendaForTranscript(agenda: String): String {
        return "会前目标/议程：\n${agenda.trim()}"
    }

    private fun audioLevel(buffer: ByteArray, size: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < size) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            peak = max(peak, abs(sample))
            i += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val KIMI_URL = "https://www.kimi.com/"
        private const val DOUBAO_URL = "https://www.doubao.com/"

        fun requiredRecordingPermissions(): Array<String> {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            return permissions.toTypedArray()
        }

        fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
            return permissions.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        }
    }
}
