package edu.institution.ims.opportunity;

import java.math.BigDecimal;
import java.util.List;

public record StudentOpportunityMatchResponse(Long opportunityId, Eligibility eligibility, String semanticStatus,
        String semanticMessage, String profileNotice, SemanticEvidence evidence, Explanation explanation) {
    public record Eligibility(String overall, List<Criterion> criteria) {}
    public record Criterion(String type, String status, String requirement, String studentValue, String message) {}
    public record SemanticEvidence(String modelName, String modelVersion, BigDecimal relatedSkillThreshold,
            List<OpportunityMatchResult.SkillMatch> requiredSkills, List<OpportunityMatchResult.SkillMatch> preferredSkills,
            List<OpportunityMatchResult.Evidence> roleEvidence, List<OpportunityMatchResult.Evidence> experienceEvidence) {}
    public record Explanation(String summary, List<SkillExplanation> alignedSkills,
            List<SkillExplanation> skillsToStrengthen, List<SkillExplanation> additionalPreferredSkills) {}
    public record SkillExplanation(String skill, String matchType, String studentSkill, String detail) {}
}
