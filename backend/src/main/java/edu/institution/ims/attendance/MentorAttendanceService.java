package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.StudentAccount;
import edu.institution.ims.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MentorAttendanceService {
    private final MentorStudentAssignmentRepository assignments;
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final Clock clock;

    public MentorAttendanceService(MentorStudentAssignmentRepository assignments,
            InternshipDetailsRepository internships, AttendanceRepository attendance,
            AttendancePolicyService policies, Clock clock) {
        this.assignments = assignments;
        this.internships = internships;
        this.attendance = attendance;
        this.policies = policies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MentorStudentAttendanceResponse> today(UserPrincipal principal) {
        List<MentorStudentAssignment> current =
                assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id());
        if (current.isEmpty()) return List.of();
        List<Long> studentIds = current.stream().map(a -> a.getStudent().getId()).toList();
        List<InternshipDetails> studentInternships = internships.findAllByOnboardingStudentIdIn(studentIds);
        Map<Long, AttendanceRecord> attendanceByInternship = new HashMap<>();
        attendance.findAllByStudentIdInAndAttendanceDate(studentIds, LocalDate.now(clock))
                .stream().filter(a -> a.getInternship() != null)
                .forEach(a -> attendanceByInternship.putIfAbsent(a.getInternship().getId(), a));

        LocalDate today = LocalDate.now(clock);
        Map<Long,User> assignedStudents=current.stream().collect(Collectors.toMap(a->a.getStudent().getId(),MentorStudentAssignment::getStudent));
        return studentInternships.stream()
                .filter(i -> assignedStudents.containsKey(i.getOnboarding().getStudent().getId()))
                .sorted(Comparator.comparing((InternshipDetails i)->i.getOnboarding().getStudent().getEmail())
                        .thenComparing(i->i.getOnboarding().getInternshipNumber()))
                .map(i -> response(assignedStudents.get(i.getOnboarding().getStudent().getId()),i,
                        attendanceByInternship.get(i.getId()),today)).toList();
    }

    private MentorStudentAttendanceResponse response(User student, InternshipDetails internship,
            AttendanceRecord attendance, LocalDate today) {
        StudentProfile profile = student.getStudentProfile();
        StudentAccount account = student.getStudentAccount();
        return new MentorStudentAttendanceResponse(student.getId(), internship.getId(), internship.getOnboarding().getInternshipNumber(),
                profile == null ? account == null ? null : account.getStudentName() : profile.getStudentName(),
                profile == null ? account == null ? null : account.getUid() : profile.getUid(),
                profile == null ? null : profile.getBranch().getLabel(),
                profile == null ? account == null ? null : account.getSemester() : profile.getSemester(),
                internship == null ? null : internship.getCompanyName(),
                internship == null ? null : internship.getInternshipMode().name(),
                internship == null ? null : internship.getInternshipMode().getLabel(),
                internship == null ? null : policies.resolve(internship, today).todayWorkMode(),
                attendance == null ? null : AttendanceService.response(attendance));
    }
}
