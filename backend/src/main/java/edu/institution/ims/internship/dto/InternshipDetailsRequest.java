package edu.institution.ims.internship.dto;

import edu.institution.ims.internship.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InternshipDetailsRequest(
        @Size(max = 200) String companyName,
        CompanyType companyType,
        @Size(max = 100) String otherCompanyType,
        @Size(max = 1000) String companyAddress,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Company phone number format is invalid") String companyPhone,
        @Size(max = 150) String industrySupervisorName,
        @Size(max = 150) String industrySupervisorDesignation,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Industry supervisor contact format is invalid") String industrySupervisorContact,
        @Positive(message = "Duration in months must be greater than zero") Integer durationMonths,
        LocalDate startDate,
        LocalDate endDate,
        // Retained for older clients; the server calculates this from the dates.
        Integer totalWeeks,
        InternshipMode internshipMode,
        @DecimalMin(value = "0.00", message = "Stipend cannot be negative") @Digits(integer = 10, fraction = 2, message = "Stipend must have at most two decimal places") BigDecimal stipendPerMonth,
        Boolean fullTimeEmploymentOffered,
        @jakarta.validation.Valid RegisteredWorkplaceRequest workplace,
        @Min(value = 1, message = "Working days per week must be between 1 and 7") @Max(value = 7, message = "Working days per week must be between 1 and 7") Integer workingDaysPerWeek,
        java.util.List<String> workingDays
) {
    public InternshipDetailsRequest(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone, String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact, Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, InternshipMode internshipMode, BigDecimal stipendPerMonth, Boolean fullTimeEmploymentOffered) {
        this(companyName,companyType,otherCompanyType,companyAddress,companyPhone,industrySupervisorName,industrySupervisorDesignation,industrySupervisorContact,durationMonths,startDate,endDate,totalWeeks,internshipMode,stipendPerMonth,fullTimeEmploymentOffered,null,null,null);
    }
    public InternshipDetailsRequest(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone, String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact, Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, InternshipMode internshipMode, BigDecimal stipendPerMonth, Boolean fullTimeEmploymentOffered, RegisteredWorkplaceRequest workplace) {
        this(companyName,companyType,otherCompanyType,companyAddress,companyPhone,industrySupervisorName,industrySupervisorDesignation,industrySupervisorContact,durationMonths,startDate,endDate,totalWeeks,internshipMode,stipendPerMonth,fullTimeEmploymentOffered,workplace,null,null);
    }
}
