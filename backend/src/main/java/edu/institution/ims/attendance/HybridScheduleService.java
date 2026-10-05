package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class HybridScheduleService {
    private final HybridWorkScheduleRepository schedules;
    private final InternshipDetailsRepository internships;
    private final MentorStudentAssignmentRepository assignments;
    private final Clock clock;

    public HybridScheduleService(HybridWorkScheduleRepository schedules,
            InternshipDetailsRepository internships, MentorStudentAssignmentRepository assignments, Clock clock) {
        this.schedules = schedules;
        this.internships = internships;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HybridScheduleResponse getForAdmin(Long studentId) { return response(hybridInternship(studentId)); }

    @Transactional
    public HybridScheduleResponse updateForAdmin(Long studentId, HybridScheduleRequest request) {
        InternshipDetails internship = hybridInternship(studentId);
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (HybridScheduleEntryRequest entry : request.entries()) {
            if (!days.add(entry.dayOfWeek())) {
                throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_SCHEDULE_DAY", "Each day may appear only once");
            }
        }
        schedules.deleteAllByInternshipId(internship.getId());
        Instant now = clock.instant();
        schedules.saveAllAndFlush(request.entries().stream()
                .map(entry -> new HybridWorkSchedule(internship, entry.dayOfWeek(), entry.workMode(), now)).toList());
        return response(internship);
    }

    @Transactional(readOnly = true)
    public HybridScheduleResponse getOwn(UserPrincipal principal) { return response(hybridInternship(principal.id())); }

    @Transactional(readOnly = true)
    public HybridScheduleResponse getForMentor(Long studentId, UserPrincipal principal) {
        MentorStudentAssignment assignment = assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(value -> value.getMentor().getId().equals(principal.id()))
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_NOT_ASSIGNED",
                        "This student is not assigned to the authenticated mentor"));
        return response(hybridInternship(assignment.getStudent().getId()));
    }

    private InternshipDetails hybridInternship(Long studentId) {
        InternshipDetails internship = internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "INTERNSHIP_DETAILS_NOT_FOUND",
                        "Internship details were not found"));
        if (internship.getInternshipMode() != InternshipMode.HYBRID) {
            throw error(HttpStatus.CONFLICT, "HYBRID_INTERNSHIP_REQUIRED",
                    "A hybrid schedule is available only for a hybrid internship");
        }
        return internship;
    }

    private HybridScheduleResponse response(InternshipDetails internship) {
        List<HybridScheduleEntryResponse> entries = schedules.findAllByInternshipId(internship.getId()).stream()
                .sorted(Comparator.comparingInt(entry -> entry.getDayOfWeek().getValue()))
                .map(entry -> new HybridScheduleEntryResponse(entry.getDayOfWeek(), entry.getWorkMode())).toList();
        return new HybridScheduleResponse(internship.getId(), entries);
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
