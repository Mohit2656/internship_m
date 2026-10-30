package edu.institution.ims.weeklyprogress;

import edu.institution.ims.internship.*;
import edu.institution.ims.notification.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.UserRepository;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.WorkflowUserLocks;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Weekly progress is independent of monthly photo reports and GPS attendance. */
@Service
public class WeeklyProgressService {
    private final JdbcTemplate jdbc;
    private final InternshipDetailsRepository internships;
    private final NotificationService students;
    private final MentorNotificationService mentors;
    private final Clock clock;
    private final UserRepository users;

    public WeeklyProgressService(JdbcTemplate jdbc, InternshipDetailsRepository internships,
            NotificationService students, MentorNotificationService mentors, Clock clock, UserRepository users) {
        this.jdbc = jdbc; this.internships = internships; this.students = students; this.mentors = mentors; this.clock = clock; this.users=users;
    }

    public record Event(int revision, String eventType, Long actorId, String actorEmail,
            Instant performedAt, String workDescription, String reason) {}
    public record Week(Long id, Long internshipId, Long studentUserId, String studentName, String uid,
            String companyName, int weekNumber, LocalDate periodStart, LocalDate periodEnd, boolean active,
            String submissionStatus, String status, String workDescription, int revision, Instant submittedAt,
            Long reviewerId, String reviewerEmail, Instant reviewedAt, String rejectionReason,
            long attendanceSubmitted, long attendancePresent, long attendanceGpsVerified, List<Event> history) {}
    public record Overview(Long internshipId, String companyName, LocalDate internshipStart,
            LocalDate internshipEnd, int totalWeeks, String calculationRule, List<Week> weeks) {}
    public record Warning(Long internshipId, Long studentUserId, String studentName, String message) {}
    public record Monitoring(List<Week> weeks, List<Warning> warnings) {}
    private record Bounds(Long id, int number, LocalDate start, LocalDate end, boolean active, boolean submitted) {}

