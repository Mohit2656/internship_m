package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record StudentOpportunityDetail(Long id, String companyName, String title, String description, String companyWebsite,
        OpportunityMode mode, String location, Integer durationMonths, BigDecimal stipendAmount, String currency,
        LocalDate applicationDeadline, BigDecimal minimumCgpa, List<Branch> eligibleBranches,
        List<Integer> eligibleSemesters, List<String> requiredSkills, List<String> preferredSkills) {}
