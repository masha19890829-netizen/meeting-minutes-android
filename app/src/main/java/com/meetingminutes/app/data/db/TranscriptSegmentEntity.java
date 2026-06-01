package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "transcript_segments",
        foreignKeys = @ForeignKey(
                entity = MeetingEntity.class,
                parentColumns = "id",
                childColumns = "meetingId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("meetingId")}
)
public class TranscriptSegmentEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long meetingId;
    public long startMs;
    public long endMs;
    public String speaker;
    public String text;
    public boolean finalSegment;

    public TranscriptSegmentEntity(long meetingId, long startMs, long endMs, String speaker, String text, boolean finalSegment) {
        this.meetingId = meetingId;
        this.startMs = startMs;
        this.endMs = endMs;
        this.speaker = speaker;
        this.text = text;
        this.finalSegment = finalSegment;
    }
}

