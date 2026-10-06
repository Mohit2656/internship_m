package edu.institution.ims.monthlyprogress;

import edu.institution.ims.internship.InternshipDetails;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "monthly_progress_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_monthly_progress_reports_month", columnNames = {"student_internship_id", "report_month_number"}),
        indexes = {
                @Index(name = "idx_monthly_progress_reports_internship", columnList = "student_internship_id"),
                @Index(name = "idx_monthly_progress_reports_submitted_at", columnList = "submitted_at")
        })
public class MonthlyProgressReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_internship_id", nullable = false, foreignKey = @ForeignKey(name = "fk_monthly_progress_reports_internship"))
    private InternshipDetails studentInternship;
    @Column(name = "report_month_number", nullable = false) private int reportMonthNumber;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "work_description", nullable = false, length = 3000) private String workDescription;
    @Column(name = "photo_storage_key", nullable = false, length = 80) private String photoStorageKey;
    @Column(name = "photo_original_name", nullable = false, length = 255) private String photoOriginalName;
    @Column(name = "photo_mime_type", nullable = false, length = 50) private String photoMimeType;
    @Column(name = "photo_size", nullable = false) private long photoSize;
    @Column(name = "exif_captured_at") private Instant exifCapturedAt;
    @Column(name = "exif_latitude", precision = 12, scale = 9) private BigDecimal exifLatitude;
    @Column(name = "exif_longitude", precision = 12, scale = 9) private BigDecimal exifLongitude;
    @Column(name = "location_name", length = 255) private String locationName;
    @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Enumerated(EnumType.STRING) @Column(name = "geo_verification_status", length = 40) private GeoVerificationStatus geoVerificationStatus;
    @Column(name = "geo_verification_message", length = 500) private String geoVerificationMessage;
    @Column(name = "capture_time_status", length = 32) private String captureTimeStatus;
    @Column(name = "gps_altitude", precision = 18, scale = 6) private BigDecimal gpsAltitude;
    @Column(name = "gps_timestamp") private Instant gpsTimestamp;
    @Column(name = "camera_make", length = 255) private String cameraMake;
    @Column(name = "camera_model", length = 255) private String cameraModel;
    @Column(name = "image_width") private Integer imageWidth;
    @Column(name = "image_height") private Integer imageHeight;
    @Column(name = "exif_present") private Boolean exifPresent;
    @Column(name = "gps_present") private Boolean gpsPresent;
    @Column(name = "image_sha256", length = 64) private String imageSha256;

    protected MonthlyProgressReport() {}

    public MonthlyProgressReport(InternshipDetails internship, int monthNumber, LocalDate periodStart, LocalDate periodEnd,
            String workDescription, String storageKey, String originalName, String mimeType, long photoSize,
            Instant capturedAt, BigDecimal latitude, BigDecimal longitude, Instant submittedAt) {
        this.studentInternship = internship;
        this.reportMonthNumber = monthNumber;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.workDescription = workDescription;
        this.photoStorageKey = storageKey;
        this.photoOriginalName = originalName;
        this.photoMimeType = mimeType;
        this.photoSize = photoSize;
        this.exifCapturedAt = capturedAt;
        this.exifLatitude = latitude;
        this.exifLongitude = longitude;
        this.submittedAt = submittedAt;
    }

    @PrePersist void created() { createdAt = submittedAt == null ? Instant.now() : submittedAt; updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public InternshipDetails getStudentInternship() { return studentInternship; }
    public int getReportMonthNumber() { return reportMonthNumber; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getWorkDescription() { return workDescription; }
    public String getPhotoStorageKey() { return photoStorageKey; }
    public String getPhotoOriginalName() { return photoOriginalName; }
    public String getPhotoMimeType() { return photoMimeType; }
    public long getPhotoSize() { return photoSize; }
    public Instant getExifCapturedAt() { return exifCapturedAt; }
    public BigDecimal getExifLatitude() { return exifLatitude; }
    public BigDecimal getExifLongitude() { return exifLongitude; }
    public String getLocationName() { return locationName; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void applyMetadata(ValidatedProgressPhoto image) {
        this.geoVerificationStatus = image.geoVerificationStatus();
        this.geoVerificationMessage = image.geoVerificationMessage();
        this.captureTimeStatus = image.captureTimeStatus();
        this.gpsAltitude = image.gpsAltitude();
        this.gpsTimestamp = image.gpsTimestamp();
        this.cameraMake = image.cameraMake();
        this.cameraModel = image.cameraModel();
        this.imageWidth = image.imageWidth();
        this.imageHeight = image.imageHeight();
        this.exifPresent = image.exifPresent();
        this.gpsPresent = image.gpsPresent();
        this.imageSha256 = image.imageSha256();
    }
    public GeoVerificationStatus getGeoVerificationStatus() { return geoVerificationStatus; }
    public String getGeoVerificationMessage() { return geoVerificationMessage; }
    public String getCaptureTimeStatus() { return captureTimeStatus; }
    public BigDecimal getGpsAltitude() { return gpsAltitude; }
    public Instant getGpsTimestamp() { return gpsTimestamp; }
    public String getCameraMake() { return cameraMake; }
    public String getCameraModel() { return cameraModel; }
    public Integer getImageWidth() { return imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public Boolean getExifPresent() { return exifPresent; }
    public Boolean getGpsPresent() { return gpsPresent; }
    public String getImageSha256() { return imageSha256; }
}
