package edu.institution.ims.internship;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "internship_onboarding", uniqueConstraints = @UniqueConstraint(name = "uk_internship_onboarding_student", columnNames = "student_user_id"), indexes = {
        @Index(name = "idx_internship_onboarding_status", columnList = "workflow_status"),
        @Index(name = "idx_internship_onboarding_source", columnList = "source")
})
public class InternshipOnboarding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_user_id", nullable = false) private User student;
    @Enumerated(EnumType.STRING) @Column(length = 20) private InternshipSource source;
    @Enumerated(EnumType.STRING) @Column(name = "workflow_status", nullable = false, length = 30) private WorkflowStatus workflowStatus;
    @Column(name = "secured_at", nullable = false, updatable = false) private Instant securedAt;
    @Column(name = "documents_completed_at") private Instant documentsCompletedAt;
    @Column(name = "diary_issued", nullable = false) private boolean diaryIssued;
    @Column(name = "diary_issued_at") private Instant diaryIssuedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToOne(mappedBy = "onboarding", fetch = FetchType.LAZY) private InternshipDetails internshipDetails;

    protected InternshipOnboarding() {}
    public InternshipOnboarding(User student) {
        this.student = student;
        this.workflowStatus = WorkflowStatus.INTERNSHIP_SECURED;
    }
    @PrePersist void createTimestamps() { var now = Instant.now(); securedAt = now; createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public void selectSource(InternshipSource source) {
        this.source = source;
        if (workflowStatus == WorkflowStatus.INTERNSHIP_SECURED) workflowStatus = WorkflowStatus.DOCUMENTS_PENDING;
    }
    public void markDocumentsComplete() {
        if (workflowStatus == WorkflowStatus.DIARY_ISSUED || workflowStatus == WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) return;
        workflowStatus = WorkflowStatus.DOCUMENTS_COMPLETE;
        if (documentsCompletedAt == null) documentsCompletedAt = Instant.now();
    }
    public void confirmDiary() {
        if (workflowStatus == WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE) return;
        diaryIssued = true;
        if (diaryIssuedAt == null) diaryIssuedAt = Instant.now();
        workflowStatus = WorkflowStatus.DIARY_ISSUED;
    }
    public void markInternshipDetailsComplete() {
        if (workflowStatus != WorkflowStatus.DIARY_ISSUED && workflowStatus != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE)
            throw new IllegalStateException("Internship details require an issued diary");
        workflowStatus = WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE;
    }
    public Long getId() { return id; }
    public User getStudent() { return student; }
    public InternshipSource getSource() { return source; }
    public WorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public Instant getSecuredAt() { return securedAt; }
    public Instant getDocumentsCompletedAt() { return documentsCompletedAt; }
    public boolean isDiaryIssued() { return diaryIssued; }
    public Instant getDiaryIssuedAt() { return diaryIssuedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public InternshipDetails getInternshipDetails() { return internshipDetails; }
}
