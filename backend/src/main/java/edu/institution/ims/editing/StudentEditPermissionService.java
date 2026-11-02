package edu.institution.ims.editing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.WorkflowUserLocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentEditPermissionService {
    public record Authorization(boolean required, Long permissionId, Long studentId, EditPermissionScope scope) {
        static Authorization unrestricted(Long studentId, EditPermissionScope scope) {
            return new Authorization(false, null, studentId, scope);
        }
    }
    private record Row(Long id, Long assignmentId, String status, Instant expiresAt) {}

    private final JdbcTemplate jdbc;
    private final MentorStudentAssignmentRepository assignments;
    private final Clock clock;
    private final ObjectMapper json;

    public StudentEditPermissionService(JdbcTemplate jdbc, MentorStudentAssignmentRepository assignments,
            Clock clock, ObjectMapper json) {
        this.jdbc = jdbc; this.assignments = assignments; this.clock = clock; this.json = json;
    }

    /** Caller holds the workflow student lock for the remainder of its transaction. */
    public Authorization authorizeUpdate(Long studentId, EditPermissionScope scope) {
        return Authorization.unrestricted(studentId, scope);
    }

    public void consume(Authorization authorization, Object before, Object after) {
        if (!authorization.required()) return;
        Instant now = clock.instant();
        int changed = jdbc.update("update student_edit_permissions set status='CONSUMED',consumed_at=? "
                + "where id=? and status='ACTIVE' and expires_at>?", now, authorization.permissionId(), now);
        if (changed != 1) throw new EditPermissionException(HttpStatus.CONFLICT, "EDIT_PERMISSION_NO_LONGER_AVAILABLE",
                "Edit access was already used, revoked, or expired. Refresh and ask your mentor for new access.");
        audit(authorization.permissionId(), authorization.studentId(), authorization.scope(), "CONSUMED",
                authorization.studentId(), now, value(before), value(after));
    }

    @Transactional(readOnly = true)
    public List<EditPermissionResponse> forStudent(UserPrincipal principal) {
        requireStudent(principal);
        return responses(principal.id());
    }

    @Transactional(readOnly = true)
    public List<EditPermissionResponse> forMentor(Long studentId, UserPrincipal principal) {
        assigned(studentId, principal, false);
        return responses(studentId);
    }

    @Transactional
    public List<EditPermissionResponse> setForMentor(Long studentId, EditPermissionRequest request,
            UserPrincipal principal) {
        requireMentor(principal);
        if (request.scope() != EditPermissionScope.INTERNSHIP_DETAILS)
            throw new EditPermissionException(HttpStatus.BAD_REQUEST, "INVALID_EDIT_PERMISSION_SCOPE",
                    "Student profiles are always editable by their owner");
        if (!WorkflowUserLocks.student(jdbc, studentId, true)) throw forbidden();
        MentorStudentAssignment assignment = assigned(studentId, principal, true);
        if (!internshipStarted(studentId)) throw new EditPermissionException(HttpStatus.CONFLICT,
                "INTERNSHIP_NOT_STARTED", "One-time edit access is only required after the internship starts");
        Row row = locked(studentId, request.scope());
        Instant now = clock.instant();
        if (request.enabled()) {
            Instant expires = now.plus(Duration.ofHours(24));
            Long permissionId;
            if (row == null) {
                jdbc.update("insert into student_edit_permissions "
                        + "(student_user_id,scope,assignment_id,granted_by,granted_at,expires_at,status) "
                        + "values (?,?,?,?,?,?,'ACTIVE')", studentId,
                        request.scope().name(), assignment.getId(), principal.id(), now, expires);
                permissionId = locked(studentId, request.scope()).id();
            } else {
                permissionId = row.id();
                jdbc.update("update student_edit_permissions set assignment_id=?,granted_by=?,granted_at=?,"
                        + "expires_at=?,status='ACTIVE',consumed_at=null,revoked_at=null where id=?",
                        assignment.getId(), principal.id(), now, expires, permissionId);
            }
            audit(permissionId, studentId, request.scope(), "GRANTED", principal.id(), now, null, null);
        } else if (row != null && "ACTIVE".equals(row.status())) {
            jdbc.update("update student_edit_permissions set status='REVOKED',revoked_at=? where id=?", now, row.id());
            audit(row.id(), studentId, request.scope(), "REVOKED", principal.id(), now, null, null);
        }
        return responses(studentId);
    }

    private List<EditPermissionResponse> responses(Long studentId) {
        boolean started = internshipStarted(studentId);
        return List.of(response(studentId, EditPermissionScope.INTERNSHIP_DETAILS, started));
    }

    private EditPermissionResponse response(Long studentId, EditPermissionScope scope, boolean started) {
        return new EditPermissionResponse(scope, false, true, "NOT_REQUIRED", null);
    }

    private boolean internshipStarted(Long studentId) {
        return Boolean.TRUE.equals(jdbc.query("select i.start_date,exists(select 1 from student_edit_permissions p "
                + "where p.student_user_id=?) from internship_details i join internship_onboarding o "
                + "on o.id=i.onboarding_id where o.student_user_id=?", rs -> rs.next()
                        && (rs.getBoolean(2) || !rs.getObject(1, LocalDate.class).isAfter(LocalDate.now(clock))),
                studentId, studentId));
    }

    private Row locked(Long studentId, EditPermissionScope scope) {
        List<Row> rows = jdbc.query("select id,assignment_id,status,expires_at from student_edit_permissions "
                + "where student_user_id=? and scope=? for update", (rs, n) -> new Row(rs.getLong(1),
                        rs.getLong(2), rs.getString(3), rs.getObject(4, java.time.OffsetDateTime.class).toInstant()),
                studentId, scope.name());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean currentAssignment(Long assignmentId, Long studentId) {
        Integer count = jdbc.queryForObject("select count(*) from mentor_student_assignments where id=? "
                + "and student_user_id=? and active=true", Integer.class, assignmentId, studentId);
        return count != null && count == 1;
    }

    private MentorStudentAssignment assigned(Long studentId, UserPrincipal principal, boolean locked) {
        requireMentor(principal);
        return (locked ? assignments.findByStudentIdAndActiveTrueForLateAttendanceUpdate(studentId)
                : assignments.findByStudentIdAndActiveTrue(studentId))
                .filter(a -> a.getMentor().getId().equals(principal.id()) && a.getMentor().isActive())
                .orElseThrow(StudentEditPermissionService::forbidden);
    }

    private void audit(Long permissionId, Long studentId, EditPermissionScope scope, String event,
            Long actor, Instant at, String before, String after) {
        jdbc.update("insert into student_edit_permission_audit(permission_id,student_user_id,scope,event_type,"
                + "actor_user_id,occurred_at,before_value,after_value) values (?,?,?,?,?,?,?,?)",
                permissionId, studentId, scope.name(), event, actor, at, before, after);
    }

    private String value(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not record edit audit", e); }
    }

    private static void requireStudent(UserPrincipal principal) {
        if (principal == null || !principal.active() || !Role.STUDENT.name().equals(principal.role())) throw forbidden();
    }
    private static void requireMentor(UserPrincipal principal) {
        if (principal == null || !principal.active() || !Role.MENTOR.name().equals(principal.role())) throw forbidden();
    }
    private static EditPermissionException forbidden() {
        return new EditPermissionException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ASSIGNED",
                "This student is not assigned to the authenticated mentor");
    }
}
