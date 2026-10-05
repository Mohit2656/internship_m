package edu.institution.ims.mentor;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mentor_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mentor_profiles_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_mentor_profiles_employee_id", columnNames = "employee_id")
}, indexes = {
        @Index(name = "idx_mentor_profiles_name", columnList = "name"),
        @Index(name = "idx_mentor_profiles_department", columnList = "department")
})
public class MentorProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_profiles_user"))
    private User user;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "employee_id", nullable = false, length = 50) private String employeeId;
    @Column(nullable = false, length = 150) private String department;
    @Column(nullable = false, length = 150) private String designation;
    @Column(nullable = false, length = 25) private String phone;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MentorProfile() {}
    public MentorProfile(User user, String name, String employeeId, String department, String designation, String phone) {
        this.user = user; this.name = name; this.employeeId = employeeId; this.department = department;
        this.designation = designation; this.phone = phone;
    }
    @PrePersist void createTimestamps() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getEmployeeId() { return employeeId; }
    public String getDepartment() { return department; }
    public String getDesignation() { return designation; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
