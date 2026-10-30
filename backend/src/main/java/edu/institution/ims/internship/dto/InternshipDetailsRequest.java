package edu.institution.ims.internship.dto;

import edu.institution.ims.internship.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InternshipDetailsRequest(
        @NotBlank(message = "Company name is required") @Size(max = 200) String companyName,
        @NotNull(message = "Company type is required") CompanyType companyType,
        @Size(max = 100) String otherCompanyType,
        @NotBlank(message = "Company address is required") @Size(max = 1000) String companyAddress,
        @NotBlank(message = "Company phone number is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Company phone number format is invalid") String companyPhone,
        @NotBlank(message = "Industry supervisor name is required") @Size(max = 150) String industrySupervisorName,
        @NotBlank(message = "Industry supervisor designation is required") @Size(max = 150) String industrySupervisorDesignation,
        @NotBlank(message = "Industry supervisor contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Industry supervisor contact format is invalid") String industrySupervisorContact,
        @NotNull(message = "Duration in months is required") @Positive(message = "Duration in months must be greater than zero") Integer durationMonths,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        // Retained for older clients; the server calculates this from the dates.
        Integer totalWeeks,
        @NotNull(message = "Internship mode is required") InternshipMode internshipMode,
        @NotNull(message = "Stipend per month is required") @DecimalMin(value = "0.00", message = "Stipend cannot be negative") @Digits(integer = 10, fraction = 2, message = "Stipend must have at most two decimal places") BigDecimal stipendPerMonth,
        @NotNull(message = "Full-time employment opportunity selection is required") Boolean fullTimeEmploymentOffered,
        @jakarta.validation.Valid RegisteredWorkplaceRequest workplace
) {
    public InternshipDetailsRequest(String companyName, CompanyType companyType, String otherCompanyType, String companyAddress, String companyPhone, String industrySupervisorName, String industrySupervisorDesignation, String industrySupervisorContact, Integer durationMonths, LocalDate startDate, LocalDate endDate, Integer totalWeeks, InternshipMode internshipMode, BigDecimal stipendPerMonth, Boolean fullTimeEmploymentOffered) {
        this(companyName,companyType,otherCompanyType,companyAddress,companyPhone,industrySupervisorName,industrySupervisorDesignation,industrySupervisorContact,durationMonths,startDate,endDate,totalWeeks,internshipMode,stipendPerMonth,fullTimeEmploymentOffered,null);
    }
}
