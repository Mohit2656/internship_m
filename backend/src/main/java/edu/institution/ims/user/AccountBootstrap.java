package edu.institution.ims.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class AccountBootstrap implements ApplicationRunner {
    private final UserRepository users; private final StudentAccountRepository students; private final PasswordEncoder encoder;
    private final String adminEmail; private final String adminPassword; private final List<String> studentEmails; private final List<String> studentPasswords;
    public AccountBootstrap(UserRepository users, StudentAccountRepository students, PasswordEncoder encoder,
            @Value("${bootstrap.admin.email:}") String adminEmail, @Value("${bootstrap.admin.password:}") String adminPassword,
            @Value("${bootstrap.students.emails:}") List<String> studentEmails, @Value("${bootstrap.students.passwords:}") List<String> studentPasswords) {
        this.users = users; this.students = students; this.encoder = encoder; this.adminEmail = adminEmail; this.adminPassword = adminPassword;
        this.studentEmails = studentEmails; this.studentPasswords = studentPasswords;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        createIfConfigured(adminEmail, adminPassword, Role.ADMIN);
        if (studentEmails.size() != studentPasswords.size()) throw new IllegalStateException("BOOTSTRAP_STUDENT_EMAILS and BOOTSTRAP_STUDENT_PASSWORDS must have equal item counts");
        for (int i = 0; i < studentEmails.size(); i++) createIfConfigured(studentEmails.get(i), studentPasswords.get(i), Role.STUDENT);
    }
    private void createIfConfigured(String email, String password, Role role) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;
        if (password.length() < 12) throw new IllegalStateException("Bootstrap passwords must contain at least 12 characters");
        users.findByEmailIgnoreCase(email).ifPresentOrElse(existing -> {
            if (existing.getRole() == Role.STUDENT && !students.existsByUserId(existing.getId())) students.save(new StudentAccount(existing));
        }, () -> {
            var saved = users.save(new User(email, encoder.encode(password), role, true));
            if (role == Role.STUDENT) students.save(new StudentAccount(saved));
        });
    }
}

