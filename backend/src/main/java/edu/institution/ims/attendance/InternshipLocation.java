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

    @Column(name="workplace_name", length=255) private String workplaceName;
    public String getWorkplaceName() { return workplaceName; }
    public void name(String name) { workplaceName = name; }
    @Column(name = "place_id", length = 255) private String placeId;
    @Column(name = "workplace_address", length = 1000) private String workplaceAddress;
    public String getPlaceId() { return placeId; }
    public String getWorkplaceAddress() { return workplaceAddress; }
    public void selectedPlace(String placeId, String address) { this.placeId = placeId; this.workplaceAddress = address; }
    @Version @Column(name="lock_version",nullable=false) private long lockVersion;
    @Column(name="revision",nullable=false) private long revision;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="mentor_approved_by") private User mentorReviewer;
    @Column(name="mentor_reviewed_at") private Instant mentorReviewedAt;
    @Column(name="mentor_decision",length=10) private String mentorDecision;
    @Column(name="mentor_remarks",length=500) private String mentorRemarks;
    @Column(name="iiic_decision",length=10) private String iiicDecision;
    @Column(name="iiic_remarks",length=500) private String iiicRemarks;
    public long getRevision() { return revision; }
    public User getMentorReviewer() { return mentorReviewer; }
    public Instant getMentorReviewedAt() { return mentorReviewedAt; }
    public String getMentorDecision() { return mentorDecision; }
    public String getMentorRemarks() { return mentorRemarks; }
    public String getIiicDecision() { return iiicDecision; }
    public String getIiicRemarks() { return iiicRemarks; }
    public boolean dualApproved() { return status==InternshipLocationStatus.APPROVED && "APPROVE".equals(mentorDecision) && "APPROVE".equals(iiicDecision) && mentorReviewer!=null && mentorReviewer.isActive() && mentorReviewer.getRole()==edu.institution.ims.user.Role.MENTOR && confirmedBy!=null && confirmedBy.isActive() && confirmedBy.getRole()==edu.institution.ims.user.Role.ADMIN && mentorReviewedAt!=null && confirmedAt!=null && workplaceAddress!=null; }
    public void review(User actor,String decision,String remarks,Instant now) {
        if(actor.getRole()==edu.institution.ims.user.Role.MENTOR) { mentorReviewer=actor;mentorReviewedAt=now;mentorDecision=decision;mentorRemarks=remarks; }
        else if(actor.getRole()==edu.institution.ims.user.Role.ADMIN) { confirmedBy=actor;confirmedAt=now;iiicDecision=decision;iiicRemarks=remarks; }
        else throw new IllegalArgumentException("Reviewer role is invalid");
        status="REJECT".equals(mentorDecision)||"REJECT".equals(iiicDecision)?InternshipLocationStatus.REJECTED:"APPROVE".equals(mentorDecision)&&"APPROVE".equals(iiicDecision)?InternshipLocationStatus.APPROVED:InternshipLocationStatus.PENDING;
        updatedAt=now;
    }
    protected InternshipLocation() {}

    public InternshipLocation(InternshipDetails internship, BigDecimal latitude, BigDecimal longitude,
            BigDecimal radius, Instant now) {
        this.internship = internship;
        updateProposal(latitude, longitude, radius, now);
        this.createdAt = now;
    }

    public void updateProposal(BigDecimal latitude, BigDecimal longitude, BigDecimal radius, Instant now) {
        if (this.latitude != null && (this.latitude.compareTo(latitude) != 0 || this.longitude.compareTo(longitude) != 0)) { this.placeId = null; this.workplaceAddress = null; this.workplaceName = null; }
        this.latitude = latitude;
        this.longitude = longitude;
        this.geofenceRadiusMeters = radius;
        this.revision++;
        this.mentorReviewer=null;this.mentorReviewedAt=null;this.mentorDecision=null;this.mentorRemarks=null;this.iiicDecision=null;this.iiicRemarks=null;
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
