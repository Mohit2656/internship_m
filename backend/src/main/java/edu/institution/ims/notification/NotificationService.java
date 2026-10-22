package edu.institution.ims.notification;

import edu.institution.ims.monthlyprogress.MonthlyProgressReport;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public NotificationService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    public record Notification(Long id, String type, String title, String message, String rejectionReason,
            String mentorName, String actionUrl, Instant createdAt, Instant readAt) {}
    public record Inbox(List<Notification> content, int page, long totalElements, int totalPages, long unreadCount) {}
    public record Count(long unreadCount) {}

    @Transactional
    public void reportRejected(MonthlyProgressReport report, User reviewer) {
        String mentor = jdbc.queryForList("select name from mentor_profiles where user_id=?", String.class, reviewer.getId())
                .stream().findFirst().orElse(reviewer.getEmail());
        create(report.getStudentInternship().getOnboarding().getStudent().getId(),
                "report-rejected:"+report.getId()+":"+report.getRevision(), "REPORT_REJECTED",
                "Monthly Progress Report - Month "+report.getReportMonthNumber()+" rejected",
                "Your faculty mentor rejected this report. Please correct the items in the rejection reason and resubmit.",
                report.getRejectionReason(), mentor, "/student/monthly-progress#report-"+report.getId(), report.getReviewedAt());
    }

    @Transactional
    public void create(Long studentId, String key, String type, String title, String message,
            String reason, String mentor, String url, Instant at) {
        // A database unique key handles both retries and concurrent scheduler instances.
        jdbc.update("insert into student_notifications(student_user_id,event_key,notification_type,title,message,"
                + "rejection_reason,mentor_name,action_url,created_at) values (?,?,?,?,?,?,?,?,?) on conflict do nothing",
                studentId,key,type,title,message,reason,mentor,url,Timestamp.from(at));
    }

    @Transactional(readOnly=true)
    public Inbox list(UserPrincipal principal, int page) {
        Long owner=student(principal); int safePage=Math.max(0,Math.min(page,1000000)); int size=30;
        long total=jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,owner);
        List<Notification> rows=jdbc.query("select * from student_notifications where student_user_id=? order by created_at desc,id desc limit ? offset ?",
                (rs,n) -> new Notification(rs.getLong("id"),rs.getString("notification_type"),rs.getString("title"),rs.getString("message"),
                        rs.getString("rejection_reason"),rs.getString("mentor_name"),rs.getString("action_url"),
                        rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant()),owner,size,safePage*size);
        return new Inbox(rows,safePage,total,(int)((total+size-1)/size),unread(principal).unreadCount());
    }
    @Transactional(readOnly=true)
    public Count unread(UserPrincipal principal) {
        return new Count(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and read_at is null",Long.class,student(principal)));
    }
    @Transactional
    public Count markRead(UserPrincipal principal, Long id) {
        Long owner=student(principal);
        int changed=jdbc.update("update student_notifications set read_at=coalesce(read_at,?) where id=? and student_user_id=?",Timestamp.from(clock.instant()),id,owner);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
        return unread(principal);
    }
    @Transactional
    public Count markAllRead(UserPrincipal principal) {
        jdbc.update("update student_notifications set read_at=? where student_user_id=? and read_at is null",Timestamp.from(clock.instant()),student(principal));
        return unread(principal);
    }
    private Long student(UserPrincipal principal) {
        if(principal==null || !principal.active() || !"STUDENT".equals(principal.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student access required");
        return principal.id();
    }
}
