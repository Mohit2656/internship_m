package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.storage.FileStorageService;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfessionalProfileIntegrationTest {
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n%%EOF".getBytes();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StudentProfessionalProfileRepository profiles;
    @Autowired StudentResumeRepository resumes;
    @Autowired FileStorageService storage;

    User admin;
    User studentA;
    User studentB;
    Cookie adminCookie;
    Cookie studentACookie;
    Cookie studentBCookie;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        admin = users.save(new User("resume-admin-" + suffix + "@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        studentA = users.save(new User("resume-a-" + suffix + "@example.edu", encoder.encode("StudentAPassword!23"), Role.STUDENT, true));
        studentB = users.save(new User("resume-b-" + suffix + "@example.edu", encoder.encode("StudentBPassword!23"), Role.STUDENT, true));
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
    void studentGetsAndEditsOwnLazyProfessionalProfile() throws Exception {
        mvc.perform(get("/api/v1/student/professional-profile").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.resume").doesNotExist());

        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\" Backend Developer | Java \",\"careerInterests\":\" DevOps, Cloud Engineering \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headline").value("Backend Developer | Java"))
                .andExpect(jsonPath("$.careerInterests").value("DevOps, Cloud Engineering"));

        Long profileId = profiles.findByStudentId(studentA.getId()).orElseThrow().getId();
        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"headline\":\"Platform Engineer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(profileId));
        assertThat(profiles.findAll().stream().filter(p -> p.getStudent().getId().equals(studentA.getId()))).hasSize(1);
    }

    @Test
    void validPdfUploadIsPrivateAndStartsNotParsed() throws Exception {
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("resume.pdf")).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resume.originalFilename").value("resume.pdf"))
                .andExpect(jsonPath("$.resume.active").value(true))
                .andExpect(jsonPath("$.resume.parsingStatus").value("NOT_PARSED"));

        mvc.perform(get("/api/v1/student/resume").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentType").value("application/pdf"));
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(PDF));
        mvc.perform(get("/resume.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void validDocxUploadIsAccepted() throws Exception {
        byte[] bytes = docx();
        mvc.perform(multipart("/api/v1/student/resume").file(new MockMultipartFile("file", "career.docx",
                        ResumeFileValidator.DOCX, bytes)).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resume.contentType").value(ResumeFileValidator.DOCX))
                .andExpect(jsonPath("$.resume.parsingStatus").value("NOT_PARSED"));
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }

    @Test
    void invalidOversizedMismatchedAndTraversalUploadsAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_RESUME"));
        mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not-pdf".getBytes()))
                        .cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESUME_TYPE_MISMATCH"));
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("../escape.pdf")).cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESUME_FILENAME"));
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, oversized, 0, PDF.length);
        mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", "large.pdf", "application/pdf", oversized)).cookie(studentACookie))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("RESUME_TOO_LARGE"));
    }

    @Test
    void secondUploadCreatesNewActiveResumeAndPreservesInactiveHistory() throws Exception {
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("first.pdf")).cookie(studentACookie))
                .andExpect(status().isOk());
        StudentProfessionalProfile profile = profiles.findByStudentId(studentA.getId()).orElseThrow();
        StudentResume first = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()).orElseThrow();
        Long firstId = first.getId();
        String oldKey = first.getStorageKey();
        byte[] updatedPdf = "%PDF-1.7\nupdated resume\n%%EOF".getBytes();

        String body = mvc.perform(multipart("/api/v1/student/resume")
                        .file(pdf("updated.pdf", updatedPdf)).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resume.originalFilename").value("updated.pdf"))
                .andExpect(jsonPath("$.resume.active").value(true))
                .andExpect(jsonPath("$.resume.parsingStatus").value("NOT_PARSED"))
                .andReturn().getResponse().getContentAsString();
        Long activeId = json.readTree(body).path("resume").path("id").asLong();

        assertThat(activeId).isNotEqualTo(firstId);
        assertThat(resumes.countByProfessionalProfileIdAndActiveTrue(profile.getId())).isEqualTo(1);
        List<StudentResume> history = resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId());
        assertThat(history).hasSize(2);
        StudentResume previous = resumes.findById(firstId).orElseThrow();
        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getActiveProfileId()).isNull();
        assertThat(storage.load(oldKey).size()).isEqualTo(PDF.length);
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(content().bytes(updatedPdf));
    }

    @Test
    void failedReuploadLeavesPreviousResumeActiveAndDownloadable() throws Exception {
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("current.pdf")).cookie(studentACookie))
                .andExpect(status().isOk());
        StudentProfessionalProfile profile = profiles.findByStudentId(studentA.getId()).orElseThrow();
        Long activeId = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()).orElseThrow().getId();

        mvc.perform(multipart("/api/v1/student/resume")
                        .file(new MockMultipartFile("file", "broken.pdf", "application/pdf", "not-pdf".getBytes()))
                        .cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESUME_TYPE_MISMATCH"));

        assertThat(resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()).orElseThrow().getId()).isEqualTo(activeId);
        assertThat(resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId())).hasSize(1);
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
    }

    @Test
    void studentCannotAccessAnotherStudentsResumeOrSupplyArbitraryStudentId() throws Exception {
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("private-v1.pdf")).cookie(studentACookie))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("private-v2.pdf")).cookie(studentACookie))
                .andExpect(status().isOk());
        StudentProfessionalProfile profile = profiles.findByStudentId(studentA.getId()).orElseThrow();
        List<StudentResume> history = resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId());
        assertThat(history).hasSize(2);
        mvc.perform(get("/api/v1/student/resume/download").cookie(studentBCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESUME_NOT_FOUND"));
        for (StudentResume resume : history) {
            mvc.perform(get("/api/v1/student/resume/{id}/download", resume.getId()).cookie(studentBCookie))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/admin/students/{id}/resume/download", studentA.getId()).cookie(studentBCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewProfessionalProfileAndSecurelyDownloadResume() throws Exception {
        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"headline\":\"Backend Developer\"}"))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/student/resume").file(pdf("admin-view.pdf")).cookie(studentACookie))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/professional-profile/skills").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Java\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/students/{id}/professional-profile", studentA.getId()).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headline").value("Backend Developer"))
                .andExpect(jsonPath("$.resume.originalFilename").value("admin-view.pdf"));
        mvc.perform(get("/api/v1/admin/students/{id}/professional-profile/skills", studentA.getId()).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").value("Java"))
                .andExpect(jsonPath("$[0].source").value("STUDENT_ADDED"));
        mvc.perform(get("/api/v1/admin/students/{id}/resume/download", studentA.getId()).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mvc.perform(get("/api/v1/admin/students/{id}/professional-profile", admin.getId()).cookie(adminCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void unauthenticatedAccessAndInvalidProfileInputAreRejected() throws Exception {
        mvc.perform(get("/api/v1/student/professional-profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/resume/download")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/student/professional-profile").cookie(studentACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("headline", "x".repeat(161)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.headline").exists());
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))))
                .andReturn().getResponse().getCookie("ims_auth");
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    private byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Professional resume");
            document.write(bytes);
            return bytes.toByteArray();
        }
    }
}
