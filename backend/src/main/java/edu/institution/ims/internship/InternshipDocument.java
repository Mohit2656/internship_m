package edu.institution.ims.internship;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "internship_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_internship_documents_type", columnNames = {"onboarding_id", "document_type"}),
        @UniqueConstraint(name = "uk_internship_documents_storage_key", columnNames = "storage_key")
}, indexes = @Index(name = "idx_internship_documents_onboarding", columnList = "onboarding_id"))
public class InternshipDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "onboarding_id", nullable = false) private InternshipOnboarding onboarding;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30) private DocumentType documentType;
    @Column(name = "original_file_name", nullable = false) private String originalFileName;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;

    protected InternshipDocument() {}
    public InternshipDocument(InternshipOnboarding onboarding, DocumentType type) { this.onboarding = onboarding; this.documentType = type; }
    public void replace(String originalFileName, String storageKey, String contentType, long fileSize) {
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = Instant.now();
    }
    public Long getId() { return id; }
    public InternshipOnboarding getOnboarding() { return onboarding; }
    public DocumentType getDocumentType() { return documentType; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Instant getUploadedAt() { return uploadedAt; }
}
