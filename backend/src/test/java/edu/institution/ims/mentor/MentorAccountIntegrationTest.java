package edu.institution.ims.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.studentprofile.StudentProfileRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class MentorAccountIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts; @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles; @Autowired PasswordEncoder encoder;
    User admin; User student;

    @BeforeEach void setup() {
        clearAccounts();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("student@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));
        studentAccounts.save(new StudentAccount(student));
    }

    @AfterEach void cleanup() {
        clearAccounts();
    }

    private void clearAccounts() {
        mentorProfiles.deleteAll(); studentProfiles.deleteAll(); studentAccounts.deleteAll(); users.deleteAll();
    }

    @Test void adminCreatesMentorAndMentorCompletesRequiredPasswordChange() throws Exception {
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(loginCookie(admin.getEmail(), "AdminPassword!23"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("mentor@example.edu", "FAC1234"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Dr. Asha Mehta"))
                .andExpect(jsonPath("$.employeeId").value("FAC1234")).andExpect(jsonPath("$.instituteEmail").value("mentor@example.edu"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User mentor = users.findByEmailIgnoreCase("mentor@example.edu").orElseThrow();
        MentorProfile profile = mentorProfiles.findByUserId(mentor.getId()).orElseThrow();
        assertThat(mentor.getRole()).isEqualTo(Role.MENTOR); assertThat(mentor.isActive()).isTrue(); assertThat(mentor.isMustChangePassword()).isTrue();
        assertThat(mentor.getPasswordHash()).startsWith("$2").doesNotContain("FAC1234");
        assertThat(encoder.matches(profile.getEmployeeId(), mentor.getPasswordHash())).isTrue();

        Cookie temporarySession = login("mentor@example.edu", "FAC1234").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR")).andExpect(jsonPath("$.roleLabel").value("Mentor"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn().getResponse().getCookie("ims_auth");
        mvc.perform(get("/api/v1/auth/me").cookie(temporarySession)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MENTOR"));
        mvc.perform(get("/api/v1/mentor/profile").cookie(temporarySession)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(post("/api/v1/auth/change-password").cookie(temporarySession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"MentorPassword!23\",\"confirmPassword\":\"MentorPassword!23\"}"))
                .andExpect(status().isNoContent());
        assertThat(users.findById(mentor.getId()).orElseThrow().isMustChangePassword()).isFalse();
        login("mentor@example.edu", "FAC1234").andExpect(status().isUnauthorized());
        Cookie permanentSession = loginCookie("mentor@example.edu", "MentorPassword!23");
        mvc.perform(get("/api/v1/mentor/profile").cookie(permanentSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dr. Asha Mehta")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(permanentSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("other@example.edu", "FAC9999"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void studentCannotUseMentorOrMentorManagementEndpoints() throws Exception {
        Cookie studentSession = loginCookie(student.getEmail(), "StudentPassword!23");
        mvc.perform(get("/api/v1/mentor/profile").cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(studentSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("mentor@example.edu", "FAC1234"))))
                .andExpect(status().isForbidden());
    }

    @Test void duplicateEmailAndEmployeeIdAreRejectedAndAdminCanListMentors() throws Exception {
        Cookie adminSession = loginCookie(admin.getEmail(), "AdminPassword!23");
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("mentor@example.edu", "FAC1234")))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(" MENTOR@EXAMPLE.EDU ", "FAC5678"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        mvc.perform(post("/api/v1/admin/mentor-accounts").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("second@example.edu", " fac1234 "))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMPLOYEE_ID"));
        mvc.perform(get("/api/v1/admin/mentor-accounts").cookie(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instituteEmail").value("mentor@example.edu"));
    }

    private MentorAccountRequest request(String email, String employeeId) {
        return new MentorAccountRequest("Dr. Asha Mehta", employeeId, email, "Computer Engineering", "Assistant Professor", "+91 9876543210");
    }
    private Cookie loginCookie(String email, String password) throws Exception { return login(email, password).andReturn().getResponse().getCookie("ims_auth"); }
    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password))));
    }
}
