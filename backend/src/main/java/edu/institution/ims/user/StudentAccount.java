package edu.institution.ims.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_student_accounts_user", columnNames = "user_id"))
public class StudentAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_accounts_user"))
    private User user;
    @Column(name = "student_name", length = 150) private String studentName;
    @Column(length = 50) private String uid;
    @Column private Integer semester;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected StudentAccount() {}
    public StudentAccount(User user) { this.user = user; }
    public StudentAccount(User user, String studentName, String uid, int semester) {
        this.user = user; this.studentName = studentName; this.uid = uid; this.semester = semester;
    }
    @PrePersist void created() { createdAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getStudentName() { return studentName; }
    public String getUid() { return uid; }
    public Integer getSemester() { return semester; }
}
