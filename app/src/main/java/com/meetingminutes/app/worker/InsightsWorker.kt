package com.meetingminutes.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meetingminutes.app.data.MeetingRepository
import com.meetingminutes.app.data.db.AppDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class InsightsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = MeetingRepository(AppDatabase.getInstance(applicationContext).meetingDao())
        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = end
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        val meetings = repository.listMeetingsBetween(start, end)
        if (meetings.isEmpty()) return Result.success()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(end)
        val topics = meetings.joinToString("、") { it.meeting.title }
        val actions = meetings.flatMap { it.actions }.joinToString("\n") { "- ${it.content}" }.ifBlank { "- 暂无明确待办" }
        repository.saveInsight(
            periodType = "daily",
            periodStart = start,
            periodEnd = end,
            title = "$date 会议洞察",
            content = "过去 24 小时共有 ${meetings.size} 场会议：$topics。\n\n待办汇总：\n$actions"
        )
        return Result.success()
    }
}

