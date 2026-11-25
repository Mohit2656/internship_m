package edu.institution.ims.internship.dto;

import java.math.BigDecimal;
import java.time.*;

public record InternshipDetailsResponse(Long id, Long onboardingId, int internshipNumber, String companyName, String companyType,
        String companyTypeLabel, String otherCompanyType, String companyAddress, String companyPhone,
        String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact,
        Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, String internshipMode,
        String internshipModeLabel, BigDecimal stipendPerMonth, Boolean fullTimeEmploymentOffered,
        Instant createdAt, Instant updatedAt, RegisteredWorkplaceRequest workplace,
        Integer workingDaysPerWeek, java.util.List<String> workingDays) {
    public InternshipDetailsResponse(Long id, Long onboardingId, int internshipNumber, String companyName, String companyType,
            String companyTypeLabel, String otherCompanyType, String companyAddress, String companyPhone,
            String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact,
            Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, String internshipMode,
            String internshipModeLabel, BigDecimal stipendPerMonth, Boolean fullTimeEmploymentOffered,
            Instant createdAt, Instant updatedAt, RegisteredWorkplaceRequest workplace) {
        this(id, onboardingId, internshipNumber, companyName, companyType, companyTypeLabel, otherCompanyType, companyAddress, companyPhone,
                industrySupervisorName, industrySupervisorDesignation, industrySupervisorContact, durationMonths, startDate, endDate, totalWeeks,
                internshipMode, modeLabel(internshipMode), stipendPerMonth, fullTimeEmploymentOffered, createdAt, updatedAt, workplace, 5,
                java.util.List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"));
    }
    private static String modeLabel(String mode) {
        if (mode == null) return null;
        try { return edu.institution.ims.internship.InternshipMode.valueOf(mode).getLabel(); } catch (Exception e) { return mode; }
    }
}
