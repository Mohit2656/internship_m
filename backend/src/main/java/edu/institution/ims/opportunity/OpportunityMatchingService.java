package edu.institution.ims.opportunity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Publication matching is local and deterministic; semantic evidence remains a separate optional API. */
@Service
public class OpportunityMatchingService {
    public static final String VERSION = "exact-skills-v1";
    public static final String SCORING = "Required skills 80%, preferred skills 20%; exact normalized matches only. "
            + "Weights for unspecified skill groups are redistributed. Eligibility is evaluated separately. "
            + "Resume education, experience and certification references are supporting evidence, not inferred skills. "
            + "Only configured branch, semester, CGPA and skill criteria are evaluated; review other listed requirements manually. "
            + "Rankings cover active students with completed institutional profiles; missing profiles cannot verify eligibility and are not notified. "
            + "A score is not a selection decision.";
    public record Candidate(Long studentId, String studentName, String uid, Branch branch, int semester,
            double matchScore, String eligibility, List<String> eligibilityReasons,
            List<String> matchedSkills, List<String> missingRequiredSkills, List<String> matchedPreferredSkills,
            List<String> contextEvidence, boolean resumeAvailable, boolean strongMatch) {}
    public record Report(String title, String companyName, String scoringExplanation, double strongMatchThreshold,
            List<Candidate> candidates) {}
    public record Run(Long id, Long opportunityId, String triggerType, String algorithmVersion, Instant createdAt, Report report) {}
    public record Summary(Long id, String triggerType, String algorithmVersion, Instant createdAt) {}

    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository opportunitySkills;
    private final StudentProfileRepository studentProfiles;
    private final StudentProfessionalProfileRepository professionalProfiles;
    private final StudentSkillRepository studentSkills;
    private final StudentResumeRepository resumes;
    private final ResumeAnalysisRepository analyses;
    private final ResumeCorrectionService corrections;
    private final SkillNormalizer normalizer;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final double threshold;

