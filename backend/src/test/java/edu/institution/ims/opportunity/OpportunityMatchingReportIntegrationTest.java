package edu.institution.ims.opportunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ims_matching_report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class OpportunityMatchingReportIntegrationTest {
    @Autowired OpportunityMatchingService matching; @Autowired OpportunityService service;
    @Autowired OpportunityMatchService semanticMatching;
    @Autowired OpportunityRepository opportunities; @Autowired OpportunitySkillRepository opportunitySkills;
    @Autowired UserRepository users; @Autowired StudentProfileRepository profiles;
    @Autowired StudentProfessionalProfileRepository professionalProfiles; @Autowired StudentSkillService studentSkills;
    @Autowired StudentResumeRepository resumes; @Autowired ResumeAnalysisRepository analyses;
    @Autowired SkillSuggestionGenerator suggestions;
    @Autowired ResumeCorrectionService corrections; @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockitoBean OpportunityMatchClient semanticClient;
    @MockitoSpyBean edu.institution.ims.notification.NotificationService notifications;
    User admin; User student; User other; Opportunity opportunity;

    @BeforeEach void setup() {
        String suffix=UUID.randomUUID().toString();
        admin=users.save(new User("matching-report-admin-"+suffix+"@example.edu",encoder.encode("Password!23"),Role.ADMIN,true));
        student=candidate("First Student",Branch.COMPUTER_ENGINEERING,7,suffix+"a");
        other=candidate("Other Student",Branch.CIVIL,8,suffix+"b");
        opportunity=opportunity(null,List.of(Branch.COMPUTER_ENGINEERING));
        studentSkills.addOwn(UserPrincipal.from(student),new SkillNameRequest("Java"));
        studentSkills.addOwn(UserPrincipal.from(student),new SkillNameRequest("Docker"));
        studentSkills.addOwn(UserPrincipal.from(other),new SkillNameRequest("Java"));
        studentSkills.addOwn(UserPrincipal.from(other),new SkillNameRequest("Docker"));
    }

    @Test void publicationSavesRankedReportAndNotifiesOnlyStrongEligibleStudentOnce() {
        service.publish(opportunity.getId()); service.publish(opportunity.getId());
        List<OpportunityMatchingService.Summary> history=matching.history(opportunity.getId());
        assertThat(history).hasSize(1);
        var run=matching.get(opportunity.getId(),history.get(0).id());
        assertThat(run.triggerType()).isEqualTo("PUBLICATION");
        assertThat(run.report().candidates()).extracting(OpportunityMatchingService.Candidate::studentId).containsExactly(student.getId(),other.getId());
        assertThat(run.report().candidates().get(0).matchScore()).isEqualTo(100);
        assertThat(run.report().candidates().get(0).strongMatch()).isTrue();
        assertThat(run.report().candidates().get(1).eligibility()).isEqualTo("INELIGIBLE");
        assertThat(notices(student)).isEqualTo(1); assertThat(notices(other)).isZero();
        String message=jdbc.queryForObject("select message from student_notifications where student_user_id=? and notification_type='OPPORTUNITY_MATCH'",String.class,student.getId());
        assertThat(message).contains("Example Company","Software Intern","100.0/100","Java","Docker","not company selection","application instructions");
        verifyNoInteractions(semanticClient);
    }

    @Test void weightedScoreUsesNormalizedExactSkillsAndEligibilityNeverFollowsScore() {
        opportunitySkills.saveAndFlush(new OpportunitySkill(opportunity,new NormalizedSkill("SQL","sql"),OpportunitySkillType.REQUIRED));
        var run=matching.rerun(opportunity.getId());
        var first=run.report().candidates().get(0);
        assertThat(first.matchScore()).isEqualTo(60);
        assertThat(first.matchedSkills()).containsExactly("Java");
        assertThat(first.missingRequiredSkills()).containsExactly("SQL");
        assertThat(first.strongMatch()).isFalse();
        assertThat(run.report().candidates().get(1).matchScore()).isEqualTo(60);
        assertThat(run.report().candidates().get(1).eligibility()).isEqualTo("INELIGIBLE");
        assertThat(notices(student)).isZero(); // Draft review never sends student alerts.
    }

    @Test void notificationRecipientLockOrderIsIndependentOfCandidateRanking() {
        User higherId=candidate("A Ranked Student",Branch.COMPUTER_ENGINEERING,7,UUID.randomUUID().toString());
        studentSkills.addOwn(UserPrincipal.from(higherId),new SkillNameRequest("Java"));
        studentSkills.addOwn(UserPrincipal.from(higherId),new SkillNameRequest("Docker"));
        service.publish(opportunity.getId());
        var run=matching.get(opportunity.getId(),matching.history(opportunity.getId()).get(0).id());
        assertThat(run.report().candidates()).filteredOn(OpportunityMatchingService.Candidate::strongMatch)
                .extracting(OpportunityMatchingService.Candidate::studentId).containsExactly(higherId.getId(),student.getId());
        var recipients=org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(notifications,times(2)).create(recipients.capture(),anyString(),eq("OPPORTUNITY_MATCH"),anyString(),anyString(),
                isNull(),isNull(),anyString(),any(java.time.Instant.class));
        assertThat(recipients.getAllValues()).containsExactly(student.getId(),higherId.getId());
    }

    @Test void cgpaIsUnknownAndUnknownStudentsNeverReceiveMatchAlerts() {
        Opportunity restricted=opportunity(new BigDecimal("7.50"),List.of());
        service.publish(restricted.getId());
        var report=matching.get(restricted.getId(),matching.history(restricted.getId()).get(0).id()).report();
        assertThat(report.candidates()).allSatisfy(c -> {
            assertThat(c.matchScore()).isEqualTo(100); assertThat(c.eligibility()).isEqualTo("UNKNOWN");
            assertThat(c.eligibilityReasons().get(0)).contains("CGPA"); assertThat(c.strongMatch()).isFalse();
        });
        assertThat(notices(student)).isZero(); assertThat(notices(other)).isZero();
    }

    @Test void manualRerunsKeepHistoryAndSuppressRepeatedAlertsUnlessMatchedEvidenceChanges() {
        service.publish(opportunity.getId());
        var first=matching.get(opportunity.getId(),matching.history(opportunity.getId()).get(0).id());
        matching.rerun(opportunity.getId());
        assertThat(notices(student)).isEqualTo(1);
        studentSkills.addOwn(UserPrincipal.from(student),new SkillNameRequest("PostgreSQL"));
        opportunitySkills.saveAndFlush(new OpportunitySkill(opportunity,new NormalizedSkill("PostgreSQL","postgresql"),OpportunitySkillType.PREFERRED));
        matching.rerun(opportunity.getId()); matching.rerun(opportunity.getId());
        assertThat(notices(student)).isEqualTo(2);
        assertThat(matching.history(opportunity.getId())).hasSize(4);
        assertThat(matching.get(opportunity.getId(),first.id()).report()).isEqualTo(first.report());
        assertThat(first.report().candidates().get(0).matchedPreferredSkills()).doesNotContain("PostgreSQL");
    }

    @Test void correctedResumeReplacesExtractedSkillsAndOriginalExtractionAndManualSkillsRemainIntact() {
        var professional=professionalProfiles.findByStudentId(student.getId()).orElseThrow();
        var resume=new StudentResume(professional); resume.activate("resume.pdf","matching-"+UUID.randomUUID(),"application/pdf",100);
        resume.markAnalysisParsed(); resumes.saveAndFlush(resume);
        var analysis=new ResumeAnalysis(resume);
        analysis.complete(new AiResumeResult("test","1",List.of("SQL"),List.of(),List.of(),List.of())); analyses.saveAndFlush(analysis);
        suggestions.generate(analysis);
        studentSkills.confirmAllOwn(UserPrincipal.from(student)); // An old accepted AI skill must not bypass its correction.
        var correction=new ResumeCorrectionService.Values(List.of("PostgreSQL"),List.of(new AiExperience("PostgreSQL developer","Example",null,null)),List.of(),List.of("PostgreSQL certificate"));
        corrections.save(student.getId(),UserPrincipal.from(student),new ResumeCorrectionService.Save(resume.getId(),0,correction));
        opportunitySkills.saveAndFlush(new OpportunitySkill(opportunity,new NormalizedSkill("PostgreSQL","postgresql"),OpportunitySkillType.REQUIRED));
        opportunitySkills.saveAndFlush(new OpportunitySkill(opportunity,new NormalizedSkill("SQL","sql"),OpportunitySkillType.REQUIRED));
        var result=matching.rerun(opportunity.getId()).report().candidates().get(0);
        assertThat(result.matchedSkills()).contains("Java","PostgreSQL").doesNotContain("SQL");
        assertThat(result.missingRequiredSkills()).containsExactly("SQL");
        assertThat(result.contextEvidence()).contains("Experience: PostgreSQL developer","Certification: PostgreSQL certificate");
        assertThat(analysis.getSkills()).extracting(ResumeExtractedSkill::getName).containsExactly("SQL");
        assertThat(resume.getStorageKey()).startsWith("matching-");
        service.publish(opportunity.getId());
        when(semanticClient.match(any())).thenThrow(new OpportunityMatchClientException("offline",null));
        semanticMatching.match(UserPrincipal.from(student),opportunity.getId());
        var payload=org.mockito.ArgumentCaptor.forClass(OpportunityMatchRequest.class);
        verify(semanticClient).match(payload.capture());
        assertThat(payload.getValue().student().skills()).extracting(OpportunityMatchRequest.Skill::normalizedName)
                .contains("java","postgresql").doesNotContain("sql");
        assertThat(payload.getValue().student().experience()).containsExactly("PostgreSQL developer at Example");
    }

    @Test void reportHistoryAndStudentScoresRespectRolesAndPrincipalIdentity() throws Exception {
        service.publish(opportunity.getId());
        long runId=matching.history(opportunity.getId()).get(0).id();
        String endpoint="/api/v1/admin/opportunities/"+opportunity.getId()+"/matching-runs";
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        Cookie own=login(student); Cookie administrator=login(admin);
        mvc.perform(get(endpoint).cookie(own)).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).cookie(own)).andExpect(status().isForbidden());
        mvc.perform(get(endpoint).cookie(administrator)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(runId));
        mvc.perform(get(endpoint+"/"+runId).cookie(administrator)).andExpect(status().isOk()).andExpect(jsonPath("$.report.candidates[0].matchScore").value(100));
        mvc.perform(get("/api/v1/student/opportunities/{id}/matching-score",opportunity.getId()).param("studentId",other.getId().toString()).cookie(own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentId").value(student.getId())).andExpect(jsonPath("$.eligibility").value("ELIGIBLE"));
        mvc.perform(get("/api/v1/student/opportunities/{id}/matching-score",opportunity.getId()).cookie(administrator)).andExpect(status().isForbidden());
        Opportunity second=opportunity(null,List.of());
        mvc.perform(get("/api/v1/admin/opportunities/{id}/matching-runs/{runId}",second.getId(),runId).cookie(administrator)).andExpect(status().isNotFound());
        service.close(opportunity.getId());
        mvc.perform(get(endpoint+"/"+runId).cookie(administrator)).andExpect(status().isOk());
        mvc.perform(post(endpoint).cookie(administrator)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/student/opportunities/{id}/matching-score",opportunity.getId()).cookie(own)).andExpect(status().isNotFound());
    }

    private User candidate(String name,Branch branch,int semester,String suffix) {
        User user=users.save(new User("matching-report-"+suffix+"@example.edu",encoder.encode("Password!23"),Role.STUDENT,true));
        StudentProfile p=new StudentProfile(user);
        p.apply(name,branch,"M"+user.getId(),"R"+user.getId(),"Address",null,user.getEmail(),user.getEmail(),"9876543210","Guardian","9876543211","9876543212","O+","Mentor","9876543213","Coordinator","9876543214","CDC","9876543215","2026-2027",semester);
        p.setEncryptedAadhaar("test-encrypted-value","1234"); profiles.saveAndFlush(p); return user;
    }
    private Opportunity opportunity(BigDecimal cgpa,List<Branch> branches) {
        var request=new OpportunityRequest("Example Company","Software Intern","Build Java applications using Docker",null,"Pune",OpportunityMode.HYBRID,3,BigDecimal.ZERO,"INR",LocalDate.now().plusDays(10),cgpa,branches,List.of(),List.of("Java"),List.of("Docker"));
        Opportunity value=opportunities.saveAndFlush(new Opportunity(admin,request));
        opportunitySkills.saveAllAndFlush(List.of(new OpportunitySkill(value,new NormalizedSkill("Java","java"),OpportunitySkillType.REQUIRED),new OpportunitySkill(value,new NormalizedSkill("Docker","docker"),OpportunitySkillType.PREFERRED)));
        return value;
    }
    private long notices(User user) { return jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='OPPORTUNITY_MATCH'",Long.class,user.getId()); }
    private Cookie login(User user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(user.getEmail(),"Password!23")))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
}
