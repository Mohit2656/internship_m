package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.internship.InternshipLifecycle;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.StudentAccount;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LateAttendancePermissionService {
    private final LateAttendancePermissionRepository permissions;
    private final LateAttendancePermissionAuditRepository audit;
    private final MentorStudentAssignmentRepository assignments;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final Clock clock;

    public LateAttendancePermissionService(LateAttendancePermissionRepository permissions,
            LateAttendancePermissionAuditRepository audit, MentorStudentAssignmentRepository assignments,
            InternshipDetailsRepository internships, UserRepository users, Clock clock) {
        this.permissions = permissions;
        this.audit = audit;
        this.assignments = assignments;
        this.internships = internships;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long studentId, Long internshipId, LocalDate date) {
        return activePermission(studentId, internshipId, date).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long studentId, LocalDate date) {
        return isEnabled(studentId, null, date);
    }

    @Transactional(readOnly = true)
    public Optional<LateAttendancePermission> activePermission(Long studentId, Long internshipId, LocalDate date) {
        LocalDate today = InternshipLifecycle.today(clock);
        if (date == null || !date.equals(today)) return Optional.empty();
        if (studentId == null) return Optional.empty();
        Long targetInternshipId = internshipId;
        if (targetInternshipId == null) {
            targetInternshipId = defaultInternshipId(studentId);
            if (targetInternshipId == null) return Optional.empty();
        }
        return permissions.findByStudentIdAndInternshipIdAndAttendanceDate(studentId, targetInternshipId, date)
                .filter(permission -> permission.isEnabled()
                        && permission.getAssignment().isActive()
                        && permission.getAttendanceDate().equals(date)
                        && permission.getAttendanceDate().equals(today)
                        && (internshipId == null || (permission.getInternship() != null && permission.getInternship().getId().equals(internshipId))));
    }

    @Transactional(readOnly = true)
    public Optional<LateAttendancePermission> activePermission(Long studentId, LocalDate date) {
        return activePermission(studentId, null, date);
    }

    private Long defaultInternshipId(Long studentId) {
        return internships.findByOnboardingStudentId(studentId)
                .map(InternshipDetails::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LateAttendancePermissionResponse> forMentor(UserPrincipal principal) {
        requireMentor(principal);
        LocalDate today = InternshipLifecycle.today(clock);
        List<MentorStudentAssignment> current = assignments
                .findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id());
        if (current.isEmpty()) return List.of();
        List<Long> studentIds = current.stream().map(value -> value.getStudent().getId()).toList();
        Map<Long, User> assignedStudents = current.stream().filter(a -> a.getStudent().isActive())
                .collect(Collectors.toMap(a -> a.getStudent().getId(), MentorStudentAssignment::getStudent));

        List<InternshipDetails> allInternships = internships.findAllByOnboardingStudentIdIn(studentIds);
        Map<String, LateAttendancePermission> byStudentAndInternship = permissions
                .findAllByStudentIdInAndAttendanceDate(studentIds, today).stream()
                .filter(p -> p.getInternship() != null)
                .collect(Collectors.toMap(
                        p -> p.getStudent().getId() + "_" + p.getInternship().getId(),
                        Function.identity(),
                        (a, b) -> a));

        Map<Long, List<InternshipDetails>> internshipsByStudent = allInternships.stream()
                .filter(i -> assignedStudents.containsKey(i.getOnboarding().getStudent().getId()))
                .collect(Collectors.groupingBy(i -> i.getOnboarding().getStudent().getId()));

        List<LateAttendancePermissionResponse> result = new ArrayList<>();
        for (MentorStudentAssignment assignment : current) {
            User student = assignment.getStudent();
            if (!student.isActive()) continue;
            List<InternshipDetails> studentInternshipList = internshipsByStudent.getOrDefault(student.getId(), List.of());
            if (studentInternshipList.isEmpty()) continue;

            boolean hasApprovedSecondInternship = studentInternshipList.stream()
                    .anyMatch(i -> i.getOnboarding().getInternshipNumber() == 2);

            List<InternshipDetails> targetList = studentInternshipList.stream()
                    .filter(i -> i.getOnboarding().getInternshipNumber() == 1
                            || (hasApprovedSecondInternship && i.getOnboarding().getInternshipNumber() == 2))
                    .sorted(Comparator.comparingInt(i -> i.getOnboarding().getInternshipNumber()))
                    .toList();

            for (InternshipDetails internship : targetList) {
                LateAttendancePermission permission = byStudentAndInternship.get(student.getId() + "_" + internship.getId());
                result.add(response(assignment, internship, permission, today));
            }
        }
        return result;
    }

    @Transactional
    public LateAttendancePermissionResponse setForMentor(Long studentId, Long internshipId, boolean enabled,
            UserPrincipal principal) {
        requireMentor(principal);
        // Attendance submission and assignment changes take the same student lock first.
        users.findByIdForAttendanceUpdate(studentId)
                .filter(u -> u.getRole() == Role.STUDENT && u.isActive())
                .orElseThrow(LateAttendancePermissionService::notAssigned);
        MentorStudentAssignment assignment = assignments
                .findByStudentIdAndActiveTrueForLateAttendanceUpdate(studentId)
                .filter(value -> value.getMentor().getId().equals(principal.id())
                        && value.getMentor().getRole() == Role.MENTOR && value.getMentor().isActive())
                .orElseThrow(LateAttendancePermissionService::notAssigned);

        InternshipDetails targetInternship = (internshipId != null
                ? internships.findByIdAndOnboardingStudentId(internshipId, studentId)
                : internships.findByOnboardingStudentId(studentId))
                .orElseThrow(() -> new AttendanceException(HttpStatus.CONFLICT, "INTERNSHIP_NOT_FOUND",
                        "The selected internship is not found for this student"));

        LocalDate today = InternshipLifecycle.today(clock);
        LateAttendancePermission permission = permissions
                .findByStudentIdAndInternshipIdAndAttendanceDate(studentId, targetInternship.getId(), today).orElse(null);
        boolean currentlyEnabled = isCurrentPermission(permission, assignment) && permission.isEnabled();
        if (currentlyEnabled == enabled) return response(assignment, targetInternship, permission, today);

        Instant now = clock.instant();
        if (enabled) {
            if (permission == null) {
                permission = permissions.save(new LateAttendancePermission(assignment, targetInternship, today, now));
            } else {
                permission.enable(assignment, now);
            }
        } else {
            permission.revoke();
        }
        audit.save(new LateAttendancePermissionAudit(permission, assignment, targetInternship, enabled, now));
        return response(assignment, targetInternship, permission, today);
    }

    @Transactional
    public LateAttendancePermissionResponse setForMentor(Long studentId, boolean enabled, UserPrincipal principal) {
        return setForMentor(studentId, null, enabled, principal);
    }

    private LateAttendancePermissionResponse response(MentorStudentAssignment assignment,
            InternshipDetails internship, LateAttendancePermission permission, LocalDate day) {
        User student = assignment.getStudent();
        StudentProfile profile = student.getStudentProfile();
        StudentAccount account = student.getStudentAccount();
        boolean current = isCurrentPermission(permission, assignment);
        return new LateAttendancePermissionResponse(student.getId(),
                internship.getId(),
                internship.getOnboarding().getInternshipNumber(),
                internship.getCompanyName(),
                profile == null ? account == null ? null : account.getStudentName() : profile.getStudentName(),
                profile == null ? account == null ? null : account.getUid() : profile.getUid(),
                day, current && permission.isEnabled(),
                current ? permission.getAuthorizedBy().getId() : null,
                current ? permission.getAuthorizedAt() : null, InternshipLifecycle.INSTITUTIONAL_ZONE.getId());
    }

    private static boolean isCurrentPermission(LateAttendancePermission permission,
            MentorStudentAssignment assignment) {
        return permission != null && permission.getAssignment().isActive()
                && permission.getAssignment().getId().equals(assignment.getId());
    }

    private static void requireMentor(UserPrincipal principal) {
        if (principal == null || !principal.active() || !Role.MENTOR.name().equals(principal.role())) {
            throw new AttendanceException(HttpStatus.FORBIDDEN, "MENTOR_REQUIRED",
                    "Only an authenticated faculty mentor may manage late attendance permission");
        }
    }

    private static AttendanceException notAssigned() {
        return new AttendanceException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ASSIGNED",
                "This student is not assigned to the authenticated mentor");
    }
}
