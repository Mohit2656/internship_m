package edu.institution.ims.internship;

import com.fasterxml.jackson.databind.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.time.Instant;
import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InternshipOnboardingIntegrationTest {
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n%%EOF".getBytes();
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles; @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDocumentRepository documents; @Autowired InternshipDetailsRepository details; @Autowired PasswordEncoder encoder;
    @Autowired MentorStudentAssignmentRepository assignments;
    User admin; User mentor; User studentA; User studentB; Cookie adminCookie; Cookie mentorCookie; Cookie studentACookie; Cookie studentBCookie;

    @BeforeEach void setUp() throws Exception {
        details.deleteAll(); documents.deleteAll(); onboardings.deleteAll(); profiles.deleteAll(); assignments.deleteAll(); accounts.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        mentor = users.save(new User("mentor@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        studentA = student("student.a@example.edu", "StudentAPassword!23"); studentB = student("student.b@example.edu", "StudentBPassword!23");
        assignments.save(new MentorStudentAssignment(mentor, studentA, admin, Instant.now()));
        adminCookie = login(admin.getEmail(), "AdminPassword!23"); mentorCookie = login(mentor.getEmail(), "MentorPassword!23"); studentACookie = login(studentA.getEmail(), "StudentAPassword!23"); studentBCookie = login(studentB.getEmail(), "StudentBPassword!23");
    }

    @AfterEach void tearDown() { assignments.deleteAll(); }

    @Test void studentStartsInternshipOnboardingAfterCompletingProfile() throws Exception {
        completeProfile(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(studentACookie))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workflowStatus").value("INTERNSHIP_SECURED"))
                .andExpect(jsonPath("$.documentsCompleted").value(0)).andExpect(jsonPath("$.diaryIssued").value(false));
    }

    @Test void incompleteProfileCannotStartOnboarding() throws Exception {
        mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(studentACookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));
    }

    @ParameterizedTest @EnumSource(InternshipSource.class)
    void studentSelectsEachControlledInternshipSource(InternshipSource source) throws Exception {
        start(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"" + source.name() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value(source.name()))
                .andExpect(jsonPath("$.workflowStatus").value("DOCUMENTS_PENDING"));
    }

    @Test void invalidSourceIsRejected() throws Exception {
        start(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(studentACookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"FRIEND\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest @EnumSource(DocumentType.class)
    void studentUploadsEachRequiredDocumentIndependently(DocumentType type) throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type)
                        .file(pdf(type.name().toLowerCase() + ".pdf")).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentsCompleted").value(1))
                .andExpect(jsonPath("$.documents[?(@.documentType == '" + type.name() + "')].uploaded").value(true));
    }

    @Test void unsupportedAndMismatchedFilesAreRejected() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        var executable = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1,2,3});
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/OFFER_LETTER").file(executable).cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT"));
        var fakePdf = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/OFFER_LETTER").file(fakePdf).cookie(studentACookie))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_MISMATCH"));
    }

    @Test void oversizedFileIsRejected() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        byte[] oversized = new byte[10 * 1024 * 1024 + 1]; System.arraycopy(PDF, 0, oversized, 0, PDF.length);
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/OFFER_LETTER")
                        .file(new MockMultipartFile("file", "large.pdf", "application/pdf", oversized)).cookie(studentACookie))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"));
    }

    @Test void studentCannotAccessAnotherStudentsDocument() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        long documentId = uploadAndReadId(studentACookie, DocumentType.OFFER_LETTER);
        mvc.perform(get("/api/v1/student/internship-onboarding/documents/{id}/download", documentId).cookie(studentBCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test void authenticatedRolesCanOnlyAccessDocumentsWithinTheirScope() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        long documentId = uploadAndReadId(studentACookie, DocumentType.OFFER_LETTER);

        mvc.perform(get("/api/v1/student/internship-onboarding/documents/{id}/view", documentId).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline"))).andExpect(content().bytes(PDF));

        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding", studentA.getId()).cookie(mentorCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documents.length()").value(4))
                .andExpect(jsonPath("$.documents[0].uploaded").value(true));
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding/documents/{documentId}/view", studentA.getId(), documentId).cookie(mentorCookie))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding/documents/{documentId}/download", studentA.getId(), documentId).cookie(mentorCookie))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                .andExpect(content().bytes(PDF));

        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding", studentB.getId()).cookie(mentorCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_NOT_ASSIGNED"));
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding/documents/{documentId}/download", studentB.getId(), documentId).cookie(mentorCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_NOT_ASSIGNED"));

        mvc.perform(get("/api/v1/admin/students/{studentId}/internship-onboarding/documents/{documentId}/view", studentA.getId(), documentId).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")));
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship-onboarding", studentA.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test void documentsRemainPendingWithFewerThanFourRequiredFiles() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        upload(studentACookie, DocumentType.OFFER_LETTER); upload(studentACookie, DocumentType.UNDERTAKING);
        mvc.perform(get("/api/v1/student/internship-onboarding").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("DOCUMENTS_PENDING")).andExpect(jsonPath("$.documentsCompleted").value(2));
    }

    @Test void documentsBecomeCompleteOnlyAfterAllFourExist() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu"); uploadAll(studentACookie);
        mvc.perform(get("/api/v1/student/internship-onboarding").cookie(studentACookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("DOCUMENTS_COMPLETE")).andExpect(jsonPath("$.documentsCompleted").value(4))
                .andExpect(jsonPath("$.documentsCompletedAt").isNotEmpty());
    }

    @Test void replacingDocumentKeepsOneRecordAndDoesNotMoveWorkflowBackwards() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu"); uploadAll(studentACookie);
        mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/OFFER_LETTER").file(pdf("replacement.pdf")).cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workflowStatus").value("DOCUMENTS_COMPLETE"))
                .andExpect(jsonPath("$.documentsCompleted").value(4));
        assertThat(documents.count()).isEqualTo(4);
    }

    @Test void diaryCannotBeConfirmedBeforeDocumentsComplete() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu"); upload(studentACookie, DocumentType.OFFER_LETTER);
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(studentACookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENTS_INCOMPLETE"));
    }

    @Test void diaryCanBeConfirmedAfterDocumentsCompleteAndPersists() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu"); uploadAll(studentACookie);
        mvc.perform(post("/api/v1/student/internship-onboarding/diary/confirm").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workflowStatus").value("DIARY_ISSUED"))
                .andExpect(jsonPath("$.diaryIssued").value(true)).andExpect(jsonPath("$.diaryIssuedAt").isNotEmpty());
        mvc.perform(get("/api/v1/student/internship-onboarding").cookie(studentACookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workflowStatus").value("DIARY_ISSUED"));
    }

    @Test void adminCanViewOnboardingSummaryAndDetail() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu"); upload(studentACookie, DocumentType.OFFER_LETTER);
        mvc.perform(get("/api/v1/admin/students").param("internshipStatus", "DOCUMENTS_PENDING").param("internshipSource", "SELF").cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].internshipStatus").value("DOCUMENTS_PENDING"))
                .andExpect(jsonPath("$.content[0].documentsCompleted").value(1));
        mvc.perform(get("/api/v1/admin/students/{id}", studentA.getId()).cookie(adminCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.internshipOnboarding.source").value("SELF"))
                .andExpect(jsonPath("$.internshipOnboarding.documents[0].uploaded").value(true));
    }

    @Test void adminCanSecurelyDownloadStudentDocument() throws Exception {
        startAndSelect(studentACookie, "A", "UID-A", "a@college.edu");
        long documentId = uploadAndReadId(studentACookie, DocumentType.OFFER_LETTER);
        mvc.perform(get("/api/v1/admin/students/{studentId}/internship-onboarding/documents/{documentId}/download", studentA.getId(), documentId).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(content().bytes(PDF));
        mvc.perform(get("/api/v1/admin/students/{studentId}/internship-onboarding/documents/{documentId}/download", studentB.getId(), documentId).cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test void studentCannotAccessAdminDocumentEndpoint() throws Exception {
        mvc.perform(get("/api/v1/admin/students/1/internship-onboarding/documents/1/download").cookie(studentACookie)).andExpect(status().isForbidden());
    }

    @Test void unauthenticatedUserCannotAccessInternshipDocuments() throws Exception {
        mvc.perform(get("/api/v1/student/internship-onboarding/documents/1/download")).andExpect(status().isUnauthorized());
    }

    private User student(String email, String password) { User user = users.save(new User(email, encoder.encode(password), Role.STUDENT, true)); accounts.save(new StudentAccount(user)); return user; }
    private Cookie login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn().getResponse().getCookie("ims_auth"); }
    private void start(Cookie cookie, String suffix, String uid, String email) throws Exception { completeProfile(cookie, suffix, uid, email); mvc.perform(post("/api/v1/student/internship-onboarding/secure").cookie(cookie)).andExpect(status().isCreated()); }
    private void startAndSelect(Cookie cookie, String suffix, String uid, String email) throws Exception { start(cookie, suffix, uid, email); mvc.perform(put("/api/v1/student/internship-onboarding/source").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"SELF\"}")).andExpect(status().isOk()); }
    private void uploadAll(Cookie cookie) throws Exception { for (DocumentType type : DocumentType.values()) upload(cookie, type); }
    private void upload(Cookie cookie, DocumentType type) throws Exception { mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type).file(pdf(type.name().toLowerCase() + ".pdf")).cookie(cookie)).andExpect(status().isOk()); }
    private long uploadAndReadId(Cookie cookie, DocumentType type) throws Exception { String body = mvc.perform(multipart("/api/v1/student/internship-onboarding/documents/{type}", type).file(pdf("offer.pdf")).cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("documents").findValues("id").stream().filter(JsonNode::isNumber).findFirst().orElseThrow().asLong(); }
    private MockMultipartFile pdf(String name) { return new MockMultipartFile("file", name, "application/pdf", PDF); }
    private void completeProfile(Cookie cookie, String suffix, String uid, String instituteEmail) throws Exception { mvc.perform(post("/api/v1/student/profile").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile(suffix, uid, instituteEmail)))).andExpect(status().isCreated()); }
    private Map<String,Object> profile(String suffix, String uid, String instituteEmail) {
        Map<String,Object> p = new LinkedHashMap<>(); p.put("studentName", "Student " + suffix); p.put("branch", "COMPUTER_ENGINEERING"); p.put("uid", uid); p.put("rollNumber", "ROLL-" + suffix);
        p.put("permanentAddress", "Permanent address " + suffix); p.put("currentAddress", ""); p.put("personalEmail", "personal" + suffix.toLowerCase() + "@example.com"); p.put("instituteEmail", instituteEmail);
        p.put("phoneNumber", "+91 98765 43210"); p.put("guardianName", "Guardian " + suffix); p.put("guardianContact", "+91 98765 43211"); p.put("emergencyContact", "+91 98765 43212");
        p.put("bloodGroup", "O+"); p.put("aadhaarNumber", "123456789012"); p.put("facultyMentorName", "Faculty Mentor"); p.put("facultyMentorContact", "+91 98765 43213");
        p.put("internshipCoordinatorName", "Internship Coordinator"); p.put("internshipCoordinatorContact", "+91 98765 43214"); p.put("cdcCoordinatorName", "CDC Coordinator"); p.put("cdcCoordinatorContact", "+91 98765 43215");
        p.put("academicYear", "2026-2027"); p.put("semester", 7); return p;
    }
}
