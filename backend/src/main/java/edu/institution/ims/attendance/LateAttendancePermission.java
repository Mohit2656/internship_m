package edu.institution.ims.attendance;

import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "late_attendance_permissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_late_attendance_permission_student_date",
                columnNames = {"student_user_id", "attendance_date"}))
public class LateAttendancePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private MentorStudentAssignment assignment;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by", nullable = false)
    private User authorizedBy;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt;

    protected LateAttendancePermission() {}

    public LateAttendancePermission(MentorStudentAssignment assignment, LocalDate attendanceDate,
            Instant authorizedAt) {
        this.student = assignment.getStudent();
        this.attendanceDate = attendanceDate;
        enable(assignment, authorizedAt);
    }

    public void enable(MentorStudentAssignment assignment, Instant authorizedAt) {
        this.assignment = assignment;
        this.authorizedBy = assignment.getMentor();
        this.authorizedAt = authorizedAt;
        this.enabled = true;
    }

    public void revoke() { this.enabled = false; }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public MentorStudentAssignment getAssignment() { return assignment; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public boolean isEnabled() { return enabled; }
    public User getAuthorizedBy() { return authorizedBy; }
    public Instant getAuthorizedAt() { return authorizedAt; }
}
