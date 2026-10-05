package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "resume_analyses", uniqueConstraints = @UniqueConstraint(name = "uk_resume_analyses_resume", columnNames = "resume_id"))
public class ResumeAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false) private StudentResume resume;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ResumeParsingStatus status;
    @Column(name = "model_name", length = 200) private String modelName;
    @Column(name = "model_version", length = 200) private String modelVersion;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "failure_reason_safe", length = 100) private String failureReasonSafe;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") private List<ResumeExtractedSkill> skills = new ArrayList<>();
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") private List<ResumeExtractedExperience> experiences = new ArrayList<>();
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") private List<ResumeExtractedEducation> education = new ArrayList<>();
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") private List<ResumeExtractedCertification> certifications = new ArrayList<>();

    protected ResumeAnalysis() {}
    public ResumeAnalysis(StudentResume resume) {
        this.resume = resume;
        this.status = ResumeParsingStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.startedAt = this.createdAt;
    }

    public void restart() {
        status = ResumeParsingStatus.PENDING;
        startedAt = Instant.now();
        completedAt = null;
        failureReasonSafe = null;
        modelName = null;
        modelVersion = null;
        clearResults();
        updatedAt = startedAt;
    }

    public void complete(AiResumeResult result) {
        clearResults();
        modelName = result.modelName();
        modelVersion = result.modelVersion();
        for (int i = 0; i < result.skills().size(); i++) skills.add(new ResumeExtractedSkill(this, result.skills().get(i), i));
        for (int i = 0; i < result.experience().size(); i++) experiences.add(new ResumeExtractedExperience(this, result.experience().get(i), i));
        for (int i = 0; i < result.education().size(); i++) education.add(new ResumeExtractedEducation(this, result.education().get(i), i));
        for (int i = 0; i < result.certifications().size(); i++) certifications.add(new ResumeExtractedCertification(this, result.certifications().get(i), i));
        status = ResumeParsingStatus.PARSED;
        completedAt = Instant.now();
        failureReasonSafe = null;
        updatedAt = completedAt;
    }

    public void fail(String safeReason) {
        status = ResumeParsingStatus.FAILED;
        completedAt = Instant.now();
        failureReasonSafe = safeReason;
        updatedAt = completedAt;
        clearResults();
    }

    private void clearResults() {
        skills.clear(); experiences.clear(); education.clear(); certifications.clear();
    }

    public Long getId() { return id; }
    public StudentResume getResume() { return resume; }
    public ResumeParsingStatus getStatus() { return status; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReasonSafe() { return failureReasonSafe; }
    public List<ResumeExtractedSkill> getSkills() { return List.copyOf(skills); }
    public List<ResumeExtractedExperience> getExperiences() { return List.copyOf(experiences); }
    public List<ResumeExtractedEducation> getEducation() { return List.copyOf(education); }
    public List<ResumeExtractedCertification> getCertifications() { return List.copyOf(certifications); }
}