    /** The parent lock serializes date edits, generation, submissions and reviews. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(InternshipDetails internship) {
        lockInternship(internship.getId());
        int count = InternshipDuration.generatedWeeks(internship.getStartDate(), internship.getEndDate());
        // Normalize only this derived legacy value; retain dates and student timestamps.
        jdbc.update("update internship_details set total_weeks=? where id=? and total_weeks<>?",count,internship.getId(),count);
        List<Bounds> existing = jdbc.query("select p.*, r.period_id as submitted_id from weekly_internship_periods p "
                        + "left join weekly_progress_reports r on r.period_id=p.id where p.internship_id=? order by p.id",
                (rs, n) -> new Bounds(rs.getLong("id"), rs.getInt("week_number"), rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate(), rs.getBoolean("active"), rs.getObject("submitted_id") != null), internship.getId());
        int unchangedActive = 0;
        for (Bounds period : existing) {
            LocalDate start = internship.getStartDate().plusWeeks(period.number() - 1L);
            LocalDate end = start.plusDays(6).isAfter(internship.getEndDate()) ? internship.getEndDate() : start.plusDays(6);
            boolean unchanged = period.number() <= count && period.start().equals(start) && period.end().equals(end);
            if (unchanged && period.active()) unchangedActive++;
            if (!unchanged && period.submitted())
                throw error(HttpStatus.CONFLICT, "SUBMITTED_WEEK_DATE_CONFLICT",
                        "These dates would change submitted week " + period.number() + ". Keep submitted reporting periods unchanged; later empty weeks can be adjusted.");
        }
        if (unchangedActive == count && existing.stream().filter(Bounds::active).count() == count) return;
        // Never remove historical rows, even when they have no report.
        jdbc.update("update weekly_internship_periods set active=false where internship_id=?", internship.getId());
        for (int number = 1; number <= count; number++) {
            LocalDate start = internship.getStartDate().plusWeeks(number - 1L);
            LocalDate end = start.plusDays(6).isAfter(internship.getEndDate()) ? internship.getEndDate() : start.plusDays(6);
            final int weekNumber = number;
            Optional<Bounds> retained = existing.stream().filter(p -> p.number() == weekNumber && p.start().equals(start) && p.end().equals(end)).findFirst();
            if (retained.isPresent()) jdbc.update("update weekly_internship_periods set active=true where id=?", retained.get().id());
            else jdbc.update("insert into weekly_internship_periods(internship_id,week_number,period_start,period_end,active,created_at) values (?,?,?,?,true,?)",
                    internship.getId(), number, start, end, Timestamp.from(clock.instant()));
        }
    }

    @Transactional
    public Overview ownOverview(UserPrincipal principal) {
        role(principal, "STUDENT");
        InternshipDetails internship = internships.findForUpdateByStudentId(principal.id()).orElseThrow(() ->
                error(HttpStatus.CONFLICT, "WEEKLY_PROGRESS_NOT_ELIGIBLE", "Complete internship registration to generate weekly records"));
        eligible(internship); synchronize(internship);
        return new Overview(internship.getId(), internship.getCompanyName(), internship.getStartDate(), internship.getEndDate(),
                InternshipDuration.weeks(internship.getStartDate(), internship.getEndDate()),
                "Both dates included; weeks = ceiling(internship days / 7). The final week ends on the internship end date.",
                weeks("i.id=?", internship.getId()));
    }

    @Transactional
    public List<Week> monitored(UserPrincipal principal, boolean admin) {
        return monitoring(principal,admin).weeks();
    }
    @Transactional
    public Monitoring monitoring(UserPrincipal principal, boolean admin) {
        role(principal, admin ? "ADMIN" : "MENTOR");
        List<Warning> warnings=new ArrayList<>();
        List<Long> ids = admin ? jdbc.queryForList("select i.id from internship_details i join internship_onboarding o on o.id=i.onboarding_id "
                        + "where o.workflow_status='INTERNSHIP_DETAILS_COMPLETE' and exists(select 1 from users su where su.id=o.student_user_id and su.active=true) order by i.id", Long.class)
                : jdbc.queryForList("select i.id from internship_details i join internship_onboarding o on o.id=i.onboarding_id "
                        + "join mentor_student_assignments a on a.student_user_id=o.student_user_id and a.active=true "
                        + "where a.mentor_user_id=? and o.workflow_status='INTERNSHIP_DETAILS_COMPLETE' and exists(select 1 from users su where su.id=o.student_user_id and su.active=true) order by i.id", Long.class, principal.id());
        // Backfill old registrations using the same idempotent path as a new registration.
        for (Long id : ids) {
            lockInternship(id);
            internships.findById(id).ifPresent(internship -> {
                try { InternshipDuration.generatedWeeks(internship.getStartDate(), internship.getEndDate()); }
                catch (InternshipDetailsException unsupported) {
                    Long studentId=internship.getOnboarding().getStudent().getId();
                    String name=jdbc.queryForObject("select coalesce(p.student_name,a.student_name,u.email) from users u left join student_profiles p on p.user_id=u.id left join student_accounts a on a.user_id=u.id where u.id=?",String.class,studentId);
                    warnings.add(new Warning(id,studentId,name,"Weekly records need an internship date review: "+unsupported.getMessage()+". Existing internship records are preserved."));
                    return;
                }
                synchronize(internship);
            });
        }
        return new Monitoring(admin ? weeks("exists(select 1 from users su where su.id=o.student_user_id and su.active=true)") : weeks("exists(select 1 from users su where su.id=o.student_user_id and su.active=true) and exists(select 1 from mentor_student_assignments a where a.student_user_id=o.student_user_id and a.mentor_user_id=? and a.active=true)", principal.id()),warnings);
    }

    @Transactional
    public Week submit(UserPrincipal principal, Long id, Integer revision, String description) {
        role(principal, "STUDENT");
        // Assignment changes lock mentor -> student. Attendance locks student before
        // inserting its internship FK. Follow both orders before taking the parent.
        Long observedMentor=assignedMentor(principal.id());
        if(observedMentor!=null) users.findByIdForWorkflowRead(observedMentor).orElseThrow(WeeklyProgressService::notFound);
        if (!WorkflowUserLocks.student(jdbc,principal.id(),true)) throw notFound();
        if(!Objects.equals(observedMentor,assignedMentor(principal.id())))
            throw error(HttpStatus.CONFLICT,"MENTOR_ASSIGNMENT_CHANGED","Your faculty mentor assignment changed; refresh and submit again");
        Long internshipId = jdbc.queryForList("select p.internship_id from weekly_internship_periods p join internship_details i on i.id=p.internship_id "
                        + "join internship_onboarding o on o.id=i.onboarding_id where p.id=? and o.student_user_id=?", Long.class, id, principal.id())
                .stream().findFirst().orElseThrow(WeeklyProgressService::notFound);
        lockInternship(internshipId);
        InternshipDetails internship = internships.findById(internshipId).orElseThrow(WeeklyProgressService::notFound);
        eligible(internship);
        Week week = week(id);
        if (!week.active()) throw error(HttpStatus.CONFLICT, "WEEK_ARCHIVED", "This period was replaced after the internship dates changed");
        if (LocalDate.now(clock).isBefore(week.periodStart())) throw error(HttpStatus.CONFLICT, "WEEK_UPCOMING", "This internship week has not started yet");
        String work = description == null ? "" : description.strip();
        if (work.isBlank() || work.length() > 3000) throw error(HttpStatus.BAD_REQUEST, "WORK_DESCRIPTION_INVALID", "Describe the work performed using 1 to 3000 characters");
        int next = 1;
        Instant at = clock.instant();
        if (week.revision() > 0) {
            if (!"REJECTED".equals(week.status())) throw error(HttpStatus.CONFLICT, "WEEK_ALREADY_SUBMITTED", "Only a rejected weekly report may be corrected and resubmitted");
            requireRevision(week, revision);
            next = week.revision() + 1;
            jdbc.update("update weekly_progress_reports set work_description=?,revision=?,review_status='PENDING_MENTOR_REVIEW',submitted_at=?,reviewer_id=null,reviewed_at=null,rejection_reason=null where period_id=?",
                    work, next, Timestamp.from(at), id);
        } else {
            jdbc.update("insert into weekly_progress_reports(period_id,work_description,revision,review_status,submitted_at) values (?,?,1,'PENDING_MENTOR_REVIEW',?)", id, work, Timestamp.from(at));
        }
        event(id, next, "SUBMITTED", principal.id(), at, work, null);
        mentors.workflowSubmitted(principal.id(), "weekly-submitted:" + id + ":" + next, "WEEKLY_REPORT_SUBMITTED",
                "Weekly progress requires review", Objects.toString(week.studentName(), "Student") + " submitted week " + week.weekNumber()
                        + " at " + week.companyName() + ".", "/mentor/weekly-progress#week-" + id, at);
        return week(id);
    }

    @Transactional
    public Week review(UserPrincipal principal, Long id, int revision, boolean approved, String reason) {
        role(principal, "MENTOR");
        Long studentId = jdbc.queryForList("select o.student_user_id from weekly_internship_periods p join internship_details i on i.id=p.internship_id "
                        + "join internship_onboarding o on o.id=i.onboarding_id where p.id=?", Long.class, id)
                .stream().findFirst().orElseThrow(WeeklyProgressService::notFound);
        users.findByIdForWorkflowRead(principal.id()).filter(user->user.isActive()&&user.getRole()==Role.MENTOR).orElseThrow(WeeklyProgressService::notFound);
        if (!WorkflowUserLocks.student(jdbc,studentId,true)) throw notFound();
        // Mentor SHARE -> student -> assignment -> parent, including notification FKs.
        if (jdbc.queryForList("select id from mentor_student_assignments where student_user_id=? and mentor_user_id=? and active=true for update",
                Long.class, studentId, principal.id()).isEmpty()) throw notFound();
        Long internshipId = jdbc.queryForObject("select internship_id from weekly_internship_periods where id=?", Long.class, id);
        lockInternship(internshipId);
        Week week = week(id);
        requireRevision(week, revision);
        if (!"PENDING_MENTOR_REVIEW".equals(week.status())) throw error(HttpStatus.CONFLICT, "WEEK_ALREADY_REVIEWED", "This weekly report is not awaiting review");
        String clean = approved ? null : reason == null ? "" : reason.strip();
        if (!approved && (clean.isBlank() || clean.length() > 1000)) throw error(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED", "Provide a rejection reason of 1 to 1000 characters");
        Instant at = clock.instant();
        String status = approved ? "APPROVED" : "REJECTED";
        jdbc.update("update weekly_progress_reports set review_status=?,reviewer_id=?,reviewed_at=?,rejection_reason=? where period_id=?",
                status, principal.id(), Timestamp.from(at), clean, id);
        event(id, revision, status, principal.id(), at, week.workDescription(), clean);
        students.create(studentId, "weekly-reviewed:" + id + ":" + revision, "WEEKLY_REPORT_" + status,
                "Week " + week.weekNumber() + " progress " + (approved ? "approved" : "rejected"),
                approved ? "Your faculty mentor approved your weekly progress report." : "Your faculty mentor rejected your weekly progress report. Review the feedback and resubmit.",
                clean, principal.email(), "/student/weekly-progress#week-" + id, at);
        return week(id);
    }

    private void event(Long id, int revision, String type, Long actorId, Instant at, String work, String reason) {
        jdbc.update("insert into weekly_progress_events(period_id,revision,event_type,actor_id,performed_at,work_description,reason) values (?,?,?,?,?,?,?)",
                id, revision, type, actorId, Timestamp.from(at), work, reason);
    }
    private Long assignedMentor(Long studentId) {
        return jdbc.queryForList("select mentor_user_id from mentor_student_assignments where student_user_id=? and active=true",Long.class,studentId).stream().findFirst().orElse(null);
    }
    private Week week(Long id) { return weeks("p.id=?", id).stream().findFirst().orElseThrow(WeeklyProgressService::notFound); }
    private List<Week> weeks(String scope, Object... args) {
        Map<Long,List<Event>> histories=new HashMap<>();
        jdbc.query("select e.*,actor.email from weekly_progress_events e join weekly_internship_periods p on p.id=e.period_id "
                        + "join internship_details i on i.id=p.internship_id join internship_onboarding o on o.id=i.onboarding_id "
                        + "join users actor on actor.id=e.actor_id where "+scope+" order by e.period_id,e.id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> histories.computeIfAbsent(rs.getLong("period_id"),ignored->new ArrayList<>())
                        .add(new Event(rs.getInt("revision"),rs.getString("event_type"),rs.getLong("actor_id"),rs.getString("email"),
                                instant(rs,"performed_at"),rs.getString("work_description"),rs.getString("reason"))),args);
        return jdbc.query("select p.*,o.student_user_id,coalesce(sp.student_name,sa.student_name,u.email) as student_name,coalesce(sp.uid,sa.uid) as uid,i.company_name,"
                        + "r.review_status,r.work_description,r.revision,r.submitted_at,r.reviewer_id,r.reviewed_at,r.rejection_reason,reviewer.email as reviewer_email,"
                        + "(select count(*) from attendance_records a where a.internship_id=i.id and a.attendance_date between p.period_start and p.period_end and a.deleted_at is null and a.submission_type<>'AUTO_ABSENT') as attendance_submitted,"
                        + "(select count(*) from attendance_records a where a.internship_id=i.id and a.attendance_date between p.period_start and p.period_end and a.deleted_at is null and a.attendance_result='PRESENT') as attendance_present,"
                        + "(select count(*) from attendance_records a join attendance_evidence e on e.attendance_id=a.id where a.internship_id=i.id and a.attendance_date between p.period_start and p.period_end and a.deleted_at is null and e.location_verification_status='VERIFIED') as attendance_verified "
                        + "from weekly_internship_periods p join internship_details i on i.id=p.internship_id join internship_onboarding o on o.id=i.onboarding_id "
                        + "join users u on u.id=o.student_user_id left join student_profiles sp on sp.user_id=u.id left join student_accounts sa on sa.user_id=u.id "
                        + "left join weekly_progress_reports r on r.period_id=p.id left join users reviewer on reviewer.id=r.reviewer_id where " + scope
                        + " order by student_name,p.internship_id,p.active desc,p.week_number,p.id", (rs, n) -> mapWeek(rs,histories.getOrDefault(rs.getLong("id"),List.of())), args);
    }
    private Week mapWeek(ResultSet rs,List<Event> events) throws SQLException {
        Long id = rs.getLong("id");
        LocalDate start = rs.getDate("period_start").toLocalDate();
        String status = rs.getString("review_status");
        boolean active = rs.getBoolean("active");
        return new Week(id, rs.getLong("internship_id"), rs.getLong("student_user_id"), rs.getString("student_name"), rs.getString("uid"),
                rs.getString("company_name"), rs.getInt("week_number"), start, rs.getDate("period_end").toLocalDate(), active,
                status == null ? "NOT_SUBMITTED" : "SUBMITTED", status == null ? (!active ? "ARCHIVED" : LocalDate.now(clock).isBefore(start) ? "UPCOMING" : "AVAILABLE") : status,
                rs.getString("work_description"), rs.getInt("revision"), instant(rs, "submitted_at"), rs.getObject("reviewer_id") == null ? null : rs.getLong("reviewer_id"),
                rs.getString("reviewer_email"), instant(rs, "reviewed_at"), rs.getString("rejection_reason"), rs.getLong("attendance_submitted"),
                rs.getLong("attendance_present"), rs.getLong("attendance_verified"), events);
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException { Timestamp value = rs.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private void lockInternship(Long id) {
        if (jdbc.queryForList("select id from internship_details where id=? for update", Long.class, id).isEmpty()) throw notFound();
    }
    private static void eligible(InternshipDetails internship) {
        if (internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE)
            throw error(HttpStatus.CONFLICT, "WEEKLY_PROGRESS_NOT_ELIGIBLE", "Complete internship registration before submitting weekly progress");
    }
    private static void requireRevision(Week week, Integer revision) {
        if (revision == null || revision != week.revision()) throw error(HttpStatus.CONFLICT, "WEEK_REVISION_CHANGED", "This report changed; refresh it before continuing");
    }
    private static void role(UserPrincipal principal, String expected) {
        if (principal == null || !principal.active() || !expected.equals(principal.role())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", expected + " access required");
    }
    private static InternshipDetailsException notFound() { return error(HttpStatus.NOT_FOUND, "WEEK_NOT_FOUND", "Weekly record was not found"); }
    private static InternshipDetailsException error(HttpStatus status, String code, String message) { return new InternshipDetailsException(status, code, message); }
}
