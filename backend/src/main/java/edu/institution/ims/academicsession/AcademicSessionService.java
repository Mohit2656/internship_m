package edu.institution.ims.academicsession;

import edu.institution.ims.security.UserPrincipal;
import java.sql.*;
import java.time.*;
import java.util.*;
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
    public record Session(Long id,String name,LocalDate startDate,LocalDate endDate,String description,boolean active,long studentCount) {}
    public record Create(String name,LocalDate startDate,LocalDate endDate,String description,boolean activate) {}
    private Session map(ResultSet rs,int row) throws SQLException {
        return new Session(rs.getLong("id"),rs.getString("name"),rs.getDate("start_date")==null?null:rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date")==null?null:rs.getDate("end_date").toLocalDate(),rs.getString("description"),rs.getBoolean("active"),rs.getLong("student_count"));
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Session> list() {
        return jdbc.query("select s.*,case when c.active_session_id=s.id then true else false end as active,"
                +"(select count(*) from users u where u.role='STUDENT' and u.academic_session_id=s.id) as student_count "
                +"from academic_sessions s cross join academic_session_settings c order by s.name desc,s.id desc",this::map);
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
        if(id==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"No active academic session. Ask IIIC to activate a session in Settings before creating student accounts.");
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
        if(request.activate()) activateLocked(actor,id);
        return list().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session activate(UserPrincipal actor,Long id) {
        lock(); activateLocked(actor,id);
        return list().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();
    }
    /** Supply missing historical calendar metadata without changing membership/name. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Session dateHistorical(UserPrincipal actor,Long id,Create request) {
        if(request.startDate()==null || request.endDate()==null || !request.endDate().isAfter(request.startDate())
                || (request.description()!=null && request.description().length()>1000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter valid session dates and a description of at most 1000 characters");
        lock();
        if(jdbc.queryForObject("select count(*) from academic_sessions where id=? and start_date is null",Long.class,id)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only missing historical calendar dates can be supplied; recorded dates remain unchanged");
        if(jdbc.queryForObject("select count(*) from academic_sessions where id<>? and start_date<=? and end_date>=?",Long.class,id,request.endDate(),request.startDate())>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Academic session dates must not overlap an existing dated session");
        jdbc.update("update academic_sessions set start_date=?,end_date=?,description=? where id=?",request.startDate(),request.endDate(),clean(request.description()),id);
        if(request.activate()) activateLocked(actor,id);
        return list().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();
    }
    private void activateLocked(UserPrincipal actor,Long id) {
        if(jdbc.queryForObject("select count(*) from academic_sessions where id=? and start_date is not null and end_date is not null",Long.class,id)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a session with recorded start and end dates can be activated");
        jdbc.update("update academic_session_settings set active_session_id=?,activated_by=?,activated_at=current_timestamp where id=1",id,actor.id());
    }
    private void lock() { jdbc.queryForObject("select id from academic_session_settings where id=1 for update",Integer.class); }
    private static String clean(String value) { return value==null||value.isBlank()?null:value.strip(); }
    static String normalize(String value) {
        String name=value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).strip().replaceAll("[–—−]","-").replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
        if(name.matches("\\d{4}\\s*-\\s*\\d{2}")) { String[] parts=name.split("\\s*-\\s*"); int start=Integer.parseInt(parts[0]); int end=(start/100)*100+Integer.parseInt(parts[1]); if(end<=start)end+=100; name=start+"-"+end; }
        return name.replaceAll("\\s*-\\s*","-");
    }
}
