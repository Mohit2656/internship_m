package edu.institution.ims.opportunity;

import java.time.Instant;
import java.util.List;

public record OpportunityAiAnalysisResponse(Long analysisId, Long opportunityId, String status, boolean stale,
        String modelName, String modelVersion, String safeFailureReason, Instant startedAt, Instant completedAt,
        List<Suggestion> requiredSuggestions, List<Suggestion> preferredSuggestions,
        List<String> roleSignals, List<String> responsibilitySignals) {
    public record Suggestion(Long id, String displayName, String normalizedName, OpportunityAiReviewStatus reviewStatus) {}
    static OpportunityAiAnalysisResponse notAnalyzed(Long opportunityId) {
        return new OpportunityAiAnalysisResponse(null, opportunityId, "NOT_ANALYZED", false, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }
    static OpportunityAiAnalysisResponse stale(OpportunityAiAnalysis analysis) {
        return new OpportunityAiAnalysisResponse(analysis.getId(), analysis.getOpportunity().getId(), "STALE", true,
                analysis.getModelName(), analysis.getModelVersion(), null, analysis.getStartedAt(), analysis.getCompletedAt(),
                List.of(), List.of(), List.of(), List.of());
    }
    static OpportunityAiAnalysisResponse from(OpportunityAiAnalysis analysis) {
        List<Suggestion> required = analysis.getSuggestions().stream().filter(x -> x.getSuggestedRequirementType() == OpportunitySkillType.REQUIRED)
                .map(x -> new Suggestion(x.getId(), x.getDisplayName(), x.getNormalizedName(), x.getReviewStatus())).toList();
        List<Suggestion> preferred = analysis.getSuggestions().stream().filter(x -> x.getSuggestedRequirementType() == OpportunitySkillType.PREFERRED)
                .map(x -> new Suggestion(x.getId(), x.getDisplayName(), x.getNormalizedName(), x.getReviewStatus())).toList();
        List<String> roles = analysis.getSignals().stream().filter(x -> x.getSignalType() == OpportunityAiSignalType.ROLE).map(OpportunityAiSignal::getDisplayValue).toList();
        List<String> responsibilities = analysis.getSignals().stream().filter(x -> x.getSignalType() == OpportunityAiSignalType.RESPONSIBILITY).map(OpportunityAiSignal::getDisplayValue).toList();
        return new OpportunityAiAnalysisResponse(analysis.getId(), analysis.getOpportunity().getId(), analysis.getStatus().name(), false,
                analysis.getModelName(), analysis.getModelVersion(), analysis.getFailureReasonSafe(), analysis.getStartedAt(), analysis.getCompletedAt(),
                required, preferred, roles, responsibilities);
    }
}
