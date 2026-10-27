package edu.institution.ims.notification;

import edu.institution.ims.attendance.AttendanceRecord;
import edu.institution.ims.attendance.AttendanceResult;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Clock;

/** A durable mentor inbox with the same ownership boundary as attendance history. */
@Service
public class MentorNotificationService {
    private static final int PAGE_SIZE = 30;
    // Reassignment, mentor deactivation and attendance deletion revoke inbox visibility too.
    private static final String VISIBLE = "mentor_user_id=? and exists (select 1 from mentor_student_assignments a "
            + "where a.mentor_user_id=mentor_notifications.mentor_user_id "
            + "and a.student_user_id=mentor_notifications.student_user_id and a.active=true) "
            + "and exists (select 1 from attendance_records r where r.id=mentor_notifications.attendance_id and r.deleted_at is null)";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MentorNotificationService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    // The caller's transaction includes attendance, evidence, audit and this event. A failure
    // later in submission rolls back the notification, and the unique key handles retries.
    @Transactional(propagation = Propagation.MANDATORY)
    public void attendanceSubmitted(AttendanceRecord record) {
        if (record.isAutomaticAbsent() || record.getAttendanceResult() != AttendanceResult.PRESENT) return;
        jdbc.update("insert into mentor_notifications(mentor_user_id,student_user_id,attendance_id,"
                + "notification_type,title,message,action_url,created_at) "
                + "select a.mentor_user_id, s.id, ?, 'ATTENDANCE_SUBMITTED', 'Attendance submitted', "
                + "coalesce(p.student_name, sa.student_name, 'Student') || ' submitted attendance for ' || ?, "
                + "'/mentor/attendance', ? from mentor_student_assignments a "
                + "join users m on m.id=a.mentor_user_id and m.role='MENTOR' and m.active=true "
                + "join users s on s.id=a.student_user_id and s.role='STUDENT' and s.active=true "
                + "left join student_profiles p on p.user_id=s.id left join student_accounts sa on sa.user_id=s.id "
                + "where a.student_user_id=? and a.active=true on conflict do nothing",
                record.getId(), record.getAttendanceDate().toString(), Timestamp.from(record.getServerSubmittedAt()),
                record.getStudent().getId());
    }

    @Transactional(readOnly = true)
    public NotificationService.Inbox list(UserPrincipal principal, int page) {
        Long owner = mentor(principal);
        int safePage = Math.max(0, Math.min(page, 1_000_000));
        long total = jdbc.queryForObject("select count(*) from mentor_notifications where " + VISIBLE, Long.class, owner);
        var rows = jdbc.query("select * from mentor_notifications where " + VISIBLE
                        + " order by created_at desc,id desc limit ? offset ?",
                (rs, n) -> new NotificationService.Notification(rs.getLong("id"), rs.getString("notification_type"),
                        rs.getString("title"), rs.getString("message"), null, null, rs.getString("action_url"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                owner, PAGE_SIZE, safePage * PAGE_SIZE);
        return new NotificationService.Inbox(rows, safePage, total, (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE),
                unread(principal).unreadCount());
    }

    @Transactional(readOnly = true)
    public NotificationService.Count unread(UserPrincipal principal) {
        return new NotificationService.Count(jdbc.queryForObject("select count(*) from mentor_notifications where "
                + VISIBLE + " and read_at is null", Long.class, mentor(principal)));
    }

    @Transactional
    public NotificationService.Count markRead(UserPrincipal principal, Long id) {
        Long owner = mentor(principal);
        int changed = jdbc.update("update mentor_notifications set read_at=coalesce(read_at,?) where "
                + VISIBLE + " and id=?", Timestamp.from(clock.instant()), owner, id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        return unread(principal);
    }

    @Transactional
    public NotificationService.Count markAllRead(UserPrincipal principal) {
        jdbc.update("update mentor_notifications set read_at=? where " + VISIBLE + " and read_at is null",
                Timestamp.from(clock.instant()), mentor(principal));
        return unread(principal);
    }

    private Long mentor(UserPrincipal principal) {
        if (principal == null || !principal.active() || !"MENTOR".equals(principal.role())
                || Boolean.FALSE.equals(jdbc.queryForObject("select exists(select 1 from users where id=? and role='MENTOR' and active=true)",
                        Boolean.class, principal.id())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Mentor access required");
        return principal.id();
    }
}
