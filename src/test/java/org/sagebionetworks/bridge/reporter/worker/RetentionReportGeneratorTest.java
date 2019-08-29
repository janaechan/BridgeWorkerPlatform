package org.sagebionetworks.bridge.reporter.worker;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.rest.model.AccountSummary;
import org.sagebionetworks.bridge.rest.model.ActivityEvent;
import org.sagebionetworks.bridge.rest.model.ActivityEventList;
import org.sagebionetworks.bridge.rest.model.RequestInfo;
import org.sagebionetworks.bridge.reporter.helper.BridgeHelper;
import org.sagebionetworks.bridge.reporter.request.ReportType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RetentionReportGeneratorTest {
    
    private static final String STUDY_ID = "test-study";
    private static final String USER_ID_1 = "test-user1";
    private static final String USER_ID_2 = "test-user2";
    private static final String USER_ID_3 = "test-user3";
    private static final DateTime START_DATE = DateTime.parse("2017-06-09T00:00:00.000Z");
    private static final DateTime END_DATE = DateTime.parse("2017-06-09T23:59:59.999Z");
    
    private static final DateTime STUDY_START_DATE_1 = DateTime.parse("2017-06-05T00:00:00.000Z");
    private static final DateTime STUDY_START_DATE_2 = DateTime.parse("2017-06-04T00:00:00.000Z");
    private static final DateTime SIGN_IN_ON = DateTime.parse("2017-06-09T00:00:00.000Z");
    private static final DateTime UPLOADED_ON = DateTime.parse("2017-06-09T00:00:00.000Z");
    
    private static final BridgeReporterRequest REQUEST = new BridgeReporterRequest.Builder()
            .withScheduleType(ReportType.DAILY_RETENTION)
            .withScheduler("test-scheduler")
            .withStartDateTime(START_DATE)
            .withEndDateTime(END_DATE).build();
    
    @Spy
    RetentionReportGenerator generator;
    
    @Mock
    private BridgeHelper bridgeHelper;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        generator = new RetentionReportGenerator();
        generator.setBridgeHelper(bridgeHelper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testStudyStartDate() throws Exception {
        AccountSummary accountSummary = mockAccountSummary(USER_ID_1);
        List<AccountSummary> accountSummaries = new ArrayList<>();
        accountSummaries.add(accountSummary);
        Iterator<AccountSummary> accountSummaryIter = accountSummaries.iterator();
        when(bridgeHelper.getAllAccountSummaries(STUDY_ID)).thenReturn(accountSummaryIter);
        
        mockStudyStateDateEvent(bridgeHelper, accountSummary, STUDY_START_DATE_1);
        
        RequestInfo requestInfo = mockRequestInfo(SIGN_IN_ON, UPLOADED_ON);
        when(bridgeHelper.getRequestInfoForParticipant(STUDY_ID, accountSummary.getId())).thenReturn(requestInfo);
        
        Report report = generator.generate(REQUEST, STUDY_ID);
        assertEquals(report.getStudyId(), STUDY_ID);
        assertEquals(report.getReportId(), "-daily-retention-report");
        assertEquals(report.getDate().toString(), "2017-06-09");
        
        Map<String, List<Integer>> map = (Map<String, List<Integer>>) report.getData();
        assertEquals(map.get("bySignIn").get(4), new Integer(1));
        assertEquals(map.get("byUploadedOn").get(4), new Integer(1)); 
        
        verify(bridgeHelper).getAllAccountSummaries(STUDY_ID);
        verify(bridgeHelper).getActivityEventForParticipant(STUDY_ID, accountSummary.getId());
        verify(bridgeHelper).getRequestInfoForParticipant(STUDY_ID, accountSummary.getId());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testMultipleParticipants() throws Exception {
        AccountSummary accountSummary = mockAccountSummary(USER_ID_1);
        AccountSummary accountSummary2 = mockAccountSummary(USER_ID_2);
        AccountSummary accountSummary3 = mockAccountSummary(USER_ID_3);
        List<AccountSummary> accountSummaries = new ArrayList<>();
        accountSummaries.add(accountSummary);
        accountSummaries.add(accountSummary2);
        accountSummaries.add(accountSummary3);
        Iterator<AccountSummary> accountSummaryIter = accountSummaries.iterator();
        when(bridgeHelper.getAllAccountSummaries(STUDY_ID)).thenReturn(accountSummaryIter);
        
        mockStudyStateDateEvent(bridgeHelper, accountSummary, STUDY_START_DATE_1);
        mockStudyStateDateEvent(bridgeHelper, accountSummary2, STUDY_START_DATE_2);
        mockStudyStateDateEvent(bridgeHelper, accountSummary3, STUDY_START_DATE_2);
        
        RequestInfo requestInfo = mockRequestInfo(SIGN_IN_ON, UPLOADED_ON);
        when(bridgeHelper.getRequestInfoForParticipant(STUDY_ID, accountSummary.getId())).thenReturn(requestInfo);
        when(bridgeHelper.getRequestInfoForParticipant(STUDY_ID, accountSummary2.getId())).thenReturn(requestInfo);
        when(bridgeHelper.getRequestInfoForParticipant(STUDY_ID, accountSummary3.getId())).thenReturn(requestInfo);
        
        Report report = generator.generate(REQUEST, STUDY_ID);
        assertEquals(report.getStudyId(), STUDY_ID);
        assertEquals(report.getReportId(), "-daily-retention-report");
        assertEquals(report.getDate().toString(), "2017-06-09");
        
        Map<String, List<Integer>> map = (Map<String, List<Integer>>) report.getData();
        assertEquals(map.get("bySignIn").get(4), new Integer(1));
        assertEquals(map.get("bySignIn").get(5), new Integer(2));
        assertEquals(map.get("byUploadedOn").get(4), new Integer(1)); 
        assertEquals(map.get("byUploadedOn").get(5), new Integer(2)); 
        
        verify(bridgeHelper).getAllAccountSummaries(STUDY_ID);
        verify(bridgeHelper).getActivityEventForParticipant(STUDY_ID, accountSummary.getId());
        verify(bridgeHelper).getActivityEventForParticipant(STUDY_ID, accountSummary2.getId());
        verify(bridgeHelper).getActivityEventForParticipant(STUDY_ID, accountSummary3.getId());
        
        verify(bridgeHelper).getRequestInfoForParticipant(STUDY_ID, accountSummary.getId());
        verify(bridgeHelper).getRequestInfoForParticipant(STUDY_ID, accountSummary2.getId());
        verify(bridgeHelper).getRequestInfoForParticipant(STUDY_ID, accountSummary3.getId());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testNoStudyStartDate() throws Exception {
        AccountSummary accountSummary = mockAccountSummary(USER_ID_1);
        List<AccountSummary> accountSummaries = new ArrayList<>();
        accountSummaries.add(accountSummary);
        Iterator<AccountSummary> accountSummaryIter = accountSummaries.iterator();
        when(bridgeHelper.getAllAccountSummaries(STUDY_ID)).thenReturn(accountSummaryIter);
        
        List<ActivityEvent> activityEvents = new ArrayList<>();
        ActivityEventList activityEventList = mock(ActivityEventList.class);
        when(activityEventList.getItems()).thenReturn(activityEvents);
        when(bridgeHelper.getActivityEventForParticipant(STUDY_ID, accountSummary.getId())).thenReturn(activityEventList);
        
        Report report = generator.generate(REQUEST, STUDY_ID);
        assertEquals(report.getStudyId(), STUDY_ID);
        assertEquals(report.getReportId(), "-daily-retention-report");
        assertEquals(report.getDate().toString(), "2017-06-09");
        
        Map<String, List<Integer>> map = (Map<String, List<Integer>>) report.getData();
        assertEquals(map.get("bySignIn").size(), 0);
        assertEquals(map.get("byUploadedOn").size(), 0); 
        
        verify(bridgeHelper).getAllAccountSummaries(STUDY_ID);
        verify(bridgeHelper).getActivityEventForParticipant(STUDY_ID, accountSummary.getId());
        verify(bridgeHelper, never()).getRequestInfoForParticipant(anyString(), anyString());
    }
    
    private static AccountSummary mockAccountSummary(String userId) {
        AccountSummary mockAccountSummary = mock(AccountSummary.class);
        when(mockAccountSummary.getId()).thenReturn(userId);
        return mockAccountSummary;
    }
    
    private static RequestInfo mockRequestInfo(DateTime signInOn, DateTime uploadedOn) {
        RequestInfo mockRequestInfo = mock(RequestInfo.class);
        when(mockRequestInfo.getSignedInOn()).thenReturn(signInOn);
        when(mockRequestInfo.getUploadedOn()).thenReturn(uploadedOn);
        return mockRequestInfo;
    }
    
    private static void mockStudyStateDateEvent(BridgeHelper bridgeHelper, AccountSummary accountSummary,
            DateTime studyStateDate) throws IOException {
        ActivityEvent studyStateDateEvent = new ActivityEvent().eventId("study_start_date").timestamp(studyStateDate);
        List<ActivityEvent> activityEvents = new ArrayList<>();
        activityEvents.add(studyStateDateEvent);
        
        ActivityEventList activityEventList = mock(ActivityEventList.class);
        when(activityEventList.getItems()).thenReturn(activityEvents);
        when(bridgeHelper.getActivityEventForParticipant(STUDY_ID, accountSummary.getId())).thenReturn(activityEventList);
    }
}
