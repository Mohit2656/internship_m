package edu.institution.ims.attendance;

import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** An append-only record of an effective grant or revocation. */
@Entity
@Table(name = "late_attendance_permission_audit")
public class LateAttendancePermissionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false, updatable = false)
    private LateAttendancePermission permission;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private MentorStudentAssignment assignment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false)
    private User student;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, updatable = false)
    private boolean enabled;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected LateAttendancePermissionAudit() {}

    public LateAttendancePermissionAudit(LateAttendancePermission permission,
            MentorStudentAssignment assignment, boolean enabled, Instant changedAt) {
        this.permission = permission;
        this.assignment = assignment;
        this.student = assignment.getStudent();
        this.attendanceDate = permission.getAttendanceDate();
        this.enabled = enabled;
        this.changedBy = assignment.getMentor();
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public LateAttendancePermission getPermission() { return permission; }
    public MentorStudentAssignment getAssignment() { return assignment; }
    public User getStudent() { return student; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public boolean isEnabled() { return enabled; }
    public User getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
