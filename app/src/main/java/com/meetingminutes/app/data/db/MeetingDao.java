package com.meetingminutes.app.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MeetingDao {
    @Insert
    long insertMeeting(MeetingEntity meeting);

    @Update
    void updateMeeting(MeetingEntity meeting);

    @Insert
    long insertTranscriptSegment(TranscriptSegmentEntity segment);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSummary(MeetingSummaryEntity summary);

    @Insert
    long insertActionItem(ActionItemEntity item);

    @Update
    void updateActionItem(ActionItemEntity item);

    @Insert
    long insertDocumentExport(DocumentExportEntity export);

    @Insert
    long insertInsightReport(InsightReportEntity report);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertCalendarSyncState(CalendarSyncStateEntity state);

    @Query("SELECT * FROM meetings ORDER BY startedAt DESC")
    List<MeetingEntity> listMeetings();

    @Query("SELECT * FROM meetings WHERE title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY startedAt DESC")
    List<MeetingEntity> searchMeetings(String query);

    @Query("SELECT * FROM meetings WHERE startedAt >= :start AND startedAt <= :end ORDER BY startedAt ASC")
    List<MeetingEntity> listMeetingsBetween(long start, long end);

    @Query("SELECT * FROM meetings WHERE id = :meetingId LIMIT 1")
    MeetingEntity getMeeting(long meetingId);

    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startMs ASC, id ASC")
    List<TranscriptSegmentEntity> listTranscript(long meetingId);

    @Query("SELECT * FROM meeting_summaries WHERE meetingId = :meetingId LIMIT 1")
    MeetingSummaryEntity getSummary(long meetingId);

    @Query("SELECT * FROM action_items WHERE meetingId = :meetingId ORDER BY id ASC")
    List<ActionItemEntity> listActions(long meetingId);

    @Query("SELECT * FROM action_items WHERE id = :actionId LIMIT 1")
    ActionItemEntity getActionItem(long actionId);

    @Query("SELECT * FROM insight_reports ORDER BY createdAt DESC LIMIT 20")
    List<InsightReportEntity> listInsightReports();

    @Query("SELECT * FROM calendar_sync_states WHERE meetingId = :meetingId LIMIT 1")
    CalendarSyncStateEntity getCalendarSyncState(long meetingId);

    @Query("SELECT * FROM document_exports WHERE meetingId = :meetingId")
    List<DocumentExportEntity> listDocumentExports(long meetingId);

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    void deleteMeeting(long meetingId);

    @Query("DELETE FROM transcript_segments WHERE meetingId = :meetingId")
    void deleteTranscript(long meetingId);

    @Query("DELETE FROM action_items WHERE meetingId = :meetingId")
    void deleteActionItems(long meetingId);

    @Transaction
    default void replaceTranscript(long meetingId, List<TranscriptSegmentEntity> segments) {
        deleteTranscript(meetingId);
        for (TranscriptSegmentEntity segment : segments) {
            insertTranscriptSegment(segment);
        }
    }

    @Transaction
    default void replaceActionItems(long meetingId, List<ActionItemEntity> items) {
        deleteActionItems(meetingId);
        for (ActionItemEntity item : items) {
            insertActionItem(item);
        }
    }
}
