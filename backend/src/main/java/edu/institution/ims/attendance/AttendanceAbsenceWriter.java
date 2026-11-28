package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service
public class AttendanceAbsenceWriter {
    private final AttendanceRepository attendance;
    private final InternshipDetailsRepository internships;
    private final AttendancePolicyService policies;
    private final AttendanceCalendarProperties calendar;
    private final Clock clock;
    private final AttendanceDailyExceptionRepository exceptions;

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceAbsenceWriter(AttendanceRepository attendance, InternshipDetailsRepository internships,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar, Clock clock,
            AttendanceDailyExceptionRepository exceptions) {
        this.attendance = attendance;
        this.internships = internships;
        this.policies = policies;
        this.calendar = calendar;
        this.clock = clock;
        this.exceptions = exceptions;
    }

    public AttendanceAbsenceWriter(AttendanceRepository attendance, InternshipDetailsRepository internships,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar, Clock clock) {
        this(attendance, internships, policies, calendar, clock, null);
    }

    // Isolate each insert so a concurrent submission/worker conflict cannot roll back other days.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createIfMissing(Long internshipId, LocalDate date) {
        return createIfMissing(internshipId, date, 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createIfMissing(Long internshipId, LocalDate date, int slot) {
        if (slot < 1 || slot > AttendanceAbsenceService.DAILY_SUBMISSION_SLOTS) return false;
        InternshipDetails internship = internships.findById(internshipId).orElse(null);
        if (internship == null || !AttendanceAbsenceService.isApplicable(internship, date, clock, policies, calendar, exceptions)
                || attendance.countIncludingDeleted(internshipId, date) >= AttendanceAbsenceService.DAILY_SUBMISSION_SLOTS) {
            return false;
        }
        var activeSlots = attendance.findActiveSlots(internshipId, date);
        if (activeSlots != null && activeSlots.contains(slot)) return false;
        attendance.saveAndFlush(AttendanceRecord.automaticAbsent(internship.getOnboarding().getStudent(),
                internship, date, clock.instant(), slot));
        return true;
    }
}
