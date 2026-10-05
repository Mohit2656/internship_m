package edu.institution.ims.opportunity;

import java.util.List;

public record StudentSkillGapResponse(String status, String message, int opportunitiesAnalyzed,
        List<Gap> skillsToStrengthen, List<Strength> existingStrengths, List<Related> relatedSkills) {
    public record Gap(String skill, int requiredByCount, int preferredByCount) {}
    public record Strength(String skill, int exactMatchCount) {}
    public record Related(String studentSkill, String opportunitySkill, int opportunityCount) {}
}
