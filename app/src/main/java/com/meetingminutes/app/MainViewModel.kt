package com.meetingminutes.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminutes.app.calendar.CalendarSyncService
import com.meetingminutes.app.data.CloudSettings
import com.meetingminutes.app.data.MeetingCard
import com.meetingminutes.app.data.MeetingDetail
import com.meetingminutes.app.data.MeetingRepository
import com.meetingminutes.app.data.SecretStore
import com.meetingminutes.app.data.TranscriptLine
import com.meetingminutes.app.network.AliyunSpeechTranscriptionService
import com.meetingminutes.app.network.KimiMeetingSummaryService
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
    val insights: List<com.meetingminutes.app.data.InsightReport> = emptyList(),
    val recording: RecordingUiState = RecordingUiState(),
    val settings: CloudSettings = CloudSettings("", "kimi-k2.6", "", "", "", "cn-shanghai"),
    val query: String = "",
    val selectedDay: Long = System.currentTimeMillis(),
    val busy: Boolean = false,
    val message: String = ""
)

class MainViewModel(application: Application) : ViewModel() {
    private val app = application as MeetingMinutesApplication
    private val repository: MeetingRepository = app.appContainer.repository
    private val secretStore: SecretStore = app.appContainer.secretStore
    private val speechService: AliyunSpeechTranscriptionService = app.appContainer.speechService
    private val summaryService: KimiMeetingSummaryService = app.appContainer.summaryService
    private val calendarSyncService = CalendarSyncService(app, repository)

    private val _uiState = MutableStateFlow(AppUiState(settings = secretStore.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val stopRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDemoData()
            refreshAll()
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

    @SuppressLint("MissingPermission")
    fun startRecording(title: String, realtime: Boolean) {
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
                val finalLines = mutableListOf<TranscriptLine>()
                var realtimeSession: AliyunSpeechTranscriptionService.RealtimeSpeechSession? = null
                if (realtime && speechService.hasCredentials()) {
                    realtimeSession = runCatching {
                        speechService.openRealtimeSession(
                            onText = { text, finalSegment ->
                                _uiState.value = _uiState.value.copy(
                                    recording = _uiState.value.recording.copy(
                                        liveTranscript = text,
                                        message = if (finalSegment) "实时转写中" else "正在识别"
                                    )
                                )
                                if (finalSegment && text.isNotBlank()) {
                                    val line = TranscriptLine(
                                        id = 0,
                                        startMs = finalLines.size * 10_000L,
                                        endMs = (finalLines.size + 1) * 10_000L,
                                        speaker = "发言人",
                                        text = text,
                                        finalSegment = true
                                    )
                                    finalLines += line
                                    viewModelScope.launch { repository.saveTranscriptSegment(meetingId, text, line.startMs, line.endMs) }
                                }
                            },
                            onError = { updateMessage("实时转写暂时不可用：$it") }
                        )
                    }.getOrNull()
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
                                realtimeSession?.sendPcm(buffer, read)
                                val duration = System.currentTimeMillis() - startMs
                                _uiState.value = _uiState.value.copy(
                                    recording = _uiState.value.recording.copy(
                                        durationMs = duration,
                                        level = audioLevel(buffer, read)
                                    )
                                )
                            }
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    realtimeSession?.close()
                    runCatching { RecordingForegroundService.stop(app) }
                }

                val endedAt = System.currentTimeMillis()
                processRecording(meetingId, cleanTitle, startedAt, endedAt, audioFile, finalLines)
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

    fun regenerateSummary(meetingId: Long) {
        viewModelScope.launch {
            val detail = repository.getMeetingDetail(meetingId) ?: return@launch
            val transcript = repository.buildTranscriptText(meetingId)
            val bundle = summaryService.summarize(detail.meeting, transcript)
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
        realtimeLines: List<TranscriptLine>
    ) {
        _uiState.value = _uiState.value.copy(recording = _uiState.value.recording.copy(isRecording = false, message = "正在转写和总结"))
        val meetingCard = MeetingCard(meetingId, title, startedAt, endedAt, "processing", "")
        runCatching {
            var transcript = repository.buildTranscriptText(meetingId)
            if (transcript.isBlank() && speechService.hasCredentials()) {
                val lines = speechService.transcribePcmFile(audioFile) { line ->
                    repository.saveTranscriptSegment(meetingId, line.text, line.startMs, line.endMs)
                }
                transcript = lines.joinToString("\n") { it.text }
            }
            if (transcript.isBlank()) {
                transcript = "录音已保存，但还没有可用转写。请在设置里填写阿里云和 Kimi 密钥后重新录制。"
                repository.saveTranscriptSegment(meetingId, transcript, 0, endedAt - startedAt)
            }
            val bundle = summaryService.summarize(meetingCard, transcript)
            repository.saveSummary(meetingId, bundle.summary, bundle.actions)
            audioFile.delete()
            repository.updateMeetingCompleted(meetingId, endedAt, "completed", "", bundle.tags)
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

    private suspend fun refreshAll() {
        refreshMeetings()
        refreshDayMeetings()
        _uiState.value = _uiState.value.copy(insights = repository.listInsights(), settings = secretStore.load())
    }

    private suspend fun refreshMeetings() {
        _uiState.value = _uiState.value.copy(meetings = repository.listMeetings(_uiState.value.query))
    }

    private suspend fun refreshDayMeetings() {
        _uiState.value = _uiState.value.copy(selectedDayMeetings = repository.listMeetingsForDay(_uiState.value.selectedDay))
    }

    private fun updateMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
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

