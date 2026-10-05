package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "hybrid_work_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_hybrid_work_schedule_day",
                columnNames = {"internship_id", "day_of_week"}),
        indexes = @Index(name = "idx_hybrid_work_schedule_internship", columnList = "internship_id"))
public class HybridWorkSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "internship_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hybrid_work_schedule_internship"))
    private InternshipDetails internship;
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;
    @Enumerated(EnumType.STRING) @Column(name = "work_mode", nullable = false, length = 20)
    private HybridWorkMode workMode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HybridWorkSchedule() {}

    public HybridWorkSchedule(InternshipDetails internship, DayOfWeek dayOfWeek,
            HybridWorkMode workMode, Instant now) {
        this.internship = internship;
        this.dayOfWeek = dayOfWeek;
        this.workMode = workMode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public InternshipDetails getInternship() { return internship; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public HybridWorkMode getWorkMode() { return workMode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
