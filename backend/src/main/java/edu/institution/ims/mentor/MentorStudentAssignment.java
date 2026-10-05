package edu.institution.ims.mentor;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mentor_student_assignments", uniqueConstraints =
        @UniqueConstraint(name = "uk_mentor_student_assignments_active_student", columnNames = "active_student_user_id"),
        indexes = {
                @Index(name = "idx_mentor_student_assignments_mentor_active", columnList = "mentor_user_id,active"),
                @Index(name = "idx_mentor_student_assignments_student_history", columnList = "student_user_id,assigned_at"),
                @Index(name = "idx_mentor_student_assignments_assigned_by", columnList = "assigned_by")
        })
public class MentorStudentAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mentor_student_assignments_mentor"))
    private User mentor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mentor_student_assignments_student"))
    private User student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mentor_student_assignments_admin"))
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "active_student_user_id", unique = true)
    private Long activeStudentUserId;

    protected MentorStudentAssignment() {}

    public MentorStudentAssignment(User mentor, User student, User assignedBy, Instant assignedAt) {
        this.mentor = mentor;
        this.student = student;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.active = true;
        this.activeStudentUserId = student.getId();
    }

    public void end(Instant endedAt) {
        if (!active) return;
        this.active = false;
        this.endedAt = endedAt;
        this.activeStudentUserId = null;
    }

    public Long getId() { return id; }
    public User getMentor() { return mentor; }
    public User getStudent() { return student; }
    public User getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public boolean isActive() { return active; }
    public Instant getEndedAt() { return endedAt; }
}
