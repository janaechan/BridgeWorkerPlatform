package org.sagebionetworks.bridge.notification.helper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.rest.ClientManager;
import org.sagebionetworks.bridge.rest.api.ForWorkersApi;
import org.sagebionetworks.bridge.rest.model.AccountSummary;
import org.sagebionetworks.bridge.rest.model.ActivityEvent;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.SmsTemplate;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;

/** Abstracts away calls to Bridge and wraps the iterator classes. */
@Component("NotificationWorkerBridgeHelper")
public class BridgeHelper {
    private ClientManager clientManager;

    /** Bridge client manager. */
    @Autowired
    public final void setClientManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /** Get all activity events (e.g. enrollment) for the given user in the given study. */
    public List<ActivityEvent> getActivityEvents(String studyId, String userId) throws IOException {
        return clientManager.getClient(ForWorkersApi.class).getActivityEventsForParticipantAndStudy(studyId, userId).execute()
                .body().getItems();
    }

    /**
     * Get an iterator for all account summaries in the given study. Note that since getAllAccountSummaries is a
     * paginated API, the iterator may continue to call the server.
     */
    public Iterator<AccountSummary> getAllAccountSummaries(String studyId) {
        return new AccountSummaryIterator(clientManager, studyId);
    }

    /** Gets a participant for the given user in the given study. */
    public StudyParticipant getParticipant(String studyId, String userId) throws IOException {
        return clientManager.getClient(ForWorkersApi.class).getParticipantByIdForStudy(studyId, userId, true)
                .execute().body();
    }

    /** Gets the given report for the given user in the given study for the given date range (inclusive). */
    public List<ReportData> getParticipantReports(String studyId, String userId, String reportId, LocalDate startDate,
            LocalDate endDate) throws IOException {
        return clientManager.getClient(ForWorkersApi.class).getParticipantReportsForParticipant(studyId, userId,
                reportId, startDate, endDate).execute().body().getItems();
    }

    /**
     * Get the user's survey history for the given user, study, survey GUID, and time range. Note that since
     * getSurveyHistory is a paginated API, the iterator may continue to call the server.
     */
    public Iterator<ScheduledActivity> getSurveyHistory(String studyId, String userId, String surveyGuid,
            DateTime scheduledOnStart, DateTime scheduledOnEnd) {
        return new SurveyHistoryIterator(clientManager, studyId, userId, surveyGuid, scheduledOnStart, scheduledOnEnd);
    }

    /**
     * Get the user's task history for the given user, study, task ID, and time range. Note that since getTaskHistory
     * is a paginated API, the iterator may continue to call the server.
     * */
    public Iterator<ScheduledActivity> getTaskHistory(String studyId, String userId, String taskId,
            DateTime scheduledOnStart, DateTime scheduledOnEnd) {
        return new TaskHistoryIterator(clientManager, studyId, userId, taskId, scheduledOnStart, scheduledOnEnd);
    }

    /** Sends the given message as an SMS to the given user in the given study. */
    public void sendSmsToUser(String studyId, String userId, String message) throws IOException {
        SmsTemplate smsTemplate = new SmsTemplate().message(message);
        clientManager.getClient(ForWorkersApi.class).sendSmsMessageToParticipantForStudy(studyId, userId, smsTemplate)
                .execute();
    }
}
