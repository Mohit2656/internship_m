package edu.institution.ims.opportunity;

import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.professionalprofile.SkillNormalizer;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpportunityPublicationNotificationTest {
    private final OpportunityRepository opportunities = mock(OpportunityRepository.class);
    private final OpportunitySkillRepository skills = mock(OpportunitySkillRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OpportunityService service = new OpportunityService(opportunities, skills,
            mock(UserRepository.class), mock(SkillNormalizer.class), notifications);

    private OpportunityRequest request(String description) {
        return new OpportunityRequest("Example Company", "Software Intern", description, null, "Pune",
                OpportunityMode.HYBRID, 3, BigDecimal.ZERO, "INR", LocalDate.now().plusDays(7),
                null, List.of(), List.of(), List.of(), List.of());
    }

    private Opportunity draft(String description) {
        var creator = mock(User.class);
        when(creator.getId()).thenReturn(1L);
        var opportunity = new Opportunity(creator, request(description));
        ReflectionTestUtils.setField(opportunity, "id", 42L);
        when(opportunities.findByIdForUpdate(42L)).thenReturn(Optional.of(opportunity));
        when(skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(42L)).thenReturn(List.of());
        return opportunity;
    }

    @Test void successfulPublicationNotifiesOnceAfterPersistenceAndRetryKeepsPublicationDate() {
        var opportunity = draft("Build useful software.");
        var first = service.publish(42L);
        var retry = service.publish(42L);

        assertThat(first.status()).isEqualTo(OpportunityStatus.PUBLISHED);
        assertThat(retry.publishedAt()).isEqualTo(first.publishedAt());
        var ordered = inOrder(opportunities, notifications);
        ordered.verify(opportunities).findByIdForUpdate(42L);
        ordered.verify(opportunities).saveAndFlush(opportunity);
        ordered.verify(notifications).opportunityPublished(opportunity);
        verify(notifications, times(1)).opportunityPublished(opportunity);
        verify(opportunities, times(1)).saveAndFlush(opportunity);
    }

    @Test void editingPublishedOpportunityKeepsPublicationDateAndDoesNotNotifyAgain() {
        var opportunity = draft("Original description.");
        var published = service.publish(42L);
        var updated = service.update(42L, request("Updated description."));

        assertThat(updated.description()).isEqualTo("Updated description.");
        assertThat(updated.publishedAt()).isEqualTo(published.publishedAt());
        verify(notifications, times(1)).opportunityPublished(opportunity);
    }

    @Test void savingDraftDoesNotNotify() {
        draft(null);
        assertThat(service.update(42L, request("Ready for review.")).status()).isEqualTo(OpportunityStatus.DRAFT);
        verifyNoInteractions(notifications);
    }

    @Test void invalidOrClosedPublicationDoesNotNotify() {
        var opportunity = draft(null);
        assertThatThrownBy(() -> service.publish(42L)).isInstanceOf(OpportunityException.class);
        opportunity.publish(); opportunity.close();
        assertThatThrownBy(() -> service.publish(42L)).isInstanceOf(OpportunityException.class);
        verifyNoInteractions(notifications);
        verify(opportunities, never()).saveAndFlush(any());
    }

    @Test void persistenceFailureDoesNotNotifyAndNotificationFailurePropagatesForRollback() {
        var opportunity = draft("Build useful software.");
        doThrow(new DataAccessResourceFailureException("Unavailable")).when(opportunities).saveAndFlush(opportunity);
        assertThatThrownBy(() -> service.publish(42L)).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(notifications);

        // A notification write failure must reach the existing @Transactional publisher.
        opportunity = draft("Build useful software.");
        doReturn(opportunity).when(opportunities).saveAndFlush(opportunity);
        doThrow(new DataAccessResourceFailureException("Unavailable")).when(notifications).opportunityPublished(opportunity);
        assertThatThrownBy(() -> service.publish(42L)).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
