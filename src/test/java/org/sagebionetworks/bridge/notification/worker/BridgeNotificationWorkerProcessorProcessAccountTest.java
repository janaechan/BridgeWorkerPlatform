package org.sagebionetworks.bridge.notification.worker;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.notification.helper.BridgeHelper;
import org.sagebionetworks.bridge.notification.helper.DynamoHelper;
import org.sagebionetworks.bridge.notification.helper.TemplateVariableHelper;
import org.sagebionetworks.bridge.rest.model.ActivityEvent;
import org.sagebionetworks.bridge.rest.model.Phone;
import org.sagebionetworks.bridge.rest.model.ScheduleStatus;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserConsentHistory;

@SuppressWarnings("JavaReflectionMemberAccess")
public class BridgeNotificationWorkerProcessorProcessAccountTest {
    private static final String CLINICAL_CONSENT_GROUP = "clinical_consent";
    private static final String EVENT_ID_ENROLLMENT = "enrollment";
    private static final String EVENT_ID_BURST_2_START = "custom:activityBurst2Start";
    private static final String EXCLUDED_DATA_GROUP_1 = "excluded-group-1";
    private static final String EXCLUDED_DATA_GROUP_2 = "excluded-group-2";
    private static final String MESSAGE_CUMULATIVE = "message-cumulative";
    private static final String MESSAGE_EARLY = "message-early";
    private static final String MESSAGE_LATE = "message-late";
    private static final String MESSAGE_PRE_BURST_1 = "message-pre-burst-1";
    private static final String MESSAGE_PRE_BURST_2 = "message-pre-burst-2";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2018-04-30T16:41:15.831-0700").getMillis();
    private static final Phone PHONE = new Phone().regionCode("US").number("425-555-5555");
    private static final String RESOLVED_TEMPLATE_VAR_SUFFIX = " w/ resolved variables";
    private static final String PREBURST_GROUP_1 = "preburst-group-1";
    private static final String PREBURST_GROUP_2 = "preburst-group-2";
    private static final String STUDY_ID = "test-study";
    private static final String TASK_ID = "study-burst-task";
    private static final String TEST_NO_CONSENT_GROUP = "test_no_consent";
    private static final String USER_ID = "test-user";

    private static final DateTime ENROLLMENT_TIME = DateTime.parse("2018-04-27T18:51:47.159-0700");
    private static final LocalDate ENROLLMENT_DATE = ENROLLMENT_TIME.toLocalDate();
    private static final LocalDate TEST_DATE = ENROLLMENT_DATE.plusDays(3);
    private static final DateTime STUDY_BURST_2_START_TIME = ENROLLMENT_TIME.plusDays(14);

    private List<ScheduledActivity> activityList;
    private BridgeHelper mockBridgeHelper;
    private UserConsentHistory mockConsent;
    private DynamoHelper mockDynamoHelper;
    private StudyParticipant mockParticipant;
    private TemplateVariableHelper mockTemplateVariableHelper;
    private BridgeNotificationWorkerProcessor processor;
    private WorkerConfig config;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @BeforeMethod
    public void before() throws Exception {
        // Set up mocks
        mockBridgeHelper = mock(BridgeHelper.class);
        mockDynamoHelper = mock(DynamoHelper.class);

        // Mock getActivityEvents - enrollment, burst 2 start, and an unrelated event (which will be ignored)
        ActivityEvent enrollmentEvent = new ActivityEvent().eventId(EVENT_ID_ENROLLMENT).timestamp(ENROLLMENT_TIME);
        ActivityEvent unrelatedEvent = new ActivityEvent().eventId("unrelated-event").timestamp(ENROLLMENT_TIME
                .plusDays(6));
        ActivityEvent studyBurst2StartEvent = new ActivityEvent().eventId(EVENT_ID_BURST_2_START)
                .timestamp(STUDY_BURST_2_START_TIME);
        when(mockBridgeHelper.getActivityEvents(STUDY_ID, USER_ID)).thenReturn(ImmutableList.of(enrollmentEvent,
                unrelatedEvent, studyBurst2StartEvent));

        // Participant needs to be mocked because we can't set ID
        mockParticipant = mock(StudyParticipant.class);
        when(mockParticipant.getId()).thenReturn(USER_ID);
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-data-group"));
        when(mockParticipant.getPhone()).thenReturn(PHONE);
        when(mockParticipant.isPhoneVerified()).thenReturn(true);
        when(mockParticipant.getTimeZone()).thenReturn("-07:00");
        when(mockBridgeHelper.getParticipant(STUDY_ID, USER_ID)).thenReturn(mockParticipant);

        // Similarly, mock consent.
        mockConsent = mock(UserConsentHistory.class);
        when(mockParticipant.getConsentHistories()).thenReturn(ImmutableMap.of(STUDY_ID,
                ImmutableList.of(mockConsent)));

        // Mock getTaskHistory - We only ask for events between the burst start and the current date, but for the sake
        // of simpler tests, return all tasks for burst 1.
        activityList = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            // We only care about scheduledOn and status, and for status, we only care about finished vs not finished.
            ScheduledActivity activity = new ScheduledActivity().status(ScheduleStatus.AVAILABLE);

            // Use reflection to set scheduledOn.
            Field scheduledOnField = ScheduledActivity.class.getDeclaredField("scheduledOn");
            scheduledOnField.setAccessible(true);
            scheduledOnField.set(activity, ENROLLMENT_TIME.plusDays(i));

            activityList.add(activity);
        }
        when(mockBridgeHelper.getTaskHistory(eq(STUDY_ID), eq(USER_ID), eq(TASK_ID), any(), any())).thenReturn(
                activityList.iterator());

