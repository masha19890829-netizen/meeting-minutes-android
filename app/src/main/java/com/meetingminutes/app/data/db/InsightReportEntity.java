package com.meetingminutes.app.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "insight_reports")
public class InsightReportEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String periodType;
    public long periodStart;
    public long periodEnd;
    public String title;
    public String content;
    public long createdAt;

    public InsightReportEntity(String periodType, long periodStart, long periodEnd, String title, String content, long createdAt) {
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }
}

