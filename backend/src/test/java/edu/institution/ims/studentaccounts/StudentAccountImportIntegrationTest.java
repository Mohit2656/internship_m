package edu.institution.ims.studentaccounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class StudentAccountImportIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts; @Autowired PasswordEncoder encoder;
    User admin; User student;

    @BeforeEach void setup() {
        accounts.deleteAll(); users.deleteAll();
        admin = users.save(new User("incharge@example.edu", encoder.encode("AdminPassword!23"), Role.ADMIN, true));
        student = users.save(new User("student@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true)); accounts.save(new StudentAccount(student));
    }

    @Test void adminDownloadsTemplateAndStudentIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/student-accounts/template").cookie(loginCookie(admin.getEmail(), "AdminPassword!23"))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("student-account-import-template.xlsx")));
        mvc.perform(get("/api/v1/admin/student-accounts/template").cookie(loginCookie(student.getEmail(), "StudentPassword!23"))).andExpect(status().isForbidden());
    }

    @Test void importIsPartialIdempotentAndRequiresPasswordChange() throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", "students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook());
        mvc.perform(multipart("/api/v1/admin/student-accounts/import").file(upload).cookie(loginCookie(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(3)).andExpect(jsonPath("$.accountsCreated").value(1))
                .andExpect(jsonPath("$.invalidRows").value(2));
        User imported = users.findByEmailIgnoreCase("aditi@college.edu").orElseThrow();
        assertThat(imported.getRole()).isEqualTo(Role.STUDENT); assertThat(imported.isMustChangePassword()).isTrue();
        assertThat(imported.getPasswordHash()).doesNotContain("2026CSE001"); assertThat(encoder.matches("2026CSE001", imported.getPasswordHash())).isTrue();
        Cookie temporarySession = loginCookie(imported.getEmail(), "2026CSE001");
        mvc.perform(get("/api/v1/auth/me").cookie(temporarySession)).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/v1/student/profile").cookie(temporarySession)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/v1/auth/change-password").cookie(temporarySession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"ChangedPassword!23\",\"confirmPassword\":\"ChangedPassword!23\"}"))
                .andExpect(status().isNoContent());
        assertThat(users.findById(imported.getId()).orElseThrow().isMustChangePassword()).isFalse();
        login(imported.getEmail(), "2026CSE001").andExpect(status().isUnauthorized());
        Cookie changedSession = loginCookie(imported.getEmail(), "ChangedPassword!23");
        mvc.perform(get("/api/v1/student/profile").cookie(changedSession)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/admin/student-accounts/import").file(upload).cookie(loginCookie(admin.getEmail(), "AdminPassword!23")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountsCreated").value(0)).andExpect(jsonPath("$.alreadyExisting").value(1));
    }

    private Cookie loginCookie(String email, String password) throws Exception { return login(email, password).andReturn().getResponse().getCookie("ims_auth"); }
    private ResultActions login(String email, String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new edu.institution.ims.auth.LoginRequest(email, password)))); }
    private static byte[] workbook() throws Exception {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Students"); Row headers = sheet.createRow(0); String[] values = {"Name", "UID", "Email ID", "Semester"};
            for (int i = 0; i < values.length; i++) headers.createCell(i).setCellValue(values[i]);
            row(sheet, 1, "Aditi Sharma", "2026CSE001", "aditi@college.edu", "8");
            row(sheet, 2, "Duplicate UID", "2026CSE001", "second@college.edu", "8");
            row(sheet, 3, "Invalid Email", "2026CSE003", "invalid-email", "8");
            book.write(output); return output.toByteArray();
        }
    }
    private static void row(Sheet sheet, int index, String name, String uid, String email, String semester) { Row row = sheet.createRow(index); row.createCell(0).setCellValue(name); row.createCell(1).setCellValue(uid); row.createCell(2).setCellValue(email); row.createCell(3).setCellValue(semester); }
}
