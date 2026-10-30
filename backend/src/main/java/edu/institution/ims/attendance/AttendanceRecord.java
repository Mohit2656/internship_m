package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.*;

@Entity
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Table(name = "attendance_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_attendance_records_student_internship_date",
                columnNames = {"student_user_id", "internship_id", "attendance_date", "active_slot"}),
        indexes = {
                @Index(name = "idx_attendance_records_student_date", columnList = "student_user_id,attendance_date"),
                @Index(name = "idx_attendance_records_internship_date", columnList = "internship_id,attendance_date"),
                @Index(name = "idx_attendance_records_date_verification", columnList = "attendance_date,verification_status")
        })
public class AttendanceRecord {
    @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="active_slot") private Integer activeSlot = 1;
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
    @Column(name = "verification_basis", nullable = false, length = 20)
    private AttendanceVerificationBasis verificationBasis = AttendanceVerificationBasis.LEGACY;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, length = 30)
    private AttendanceSubmissionType submissionType;

    @Column(name = "server_submitted_at", nullable = false)
    private Instant serverSubmittedAt;

    @Column(name = "submitted_late", nullable = false)
    private boolean submittedLate;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "late_authorized_by")
    private User lateAuthorizedBy;
    @Column(name = "late_authorized_at")
    private Instant lateAuthorizedAt;
    @Column(name = "automatic_absent_at")
    private Instant automaticAbsentAt;

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

    @OneToOne(mappedBy="attendance", fetch=FetchType.LAZY) private AttendanceEvidence evidence;
    @OneToOne(mappedBy="attendance", fetch=FetchType.LAZY) private AttendanceReview review;
    public AttendanceReview getReview() { return review; }
    public void attachReview(AttendanceReview review) { this.review = review; }
    public AttendanceEvidence getEvidence() { return evidence; }
    public void attachEvidence(AttendanceEvidence evidence) { this.evidence = evidence; }
    public Long getId() { return id; }
    public User getStudent() { return student; }
    public InternshipDetails getInternship() { return internship; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public AttendanceResult getAttendanceResult() { return attendanceResult; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public AttendanceVerificationBasis getVerificationBasis() { return verificationBasis; }
    public void useLivenessVerification() { verificationBasis = AttendanceVerificationBasis.LIVENESS; }
    public boolean isAutomaticAbsent() { return submissionType == AttendanceSubmissionType.AUTO_ABSENT; }
    public boolean isSubmittedLate() { return submittedLate; }
    public User getLateAuthorizedBy() { return lateAuthorizedBy; }
    public Instant getLateAuthorizedAt() { return lateAuthorizedAt; }
    public Instant getAutomaticAbsentAt() { return automaticAbsentAt; }

    void replaceAutomaticAbsence(VerificationStatus status, AttendanceSubmissionType type, Instant submittedAt) {
        if (!isAutomaticAbsent() || evidence != null) throw new IllegalStateException("Only an automatic absence can be replaced");
        automaticAbsentAt = serverSubmittedAt;
        attendanceResult = AttendanceResult.PRESENT;
        verificationStatus = status;
        submissionType = type;
        serverSubmittedAt = submittedAt;
    }

    void recordLatePermission(User authorizedBy, Instant authorizedAt) {
        submittedLate = true;
        lateAuthorizedBy = authorizedBy;
        lateAuthorizedAt = authorizedAt;
    }
    public static AttendanceRecord automaticAbsent(User student, InternshipDetails internship,
            LocalDate date, Instant processedAt) {
        AttendanceRecord record = new AttendanceRecord(student, internship, date, AttendanceResult.ABSENT,
                VerificationStatus.UNVERIFIED, AttendanceSubmissionType.AUTO_ABSENT, processedAt);
        record.verificationBasis = AttendanceVerificationBasis.AUTOMATIC;
        return record;
    }
    public LivenessStatus getLivenessStatus() {
        if (isAutomaticAbsent()) return LivenessStatus.NOT_APPLICABLE;
        if (verificationBasis != AttendanceVerificationBasis.LIVENESS) return LivenessStatus.HISTORICAL;
        if (evidence == null || evidence.getAiVerificationSession() == null || evidence.getAiProcessedAt() == null)
            return LivenessStatus.NOT_PERFORMED;
        boolean accepted = verificationStatus == VerificationStatus.VERIFIED
                && evidence.isAiFaceDetected() && evidence.isAiSingleFace()
                && evidence.isAiQualityPassed() && evidence.isAiLivenessPassed()
                && evidence.getAiFailureReason() == null;
        return accepted ? LivenessStatus.VERIFIED : LivenessStatus.FAILED;
    }
    public AttendanceSubmissionType getSubmissionType() { return submissionType; }
    public Instant getServerSubmittedAt() { return serverSubmittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
