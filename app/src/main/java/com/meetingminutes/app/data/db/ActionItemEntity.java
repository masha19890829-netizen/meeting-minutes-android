package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "action_items",
        foreignKeys = @ForeignKey(
                entity = MeetingEntity.class,
                parentColumns = "id",
                childColumns = "meetingId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("meetingId")}
)
public class ActionItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long meetingId;
    public String owner;
    public String content;
    public long dueAt;
    public boolean done;

    public ActionItemEntity(long meetingId, String owner, String content, long dueAt, boolean done) {
        this.meetingId = meetingId;
        this.owner = owner;
        this.content = content;
        this.dueAt = dueAt;
        this.done = done;
    }
}

