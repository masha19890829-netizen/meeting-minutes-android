package com.meetingminutes.app.data

import com.meetingminutes.app.data.db.ActionItemEntity
import com.meetingminutes.app.data.db.CalendarSyncStateEntity
import com.meetingminutes.app.data.db.DocumentExportEntity
import com.meetingminutes.app.data.db.InsightReportEntity
import com.meetingminutes.app.data.db.MeetingDao
import com.meetingminutes.app.data.db.MeetingEntity
import com.meetingminutes.app.data.db.MeetingSummaryEntity
import com.meetingminutes.app.data.db.TranscriptSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class MeetingRepository(private val dao: MeetingDao) {
    suspend fun ensureDemoData() = withContext(Dispatchers.IO) {
        if (dao.listMeetings().isNotEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val meetingId = dao.insertMeeting(
            MeetingEntity(
                "演示会议：产品周会",
                now - 86_400_000,
                now - 86_000_000,
                "completed",
                "",
                "demo",
                "产品,周会,演示"
            )
        )
        dao.insertTranscriptSegment(
            TranscriptSegmentEntity(
                meetingId,
                0,
                18_000,
                "发言人",
                "这是一条演示转写。真实录音需要先在设置里填写阿里云和 Kimi 的密钥。",
                true
            )
        )
        val markdown = """
            # 演示会议：产品周会

            ## 摘要
            这是一份演示纪要，用来展示会议库会如何把转写整理成结构化文档。

            ## 关键决策
            - 第一版优先跑通录音、转写、总结、归档和导出。

            ## 待办
            - 在设置页填写云服务密钥后进行真实会议测试。
        """.trimIndent()
        dao.insertSummary(
            MeetingSummaryEntity(
                meetingId,
                "第一版优先跑通会议记录闭环，并保留清晰的设置入口。",
                "第一版以私人试用 APK 为目标。",
                "未填写云服务密钥时只能使用演示数据。",
                "真实长会议需要验证阿里云实时识别稳定性。",
                markdown,
                now
            )
        )
        dao.insertActionItem(ActionItemEntity(meetingId, "你", "填写 Kimi 与阿里云密钥后录一次真实会议", 0, false))
        dao.insertInsightReport(
            InsightReportEntity(
                "daily",
                now - 86_400_000,
                now,
                "今日会议洞察",
                "当前只有演示会议。真实数据进入后，这里会自动汇总主题、风险和待办趋势。",
                now
            )
        )
    }

    suspend fun createMeeting(title: String, startedAt: Long, source: String): Long = withContext(Dispatchers.IO) {
        dao.insertMeeting(MeetingEntity(title, startedAt, startedAt, "recording", "", source, ""))
    }

    suspend fun updateMeetingCompleted(meetingId: Long, endedAt: Long, status: String, audioPath: String, tags: String = "") =
        withContext(Dispatchers.IO) {
            val meeting = dao.getMeeting(meetingId) ?: return@withContext
            meeting.endedAt = endedAt
            meeting.status = status
            meeting.audioPath = audioPath
            meeting.tags = tags
            dao.updateMeeting(meeting)
        }

    suspend fun saveTranscriptSegment(meetingId: Long, text: String, startMs: Long, endMs: Long, finalSegment: Boolean = true) =
        withContext(Dispatchers.IO) {
            dao.insertTranscriptSegment(TranscriptSegmentEntity(meetingId, startMs, endMs, "发言人", text, finalSegment))
        }

    suspend fun replaceTranscript(meetingId: Long, lines: List<TranscriptLine>) = withContext(Dispatchers.IO) {
        dao.replaceTranscript(
            meetingId,
            lines.map { TranscriptSegmentEntity(meetingId, it.startMs, it.endMs, it.speaker, it.text, it.finalSegment) }
        )
    }

    suspend fun saveSummary(meetingId: Long, summary: MeetingSummary, actions: List<ActionItem>) = withContext(Dispatchers.IO) {
        dao.insertSummary(
            MeetingSummaryEntity(
                meetingId,
                summary.summary,
                summary.decisions,
                summary.risks,
                summary.openQuestions,
                summary.markdown,
                System.currentTimeMillis()
            )
        )
        dao.replaceActionItems(
            meetingId,
            actions.map { ActionItemEntity(meetingId, it.owner, it.content, it.dueAt, it.done) }
        )
    }

    suspend fun listMeetings(query: String = ""): List<MeetingCard> = withContext(Dispatchers.IO) {
        val rows = if (query.isBlank()) dao.listMeetings() else dao.searchMeetings(query.trim())
        rows.map { it.toCard() }
    }

    suspend fun listMeetingsForDay(dayMillis: Long): List<MeetingCard> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply { timeInMillis = dayMillis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        dao.listMeetingsBetween(start, calendar.timeInMillis - 1).map { it.toCard() }
    }

    suspend fun getMeetingDetail(meetingId: Long): MeetingDetail? = withContext(Dispatchers.IO) {
        val meeting = dao.getMeeting(meetingId) ?: return@withContext null
        MeetingDetail(
            meeting = meeting.toCard(),
            transcript = dao.listTranscript(meetingId).map { it.toLine() },
            summary = dao.getSummary(meetingId)?.toSummary(),
            actions = dao.listActions(meetingId).map { it.toActionItem() },
            calendarEventId = dao.getCalendarSyncState(meetingId).calendarEventIdOrNull()
        )
    }

    suspend fun buildTranscriptText(meetingId: Long): String = withContext(Dispatchers.IO) {
        dao.listTranscript(meetingId).joinToString("\n") { it.text }
    }

    suspend fun saveExport(meetingId: Long, type: String, path: String) = withContext(Dispatchers.IO) {
        dao.insertDocumentExport(DocumentExportEntity(meetingId, type, path, System.currentTimeMillis()))
    }

    suspend fun saveCalendarSync(meetingId: Long, eventId: Long, status: String) = withContext(Dispatchers.IO) {
        dao.insertCalendarSyncState(CalendarSyncStateEntity(meetingId, eventId, status, System.currentTimeMillis()))
    }

    suspend fun listInsights(): List<InsightReport> = withContext(Dispatchers.IO) {
        dao.listInsightReports().map { it.toReport() }
    }

    suspend fun saveInsight(periodType: String, periodStart: Long, periodEnd: Long, title: String, content: String) =
        withContext(Dispatchers.IO) {
            dao.insertInsightReport(
                InsightReportEntity(periodType, periodStart, periodEnd, title, content, System.currentTimeMillis())
            )
        }

    suspend fun listMeetingsBetween(start: Long, end: Long): List<MeetingDetail> = withContext(Dispatchers.IO) {
        dao.listMeetingsBetween(start, end).mapNotNull { meeting ->
            MeetingDetail(
                meeting = meeting.toCard(),
                transcript = dao.listTranscript(meeting.id).map { it.toLine() },
                summary = dao.getSummary(meeting.id)?.toSummary(),
                actions = dao.listActions(meeting.id).map { it.toActionItem() },
                calendarEventId = dao.getCalendarSyncState(meeting.id).calendarEventIdOrNull()
            )
        }
    }
}

