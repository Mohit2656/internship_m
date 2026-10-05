package edu.institution.ims.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.user.*;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired StudentAccountRepository students; @Autowired PasswordEncoder encoder;
    @Autowired StudentProfileRepository profiles;
    User admin; User student; User inactive;

    @BeforeEach void setUp() {
        profiles.deleteAll(); students.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("student1@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        students.save(new StudentAccount(student));
        inactive = users.save(new User("inactive@example.edu", encoder.encode("InactivePassword!23"), Role.STUDENT, false));
        students.save(new StudentAccount(inactive));
    }

    @Test void successfulAdminLoginAndMe() throws Exception {
        Cookie auth = login("incharge@example.edu", "AdminPassword!23").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.roleLabel").value("III Cell Incharge"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly"))).andReturn().getResponse().getCookie("ims_auth");
        assertThat(auth).isNotNull();
        mvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(admin.getId())).andExpect(jsonPath("$.email").value(admin.getEmail())).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void successfulStudentLogin() throws Exception {
        login("student1@example.edu", "StudentPassword!23").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT")).andExpect(jsonPath("$.id").value(student.getId()));
    }

    @Test void loginNormalizesEmailWhitespaceAndCaseWithoutChangingThePassword() throws Exception {
        login("  STUDENT1@EXAMPLE.EDU  ", "StudentPassword!23").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(student.getId()));
    }

    @Test void incorrectPasswordReturns401() throws Exception { login("student1@example.edu", "WrongPassword!23").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")); }
    @Test void inactiveAccountReturns403() throws Exception { login("inactive@example.edu", "InactivePassword!23").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE")); }
    @Test void unauthenticatedMeReturns401() throws Exception { mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()); }

    @Test void logoutClearsCookie() throws Exception {
        Cookie auth = login("student1@example.edu", "StudentPassword!23").andReturn().getResponse().getCookie("ims_auth");
        mvc.perform(post("/api/v1/auth/logout").cookie(auth)).andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("ims_auth="), org.hamcrest.Matchers.containsString("Max-Age=0"))));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void studentCannotAccessAdminEndpoint() throws Exception {
        Cookie auth = login("student1@example.edu", "StudentPassword!23").andReturn().getResponse().getCookie("ims_auth");
        mvc.perform(get("/api/v1/admin/verification").cookie(auth)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void passwordsAreBcryptHashes() {
        User stored = users.findById(student.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").doesNotContain("StudentPassword!23");
        assertThat(encoder.matches("StudentPassword!23", stored.getPasswordHash())).isTrue();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password))));
    }
}
