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
    public static final int DAILY_SUBMISSION_SLOTS = 2;
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
        LocalDate through = overdueCutoff();
        int created = 0;
        for (InternshipDetails internship : internships.findEligibleForAbsenceProcessing(through)) {
            created += processInternship(internship, through);
        }
        return created;
    }

    public int processOverdueForInternship(Long internshipId) {
        if (internshipId == null) return 0;
        purgeInvalidAutomaticAbsences();
        InternshipDetails internship = internships.findByIdWithOnboardingAndStudent(internshipId)
                .orElseGet(() -> internships.findById(internshipId).orElse(null));
        if (!isEligibleInternship(internship)) return 0;
        return processInternship(internship, overdueCutoff());
    }

    private LocalDate overdueCutoff() {
        LocalDate today = InternshipLifecycle.today(clock);
        return AttendanceSubmissionWindow.isClosed(today, clock) ? today : today.minusDays(1);
    }

    private static boolean isEligibleInternship(InternshipDetails internship) {
        if (internship == null || internship.getStartDate() == null || internship.getEndDate() == null) return false;
        if (internship.isDeleted() || internship.isEarlyTerminated()) return false;
        if ("COMPLETED".equals(internship.getCompletionStatus())) return false;
        var onboarding = internship.getOnboarding();
        if (onboarding == null || onboarding.getWorkflowStatus() != edu.institution.ims.internship.WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) {
            return false;
        }
        var student = onboarding.getStudent();
        return student != null && student.isActive() && student.getRole() == edu.institution.ims.user.Role.STUDENT;
    }

    private int processInternship(InternshipDetails internship, LocalDate through) {
        if (internship.getStartDate() == null || internship.getEndDate() == null) return 0;
        if (internship.getStartDate().isAfter(through)) return 0;
        purgeNonApplicableAutomaticAbsences(internship);
        int created = 0;
        try {
            Map<LocalDate, Long> recordedCounts = attendance.findRecordedDatesIncludingDeleted(internship.getId()).stream()
                    .map(java.sql.Date::toLocalDate)
                    .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
            LocalDate end = internship.getEndDate().isBefore(through) ? internship.getEndDate() : through;
            for (LocalDate date = internship.getStartDate(); !date.isAfter(end); date = date.plusDays(1)) {
                long existingCount = recordedCounts.getOrDefault(date, 0L);
                if (existingCount >= DAILY_SUBMISSION_SLOTS || !isApplicable(internship, date, clock, policies, calendar, exceptions)) {
                    continue;
                }
                List<Integer> activeSlots = attendance.findActiveSlots(internship.getId(), date);
                for (int slot = 1; slot <= DAILY_SUBMISSION_SLOTS && existingCount < DAILY_SUBMISSION_SLOTS; slot++) {
                    if (activeSlots != null && activeSlots.contains(slot)) continue;
                    try {
                        if (writer.createIfMissing(internship.getId(), date, slot)) {
                            created++;
                            existingCount++;
                        }
                    } catch (DataIntegrityViolationException conflict) {
                        // The database daily unique key arbitrates concurrent workers/submissions.
                        if (attendance.countIncludingDeleted(internship.getId(), date) < DAILY_SUBMISSION_SLOTS)
                            log.error("Automatic absence insert failed for internship {} on {} (slot {}); will retry",
                                    internship.getId(), date, slot, conflict);
                    } catch (RuntimeException failure) {
                        log.error("Automatic absence insert failed for internship {} on {} (slot {}); will retry",
                                internship.getId(), date, slot, failure);
                    }
                }
            }
        } catch (RuntimeException failure) {
            // No checkpoint is advanced. Missing days retry automatically on the next execution.
            log.error("Automatic absence processing failed for internship {}; will retry", internship.getId(), failure);
        }
        return created;
    }

    private void purgeNonApplicableAutomaticAbsences(InternshipDetails internship) {
        if (jdbc == null || internship.getId() == null) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, attendance_date from attendance_records where internship_id = ? and submission_type = 'AUTO_ABSENT' and deleted_at is null",
                internship.getId());
        for (Map<String, Object> row : rows) {
            Object rawDate = row.get("attendance_date");
            LocalDate date;
            if (rawDate instanceof java.sql.Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else if (rawDate instanceof LocalDate localDate) {
                date = localDate;
            } else if (rawDate != null) {
                date = LocalDate.parse(rawDate.toString());
            } else {
                continue;
            }
            if (!isApplicable(internship, date, clock, policies, calendar, exceptions)) {
                jdbc.update("delete from attendance_records where id = ? and submission_type = 'AUTO_ABSENT'",
                        ((Number) row.get("id")).longValue());
            }
        }
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
                && isTrackedWorkingDayPolicy(internship, date, policies);
    }

    static boolean isTrackedWorkingDayPolicy(InternshipDetails internship, LocalDate date, AttendancePolicyService policies) {
        if (policies.resolve(internship, date).configurationReady()) {
            return true;
        }
        return internship.getInternshipMode() == InternshipMode.HYBRID
                && !policies.hasConfiguredHybridSchedule(internship.getId());
    }
}

