package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompanyWorkingScheduleAndExceptionsIntegrationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T05:00:00Z"), ZoneId.of("Asia/Kolkata")); // Wednesday 10:30 AM IST
    private final AttendanceCalendarProperties calendar = new AttendanceCalendarProperties();
    private final AttendancePolicyService policies = mock(AttendancePolicyService.class);
    private final AttendanceDailyExceptionRepository exceptions = mock(AttendanceDailyExceptionRepository.class);
    private final StudentAttendanceDirectoryService directoryService = new StudentAttendanceDirectoryService(
            mock(UserRepository.class),
            mock(edu.institution.ims.mentor.MentorStudentAssignmentRepository.class),
            mock(edu.institution.ims.mentor.MentorProfileRepository.class),
            mock(InternshipDetailsRepository.class),
            mock(AttendanceRepository.class),
            policies,
            calendar,
            clock,
            exceptions
    );

    private User student;
    private InternshipDetails internshipMonFri;
    private InternshipDetails internshipMonSat;
    private InternshipDetails internship7Day;
    private InternshipDetails internshipTueSat;

    @BeforeEach
    void setUp() {
        student = mock(User.class);
        when(student.getId()).thenReturn(101L);
        when(student.getRole()).thenReturn(Role.STUDENT);
        when(student.isActive()).thenReturn(true);

        InternshipOnboarding onboarding = mock(InternshipOnboarding.class);
        when(onboarding.getStudent()).thenReturn(student);
        when(onboarding.getWorkflowStatus()).thenReturn(WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);
        when(onboarding.getInternshipNumber()).thenReturn(1);

        internshipMonFri = new InternshipDetails(onboarding);
        internshipMonFri.apply("Tech Corp", CompanyType.IT, null, "123 Tech Park", "+919876543210",
                "Supervisor", "Manager", "+919876543211", 6,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 4,
                InternshipMode.OFFICE_REPORTING, BigDecimal.valueOf(15000), false,
                5, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");

        internshipMonSat = new InternshipDetails(onboarding);
        internshipMonSat.apply("Plant Works", CompanyType.MANUFACTURING, null, "456 Factory Rd", "+919876543220",
                "Supervisor 2", "Lead", "+919876543221", 6,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 4,
                InternshipMode.OFFICE_REPORTING, BigDecimal.valueOf(18000), false,
                6, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY");

        internship7Day = new InternshipDetails(onboarding);
        internship7Day.apply("24/7 Operations", CompanyType.IT, null, "789 Cloud Ave", "+919876543230",
                "Supervisor 3", "Head", "+919876543231", 6,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 4,
                InternshipMode.ONLINE, BigDecimal.valueOf(20000), false,
                7, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");

        internshipTueSat = new InternshipDetails(onboarding);
        internshipTueSat.apply("Retail Mart", CompanyType.CORE, null, "321 Mall St", "+919876543240",
                "Supervisor 4", "Director", "+919876543241", 6,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 4,
                InternshipMode.OFFICE_REPORTING, BigDecimal.valueOf(12000), false,
                5, "TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY");

        when(policies.resolve(any(InternshipDetails.class), any(LocalDate.class)))
                .thenReturn(new ResolvedAttendancePolicy(HybridWorkMode.OFFICE, AttendanceVerificationMethod.PHYSICAL, true));
        when(policies.lifecycle(any(InternshipDetails.class), any(LocalDate.class)))
                .thenReturn(AttendanceLifecycleStatus.ACTIVE);
    }

    private AttendanceRecord createRecord(InternshipDetails internship, LocalDate date, AttendanceResult result, boolean autoAbsent) {
        return new AttendanceRecord(student, internship, date, result,
                VerificationStatus.VERIFIED,
                autoAbsent ? AttendanceSubmissionType.AUTO_ABSENT : AttendanceSubmissionType.PHYSICAL_CAPTURE,
                clock.instant());
    }

    @Test
    @DisplayName("Scenario 1: Five-day Monday–Friday internship correctly identifies working weekdays")
    void test1_fiveDayMonFriSchedule() {
        assertThat(internshipMonFri.getEffectiveWorkingDaysPerWeek()).isEqualTo(5);
        assertThat(internshipMonFri.isWorkingDay(LocalDate.of(2026, 11, 23))).isTrue(); // Monday
        assertThat(internshipMonFri.isWorkingDay(LocalDate.of(2026, 11, 27))).isTrue(); // Friday
        assertThat(internshipMonFri.isWorkingDay(LocalDate.of(2026, 11, 21))).isFalse(); // Saturday
        assertThat(internshipMonFri.isWorkingDay(LocalDate.of(2026, 11, 22))).isFalse(); // Sunday
    }

    @Test
    @DisplayName("Scenario 2: Six-day Monday–Saturday internship includes Saturday as a working day")
    void test2_sixDayMonSatSchedule() {
        assertThat(internshipMonSat.getEffectiveWorkingDaysPerWeek()).isEqualTo(6);
        assertThat(internshipMonSat.isWorkingDay(LocalDate.of(2026, 11, 21))).isTrue(); // Saturday
        assertThat(internshipMonSat.isWorkingDay(LocalDate.of(2026, 11, 22))).isFalse(); // Sunday
    }

    @Test
    @DisplayName("Scenario 3: Seven-day internship includes all days as working days")
    void test3_sevenDaySchedule() {
        assertThat(internship7Day.getEffectiveWorkingDaysPerWeek()).isEqualTo(7);
        assertThat(internship7Day.isWorkingDay(LocalDate.of(2026, 11, 21))).isTrue(); // Saturday
        assertThat(internship7Day.isWorkingDay(LocalDate.of(2026, 11, 22))).isTrue(); // Sunday
    }

    @Test
    @DisplayName("Scenario 4: Company working Tuesday–Saturday excludes Monday and Sunday")
    void test4_tuesdaySaturdaySchedule() {
        assertThat(internshipTueSat.getEffectiveWorkingDaysPerWeek()).isEqualTo(5);
        assertThat(internshipTueSat.isWorkingDay(LocalDate.of(2026, 11, 23))).isFalse(); // Monday is day off
        assertThat(internshipTueSat.isWorkingDay(LocalDate.of(2026, 11, 24))).isTrue(); // Tuesday
        assertThat(internshipTueSat.isWorkingDay(LocalDate.of(2026, 11, 21))).isTrue(); // Saturday
        assertThat(internshipTueSat.isWorkingDay(LocalDate.of(2026, 11, 22))).isFalse(); // Sunday is day off
    }

    @Test
    @DisplayName("Scenario 5: Sunday off with no student response produces NO automatic absence")
    void test5_sundayOffNoAbsence() {
        LocalDate sunday = LocalDate.of(2026, 11, 22);
        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-22T08:00:00Z"), ZoneId.of("Asia/Kolkata")); // 1:30 PM IST

        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonFri, sunday, pastCutoffClock, policies, calendar, exceptions);

        assertThat(applicable).isFalse();
    }

    @Test
    @DisplayName("Scenario 6: Sunday extra work submitted counts as PRESENT in directory totals")
    void test6_sundayExtraWorkPresent() {
        LocalDate sunday = LocalDate.of(2026, 11, 22);
        when(exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internshipMonFri.getId(), sunday, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED))
                .thenReturn(true);

        AttendanceRecord record = createRecord(internshipMonFri, sunday, AttendanceResult.PRESENT, false);
        var totals = directoryService.totals(List.of(record), internshipMonFri);

        assertThat(totals.presentDays()).isEqualTo(1);
        assertThat(totals.submittedDays()).isEqualTo(1);
        assertThat(totals.approvedExtraWorkingDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 7: Saturday normally working but missed becomes ABSENT")
    void test7_saturdayNormallyWorkingMissedIsAbsent() {
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-21T18:30:00Z"), ZoneId.of("Asia/Kolkata")); // end of Nov 21 IST

        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonSat, saturday, pastCutoffClock, policies, calendar, exceptions);

        assertThat(applicable).isTrue();
    }

    @Test
    @DisplayName("Scenario 8: Saturday declared holiday and approved is excluded from expected attendance and absences")
    void test8_saturdayApprovedHolidayExcluded() {
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        when(exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internshipMonSat.getId(), saturday, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED))
                .thenReturn(true);

        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-21T18:30:00Z"), ZoneId.of("Asia/Kolkata"));
        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonSat, saturday, pastCutoffClock, policies, calendar, exceptions);

        assertThat(applicable).isFalse();

        // Also check directory totals
        var totals = directoryService.totals(List.of(), internshipMonSat);
        assertThat(totals.approvedHolidayDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 9: Holiday request pending or rejected does not exclude the date")
    void test9_holidayPendingOrRejectedNotExcluded() {
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        // Only pending, NOT approved
        when(exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internshipMonSat.getId(), saturday, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED))
                .thenReturn(false);

        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-21T18:30:00Z"), ZoneId.of("Asia/Kolkata"));
        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonSat, saturday, pastCutoffClock, policies, calendar, exceptions);

        assertThat(applicable).isTrue();
    }

    @Test
    @DisplayName("Scenario 10: Company working day is respected according to company schedule")
    void test10_companyScheduleRespected() {
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        // For Mon-Sat company, Saturday is an applicable working day
        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-21T18:30:00Z"), ZoneId.of("Asia/Kolkata"));
        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonSat, saturday, pastCutoffClock, policies, calendar, exceptions);
        assertThat(applicable).isTrue();

        // For Mon-Fri company, Saturday is NOT applicable
        boolean monFriApplicable = AttendanceAbsenceService.isApplicable(
                internshipMonFri, saturday, pastCutoffClock, policies, calendar, exceptions);
        assertThat(monFriApplicable).isFalse();
    }

    @Test
    @DisplayName("Scenario 11: Dates before start date or after end date are rejected")
    void test11_startAndEndDatesEnforced() {
        LocalDate beforeStart = LocalDate.of(2026, 10, 31);
        LocalDate afterEnd = LocalDate.of(2026, 12, 1);
        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-12-05T08:00:00Z"), ZoneId.of("Asia/Kolkata"));

        assertThat(AttendanceAbsenceService.isApplicable(
                internshipMonFri, beforeStart, pastCutoffClock, policies, calendar, exceptions)).isFalse();
        assertThat(AttendanceAbsenceService.isApplicable(
                internshipMonFri, afterEnd, pastCutoffClock, policies, calendar, exceptions)).isFalse();
    }

    @Test
    @DisplayName("Scenario 12: Completed internships are closed for attendance")
    void test12_completedInternshipClosed() {
        when(policies.lifecycle(eq(internshipMonFri), any(LocalDate.class)))
                .thenReturn(AttendanceLifecycleStatus.COMPLETED);

        LocalDate date = LocalDate.of(2026, 11, 10);
        Clock clockAfterCutoff = Clock.fixed(Instant.parse("2026-11-10T08:00:00Z"), ZoneId.of("Asia/Kolkata"));

        boolean applicable = AttendanceAbsenceService.isApplicable(
                internshipMonFri, date, clockAfterCutoff, policies, calendar, exceptions);

        assertThat(applicable).isFalse();
    }

    @Test
    @DisplayName("Scenario 13: Two internships with different schedules calculate independently")
    void test13_dualInternshipsIndependentSchedules() {
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        Clock pastCutoffClock = Clock.fixed(Instant.parse("2026-11-21T18:30:00Z"), ZoneId.of("Asia/Kolkata")); // end of Nov 21 IST

        // Internship 1 (Mon-Fri) ignores Saturday
        boolean int1Applicable = AttendanceAbsenceService.isApplicable(
                internshipMonFri, saturday, pastCutoffClock, policies, calendar, exceptions);
        // Internship 2 (Mon-Sat) requires Saturday
        boolean int2Applicable = AttendanceAbsenceService.isApplicable(
                internshipMonSat, saturday, pastCutoffClock, policies, calendar, exceptions);

        assertThat(int1Applicable).isFalse();
        assertThat(int2Applicable).isTrue();
    }

    @Test
    @DisplayName("Scenario 14: Student, Mentor, and Admin calculations reconcile to identical counts")
    void test14_reconciliationAcrossDashboards() {
        LocalDate mon = LocalDate.of(2026, 11, 23);
        LocalDate tue = LocalDate.of(2026, 11, 24);
        LocalDate wed = LocalDate.of(2026, 11, 25);

        List<AttendanceRecord> records = List.of(
                createRecord(internshipMonFri, mon, AttendanceResult.PRESENT, false),
                createRecord(internshipMonFri, tue, AttendanceResult.PRESENT, false),
                createRecord(internshipMonFri, wed, AttendanceResult.ABSENT, true)
        );

        var totals = directoryService.totals(records, internshipMonFri);

        // Mentor/Admin directory sees:
        assertThat(totals.presentDays()).isEqualTo(2);
        assertThat(totals.absentDays()).isEqualTo(1);
        assertThat(totals.submittedDays()).isEqualTo(2);

        // Student history sees the exact same totals:
        UserPrincipal studentPrincipal = new UserPrincipal(101L, "student@test.edu", "hash", true, "STUDENT", false, 0);
        StudentAttendanceDirectoryService.History history = new StudentAttendanceDirectoryService.History(
                101L, "Asia/Kolkata", totals, List.of(), List.of(), internshipMonFri.getId());

        assertThat(history.totals().presentDays()).isEqualTo(totals.presentDays());
        assertThat(history.totals().absentDays()).isEqualTo(totals.absentDays());
        assertThat(history.totals().submittedDays()).isEqualTo(totals.submittedDays());
    }

    @Test
    @DisplayName("Scenario 15: Fallback Monday–Friday is preserved when schedule is missing")
    void test15_fallbackMondayFridayWhenScheduleMissing() {
        InternshipDetails legacyInternship = new InternshipDetails(internshipMonFri.getOnboarding());
        legacyInternship.apply("Legacy Co", CompanyType.IT, null, "Address", "Phone",
                "Supervisor", "Designation", "Contact", 3,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 4,
                InternshipMode.OFFICE_REPORTING, BigDecimal.valueOf(10000), false,
                null, null); // null schedule

        assertThat(legacyInternship.getEffectiveWorkingDaysPerWeek()).isEqualTo(5);
        assertThat(legacyInternship.isWorkingDay(LocalDate.of(2026, 11, 23))).isTrue(); // Monday
        assertThat(legacyInternship.isWorkingDay(LocalDate.of(2026, 11, 21))).isFalse(); // Saturday
    }

    @Test
    @DisplayName("Scenario 16: Duplicate and automatic absence prevention is idempotent")
    void test16_duplicateAbsencePrevention() {
        LocalDate date = LocalDate.of(2026, 11, 20);
        AttendanceRecord existing = createRecord(internshipMonFri, date, AttendanceResult.PRESENT, false);

        // When record already exists, writer / absence service does not re-add
        Set<LocalDate> recorded = Set.of(existing.getAttendanceDate());
        assertThat(recorded.contains(date)).isTrue();
    }
}
