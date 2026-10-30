package edu.institution.ims.attendance;

import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

@Service
public class AttendanceReviewService {
    public record Decision(AttendanceReviewStatus status, Integer revision, String reason) {}
    public record Audit(int revision, AttendanceReviewStatus previousStatus, AttendanceReviewStatus status,
            Long reviewerId, String reviewerName, Instant reviewedAt, String reason) {}
    private final AttendanceRepository attendance;
    private final AttendanceReviewRepository reviews;
    private final MentorStudentAssignmentRepository assignments;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;

    public AttendanceReviewService(AttendanceRepository attendance, AttendanceReviewRepository reviews,
            MentorStudentAssignmentRepository assignments, UserRepository users, JdbcTemplate jdbc,
            NotificationService notifications, Clock clock) {
        this.attendance=attendance; this.reviews=reviews; this.assignments=assignments; this.users=users;
        this.jdbc=jdbc; this.notifications=notifications; this.clock=clock;
    }

    @Transactional
    public AttendanceResponse review(Long id, UserPrincipal principal, Decision request) {
        if (principal == null || !principal.active() || !"MENTOR".equals(principal.role()))
            throw error(HttpStatus.FORBIDDEN,"MENTOR_REQUIRED","An active faculty mentor is required");
        if (request == null || request.revision() == null || request.status() == null
                || request.status() == AttendanceReviewStatus.PENDING || request.status() == AttendanceReviewStatus.NOT_APPLICABLE)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_ATTENDANCE_REVIEW","Choose approve, reject, or manual review and include the current revision");
        String reason=request.reason()==null?null:request.reason().strip();
        if (reason != null && reason.isEmpty()) reason=null;
        if ((request.status()!=AttendanceReviewStatus.APPROVED && (reason==null || reason.length()<5))
                || (reason!=null && reason.length()>1000))
            throw error(HttpStatus.BAD_REQUEST,"REVIEW_REASON_REQUIRED","Provide a reason between 5 and 1000 characters for rejection or manual review");
        // Match assignment changes: mentor shared lock, student write lock, assignment, record.
        User actor=users.findByIdForWorkflowRead(principal.id()).filter(u -> u.isActive() && u.getRole()==Role.MENTOR)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN,"MENTOR_REQUIRED","An active faculty mentor is required"));
        Long studentId=attendance.findStudentIdForReview(id).orElseThrow(AttendanceReviewService::missing);
        if (!WorkflowUserLocks.student(jdbc,studentId,false)) throw missing();
        var assignment=assignments.findByStudentIdAndActiveTrueForLateAttendanceUpdate(studentId)
                .filter(a -> a.getMentor().getId().equals(actor.getId())).orElseThrow(AttendanceReviewService::missing);
        AttendanceRecord record=attendance.findForReviewUpdate(id).orElseThrow(AttendanceReviewService::missing);
        if (record.isAutomaticAbsent() || record.getAttendanceResult()!=AttendanceResult.PRESENT)
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_NOT_SUBMITTED","Only student-submitted present attendance can be reviewed");
        AttendanceReviewResponse previous=AttendanceReviewResponse.from(record);
        if (request.revision()!=previous.revision())
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_REVIEW_CHANGED","This attendance review changed. Reload before reviewing it");
        if (previous.status()==AttendanceReviewStatus.APPROVED || previous.status()==AttendanceReviewStatus.REJECTED
                || previous.status()==request.status())
            throw error(HttpStatus.CONFLICT,"INVALID_REVIEW_TRANSITION","This attendance decision cannot be repeated or changed");
        if (request.status()==AttendanceReviewStatus.APPROVED && !AttendanceReviewResponse.approvalAllowed(record))
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_VERIFICATION_REQUIRED","Approval requires the original GPS/workplace checks and any required liveness verification to pass. Flag this entry for manual review instead");
        String name=jdbc.queryForList("select name from mentor_profiles where user_id=?",String.class,actor.getId())
                .stream().findFirst().orElse(actor.getEmail());
        Instant at=clock.instant();
        AttendanceReview review=record.getReview()==null?new AttendanceReview(record):record.getReview();
        review.decide(request.status(),actor.getId(),name,at,reason);
        reviews.saveAndFlush(review);
        jdbc.update("insert into attendance_review_audit(attendance_id,assignment_id,revision,previous_status,status,reviewer_id,reviewer_name,reviewed_at,reason) values (?,?,?,?,?,?,?,?,?)",
                id,assignment.getId(),review.getRevision(),previous.status().name(),review.getStatus().name(),actor.getId(),name,Timestamp.from(at),reason);
        String action=request.status()==AttendanceReviewStatus.APPROVED?"approved":request.status()==AttendanceReviewStatus.REJECTED?"rejected":"requested manual review of";
        String message="Your faculty mentor "+action+" your attendance for "+record.getAttendanceDate()+"."
                +(request.status()==AttendanceReviewStatus.MANUAL_REVIEW?" Contact your mentor about the requested review; original submission remains available.":"");
        notifications.create(studentId,"attendance-review:"+id+":"+review.getRevision(),"ATTENDANCE_"+request.status().name(),
                request.status()==AttendanceReviewStatus.MANUAL_REVIEW?"Attendance requires manual review":"Attendance "+action,
                message,reason,name,"/student/dashboard#daily-attendance",at);
        return AttendanceService.response(record);
    }

    @Transactional(readOnly=true)
    public List<Audit> history(Long id, UserPrincipal principal) {
        if (principal==null || !principal.active()) throw missing();
        Long studentId=attendance.findStudentIdForReview(id).orElseThrow(AttendanceReviewService::missing);
        boolean allowed="ADMIN".equals(principal.role()) || ("STUDENT".equals(principal.role()) && principal.id().equals(studentId))
                || ("MENTOR".equals(principal.role()) && assignments.findByStudentIdAndActiveTrue(studentId)
                    .filter(a -> a.getMentor().getId().equals(principal.id())).isPresent());
        if (!allowed) throw missing();
        return jdbc.query("select * from attendance_review_audit where attendance_id=? order by revision desc",(rs,n) ->
                new Audit(rs.getInt("revision"),AttendanceReviewStatus.valueOf(rs.getString("previous_status")),
                        AttendanceReviewStatus.valueOf(rs.getString("status")),rs.getLong("reviewer_id"),rs.getString("reviewer_name"),
                        rs.getTimestamp("reviewed_at").toInstant(),rs.getString("reason")),id);
    }
    private static AttendanceException missing() { return error(HttpStatus.NOT_FOUND,"ATTENDANCE_NOT_FOUND","Attendance was not found in your assigned scope"); }
    private static AttendanceException error(HttpStatus status,String code,String message) { return new AttendanceException(status,code,message); }
}
