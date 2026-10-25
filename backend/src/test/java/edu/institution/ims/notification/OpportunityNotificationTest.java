package edu.institution.ims.notification;

import edu.institution.ims.opportunity.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpportunityNotificationTest {
    private static final Instant PUBLISHED = Instant.parse("2026-10-25T07:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NotificationService service = spy(new NotificationService(jdbc, Clock.systemUTC()));

    private Opportunity opportunity(OpportunityStatus status, LocalDate deadline, String description) {
        var opportunity = mock(Opportunity.class);
        when(opportunity.getId()).thenReturn(42L);
        when(opportunity.getStatus()).thenReturn(status);
        when(opportunity.getPublishedAt()).thenReturn(PUBLISHED);
        when(opportunity.getApplicationDeadline()).thenReturn(deadline);
        when(opportunity.getTitle()).thenReturn("Software Intern");
        when(opportunity.getCompanyName()).thenReturn("Example Company");
        when(opportunity.getDescription()).thenReturn(description);
        return opportunity;
    }

    @Test void publicationUsesExistingInboxWithCompanySummaryDateAndCorrectDetailsLink() {
        when(jdbc.queryForList("select id from users where role='STUDENT' and active=true", Long.class))
                .thenReturn(List.of(11L, 12L));
        var opportunity = opportunity(OpportunityStatus.PUBLISHED, LocalDate.now(), "Build\n useful   software.");

        service.opportunityPublished(opportunity);

        for (long student : List.of(11L, 12L)) {
            verify(service).create(student, "opportunity-published:42", "OPPORTUNITY_PUBLISHED", "Software Intern",
                    "Example Company: Build useful software.", null, null, "/student/opportunities/42", PUBLISHED);
        }
        verify(jdbc, times(2)).update(contains("on conflict do nothing"), any(Object[].class));
    }

    @Test void draftsClosedAndExpiredOpportunitiesNeverQueryRecipientsOrWriteNotifications() {
        service.opportunityPublished(opportunity(OpportunityStatus.DRAFT, null, "Draft"));
        service.opportunityPublished(opportunity(OpportunityStatus.CLOSED, null, "Closed"));
        service.opportunityPublished(opportunity(OpportunityStatus.PUBLISHED, LocalDate.now().minusDays(1), "Expired"));
        verifyNoInteractions(jdbc);
    }

    @Test void summaryIsShortAndRetryUsesSamePersistentEventKey() {
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(11L));
        var opportunity = opportunity(OpportunityStatus.PUBLISHED, null, "x".repeat(300));

        service.opportunityPublished(opportunity);
        service.opportunityPublished(opportunity);

        verify(service, times(2)).create(11L, "opportunity-published:42", "OPPORTUNITY_PUBLISHED", "Software Intern",
                "Example Company: " + "x".repeat(237) + "...", null, null, "/student/opportunities/42", PUBLISHED);
    }
}
