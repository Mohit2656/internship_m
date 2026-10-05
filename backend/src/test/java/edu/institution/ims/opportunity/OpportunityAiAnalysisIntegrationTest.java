package edu.institution.ims.opportunity;

import com.fasterxml.jackson.databind.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.studentprofile.Branch;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
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
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OpportunityAiAnalysisIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
    @Autowired OpportunityRepository opportunities; @Autowired OpportunitySkillRepository skills;
    @Autowired OpportunityAiAnalysisRepository analyses;
    @MockitoBean OpportunityAiClient aiClient;
    User admin; User student; User mentor; Cookie adminSession;

    @BeforeEach void setup() throws Exception {
        analyses.deleteAll(); skills.deleteAll(); opportunities.deleteAll(); users.deleteAll();
        admin = users.save(new User("ai-admin@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("ai-student@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        mentor = users.save(new User("ai-mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        adminSession = login(admin.getEmail(), "AdminPassword!23");
    }
    @AfterEach void cleanup() { analyses.deleteAll(); skills.deleteAll(); opportunities.deleteAll(); users.deleteAll(); }

    @Test void analysisPersistsSuggestionsWithoutAutomaticAdoptionAndSupportsReviewAndStaleness() throws Exception {
        doReturn(result("Spring Boot", "Java", "Docker", "AWS")).when(aiClient).analyze(anyString(), anyString());
        long id = create(request(description(), List.of("Java"), List.of()));
        MvcResult analyzed = mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANALYZED"))
                .andExpect(jsonPath("$.requiredSuggestions.length()").value(1))
                .andExpect(jsonPath("$.requiredSuggestions[0].displayName").value("Spring Boot"))
                .andExpect(jsonPath("$.preferredSuggestions.length()").value(2))
                .andExpect(jsonPath("$.roleSignals[0]").value("Backend Development"))
                .andReturn();
        JsonNode body = json.readTree(analyzed.getResponse().getContentAsString());
        long requiredId = body.at("/requiredSuggestions/0/id").asLong();
        long rejectedId = body.at("/preferredSuggestions/0/id").asLong();
        assertThat(skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(id)).extracting(OpportunitySkill::getDisplayName)
                .containsExactly("Java");

        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis/suggestions/{suggestionId}/confirm", id, requiredId).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiredSuggestions[0].reviewStatus").value("CONFIRMED"));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis/suggestions/{suggestionId}/reject", id, rejectedId).cookie(adminSession))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis/suggestions/confirm-all", id).cookie(adminSession))
                .andExpect(status().isOk());
        assertThat(skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(id)).extracting(OpportunitySkill::getDisplayName)
                .containsExactlyInAnyOrder("Java", "Spring Boot", "Docker");
        assertThat(analyses.findFirstByOpportunityIdOrderByCreatedAtDesc(id)).isPresent();

        OpportunityRequest changed = request(description() + " Knowledge of Kubernetes is required.",
                List.of("Java", "Spring Boot"), List.of("Docker", "AWS"));
        mvc.perform(put("/api/v1/admin/opportunities/{id}", id).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(changed))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STALE"))
                .andExpect(jsonPath("$.stale").value(true)).andExpect(jsonPath("$.requiredSuggestions").isEmpty());

        when(aiClient.analyze(anyString(), contains("Kubernetes"))).thenReturn(result("Kubernetes", "Spring Boot", "Docker", "AWS"));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANALYZED"))
                .andExpect(jsonPath("$.requiredSuggestions[0].displayName").value("Kubernetes"));
        assertThat(analyses.count()).isEqualTo(2);
    }

    @Test void studentMentorAndUnauthenticatedUsersCannotControlAnalysis() throws Exception {
        long id = create(request(description(), List.of(), List.of()));
        for (Cookie cookie : List.of(login(student.getEmail(), "StudentPassword!23"), login(mentor.getEmail(), "MentorPassword!23"))) {
            mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(cookie)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(aiClient);
    }

    @Test void failureIsRetryableAndDoesNotAlterAuthoritativeSkills() throws Exception {
        long id = create(request(description(), List.of("Java"), List.of("Docker")));
        when(aiClient.analyze(anyString(), anyString())).thenThrow(new OpportunityAiClientException("offline", null));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        mvc.perform(get("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(id)).extracting(OpportunitySkill::getDisplayName)
                .containsExactlyInAnyOrder("Java", "Docker");
        doReturn(result("Spring Boot", "Java", "Docker", "AWS")).when(aiClient).analyze(anyString(), anyString());
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANALYZED"));
    }

    @Test void duplicatePendingAndClosedOpportunityAreRejected() throws Exception {
        long pendingId = create(request(description(), List.of(), List.of()));
        Opportunity pendingOpportunity = opportunities.findById(pendingId).orElseThrow();
        analyses.save(new OpportunityAiAnalysis(pendingOpportunity, OpportunityAiFingerprint.of(pendingOpportunity)));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", pendingId).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_IN_PROGRESS"));

        long closedId = create(request(description(), List.of(), List.of()));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", closedId).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/opportunities/{id}/close", closedId).cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/opportunities/{id}/ai-analysis", closedId).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPPORTUNITY_CLOSED"));
    }

    private AiOpportunityResult result(String required, String duplicateManual, String preferredOne, String preferredTwo) {
        List<AiOpportunityResult.AiSkill> requiredSkills = List.of(skill(required), skill(duplicateManual));
        return new AiOpportunityResult("all-MiniLM-L6-v2", "test-version",
                List.of(skill(required), skill(duplicateManual), skill(preferredOne), skill(preferredTwo)), requiredSkills,
                List.of(skill(preferredOne), skill(preferredTwo)), List.of("Backend Development"), List.of("Build scalable backend services"));
    }
    private AiOpportunityResult.AiSkill skill(String name) { return new AiOpportunityResult.AiSkill(name, name.toLowerCase()); }
    private String description() { return "Java and Spring Boot are required. Docker and AWS are preferred. Build scalable backend services."; }
    private OpportunityRequest request(String description, List<String> required, List<String> preferred) {
        return new OpportunityRequest("Microsoft", "Software Engineering Intern", description, "https://www.microsoft.com", "Hyderabad",
                OpportunityMode.HYBRID, 6, new BigDecimal("30000.00"), "INR", LocalDate.now().plusDays(30), new BigDecimal("7.50"),
                List.of(Branch.COMPUTER_ENGINEERING), List.of(7, 8), required, preferred);
    }
    private long create(OpportunityRequest request) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/opportunities").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
}
