package edu.institution.ims.attendance;

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
    private final UserRepository users;
    private final Clock clock;

    public LateAttendancePermissionService(LateAttendancePermissionRepository permissions,
            LateAttendancePermissionAuditRepository audit, MentorStudentAssignmentRepository assignments,
            UserRepository users, Clock clock) {
        this.permissions = permissions;
        this.audit = audit;
        this.assignments = assignments;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long studentId, LocalDate date) {
        return activePermission(studentId, date).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<LateAttendancePermission> activePermission(Long studentId, LocalDate date) {
        if (!LocalDate.now(clock).equals(date)) return Optional.empty();
        return permissions.findByStudentIdAndAttendanceDate(studentId, date)
                .filter(permission -> permission.isEnabled() && permission.getAssignment().isActive());
    }

    @Transactional(readOnly = true)
    public List<LateAttendancePermissionResponse> forMentor(UserPrincipal principal) {
        requireMentor(principal);
        LocalDate today = LocalDate.now(clock);
        List<MentorStudentAssignment> current = assignments
                .findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id());
        if (current.isEmpty()) return List.of();
        List<Long> studentIds = current.stream().map(value -> value.getStudent().getId()).toList();
        Map<Long, LateAttendancePermission> byStudent = permissions
                .findAllByStudentIdInAndAttendanceDate(studentIds, today).stream()
                .collect(Collectors.toMap(value -> value.getStudent().getId(), Function.identity()));
        return current.stream().filter(a -> a.getStudent().isActive()).map(assignment -> response(assignment,
                byStudent.get(assignment.getStudent().getId()), today)).toList();
    }

    @Transactional
    public LateAttendancePermissionResponse setForMentor(Long studentId, boolean enabled,
            UserPrincipal principal) {
        requireMentor(principal);
        // Attendance submission and assignment changes take the same student lock first.
        users.findByIdForAttendanceUpdate(studentId)
                .filter(u->u.getRole()==Role.STUDENT && u.isActive())
                .orElseThrow(LateAttendancePermissionService::notAssigned);
        MentorStudentAssignment assignment = assignments
                .findByStudentIdAndActiveTrueForLateAttendanceUpdate(studentId)
                .filter(value -> value.getMentor().getId().equals(principal.id())
                        && value.getMentor().getRole() == Role.MENTOR && value.getMentor().isActive())
                .orElseThrow(LateAttendancePermissionService::notAssigned);
        LocalDate today = LocalDate.now(clock);
        LateAttendancePermission permission = permissions
                .findByStudentIdAndAttendanceDate(studentId, today).orElse(null);
        boolean currentlyEnabled = isCurrentPermission(permission, assignment) && permission.isEnabled();
        if (currentlyEnabled == enabled) return response(assignment, permission, today);

        Instant now = clock.instant();
        if (enabled) {
            if (permission == null) {
                permission = permissions.save(new LateAttendancePermission(assignment, today, now));
            } else {
                permission.enable(assignment, now);
            }
        } else {
            permission.revoke();
        }
        audit.save(new LateAttendancePermissionAudit(permission, assignment, enabled, now));
        return response(assignment, permission, today);
    }

    private LateAttendancePermissionResponse response(MentorStudentAssignment assignment,
            LateAttendancePermission permission, LocalDate day) {
        User student = assignment.getStudent();
        StudentProfile profile = student.getStudentProfile();
        StudentAccount account = student.getStudentAccount();
        boolean current = isCurrentPermission(permission, assignment);
        return new LateAttendancePermissionResponse(student.getId(),
                profile == null ? account == null ? null : account.getStudentName() : profile.getStudentName(),
                profile == null ? account == null ? null : account.getUid() : profile.getUid(),
                day, current && permission.isEnabled(),
                current ? permission.getAuthorizedBy().getId() : null,
                current ? permission.getAuthorizedAt() : null, clock.getZone().getId());
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
