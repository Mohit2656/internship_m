package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceAbsenceService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceAbsenceService.class);
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final AttendanceCalendarProperties calendar;
    private final AttendanceAbsenceWriter writer;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AttendanceDailyExceptionRepository exceptions;

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceAbsenceService(InternshipDetailsRepository internships, AttendanceRepository attendance,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar,
            AttendanceAbsenceWriter writer, JdbcTemplate jdbc, Clock clock,
            AttendanceDailyExceptionRepository exceptions) {
        this.internships = internships;
        this.attendance = attendance;
        this.policies = policies;
        this.calendar = calendar;
        this.writer = writer;
        this.jdbc = jdbc;
        this.clock = clock;
        this.exceptions = exceptions;
    }

    public AttendanceAbsenceService(InternshipDetailsRepository internships, AttendanceRepository attendance,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar,
            AttendanceAbsenceWriter writer, JdbcTemplate jdbc, Clock clock) {
        this(internships, attendance, policies, calendar, writer, jdbc, clock, null);
    }

    public AttendanceAbsenceService(InternshipDetailsRepository internships, AttendanceRepository attendance,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar,
            AttendanceAbsenceWriter writer, Clock clock) {
        this(internships, attendance, policies, calendar, writer, null, clock, null);
    }

    public int processOverdueAttendance() {
        purgeInvalidAutomaticAbsences();
        LocalDate today = InternshipLifecycle.today(clock);
        LocalDate through = AttendanceSubmissionWindow.isClosed(today, clock) ? today : today.minusDays(1);
        int created = 0;
        for (InternshipDetails internship : internships.findEligibleForAbsenceProcessing(through)) {
            if (internship.getStartDate() == null || internship.getEndDate() == null) continue;
            try {
                Set<LocalDate> recorded = attendance.findRecordedDatesIncludingDeleted(internship.getId()).stream()
                        .map(java.sql.Date::toLocalDate).collect(Collectors.toSet());
                LocalDate end = internship.getEndDate().isBefore(through) ? internship.getEndDate() : through;
                for (LocalDate date = internship.getStartDate(); !date.isAfter(end); date = date.plusDays(1)) {
                    if (recorded.contains(date) || !isApplicable(internship, date, clock, policies, calendar, exceptions)) continue;
                    try {
                        if (writer.createIfMissing(internship.getId(), date)) created++;
                    } catch (DataIntegrityViolationException conflict) {
                        // The database daily unique key arbitrates concurrent workers/submissions.
                        if (attendance.countIncludingDeleted(internship.getId(), date) == 0)
                            log.error("Automatic absence insert failed for internship {} on {}; will retry",
                                    internship.getId(), date, conflict);
                    } catch (RuntimeException failure) {
                        log.error("Automatic absence insert failed for internship {} on {}; will retry",
                                internship.getId(), date, failure);
                    }
                }
            } catch (RuntimeException failure) {
                // No checkpoint is advanced. Missing days retry automatically on the next execution.
                log.error("Automatic absence processing failed for internship {}; will retry", internship.getId(), failure);
            }
        }
        return created;
    }

    @Transactional
    public int purgeInvalidAutomaticAbsences() {
        if (jdbc == null) return 0;
        int deletedOutOfBounds = jdbc.update("""
            delete from attendance_records
            where submission_type = 'AUTO_ABSENT'
              and exists (
                select 1 from internship_details i
                where i.id = attendance_records.internship_id
                  and (attendance_records.attendance_date < i.start_date or attendance_records.attendance_date > i.end_date)
              )
            """);
        int deletedApprovedHolidays = jdbc.update("""
            delete from attendance_records
            where submission_type = 'AUTO_ABSENT'
              and exists (
                select 1 from attendance_exceptions e
                where e.internship_id = attendance_records.internship_id
                  and e.exception_date = attendance_records.attendance_date
                  and e.exception_type = 'COMPANY_HOLIDAY'
                  and e.status = 'APPROVED'
              )
            """);
        return deletedOutOfBounds + deletedApprovedHolidays;
    }

    static boolean isApplicable(InternshipDetails internship, LocalDate date, Clock clock,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar) {
        return isApplicable(internship, date, clock, policies, calendar, null);
    }

    static boolean isApplicable(InternshipDetails internship, LocalDate date, Clock clock,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar,
            AttendanceDailyExceptionRepository exceptions) {
        if (internship == null || internship.getStartDate() == null || internship.getEndDate() == null) return false;
        if (date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate())) return false;
        LocalDate today = InternshipLifecycle.today(clock);
        if (date.isAfter(today)) return false;
        var student = internship.getOnboarding().getStudent();

        // 1. Check if date is a working day according to internship working schedule
        boolean isWorkingDay = internship.isWorkingDay(date);
        boolean isApprovedExtraWork = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internship.getId(), date, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED);

        if (!isWorkingDay && !isApprovedExtraWork) {
            // Scheduled day off with no action never generates automatic absence
            return false;
        }

        // 2. Check for approved company holiday exception
        boolean isApprovedHoliday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                internship.getId(), date, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED);
        if (isApprovedHoliday) {
            return false;
        }

        return internship.getOnboarding().getWorkflowStatus() == WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE
                && student.isActive() && student.getRole() == Role.STUDENT
                && policies.lifecycle(internship, date) == AttendanceLifecycleStatus.ACTIVE
                && AttendanceSubmissionWindow.isClosed(date, clock)
                && !calendar.isExcluded(student.getId(), date)
                && policies.resolve(internship, date).configurationReady();
    }
}

