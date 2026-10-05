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
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class StudentOpportunityFeedIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired OpportunityRepository opportunities; @Autowired OpportunitySkillRepository skills;
    @Autowired PasswordEncoder encoder;
    User admin; User student; User mentor; Cookie studentSession;

    @BeforeEach void setup() throws Exception {
        skills.deleteAll(); opportunities.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("student@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        studentSession = loginCookie(student.getEmail(), "StudentPassword!23");
    }

    @AfterEach void cleanup() { skills.deleteAll(); opportunities.deleteAll(); users.deleteAll(); }

    @Test void listContainsOnlyActivePublishedAndAllowsNoDeadline() throws Exception {
        Opportunity active = create("Microsoft", "Software Intern", OpportunityMode.HYBRID, LocalDate.now().plusDays(10), OpportunityStatus.PUBLISHED);
        Opportunity noDeadline = create("Google", "Cloud Intern", OpportunityMode.REMOTE, null, OpportunityStatus.PUBLISHED);
        create("Draft Co", "Draft Intern", OpportunityMode.ONSITE, LocalDate.now().plusDays(10), OpportunityStatus.DRAFT);
        create("Closed Co", "Closed Intern", OpportunityMode.ONSITE, LocalDate.now().plusDays(10), OpportunityStatus.CLOSED);
        create("Expired Co", "Expired Intern", OpportunityMode.REMOTE, LocalDate.now().minusDays(1), OpportunityStatus.PUBLISHED);

        mvc.perform(get("/api/v1/student/opportunities").cookie(studentSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(noDeadline.getId()))
                .andExpect(jsonPath("$.content[1].id").value(active.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void supportsCaseInsensitiveSearchModeFilterAndDeterministicSorting() throws Exception {
        Opportunity later = create("Microsoft", "Platform Engineering Intern", OpportunityMode.HYBRID, LocalDate.now().plusDays(20), OpportunityStatus.PUBLISHED);
        Opportunity soon = create("Google", "Cloud Engineering Intern", OpportunityMode.REMOTE, LocalDate.now().plusDays(5), OpportunityStatus.PUBLISHED);
        Opportunity none = create("Acme", "Operations Intern", OpportunityMode.ONSITE, null, OpportunityStatus.PUBLISHED);

        mvc.perform(get("/api/v1/student/opportunities").param("search", "MICRO").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(later.getId()));
        mvc.perform(get("/api/v1/student/opportunities").param("search", "cloud engineering").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(soon.getId()));
        mvc.perform(get("/api/v1/student/opportunities").param("mode", "ONSITE").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(none.getId()));
        mvc.perform(get("/api/v1/student/opportunities").param("sort", "DEADLINE_SOON").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(soon.getId()))
                .andExpect(jsonPath("$.content[1].id").value(later.getId()))
                .andExpect(jsonPath("$.content[2].id").value(none.getId()));
        mvc.perform(get("/api/v1/student/opportunities").param("sort", "NEWEST").cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(none.getId()));
    }

    @Test void detailContainsStructuredEligibilityAndSkillsWithoutAuditFields() throws Exception {
        Opportunity opportunity = create("Microsoft", "Software Intern", OpportunityMode.HYBRID, LocalDate.now().plusDays(10), OpportunityStatus.PUBLISHED);
        skills.saveAll(List.of(
                new OpportunitySkill(opportunity, new edu.institution.ims.professionalprofile.NormalizedSkill("Java", "java"), OpportunitySkillType.REQUIRED),
                new OpportunitySkill(opportunity, new edu.institution.ims.professionalprofile.NormalizedSkill("Spring Boot", "spring boot"), OpportunitySkillType.REQUIRED),
                new OpportunitySkill(opportunity, new edu.institution.ims.professionalprofile.NormalizedSkill("Docker", "docker"), OpportunitySkillType.PREFERRED)));

        mvc.perform(get("/api/v1/student/opportunities/{id}", opportunity.getId()).cookie(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Microsoft"))
                .andExpect(jsonPath("$.eligibleBranches[0]").value("COMPUTER_ENGINEERING"))
                .andExpect(jsonPath("$.eligibleSemesters[0]").value(7)).andExpect(jsonPath("$.eligibleSemesters[1]").value(8))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.requiredSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.preferredSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.createdBy").doesNotExist()).andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test void detailDoesNotRevealDraftClosedOrExpiredIds() throws Exception {
        Opportunity draft = create("Draft Co", "Draft", OpportunityMode.ONSITE, LocalDate.now().plusDays(10), OpportunityStatus.DRAFT);
        Opportunity closed = create("Closed Co", "Closed", OpportunityMode.ONSITE, LocalDate.now().plusDays(10), OpportunityStatus.CLOSED);
        Opportunity expired = create("Expired Co", "Expired", OpportunityMode.REMOTE, LocalDate.now().minusDays(1), OpportunityStatus.PUBLISHED);
        for (Opportunity opportunity : List.of(draft, closed, expired)) {
            mvc.perform(get("/api/v1/student/opportunities/{id}", opportunity.getId()).cookie(studentSession))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OPPORTUNITY_NOT_FOUND"));
        }
    }

    @Test void endpointIsStudentOnlyAndDoesNotGrantAdminManagementToStudent() throws Exception {
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        Cookie mentorSession = loginCookie(mentor.getEmail(), "MentorPassword!23");
        mvc.perform(get("/api/v1/student/opportunities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/opportunities").cookie(adminSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/opportunities").cookie(mentorSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/opportunities").cookie(studentSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("Blocked", LocalDate.now().plusDays(5))))).andExpect(status().isForbidden());
    }

    private Opportunity create(String company, String title, OpportunityMode mode, LocalDate deadline, OpportunityStatus status) {
        OpportunityRequest request = request(title, deadline);
        request = new OpportunityRequest(company, request.title(), request.description(), request.companyWebsite(), request.location(), mode,
                request.durationMonths(), request.stipendAmount(), request.currency(), deadline, request.minimumCgpa(),
                request.eligibleBranches(), request.eligibleSemesters(), request.requiredSkills(), request.preferredSkills());
        Opportunity opportunity = new Opportunity(admin, request);
        if (status == OpportunityStatus.PUBLISHED || status == OpportunityStatus.CLOSED) opportunity.publish();
        if (status == OpportunityStatus.CLOSED) opportunity.close();
        return opportunities.saveAndFlush(opportunity);
    }

    private OpportunityRequest request(String title, LocalDate deadline) {
        return new OpportunityRequest("Microsoft", title, "Backend and cloud engineering internship.", "https://www.microsoft.com",
                "Hyderabad", OpportunityMode.HYBRID, 6, new BigDecimal("30000.00"), "INR", deadline,
                new BigDecimal("7.50"), List.of(Branch.COMPUTER_ENGINEERING, Branch.INFORMATION_TECHNOLOGY),
                List.of(7, 8), List.of(), List.of());
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
}
