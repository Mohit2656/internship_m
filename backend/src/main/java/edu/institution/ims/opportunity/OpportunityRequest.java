package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OpportunityRequest(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @Size(max = 500) String companyWebsite,
        @Size(max = 200) String location,
        OpportunityMode internshipMode,
        @Min(1) @Max(60) Integer durationMonths,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal stipendAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter uppercase code") String currency,
        LocalDate applicationDeadline,
        @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2) BigDecimal minimumCgpa,
        @Size(max = 20) List<@NotNull Branch> eligibleBranches,
        @Size(max = 8) List<@Min(1) @Max(8) Integer> eligibleSemesters,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> requiredSkills,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> preferredSkills) {

    public OpportunityRequest {
        eligibleBranches = eligibleBranches == null ? List.of() : List.copyOf(eligibleBranches);
        eligibleSemesters = eligibleSemesters == null ? List.of() : List.copyOf(eligibleSemesters);
        requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
        preferredSkills = preferredSkills == null ? List.of() : List.copyOf(preferredSkills);
    }
}
