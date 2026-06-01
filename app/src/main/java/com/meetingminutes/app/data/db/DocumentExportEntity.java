package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "document_exports",
        foreignKeys = @ForeignKey(
                entity = MeetingEntity.class,
                parentColumns = "id",
                childColumns = "meetingId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("meetingId")}
)
public class DocumentExportEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long meetingId;
    public String type;
    public String path;
    public long createdAt;

    public DocumentExportEntity(long meetingId, String type, String path, long createdAt) {
        this.meetingId = meetingId;
        this.type = type;
        this.path = path;
        this.createdAt = createdAt;
    }
}

