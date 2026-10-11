package edu.institution.ims.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiBoundaryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.boot.actuate.health.HealthContributorRegistry healthContributors;

    @Test void permittedOriginCanPreflightExistingSkillPatch() throws Exception {
        mvc.perform(options("/api/v1/student/professional-profile/skills/1")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }

    @Test void untrustedOriginCannotPreflightPatch() throws Exception {
        mvc.perform(options("/api/v1/student/professional-profile/skills/1")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PATCH"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void patchStillRequiresAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/student/professional-profile/skills/1")
                .contentType("application/json").content("{\"name\":\"Java\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles="ADMIN")
    void malformedRecordIdIsAClientError() throws Exception {
        mvc.perform(get("/api/v1/admin/students/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test @WithMockUser(roles="STUDENT")
    void malformedBrowserGpsIsAClientErrorRatherThanAServerCrash() throws Exception {
        mvc.perform(multipart("/api/v1/student/attendance/today/physical")
                .file(new MockMultipartFile("photo", "capture.png", "image/png", new byte[]{1}))
                .param("latitude", "not-a-number").param("longitude", "73.8567").param("accuracy", "10"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void publicHealthEndpointExistsWithoutDisclosingDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test void unhealthyDatabaseReturns503WithoutDisclosingDetails() throws Exception {
        var database = healthContributors.getContributor("db");
        try {
            healthContributors.unregisterContributor("db");
            healthContributors.registerContributor("db", (org.springframework.boot.actuate.health.HealthIndicator)
                () -> org.springframework.boot.actuate.health.Health.down().withDetail("privateDetail", "test-only").build());
            mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.privateDetail").doesNotExist());
        } finally {
            healthContributors.unregisterContributor("db");
            if (database != null) healthContributors.registerContributor("db", database);
        }
    }

    @Test void configurationEndpointsAreNotPublic() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());
    }
}
