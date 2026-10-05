package edu.institution.ims.opportunity;

import java.util.List;

public record AdminSkillGapResponse(String status, String message, int candidatesAnalyzed,
        List<Gap> requiredGaps, List<Gap> preferredGaps) {
    public record Gap(String skill, int studentsMissing) {}
}
