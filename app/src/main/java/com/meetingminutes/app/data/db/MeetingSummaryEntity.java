package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "meeting_summaries",
        foreignKeys = @ForeignKey(
                entity = MeetingEntity.class,
                parentColumns = "id",
                childColumns = "meetingId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = "meetingId", unique = true)}
)
public class MeetingSummaryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long meetingId;
    public String summary;
    public String decisions;
    public String risks;
    public String openQuestions;
    public String markdown;
    public long createdAt;

    public MeetingSummaryEntity(long meetingId, String summary, String decisions, String risks, String openQuestions, String markdown, long createdAt) {
        this.meetingId = meetingId;
        this.summary = summary;
        this.decisions = decisions;
        this.risks = risks;
        this.openQuestions = openQuestions;
        this.markdown = markdown;
        this.createdAt = createdAt;
    }
}

