package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_resumes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_resumes_one_active", columnNames = "active_profile_id"),
        @UniqueConstraint(name = "uk_student_resumes_storage_key", columnNames = "storage_key")
}, indexes = @Index(name = "idx_student_resumes_profile", columnList = "professional_profile_id"))
public class StudentResume {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_profile_id", nullable = false) private StudentProfessionalProfile professionalProfile;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "active_profile_id") private Long activeProfileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "parsing_status", nullable = false, length = 20) private ResumeParsingStatus parsingStatus;

    protected StudentResume() {}

    public StudentResume(StudentProfessionalProfile professionalProfile) { this.professionalProfile = professionalProfile; }

    public void activate(String originalFilename, String storageKey, String contentType, long fileSize) {
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = Instant.now();
        this.active = true;
        this.activeProfileId = professionalProfile.getId();
        this.parsingStatus = ResumeParsingStatus.NOT_PARSED;
    }

    public void deactivate() {
        this.active = false;
        this.activeProfileId = null;
    }

    public void markAnalysisPending() { this.parsingStatus = ResumeParsingStatus.PENDING; }
    public void markAnalysisParsed() { this.parsingStatus = ResumeParsingStatus.PARSED; }
    public void markAnalysisFailed() { this.parsingStatus = ResumeParsingStatus.FAILED; }

    public Long getId() { return id; }
    public StudentProfessionalProfile getProfessionalProfile() { return professionalProfile; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Instant getUploadedAt() { return uploadedAt; }
    public boolean isActive() { return active; }
    public Long getActiveProfileId() { return activeProfileId; }
    public ResumeParsingStatus getParsingStatus() { return parsingStatus; }
}
