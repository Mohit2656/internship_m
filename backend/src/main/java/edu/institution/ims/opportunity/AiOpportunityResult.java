package edu.institution.ims.opportunity;

import java.util.*;

public record AiOpportunityResult(String modelName, String modelVersion, List<AiSkill> technicalSkills,
        List<AiSkill> requiredSkillSuggestions, List<AiSkill> preferredSkillSuggestions,
        List<String> roleSignals, List<String> responsibilitySignals) {
    public record AiSkill(String name, String normalizedName) {}

    AiOpportunityResult validated() {
        if (blank(modelName) || blank(modelVersion) || technicalSkills == null || requiredSkillSuggestions == null
                || preferredSkillSuggestions == null || roleSignals == null || responsibilitySignals == null)
            throw new IllegalArgumentException("Invalid opportunity AI response");
        validateSkills(technicalSkills); validateSkills(requiredSkillSuggestions); validateSkills(preferredSkillSuggestions);
        roleSignals.forEach(AiOpportunityResult::validateSignal); responsibilitySignals.forEach(AiOpportunityResult::validateSignal);
        return this;
    }
    private static void validateSkills(List<AiSkill> values) {
        if (values.size() > 100) throw new IllegalArgumentException("Too many AI skill candidates");
        values.forEach(value -> { if (value == null || blank(value.name()) || value.name().length() > 100) throw new IllegalArgumentException("Invalid AI skill"); });
    }
    private static void validateSignal(String value) { if (blank(value) || value.length() > 300) throw new IllegalArgumentException("Invalid AI signal"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
