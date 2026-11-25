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
    @Column(name = "company_name", length = 200) private String companyName;
    @Enumerated(EnumType.STRING) @Column(name = "company_type", length = 30) private CompanyType companyType;
    @Column(name = "other_company_type", length = 100) private String otherCompanyType;
    @Column(name = "company_address", length = 1000) private String companyAddress;
    @Column(name = "company_phone", length = 25) private String companyPhone;
    @Column(name = "industry_supervisor_name", length = 150) private String industrySupervisorName;
    @Column(name = "industry_supervisor_designation", length = 150) private String industrySupervisorDesignation;
    @Column(name = "industry_supervisor_contact", length = 25) private String industrySupervisorContact;
    @Column(name = "duration_months") private Integer durationMonths;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "total_weeks") private Integer totalWeeks;
    @Enumerated(EnumType.STRING) @Column(name = "internship_mode", length = 30) private InternshipMode internshipMode;
    @Column(name = "stipend_per_month", precision = 12, scale = 2) private BigDecimal stipendPerMonth;
    @Column(name = "full_time_employment_offered") private Boolean fullTimeEmploymentOffered;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completion_status", nullable = false, length = 30) private String completionStatus = "IN_PROGRESS";
    @Column(name = "working_days_per_week") private Integer workingDaysPerWeek = 5;
    @Column(name = "working_days", length = 100) private String workingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @OneToOne(mappedBy="internship", fetch=FetchType.LAZY) private edu.institution.ims.attendance.InternshipLocation workplace;
    public edu.institution.ims.attendance.InternshipLocation getWorkplace() { return workplace; }
    public void attachWorkplace(edu.institution.ims.attendance.InternshipLocation workplace) { this.workplace = workplace; }
    protected InternshipDetails() {}
    public InternshipDetails(InternshipOnboarding onboarding) { this.onboarding = onboarding; }
    public void apply(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone,
            String supervisorName, String supervisorDesignation, String supervisorContact, Integer durationMonths, LocalDate startDate,
            LocalDate endDate, Integer totalWeeks, InternshipMode mode, BigDecimal stipend, Boolean fullTimeEmploymentOffered) {
        apply(companyName, companyType, otherCompanyType, companyAddress, companyPhone, supervisorName, supervisorDesignation, supervisorContact,
                durationMonths, startDate, endDate, totalWeeks, mode, stipend, fullTimeEmploymentOffered, 5, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
    }
    public void apply(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone,
            String supervisorName, String supervisorDesignation, String supervisorContact, Integer durationMonths, LocalDate startDate,
            LocalDate endDate, Integer totalWeeks, InternshipMode mode, BigDecimal stipend, Boolean fullTimeEmploymentOffered,
            Integer workingDaysPerWeek, String workingDays) {
        this.companyName = companyName; this.companyType = companyType; this.otherCompanyType = otherCompanyType;
        this.companyAddress = companyAddress; this.companyPhone = companyPhone; this.industrySupervisorName = supervisorName;
        this.industrySupervisorDesignation = supervisorDesignation; this.industrySupervisorContact = supervisorContact;
        this.durationMonths = durationMonths; this.startDate = startDate; this.endDate = endDate; this.totalWeeks = totalWeeks;
        this.internshipMode = mode; this.stipendPerMonth = stipend; this.fullTimeEmploymentOffered = fullTimeEmploymentOffered;
        this.workingDaysPerWeek = workingDaysPerWeek != null ? workingDaysPerWeek : 5;
        this.workingDays = workingDays != null && !workingDays.isBlank() ? workingDays : "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
        this.updatedAt = Instant.now();
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
    public Integer getDurationMonths() { return durationMonths; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getTotalWeeks() { return totalWeeks; }
    public InternshipMode getInternshipMode() { return internshipMode; }
    public BigDecimal getStipendPerMonth() { return stipendPerMonth; }
    public Boolean isFullTimeEmploymentOffered() { return fullTimeEmploymentOffered; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCompletionStatus() { return completionStatus; }
    public Integer getWorkingDaysPerWeek() { return workingDaysPerWeek; }
    public String getWorkingDays() { return workingDays; }

    public java.util.Set<DayOfWeek> getWorkingDaysSet() {
        if (workingDays == null || workingDays.trim().isEmpty()) {
            return java.util.Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        java.util.Set<DayOfWeek> set = new java.util.HashSet<>();
        for (String part : workingDays.split(",")) {
            String trimmed = part.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                try {
                    set.add(DayOfWeek.valueOf(trimmed));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return set.isEmpty() ? java.util.Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) : set;
    }

    public boolean isWorkingDay(LocalDate date) {
        return date != null && getWorkingDaysSet().contains(date.getDayOfWeek());
    }

    public java.util.List<String> getWorkingDaysList() {
        return getWorkingDaysSet().stream().sorted().map(Enum::name).toList();
    }

    public Integer getEffectiveWorkingDaysPerWeek() {
        return workingDaysPerWeek != null && workingDaysPerWeek > 0 ? workingDaysPerWeek : getWorkingDaysSet().size();
    }
}
