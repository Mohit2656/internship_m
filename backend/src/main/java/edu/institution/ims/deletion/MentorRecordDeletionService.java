package edu.institution.ims.deletion;

import edu.institution.ims.security.UserPrincipal;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MentorRecordDeletionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public MentorRecordDeletionService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    public void delete(UserPrincipal user, Long id, boolean monthly, String reason) {
        if (user == null || !user.active() || !"MENTOR".equals(user.role())) throw failure(HttpStatus.FORBIDDEN, "Faculty Mentor role is required");
        if (reason == null || reason.strip().length()<5 || reason.strip().length()>500) throw failure(HttpStatus.BAD_REQUEST, "Provide a deletion reason between 5 and 500 characters");
        String table=monthly ? "monthly_progress_reports" : "attendance_records";
        String owner=monthly ? "select o.student_user_id from monthly_progress_reports r join internship_details i on i.id=r.student_internship_id join internship_onboarding o on o.id=i.onboarding_id where r.id=? and r.deleted_at is null" : "select student_user_id from attendance_records where id=? and deleted_at is null";
        var students=jdbc.queryForList(owner, Long.class, id);
        if(students.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "Record not found in your assigned scope");
        // Lock assignment as well as the record: a concurrent reassignment cannot expand access.
        var assigned=jdbc.queryForList("select id from mentor_student_assignments where student_user_id=? and mentor_user_id=? and active=true for update", Long.class, students.get(0), user.id());
        if(assigned.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "Record not found in your assigned scope");
        var now=Timestamp.from(clock.instant());
        if(jdbc.update("update "+table+" set deleted_at=?,active_slot=null where id=? and deleted_at is null",now,id)!=1) throw failure(HttpStatus.NOT_FOUND, "Record already deleted");
        try {
            jdbc.update("insert into mentor_record_deletion_audit(record_type,record_id,deleting_user_id,deleted_at,reason) values (?,?,?,?,?)",monthly?"MONTHLY_REPORT":"ATTENDANCE",id,user.id(),now,reason.strip());
        } catch (org.springframework.dao.DataAccessException failure) {
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "Deletion could not be recorded; the deletion was rolled back");
        }
        // Original evidence, consumed sessions and files remain referenced, never cascaded or unlinked.
    }
    private static edu.institution.ims.attendance.AttendanceException failure(HttpStatus status, String message) { return new edu.institution.ims.attendance.AttendanceException(status,"RECORD_DELETION_FAILED",message); }
}
