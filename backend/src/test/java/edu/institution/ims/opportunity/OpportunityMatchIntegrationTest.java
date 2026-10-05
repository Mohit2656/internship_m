package edu.institution.ims.opportunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OpportunityMatchIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
    @Autowired OpportunityRepository opportunities; @Autowired OpportunitySkillRepository opportunitySkills;
    @Autowired OpportunityAiAnalysisRepository opportunityAnalyses; @Autowired StudentProfileRepository studentProfiles;
    @Autowired StudentProfessionalProfileRepository professionalProfiles; @Autowired StudentSkillRepository studentSkills;
    @MockitoBean OpportunityMatchClient matchClient;
    User admin; User student; User otherStudent; User mentor; Cookie studentSession; Opportunity opportunity;

    @BeforeEach void setup() throws Exception {
        opportunityAnalyses.deleteAll(); opportunitySkills.deleteAll(); opportunities.deleteAll();
        studentSkills.deleteAll(); professionalProfiles.deleteAll(); studentProfiles.deleteAll(); users.deleteAll();
        String suffix = UUID.randomUUID().toString();
        admin = users.save(new User("match-admin-" + suffix + "@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("match-student-" + suffix + "@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        otherStudent = users.save(new User("match-other-" + suffix + "@example.edu", encoder.encode("OtherPassword!23"), Role.STUDENT, true));
        mentor = users.save(new User("match-mentor-" + suffix + "@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        studentSession = login(student.getEmail(), "StudentPassword!23");
        createInstitutionalProfile(student, studentSession, Branch.COMPUTER_ENGINEERING, 7);
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Java\"}"))
                .andExpect(status().isOk());
        opportunity = createOpportunity(OpportunityStatus.PUBLISHED, LocalDate.now().plusDays(10),
                List.of(Branch.COMPUTER_ENGINEERING), List.of(7), new BigDecimal("7.50"));
        opportunitySkills.saveAll(List.of(skill("Java", OpportunitySkillType.REQUIRED), skill("PostgreSQL", OpportunitySkillType.REQUIRED), skill("Docker", OpportunitySkillType.PREFERRED)));
        when(matchClient.match(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));
        when(matchClient.matchAll(anyList())).thenAnswer(invocation -> ((List<OpportunityMatchRequest>) invocation.getArgument(0)).stream().map(this::result).toList());
    }

    @AfterEach void cleanup() {
        opportunityAnalyses.deleteAll(); opportunitySkills.deleteAll(); opportunities.deleteAll();
        studentSkills.deleteAll(); professionalProfiles.deleteAll(); studentProfiles.deleteAll(); users.deleteAll();
    }

    @Test void authenticatedStudentGetsDeterministicEligibilityAndSeparatedSkillEvidence() throws Exception {
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibility.overall").value("UNKNOWN"))
                .andExpect(jsonPath("$.eligibility.criteria[0].status").value("PASS"))
                .andExpect(jsonPath("$.eligibility.criteria[1].status").value("PASS"))
                .andExpect(jsonPath("$.eligibility.criteria[2].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.explanation.summary").value("Your professional profile has relevant alignment, but all eligibility requirements could not be verified."))
                .andExpect(jsonPath("$.evidence.requiredSkills[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.evidence.requiredSkills[1].matchType").value("MISSING"))
                .andExpect(jsonPath("$.evidence.preferredSkills[0].opportunitySkill").value("Docker"))
                .andExpect(jsonPath("$.explanation.alignedSkills[0].detail").value("Exact required skill match."))
                .andExpect(jsonPath("$.explanation.skillsToStrengthen[0].skill").value("PostgreSQL"))
                .andExpect(jsonPath("$.explanation.additionalPreferredSkills[0].skill").value("Docker"));
        ArgumentCaptor<OpportunityMatchRequest> captor = ArgumentCaptor.forClass(OpportunityMatchRequest.class);
        verify(matchClient).match(captor.capture());
        OpportunityMatchRequest payload = captor.getValue();
        assertThat(payload.student().skills()).extracting(OpportunityMatchRequest.Skill::normalizedName).containsExactly("java");
        assertThat(json.writeValueAsString(payload)).doesNotContain(student.getEmail(), "studentId", "userId", "uid", "phone", "address");
    }

    @Test void principalIdentityWinsEvenWhenCallerAddsStudentIdAndRemovedSkillsAreExcluded() throws Exception {
        MvcResult added = mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docker\"}"))
                .andExpect(status().isOk()).andReturn();
        long dockerId = json.readTree(added.getResponse().getContentAsString()).path("skills").findValuesAsText("displayName").indexOf("Docker");
        StudentProfessionalProfile profile = professionalProfiles.findByStudentId(student.getId()).orElseThrow();
        StudentSkill docker = studentSkills.findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(profile.getId(), "docker").orElseThrow();
        mvc.perform(delete("/api/v1/student/professional-profile/skills/{id}", docker.getId()).cookie(studentSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).param("studentId", otherStudent.getId().toString()).cookie(studentSession)).andExpect(status().isOk());
        ArgumentCaptor<OpportunityMatchRequest> captor = ArgumentCaptor.forClass(OpportunityMatchRequest.class); verify(matchClient).match(captor.capture());
        assertThat(captor.getValue().student().skills()).extracting(OpportunityMatchRequest.Skill::normalizedName).containsExactly("java");
        assertThat(dockerId).isGreaterThanOrEqualTo(0);
    }

    @Test void knownBranchOrSemesterFailureMakesOverallIneligible() throws Exception {
        Opportunity mismatch = createOpportunity(OpportunityStatus.PUBLISHED, LocalDate.now().plusDays(10), List.of(Branch.CIVIL), List.of(8), null);
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", mismatch.getId()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibility.overall").value("INELIGIBLE"))
                .andExpect(jsonPath("$.eligibility.criteria[0].status").value("FAIL"))
                .andExpect(jsonPath("$.eligibility.criteria[1].status").value("FAIL"))
                .andExpect(jsonPath("$.eligibility.criteria[2].status").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.explanation.summary").value("One or more listed eligibility requirements are not met. Professional alignment is shown separately below."));
    }

    @Test void eligibleAndRelatedExplanationsAreDeterministicAndNotPercentages() throws Exception {
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"MySQL\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docker\"}"))
                .andExpect(status().isOk());
        Opportunity eligible = createOpportunity(OpportunityStatus.PUBLISHED, LocalDate.now().plusDays(10),
                List.of(Branch.COMPUTER_ENGINEERING), List.of(7), null);
        opportunitySkills.saveAll(List.of(
                new OpportunitySkill(eligible, new NormalizedSkill("Java", "java"), OpportunitySkillType.REQUIRED),
                new OpportunitySkill(eligible, new NormalizedSkill("PostgreSQL", "postgresql"), OpportunitySkillType.REQUIRED),
                new OpportunitySkill(eligible, new NormalizedSkill("Docker", "docker"), OpportunitySkillType.PREFERRED)));
        doReturn(new OpportunityMatchResult("model", "1", new BigDecimal("0.72"), List.of(
                new OpportunityMatchResult.SkillMatch("Java", "EXACT", "Java", BigDecimal.ONE),
                new OpportunityMatchResult.SkillMatch("PostgreSQL", "RELATED", "MySQL", new BigDecimal("0.80"))),
                List.of(new OpportunityMatchResult.SkillMatch("Docker", "EXACT", "Docker", BigDecimal.ONE)), List.of(), List.of()))
                .when(matchClient).match(any());
        String body = mvc.perform(get("/api/v1/student/opportunities/{id}/match", eligible.getId()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibility.overall").value("ELIGIBLE"))
                .andExpect(jsonPath("$.explanation.summary").value("Your profile aligns with the listed required skills. Review the evidence below before making your decision."))
                .andExpect(jsonPath("$.explanation.alignedSkills[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.explanation.alignedSkills[1].matchType").value("RELATED"))
                .andExpect(jsonPath("$.explanation.alignedSkills[1].detail").value("Related required skill: MySQL."))
                .andExpect(jsonPath("$.explanation.alignedSkills[2].detail").value("Exact match for a preferred skill."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("%", "probability", "confidence", "should apply");
    }

    @Test void noConfirmedSkillsReturnsProfileCompletionExplanation() throws Exception {
        StudentProfessionalProfile profile = professionalProfiles.findByStudentId(student.getId()).orElseThrow();
        studentSkills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(profile.getId()).forEach(skill -> { skill.deactivate(); studentSkills.save(skill); });
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileNotice").value("No confirmed skills are currently available for semantic skill comparison."))
                .andExpect(jsonPath("$.explanation.alignedSkills").isEmpty())
                .andExpect(jsonPath("$.explanation.skillsToStrengthen.length()").value(2));
    }

    @Test void nonVisibleOpportunitiesAndNonStudentsCannotMatch() throws Exception {
        List<Opportunity> hidden = List.of(createOpportunity(OpportunityStatus.DRAFT, LocalDate.now().plusDays(2), List.of(), List.of(), null),
                createOpportunity(OpportunityStatus.CLOSED, LocalDate.now().plusDays(2), List.of(), List.of(), null),
                createOpportunity(OpportunityStatus.PUBLISHED, LocalDate.now().minusDays(1), List.of(), List.of(), null));
        for (Opportunity value : hidden) mvc.perform(get("/api/v1/student/opportunities/{id}/match", value.getId()).cookie(studentSession)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(login(admin.getEmail(), "AdminPassword!23"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
    }

    @Test void staleAiSignalsAreExcludedWhileCurrentSignalsAreIncluded() throws Exception {
        OpportunityAiAnalysis current = analysis(opportunity, "Backend Development");
        opportunityAnalyses.saveAndFlush(current);
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(studentSession)).andExpect(status().isOk());
        ArgumentCaptor<OpportunityMatchRequest> currentCaptor = ArgumentCaptor.forClass(OpportunityMatchRequest.class); verify(matchClient).match(currentCaptor.capture());
        assertThat(currentCaptor.getValue().opportunity().roleSignals()).containsExactly("Backend Development");
        clearInvocations(matchClient);
        OpportunityRequest changed = request(LocalDate.now().plusDays(10), List.of(Branch.COMPUTER_ENGINEERING), List.of(7), new BigDecimal("7.50"));
        opportunity.apply(new OpportunityRequest(changed.companyName(), changed.title(), "Changed description", changed.companyWebsite(), changed.location(), changed.internshipMode(), changed.durationMonths(), changed.stipendAmount(), changed.currency(), changed.applicationDeadline(), changed.minimumCgpa(), changed.eligibleBranches(), changed.eligibleSemesters(), changed.requiredSkills(), changed.preferredSkills()));
        opportunities.saveAndFlush(opportunity);
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(studentSession)).andExpect(status().isOk());
        ArgumentCaptor<OpportunityMatchRequest> staleCaptor = ArgumentCaptor.forClass(OpportunityMatchRequest.class); verify(matchClient).match(staleCaptor.capture());
        assertThat(staleCaptor.getValue().opportunity().roleSignals()).isEmpty();
    }

    @Test void semanticFailureLeavesEligibilityIntactAndDoesNotInventMissingSkills() throws Exception {
        doThrow(new OpportunityMatchClientException("offline", null)).when(matchClient).match(any());
        mvc.perform(get("/api/v1/student/opportunities/{id}/match", opportunity.getId()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibility.criteria[0].status").value("PASS"))
                .andExpect(jsonPath("$.semanticStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.evidence").isEmpty())
                .andExpect(jsonPath("$.explanation.summary").value("Eligibility information is available, but professional alignment could not be analyzed right now."));
    }

    @Test void adminTalentDiscoveryUsesOneBatchAndDefaultsToEligibleOrUnknownCandidates() throws Exception {
        Cookie otherSession = login(otherStudent.getEmail(), "OtherPassword!23");
        createInstitutionalProfile(otherStudent, otherSession, Branch.CIVIL, 8, "Civil Student");
        String body = mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", opportunity.getId())
                        .cookie(login(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.candidates[0].studentId").value(student.getId()))
                .andExpect(jsonPath("$.candidates[0].alignment.eligibility.overall").value("UNKNOWN"))
                .andExpect(jsonPath("$.candidates[0].exactRequiredSkills").value(1))
                .andExpect(jsonPath("$.candidates[0].alignment.evidence.requiredSkills[1].matchType").value("MISSING"))
                .andReturn().getResponse().getContentAsString();
        verify(matchClient, times(1)).matchAll(anyList());
        assertThat(body).doesNotContain(student.getEmail(), otherStudent.getEmail(), "phoneNumber", "address", "password", "aadhaar");
    }

    @Test void talentDiscoveryFiltersSearchesOrdersAndPaginatesWithoutScores() throws Exception {
        Cookie otherSession = login(otherStudent.getEmail(), "OtherPassword!23");
        createInstitutionalProfile(otherStudent, otherSession, Branch.CIVIL, 8, "Civil Student");
        String adminEmail = admin.getEmail(); Cookie adminSession = login(adminEmail, "AdminPassword!23");
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", opportunity.getId())
                        .param("eligibility", "ALL").param("page", "0").param("size", "1").cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.candidates[0].uid").value("UID-" + student.getId()));
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", opportunity.getId())
                        .param("eligibility", "INELIGIBLE").param("branch", "CIVIL").param("semester", "8").param("search", "Civil")
                        .cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.candidates[0].studentId").value(otherStudent.getId()));
        String filtered = mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", opportunity.getId())
                        .param("skill", "JAVA").cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(filtered).doesNotContain("score", "percentage", "probability", "topCandidate", "hire");
    }

    @Test void talentDiscoveryIsAdminOnlyAndClosedOpportunitiesAreRejected() throws Exception {
        String endpoint = "/api/v1/admin/opportunities/" + opportunity.getId() + "/talent-discovery";
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(get(endpoint).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
        Opportunity closed = createOpportunity(OpportunityStatus.CLOSED, LocalDate.now().plusDays(2), List.of(), List.of(), null);
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", closed.getId())
                        .cookie(login(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPPORTUNITY_CLOSED"));
    }

    @Test void talentDiscoveryKeepsEligibilityWhenBatchAiIsUnavailableAndAllowsDrafts() throws Exception {
        doThrow(new OpportunityMatchClientException("offline", null)).when(matchClient).matchAll(anyList());
        Opportunity draft = createOpportunity(OpportunityStatus.DRAFT, LocalDate.now().plusDays(2), List.of(Branch.COMPUTER_ENGINEERING), List.of(7), null);
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery", draft.getId())
                        .cookie(login(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].alignment.eligibility.overall").value("ELIGIBLE"))
                .andExpect(jsonPath("$.candidates[0].alignment.semanticStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].alignment.evidence").isEmpty());
    }

    @Test void studentSkillGapsAggregateRequiredPreferredAndExactEvidenceWithoutScores() throws Exception {
        String body = mvc.perform(get("/api/v1/student/skill-gaps").param("studentId", otherStudent.getId().toString()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.opportunitiesAnalyzed").value(1))
                .andExpect(jsonPath("$.skillsToStrengthen[0].skill").value("PostgreSQL"))
                .andExpect(jsonPath("$.skillsToStrengthen[0].requiredByCount").value(1))
                .andExpect(jsonPath("$.skillsToStrengthen[1].skill").value("Docker"))
                .andExpect(jsonPath("$.skillsToStrengthen[1].preferredByCount").value(1))
                .andExpect(jsonPath("$.existingStrengths[0].skill").value("Java"))
                .andExpect(jsonPath("$.existingStrengths[0].exactMatchCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("percentage", "score", "probability", "hiring", "%");
    }

    @Test void studentSkillGapsKeepRelatedEvidenceSeparateAndExcludeUnavailableOpportunityStates() throws Exception {
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"MySQL\"}")).andExpect(status().isOk());
        createOpportunity(OpportunityStatus.DRAFT, LocalDate.now().plusDays(3), List.of(), List.of(), null);
        createOpportunity(OpportunityStatus.CLOSED, LocalDate.now().plusDays(3), List.of(), List.of(), null);
        createOpportunity(OpportunityStatus.PUBLISHED, LocalDate.now().minusDays(1), List.of(), List.of(), null);
        doAnswer(invocation -> ((List<OpportunityMatchRequest>) invocation.getArgument(0)).stream().map(request ->
                new OpportunityMatchResult("model", "1", new BigDecimal("0.72"), List.of(
                        new OpportunityMatchResult.SkillMatch("Java", "EXACT", "Java", BigDecimal.ONE),
                        new OpportunityMatchResult.SkillMatch("PostgreSQL", "RELATED", "MySQL", new BigDecimal("0.80"))),
                        List.of(new OpportunityMatchResult.SkillMatch("Docker", "MISSING", null, null)), List.of(), List.of())).toList())
                .when(matchClient).matchAll(anyList());
        mvc.perform(get("/api/v1/student/skill-gaps").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.opportunitiesAnalyzed").value(1))
                .andExpect(jsonPath("$.relatedSkills[0].studentSkill").value("MySQL"))
                .andExpect(jsonPath("$.relatedSkills[0].opportunitySkill").value("PostgreSQL"))
                .andExpect(jsonPath("$.skillsToStrengthen[0].skill").value("Docker"))
                .andExpect(jsonPath("$.existingStrengths[0].skill").value("Java"));
    }

    @Test void studentSkillGapsHandleIncompleteProfilesAndAiFailureSafely() throws Exception {
        StudentProfessionalProfile profile = professionalProfiles.findByStudentId(student.getId()).orElseThrow();
        studentSkills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(profile.getId())
                .forEach(skill -> { skill.deactivate(); studentSkills.save(skill); });
        mvc.perform(get("/api/v1/student/skill-gaps").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETE_PROFILE"))
                .andExpect(jsonPath("$.skillsToStrengthen").isEmpty());
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Java\"}")).andExpect(status().isOk());
        doThrow(new OpportunityMatchClientException("offline", null)).when(matchClient).matchAll(anyList());
        mvc.perform(get("/api/v1/student/skill-gaps").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.skillsToStrengthen").isEmpty())
                .andExpect(jsonPath("$.message").value("Skill-gap analysis is temporarily unavailable."));
    }

    @Test void adminSkillGapOverviewUsesEligibleUnknownPopulationAndSupportsExplicitIneligibleFilter() throws Exception {
        Cookie otherSession = login(otherStudent.getEmail(), "OtherPassword!23");
        createInstitutionalProfile(otherStudent, otherSession, Branch.CIVIL, 8, "Civil Student");
        Cookie adminSession = login(admin.getEmail(), "AdminPassword!23");
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery/skill-gaps", opportunity.getId()).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.candidatesAnalyzed").value(1))
                .andExpect(jsonPath("$.requiredGaps[0].skill").value("PostgreSQL"))
                .andExpect(jsonPath("$.requiredGaps[0].studentsMissing").value(1))
                .andExpect(jsonPath("$.preferredGaps[0].skill").value("Docker"));
        mvc.perform(get("/api/v1/admin/opportunities/{id}/talent-discovery/skill-gaps", opportunity.getId())
                        .param("eligibility", "INELIGIBLE").cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidatesAnalyzed").value(1));
    }

    @Test void skillGapEndpointsEnforceRolesAndDoNotExposeAnotherStudentOrFabricateOnFailure() throws Exception {
        String studentEndpoint = "/api/v1/student/skill-gaps";
        String adminEndpoint = "/api/v1/admin/opportunities/" + opportunity.getId() + "/talent-discovery/skill-gaps";
        mvc.perform(get(studentEndpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(studentEndpoint).cookie(login(admin.getEmail(), "AdminPassword!23"))).andExpect(status().isForbidden());
        mvc.perform(get(studentEndpoint).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
        mvc.perform(get(adminEndpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(adminEndpoint).cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(get(adminEndpoint).cookie(login(mentor.getEmail(), "MentorPassword!23"))).andExpect(status().isForbidden());
        doThrow(new OpportunityMatchClientException("offline", null)).when(matchClient).matchAll(anyList());
        mvc.perform(get(adminEndpoint).cookie(login(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.requiredGaps").isEmpty()).andExpect(jsonPath("$.preferredGaps").isEmpty());
    }

    private OpportunityMatchResult result(OpportunityMatchRequest request) {
        return new OpportunityMatchResult("onnx-community/all-MiniLM-L6-v2-ONNX", "test", new BigDecimal("0.72"),
                request.opportunity().requiredSkills().stream().map(skill -> request.student().skills().stream().filter(studentSkill -> studentSkill.normalizedName().equals(skill.normalizedName())).findFirst()
                        .map(studentSkill -> new OpportunityMatchResult.SkillMatch(skill.name(), "EXACT", studentSkill.name(), BigDecimal.ONE))
                        .orElseGet(() -> new OpportunityMatchResult.SkillMatch(skill.name(), "MISSING", null, null))).toList(),
                request.opportunity().preferredSkills().stream().map(skill -> new OpportunityMatchResult.SkillMatch(skill.name(), "MISSING", null, null)).toList(), List.of(), List.of());
    }
    private OpportunityAiAnalysis analysis(Opportunity value, String role) {
        OpportunityAiAnalysis analysis = new OpportunityAiAnalysis(value, OpportunityAiFingerprint.of(value));
        AiOpportunityResult result = new AiOpportunityResult("model", "1", List.of(), List.of(), List.of(), List.of(role), List.of("Build services"));
        analysis.complete(result, List.of(), List.of(new OpportunityAiSignal(analysis, OpportunityAiSignalType.ROLE, role, 0), new OpportunityAiSignal(analysis, OpportunityAiSignalType.RESPONSIBILITY, "Build services", 0)));
        return analysis;
    }
    private OpportunitySkill skill(String name, OpportunitySkillType type) { return new OpportunitySkill(opportunity, new NormalizedSkill(name, name.toLowerCase(Locale.ROOT)), type); }
    private Opportunity createOpportunity(OpportunityStatus status, LocalDate deadline, List<Branch> branches, List<Integer> semesters, BigDecimal cgpa) {
        Opportunity value = new Opportunity(admin, request(deadline, branches, semesters, cgpa));
        if (status != OpportunityStatus.DRAFT) value.publish(); if (status == OpportunityStatus.CLOSED) value.close();
        return opportunities.saveAndFlush(value);
    }
    private OpportunityRequest request(LocalDate deadline, List<Branch> branches, List<Integer> semesters, BigDecimal cgpa) {
        return new OpportunityRequest("Acme", "Backend Intern", "Build backend services", null, "Pune", OpportunityMode.HYBRID, 3,
                BigDecimal.ZERO, "INR", deadline, cgpa, branches, semesters, List.of(), List.of());
    }
    private void createInstitutionalProfile(User value, Cookie cookie, Branch branch, int semester) throws Exception {
        createInstitutionalProfile(value, cookie, branch, semester, "Match Student");
    }
    private void createInstitutionalProfile(User value, Cookie cookie, Branch branch, int semester, String name) throws Exception {
        String id = value.getId().toString(); Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentName", name); body.put("branch", branch.name()); body.put("uid", "UID-" + id); body.put("rollNumber", "ROLL-" + id);
        body.put("permanentAddress", "Address"); body.put("personalEmail", "personal-" + id + "@example.com"); body.put("instituteEmail", value.getEmail()); body.put("phoneNumber", "9876543210");
        body.put("guardianName", "Guardian"); body.put("guardianContact", "9876543211"); body.put("emergencyContact", "9876543212"); body.put("bloodGroup", "O+"); body.put("aadhaarNumber", "123456789012");
        body.put("facultyMentorName", "Faculty"); body.put("facultyMentorContact", "9876543213"); body.put("internshipCoordinatorName", "Coordinator"); body.put("internshipCoordinatorContact", "9876543214");
        body.put("cdcCoordinatorName", "CDC"); body.put("cdcCoordinatorContact", "9876543215"); body.put("academicYear", "2025-2026"); body.put("semester", semester);
        mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated());
    }
    private Cookie login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth"); }
}
