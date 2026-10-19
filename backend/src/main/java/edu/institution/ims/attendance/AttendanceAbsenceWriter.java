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

    public AttendanceAbsenceWriter(AttendanceRepository attendance, InternshipDetailsRepository internships,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar, Clock clock) {
        this.attendance = attendance;
        this.internships = internships;
        this.policies = policies;
        this.calendar = calendar;
        this.clock = clock;
    }

    // Isolate each insert so a concurrent submission/worker conflict cannot roll back other days.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createIfMissing(Long internshipId, LocalDate date) {
        InternshipDetails internship = internships.findById(internshipId).orElse(null);
        if (internship == null || !AttendanceAbsenceService.isApplicable(internship, date, clock, policies, calendar)
                || attendance.countIncludingDeleted(internshipId, date) > 0) return false;
        attendance.saveAndFlush(AttendanceRecord.automaticAbsent(internship.getOnboarding().getStudent(),
                internship, date, clock.instant()));
        return true;
    }
}
