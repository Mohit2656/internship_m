package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentAttendanceDirectoryServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MentorStudentAssignmentRepository assignments = mock(MentorStudentAssignmentRepository.class);
    private final MentorProfileRepository mentorProfiles = mock(MentorProfileRepository.class);
    private final InternshipDetailsRepository internships = mock(InternshipDetailsRepository.class);
    private final AttendanceRepository records = mock(AttendanceRepository.class);
    private final AttendancePolicyService policies = mock(AttendancePolicyService.class);
    private final AttendanceCalendarProperties calendar = new AttendanceCalendarProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T07:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private final StudentAttendanceDirectoryService service = new StudentAttendanceDirectoryService(users, assignments, mentorProfiles, internships, records, policies, calendar, clock);
    private final User student = student();
    private final InternshipDetails internship = internship();
    private User student() { User user = mock(User.class); when(user.getId()).thenReturn(1L); when(user.getRole()).thenReturn(Role.STUDENT); when(user.isActive()).thenReturn(true); return user; }
    private InternshipDetails internship() {
        InternshipDetails i = mock(InternshipDetails.class);
        when(i.getStartDate()).thenReturn(LocalDate.of(2026, 10, 1)); when(i.getEndDate()).thenReturn(LocalDate.of(2026, 11, 1));
        when(policies.resolve(eq(i), any())).thenReturn(new ResolvedAttendancePolicy(null, AttendanceVerificationMethod.BASIC, true));
        return i;
    }
    private AttendanceRecord row(String date, AttendanceResult result) { return new AttendanceRecord(student, internship, LocalDate.parse(date), result, VerificationStatus.UNVERIFIED, AttendanceSubmissionType.BASIC, clock.instant()); }
    private UserPrincipal principal(String role) { return new UserPrincipal(9L, "test@example.edu", "unused", true, role, false, 0); }

    @Test void countsPresentAndAbsentOncePerApplicableDateAndExcludesNonWorkingDays() {
        calendar.getHolidays().add(LocalDate.parse("2026-10-20"));
        calendar.getApprovedLeave().put(1L, List.of(new AttendanceCalendarProperties.LeavePeriod(LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-21"))));
        when(policies.resolve(internship, LocalDate.parse("2026-10-15"))).thenReturn(new ResolvedAttendancePolicy(null, AttendanceVerificationMethod.UNAVAILABLE, false));
        var totals = service.totals(List.of(row("2026-10-19", AttendanceResult.PRESENT), row("2026-10-19", AttendanceResult.PRESENT),
                row("2026-10-22", AttendanceResult.ABSENT), row("2026-10-23", AttendanceResult.PRESENT), row("2026-10-18", AttendanceResult.PRESENT),
                row("2026-10-20", AttendanceResult.PRESENT), row("2026-10-21", AttendanceResult.PRESENT), row("2026-10-15", AttendanceResult.PRESENT), row("2026-09-30", AttendanceResult.PRESENT)));
        assertThat(totals.presentDays()).isEqualTo(1); assertThat(totals.absentDays()).isEqualTo(1);
        assertThat(totals.totalDays()).isEqualTo(2); assertThat(totals.percentage()).isEqualByComparingTo("50.0");
        assertThat(service.totals(List.of()).percentage()).isNull();
    }
    @Test void directoryHasOneUnscopedRowPerAccountWhenNoInternshipExists() {
        when(users.findAllByRoleOrderByEmailAsc(Role.STUDENT)).thenReturn(List.of(student, student));
        when(assignments.findAllByActiveTrue()).thenReturn(List.of());
        when(internships.findAllByOnboardingStudentIdIn(any())).thenReturn(List.of());
        when(records.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(any())).thenReturn(List.of(row("2026-10-19", AttendanceResult.PRESENT), row("2026-10-22", AttendanceResult.ABSENT)));
        var rows = service.students(principal("ADMIN"));
        assertThat(rows).hasSize(1); assertThat(rows.get(0).studentUserId()).isEqualTo(1L);
        assertThat(rows.get(0).internshipId()).isNull();
        assertThat(rows.get(0).attendance().percentage()).isNull();
    }
    @Test void mentorCannotReadAnUnassignedStudentsHistory() {
        when(assignments.findByStudentIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(principal("MENTOR"), 1L)).isInstanceOf(AttendanceException.class);
        verifyNoInteractions(records);
        assertThatThrownBy(() -> service.students(principal("STUDENT"))).isInstanceOf(AttendanceException.class);
    }
    @Test void authorizedHistoryUsesSelectedStudentAndChronologicalRepositoryOrder() {
        when(users.findById(1L)).thenReturn(Optional.of(student));
        when(records.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(List.of(1L))).thenReturn(List.of(row("2026-10-19", AttendanceResult.PRESENT), row("2026-10-22", AttendanceResult.ABSENT)));
        var history = service.history(principal("ADMIN"), 1L);
        assertThat(history.studentUserId()).isEqualTo(1L);
        assertThat(history.records()).extracting(AttendanceResponse::attendanceDate).containsExactly(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-22"));
    }
    @Test void doesNotCountPendingMentorReviewAsAbsent() {
        var totals = service.totals(List.of(
                row("2026-10-19", AttendanceResult.PRESENT),
                row("2026-10-22", AttendanceResult.PENDING_MENTOR_REVIEW)));
        assertThat(totals.presentDays()).isEqualTo(1);
        assertThat(totals.absentDays()).isEqualTo(0);
        assertThat(totals.totalDays()).isEqualTo(1);
        assertThat(totals.percentage()).isEqualByComparingTo("100.0");
    }

    @Test void bismeetScenarioCountsVerifiedPresentWhileMentorReviewIsPending() {
        User bismeet = mock(User.class);
        StudentAccount account = mock(StudentAccount.class);
        InternshipOnboarding onboarding = mock(InternshipOnboarding.class);
        InternshipDetails selectedInternship = mock(InternshipDetails.class);
        AttendanceEvidence verifiedEvidence = mock(AttendanceEvidence.class);
        AttendanceRecord verifiedPresent = mock(AttendanceRecord.class);

        when(bismeet.getId()).thenReturn(77L);
        when(bismeet.getRole()).thenReturn(Role.STUDENT);
        when(bismeet.isActive()).thenReturn(true);
        when(bismeet.getStudentAccount()).thenReturn(account);
        when(account.getStudentName()).thenReturn("Bismeet Kukreja");
        when(account.getUid()).thenReturn("BISMEET-77");
        when(selectedInternship.getId()).thenReturn(701L);
        when(selectedInternship.getOnboarding()).thenReturn(onboarding);
        when(selectedInternship.getStartDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(selectedInternship.getEndDate()).thenReturn(LocalDate.of(2026, 10, 31));
        when(selectedInternship.getCompanyName()).thenReturn("Company A");
        when(onboarding.getStudent()).thenReturn(bismeet);
        when(onboarding.getInternshipNumber()).thenReturn(1);
        when(onboarding.getWorkflowStatus()).thenReturn(WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);
        when(policies.resolve(eq(selectedInternship), any())).thenReturn(
                new ResolvedAttendancePolicy(null, AttendanceVerificationMethod.BASIC, true));

        when(verifiedPresent.getStudent()).thenReturn(bismeet);
        when(verifiedPresent.getInternship()).thenReturn(selectedInternship);
        when(verifiedPresent.getAttendanceDate()).thenReturn(LocalDate.of(2026, 10, 19));
        when(verifiedPresent.getAttendanceResult()).thenReturn(AttendanceResult.PRESENT);
        when(verifiedPresent.getEvidence()).thenReturn(verifiedEvidence);
        when(verifiedPresent.getSubmissionType()).thenReturn(AttendanceSubmissionType.BASIC);
        when(verifiedEvidence.getLocationVerificationStatus()).thenReturn(LocationVerificationStatus.VERIFIED);

        when(users.findAllByRoleOrderByEmailAsc(Role.STUDENT)).thenReturn(List.of(bismeet));
        when(assignments.findAllByActiveTrue()).thenReturn(List.of());
        when(mentorProfiles.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(internships.findAllByOnboardingStudentIdIn(any())).thenReturn(List.of(selectedInternship));
        when(records.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(any())).thenReturn(List.of(verifiedPresent));

        var result = service.students(principal("ADMIN"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).internshipId()).isEqualTo(701L);
        assertThat(result.get(0).attendance().presentDays()).isEqualTo(1);
        assertThat(result.get(0).attendance().absentDays()).isZero();
        assertThat(result.get(0).attendance().totalDays()).isEqualTo(1);
        assertThat(result.get(0).attendance().percentage()).isEqualByComparingTo("100.0");
        assertThat(result.get(0).reviewSummary().submitted()).isEqualTo(1);
        assertThat(result.get(0).reviewSummary().gpsVerified()).isEqualTo(1);
        assertThat(result.get(0).reviewSummary().pending()).isEqualTo(1);
    }
}
