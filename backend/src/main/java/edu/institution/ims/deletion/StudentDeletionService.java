package edu.institution.ims.deletion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @PreAuthorize("hasRole('ADMIN')")
public class StudentDeletionService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Clock clock;
    public StudentDeletionService(JdbcTemplate jdbc,UserRepository users,ObjectMapper json,Clock clock) {
        this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);this.users=users;this.json=json;this.clock=clock;
    }
    public record Request(Boolean confirmed,String confirmationEmail,String reason) {}
    public record Preview(Long studentId,String name,String uid,String email,boolean active,Map<String,Long> dependencies,String policy,String applicationsNote) {}
    public record Result(Long studentId,boolean active,Long initiatedBy,Instant deactivatedAt,String reason) {}
    private record Dependency(String table,String predicate,String group) {}
    private record Inventory(Map<String,Long> tables,Map<String,Long> groups) {}
    private static final String INTERNSHIPS="select i.id from internship_details i join internship_onboarding o on o.id=i.onboarding_id where o.student_user_id=:student";
    private static final String PROFILES="select id from student_professional_profiles where student_user_id=:student";
    private static final String RESUMES="select id from student_resumes where professional_profile_id in ("+PROFILES+")";
    private static final String ANALYSES="select id from resume_analyses where resume_id in ("+RESUMES+")";
    private static final String ATTENDANCE="select id from attendance_records where student_user_id=:student";
    private static final String WEEKS="select id from weekly_internship_periods where internship_id in ("+INTERNSHIPS+")";
    private static final String MONTHS="select id from monthly_progress_reports where student_internship_id in ("+INTERNSHIPS+")";
    private static final String LOCATIONS="select id from internship_locations where internship_id in ("+INTERNSHIPS+")";
    private static final List<Dependency> DEPENDENCIES=List.of(
        new Dependency("student_accounts","user_id=:student","Profile and account records"),
        new Dependency("student_profiles","user_id=:student","Profile and account records"),
        new Dependency("student_professional_profiles","student_user_id=:student","Profile and account records"),
        new Dependency("student_resumes","professional_profile_id in ("+PROFILES+")","Resume versions and extracted information"),
        new Dependency("resume_analyses","resume_id in ("+RESUMES+")","Resume versions and extracted information"),
        new Dependency("resume_corrections","resume_id in ("+RESUMES+")","Resume versions and extracted information"),
        new Dependency("resume_extracted_skills","analysis_id in ("+ANALYSES+")","Resume versions and extracted information"),
        new Dependency("resume_extracted_experiences","analysis_id in ("+ANALYSES+")","Resume versions and extracted information"),
        new Dependency("resume_extracted_education","analysis_id in ("+ANALYSES+")","Resume versions and extracted information"),
        new Dependency("resume_extracted_certifications","analysis_id in ("+ANALYSES+")","Resume versions and extracted information"),
        new Dependency("student_skills","professional_profile_id in ("+PROFILES+")","Resume versions and extracted information"),
        new Dependency("student_skill_suggestions","professional_profile_id in ("+PROFILES+")","Resume versions and extracted information"),
        new Dependency("internship_onboarding","student_user_id=:student","Internship and workplace records"),
        new Dependency("internship_documents","onboarding_id in (select id from internship_onboarding where student_user_id=:student)","Internship and workplace records"),
        new Dependency("internship_details","id in ("+INTERNSHIPS+")","Internship and workplace records"),
        new Dependency("internship_locations","id in ("+LOCATIONS+")","Internship and workplace records"),
        new Dependency("hybrid_work_schedule","internship_id in ("+INTERNSHIPS+")","Internship and workplace records"),
        new Dependency("attendance_records","student_user_id=:student","Attendance and GPS evidence"),
        new Dependency("attendance_evidence","attendance_id in ("+ATTENDANCE+")","Attendance and GPS evidence"),
        new Dependency("attendance_verification_sessions","student_user_id=:student","Attendance and GPS evidence"),
        new Dependency("late_attendance_permissions","student_user_id=:student","Attendance and GPS evidence"),
        new Dependency("weekly_internship_periods","internship_id in ("+INTERNSHIPS+")","Weekly, monthly and project records"),
        new Dependency("weekly_progress_reports","period_id in ("+WEEKS+")","Weekly, monthly and project records"),
        new Dependency("monthly_progress_reports","student_internship_id in ("+INTERNSHIPS+")","Weekly, monthly and project records"),
        new Dependency("project_seminar_evaluations","student_user_id=:student","Weekly, monthly and project records"),
        new Dependency("mentor_student_assignments","student_user_id=:student","Mentor assignment history"),
        new Dependency("student_notifications","student_user_id=:student","Notifications"),
        new Dependency("mentor_notifications","student_user_id=:student","Notifications"),
        new Dependency("weekly_progress_events","period_id in ("+WEEKS+")","Reviews and audit history"),
        new Dependency("monthly_progress_review_events","report_id in ("+MONTHS+")","Reviews and audit history"),
        new Dependency("attendance_reviews","attendance_id in ("+ATTENDANCE+")","Reviews and audit history"),
        new Dependency("attendance_review_audit","attendance_id in ("+ATTENDANCE+")","Reviews and audit history"),
        new Dependency("attendance_location_verification_audit","attendance_id in ("+ATTENDANCE+")","Reviews and audit history"),
        new Dependency("attendance_submission_audit","attendance_id in ("+ATTENDANCE+")","Reviews and audit history"),
        new Dependency("late_attendance_permission_audit","student_user_id=:student","Reviews and audit history"),
        new Dependency("workplace_review_audit","location_id in ("+LOCATIONS+")","Reviews and audit history"),
        new Dependency("workplace_correction_audit","internship_id in ("+INTERNSHIPS+")","Reviews and audit history"),
        new Dependency("internship_completion_events","student_user_id=:student","Reviews and audit history"),
        new Dependency("mentor_record_deletion_audit","(record_type='ATTENDANCE' and record_id in ("+ATTENDANCE+")) or (record_type='MONTHLY_REPORT' and record_id in ("+MONTHS+"))","Reviews and audit history")
    );
    @Transactional(readOnly=true)
    public Preview preview(Long studentId,UserPrincipal actor) {
        authorize(actor,false); Map<String,Object> student=student(studentId); Inventory inventory=inventory(studentId);
        return new Preview(studentId,(String)student.get("name"),(String)student.get("uid"),(String)student.get("email"),(Boolean)student.get("active"),inventory.groups(),
                "Delete Student deactivates access and invalidates existing logins. All related records, files, assignments and historical session reports are retained.",
                "Opportunity applications use external links; no local application table exists. Stored matching snapshots are retained.");
    }
    @Transactional
    public Result deactivate(Long studentId,UserPrincipal actor,Request request) {
        authorize(actor,true);
        if(actor.id().equals(studentId)) throw error(HttpStatus.BAD_REQUEST,"Your own administrator account cannot be deleted");
        // Reject every non-student target, including the last active administrator.
        student(studentId);
        if(!Boolean.TRUE.equals(request.confirmed())) throw error(HttpStatus.BAD_REQUEST,"Explicit deletion confirmation is required");
        String reason=request.reason()==null?"":request.reason().strip();
        if(reason.length()<5 || reason.length()>500) throw error(HttpStatus.BAD_REQUEST,"Provide a deletion reason of 5–500 characters");
        if(!WorkflowUserLocks.student(jdbc,studentId,false)) throw error(HttpStatus.NOT_FOUND,"Student not found");
        Map<String,Object> target=student(studentId);
        if(!Boolean.TRUE.equals(target.get("active"))) throw error(HttpStatus.CONFLICT,"This student account is already deactivated");
        if(!Objects.equals(target.get("email"),request.confirmationEmail())) throw error(HttpStatus.CONFLICT,"Student identity changed. Reload the confirmation before continuing");
        Inventory checked=inventory(studentId);
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("identity",target);snapshot.put("tables",checked.tables());snapshot.put("groups",checked.groups());
        String audit;
        try { audit=json.writeValueAsString(snapshot); } catch(JsonProcessingException e) { throw new IllegalStateException("Could not record dependency review",e); }
        Instant at=clock.instant();
        jdbc.update("insert into student_account_deactivation_audit(student_user_id,initiated_by,deactivated_at,reason,dependency_summary) values(?,?,?,?,?)",studentId,actor.id(),Timestamp.from(at),reason,audit);
        jdbc.update("update users set active=false,token_version=token_version+1,updated_at=? where id=? and role='STUDENT' and active=true",Timestamp.from(at),studentId);
        return new Result(studentId,false,actor.id(),at,reason);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> audit(Long studentId,UserPrincipal actor) {
        authorize(actor,false);student(studentId);
        return jdbc.queryForList("select a.student_user_id,a.initiated_by,u.email as initiator_email,a.deactivated_at,a.reason,a.dependency_summary from student_account_deactivation_audit a join users u on u.id=a.initiated_by where a.student_user_id=?",studentId)
                .stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"No student deactivation audit exists"));
    }
    private void authorize(UserPrincipal principal,boolean lock) {
        if(principal==null || !principal.active() || !"ADMIN".equals(principal.role())) throw new AccessDeniedException("An active IIIC administrator is required");
        if(lock) users.findByIdForWorkflowRead(principal.id()).orElseThrow(()->new AccessDeniedException("Administrator not found"));
        if(jdbc.queryForObject("select count(*) from users where id=? and role='ADMIN' and active=true and token_version=?",Long.class,principal.id(),principal.tokenVersion())!=1)
            throw new AccessDeniedException("The administrator session is no longer valid");
    }
    private Map<String,Object> student(Long id) {
        var result=jdbc.queryForList("select u.id,u.email,u.active,u.role,coalesce(p.student_name,a.student_name,u.email) as name,coalesce(p.uid,a.uid) as uid from users u left join student_profiles p on p.user_id=u.id left join student_accounts a on a.user_id=u.id where u.id=?",id);
        if(result.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Student account not found");
        if(!"STUDENT".equals(result.get(0).get("role"))) throw error(HttpStatus.BAD_REQUEST,"Only student accounts can be deleted here; administrator and mentor accounts are protected");
        return result.get(0);
    }
    private Inventory inventory(Long studentId) {
        Map<String,Long> tables=new LinkedHashMap<>(),groups=new LinkedHashMap<>();
        // All identifiers/predicates are server constants. Include archived/soft-deleted
        // rows and inactive resume versions, rather than only currently visible records.
        String sql=String.join(" union all ",DEPENDENCIES.stream().map(d->"select '"+d.table()+"' as dependency_table,count(*) as record_count from "+d.table()+" where "+d.predicate()).toList());
        named.query(sql,Map.of("student",studentId),(org.springframework.jdbc.core.RowCallbackHandler) rs->tables.put(rs.getString("dependency_table"),rs.getLong("record_count")));
        for(var d:DEPENDENCIES) groups.merge(d.group(),tables.get(d.table()),Long::sum);
        long[] matching={0};
        jdbc.query("select report_json from opportunity_matching_runs where report_json like ?",(org.springframework.jdbc.core.RowCallbackHandler) rs->{
            try { for(var candidate:json.readTree(rs.getString(1)).path("candidates")) if(candidate.path("studentId").isIntegralNumber() && candidate.path("studentId").asLong()==studentId) {matching[0]++;break;} }
            catch(JsonProcessingException e) { throw error(HttpStatus.CONFLICT,"Matching history could not be inspected; no account was changed"); }
        },"%"+studentId+"%");
        tables.put("opportunity_matching_runs",matching[0]);groups.put("Opportunity matching history",matching[0]);
        return new Inventory(Collections.unmodifiableMap(tables),Collections.unmodifiableMap(groups));
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
