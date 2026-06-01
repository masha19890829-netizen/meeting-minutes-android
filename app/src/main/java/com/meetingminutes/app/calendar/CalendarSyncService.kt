package com.meetingminutes.app.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.meetingminutes.app.data.MeetingDetail
import com.meetingminutes.app.data.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

class CalendarSyncService(
    private val context: Context,
    private val repository: MeetingRepository
) {
    suspend fun sync(detail: MeetingDetail): Long = withContext(Dispatchers.IO) {
        val calendarId = firstCalendarId() ?: error("没有找到可写入的系统日历。")
        val description = buildString {
            appendLine(detail.summary?.summary ?: "会议纪要尚未生成。")
            if (detail.actions.isNotEmpty()) {
                appendLine()
                appendLine("待办：")
                detail.actions.forEach { appendLine("- ${it.content}") }
            }
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, detail.meeting.startedAt)
            put(CalendarContract.Events.DTEND, detail.meeting.endedAt.takeIf { it > detail.meeting.startedAt } ?: detail.meeting.startedAt + 30 * 60_000)
            put(CalendarContract.Events.TITLE, detail.meeting.title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("系统日历写入失败。")
        val eventId = ContentUris.parseId(uri)
        repository.saveCalendarSync(detail.meeting.id, eventId, "synced")
        eventId
    }

    suspend fun unsync(detail: MeetingDetail) = withContext(Dispatchers.IO) {
        val eventId = detail.calendarEventId ?: return@withContext
        context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        repository.saveCalendarSync(detail.meeting.id, 0, "removed")
    }

    private fun firstCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null).use { cursor ->
            if (cursor == null) return null
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }
}

