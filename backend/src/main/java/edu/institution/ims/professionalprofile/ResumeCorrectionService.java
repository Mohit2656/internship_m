package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Corrections are append-only overlays. The AI extraction and uploaded file stay intact. */
@Service
public class ResumeCorrectionService {
    public record Values(List<String> skills, List<AiExperience> experience, List<AiEducation> education, List<String> certifications) {
        public Values validated() {
            AiResumeResult clean = new AiResumeResult("correction", "1", skills, experience, education, certifications).validated();
            return new Values(clean.skills(), clean.experience(), clean.education(), clean.certifications());
        }
        static Values from(ResumeAnalysis a) {
            return new Values(a.getSkills().stream().map(ResumeExtractedSkill::getName).toList(),
                    a.getExperiences().stream().map(e -> new AiExperience(e.getTitle(),e.getCompany(),e.getStartDate(),e.getEndDate())).toList(),
                    a.getEducation().stream().map(e -> new AiEducation(e.getDegree(),e.getField(),e.getInstitution())).toList(),
                    a.getCertifications().stream().map(ResumeExtractedCertification::getName).toList());
        }
    }
    public record Change(int revision, String source, Instant editedAt, Long editorId, String editorName, Values values) {}
    public record Corrections(Long resumeId, ResumeAnalysisResponse original, Values values, int revision,
            String source, Instant editedAt, Long editorId, String editorName, List<Change> history) {}
    public record Save(Long resumeId, Integer revision, Values values) {}
    private final StudentProfessionalProfileRepository profiles;
    private final StudentResumeRepository resumes;
    private final ResumeAnalysisRepository analyses;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final NotificationService notifications;

    public ResumeCorrectionService(StudentProfessionalProfileRepository profiles, StudentResumeRepository resumes,
            ResumeAnalysisRepository analyses, JdbcTemplate jdbc, ObjectMapper json, Clock clock, NotificationService notifications) {
        this.profiles=profiles; this.resumes=resumes; this.analyses=analyses; this.jdbc=jdbc;
        this.json=json; this.clock=clock; this.notifications=notifications;
    }

    @Transactional(readOnly=true)
    public Corrections get(Long studentId) { return response(activeAnalysis(studentId, false)); }

    @Transactional
    public Corrections save(Long studentId, UserPrincipal editor, Save request) {
        if (editor == null || !editor.active() || !("ADMIN".equals(editor.role())
                || ("STUDENT".equals(editor.role()) && editor.id().equals(studentId))))
            throw error(HttpStatus.FORBIDDEN,"RESUME_EDIT_FORBIDDEN","You cannot edit this resume");
        ResumeAnalysis analysis=activeAnalysis(studentId,true);
        Corrections current=response(analysis);
        if(request == null || request.values()==null || request.revision()==null || request.resumeId()==null)
            throw error(HttpStatus.BAD_REQUEST,"INVALID_CORRECTION","Resume, revision and extracted values are required");
        if(!request.resumeId().equals(current.resumeId()) || request.revision()!=current.revision())
            throw error(HttpStatus.CONFLICT,"RESUME_CHANGED","This resume changed. Reload before saving corrections");
        Values values;
        try { values=request.values().validated(); }
        catch(IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST,"INVALID_CORRECTION","Check the extracted fields and entry lengths"); }
        String source="ADMIN".equals(editor.role())?"IIIC_REVIEWED":"STUDENT_CORRECTED";
        if(values.equals(current.values()) && source.equals(current.source())) return current;
        String name=jdbc.queryForList("select coalesce(p.student_name,sa.student_name,u.email) from users u "
                + "left join student_profiles p on p.user_id=u.id left join student_accounts sa on sa.user_id=u.id where u.id=?",
                String.class,editor.id()).stream().findFirst().orElse(editor.email());
        Instant at=clock.instant();
        jdbc.update("insert into resume_corrections(resume_id,revision,values_json,source,editor_user_id,editor_name,edited_at) values (?,?,?,?,?,?,?)",
                current.resumeId(),current.revision()+1,encode(values),source,editor.id(),name,Timestamp.from(at));
        if("STUDENT_CORRECTED".equals(source)) notifications.notifyCoordinators(studentId,
                "resume-corrected:"+current.resumeId()+":"+(current.revision()+1),"RESUME_CORRECTED",
                "Resume information corrected"," corrected their parsed resume information.",at);
        return response(analysis);
    }

    /** Matching uses the current overlay; AI-original APIs continue to return original extraction. */
    @Transactional(readOnly=true)
    public Values effectiveValues(ResumeAnalysis analysis) {
        return history(analysis.getResume().getId()).stream().findFirst().map(Change::values).orElseGet(() -> Values.from(analysis));
    }

    private ResumeAnalysis activeAnalysis(Long studentId, boolean lock) {
        var profile=(lock?profiles.findByStudentIdForUpdate(studentId):profiles.findByStudentId(studentId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"RESUME_NOT_FOUND","No resume found"));
        var resume=resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND,"RESUME_NOT_FOUND","No resume found"));
        return analyses.findByResumeId(resume.getId()).filter(a -> a.getStatus()==ResumeParsingStatus.PARSED)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,"RESUME_NOT_PARSED","Analyze the current resume before editing extracted information"));
    }
    private Corrections response(ResumeAnalysis a) {
        List<Change> history=history(a.getResume().getId());
        Change last=history.isEmpty()?null:history.get(0);
        return new Corrections(a.getResume().getId(),ResumeAnalysisResponse.from(a),last==null?Values.from(a):last.values(),
                last==null?0:last.revision(),last==null?"AI_EXTRACTED":last.source(),last==null?null:last.editedAt(),
                last==null?null:last.editorId(),last==null?null:last.editorName(),history);
    }
    private List<Change> history(Long resumeId) {
        return jdbc.query("select * from resume_corrections where resume_id=? order by revision desc",(r,n) ->
                new Change(r.getInt("revision"),r.getString("source"),r.getTimestamp("edited_at").toInstant(),
                        r.getObject("editor_user_id",Long.class),r.getString("editor_name"),decode(r.getString("values_json"))),resumeId);
    }
    private String encode(Values values) { try { return json.writeValueAsString(values); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private Values decode(String values) { try { return json.readValue(values,Values.class); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private static ResumeException error(HttpStatus status,String code,String message) { return new ResumeException(status,code,message); }
}
