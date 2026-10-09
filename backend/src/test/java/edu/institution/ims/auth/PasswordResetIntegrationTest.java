package edu.institution.ims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
    User admin, student, mentor, inactive;
    static final String ORIGINAL = "OriginalPassword!23";
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        admin = users.save(new User("admin" + suffix + "@example.edu", encoder.encode(ORIGINAL), Role.ADMIN, true));
        student = users.save(new User("student" + suffix + "@example.edu", encoder.encode(ORIGINAL), Role.STUDENT, true));
        mentor = users.save(new User("mentor" + suffix + "@example.edu", encoder.encode(ORIGINAL), Role.MENTOR, true));
        inactive = users.save(new User("inactive" + suffix + "@example.edu", encoder.encode(ORIGINAL), Role.STUDENT, false));
    }
    ResultActions login(User user, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", password))));
    }
    Cookie cookie(User user) throws Exception { return login(user, ORIGINAL).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth"); }
    ResultActions reset(Long id, Cookie auth) throws Exception { return mvc.perform(post("/api/v1/admin/users/" + id + "/reset-password").cookie(auth)); }
    @Test void resetAndForcedChangeRevokesBothOldAndTemporarySessions() throws Exception {
        Cookie old = cookie(student);
        var response = reset(student.getId(), cookie(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.mustChangePassword").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn().getResponse();
        String temporary = json.readTree(response.getContentAsString()).get("temporaryPassword").asText();
        assertThat(temporary).matches("[A-Za-z0-9_-]{24}");
        var stored = users.findById(student.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(temporary);
        assertThat(encoder.matches(temporary, stored.getPasswordHash())).isTrue();
        assertThat(stored.getTokenVersion()).isEqualTo(1);
        login(student, ORIGINAL).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(old)).andExpect(status().isUnauthorized());
        Cookie temp = login(student, temporary).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn().getResponse().getCookie("ims_auth");
        mvc.perform(get("/api/v1/student/profile").cookie(temp)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").cookie(temp)).andExpect(status().isOk()).andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        String otherHash = users.findById(mentor.getId()).orElseThrow().getPasswordHash();
        Cookie changed = mvc.perform(post("/api/v1/auth/change-password").cookie(temp).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword", "NewSecurePassword!45", "confirmPassword", "NewSecurePassword!45", "userId", mentor.getId()))))
            .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("ims_auth");
        assertThat(users.findById(mentor.getId()).orElseThrow().getPasswordHash()).isEqualTo(otherHash);
        assertThat(users.findById(student.getId()).orElseThrow().isMustChangePassword()).isFalse();
        mvc.perform(get("/api/v1/auth/me").cookie(temp)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(changed)).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        login(student, temporary).andExpect(status().isUnauthorized());
        login(student, "NewSecurePassword!45").andExpect(status().isOk());
    }
    @Test void legacyJwtWorksUntilAccountIsReset() throws Exception {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-only-secret-with-more-than-thirty-two-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = io.jsonwebtoken.Jwts.builder().subject(student.getId().toString()).claim("role", "STUDENT")
            .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(120))).signWith(key).compact();
        Cookie legacy = new Cookie("ims_auth", token);
        mvc.perform(get("/api/v1/auth/me").cookie(legacy)).andExpect(status().isOk());
        reset(student.getId(), cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(legacy)).andExpect(status().isUnauthorized());
    }
    @Test void authorizationAndEligibility() throws Exception {
        mvc.perform(post("/api/v1/admin/users/" + student.getId() + "/reset-password")).andExpect(status().isUnauthorized());
        reset(student.getId(), cookie(mentor)).andExpect(status().isForbidden());
        reset(mentor.getId(), cookie(student)).andExpect(status().isForbidden());
        Cookie auth = cookie(admin);
        reset(Long.MAX_VALUE, auth).andExpect(status().isNotFound()).andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        reset(inactive.getId(), auth).andExpect(status().isConflict());
        reset(admin.getId(), auth).andExpect(status().isConflict());
        reset(mentor.getId(), auth).andExpect(status().isOk());
        assertThat(users.findById(inactive.getId()).orElseThrow().getTokenVersion()).isZero();
    }
    @Test void repeatedResetReplacesCredentialAndDoesNotLeakInToString() throws Exception {
        Cookie auth = cookie(admin);
        String first = json.readTree(reset(student.getId(), auth).andReturn().getResponse().getContentAsString()).get("temporaryPassword").asText();
        String second = json.readTree(reset(student.getId(), auth).andReturn().getResponse().getContentAsString()).get("temporaryPassword").asText();
        assertThat(second).isNotEqualTo(first);
        login(student, first).andExpect(status().isUnauthorized());
        assertThat(users.findById(student.getId()).orElseThrow().getTokenVersion()).isEqualTo(2);
        assertThat(new AdminPasswordResetService.PasswordResetResponse(1L, "safe@example.edu", second, true).toString()).doesNotContain(second);
    }
    @Test void rejectsSamePasswordAndBcryptTruncation() throws Exception {
        Cookie auth = cookie(student);
        for (String password : new String[]{ORIGINAL, "x".repeat(73), "\u00e9".repeat(37)}) {
            mvc.perform(post("/api/v1/auth/change-password").cookie(auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("newPassword", password, "confirmPassword", password)))).andExpect(status().isBadRequest());
        }
        login(student, ORIGINAL).andExpect(status().isOk());
    }
}
