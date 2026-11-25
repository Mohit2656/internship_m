package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_exceptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_exception_date",
                columnNames = {"internship_id", "exception_date", "exception_type"}),
        indexes = {
                @Index(name = "idx_attendance_exceptions_student_date", columnList = "student_user_id, exception_date"),
                @Index(name = "idx_attendance_exceptions_internship", columnList = "internship_id, status")
        })
public class AttendanceDailyException {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "internship_id", nullable = false)
    private InternshipDetails internship;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 30)
    private AttendanceExceptionType exceptionType;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceExceptionStatus status = AttendanceExceptionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AttendanceDailyException() {}

    public AttendanceDailyException(InternshipDetails internship, User student, LocalDate exceptionDate,
                                   AttendanceExceptionType exceptionType, String reason, AttendanceExceptionStatus status) {
        this.internship = internship;
        this.student = student;
        this.exceptionDate = exceptionDate;
        this.exceptionType = exceptionType;
        this.reason = reason;
        this.status = status != null ? status : AttendanceExceptionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void review(User reviewer, AttendanceExceptionStatus newStatus, String remarks) {
        this.reviewedBy = reviewer;
        this.status = newStatus;
        this.reviewRemarks = remarks;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public InternshipDetails getInternship() { return internship; }
    public User getStudent() { return student; }
    public LocalDate getExceptionDate() { return exceptionDate; }
    public AttendanceExceptionType getExceptionType() { return exceptionType; }
    public String getReason() { return reason; }
    public AttendanceExceptionStatus getStatus() { return status; }
    public User getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewRemarks() { return reviewRemarks; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
