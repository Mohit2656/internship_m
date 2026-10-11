package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "opportunities", indexes = {
        @Index(name = "idx_opportunities_status", columnList = "status"),
        @Index(name = "idx_opportunities_company", columnList = "company_name"),
        @Index(name = "idx_opportunities_deadline", columnList = "application_deadline")
})
public class Opportunity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_name", nullable = false, length = 200) private String companyName;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "company_website", length = 500) private String companyWebsite;
    @Column(length = 200) private String location;
    @Enumerated(EnumType.STRING) @Column(name = "internship_mode", length = 20) private OpportunityMode internshipMode;
    @Column(name = "duration_months") private Integer durationMonths;
    @Column(name = "stipend_amount", nullable = false, precision = 12, scale = 2) private BigDecimal stipendAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "application_deadline") private LocalDate applicationDeadline;
    @Column(name = "minimum_cgpa", precision = 4, scale = 2) private BigDecimal minimumCgpa;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OpportunityStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "opportunity_eligible_branches", joinColumns = @JoinColumn(name = "opportunity_id"))
    @Column(name = "branch", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Branch> eligibleBranches = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "opportunity_eligible_semesters", joinColumns = @JoinColumn(name = "opportunity_id"))
    @Column(name = "semester", nullable = false)
    private Set<Integer> eligibleSemesters = new LinkedHashSet<>();

    protected Opportunity() {}

    Opportunity(User creator, OpportunityRequest request) {
        createdBy = creator; status = OpportunityStatus.DRAFT;
        createdAt = databaseTime(); updatedAt = createdAt; apply(request);
    }

    void apply(OpportunityRequest request) {
        companyName = request.companyName().trim(); title = request.title().trim();
        description = clean(request.description()); companyWebsite = clean(request.companyWebsite());
        location = clean(request.location()); internshipMode = request.internshipMode();
        durationMonths = request.durationMonths(); stipendAmount = request.stipendAmount();
        currency = request.currency().trim(); applicationDeadline = request.applicationDeadline();
        minimumCgpa = request.minimumCgpa();
        eligibleBranches.clear(); eligibleBranches.addAll(request.eligibleBranches());
        eligibleSemesters.clear(); eligibleSemesters.addAll(request.eligibleSemesters());
        updatedAt = databaseTime();
    }

    void publish() { status = OpportunityStatus.PUBLISHED; publishedAt = databaseTime(); updatedAt = publishedAt; }
    void close() { status = OpportunityStatus.CLOSED; closedAt = databaseTime(); updatedAt = closedAt; }
    // PostgreSQL TIMESTAMP(6) retains microseconds; responses must match subsequent reads.
    private static Instant databaseTime() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCompanyWebsite() { return companyWebsite; }
    public String getLocation() { return location; }
    public OpportunityMode getInternshipMode() { return internshipMode; }
    public Integer getDurationMonths() { return durationMonths; }
    public BigDecimal getStipendAmount() { return stipendAmount; }
    public String getCurrency() { return currency; }
    public LocalDate getApplicationDeadline() { return applicationDeadline; }
    public BigDecimal getMinimumCgpa() { return minimumCgpa; }
    public OpportunityStatus getStatus() { return status; }
    public Long getCreatedById() { return createdBy.getId(); }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Branch> getEligibleBranches() { return Set.copyOf(eligibleBranches); }
    public Set<Integer> getEligibleSemesters() { return Set.copyOf(eligibleSemesters); }
}