        // Mock last notification time
        when(mockDynamoHelper.getLastNotificationTimeForUser(USER_ID)).thenReturn(null);

        // Make worker config
        List<String> missedCumulativeMessageList = ImmutableList.of(MESSAGE_CUMULATIVE);
        List<String> missedEarlyMessageList = ImmutableList.of(MESSAGE_EARLY);
        List<String> missedLateMessageList = ImmutableList.of(MESSAGE_LATE);
        Map<String, List<String>> preburstMessageMap = ImmutableMap.of(
                PREBURST_GROUP_1, ImmutableList.of(MESSAGE_PRE_BURST_1),
                PREBURST_GROUP_2, ImmutableList.of(MESSAGE_PRE_BURST_2));

        config = new WorkerConfig();
        config.setBurstDurationDays(9);
        config.setBurstStartEventIdSet(ImmutableSet.of(EVENT_ID_ENROLLMENT, EVENT_ID_BURST_2_START));
        config.setBurstTaskId(TASK_ID);
        config.setEarlyLateCutoffDays(5);
        config.setExcludedDataGroupSet(ImmutableSet.of(EXCLUDED_DATA_GROUP_1, EXCLUDED_DATA_GROUP_2));
        config.setMissedCumulativeActivitiesMessagesList(missedCumulativeMessageList);
        config.setMissedEarlyActivitiesMessagesList(missedEarlyMessageList);
        config.setMissedLaterActivitiesMessagesList(missedLateMessageList);
        config.setNotificationBlackoutDaysFromStart(3);
        config.setNotificationBlackoutDaysFromEnd(1);
        config.setNumActivitiesToCompleteBurst(6);
        config.setNumMissedConsecutiveDaysToNotify(2);
        config.setNumMissedDaysToNotify(3);
        config.setPreburstMessagesByDataGroup(preburstMessageMap);
        when(mockDynamoHelper.getNotificationConfigForStudy(STUDY_ID)).thenReturn(config);

        // Mock template variable helper. For this test, just append a string. Actually template variable logic is
        // tested somewhere else.
        mockTemplateVariableHelper = mock(TemplateVariableHelper.class);
        when(mockTemplateVariableHelper.resolveTemplateVariables(eq(STUDY_ID), any(), any()))
                .thenAnswer(invocation -> {
                    String message = invocation.getArgumentAt(2, String.class);
                    return message + RESOLVED_TEMPLATE_VAR_SUFFIX;
                });

