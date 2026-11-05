package edu.institution.ims.internship;

import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

@Service
public class SecondInternshipRequestService {
    public record RequestView(Long id, Long studentId, String studentName, Long existingInternshipId,
            String source, String company, LocalDate startDate, LocalDate endDate, String status,
            Instant requestedAt, Instant reviewedAt, String rejectionReason) {}
    public record ReviewRequest(boolean approved, String reason) {}
    private final JdbcTemplate jdbc; private final InternshipDetailsRepository details;
    private final InternshipOnboardingRepository onboardings; private final UserRepository users;
    private final NotificationService notifications; private final Clock clock;
    public SecondInternshipRequestService(JdbcTemplate jdbc, InternshipDetailsRepository details,
            InternshipOnboardingRepository onboardings, UserRepository users, NotificationService notifications, Clock clock) {
        this.jdbc=jdbc; this.details=details; this.onboardings=onboardings; this.users=users; this.notifications=notifications; this.clock=clock;
    }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private edu.institution.ims.mentor.MentorAssignmentService mentorAssignments;

    @Transactional public RequestView apply(UserPrincipal principal) {
        lock(principal.id());
        InternshipDetails first=details.findByOnboardingStudentId(principal.id())
                .orElseThrow(() -> conflict("Complete Internship 1 before requesting another internship"));
        if (onboardings.findByStudentIdAndInternshipNumber(principal.id(),(short)2).isPresent()) throw conflict("Internship 2 already exists");
        Instant now=clock.instant();
        try { jdbc.update("insert into second_internship_requests(student_user_id,existing_internship_id,status,pending_student_id,requested_at) values (?,?,\'PENDING\',?,?)",
                principal.id(),first.getId(),principal.id(),Timestamp.from(now)); }
        catch (DataIntegrityViolationException e) { throw conflict("A second-internship request is already pending or approved"); }
        Long id=jdbc.queryForObject("select id from second_internship_requests where pending_student_id=?",Long.class,principal.id());
        notifications.notifyCoordinators(principal.id(),"second-internship-request:"+id,"SECOND_INTERNSHIP_REQUEST",
                "Second internship permission requested"," requested permission to create Internship 2.",
                "/admin/second-internship-requests/"+id,now);
        return one(id);
    }
    @Transactional(readOnly=true) public List<RequestView> own(UserPrincipal principal) { return query("where r.student_user_id=? order by r.requested_at desc",principal.id()); }
    @Transactional(readOnly=true) public List<RequestView> pending() { return query("where r.status=\'PENDING\' order by r.requested_at",new Object[]{}); }
    @Transactional(readOnly=true) public RequestView findForAdmin(Long id) { return one(id); }
    @Transactional public RequestView review(UserPrincipal principal, Long id, ReviewRequest request) {
        if (!"ADMIN".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"III Cell authorization required");
        Long studentId=jdbc.query("select student_user_id from second_internship_requests where id=? and status=\'PENDING\' for update",
                rs -> rs.next()?rs.getLong(1):null,id);
        if(studentId==null) throw conflict("Request is no longer pending");
        lock(studentId); Instant now=clock.instant();
        if(request.approved()) {
            if(onboardings.findByStudentIdAndInternshipNumber(studentId,(short)2).isPresent()) throw conflict("Internship 2 already exists");
            User student=users.findById(studentId).orElseThrow();
            InternshipOnboarding second=new InternshipOnboarding(student,2);
            onboardings.saveAndFlush(second);
            if (mentorAssignments != null) {
                mentorAssignments.ensureInternship2InheritsMentor(studentId);
            }
            jdbc.update("update second_internship_requests set status=\'APPROVED\',pending_student_id=null,approved_student_id=student_user_id,reviewed_by=?,reviewed_at=? where id=?",
                    principal.id(),Timestamp.from(now),id);
            notifications.create(studentId,"second-internship-approved:"+id,"SECOND_INTERNSHIP_APPROVED","Second internship approved",
                    "III Cell approved your request. You may now start your Internship 2 onboarding.",null,null,"/student/internship?tab=2",now);
        } else {
            String reason=request.reason()==null?"":request.reason().trim();
            if(reason.isEmpty() || reason.length()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A rejection reason is required");
            jdbc.update("update second_internship_requests set status=\'REJECTED\',pending_student_id=null,reviewed_by=?,reviewed_at=?,rejection_reason=? where id=?",
                    principal.id(),Timestamp.from(now),reason,id);
            notifications.create(studentId,"second-internship-rejected:"+id,"SECOND_INTERNSHIP_REJECTED","Second internship request rejected",
                    "III Cell rejected your request.",reason,null,"/student/dashboard",now);
        }
        return one(id);
    }
    private void lock(Long studentId){ if(!edu.institution.ims.user.WorkflowUserLocks.student(jdbc,studentId,true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Student is inactive"); }
    private RequestView one(Long id){ return query("where r.id=?",id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Second-internship request not found")); }
    private List<RequestView> query(String suffix,Object... args){ return jdbc.query("select r.id,r.student_user_id,coalesce(p.student_name,sa.student_name,u.email),r.existing_internship_id,o.source,d.company_name,d.start_date,d.end_date,r.status,r.requested_at,r.reviewed_at,r.rejection_reason from second_internship_requests r join users u on u.id=r.student_user_id left join student_profiles p on p.user_id=u.id left join student_accounts sa on sa.user_id=u.id join internship_details d on d.id=r.existing_internship_id join internship_onboarding o on o.id=d.onboarding_id "+suffix,
            (rs,n)->new RequestView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getObject(7,LocalDate.class),rs.getObject(8,LocalDate.class),rs.getString(9),rs.getTimestamp(10).toInstant(),rs.getTimestamp(11)==null?null:rs.getTimestamp(11).toInstant(),rs.getString(12)),args); }
    private static ResponseStatusException conflict(String message){ return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
