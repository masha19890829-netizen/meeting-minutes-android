package com.meetingminutes.app

import android.app.Application
import com.meetingminutes.app.data.MeetingRepository
import com.meetingminutes.app.data.SecretStore
import com.meetingminutes.app.data.db.AppDatabase
import com.meetingminutes.app.export.DocumentExportService
import com.meetingminutes.app.network.OpenAiMeetingService

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.getInstance(application)
    val secretStore = SecretStore(application)
    val repository = MeetingRepository(database.meetingDao())
    val openAiService = OpenAiMeetingService(secretStore)
    val documentExportService = DocumentExportService(application, repository)
}
