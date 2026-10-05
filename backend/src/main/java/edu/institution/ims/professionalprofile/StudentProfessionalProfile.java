package edu.institution.ims.professionalprofile;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_professional_profiles", uniqueConstraints =
        @UniqueConstraint(name = "uk_student_professional_profiles_student", columnNames = "student_user_id"))
public class StudentProfessionalProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false) private User student;
    @Column(length = 160) private String headline;
    @Column(name = "career_interests", length = 1000) private String careerInterests;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected StudentProfessionalProfile() {}

    public StudentProfessionalProfile(User student) { this.student = student; }

    @PrePersist void created() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public void update(String headline, String careerInterests) {
        this.headline = headline;
        this.careerInterests = careerInterests;
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public String getHeadline() { return headline; }
    public String getCareerInterests() { return careerInterests; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
