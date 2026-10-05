package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record OpportunityResponse(
        Long id, String companyName, String title, String description, String companyWebsite, String location,
        OpportunityMode internshipMode, Integer durationMonths, BigDecimal stipendAmount, String currency,
        LocalDate applicationDeadline, BigDecimal minimumCgpa, OpportunityStatus status, Long createdBy,
        Instant publishedAt, Instant closedAt, Instant createdAt, Instant updatedAt,
        List<Branch> eligibleBranches, List<Integer> eligibleSemesters,
        List<SkillResponse> requiredSkills, List<SkillResponse> preferredSkills) {

    public record SkillResponse(Long id, String displayName, String normalizedName) {
        static SkillResponse from(OpportunitySkill skill) {
            return new SkillResponse(skill.getId(), skill.getDisplayName(), skill.getNormalizedName());
        }
    }
}
