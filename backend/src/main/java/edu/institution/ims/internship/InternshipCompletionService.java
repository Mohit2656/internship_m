package edu.institution.ims.internship;

import edu.institution.ims.academicsession.AcademicSessionService;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.WorkflowUserLocks;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InternshipCompletionService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final InternshipDetailsRepository internships;
    private final AcademicSessionService sessions;
    private final NotificationService notifications;
    private final Clock clock;
    public InternshipCompletionService(JdbcTemplate jdbc,NamedParameterJdbcTemplate named,InternshipDetailsRepository internships,
            AcademicSessionService sessions,NotificationService notifications,Clock clock) {
        this.jdbc=jdbc; this.named=named; this.internships=internships; this.sessions=sessions; this.notifications=notifications; this.clock=clock;
    }
    public record Event(int revision,String previousStatus,String finalStatus,Long reviewerId,String reviewer,Instant performedAt,String remarks) {}
    public record Completion(Long internshipId,Long studentId,String studentName,String uid,String companyName,LocalDate startDate,LocalDate endDate,int totalWeeks,
            Long sessionId,String sessionName,String status,int revision,Instant completedAt,Long reviewerId,String reviewer,String remarks,
            int requiredReports,int submittedReports,int pendingReports,int approvedReports,int weeklyRequired,int monthlyRequired,
            String mentorApprovalStatus,String finalProjectReportStatus,boolean eligible,List<String> blockers,List<Event> history) {}
    public record Decision(Integer revision,String remarks) {}
    public record Stats(long totalStudents,long totalInternships,long completedInternships,long activeInternships,long completionPending,double completionPercentage) {}
    public record Page(Long sessionId,Stats stats,List<Map<String,Object>> content,long totalElements,int page) {}
    /** Reused by session exports; end dates never imply an approved completion. */
    public static final String STATUS_SQL="case when i.completion_status='COMPLETED' then 'COMPLETED' when i.end_date < :completionToday then 'COMPLETION_PENDING' when i.start_date > :completionToday then 'NOT_STARTED' else 'IN_PROGRESS' end";

    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public Completion forAdmin(Long studentId) { return read(internships.findByOnboardingStudentId(studentId).orElseThrow(InternshipCompletionService::notFound)); }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('STUDENT')")
    public Completion own(UserPrincipal principal) { return read(internships.findByOnboardingStudentId(principal.id()).orElseThrow(InternshipCompletionService::notFound)); }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Completion decide(UserPrincipal actor,Long studentId,Decision request,boolean reopen) {
        if(!WorkflowUserLocks.student(jdbc,studentId,true)) throw notFound();
        InternshipDetails internship=internships.findForUpdateByStudentId(studentId).orElseThrow(InternshipCompletionService::notFound);
        Completion before=read(internship);
        if(request.revision()==null || request.revision()!=before.revision()) throw conflict("The completion record changed. Refresh before continuing.");
        String remarks=request.remarks()==null?null:request.remarks().strip();
        if(remarks!=null && remarks.isEmpty()) remarks=null;
        if(remarks!=null && remarks.length()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Remarks must be 1000 characters or fewer");
        if(reopen) {
            if(!before.status().equals("COMPLETED")) throw conflict("Only a completed internship can be reopened");
            if(remarks==null || remarks.length()<5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide a reopening reason of 5–1000 characters");
        } else {
            if(before.status().equals("COMPLETED")) throw conflict("This internship has already been completed");
            if(!before.eligible()) throw conflict("Completion requirements are outstanding: "+String.join("; ",before.blockers()));
        }
        Instant at=clock.instant(); int next=before.revision()+1;
        String status=reopen?"IN_PROGRESS":"COMPLETED";
        jdbc.update("update internship_details set completion_status=?,completion_revision=?,completed_at=?,completion_reviewer_id=?,completion_remarks=? where id=?",
                status,next,reopen?null:Timestamp.from(at),reopen?null:actor.id(),reopen?null:remarks,internship.getId());
        jdbc.update("insert into internship_completion_events(internship_id,student_user_id,academic_session_id,revision,previous_status,final_status,reviewer_id,performed_at,remarks) values(?,?,?,?,?,?,?,?,?)",
                internship.getId(),studentId,before.sessionId(),next,before.status(),status,actor.id(),Timestamp.from(at),remarks);
        notifications.create(studentId,"internship-completion:"+internship.getId()+":"+next,
                reopen?"INTERNSHIP_REOPENED":"INTERNSHIP_COMPLETED",reopen?"Internship reopened":"Internship officially completed",
                "Your internship at "+internship.getCompanyName()+" ("+internship.getStartDate()+" to "+internship.getEndDate()+") has been "
                        +(reopen?"reopened by the IIIC coordinator. Reason: "+remarks:"officially marked as completed by the IIIC coordinator."),
                reopen?remarks:null,null,"/student/dashboard#internship-completion",at);
        return read(internship);
    }

    private Completion read(InternshipDetails i) {
        Long student=i.getOnboarding().getStudent().getId();
        Map<String,Object> row=jdbc.queryForMap("select i.completion_status,i.completion_revision,i.completed_at,i.completion_reviewer_id,i.completion_remarks,"
                +"coalesce(p.student_name,a.student_name,u.email) as student_name,coalesce(p.uid,a.uid) as uid,u.active as student_active,u.academic_session_id,s.name as session_name,r.email as reviewer "
                +"from internship_details i join internship_onboarding o on o.id=i.onboarding_id join users u on u.id=o.student_user_id "
                +"left join student_profiles p on p.user_id=u.id left join student_accounts a on a.user_id=u.id left join academic_sessions s on s.id=u.academic_session_id "
                +"left join users r on r.id=i.completion_reviewer_id where i.id=?",i.getId());
        List<String> blockers=new ArrayList<>(); int weeks=0,months=0,submitted=0,approved=0;
        try { weeks=InternshipDuration.generatedWeeks(i.getStartDate(),i.getEndDate()); }
        catch(RuntimeException invalid) { blockers.add("Internship dates need administrative review"); }
        List<ReportPeriod> weeklyReports=jdbc.query("select p.week_number as period_number,p.period_start,p.period_end,r.review_status,r.reviewer_id,r.reviewed_at from weekly_internship_periods p join weekly_progress_reports r on r.period_id=p.id where p.internship_id=? and p.active=true",InternshipCompletionService::reportPeriod,i.getId());
        List<ReportPeriod> monthlyReports=jdbc.query("select report_month_number as period_number,period_start,period_end,review_status,reviewer_id,reviewed_at from monthly_progress_reports where student_internship_id=? and deleted_at is null",InternshipCompletionService::reportPeriod,i.getId());
        // Derive every expected period from the actual dates. Missing generated rows,
        // wrong bounds, deleted reports or legacy null review states cannot pass.
        for(int w=1;w<=weeks;w++) {
            LocalDate start=i.getStartDate().plusWeeks(w-1L), end=min(start.plusDays(6),i.getEndDate());
            final int number=w;
            var report=weeklyReports.stream().filter(r->r.number()==number && r.start().equals(start) && r.end().equals(end)).findFirst();
            if(report.isPresent()) { submitted++; if(report.get().approved()) approved++; }
        }
        if(weeks>0) for(LocalDate start=i.getStartDate();!start.isAfter(i.getEndDate());start=i.getStartDate().plusMonths(months)) {
            months++; LocalDate end=min(i.getStartDate().plusMonths(months).minusDays(1),i.getEndDate());
            final int number=months; final LocalDate periodStart=start;
            var report=monthlyReports.stream().filter(r->r.number()==number && r.start().equals(periodStart) && r.end().equals(end)).findFirst();
            if(report.isPresent()) { submitted++; if(report.get().approved()) approved++; }
        }
        int required=weeks+months;
        if(i.getOnboarding().getWorkflowStatus()!=WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) blockers.add("Complete internship registration and diary issuance");
        if(!Boolean.TRUE.equals(row.get("student_active"))) blockers.add("Student account is deactivated; new completion actions are unavailable");
        if(row.get("academic_session_id")==null) blockers.add("Academic session membership is not recorded; IIIC must review this legacy account");
        if(i.getEndDate().isAfter(LocalDate.now(clock))) blockers.add("The internship end date has not been reached");
        if(submitted<required) blockers.add((required-submitted)+" mandatory weekly/monthly reports are missing");
        if(approved<submitted) blockers.add((submitted-approved)+" submitted reports need mentor approval or correction");
        String status=effectiveStatus((String)row.get("completion_status"),i.getStartDate(),i.getEndDate());
        List<Event> history=jdbc.query("select e.*,u.email from internship_completion_events e join users u on u.id=e.reviewer_id where internship_id=? order by e.revision",
                (rs,n)->new Event(rs.getInt("revision"),rs.getString("previous_status"),rs.getString("final_status"),rs.getLong("reviewer_id"),rs.getString("email"),rs.getTimestamp("performed_at").toInstant(),rs.getString("remarks")),i.getId());
        return new Completion(i.getId(),student,(String)row.get("student_name"),(String)row.get("uid"),i.getCompanyName(),i.getStartDate(),i.getEndDate(),weeks,
                number(row.get("academic_session_id")),(String)row.get("session_name"),status,((Number)row.get("completion_revision")).intValue(),instant(row.get("completed_at")),
                number(row.get("completion_reviewer_id")),(String)row.get("reviewer"),(String)row.get("completion_remarks"),required,submitted,required-submitted,approved,weeks,months,
                approved==required && required>0?"APPROVED":"PENDING","NOT_APPLICABLE — no separate final-project report workflow is configured",
                !status.equals("COMPLETED") && required>0 && blockers.isEmpty(),List.copyOf(blockers),history);
    }
    private record ReportPeriod(int number,LocalDate start,LocalDate end,boolean approved) {}
    private static ReportPeriod reportPeriod(ResultSet rs,int index) throws SQLException {
        return new ReportPeriod(rs.getInt("period_number"),rs.getDate("period_start").toLocalDate(),rs.getDate("period_end").toLocalDate(),
                "APPROVED".equals(rs.getString("review_status")) && rs.getObject("reviewer_id")!=null && rs.getObject("reviewed_at")!=null);
    }
    private String effectiveStatus(String saved,LocalDate start,LocalDate end) {
        if("COMPLETED".equals(saved)) return saved;
        LocalDate today=LocalDate.now(clock);
        return end.isBefore(today)?"COMPLETION_PENDING":start.isAfter(today)?"NOT_STARTED":"IN_PROGRESS";
    }
    private static LocalDate min(LocalDate a,LocalDate b) { return a.isBefore(b)?a:b; }
    private static Long number(Object n) { return n==null?null:((Number)n).longValue(); }
    private static Instant instant(Object value) { return value==null?null:value instanceof OffsetDateTime time?time.toInstant():((Timestamp)value).toInstant(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Internship not found"); }

    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page list(Long sessionId,String search,String status,String sort,int page) {
        Long selected=sessions.selectedId(sessionId);
        MapSqlParameterSource params=new MapSqlParameterSource("session",selected).addValue("completionToday",LocalDate.now(clock));
        String base=" from users u left join student_accounts sa on sa.user_id=u.id left join student_profiles p on p.user_id=u.id "
                +"left join internship_onboarding o on o.student_user_id=u.id left join internship_details i on i.onboarding_id=o.id "
                +"left join mentor_student_assignments a on a.student_user_id=u.id and a.active=true left join users mu on mu.id=a.mentor_user_id "
                +"left join mentor_profiles mp on mp.user_id=mu.id left join users reviewer on reviewer.id=i.completion_reviewer_id "
                +"where u.role='STUDENT' and u.academic_session_id=:session";
        Map<String,Object> counts=named.queryForMap("select count(u.id) as students,count(i.id) as total,coalesce(sum(case when i.completion_status='COMPLETED' then 1 else 0 end),0) as completed,"
                +"coalesce(sum(case when i.id is not null and i.completion_status<>'COMPLETED' and i.end_date < :completionToday then 1 else 0 end),0) as pending"+base,params);
        long total=((Number)counts.get("total")).longValue(),completed=((Number)counts.get("completed")).longValue(),pending=((Number)counts.get("pending")).longValue();
        Stats stats=new Stats(((Number)counts.get("students")).longValue(),total,completed,total-completed-pending,pending,total==0?0:Math.round(10000.0*completed/total)/100.0);
        String where=base+" and i.id is not null";
        if(search!=null && !search.isBlank()) {
            if(search.length()>254) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Search must be 254 characters or fewer");
            params.addValue("search","%"+search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            where+=" and (lower(coalesce(p.student_name,sa.student_name,u.email)) like :search escape '!' or lower(coalesce(p.uid,sa.uid,'')) like :search escape '!' or lower(i.company_name) like :search escape '!')";
        }
        if(status!=null && !status.isBlank()) {
            if(!Set.of("COMPLETED","IN_PROGRESS","NOT_STARTED","COMPLETION_PENDING").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid completion status");
            params.addValue("status",status); where+=" and ("+STATUS_SQL+")=:status";
        }
        String order=switch(sort==null?"completionDate":sort) {
            case "name" -> "student_name asc,u.id";
            case "company" -> "i.company_name asc,u.id";
            case "startDate" -> "i.start_date desc,u.id";
            case "completionDate" -> "i.completed_at desc nulls last,u.id";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported sort");
        };
        int safePage=Math.max(0,Math.min(page,1000000));
        long rows=named.queryForObject("select count(*)"+where,params,Long.class);
        params.addValue("offset",safePage*30);
        List<Map<String,Object>> content=named.queryForList("select u.id as \"studentId\",coalesce(p.student_name,sa.student_name,u.email) as student_name,coalesce(p.uid,sa.uid) as uid,"
                +"i.company_name as company,coalesce(mp.name,mu.email) as mentor,i.start_date as \"startDate\",i.end_date as \"endDate\",i.completed_at as \"completionDate\","
                +"reviewer.email as reviewer,("+STATUS_SQL+") as status"+where+" order by "+order+" limit 30 offset :offset",params);
        return new Page(selected,stats,content,rows,safePage);
    }
}
