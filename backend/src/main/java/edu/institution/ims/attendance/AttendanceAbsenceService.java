package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
    private final Clock clock;

    public AttendanceAbsenceService(InternshipDetailsRepository internships, AttendanceRepository attendance,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar,
            AttendanceAbsenceWriter writer, Clock clock) {
        this.internships = internships;
        this.attendance = attendance;
        this.policies = policies;
        this.calendar = calendar;
        this.writer = writer;
        this.clock = clock;
    }

    public int processOverdueAttendance() {
        LocalDate today = LocalDate.now(clock);
        LocalDate through = AttendanceSubmissionWindow.isClosed(today, clock) ? today : today.minusDays(1);
        int created = 0;
        for (InternshipDetails internship : internships.findEligibleForAbsenceProcessing(through)) {
            try {
                Set<LocalDate> recorded = attendance.findRecordedDatesIncludingDeleted(internship.getId()).stream()
                        .map(java.sql.Date::toLocalDate).collect(Collectors.toSet());
                LocalDate end = internship.getEndDate().isBefore(through) ? internship.getEndDate() : through;
                for (LocalDate date = internship.getStartDate(); !date.isAfter(end); date = date.plusDays(1)) {
                    if (recorded.contains(date) || !isApplicable(internship, date, clock, policies, calendar)) continue;
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

    static boolean isApplicable(InternshipDetails internship, LocalDate date, Clock clock,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar) {
        var student = internship.getOnboarding().getStudent();
        return internship.getOnboarding().getWorkflowStatus() == WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE
                && student.isActive() && student.getRole() == Role.STUDENT
                && AttendanceSubmissionWindow.isClosed(date, clock)
                && !date.isBefore(internship.getStartDate()) && !date.isAfter(internship.getEndDate())
                && date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !calendar.isExcluded(student.getId(), date)
                && policies.resolve(internship, date).configurationReady();
    }
}
