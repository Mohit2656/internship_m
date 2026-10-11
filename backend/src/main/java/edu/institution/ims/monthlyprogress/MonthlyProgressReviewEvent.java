package edu.institution.ims.monthlyprogress;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

/** Immutable decisions and submission snapshots, including retained photo references. */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "monthly_progress_review_events")
public class MonthlyProgressReviewEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="report_id", nullable=false)
    private MonthlyProgressReport report;
    @Column(nullable=false) private int revision;
    @Column(name="event_type",nullable=false,length=30) private String eventType;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private User actor;
    @Column(name="performed_at",nullable=false) private Instant performedAt;
    @Column(length=1000) private String reason;
    @Column(name="work_description",length=3000) private String workDescription;
    @Column(name="photo_storage_key",length=80) private String photoStorageKey;
    @Column(name="photo_original_name",length=255) private String photoOriginalName;
    @Column(name="image_sha256",length=64) private String imageSha256;
    @Column(name="legacy_submission",nullable=false) private boolean legacySubmission;
    protected MonthlyProgressReviewEvent() {}
    public MonthlyProgressReviewEvent(MonthlyProgressReport report, User actor, String eventType, String reason, Instant at) {
        this.report=report; this.revision=report.getRevision(); this.actor=actor;
        this.eventType=eventType; this.reason=reason; this.performedAt=at;
        if (eventType.equals("SUBMITTED")) {
            workDescription=report.getWorkDescription(); photoStorageKey=report.getPhotoStorageKey();
            photoOriginalName=report.getPhotoOriginalName(); imageSha256=report.getImageSha256();
        }
    }
}
