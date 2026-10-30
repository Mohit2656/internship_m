package edu.institution.ims.attendance;

import jakarta.persistence.*;
import java.time.Instant;

/** Mentor decisions are separate from the student's immutable submission and GPS outcome. */
@Entity
@Table(name = "attendance_reviews")
public class AttendanceReview {
    @Id @Column(name = "attendance_id") private Long attendanceId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "attendance_id") private AttendanceRecord attendance;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AttendanceReviewStatus status;
    @Column(nullable = false) private int revision;
    @Column(name = "reviewer_id", nullable = false) private Long reviewerId;
    @Column(name = "reviewer_name", nullable = false, length = 254) private String reviewerName;
    @Column(name = "reviewed_at", nullable = false) private Instant reviewedAt;
    @Column(length = 1000) private String reason;
    protected AttendanceReview() {}
    public AttendanceReview(AttendanceRecord attendance) { this.attendance = attendance; attendance.attachReview(this); }
    void decide(AttendanceReviewStatus status, Long reviewerId, String reviewerName, Instant at, String reason) {
        this.status = status; this.revision++; this.reviewerId = reviewerId;
        this.reviewerName = reviewerName; this.reviewedAt = at; this.reason = reason;
    }
    public AttendanceReviewStatus getStatus() { return status; }
    public int getRevision() { return revision; }
    public Long getReviewerId() { return reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReason() { return reason; }
}
