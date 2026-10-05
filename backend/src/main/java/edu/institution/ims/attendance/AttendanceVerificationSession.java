package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "attendance_verification_sessions", indexes = {
        @Index(name = "idx_attendance_verification_sessions_student_date",
                columnList = "student_user_id, attendance_date"),
        @Index(name = "idx_attendance_verification_sessions_expiry", columnList = "expires_at")
})
public class AttendanceVerificationSession {
    @Id @Column(length = 36) private String id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_verification_sessions_student"))
    private User student;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "internship_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_verification_sessions_internship"))
    private InternshipDetails internship;
    @Column(name = "attendance_date", nullable = false) private LocalDate attendanceDate;
    @Enumerated(EnumType.STRING) @Column(name = "challenge_type", nullable = false, length = 30)
    private AiChallengeType challengeType;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;

    protected AttendanceVerificationSession() {}

    public AttendanceVerificationSession(String id, User student, InternshipDetails internship,
            LocalDate attendanceDate, AiChallengeType challengeType, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.student = student;
        this.internship = internship;
        this.attendanceDate = attendanceDate;
        this.challengeType = challengeType;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void consume(Instant at) { this.consumedAt = at; }
    public String getId() { return id; }
    public User getStudent() { return student; }
    public InternshipDetails getInternship() { return internship; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public AiChallengeType getChallengeType() { return challengeType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}