        // Create processor
        processor = new BridgeNotificationWorkerProcessor();
        processor.setBridgeHelper(mockBridgeHelper);
        processor.setDynamoHelper(mockDynamoHelper);
        processor.setTemplateVariableHelper(mockTemplateVariableHelper);
    }

    @Test
    public void unverifiedPhone() throws Exception {
        when(mockParticipant.isPhoneVerified()).thenReturn(false);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void noTimezone() throws Exception {
        when(mockParticipant.getTimeZone()).thenReturn(null);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void timezoneTooLow() throws Exception {
        when(mockParticipant.getTimeZone()).thenReturn("-12:00");
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void timezoneTooHigh() throws Exception {
        when(mockParticipant.getTimeZone()).thenReturn("+00:00");
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void nullConsentList() throws Exception {
        when(mockParticipant.getConsentHistories()).thenReturn(ImmutableMap.of());
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void emptyConsentList() throws Exception {
        when(mockParticipant.getConsentHistories()).thenReturn(ImmutableMap.of(STUDY_ID, ImmutableList.of()));
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void withdrawnConsent() throws Exception {
        when(mockConsent.getWithdrewOn()).thenReturn(DateTime.parse("2019-07-23T16:47:43.715-0700"));
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void excludedByDataGroup() throws Exception {
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group",
                EXCLUDED_DATA_GROUP_2));
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void beforeBurst() throws Exception {
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.minusDays(2), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void betweenBursts() throws Exception {
        // Next burst starts on enrollment + 14 days
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(12), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void afterBurst() throws Exception {
        // Next burst starts on enrollment + 14 and lasts 9 days. Enrollment + 23 is the first day after the bursts.
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(23), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void blackoutHead() throws Exception {
        // First three days (0, 1, 2) are blackout days
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(2), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void blackoutTail() throws Exception {
        // Last days (8) is a blackout days
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(8), USER_ID);
        verifyNoNotification();
    }

    // branch coverage
    @Test
    public void noActivities() throws Exception {
        activityList.clear();
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void didTodaysActivities() throws Exception {
        // Today is enrollment + 3. Do that activity.
        activityList.get(3).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void didPreviousDaysActivities() throws Exception {
        // Today is enrollment + 3. Do activities for days 0, 1, and 2.
        activityList.get(0).setStatus(ScheduleStatus.FINISHED);
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        activityList.get(2).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    @Test
    public void dontNotifyIfCompletedBurst() throws Exception {
        // Participant did days 0-5, then missed day 6 and 7. Burst is only 6 activities, so don't notify.
        activityList.get(0).setStatus(ScheduleStatus.FINISHED);
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        activityList.get(2).setStatus(ScheduleStatus.FINISHED);
        activityList.get(3).setStatus(ScheduleStatus.FINISHED);
        activityList.get(4).setStatus(ScheduleStatus.FINISHED);
        activityList.get(5).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(7), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void notifiedRecently() throws Exception {
        // For the purposes of this test, set the last notification time to enrollment time. This is very recent, so we
        // don't send another notification.
        UserNotification userNotification = new UserNotification();
        userNotification.setMessage(MESSAGE_EARLY);
        userNotification.setTime(ENROLLMENT_TIME.getMillis());
        userNotification.setType(NotificationType.EARLY);
        userNotification.setUserId(USER_ID);
        when(mockDynamoHelper.getLastNotificationTimeForUser(USER_ID)).thenReturn(userNotification);

        // Execute and verify
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    // branch coverage
    @Test
    public void noActivityEvents() throws Exception {
        when(mockBridgeHelper.getActivityEvents(STUDY_ID, USER_ID)).thenReturn(ImmutableList.of());
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifyNoNotification();
    }

    private void verifyNoNotification() throws Exception {
        verify(mockDynamoHelper, never()).setLastNotificationTimeForUser(any());
        verify(mockBridgeHelper, never()).sendSmsToUser(any(), any(), any());
        verifyZeroInteractions(mockTemplateVariableHelper);
    }

    @Test
    public void didNoActivities() throws Exception {
        // This is the "base case" for our tests. Since the majority of our tests do no send notifications, we wanted
        // the basic configuration to send a notification, to help ensure that our tests are working properly.
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    @Test
    public void clinicalConsentDoesNotRequireConsent() throws Exception {
        when(mockParticipant.getConsentHistories()).thenReturn(ImmutableMap.of());
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group",
                CLINICAL_CONSENT_GROUP));
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    @Test
    public void testNoConsentGroupDoesNotRequireConsent() throws Exception {
        when(mockParticipant.getConsentHistories()).thenReturn(ImmutableMap.of());
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group",
                TEST_NO_CONSENT_GROUP));
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    @Test
    public void missedThreeTotalDays() throws Exception {
        // We did days 1 and 3, but missed 0, 2, and 4
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        activityList.get(3).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(4), USER_ID);
        verifySentNotification(NotificationType.CUMULATIVE, MESSAGE_CUMULATIVE);
    }

    @Test
    public void preburstNotification() throws Exception {
        // Set up data group
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group",
                PREBURST_GROUP_1));

        // Technically, the notification worker will never process a user _before_ they're enrolled. But for the
        // purposes of this test, this represents sending the pre-burst notification a day before the start of burst.
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.minusDays(1), USER_ID);
        verifySentNotification(NotificationType.PRE_BURST, MESSAGE_PRE_BURST_1);
    }

    @Test
    public void preburstNotificationUsesDataGroup() throws Exception {
        // Set up data group
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group",
                PREBURST_GROUP_2));

        // Execute test.
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.minusDays(1), USER_ID);
        verifySentNotification(NotificationType.PRE_BURST, MESSAGE_PRE_BURST_2);
    }

    @Test
    public void preburstNotificationWithNoDataGroups() throws Exception {
        // Set up data group
        when(mockParticipant.getDataGroups()).thenReturn(ImmutableList.of("irrelevant-other-group"));

        // Execute test.
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.minusDays(1), USER_ID);
        verifyNoNotification();
    }

    @Test
    public void preburstDoesNotPreventNormalNotification() throws Exception {
        // Mock preburst notification in the log.
        UserNotification userNotification = new UserNotification();
        userNotification.setMessage(MESSAGE_PRE_BURST_1);
        userNotification.setTime(ENROLLMENT_TIME.minusDays(1).getMillis());
        userNotification.setType(NotificationType.PRE_BURST);
        userNotification.setUserId(USER_ID);
        when(mockDynamoHelper.getLastNotificationTimeForUser(USER_ID)).thenReturn(userNotification);

        // User should still get a notification.
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    @Test
    public void missedTwoConsecutiveDays() throws Exception {
        // Mark day 0 and 1 as finished. We missed days 2 and days 3, and we send a notification.
        activityList.get(0).setStatus(ScheduleStatus.FINISHED);
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    @Test
    public void randomizedMessages() throws Exception {
        // Mock RNG.
        Random mockRng = mock(Random.class);
        when(mockRng.nextInt(3)).thenReturn(1);
        processor.setRng(mockRng);

        // Mock config to have multiple messages to choose from.
        config.setMissedEarlyActivitiesMessagesList(ImmutableList.of("message-0", "message-1", "message-2"));

        // Mark day 0 and 1 as finished. We missed days 2 and days 3, and we send a notification.
        activityList.get(0).setStatus(ScheduleStatus.FINISHED);
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, "message-1");
    }

    @Test
    public void missedLateActivities() throws Exception {
        // Mark days 0-3 as completed. We missed days 4 and 5, and we're processing on day 5.
        activityList.get(0).setStatus(ScheduleStatus.FINISHED);
        activityList.get(1).setStatus(ScheduleStatus.FINISHED);
        activityList.get(2).setStatus(ScheduleStatus.FINISHED);
        activityList.get(3).setStatus(ScheduleStatus.FINISHED);
        processor.processAccountForDate(STUDY_ID, ENROLLMENT_DATE.plusDays(5), USER_ID);
        verifySentNotification(NotificationType.LATE, MESSAGE_LATE);
    }

    // branch coverage
    @Test
    public void notifiedButNotRecently() throws Exception {
        // For branch coverage, this user was last notified 10 days before enrollment. This is long enough ago that we
        // still send a notification
        UserNotification userNotification = new UserNotification();
        userNotification.setMessage(MESSAGE_EARLY);
        userNotification.setTime(ENROLLMENT_TIME.minusDays(10).getMillis());
        userNotification.setType(NotificationType.EARLY);
        userNotification.setUserId(USER_ID);
        when(mockDynamoHelper.getLastNotificationTimeForUser(USER_ID)).thenReturn(userNotification);

        // Execute and verify
        processor.processAccountForDate(STUDY_ID, TEST_DATE, USER_ID);
        verifySentNotification(NotificationType.EARLY, MESSAGE_EARLY);
    }

    private void verifySentNotification(NotificationType type, String message) throws Exception {
        // Verify notification log
        ArgumentCaptor<UserNotification> userNotificationCaptor = ArgumentCaptor.forClass(UserNotification.class);
        verify(mockDynamoHelper).setLastNotificationTimeForUser(userNotificationCaptor.capture());

        UserNotification userNotification = userNotificationCaptor.getValue();
        assertEquals(userNotification.getMessage(), message + RESOLVED_TEMPLATE_VAR_SUFFIX);
        assertEquals(userNotification.getTime(), MOCK_NOW_MILLIS);
        assertEquals(userNotification.getType(), type);
        assertEquals(userNotification.getUserId(), USER_ID);

        // Verify SMS message
        verify(mockBridgeHelper).sendSmsToUser(STUDY_ID, USER_ID, message + RESOLVED_TEMPLATE_VAR_SUFFIX);
    }
}
