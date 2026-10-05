package edu.institution.ims.opportunity;

import java.math.BigDecimal;
import java.util.List;

public record OpportunityMatchResult(String modelName, String modelVersion, BigDecimal relatedSkillThreshold,
        List<SkillMatch> requiredSkillMatches, List<SkillMatch> preferredSkillMatches,
        List<Evidence> roleEvidence, List<Evidence> experienceEvidence) {
    public record SkillMatch(String opportunitySkill, String matchType, String studentSkill, BigDecimal semanticSimilarity) {}
    public record Evidence(String studentSignal, String opportunitySignal, BigDecimal semanticSimilarity) {}

    OpportunityMatchResult validated() {
        if (blank(modelName) || blank(modelVersion) || relatedSkillThreshold == null || requiredSkillMatches == null ||
                preferredSkillMatches == null || roleEvidence == null || experienceEvidence == null)
            throw new IllegalArgumentException("Invalid matching response");
        if (relatedSkillThreshold.compareTo(BigDecimal.ZERO) < 0 || relatedSkillThreshold.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("Invalid matching threshold");
        if (requiredSkillMatches.size() > 100 || preferredSkillMatches.size() > 100 || roleEvidence.size() > 20 || experienceEvidence.size() > 20)
            throw new IllegalArgumentException("Matching response is too large");
        requiredSkillMatches.forEach(OpportunityMatchResult::validateSkillMatch);
        preferredSkillMatches.forEach(OpportunityMatchResult::validateSkillMatch);
        roleEvidence.forEach(OpportunityMatchResult::validateEvidence);
        experienceEvidence.forEach(OpportunityMatchResult::validateEvidence);
        return this;
    }
    private static void validateSkillMatch(SkillMatch value) {
        if (value == null || blank(value.opportunitySkill()) || !("EXACT".equals(value.matchType()) || "RELATED".equals(value.matchType()) || "MISSING".equals(value.matchType())))
            throw new IllegalArgumentException("Invalid skill match");
        if ("MISSING".equals(value.matchType()) && (value.studentSkill() != null || value.semanticSimilarity() != null))
            throw new IllegalArgumentException("Invalid missing skill match");
        if (!"MISSING".equals(value.matchType()) && blank(value.studentSkill())) throw new IllegalArgumentException("Invalid matched skill");
        if (value.semanticSimilarity() != null && (value.semanticSimilarity().compareTo(BigDecimal.ZERO) < 0 || value.semanticSimilarity().compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException("Invalid semantic similarity");
    }
    private static void validateEvidence(Evidence value) {
        if (value == null || blank(value.studentSignal()) || blank(value.opportunitySignal()) || value.semanticSimilarity() == null)
            throw new IllegalArgumentException("Invalid matching evidence");
        if (value.semanticSimilarity().compareTo(BigDecimal.ZERO) < 0 || value.semanticSimilarity().compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("Invalid semantic similarity");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
