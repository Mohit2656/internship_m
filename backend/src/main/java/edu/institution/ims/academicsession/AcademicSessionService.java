package edu.institution.ims.academicsession;

import edu.institution.ims.security.UserPrincipal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AcademicSessionService {
    private final JdbcTemplate jdbc;
    public AcademicSessionService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Session(Long id,String name,LocalDate startDate,LocalDate endDate,String description,boolean active,long studentCount,
            String status,Instant closedAt,Long closedById,String closedBy) {}
    public record Create(String name,LocalDate startDate,LocalDate endDate,String description,boolean activate) {}
    public record Confirmation(boolean confirmed,String sessionName) {}
    public record DeletionPreview(Session session,Map<String,Long> records,long recordCount,boolean canDelete,String explanation) {}
    public record Event(long id,long sessionId,String sessionName,String action,Long performedBy,String performedByEmail,Instant performedAt) {}
    private static final String SELECT="select s.*,case when c.active_session_id=s.id then true else false end as active,"
            +"(select count(*) from users u where u.academic_session_id=s.id) as student_count,closer.email as closed_by_email "
            +"from academic_sessions s cross join academic_session_settings c left join users closer on closer.id=s.closed_by ";
    private Session map(ResultSet rs,int row) throws SQLException {
        return new Session(rs.getLong("id"),rs.getString("name"),rs.getDate("start_date")==null?null:rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date")==null?null:rs.getDate("end_date").toLocalDate(),rs.getString("description"),rs.getBoolean("active"),rs.getLong("student_count"),
                rs.getBoolean("active")?"ACTIVE":rs.getTimestamp("closed_at")!=null?"CLOSED":"INACTIVE",
                rs.getTimestamp("closed_at")==null?null:rs.getTimestamp("closed_at").toInstant(),rs.getObject("closed_by",Long.class),rs.getString("closed_by_email"));
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Session> list() {
        return jdbc.query(SELECT+"order by s.name desc,s.id desc",this::map);
    }
    @Transactional(readOnly=true)
    public Long activeId() { return jdbc.queryForObject("select active_session_id from academic_session_settings where id=1",Long.class); }
    @Transactional(readOnly=true)
    public Long selectedId(Long id) {
        if(id==null) { Long active=activeId(); return active==null?-1L:active; }
        if(id<1 || jdbc.queryForObject("select count(*) from academic_sessions where id=?",Long.class,id)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Academic session not found");
        return id;
    }
    /** The same singleton lock is used by activation and every student creation path. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Long activeForNewStudent() {
        Long id=jdbc.queryForObject("select active_session_id from academic_session_settings where id=1 for update",Long.class);
        if(id==null || jdbc.queryForObject("select count(*) from academic_sessions where id=? and closed_at is null",Long.class,id)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No active academic session. Ask IIIC to activate a session in Settings before creating student accounts.");
        return id;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session create(UserPrincipal actor,Create request) {
        String name=normalize(request.name()); String description=clean(request.description());
        if(name.isEmpty() || name.length()>60 || (description!=null && description.length()>1000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a session name of 1–60 characters and description of at most 1000 characters");
        if(request.startDate()==null || request.endDate()==null || !request.endDate().isAfter(request.startDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Session end date must be after its start date");
        lock();
        if(jdbc.queryForObject("select count(*) from academic_sessions where name=?",Long.class,name)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An academic session with this name already exists");
        if(jdbc.queryForObject("select count(*) from academic_sessions where start_date<=? and end_date>=?",Long.class,request.endDate(),request.startDate())>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Academic session dates must not overlap an existing dated session");
        jdbc.update("insert into academic_sessions(name,start_date,end_date,description,created_by) values(?,?,?,?,?)",name,request.startDate(),request.endDate(),description,actor.id());
        Long id=jdbc.queryForObject("select id from academic_sessions where name=?",Long.class,name);
        audit(actor,id,name,"CREATED");
        if(request.activate()) activateLocked(actor,id);
        return session(id);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session activate(UserPrincipal actor,Long id) {
        lock(); activateLocked(actor,id);
        return session(id);
    }
    /** Supply missing historical calendar metadata without changing membership/name. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session dateHistorical(UserPrincipal actor,Long id,Create request) {
        if(request.startDate()==null || request.endDate()==null || !request.endDate().isAfter(request.startDate())
                || (request.description()!=null && request.description().length()>1000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter valid session dates and a description of at most 1000 characters");
        lock();
        Session existing=session(id);
        if(request.activate() && existing.closedAt()!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This session is closed. Record dates without activation, then explicitly reopen it if required.");
        if(jdbc.queryForObject("select count(*) from academic_sessions where id=? and start_date is null",Long.class,id)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only missing historical calendar dates can be supplied; recorded dates remain unchanged");
        if(jdbc.queryForObject("select count(*) from academic_sessions where id<>? and start_date<=? and end_date>=?",Long.class,id,request.endDate(),request.startDate())>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Academic session dates must not overlap an existing dated session");
        jdbc.update("update academic_sessions set start_date=?,end_date=?,description=? where id=?",request.startDate(),request.endDate(),clean(request.description()),id);
        audit(actor,id,existing.name(),"DATES_RECORDED");
        if(request.activate()) activateLocked(actor,id);
        return session(id);
    }
    private void activateLocked(UserPrincipal actor,Long id) {
        Session target=session(id);
        if(target.closedAt()!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This session is closed. An administrator must explicitly reopen it.");
        if(target.startDate()==null || target.endDate()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a session with recorded start and end dates can be activated");
        if(target.active()) return;
        Long previous=activeId();
        if(previous!=null) closeLocked(actor,session(previous));
        jdbc.update("update academic_session_settings set active_session_id=?,activated_by=?,activated_at=current_timestamp where id=1",id,actor.id());
        audit(actor,id,target.name(),"ACTIVATED");
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session close(UserPrincipal actor,Long id,Confirmation confirmation) {
        lock(); Session target=lockSession(id); confirm(target,confirmation);
        if(target.closedAt()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"This academic session is already closed");
        closeLocked(actor,target); return session(id);
    }
    private void closeLocked(UserPrincipal actor,Session target) {
        jdbc.update("update academic_sessions set closed_at=current_timestamp,closed_by=? where id=?",actor.id(),target.id());
        jdbc.update("update academic_session_settings set active_session_id=null,activated_by=null,activated_at=null where id=1 and active_session_id=?",target.id());
        audit(actor,target.id(),target.name(),"CLOSED");
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session reopen(UserPrincipal actor,Long id,Confirmation confirmation) {
        lock(); Session target=lockSession(id); confirm(target,confirmation);
        if(target.closedAt()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a closed session can be reopened");
        if(target.startDate()==null || target.endDate()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Record the historical session dates before reopening it");
        jdbc.update("update academic_sessions set closed_at=null,closed_by=null where id=?",id);
        audit(actor,id,target.name(),"REOPENED");
        activateLocked(actor,id); return session(id);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public DeletionPreview deletionPreview(Long id) { return preview(session(id)); }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(UserPrincipal actor,Long id,Confirmation confirmation) {
        lock(); Session target=lockSession(id); confirm(target,confirmation);
        DeletionPreview checked=preview(target);
        if(!checked.canDelete()) throw new ResponseStatusException(HttpStatus.CONFLICT,checked.explanation());
        // Restrictive database FKs are the final guard for concurrent or future dependencies.
        try { jdbc.update("delete from academic_sessions where id=?",id); }
        catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"This session has dependent records and cannot be deleted. Close it to preserve its history."); }
        audit(actor,id,target.name(),"DELETED");
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Event> history(Long id) {
        return jdbc.query("select e.*,u.email as performed_by_email from academic_session_events e left join users u on u.id=e.performed_by where e.session_id=? order by e.id",
                (rs,row)->new Event(rs.getLong("id"),rs.getLong("session_id"),rs.getString("session_name"),rs.getString("action"),rs.getObject("performed_by",Long.class),rs.getString("performed_by_email"),rs.getTimestamp("performed_at").toInstant()),id);
    }
    private Session session(Long id) {
        return jdbc.query(SELECT+"where s.id=?",this::map,id).stream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Academic session not found"));
    }
    private Session lockSession(Long id) {
        if(jdbc.queryForList("select id from academic_sessions where id=? for update",id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Academic session not found");
        return session(id);
    }
    private static void confirm(Session target,Confirmation confirmation) {
        if(confirmation==null || !confirmation.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit confirmation is required");
        if(!target.name().equals(confirmation.sessionName())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Session identity changed. Reload the confirmation.");
    }
    private void audit(UserPrincipal actor,Long id,String name,String action) {
        jdbc.update("insert into academic_session_events(session_id,session_name,action,performed_by) values(?,?,?,?)",id,name,action,actor.id());
    }
    private DeletionPreview preview(Session target) {
        String students="select id from users where academic_session_id=?";
        String internships="select i.id from internship_details i join internship_onboarding o on o.id=i.onboarding_id where o.student_user_id in ("+students+")";
        Map<String,Long> counts=new LinkedHashMap<>();
        counts.put("Internships",count("internship_details","id in ("+internships+")",target.id()));
        counts.put("Attendance records",count("attendance_records","student_user_id in ("+students+")",target.id()));
        counts.put("Weekly reports",count("weekly_progress_reports","period_id in (select id from weekly_internship_periods where internship_id in ("+internships+"))",target.id()));
        counts.put("Monthly reports",count("monthly_progress_reports","student_internship_id in ("+internships+")",target.id()));
        counts.put("Project evaluations",count("project_seminar_evaluations","student_user_id in ("+students+")",target.id()));
        counts.put("Completion history",jdbc.queryForObject("select count(*) from internship_completion_events where academic_session_id=? or student_user_id in ("+students+")",Long.class,target.id(),target.id()));
        // These are direct historical snapshots and may reference a session independently
        // of a student's current membership. Count them even when no students remain.
        long records=counts.values().stream().mapToLong(Long::longValue).sum();
        boolean allowed=!target.active() && target.studentCount()==0 && records==0;
        String explanation=target.active()?"The active session cannot be deleted. Close it first.":
                !allowed?"This session contains student accounts or historical records and cannot be permanently deleted. Close the session to preserve its history.":
                "Only this empty session will be permanently deleted. Its audit history is retained. This action cannot be undone.";
        return new DeletionPreview(target,Collections.unmodifiableMap(counts),records,allowed,explanation);
    }
    private long count(String table,String predicate,Long id) { return jdbc.queryForObject("select count(*) from "+table+" where "+predicate,Long.class,id); }
    private void lock() { jdbc.queryForObject("select id from academic_session_settings where id=1 for update",Integer.class); }
    private static String clean(String value) { return value==null||value.isBlank()?null:value.strip(); }
    static String normalize(String value) {
        String name=value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).strip().replaceAll("[–—−]","-").replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
        if(name.matches("\\d{4}\\s*-\\s*\\d{2}")) { String[] parts=name.split("\\s*-\\s*"); int start=Integer.parseInt(parts[0]); int end=(start/100)*100+Integer.parseInt(parts[1]); if(end<=start)end+=100; name=start+"-"+end; }
        return name.replaceAll("\\s*-\\s*","-");
    }
}
