package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "meetings")
public class MeetingEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public long startedAt;
    public long endedAt;
    public String status;
    public String audioPath;
    public String source;
    public String tags;

    public MeetingEntity(String title, long startedAt, long endedAt, String status, String audioPath, String source, String tags) {
        this.title = title;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.audioPath = audioPath;
        this.source = source;
        this.tags = tags;
    }
}

