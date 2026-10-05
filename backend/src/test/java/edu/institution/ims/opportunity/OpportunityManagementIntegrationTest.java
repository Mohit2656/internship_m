package edu.institution.ims.opportunity;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OpportunityManagementIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired OpportunityRepository opportunities; @Autowired OpportunitySkillRepository skills;
    @Autowired PasswordEncoder encoder;
    User admin; User student; User mentor; Cookie adminSession;

    @BeforeEach void setup() throws Exception {
        skills.deleteAll(); opportunities.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("student@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
    }

    @AfterEach void cleanup() { skills.deleteAll(); opportunities.deleteAll(); users.deleteAll(); }

    @Test void adminCreatesDraftAndCanListRetrieveAndEditStructuredData() throws Exception {
        ObjectNode payload = json.valueToTree(validRequest());
        payload.put("status", "PUBLISHED"); payload.put("publishedAt", "2000-01-01T00:00:00Z");
        MvcResult created = mvc.perform(post("/api/v1/admin/opportunities").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").isEmpty()).andExpect(jsonPath("$.eligibleBranches.length()").value(2))
                .andExpect(jsonPath("$.eligibleSemesters[0]").value(7)).andExpect(jsonPath("$.eligibleSemesters[1]").value(8))
                .andExpect(jsonPath("$.requiredSkills[0].displayName").value("Java"))
                .andExpect(jsonPath("$.preferredSkills[0].displayName").value("AWS"))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/admin/opportunities").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/admin/opportunities/{id}", id).cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Microsoft"));

        OpportunityRequest edited = request("Platform Engineering Intern", "Backend and cloud engineering internship.", OpportunityMode.HYBRID,
                List.of("Java", "PostgreSQL"), List.of("Docker"), List.of(Branch.COMPUTER_ENGINEERING), List.of(8));
        mvc.perform(put("/api/v1/admin/opportunities/{id}", id).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(edited))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Platform Engineering Intern"))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.eligibleBranches.length()").value(1))
                .andExpect(jsonPath("$.requiredSkills.length()").value(2));
    }

    @Test void backendControlsPublishAndCloseLifecycleAndTimestamps() throws Exception {
        long invalidId = create(request("Incomplete", null, null, List.of(), List.of(), List.of(), List.of()));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", invalidId).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPPORTUNITY_NOT_READY"));
        mvc.perform(post("/api/v1/admin/opportunities/{id}/close", invalidId).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_OPPORTUNITY_TRANSITION"));

        long id = create(validRequest()); Instant beforePublish = Instant.now().minusSeconds(1);
        MvcResult published = mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andReturn();
        Instant publishedAt = Instant.parse(json.readTree(published.getResponse().getContentAsString()).get("publishedAt").asText());
        assertThat(publishedAt).isAfter(beforePublish);
        mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").value(publishedAt.toString()));

        MvcResult closed = mvc.perform(post("/api/v1/admin/opportunities/{id}/close", id).cookie(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty()).andReturn();
        Instant closedAt = Instant.parse(json.readTree(closed.getResponse().getContentAsString()).get("closedAt").asText());
        assertThat(closedAt).isAfterOrEqualTo(publishedAt);
        mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", id).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_OPPORTUNITY_TRANSITION"));
        mvc.perform(put("/api/v1/admin/opportunities/{id}", id).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest()))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPPORTUNITY_CLOSED"));
        mvc.perform(get("/api/v1/admin/opportunities/{id}", id).cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test void rejectsInvalidMoneyCgpaWebsiteAndDuplicateStructuredValues() throws Exception {
        OpportunityRequest negative = new OpportunityRequest("Microsoft", "Intern", "Description", null, null, OpportunityMode.REMOTE,
                2, new BigDecimal("-1"), "INR", LocalDate.now().plusDays(5), null, List.of(), List.of(), List.of(), List.of());
        createExpectingBadRequest(negative, "VALIDATION_FAILED");

        OpportunityRequest cgpa = new OpportunityRequest("Microsoft", "Intern", "Description", null, null, OpportunityMode.REMOTE,
                2, BigDecimal.ZERO, "INR", LocalDate.now().plusDays(5), new BigDecimal("10.01"), List.of(), List.of(), List.of(), List.of());
        createExpectingBadRequest(cgpa, "VALIDATION_FAILED");

        OpportunityRequest duplicateSkills = request("Intern", "Description", OpportunityMode.REMOTE,
                List.of("Java", " java ", "JAVA"), List.of(), List.of(), List.of());
        createExpectingBadRequest(duplicateSkills, "DUPLICATE_OPPORTUNITY_SKILL");

        OpportunityRequest duplicateEligibility = request("Intern", "Description", OpportunityMode.REMOTE, List.of(), List.of(),
                List.of(Branch.COMPUTER_ENGINEERING, Branch.COMPUTER_ENGINEERING), List.of(7, 7));
        createExpectingBadRequest(duplicateEligibility, "DUPLICATE_ELIGIBLE_BRANCH");

        OpportunityRequest website = new OpportunityRequest("Microsoft", "Intern", "Description", "javascript:alert(1)", null,
                OpportunityMode.REMOTE, 2, BigDecimal.ZERO, "INR", LocalDate.now().plusDays(5), null,
                List.of(), List.of(), List.of(), List.of());
        createExpectingBadRequest(website, "INVALID_COMPANY_WEBSITE");
    }

    @Test void studentAndMentorCannotUseAdminManagementApis() throws Exception {
        Cookie studentSession = loginCookie(student.getEmail(), "StudentPassword!23");
        Cookie mentorSession = loginCookie(mentor.getEmail(), "MentorPassword!23");
        long id = create(validRequest());
        for (Cookie session : List.of(studentSession, mentorSession)) {
            mvc.perform(get("/api/v1/admin/opportunities").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/opportunities").cookie(session).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(validRequest()))).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/opportunities/{id}", id).cookie(session).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(validRequest()))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", id).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/opportunities/{id}/close", id).cookie(session)).andExpect(status().isForbidden());
        }
    }

    @Test void publicationRejectsPastDeadlineWithoutHardcodedCalendarDate() throws Exception {
        OpportunityRequest past = new OpportunityRequest("Microsoft", "Intern", "Description", null, null, OpportunityMode.ONSITE,
                2, BigDecimal.ZERO, "INR", LocalDate.now().minusDays(1), null, List.of(), List.of(), List.of(), List.of());
        long id = create(past);
        mvc.perform(post("/api/v1/admin/opportunities/{id}/publish", id).cookie(adminSession))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPPORTUNITY_NOT_READY"));
    }

    private long create(OpportunityRequest request) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/opportunities").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private void createExpectingBadRequest(OpportunityRequest request, String code) throws Exception {
        mvc.perform(post("/api/v1/admin/opportunities").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }
    private OpportunityRequest validRequest() {
        return request("Software Engineering Intern", "Backend and cloud engineering internship.", OpportunityMode.HYBRID,
                List.of("Java", "Spring Boot", "PostgreSQL"), List.of("Docker", "AWS"),
                List.of(Branch.COMPUTER_ENGINEERING, Branch.INFORMATION_TECHNOLOGY), List.of(7, 8));
    }
    private OpportunityRequest request(String title, String description, OpportunityMode mode, List<String> required,
            List<String> preferred, List<Branch> branches, List<Integer> semesters) {
        return new OpportunityRequest("Microsoft", title, description, "https://www.microsoft.com", "Hyderabad", mode,
                6, new BigDecimal("30000.00"), "INR", LocalDate.now().plusDays(30), new BigDecimal("7.50"),
                branches, semesters, required, preferred);
    }
    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
}
