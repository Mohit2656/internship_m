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
    @Column(name = "exif_latitude", precision = 9, scale = 6) private BigDecimal exifLatitude;
    @Column(name = "exif_longitude", precision = 10, scale = 6) private BigDecimal exifLongitude;
    @Column(name = "location_name", length = 255) private String locationName;
    @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

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
}
