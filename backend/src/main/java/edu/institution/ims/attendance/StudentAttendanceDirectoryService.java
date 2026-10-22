package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudentAttendanceDirectoryService {
    private final UserRepository users;
    private final MentorStudentAssignmentRepository assignments;
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final AttendanceCalendarProperties calendar;
    private final Clock clock;

    public StudentAttendanceDirectoryService(UserRepository users, MentorStudentAssignmentRepository assignments,
            InternshipDetailsRepository internships, AttendanceRepository attendance, AttendancePolicyService policies,
            AttendanceCalendarProperties calendar, Clock clock) {
        this.users = users; this.assignments = assignments; this.internships = internships;
        this.attendance = attendance; this.policies = policies; this.calendar = calendar; this.clock = clock;
    }

    public record Totals(long presentDays, long absentDays, long totalDays, BigDecimal percentage) {}
    public record StudentRow(Long studentUserId, String uid, String studentName, String branch,
            String facultyMentor, String company, String workflowStatus, Totals attendance) {}
    public record History(Long studentUserId, String attendanceZone, Totals totals, List<AttendanceResponse> records) {}

    public List<StudentRow> students(UserPrincipal principal) {
        requireRole(principal);
        List<MentorStudentAssignment> current = principal.role().equals("ADMIN") ? assignments.findAllByActiveTrue()
                : assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id());
        // Start from accounts/active assignments, never from a one-to-many attendance join.
        Map<Long, User> unique = new LinkedHashMap<>();
        if (principal.role().equals("ADMIN")) users.findAllByRoleOrderByEmailAsc(Role.STUDENT)
                .forEach(student -> unique.put(student.getId(), student));
        else current.forEach(assignment -> unique.put(assignment.getStudent().getId(), assignment.getStudent()));
        if (unique.isEmpty()) return List.of();
        Map<Long, String> mentors = new HashMap<>();
        current.forEach(a -> mentors.put(a.getStudent().getId(), a.getMentor().getEmail()));
        Map<Long, InternshipDetails> details = new HashMap<>();
        internships.findAllByOnboardingStudentIdIn(unique.keySet()).forEach(i -> details.putIfAbsent(i.getOnboarding().getStudent().getId(), i));
        Map<Long, List<AttendanceRecord>> records = attendance.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(unique.keySet())
                .stream().collect(Collectors.groupingBy(r -> r.getStudent().getId()));
        return unique.values().stream().map(student -> {
            var profile = student.getStudentProfile(); var account = student.getStudentAccount();
            var internship = details.get(student.getId()); var onboarding = student.getInternshipOnboarding();
            return new StudentRow(student.getId(), profile != null ? profile.getUid() : account == null ? null : account.getUid(),
                    profile != null ? profile.getStudentName() : account == null ? null : account.getStudentName(),
                    profile == null ? null : profile.getBranch().getLabel(), mentors.get(student.getId()),
                    internship == null ? null : internship.getCompanyName(),
                    onboarding == null ? "NOT_SECURED" : onboarding.getWorkflowStatus().name(),
                    totals(records.getOrDefault(student.getId(), List.of())));
        }).toList();
    }

    public History history(UserPrincipal principal, Long studentId) {
        requireRole(principal);
        if (principal.role().equals("MENTOR") && assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(a -> a.getMentor().getId().equals(principal.id())).isEmpty())
            throw new AttendanceException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ASSIGNED", "This student is not assigned to you");
        users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(() ->
                new AttendanceException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student not found"));
        List<AttendanceRecord> records = attendance.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(List.of(studentId));
        return new History(studentId, clock.getZone().getId(), totals(records), records.stream().map(AttendanceService::response).toList());
    }

    Totals totals(List<AttendanceRecord> records) {
        Map<LocalDate, AttendanceResult> days = new HashMap<>();
        for (AttendanceRecord record : records) {
            LocalDate date = record.getAttendanceDate(); var internship = record.getInternship();
            if (date.isAfter(LocalDate.now(clock)) || date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate())
                    || date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY
                    || calendar.isExcluded(record.getStudent().getId(), date) || !policies.resolve(internship, date).configurationReady()) continue;
            // A day counts once even if legacy related entries contain that day more than once.
            days.merge(date, record.getAttendanceResult(), (a, b) -> a == AttendanceResult.PRESENT || b == AttendanceResult.PRESENT
                    ? AttendanceResult.PRESENT : AttendanceResult.ABSENT);
        }
        long present = days.values().stream().filter(r -> r == AttendanceResult.PRESENT).count();
        return new Totals(present, days.size() - present, days.size(), days.isEmpty() ? null :
                BigDecimal.valueOf(present * 100).divide(BigDecimal.valueOf(days.size()), 1, RoundingMode.HALF_UP));
    }

    private void requireRole(UserPrincipal principal) {
        if (principal == null || !principal.active() || !(principal.role().equals("ADMIN") || principal.role().equals("MENTOR")))
            throw new AttendanceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin or faculty mentor access required");
    }
}
