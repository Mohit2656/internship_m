package edu.institution.ims.attendance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "attendance_evidence",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_evidence_attendance", columnNames = "attendance_id"),
        indexes = @Index(name = "idx_attendance_evidence_reason", columnList = "verification_reason"))
public class AttendanceEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_evidence_attendance"))
    private AttendanceRecord attendance;
    @Column(precision = 9, scale = 6) private BigDecimal latitude;
    @Column(precision = 10, scale = 6) private BigDecimal longitude;
    @Column(name = "gps_accuracy_meters", precision = 8, scale = 2) private BigDecimal gpsAccuracyMeters;
    @Column(name = "expected_latitude", precision = 9, scale = 6) private BigDecimal expectedLatitude;
    @Column(name = "expected_longitude", precision = 10, scale = 6) private BigDecimal expectedLongitude;
    @Column(name = "geofence_radius_meters", precision = 8, scale = 2) private BigDecimal geofenceRadiusMeters;
    @Column(name = "distance_meters", precision = 10, scale = 2) private BigDecimal distanceMeters;
    @Column(name = "location_check_passed") private Boolean locationCheckPassed;
    @Enumerated(EnumType.STRING) @Column(name = "location_verification_status", length = 40)
    private LocationVerificationStatus locationVerificationStatus;
    @Column(name = "location_captured_at") private Instant locationCapturedAt;
    @Column(name = "workplace_revision") private Long workplaceRevision;
    @Column(name = "exif_timestamp") private Instant exifTimestamp;
    @Column(name = "exif_latitude", precision = 9, scale = 6) private BigDecimal exifLatitude;
    @Column(name = "exif_longitude", precision = 10, scale = 6) private BigDecimal exifLongitude;
    @Column(name = "metadata_consistent") private Boolean metadataConsistent;
    @Column(name = "camera_capture_used", nullable = false) private boolean cameraCaptureUsed;
    @Enumerated(EnumType.STRING) @Column(name = "verification_reason", nullable = false, length = 80)
    private VerificationReason verificationReason;
    @Column(name = "image_media_type", length = 50) private String imageMediaType;
    @Column(name = "image_size_bytes", nullable = false) private long imageSizeBytes;
    @Column(name = "daily_work_summary", length = 1000) private String dailyWorkSummary;
    @Enumerated(EnumType.STRING) @Column(name = "expected_work_mode", length = 20)
    private HybridWorkMode expectedWorkMode;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_verification_session_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_attendance_evidence_ai_session"))
    private AttendanceVerificationSession aiVerificationSession;
    @Enumerated(EnumType.STRING) @Column(name = "ai_challenge_type", length = 30)
    private AiChallengeType aiChallengeType;
    @Column(name = "ai_face_detected", nullable = false) private boolean aiFaceDetected;
    @Column(name = "ai_single_face", nullable = false) private boolean aiSingleFace;
    @Column(name = "ai_quality_passed", nullable = false) private boolean aiQualityPassed;
    @Column(name = "ai_liveness_passed", nullable = false) private boolean aiLivenessPassed;
    @Column(name = "ai_model_version", length = 120) private String aiModelVersion;
    @Column(name = "ai_processed_at") private Instant aiProcessedAt;
    @Enumerated(EnumType.STRING) @Column(name = "ai_failure_reason", length = 80)
    private VerificationReason aiFailureReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @Column(name="workplace_address", length=1000) private String workplaceAddress;
    @Column(name="workplace_place_id", length=255) private String workplacePlaceId;
    public String getWorkplaceAddress() { return workplaceAddress; }
    public String getWorkplacePlaceId() { return workplacePlaceId; }
    protected AttendanceEvidence() {}

    public AttendanceEvidence(AttendanceRecord attendance, PhysicalEvidenceInput input,
            InternshipLocation location, VerificationDecision decision,
            AiVerificationDecision aiDecision, Instant createdAt) {
        this.attendance = attendance;
        attendance.attachEvidence(this);
        this.latitude = input.latitude();
        this.longitude = input.longitude();
        this.gpsAccuracyMeters = input.accuracyMeters();
        this.workplaceAddress = location == null ? null : location.getWorkplaceAddress();
        this.workplacePlaceId = location == null ? null : location.getPlaceId();
        this.expectedLatitude = location == null ? null : location.getLatitude();
        this.expectedLongitude = location == null ? null : location.getLongitude();
        this.geofenceRadiusMeters = location == null ? null : location.getGeofenceRadiusMeters();
        this.distanceMeters = decision.distanceMeters();
        this.locationCheckPassed = decision.locationCheckPassed();
        this.exifTimestamp = input.image().exifTimestamp();
        this.exifLatitude = input.image().exifLatitude();
        this.exifLongitude = input.image().exifLongitude();
        this.metadataConsistent = decision.metadataConsistent();
        this.cameraCaptureUsed = true;
        this.verificationReason = decision.reason();
        this.imageMediaType = input.image().mediaType();
        this.imageSizeBytes = input.image().sizeBytes();
        this.expectedWorkMode = HybridWorkMode.OFFICE;
        applyAi(aiDecision);
        this.createdAt = createdAt;
    }

    public AttendanceEvidence(AttendanceRecord attendance, ValidatedCameraImage image,
            String dailyWorkSummary, Boolean metadataConsistent, VerificationReason reason,
            AiVerificationDecision aiDecision, Instant createdAt) {
        this.attendance = attendance;
        attendance.attachEvidence(this);
        this.exifTimestamp = image.exifTimestamp();
        this.exifLatitude = image.exifLatitude();
        this.exifLongitude = image.exifLongitude();
        this.metadataConsistent = metadataConsistent;
        this.cameraCaptureUsed = true;
        this.verificationReason = reason;
        this.imageMediaType = image.mediaType();
        this.imageSizeBytes = image.sizeBytes();
        this.dailyWorkSummary = dailyWorkSummary;
        this.expectedWorkMode = HybridWorkMode.REMOTE;
        applyAi(aiDecision);
        this.createdAt = createdAt;
    }

    public AttendanceEvidence(AttendanceRecord attendance, SubmissionLocation location, Instant createdAt) {
        this.attendance = attendance;
        attendance.attachEvidence(this);
        recordSubmissionLocation(location.latitude(), location.longitude(), location.accuracyMeters());
        this.verificationReason = VerificationReason.EDGE_AI_EVIDENCE_MISSING;
        this.createdAt = createdAt;
        // BASIC evidence has no camera photograph or liveness session.
    }

    private void applyAi(AiVerificationDecision decision) {
        this.aiVerificationSession = decision.session();
        this.aiChallengeType = decision.challenge();
        this.aiFaceDetected = decision.faceDetected();
        this.aiSingleFace = decision.singleFace();
        this.aiQualityPassed = decision.qualityPassed();
        this.aiLivenessPassed = decision.livenessPassed();
        this.aiModelVersion = decision.modelVersion();
        this.aiProcessedAt = decision.processedAt();
        this.aiFailureReason = decision.accepted() ? null : decision.reason();
    }

    void recordSubmissionLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy) {
        SubmissionLocation submitted = SubmissionLocation.from(latitude, longitude, accuracy);
        this.latitude = submitted.latitude();
        this.longitude = submitted.longitude();
        this.gpsAccuracyMeters = submitted.accuracyMeters();
    }

    void recordLocationVerification(InternshipLocation approvedWorkplace, LocationVerificationDecision decision,
            Instant capturedAt) {
        this.locationVerificationStatus = decision.status();
        this.locationCapturedAt = capturedAt;
        this.workplaceRevision = approvedWorkplace == null ? null : approvedWorkplace.getRevision();
        this.expectedLatitude = approvedWorkplace == null ? null : approvedWorkplace.getLatitude();
        this.expectedLongitude = approvedWorkplace == null ? null : approvedWorkplace.getLongitude();
        this.geofenceRadiusMeters = approvedWorkplace == null ? null : approvedWorkplace.getGeofenceRadiusMeters();
        this.workplaceAddress = approvedWorkplace == null ? null : approvedWorkplace.getWorkplaceAddress();
        this.workplacePlaceId = approvedWorkplace == null ? null : approvedWorkplace.getPlaceId();
        this.distanceMeters = decision.distanceMeters();
        this.locationCheckPassed = decision.passed();
    }

    public LocationVerificationStatus getLocationVerificationStatus() { return locationVerificationStatus; }
    public Instant getLocationCapturedAt() { return locationCapturedAt; }
    public Long getWorkplaceRevision() { return workplaceRevision; }

    public Long getId() { return id; }
    public AttendanceRecord getAttendance() { return attendance; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getGpsAccuracyMeters() { return gpsAccuracyMeters; }
    public BigDecimal getExpectedLatitude() { return expectedLatitude; }
    public BigDecimal getExpectedLongitude() { return expectedLongitude; }
    public BigDecimal getGeofenceRadiusMeters() { return geofenceRadiusMeters; }
    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public Boolean getLocationCheckPassed() { return locationCheckPassed; }
    public Instant getExifTimestamp() { return exifTimestamp; }
    public BigDecimal getExifLatitude() { return exifLatitude; }
    public BigDecimal getExifLongitude() { return exifLongitude; }
    public Boolean getMetadataConsistent() { return metadataConsistent; }
    public boolean isCameraCaptureUsed() { return cameraCaptureUsed; }
    public VerificationReason getVerificationReason() { return verificationReason; }
    public String getImageMediaType() { return imageMediaType; }
    public long getImageSizeBytes() { return imageSizeBytes; }
    public String getDailyWorkSummary() { return dailyWorkSummary; }
    public HybridWorkMode getExpectedWorkMode() { return expectedWorkMode; }
    public AttendanceVerificationSession getAiVerificationSession() { return aiVerificationSession; }
    public AiChallengeType getAiChallengeType() { return aiChallengeType; }
    public boolean isAiFaceDetected() { return aiFaceDetected; }
    public boolean isAiSingleFace() { return aiSingleFace; }
    public boolean isAiQualityPassed() { return aiQualityPassed; }
    public boolean isAiLivenessPassed() { return aiLivenessPassed; }
    public String getAiModelVersion() { return aiModelVersion; }
    public Instant getAiProcessedAt() { return aiProcessedAt; }
    public VerificationReason getAiFailureReason() { return aiFailureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
