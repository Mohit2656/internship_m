package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.storage.FileStorageService;
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
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeAnalysisIntegrationTest {
    private static final byte[] PDF = "%PDF-1.4\nresume text\n%%EOF".getBytes();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentProfessionalProfileRepository profiles;
    @Autowired StudentResumeRepository resumes;
    @Autowired ResumeAnalysisRepository analyses;
    @Autowired FileStorageService storage;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean ResumeAiClient aiClient;

    User admin;
    User studentA;
    User studentB;
    Cookie adminCookie;
    Cookie studentACookie;
    Cookie studentBCookie;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        admin = users.save(new User("analysis-admin-" + suffix + "@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        studentA = users.save(new User("analysis-a-" + suffix + "@example.edu", encoder.encode("StudentAPassword!23"), Role.STUDENT, true));
        studentB = users.save(new User("analysis-b-" + suffix + "@example.edu", encoder.encode("StudentBPassword!23"), Role.STUDENT, true));
        adminCookie = login(admin.getEmail(), "AdminPassword!23");
        studentACookie = login(studentA.getEmail(), "StudentAPassword!23");
        studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
    }

    @AfterEach
    void cleanUp() {
        for (User student : List.of(studentA, studentB)) {
            profiles.findByStudentId(student.getId()).ifPresent(profile -> {
                resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId())
                        .forEach(resume -> storage.delete(resume.getStorageKey()));
                profiles.delete(profile);
            });
        }
        profiles.flush();
        users.deleteAllById(List.of(admin.getId(), studentA.getId(), studentB.getId()));
        users.flush();
    }

    @Test
    void studentAnalyzesOwnActiveResumeThroughPendingToParsedAndProfileIsPreserved() throws Exception {
        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Platform Engineer\",\"careerInterests\":\"Cloud systems\"}"))
                .andExpect(status().isOk());
        Long resumeId = upload(studentACookie, "current.pdf");
        mvc.perform(get("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumeId").value(resumeId))
                .andExpect(jsonPath("$.status").value("NOT_PARSED"));

        when(aiClient.analyze(any(), eq("current.pdf"), eq("application/pdf"))).thenAnswer(invocation -> {
            assertThat(resumes.findById(resumeId).orElseThrow().getParsingStatus()).isEqualTo(ResumeParsingStatus.PENDING);
            assertThat(analyses.findByResumeId(resumeId).orElseThrow().getStatus()).isEqualTo(ResumeParsingStatus.PENDING);
            return result();
        });

        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.modelName").value("oksomu/resume-ner"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.experience[0].company").value("Acme Technologies"))
                .andExpect(jsonPath("$.education[0].institution").value("SVPCET"))
                .andExpect(jsonPath("$.certifications[0]").value("AWS Certified Cloud Practitioner"));

        assertThat(resumes.findById(resumeId).orElseThrow().getParsingStatus()).isEqualTo(ResumeParsingStatus.PARSED);
        StudentProfessionalProfile profile = profiles.findByStudentId(studentA.getId()).orElseThrow();
        assertThat(profile.getHeadline()).isEqualTo("Platform Engineer");
        assertThat(profile.getCareerInterests()).isEqualTo("Cloud systems");
    }

    @Test
    void failedAnalysisIsPersistedAndCanBeRetried() throws Exception {
        Long resumeId = upload(studentACookie, "retry.pdf");
        when(aiClient.analyze(any(), anyString(), anyString()))
                .thenThrow(new ResumeAiClientException("offline", null)).thenReturn(result());

        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        assertThat(resumes.findById(resumeId).orElseThrow().getParsingStatus()).isEqualTo(ResumeParsingStatus.FAILED);
        assertThat(analyses.findByResumeId(resumeId).orElseThrow().getFailureReasonSafe()).isEqualTo("AI_SERVICE_UNAVAILABLE");

        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARSED"));
        verify(aiClient, times(2)).analyze(any(), anyString(), anyString());
    }

    @Test
    void malformedAiResponseFailsSafelyWithoutTouchingResume() throws Exception {
        Long resumeId = upload(studentACookie, "malformed.pdf");
        when(aiClient.analyze(any(), anyString(), anyString()))
                .thenReturn(new AiResumeResult("model", "1", null, List.of(), List.of(), List.of()));
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"));
        assertThat(resumes.findById(resumeId).orElseThrow().getParsingStatus()).isEqualTo(ResumeParsingStatus.FAILED);
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
    }

    @Test
    void reuploadStartsNotParsedAndNeverReturnsOldAnalysis() throws Exception {
        Long firstId = upload(studentACookie, "v1.pdf");
        when(aiClient.analyze(any(), anyString(), anyString())).thenReturn(result());
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie)).andExpect(status().isOk());

        Long secondId = upload(studentACookie, "v2.pdf");
        assertThat(secondId).isNotEqualTo(firstId);
        mvc.perform(get("/api/v1/student/resume/analysis").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumeId").value(secondId))
                .andExpect(jsonPath("$.status").value("NOT_PARSED"))
                .andExpect(jsonPath("$.skills").isEmpty());
        assertThat(analyses.findByResumeId(firstId).orElseThrow().getStatus()).isEqualTo(ResumeParsingStatus.PARSED);
    }

    @Test
    void selfServiceEndpointsPreventCrossStudentAndAdminAccess() throws Exception {
        Long resumeId = upload(studentACookie, "private.pdf");
        when(aiClient.analyze(any(), anyString(), anyString())).thenReturn(result());
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentBCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESUME_NOT_FOUND"));
        mvc.perform(get("/api/v1/student/resume/analysis").cookie(studentBCookie)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(adminCookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/student/resume/{id}/analysis", resumeId).cookie(studentBCookie)).andExpect(status().isNotFound());
        assertThat(analyses.findByResumeId(resumeId)).isEmpty();
        verifyNoInteractions(aiClient);
    }

    @Test
    void unauthenticatedAnalysisIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/resume/analysis")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/student/resume/analysis")).andExpect(status().isUnauthorized());
    }

    @Test
    void correctionsPreserveOriginalFileAndExtractionTrackReviewerAndRejectStaleWrites() throws Exception {
        Long resumeId=upload(studentACookie,"correctable.pdf");
        when(aiClient.analyze(any(),anyString(),anyString())).thenReturn(result());
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie)).andExpect(status().isOk());
        String endpoint="/api/v1/student/resume/analysis/corrections";
        String values="{\"skills\":[\"Spring Boot\"],\"experience\":[],\"education\":[{\"degree\":\"B.Tech\",\"institution\":\"Correct College\"}],\"certifications\":[]}";
        String body="{\"resumeId\":"+resumeId+",\"revision\":0,\"values\":"+values+"}";
        mvc.perform(put(endpoint).cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.source").value("STUDENT_CORRECTED"))
                .andExpect(jsonPath("$.editorId").value(studentA.getId()))
                .andExpect(jsonPath("$.editedAt").isNotEmpty())
                .andExpect(jsonPath("$.original.skills[0]").value("Java"))
                .andExpect(jsonPath("$.values.skills[0]").value("Spring Boot"));
        mvc.perform(put(endpoint).cookie(studentACookie).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        String adminEndpoint="/api/v1/admin/students/"+studentA.getId()+"/resume/analysis/corrections";
        mvc.perform(put(adminEndpoint).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"revision\":0","\"revision\":1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.source").value("IIIC_REVIEWED"))
                .andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(get(adminEndpoint).cookie(studentBCookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie)).andExpect(status().isConflict());
        mvc.perform(get(endpoint).cookie(studentACookie)).andExpect(jsonPath("$.values.skills[0]").value("Spring Boot"));
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie)).andExpect(content().bytes(PDF));
        mvc.perform(put(endpoint).cookie(studentACookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":"+resumeId+",\"revision\":2,\"values\":{\"skills\":[\"\"],\"experience\":[],\"education\":[],\"certifications\":[]}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void successfulResumeEventsRouteToIiicOnceAndUseDatabaseTimestamps() throws Exception {
        Long first=upload(studentACookie,"first.pdf");
        mvc.perform(get("/api/v1/admin/notifications").cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1)).andExpect(jsonPath("$.content[0].type").value("RESUME_UPLOADED"));
        var stored=resumes.findById(first).orElseThrow();
        var at=jdbc.queryForObject("select created_at from student_notifications where student_user_id=? and event_key=?",java.sql.Timestamp.class,admin.getId(),"resume-upload:"+first).toInstant();
        assertThat(at).isEqualTo(stored.getUploadedAt());
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,studentB.getId())).isZero();
        mvc.perform(multipart("/api/v1/student/resume").file(new MockMultipartFile("file","bad.txt","text/plain","bad".getBytes())).cookie(studentACookie))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,admin.getId())).isEqualTo(1);
        upload(studentACookie,"second.pdf");
        mvc.perform(get("/api/v1/admin/notifications").cookie(adminCookie)).andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.content[0].type").value("RESUME_UPDATED"));
        mvc.perform(patch("/api/v1/admin/notifications/read-all").cookie(adminCookie)).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/v1/admin/notifications").cookie(studentACookie)).andExpect(status().isForbidden());
    }

    private Long upload(Cookie cookie, String filename) throws Exception {
        String body = mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", filename, "application/pdf", PDF)).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resume.parsingStatus").value("NOT_PARSED"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("resume").path("id").asLong();
    }

    private AiResumeResult result() {
        return new AiResumeResult("oksomu/resume-ner", "test-sha", List.of("Java", "PostgreSQL"),
                List.of(new AiExperience("Backend Intern", "Acme Technologies", "2025", "2026")),
                List.of(new AiEducation("B.Tech", "Computer Engineering", "SVPCET")),
                List.of("AWS Certified Cloud Practitioner"));
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andReturn().getResponse().getCookie("ims_auth");
    }
}
