package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.notification.MentorNotificationService;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, date-scoped requests; manual approval never manufactures GPS or liveness evidence. */
@Service
public class LateAttendanceRequestService {
    public record Submission(Long internshipId, LocalDate attendanceDate, String reason, String supportingDetails) {}
    public record Decision(String status, String rejectionReason, Boolean allowResubmission) {}
    public record Request(Long id, Long studentUserId, Long internshipId, String studentName, String uid,
            LocalDate attendanceDate, String reason, String supportingDetails, Instant requestedAt, String status,
            Long reviewerId, Instant reviewedAt, String rejectionReason, boolean resubmissionAllowed,
            Long attendanceId, boolean mentorAssigned, String attendanceZone) {}
    private static final String ASSIGNED = "exists(select 1 from mentor_student_assignments a join users m on m.id=a.mentor_user_id "
            + "where a.student_user_id=r.student_user_id and a.active=true and m.active=true and m.role='MENTOR')";
    private static final String SELECT = "select r.*,coalesce(p.student_name,sa.student_name,s.email) as student_name,"
            + "coalesce(p.uid,sa.uid) as uid," + ASSIGNED + " as mentor_assigned from late_attendance_requests r "
            + "join users s on s.id=r.student_user_id left join student_profiles p on p.user_id=s.id "
            + "left join student_accounts sa on sa.user_id=s.id ";
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final InternshipDetailsRepository internships;
    private final MentorStudentAssignmentRepository assignments;
    private final AttendanceRepository attendance;
    private final AttendanceReviewRepository reviews;
    private final MentorNotificationService mentorNotifications;
    private final NotificationService notifications;
    private final Clock clock;
    private final AttendancePolicyService policies;
    private final AttendanceCalendarProperties calendar;

    public LateAttendanceRequestService(JdbcTemplate jdbc, UserRepository users, InternshipDetailsRepository internships,
            MentorStudentAssignmentRepository assignments, AttendanceRepository attendance, AttendanceReviewRepository reviews,
            MentorNotificationService mentorNotifications, NotificationService notifications, Clock clock,
            AttendancePolicyService policies, AttendanceCalendarProperties calendar) {
        this.jdbc=jdbc; this.users=users; this.internships=internships; this.assignments=assignments;
        this.attendance=attendance; this.reviews=reviews; this.mentorNotifications=mentorNotifications;
        this.notifications=notifications; this.clock=clock;
        this.policies=policies; this.calendar=calendar;
    }

