package edu.institution.ims.professionalprofile;

import edu.institution.ims.internship.OnboardingException;
import edu.institution.ims.internship.storage.FileStorageService;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the complete resume suite and PostgreSQL-specific transaction checks in a disposable schema. */
@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties = {"spring.datasource.url=${EXIF_TEST_DB_URL}",
        "spring.datasource.username=${EXIF_TEST_DB_USERNAME}", "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}",
        "spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}",
        "logging.level.root=WARN", "debug=false"})
class ResumeAnalysisPostgresIntegrationTest extends ResumeAnalysisIntegrationTest {
    @MockitoSpyBean FileStorageService trackedStorage;

    @BeforeAll static void requireIsolatedSchema() {
        String schema = System.getenv("EXIF_TEST_DB_SCHEMA");
        if (schema == null || !schema.matches("ims_deletion_test_[0-9a-f]{32}")
                || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema=" + schema))
            throw new IllegalStateException("Resume tests require a newly created isolated local schema");
    }

    @Test void concurrentCorrectionsCommitOneRevisionAndOneCoordinatorEvent() throws Exception {
        long resumeId = parsedResume();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> saveCorrection(resumeId, "Java,Spring"));
            var second = executor.submit(() -> saveCorrection(resumeId, "Java,SQL"));
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("select count(*) from resume_corrections where resume_id=?", Long.class, resumeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select revision from resume_corrections where resume_id=?", Integer.class, resumeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and event_key=?",
                Long.class, admin.getId(), "resume-corrected:" + resumeId + ":1")).isEqualTo(1);
    }

    @Test void notificationFailureRollsBackCorrectionAndRetainsOriginalExtraction() throws Exception {
        long resumeId = parsedResume();
        jdbc.execute("create function fail_resume_correction_notification() returns trigger language plpgsql as $$ "
                + "begin if NEW.notification_type='RESUME_CORRECTED' then raise exception 'simulated notification failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger fail_resume_correction_notification before insert on student_notifications "
                + "for each row execute function fail_resume_correction_notification()");
        try {
            assertThat(saveCorrection(resumeId, "Corrected skill")).isBetween(500, 599);
            assertThat(jdbc.queryForObject("select count(*) from resume_corrections where resume_id=?", Long.class, resumeId)).isZero();
            mvc.perform(get("/api/v1/student/resume/analysis/corrections").cookie(studentACookie))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0))
                    .andExpect(jsonPath("$.values.skills[0]").value("Java"))
                    .andExpect(jsonPath("$.original.skills[0]").value("Java"));
        } finally {
            jdbc.execute("drop trigger fail_resume_correction_notification on student_notifications");
            jdbc.execute("drop function fail_resume_correction_notification()");
        }
    }

    @Test void replacementNotificationFailureRestoresActiveResumeHistoryAndDeletesUncommittedFile() throws Exception {
        long resumeId = parsedResume();
        assertThat(saveCorrection(resumeId, "Retained correction")).isEqualTo(200);
        StudentResume original = resumes.findById(resumeId).orElseThrow();
        long eventsBefore = jdbc.queryForObject("select count(*) from student_notifications where student_user_id in (?,?)",
                Long.class, admin.getId(), studentA.getId());
        jdbc.execute("create function fail_resume_update_notification() returns trigger language plpgsql as $$ "
                + "begin if NEW.notification_type='RESUME_UPDATED' then raise exception 'simulated replacement notification failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger fail_resume_update_notification before insert on student_notifications "
                + "for each row execute function fail_resume_update_notification()");
        try {
            mvc.perform(multipart("/api/v1/student/resume")
                    .file(new MockMultipartFile("file", "replacement.pdf", "application/pdf", "%PDF-1.4\nreplacement\n%%EOF".getBytes()))
                    .cookie(studentACookie)).andExpect(status().is5xxServerError());
            var profile = profiles.findByStudentId(studentA.getId()).orElseThrow();
            assertThat(resumes.findAllByProfessionalProfileIdOrderByUploadedAtAsc(profile.getId())).hasSize(1);
            assertThat(resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()).orElseThrow().getId()).isEqualTo(resumeId);
            assertThat(resumes.findById(resumeId).orElseThrow().getParsingStatus()).isEqualTo(ResumeParsingStatus.PARSED);
            assertThat(jdbc.queryForObject("select count(*) from resume_corrections where resume_id=?", Long.class, resumeId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id in (?,?)",
                    Long.class, admin.getId(), studentA.getId())).isEqualTo(eventsBefore);
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='RESUME_UPDATED'",
                    Long.class, admin.getId())).isZero();
            mvc.perform(get("/api/v1/student/resume/analysis/corrections").cookie(studentACookie))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                    .andExpect(jsonPath("$.values.skills[0]").value("Retained correction"));
            mvc.perform(get("/api/v1/student/resume/download").cookie(studentACookie)).andExpect(status().isOk())
                    .andExpect(content().bytes("%PDF-1.4\nresume\n%%EOF".getBytes()));
            var deletedKey = ArgumentCaptor.forClass(String.class);
            verify(trackedStorage).delete(deletedKey.capture());
            assertThat(deletedKey.getValue()).isNotEqualTo(original.getStorageKey());
            assertThatThrownBy(() -> trackedStorage.load(deletedKey.getValue())).isInstanceOf(OnboardingException.class);
        } finally {
            jdbc.execute("drop trigger fail_resume_update_notification on student_notifications");
            jdbc.execute("drop function fail_resume_update_notification()");
        }
    }

    private long parsedResume() throws Exception {
        String uploaded = mvc.perform(multipart("/api/v1/student/resume")
                .file(new MockMultipartFile("file", "postgres.pdf", "application/pdf", "%PDF-1.4\nresume\n%%EOF".getBytes())).cookie(studentACookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(uploaded).path("resume").path("id").asLong();
        when(aiClient.analyze(any(), anyString(), anyString())).thenReturn(new AiResumeResult("model", "test",
                List.of("Java"), List.of(), List.of(), List.of()));
        mvc.perform(post("/api/v1/student/resume/analysis").cookie(studentACookie)).andExpect(status().isOk());
        return id;
    }

    private int saveCorrection(long resumeId, String skill) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("resumeId", resumeId, "revision", 0,
                "values", java.util.Map.of("skills", List.of(skill), "experience", List.of(), "education", List.of(), "certifications", List.of())));
        return mvc.perform(put("/api/v1/student/resume/analysis/corrections").cookie(studentACookie)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
}
