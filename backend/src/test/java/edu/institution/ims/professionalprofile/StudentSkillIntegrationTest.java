package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.databind.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.storage.FileStorageService;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentSkillIntegrationTest {
    private static final byte[] PDF = "%PDF-1.4\nskill resume\n%%EOF".getBytes();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentProfileRepository institutionalProfiles;
    @Autowired StudentProfessionalProfileRepository profiles;
    @Autowired StudentResumeRepository resumes;
    @Autowired ResumeAnalysisRepository analyses;
    @Autowired StudentSkillRepository skills;
    @Autowired StudentSkillSuggestionRepository suggestions;
    @Autowired FileStorageService storage;
    @MockitoBean ResumeAiClient aiClient;

    User studentA;
    User studentB;
    Cookie studentACookie;
    Cookie studentBCookie;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        studentA = users.save(new User("skills-a-" + suffix + "@example.edu", encoder.encode("StudentAPassword!23"), Role.STUDENT, true));
        studentB = users.save(new User("skills-b-" + suffix + "@example.edu", encoder.encode("StudentBPassword!23"), Role.STUDENT, true));
        studentACookie = login(studentA.getEmail(), "StudentAPassword!23");
        studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
        when(aiClient.analyze(any(), anyString(), anyString())).thenReturn(result("Java", "Spring Boot", "PostgreSQL"));
    }

    @AfterEach
    void cleanUp() {
        for (User student : List.of(studentA, studentB)) {
            profiles.findByStudentId(student.getId()).ifPresent(profile -> {
                resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId())
                        .forEach(resume -> storage.delete(resume.getStorageKey()));
                profiles.delete(profile);
            });
            institutionalProfiles.findByUserId(student.getId()).ifPresent(institutionalProfiles::delete);
        }
        profiles.flush(); institutionalProfiles.flush();
        users.deleteAllById(List.of(studentA.getId(), studentB.getId())); users.flush();
    }

    @Test
    void aiSkillsBecomePendingAndAreNotAutomaticallyConfirmed() throws Exception {
        analyze(studentACookie, "skills.pdf");
        mvc.perform(get("/api/v1/student/professional-profile/skills").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills").isEmpty())
                .andExpect(jsonPath("$.suggestions.length()").value(3))
                .andExpect(jsonPath("$.suggestions[*].displayName").value(org.hamcrest.Matchers.containsInAnyOrder("Java", "Spring Boot", "PostgreSQL")));
    }

    @Test
    void studentConfirmsAndRejectsSuggestionsWithoutMutatingRawAnalysis() throws Exception {
        analyze(studentACookie, "review.pdf");
        JsonNode state = getSkills(studentACookie);
        long javaId = idByName(state.path("suggestions"), "Java");
        long springId = idByName(state.path("suggestions"), "Spring Boot");

        mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/{id}/confirm", javaId).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills[0].displayName").value("Java"))
                .andExpect(jsonPath("$.skills[0].source").value("AI_EXTRACTED"));
        mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/{id}/reject", springId).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suggestions.length()").value(1));

        mvc.perform(get("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3))
                .andExpect(jsonPath("$.skills[*]").value(org.hamcrest.Matchers.hasItems("Java", "Spring Boot", "PostgreSQL")));
    }

    @Test
    void confirmAllManualAddDuplicateRenameAndRemovalWork() throws Exception {
        analyze(studentACookie, "bulk.pdf");
        MvcResult confirmed = mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/confirm-all").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3))
                .andExpect(jsonPath("$.suggestions").isEmpty()).andReturn();
        long javaId = idByName(json.readTree(confirmed.getResponse().getContentAsString()).path("skills"), "Java");

        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docker\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(4));
        for (String duplicate : List.of(" java ", "JAVA")) {
            mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentACookie)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", duplicate))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SKILL_ALREADY_EXISTS"));
        }
        mvc.perform(patch("/api/v1/student/professional-profile/skills/{id}", javaId).cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Core Java\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills[*].displayName").value(org.hamcrest.Matchers.hasItem("Core Java")));
        mvc.perform(delete("/api/v1/student/professional-profile/skills/{id}", javaId).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3));
        assertThat(analyses.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3));
    }

    @Test
    void studentCannotModifyAnotherStudentsSkillOrSuggestion() throws Exception {
        analyze(studentACookie, "private.pdf");
        JsonNode state = getSkills(studentACookie);
        long suggestionId = state.path("suggestions").get(0).path("id").asLong();
        mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/{id}/confirm", suggestionId).cookie(studentBCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SKILL_SUGGESTION_NOT_FOUND"));
        MvcResult confirmed = mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/{id}/confirm", suggestionId).cookie(studentACookie))
                .andExpect(status().isOk()).andReturn();
        long skillId = json.readTree(confirmed.getResponse().getContentAsString()).path("skills").get(0).path("id").asLong();
        mvc.perform(delete("/api/v1/student/professional-profile/skills/{id}", skillId).cookie(studentBCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));
        mvc.perform(patch("/api/v1/student/professional-profile/skills/{id}", skillId).cookie(studentBCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmedSkillsSurviveReuploadAndNewAnalysisOnlySuggestsNewSkills() throws Exception {
        analyze(studentACookie, "v1.pdf");
        mvc.perform(post("/api/v1/student/professional-profile/skill-suggestions/confirm-all").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3));

        upload(studentACookie, "v2.pdf");
        mvc.perform(get("/api/v1/student/professional-profile/skills").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3))
                .andExpect(jsonPath("$.suggestions").isEmpty());
        when(aiClient.analyze(any(), anyString(), anyString())).thenReturn(result("JAVA", "Spring Boot", "AWS", "Kubernetes"));
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/professional-profile/skills").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(3))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[*].displayName").value(org.hamcrest.Matchers.containsInAnyOrder("AWS", "Kubernetes")));
    }

    @Test
    void skillWorkflowDoesNotOverwriteInstitutionalStudentProfile() throws Exception {
        String suffix = studentA.getId().toString();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("studentName", "Institutional Name"); request.put("branch", "COMPUTER_ENGINEERING");
        request.put("uid", "UID-" + suffix); request.put("rollNumber", "ROLL-" + suffix);
        request.put("permanentAddress", "Permanent Address"); request.put("currentAddress", "Current Address");
        request.put("personalEmail", "personal" + suffix + "@example.com"); request.put("instituteEmail", studentA.getEmail());
        request.put("phoneNumber", "9876543210"); request.put("guardianName", "Guardian"); request.put("guardianContact", "9876543211");
        request.put("emergencyContact", "9876543212"); request.put("bloodGroup", "O+"); request.put("aadhaarNumber", "123456789012");
        request.put("facultyMentorName", "Faculty"); request.put("facultyMentorContact", "9876543213");
        request.put("internshipCoordinatorName", "Internship Coordinator"); request.put("internshipCoordinatorContact", "9876543214");
        request.put("cdcCoordinatorName", "CDC Coordinator"); request.put("cdcCoordinatorContact", "9876543215");
        request.put("academicYear", "2025-2026"); request.put("semester", 7);
        mvc.perform(post("/api/v1/student/profile").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request))).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Docker\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/profile").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Institutional Name"))
                .andExpect(jsonPath("$.uid").value("UID-" + suffix))
                .andExpect(jsonPath("$.instituteEmail").value(studentA.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value("9876543210"));
    }

    private void analyze(Cookie cookie, String filename) throws Exception {
        upload(cookie, filename);
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARSED"));
    }

    private long upload(Cookie cookie, String filename) throws Exception {
        String body = mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", filename, "application/pdf", PDF)).cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("resume").path("id").asLong();
    }

    private JsonNode getSkills(Cookie cookie) throws Exception {
        String body = mvc.perform(get("/api/v1/student/professional-profile/skills").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private long idByName(JsonNode values, String name) {
        for (JsonNode value : values) if (name.equals(value.path("displayName").asText())) return value.path("id").asLong();
        throw new AssertionError("Missing skill " + name);
    }

    private AiResumeResult result(String... values) {
        return new AiResumeResult("oksomu/resume-ner", "test-sha", List.of(values), List.of(), List.of(), List.of());
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andReturn().getResponse().getCookie("ims_auth");
    }
}
