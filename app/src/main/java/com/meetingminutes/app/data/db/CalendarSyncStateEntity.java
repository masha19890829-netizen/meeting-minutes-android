package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "calendar_sync_states",
        foreignKeys = @ForeignKey(
                entity = MeetingEntity.class,
                parentColumns = "id",
                childColumns = "meetingId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = "meetingId", unique = true)}
)
public class CalendarSyncStateEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long meetingId;
    public long calendarEventId;
    public String status;
    public long updatedAt;

    public CalendarSyncStateEntity(long meetingId, long calendarEventId, String status, long updatedAt) {
        this.meetingId = meetingId;
        this.calendarEventId = calendarEventId;
        this.status = status;
        this.updatedAt = updatedAt;
    }
}

