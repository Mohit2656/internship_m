package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.InternshipDetailsResponse;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InternshipLifecycleAdminServiceTest {
    private InternshipDetailsRepository internships;
    private UserRepository users;
    private JdbcTemplate jdbc;
    private Clock clock;
    private InternshipLifecycleAdminService service;

    private User adminUser;
    private UserPrincipal adminPrincipal;
    private InternshipDetails details;
    private InternshipOnboarding onboarding;
    private User studentUser;

    @BeforeEach
    void setUp() {
        internships = mock(InternshipDetailsRepository.class);
        users = mock(UserRepository.class);
        jdbc = mock(JdbcTemplate.class);
        clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        service = new InternshipLifecycleAdminService(internships, users, jdbc, clock);

        adminUser = mock(User.class);
        when(adminUser.getId()).thenReturn(1L);
        when(adminUser.isActive()).thenReturn(true);
        when(adminUser.getRole()).thenReturn(Role.ADMIN);

        adminPrincipal = new UserPrincipal(1L, "admin@institution.edu", "hash", true, "ADMIN", false, 1L);
        when(users.findById(1L)).thenReturn(Optional.of(adminUser));

        studentUser = mock(User.class);
        when(studentUser.getId()).thenReturn(10L);

        onboarding = mock(InternshipOnboarding.class);
        when(onboarding.getId()).thenReturn(100L);
        when(onboarding.getStudent()).thenReturn(studentUser);
        when(onboarding.getInternshipNumber()).thenReturn(1);
        when(onboarding.getWorkflowStatus()).thenReturn(WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);

        details = new InternshipDetails(onboarding);
        when(internships.findById(50L)).thenReturn(Optional.of(details));
    }

    @Test
    void terminatesInternshipEarlySuccessfully() {
        InternshipDetailsResponse response = service.terminateEarly(adminPrincipal, 50L, "Student accepted full time offer elsewhere");
        assertThat(details.isEarlyTerminated()).isTrue();
        assertThat(details.getLifecycleStatus()).isEqualTo("TERMINATED_EARLY");
        assertThat(details.getTerminationReason()).isEqualTo("Student accepted full time offer elsewhere");
        assertThat(details.getTerminatedAt()).isEqualTo(clock.instant());
        assertThat(response).isNotNull();
    }

    @Test
    void rejectsTerminationWithShortReason() {
        assertThatThrownBy(() -> service.terminateEarly(adminPrincipal, 50L, "bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reason must be between 5 and 1000 characters");
    }

    @Test
    void softDeletesInternshipSuccessfully() {
        InternshipDetailsResponse response = service.softDelete(adminPrincipal, 50L, "Duplicate entry entered by mistake");
        assertThat(details.isDeleted()).isTrue();
        assertThat(details.getDeletionReason()).isEqualTo("Duplicate entry entered by mistake");
        assertThat(details.getDeletedAt()).isEqualTo(clock.instant());
        assertThat(response).isNotNull();
    }

    @Test
    void forbidsNonAdminFromLifecycleActions() {
        UserPrincipal studentPrincipal = new UserPrincipal(10L, "student@institution.edu", "hash", true, "STUDENT", false, 1L);
        assertThatThrownBy(() -> service.terminateEarly(studentPrincipal, 50L, "Valid reason here"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only III Cell Incharge / Admin can perform this action");
    }
}
