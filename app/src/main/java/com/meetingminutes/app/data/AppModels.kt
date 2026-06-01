package com.meetingminutes.app.data

import com.meetingminutes.app.data.db.ActionItemEntity
import com.meetingminutes.app.data.db.CalendarSyncStateEntity
import com.meetingminutes.app.data.db.InsightReportEntity
import com.meetingminutes.app.data.db.MeetingEntity
import com.meetingminutes.app.data.db.MeetingSummaryEntity
import com.meetingminutes.app.data.db.TranscriptSegmentEntity

data class MeetingCard(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val status: String,
    val tags: String
)

data class TranscriptLine(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val speaker: String,
    val text: String,
    val finalSegment: Boolean
)

data class ActionItem(
    val id: Long = 0,
    val owner: String,
    val content: String,
    val dueAt: Long = 0,
    val done: Boolean = false
)

data class MeetingSummary(
    val summary: String,
    val decisions: String,
    val risks: String,
    val openQuestions: String,
    val markdown: String
)

data class MeetingDetail(
    val meeting: MeetingCard,
    val transcript: List<TranscriptLine>,
    val summary: MeetingSummary?,
    val actions: List<ActionItem>,
    val calendarEventId: Long?
)

data class InsightReport(
    val id: Long,
    val periodType: String,
    val periodStart: Long,
    val periodEnd: Long,
    val title: String,
    val content: String,
    val createdAt: Long
)

fun MeetingEntity.toCard() = MeetingCard(
    id = id,
    title = title,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    tags = tags
)

fun TranscriptSegmentEntity.toLine() = TranscriptLine(
    id = id,
    startMs = startMs,
    endMs = endMs,
    speaker = speaker,
    text = text,
    finalSegment = finalSegment
)

fun ActionItemEntity.toActionItem() = ActionItem(
    id = id,
    owner = owner,
    content = content,
    dueAt = dueAt,
    done = done
)

fun MeetingSummaryEntity.toSummary() = MeetingSummary(
    summary = summary,
    decisions = decisions,
    risks = risks,
    openQuestions = openQuestions,
    markdown = markdown
)

fun InsightReportEntity.toReport() = InsightReport(
    id = id,
    periodType = periodType,
    periodStart = periodStart,
    periodEnd = periodEnd,
    title = title,
    content = content,
    createdAt = createdAt
)

fun CalendarSyncStateEntity?.calendarEventIdOrNull(): Long? {
    return if (this == null || calendarEventId <= 0 || status != "synced") null else calendarEventId
}

