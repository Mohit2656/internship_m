package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "attendance_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_attendance_records_student_internship_date",
                columnNames = {"student_user_id", "internship_id", "attendance_date"}),
        indexes = {
                @Index(name = "idx_attendance_records_student_date", columnList = "student_user_id,attendance_date"),
                @Index(name = "idx_attendance_records_internship_date", columnList = "internship_id,attendance_date"),
                @Index(name = "idx_attendance_records_date_verification", columnList = "attendance_date,verification_status")
        })
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_records_student"))
    private User student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "internship_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_records_internship"))
    private InternshipDetails internship;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_result", nullable = false, length = 20)
    private AttendanceResult attendanceResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, length = 30)
    private AttendanceSubmissionType submissionType;

    @Column(name = "server_submitted_at", nullable = false, updatable = false)
    private Instant serverSubmittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceRecord() {}

    public AttendanceRecord(User student, InternshipDetails internship, LocalDate attendanceDate,
            AttendanceResult attendanceResult, VerificationStatus verificationStatus,
            AttendanceSubmissionType submissionType, Instant serverSubmittedAt) {
        this.student = student;
        this.internship = internship;
        this.attendanceDate = attendanceDate;
        this.attendanceResult = attendanceResult;
        this.verificationStatus = verificationStatus;
        this.submissionType = submissionType;
        this.serverSubmittedAt = serverSubmittedAt;
    }

    @PrePersist
    void createTimestamps() {
        createdAt = serverSubmittedAt;
        updatedAt = serverSubmittedAt;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public InternshipDetails getInternship() { return internship; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public AttendanceResult getAttendanceResult() { return attendanceResult; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public AttendanceSubmissionType getSubmissionType() { return submissionType; }
    public Instant getServerSubmittedAt() { return serverSubmittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