    @Transactional
    public Request submit(UserPrincipal principal, Submission input) {
        actor(principal, "STUDENT");
        if (input==null || input.internshipId()==null || input.attendanceDate()==null)
            throw error(HttpStatus.BAD_REQUEST,"LATE_REQUEST_DETAILS_REQUIRED","Choose an internship and attendance date");
        String reason=trim(input.reason());
        String details=trim(input.supportingDetails());
        if (reason==null || reason.length()<5 || reason.length()>1000 || (details!=null && details.length()>4000))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_LATE_REQUEST","Provide a reason of 5–1000 characters and supporting details of at most 4000 characters");
        if (!WorkflowUserLocks.student(jdbc, principal.id(), true)) throw missing();
        InternshipDetails internship=internships.findByIdAndOnboardingStudentId(input.internshipId(),principal.id())
                .filter(i -> i.getOnboarding().getWorkflowStatus()==WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,"ATTENDANCE_NOT_ELIGIBLE","Complete internship registration before requesting attendance"));
        LocalDate date=input.attendanceDate();
        if (date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate()) || date.isAfter(LocalDate.now(clock)))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_LATE_REQUEST_DATE","Choose a past or current attendance date within this internship");
        if (!AttendanceSubmissionWindow.isClosed(date,clock))
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_WINDOW_OPEN","Submit today's attendance using the regular attendance flow before the deadline");
        requireEligibleDate(internship,date);
        requireUnsubmitted(principal.id(),internship.getId(),date);
        if (jdbc.queryForObject("select count(*) from late_attendance_requests where student_user_id=? and internship_id=? and attendance_date=? and active_slot=1",
                Long.class,principal.id(),internship.getId(),date)>0)
            throw error(HttpStatus.CONFLICT,"LATE_REQUEST_ALREADY_EXISTS","A request already exists for this attendance date. Resubmit only when the reviewer allows it after rejection");
        Instant at=clock.instant();
        jdbc.update("insert into late_attendance_requests(student_user_id,internship_id,attendance_date,reason,supporting_details,requested_at) values (?,?,?,?,?,?)",
                principal.id(),internship.getId(),date,reason,details,Timestamp.from(at));
        Request request=jdbc.query(SELECT+"where r.student_user_id=? and r.internship_id=? and r.attendance_date=? and r.active_slot=1",this::map,
                principal.id(),internship.getId(),date).get(0);
        String description=request.studentName()+" (UID: "+Objects.toString(request.uid(),"unavailable")+") requested late attendance for "+date+".";
        String key="late-attendance-request:"+request.id();
        if (request.mentorAssigned()) {
            mentorNotifications.workflowSubmitted(principal.id(),key,"LATE_ATTENDANCE_REQUEST","Late attendance request",description,
                    "/mentor/attendance#late-attendance-permissions",at);
        }
        if (jdbc.queryForObject("select count(*) from mentor_notifications where student_user_id=? and event_key=?",Long.class,principal.id(),key)==0) {
            for (Long admin:jdbc.queryForList("select id from users where role='ADMIN' and active=true",Long.class))
                notifications.create(admin,key,"LATE_ATTENDANCE_REQUEST","Unassigned student: late attendance request",description+" No active faculty mentor is assigned.",
                        null,null,"/admin/attendance#late-attendance-requests",at);
        }
        return request;
    }

    @Transactional(readOnly=true)
    public List<Request> studentRequests(UserPrincipal principal, Long internshipId) {
        actor(principal,"STUDENT");
        return jdbc.query(SELECT+"where r.student_user_id=? "+(internshipId==null?"":"and r.internship_id=? ")+"order by r.requested_at desc,r.id desc",
                this::map,internshipId==null?new Object[]{principal.id()}:new Object[]{principal.id(),internshipId});
    }

    @Transactional(readOnly=true)
    public List<Request> reviewRequests(UserPrincipal principal, String view) {
        String role=reviewRole(principal);
        boolean history="history".equalsIgnoreCase(view);
        if (!history && !"pending".equalsIgnoreCase(view))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST_VIEW","Choose pending or history");
        String scope="MENTOR".equals(role)?"exists(select 1 from mentor_student_assignments a where a.student_user_id=r.student_user_id and a.active=true and a.mentor_user_id=?)"
                :history?"(not "+ASSIGNED+" or r.reviewer_id=?)":"not "+ASSIGNED;
        String sql=SELECT+"where s.active=true and "+scope+" and r.status "+(history?"<>'PENDING'":"='PENDING'")+" order by r.requested_at "+(history?"desc":"asc")+",r.id "+(history?"desc":"asc");
        return jdbc.query(sql,this::map,"MENTOR".equals(role)||history?new Object[]{principal.id()}:new Object[]{});
    }

    @Transactional
    public Request decide(Long id, UserPrincipal principal, Decision decision) {
        String role=reviewRole(principal);
        if (decision==null || !("APPROVED".equals(decision.status()) || "REJECTED".equals(decision.status())))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_LATE_REQUEST_DECISION","Choose approve or reject");
        String reason=trim(decision.rejectionReason());
        boolean resubmit="REJECTED".equals(decision.status()) && Boolean.TRUE.equals(decision.allowResubmission());
        if (reason!=null && reason.length()>1000)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_REJECTION_REASON","Keep the rejection reason within 1000 characters");
        // Actor read lock, then student lock, assignment lock, request: matches reassignment and attendance workflows.
        User reviewer=users.findByIdForWorkflowRead(principal.id()).filter(u -> u.isActive() && u.getRole().name().equals(role)).orElseThrow(LateAttendanceRequestService::missing);
        List<Long> owners=jdbc.queryForList("select student_user_id from late_attendance_requests where id=?",Long.class,id);
        if (owners.isEmpty() || !WorkflowUserLocks.student(jdbc,owners.get(0),true)) throw missing();
        MentorStudentAssignment assignment=assignments.findByStudentIdAndActiveTrueForLateAttendanceUpdate(owners.get(0))
                .filter(a -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where id=? and role='MENTOR' and active=true)",Boolean.class,a.getMentor().getId()))).orElse(null);
        if (("MENTOR".equals(role) && (assignment==null || !assignment.getMentor().getId().equals(principal.id())))
                || ("ADMIN".equals(role) && assignment!=null)) throw missing();
        if (jdbc.queryForList("select id from late_attendance_requests where id=? for update",Long.class,id).isEmpty()) throw missing();
        List<Request> requests=jdbc.query(SELECT+"where r.id=?",this::map,id);
        if (requests.isEmpty()) throw missing();
        Request request=requests.get(0);
        if (!"PENDING".equals(request.status()))
            throw error(HttpStatus.CONFLICT,"LATE_REQUEST_ALREADY_REVIEWED","This request has already been reviewed. Refresh the request history");
        Instant at=clock.instant();
        Long recordId=null;
        String reviewerName=jdbc.queryForList("select name from mentor_profiles where user_id=?",String.class,principal.id()).stream().findFirst().orElse(reviewer.getEmail());
        if ("APPROVED".equals(decision.status())) {
            InternshipDetails internship=internships.findByIdAndOnboardingStudentId(request.internshipId(),request.studentUserId())
                    .orElseThrow(LateAttendanceRequestService::missing);
            requireEligibleDate(internship,request.attendanceDate());
            AttendanceRecord record=requireUnsubmitted(request.studentUserId(),request.internshipId(),request.attendanceDate());
            if (record!=null) audit(record,"AUTO_ABSENT");
            else record=new AttendanceRecord(users.findById(request.studentUserId()).orElseThrow(),internship,
                    request.attendanceDate(),AttendanceResult.PENDING_MENTOR_REVIEW,VerificationStatus.UNVERIFIED,AttendanceSubmissionType.LATE_REQUEST,at);
            record.approveLateRequest(reviewer,at);
            try { attendance.saveAndFlush(record); }
            catch (org.springframework.dao.DataIntegrityViolationException conflict) {
                throw error(HttpStatus.CONFLICT,"ATTENDANCE_CHANGED","Attendance changed while this request was being reviewed. Refresh the records before trying again");
            }
            AttendanceReview review=new AttendanceReview(record);
            review.decide(AttendanceReviewStatus.APPROVED,reviewer.getId(),reviewerName,at,null);
            reviews.saveAndFlush(review);
            audit(record,"LATE_SUBMISSION");
            recordId=record.getId();
        }
        jdbc.update("update late_attendance_requests set status=?,reviewer_id=?,reviewed_at=?,rejection_reason=?,resubmission_allowed=?,attendance_id=?,active_slot=? where id=? and status='PENDING'",
                decision.status(),principal.id(),Timestamp.from(at),"REJECTED".equals(decision.status())?reason:null,resubmit,recordId,resubmit?null:1,id);
        notifications.create(request.studentUserId(),"late-attendance-decision:"+id,"LATE_ATTENDANCE_"+decision.status(),
                "Late attendance request "+decision.status().toLowerCase(Locale.ROOT),"Your late attendance request for "+request.attendanceDate()+" was "+decision.status().toLowerCase(Locale.ROOT)+"."
                        +(resubmit?" You may submit a new request for this date.":""),"REJECTED".equals(decision.status())?reason:null,reviewerName,
                "/student/attendance#late-attendance-requests",at);
        return jdbc.query(SELECT+"where r.id=?",this::map,id).get(0);
    }

    private AttendanceRecord requireUnsubmitted(Long student,Long internship,LocalDate date) {
        AttendanceRecord record=attendance.findByStudentIdAndInternshipIdAndAttendanceDate(student,internship,date).orElse(null);
        if (record!=null && (!record.isAutomaticAbsent() || record.getEvidence()!=null || record.getReview()!=null))
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_ALREADY_SUBMITTED","Attendance already exists for this date. Existing submissions must be handled in Attendance");
        if (record==null && attendance.countIncludingDeleted(internship,date)>0)
            throw error(HttpStatus.CONFLICT,"ATTENDANCE_HISTORY_EXISTS","This date has retained attendance history. Contact IIIC to review it");
        return record;
    }
    private void requireEligibleDate(InternshipDetails internship,LocalDate date) {
        if (date.isAfter(LocalDate.now(clock)) || !AttendanceAbsenceService.isApplicable(internship,date,clock,policies,calendar))
            throw error(HttpStatus.CONFLICT,"LATE_REQUEST_DATE_NOT_ELIGIBLE",
                    "This date is not an eligible internship working day. Check internship dates, work schedule, holidays and approved leave");
    }
    private void audit(AttendanceRecord record,String event) {
        jdbc.update("insert into attendance_submission_audit(attendance_id,event_type,event_at,attendance_result,verification_status,verification_basis,submission_type,authorized_by,authorized_at) values (?,?,?,?,?,?,?,?,?)",
                record.getId(),event,Timestamp.from(record.getServerSubmittedAt()),record.getAttendanceResult().name(),record.getVerificationStatus().name(),record.getVerificationBasis().name(),record.getSubmissionType().name(),
                record.getLateAuthorizedBy()==null?null:record.getLateAuthorizedBy().getId(),record.getLateAuthorizedAt()==null?null:Timestamp.from(record.getLateAuthorizedAt()));
    }
    private User actor(UserPrincipal principal,String role) {
        if (principal==null || !principal.active() || !role.equals(principal.role())) throw error(HttpStatus.FORBIDDEN,"ROLE_REQUIRED",role+" access is required");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from users where id=? and role=? and active=true)",Boolean.class,principal.id(),role))) throw missing();
        return users.findById(principal.id()).filter(u -> u.isActive() && u.getRole().name().equals(role)).orElseThrow(LateAttendanceRequestService::missing);
    }
    private String reviewRole(UserPrincipal principal) {
        if (principal==null || !("MENTOR".equals(principal.role()) || "ADMIN".equals(principal.role())))
            throw error(HttpStatus.FORBIDDEN,"REVIEWER_REQUIRED","Faculty mentor or IIIC access is required");
        actor(principal,principal.role()); return principal.role();
    }
    private Request map(ResultSet rs,int row) throws SQLException {
        Timestamp reviewed=rs.getTimestamp("reviewed_at");
        return new Request(rs.getLong("id"),rs.getLong("student_user_id"),rs.getLong("internship_id"),rs.getString("student_name"),rs.getString("uid"),
                rs.getDate("attendance_date").toLocalDate(),rs.getString("reason"),rs.getString("supporting_details"),rs.getTimestamp("requested_at").toInstant(),rs.getString("status"),
                rs.getObject("reviewer_id",Long.class),reviewed==null?null:reviewed.toInstant(),rs.getString("rejection_reason"),rs.getBoolean("resubmission_allowed"),rs.getObject("attendance_id",Long.class),rs.getBoolean("mentor_assigned"),clock.getZone().getId());
    }
    private static String trim(String value) { return value==null || value.isBlank()?null:value.strip(); }
    private static AttendanceException missing() { return error(HttpStatus.NOT_FOUND,"LATE_REQUEST_NOT_FOUND","Late attendance request was not found in your authorized scope"); }
    private static AttendanceException error(HttpStatus status,String code,String message) { return new AttendanceException(status,code,message); }
}
