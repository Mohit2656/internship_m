package edu.institution.ims.internship.dto;

import java.math.BigDecimal;
import java.time.*;

public record InternshipDetailsResponse(Long id, Long onboardingId, String companyName, String companyType,
        String companyTypeLabel, String otherCompanyType, String companyAddress, String companyPhone,
        String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact,
        Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, String internshipMode,
        String internshipModeLabel, BigDecimal stipendPerMonth, boolean fullTimeEmploymentOffered,
        Instant createdAt, Instant updatedAt) {}
