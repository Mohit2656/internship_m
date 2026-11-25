package edu.institution.ims.opportunity;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "opportunity_interests", uniqueConstraints = @UniqueConstraint(
        name = "uk_opportunity_interest_student", columnNames = {"opportunity_id", "student_user_id"}))
public class OpportunityInterest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opportunity_id") private Opportunity opportunity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_user_id") private User student;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected OpportunityInterest() {}
    public OpportunityInterest(Opportunity opportunity, User student) { this.opportunity = opportunity; this.student = student; }
    @PrePersist void setCreatedAt() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Opportunity getOpportunity() { return opportunity; }
    public User getStudent() { return student; }
    public Instant getCreatedAt() { return createdAt; }
}