    public OpportunityMatchingService(OpportunityRepository opportunities, OpportunitySkillRepository opportunitySkills,
            StudentProfileRepository studentProfiles, StudentProfessionalProfileRepository professionalProfiles,
            StudentSkillRepository studentSkills, StudentResumeRepository resumes, ResumeAnalysisRepository analyses,
            ResumeCorrectionService corrections, SkillNormalizer normalizer, NotificationService notifications,
            JdbcTemplate jdbc, ObjectMapper json, Clock clock,
            @Value("${app.opportunity-matching.strong-match-threshold:75}") double threshold) {
        this.opportunities=opportunities; this.opportunitySkills=opportunitySkills; this.studentProfiles=studentProfiles;
        this.professionalProfiles=professionalProfiles; this.studentSkills=studentSkills; this.resumes=resumes;
        this.analyses=analyses; this.corrections=corrections; this.normalizer=normalizer;
        this.notifications=notifications; this.jdbc=jdbc; this.json=json; this.clock=clock;
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 100)
            throw new IllegalArgumentException("Strong-match threshold must be between 0 and 100");
        this.threshold=threshold;
    }

    /** Called inside the same publication transaction: report, publication and inbox rows commit together. */
    @Transactional
    public Run published(Opportunity opportunity) { return generate(opportunity, "PUBLICATION"); }

    @Transactional
    public Run rerun(Long opportunityId) {
        Opportunity opportunity=opportunities.findByIdForUpdate(opportunityId).orElseThrow(() -> missing("Opportunity"));
        if (opportunity.getStatus()==OpportunityStatus.CLOSED)
            throw new OpportunityException(HttpStatus.CONFLICT,"OPPORTUNITY_CLOSED","Closed opportunity matching history is read-only");
        return generate(opportunity,"MANUAL");
    }

    @Transactional(readOnly=true)
    public List<Summary> history(Long opportunityId) {
        opportunities.findById(opportunityId).orElseThrow(() -> missing("Opportunity"));
        return jdbc.query("select id,trigger_type,algorithm_version,created_at from opportunity_matching_runs where opportunity_id=? order by created_at desc,id desc",
                (r,n) -> new Summary(r.getLong("id"),r.getString("trigger_type"),r.getString("algorithm_version"),r.getTimestamp("created_at").toInstant()),opportunityId);
    }

    @Transactional(readOnly=true)
    public Run get(Long opportunityId, Long runId) {
        return jdbc.query("select * from opportunity_matching_runs where opportunity_id=? and id=?",(r,n) ->
                new Run(r.getLong("id"),r.getLong("opportunity_id"),r.getString("trigger_type"),r.getString("algorithm_version"),
                        r.getTimestamp("created_at").toInstant(),decode(r.getString("report_json"))),opportunityId,runId)
                .stream().findFirst().orElseThrow(() -> missing("Matching report"));
    }

    @Transactional(readOnly=true)
    public Candidate ownScore(UserPrincipal principal, Long opportunityId) {
        Opportunity opportunity=opportunities.findActiveForStudentById(opportunityId,OpportunityStatus.PUBLISHED,LocalDate.now(clock))
                .orElseThrow(() -> missing("Active opportunity"));
        StudentProfile profile=studentProfiles.findByUserId(principal.id()).orElse(null);
        if(profile==null) return new Candidate(principal.id(),null,null,null,0,0,"UNKNOWN",List.of("Complete the institutional profile to verify eligibility"),
                List.of(),opportunitySkills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunityId).stream()
                    .filter(s -> s.getRequirementType()==OpportunitySkillType.REQUIRED).map(OpportunitySkill::getDisplayName).toList(),List.of(),List.of(),false,false);
        return evaluate(opportunity,List.of(profile)).get(0);
    }

    private Run generate(Opportunity opportunity, String trigger) {
        List<Candidate> candidates=evaluate(opportunity,studentProfiles.findAllActiveStudentCandidates());
        Report report=new Report(opportunity.getTitle(),opportunity.getCompanyName(),SCORING,threshold,candidates);
        Instant at=clock.instant();
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement=connection.prepareStatement("insert into opportunity_matching_runs(opportunity_id,trigger_type,algorithm_version,created_at,report_json) values (?,?,?,?,?)",new String[]{"id"});
            statement.setLong(1,opportunity.getId()); statement.setString(2,trigger); statement.setString(3,VERSION);
            statement.setTimestamp(4,Timestamp.from(at)); statement.setString(5,encode(report)); return statement;
        },key);
        if(opportunity.getStatus()==OpportunityStatus.PUBLISHED
                && (opportunity.getApplicationDeadline()==null || !opportunity.getApplicationDeadline().isBefore(LocalDate.now(clock)))) {
            // Match assignment updates' ascending user lock order during notification FK checks.
            // Candidate report ordering remains independent of delivery order.
            for(Candidate candidate:candidates.stream().filter(Candidate::strongMatch)
                    .sorted(Comparator.comparing(Candidate::studentId)).toList()) {
                String fingerprint=candidate.eligibility()+":"+((int)candidate.matchScore()/5)+":"
                        +candidate.matchedSkills().stream().sorted().toList()+":"+candidate.matchedPreferredSkills().stream().sorted().toList();
                notifications.create(candidate.studentId(),"opportunity-match:"+opportunity.getId()+":"+hash(fingerprint),"OPPORTUNITY_MATCH",
                        "Strong match: "+opportunity.getTitle(),"You have been identified as a strong match for "
                        +opportunity.getTitle()+" at "+opportunity.getCompanyName()+" based on your resume and skills. Match score: "
                        +candidate.matchScore()+"/100. Matching skills: "+String.join(", ",combined(candidate.matchedSkills(),candidate.matchedPreferredSkills()))
                        +". This is a recommendation, not company selection. Review the opportunity details and application instructions before applying.",
                        null,null,"/student/opportunities/"+opportunity.getId(),at);
            }
        }
        return new Run(Objects.requireNonNull(key.getKey()).longValue(),opportunity.getId(),trigger,VERSION,at,report);
    }

    private List<Candidate> evaluate(Opportunity opportunity, List<StudentProfile> candidates) {
        List<Long> studentIds=candidates.stream().map(p -> p.getUser().getId()).toList();
        if(studentIds.isEmpty()) return List.of();
        Map<Long,StudentProfessionalProfile> profiles=new HashMap<>();
        professionalProfiles.findAllByStudentIdIn(studentIds).forEach(p -> profiles.put(p.getStudent().getId(),p));
        List<Long> profileIds=profiles.values().stream().map(StudentProfessionalProfile::getId).toList();
        Map<Long,List<StudentSkill>> skills=new HashMap<>();
        Map<Long,StudentResume> activeResumes=new HashMap<>();
        if(!profileIds.isEmpty()) {
            studentSkills.findAllByProfessionalProfileIdInAndActiveTrue(profileIds).forEach(s -> skills.computeIfAbsent(s.getProfessionalProfile().getId(),ignored -> new ArrayList<>()).add(s));
            resumes.findAllByProfessionalProfileIdInAndActiveTrue(profileIds).forEach(r -> activeResumes.put(r.getProfessionalProfile().getId(),r));
        }
        Map<Long,ResumeCorrectionService.Values> values=new HashMap<>();
        List<Long> resumeIds=activeResumes.values().stream().filter(r -> r.getParsingStatus()==ResumeParsingStatus.PARSED).map(StudentResume::getId).toList();
        if(!resumeIds.isEmpty()) analyses.findAllWithExperiencesByResumeIdIn(resumeIds).stream()
                .filter(a -> a.getStatus()==ResumeParsingStatus.PARSED).forEach(a -> values.put(a.getResume().getId(),corrections.effectiveValues(a)));
        List<OpportunitySkill> criteria=opportunitySkills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunity.getId());
        List<OpportunitySkill> required=criteria.stream().filter(s -> s.getRequirementType()==OpportunitySkillType.REQUIRED).toList();
        List<OpportunitySkill> preferred=criteria.stream().filter(s -> s.getRequirementType()==OpportunitySkillType.PREFERRED).toList();
        return candidates.stream().map(profile -> {
            StudentProfessionalProfile professional=profiles.get(profile.getUser().getId());
            Long profileId=professional==null?null:professional.getId();
            StudentResume resume=activeResumes.get(profileId);
            ResumeCorrectionService.Values extracted=resume==null?null:values.get(resume.getId());
            Set<String> own=new HashSet<>();
            skills.getOrDefault(profileId,List.of()).stream()
                    .filter(s -> extracted==null || s.getSource()!=StudentSkillSource.AI_EXTRACTED)
                    .forEach(s -> own.add(s.getNormalizedName()));
            if(extracted!=null) extracted.skills().forEach(s -> own.add(normalize(s)));
            List<String> matched=required.stream().filter(s -> own.contains(s.getNormalizedName())).map(OpportunitySkill::getDisplayName).toList();
            List<String> missing=required.stream().filter(s -> !own.contains(s.getNormalizedName())).map(OpportunitySkill::getDisplayName).toList();
            List<String> matchedPreferred=preferred.stream().filter(s -> own.contains(s.getNormalizedName())).map(OpportunitySkill::getDisplayName).toList();
            double score=score(required.size(),matched.size(),preferred.size(),matchedPreferred.size());
            List<String> reasons=new ArrayList<>(); boolean ineligible=false;
            if(!opportunity.getEligibleBranches().isEmpty() && !opportunity.getEligibleBranches().contains(profile.getBranch())) {
                reasons.add("Branch does not meet the listed requirement"); ineligible=true;
            }
            if(!opportunity.getEligibleSemesters().isEmpty() && !opportunity.getEligibleSemesters().contains(profile.getSemester())) {
                reasons.add("Semester does not meet the listed requirement"); ineligible=true;
            }
            if(opportunity.getMinimumCgpa()!=null) reasons.add("Minimum CGPA cannot be verified from the authoritative student profile");
            String eligibility=ineligible?"INELIGIBLE":opportunity.getMinimumCgpa()!=null?"UNKNOWN":"ELIGIBLE";
            if(reasons.isEmpty()) reasons.add("All configured branch and semester requirements are met; no unverified CGPA restriction");
            boolean strong="ELIGIBLE".equals(eligibility) && score>=threshold && missing.isEmpty() && !criteria.isEmpty() && (!matched.isEmpty() || !matchedPreferred.isEmpty());
            return new Candidate(profile.getUser().getId(),profile.getStudentName(),profile.getUid(),profile.getBranch(),profile.getSemester(),
                    score,eligibility,List.copyOf(reasons),matched,missing,matchedPreferred,context(extracted),resume!=null,strong);
        }).sorted(Comparator.comparingInt((Candidate c) -> "ELIGIBLE".equals(c.eligibility())?0:"UNKNOWN".equals(c.eligibility())?1:2)
                .thenComparing(Candidate::matchScore,Comparator.reverseOrder()).thenComparing(Candidate::studentName,String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Candidate::studentId)).toList();
    }

    static double score(int requiredCount, int requiredMatched, int preferredCount, int preferredMatched) {
        int weight=(requiredCount>0?80:0)+(preferredCount>0?20:0);
        if(weight==0) return 0;
        double earned=(requiredCount>0?80.0*requiredMatched/requiredCount:0)+(preferredCount>0?20.0*preferredMatched/preferredCount:0);
        return Math.round(earned/weight*10000.0)/100.0;
    }
    private static List<String> context(ResumeCorrectionService.Values values) {
        if(values==null) return List.of();
        List<String> signals=new ArrayList<>();
        values.experience().forEach(e -> { if(e.title()!=null) signals.add("Experience: "+e.title()); if(e.company()!=null) signals.add("Company: "+e.company()); });
        values.education().forEach(e -> { if(e.degree()!=null) signals.add("Education: "+e.degree()); if(e.field()!=null) signals.add("Field: "+e.field()); if(e.institution()!=null) signals.add("Institution: "+e.institution()); });
        values.certifications().forEach(c -> signals.add("Certification: "+c));
        return signals.stream().distinct().limit(100).toList();
    }
    private String normalize(String skill) {
        try { return normalizer.normalize(skill).normalizedName(); }
        catch(ResumeException ignored) { return skill.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT); }
    }
    private static List<String> combined(List<String> a,List<String> b) { return java.util.stream.Stream.concat(a.stream(),b.stream()).distinct().toList(); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String encode(Report report) { try { return json.writeValueAsString(report); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private Report decode(String report) { try { return json.readValue(report,Report.class); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private static OpportunityException missing(String type) { return new OpportunityException(HttpStatus.NOT_FOUND,"MATCHING_NOT_FOUND",type+" was not found"); }
}
