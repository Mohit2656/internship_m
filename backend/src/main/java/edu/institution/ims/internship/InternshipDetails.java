package edu.institution.ims.internship;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "internship_details", uniqueConstraints = @UniqueConstraint(name = "uk_internship_details_onboarding", columnNames = "onboarding_id"), indexes = {
        @Index(name = "idx_internship_details_company_name", columnList = "company_name"),
        @Index(name = "idx_internship_details_company_type", columnList = "company_type"),
        @Index(name = "idx_internship_details_mode", columnList = "internship_mode"),
        @Index(name = "idx_internship_details_dates", columnList = "start_date,end_date")
})
public class InternshipDetails {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "onboarding_id", nullable = false) private InternshipOnboarding onboarding;
    @Column(name = "company_name", nullable = false, length = 200) private String companyName;
    @Enumerated(EnumType.STRING) @Column(name = "company_type", nullable = false, length = 30) private CompanyType companyType;
    @Column(name = "other_company_type", length = 100) private String otherCompanyType;
    @Column(name = "company_address", nullable = false, length = 1000) private String companyAddress;
    @Column(name = "company_phone", nullable = false, length = 25) private String companyPhone;
    @Column(name = "industry_supervisor_name", nullable = false, length = 150) private String industrySupervisorName;
    @Column(name = "industry_supervisor_designation", nullable = false, length = 150) private String industrySupervisorDesignation;
    @Column(name = "industry_supervisor_contact", nullable = false, length = 25) private String industrySupervisorContact;
    @Column(name = "duration_months", nullable = false) private int durationMonths;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "total_weeks", nullable = false) private int totalWeeks;
    @Enumerated(EnumType.STRING) @Column(name = "internship_mode", nullable = false, length = 30) private InternshipMode internshipMode;
    @Column(name = "stipend_per_month", nullable = false, precision = 12, scale = 2) private BigDecimal stipendPerMonth;
    @Column(name = "full_time_employment_offered", nullable = false) private boolean fullTimeEmploymentOffered;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InternshipDetails() {}
    public InternshipDetails(InternshipOnboarding onboarding) { this.onboarding = onboarding; }
    public void apply(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone,
            String supervisorName, String supervisorDesignation, String supervisorContact, int durationMonths, LocalDate startDate,
            LocalDate endDate, int totalWeeks, InternshipMode mode, BigDecimal stipend, boolean fullTimeEmploymentOffered) {
        this.companyName = companyName; this.companyType = companyType; this.otherCompanyType = otherCompanyType;
        this.companyAddress = companyAddress; this.companyPhone = companyPhone; this.industrySupervisorName = supervisorName;
        this.industrySupervisorDesignation = supervisorDesignation; this.industrySupervisorContact = supervisorContact;
        this.durationMonths = durationMonths; this.startDate = startDate; this.endDate = endDate; this.totalWeeks = totalWeeks;
        this.internshipMode = mode; this.stipendPerMonth = stipend; this.fullTimeEmploymentOffered = fullTimeEmploymentOffered;
    }
    @PrePersist void createTimestamps() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public InternshipOnboarding getOnboarding() { return onboarding; }
    public String getCompanyName() { return companyName; }
    public CompanyType getCompanyType() { return companyType; }
    public String getOtherCompanyType() { return otherCompanyType; }
    public String getCompanyAddress() { return companyAddress; }
    public String getCompanyPhone() { return companyPhone; }
    public String getIndustrySupervisorName() { return industrySupervisorName; }
    public String getIndustrySupervisorDesignation() { return industrySupervisorDesignation; }
    public String getIndustrySupervisorContact() { return industrySupervisorContact; }
    public int getDurationMonths() { return durationMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getTotalWeeks() { return totalWeeks; }
    public InternshipMode getInternshipMode() { return internshipMode; }
    public BigDecimal getStipendPerMonth() { return stipendPerMonth; }
    public boolean isFullTimeEmploymentOffered() { return fullTimeEmploymentOffered; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
