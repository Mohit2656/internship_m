package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_skills", uniqueConstraints = @UniqueConstraint(name = "uk_student_skills_active", columnNames = {"professional_profile_id", "active_normalized_name"}))
public class StudentSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "professional_profile_id", nullable = false) private StudentProfessionalProfile professionalProfile;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "normalized_name", nullable = false, length = 100) private String normalizedName;
    @Column(name = "active_normalized_name", length = 100) private String activeNormalizedName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StudentSkillSource source;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_resume_analysis_id") private ResumeAnalysis sourceResumeAnalysis;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected StudentSkill() {}
    StudentSkill(StudentProfessionalProfile profile, NormalizedSkill skill, StudentSkillSource source, ResumeAnalysis analysis) {
        professionalProfile = profile; this.source = source; sourceResumeAnalysis = analysis; active = true;
        displayName = skill.displayName(); normalizedName = skill.normalizedName(); activeNormalizedName = normalizedName;
        createdAt = Instant.now(); updatedAt = createdAt;
    }
    public void rename(NormalizedSkill skill) {
        displayName = skill.displayName(); normalizedName = skill.normalizedName(); activeNormalizedName = normalizedName; updatedAt = Instant.now();
    }
    public void deactivate() { active = false; activeNormalizedName = null; updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public StudentProfessionalProfile getProfessionalProfile() { return professionalProfile; }
    public String getDisplayName() { return displayName; }
    public String getNormalizedName() { return normalizedName; }
    public StudentSkillSource getSource() { return source; }
    public boolean isActive() { return active; }
}
