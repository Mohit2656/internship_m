package edu.institution.ims.user;

import jakarta.persistence.*;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.internship.InternshipOnboarding;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"), indexes = {
        @Index(name = "idx_users_role", columnList = "role"), @Index(name = "idx_users_active", columnList = "active")
})
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 60) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY) private StudentProfile studentProfile;
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY) private StudentAccount studentAccount;
    @OneToOne(mappedBy = "student", fetch = FetchType.LAZY) private InternshipOnboarding internshipOnboarding;

    protected User() {}
    public User(String email, String passwordHash, Role role, boolean active) {
        this.email = email.trim().toLowerCase(Locale.ROOT); this.passwordHash = passwordHash; this.role = role; this.active = active;
    }
    @PrePersist void createTimestamps() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void requirePasswordChange() { this.mustChangePassword = true; }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; this.mustChangePassword = false; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public StudentProfile getStudentProfile() { return studentProfile; }
    public StudentAccount getStudentAccount() { return studentAccount; }
    public InternshipOnboarding getInternshipOnboarding() { return internshipOnboarding; }
}
