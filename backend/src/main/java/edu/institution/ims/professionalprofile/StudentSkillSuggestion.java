package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_skill_suggestions", uniqueConstraints = @UniqueConstraint(name = "uk_student_skill_suggestions_extracted", columnNames = "extracted_skill_id"))
public class StudentSkillSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "professional_profile_id", nullable = false) private StudentProfessionalProfile professionalProfile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resume_analysis_id", nullable = false) private ResumeAnalysis resumeAnalysis;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "extracted_skill_id", nullable = false) private ResumeExtractedSkill extractedSkill;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "normalized_name", nullable = false, length = 100) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 20) private SkillReviewStatus reviewStatus;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_student_skill_id") private StudentSkill resolvedStudentSkill;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected StudentSkillSuggestion() {}
    StudentSkillSuggestion(StudentProfessionalProfile profile, ResumeAnalysis analysis, ResumeExtractedSkill extracted,
            NormalizedSkill skill, StudentSkill existing) {
        professionalProfile = profile; resumeAnalysis = analysis; extractedSkill = extracted;
        displayName = skill.displayName(); normalizedName = skill.normalizedName(); createdAt = Instant.now();
        if (existing == null) reviewStatus = SkillReviewStatus.PENDING;
        else { reviewStatus = SkillReviewStatus.CONFIRMED; resolvedStudentSkill = existing; reviewedAt = createdAt; }
    }
    public void confirm(StudentSkill skill) { reviewStatus = SkillReviewStatus.CONFIRMED; resolvedStudentSkill = skill; reviewedAt = Instant.now(); }
    public void reject() { reviewStatus = SkillReviewStatus.REJECTED; resolvedStudentSkill = null; reviewedAt = Instant.now(); }
    public Long getId() { return id; }
    public StudentProfessionalProfile getProfessionalProfile() { return professionalProfile; }
    public ResumeAnalysis getResumeAnalysis() { return resumeAnalysis; }
    public String getDisplayName() { return displayName; }
    public String getNormalizedName() { return normalizedName; }
    public SkillReviewStatus getReviewStatus() { return reviewStatus; }
}
