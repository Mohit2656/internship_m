package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "internship_locations",
        uniqueConstraints = @UniqueConstraint(name = "uk_internship_locations_internship", columnNames = "internship_id"),
        indexes = @Index(name = "idx_internship_locations_status", columnList = "status"))
public class InternshipLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "internship_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_internship_locations_internship"))
    private InternshipDetails internship;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 10, scale = 6) private BigDecimal longitude;
    @Column(name = "geofence_radius_meters", nullable = false, precision = 8, scale = 2)
    private BigDecimal geofenceRadiusMeters;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InternshipLocationStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by", foreignKey = @ForeignKey(name = "fk_internship_locations_confirmer"))
    private User confirmedBy;

    protected InternshipLocation() {}

    public InternshipLocation(InternshipDetails internship, BigDecimal latitude, BigDecimal longitude,
            BigDecimal radius, Instant now) {
        this.internship = internship;
        updateProposal(latitude, longitude, radius, now);
        this.createdAt = now;
    }

    public void updateProposal(BigDecimal latitude, BigDecimal longitude, BigDecimal radius, Instant now) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geofenceRadiusMeters = radius;
        this.status = InternshipLocationStatus.PENDING;
        this.confirmedAt = null;
        this.confirmedBy = null;
        this.updatedAt = now;
    }

    public void confirm(User admin, Instant now) {
        this.status = InternshipLocationStatus.CONFIRMED;
        this.confirmedBy = admin;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public InternshipDetails getInternship() { return internship; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getGeofenceRadiusMeters() { return geofenceRadiusMeters; }
    public InternshipLocationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public User getConfirmedBy() { return confirmedBy; }
}
