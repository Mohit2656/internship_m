package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class OpportunityAiPersistenceService {
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository skills;
    private final OpportunityAiAnalysisRepository analyses;
    private final OpportunityAiSkillSuggestionRepository suggestions;
    private final SkillNormalizer normalizer;

    public OpportunityAiPersistenceService(OpportunityRepository opportunities, OpportunitySkillRepository skills,
            OpportunityAiAnalysisRepository analyses, OpportunityAiSkillSuggestionRepository suggestions, SkillNormalizer normalizer) {
        this.opportunities = opportunities; this.skills = skills; this.analyses = analyses; this.suggestions = suggestions; this.normalizer = normalizer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunityAiCommand begin(Long opportunityId) {
        Opportunity opportunity = opportunities.findByIdForUpdate(opportunityId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
        if (opportunity.getStatus() == OpportunityStatus.CLOSED)
            throw error(HttpStatus.CONFLICT, "OPPORTUNITY_CLOSED", "Closed opportunities cannot be analyzed");
        if (opportunity.getDescription() == null || opportunity.getDescription().isBlank())
            throw error(HttpStatus.CONFLICT, "JOB_DESCRIPTION_REQUIRED", "Add a job description before requesting analysis");
        if (analyses.existsByOpportunityIdAndStatus(opportunityId, OpportunityAiAnalysisStatus.PENDING))
            throw error(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS", "Job description analysis is already in progress");
        String hash = OpportunityAiFingerprint.of(opportunity);
        Optional<OpportunityAiAnalysis> latest = analyses.findFirstByOpportunityIdOrderByCreatedAtDesc(opportunityId);
        if (latest.isPresent() && latest.get().getStatus() == OpportunityAiAnalysisStatus.ANALYZED && latest.get().getInputHash().equals(hash))
            throw error(HttpStatus.CONFLICT, "ANALYSIS_ALREADY_CURRENT", "This job description already has a current analysis");
        OpportunityAiAnalysis analysis = analyses.saveAndFlush(new OpportunityAiAnalysis(opportunity, hash));
        return new OpportunityAiCommand(analysis.getId(), opportunityId, opportunity.getTitle(), opportunity.getDescription());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpportunityAiAnalysisResponse complete(Long analysisId, AiOpportunityResult result) {
        OpportunityAiAnalysis analysis = analyses.findById(analysisId).orElseThrow();
        Opportunity opportunity = analysis.getOpportunity();
        if (!OpportunityAiFingerprint.of(opportunity).equals(analysis.getInputHash()))
            throw new IllegalStateException("Job description changed during analysis");
        Set<String> seen = new HashSet<>();
        List<OpportunityAiSkillSuggestion> generated = new ArrayList<>();
        addSuggestions(generated, seen, analysis, opportunity, result.requiredSkillSuggestions(), OpportunitySkillType.REQUIRED);
        addSuggestions(generated, seen, analysis, opportunity, result.preferredSkillSuggestions(), OpportunitySkillType.PREFERRED);
        List<OpportunityAiSignal> signalValues = new ArrayList<>();
        addSignals(signalValues, analysis, OpportunityAiSignalType.ROLE, result.roleSignals());
        addSignals(signalValues, analysis, OpportunityAiSignalType.RESPONSIBILITY, result.responsibilitySignals());
        analysis.complete(result, generated, signalValues);
        return OpportunityAiAnalysisResponse.from(analyses.saveAndFlush(analysis));
    }

    private void addSuggestions(List<OpportunityAiSkillSuggestion> target, Set<String> seen, OpportunityAiAnalysis analysis,
            Opportunity opportunity, List<AiOpportunityResult.AiSkill> values, OpportunitySkillType type) {
        for (AiOpportunityResult.AiSkill value : values) {
            NormalizedSkill skill = normalizer.normalize(value.name());
            String key = type + ":" + skill.normalizedName();
            if (!seen.add(key) || skills.findByOpportunityIdAndRequirementTypeAndNormalizedName(opportunity.getId(), type, skill.normalizedName()).isPresent()) continue;
            target.add(new OpportunityAiSkillSuggestion(analysis, value.name(), skill, type));
        }
    }
    private void addSignals(List<OpportunityAiSignal> target, OpportunityAiAnalysis analysis, OpportunityAiSignalType type, List<String> values) {
        Set<String> seen = new HashSet<>(); int order = 0;
        for (String value : values) {
            String clean = value.trim().replaceAll("\\s+", " "); String key = clean.toLowerCase(Locale.ROOT);
            if (seen.add(key)) target.add(new OpportunityAiSignal(analysis, type, clean, order++));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long analysisId, String safeReason) {
        analyses.findById(analysisId).ifPresent(analysis -> { analysis.fail(safeReason); analyses.save(analysis); });
    }

    @Transactional(readOnly = true)
    public OpportunityAiAnalysisResponse get(Long opportunityId) {
        Opportunity opportunity = opportunities.findById(opportunityId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
        return analyses.findFirstByOpportunityIdOrderByCreatedAtDesc(opportunityId)
                .map(analysis -> currentResponse(opportunity, analysis)).orElseGet(() -> OpportunityAiAnalysisResponse.notAnalyzed(opportunityId));
    }

    @Transactional
    public OpportunityAiAnalysisResponse confirm(Long opportunityId, Long suggestionId) {
        OpportunityAiSkillSuggestion suggestion = ownCurrentSuggestion(opportunityId, suggestionId);
        if (suggestion.getReviewStatus() != OpportunityAiReviewStatus.PENDING) return currentResponse(suggestion.getAnalysis().getOpportunity(), suggestion.getAnalysis());
        confirmSuggestion(suggestion); suggestions.flush();
        return currentResponse(suggestion.getAnalysis().getOpportunity(), suggestion.getAnalysis());
    }

    @Transactional
    public OpportunityAiAnalysisResponse reject(Long opportunityId, Long suggestionId) {
        OpportunityAiSkillSuggestion suggestion = ownCurrentSuggestion(opportunityId, suggestionId);
        if (suggestion.getReviewStatus() == OpportunityAiReviewStatus.PENDING) { suggestion.reject(); suggestions.saveAndFlush(suggestion); }
        return currentResponse(suggestion.getAnalysis().getOpportunity(), suggestion.getAnalysis());
    }

    @Transactional
    public OpportunityAiAnalysisResponse confirmAll(Long opportunityId) {
        Opportunity opportunity = opportunities.findByIdForUpdate(opportunityId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
        OpportunityAiAnalysis analysis = currentAnalyzed(opportunity);
        List<OpportunityAiSkillSuggestion> pending = suggestions.findAllByAnalysisIdAndReviewStatusOrderBySuggestedRequirementTypeAscDisplayNameAsc(
                analysis.getId(), OpportunityAiReviewStatus.PENDING);
        pending.forEach(this::confirmSuggestion); suggestions.flush();
        return currentResponse(opportunity, analysis);
    }

    private void confirmSuggestion(OpportunityAiSkillSuggestion suggestion) {
        Opportunity opportunity = suggestion.getAnalysis().getOpportunity();
        OpportunitySkill skill = skills.findByOpportunityIdAndRequirementTypeAndNormalizedName(opportunity.getId(),
                suggestion.getSuggestedRequirementType(), suggestion.getNormalizedName())
                .orElseGet(() -> skills.saveAndFlush(new OpportunitySkill(opportunity,
                        new NormalizedSkill(suggestion.getDisplayName(), suggestion.getNormalizedName()), suggestion.getSuggestedRequirementType())));
        suggestion.confirm(skill); suggestions.save(suggestion);
    }
    private OpportunityAiSkillSuggestion ownCurrentSuggestion(Long opportunityId, Long suggestionId) {
        OpportunityAiSkillSuggestion suggestion = suggestions.findByIdForUpdate(suggestionId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "AI_SUGGESTION_NOT_FOUND", "AI suggestion was not found"));
        if (!suggestion.getAnalysis().getOpportunity().getId().equals(opportunityId))
            throw error(HttpStatus.NOT_FOUND, "AI_SUGGESTION_NOT_FOUND", "AI suggestion was not found");
        currentAnalyzed(suggestion.getAnalysis().getOpportunity());
        return suggestion;
    }
    private OpportunityAiAnalysis currentAnalyzed(Opportunity opportunity) {
        OpportunityAiAnalysis analysis = analyses.findFirstByOpportunityIdOrderByCreatedAtDesc(opportunity.getId())
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "ANALYSIS_NOT_AVAILABLE", "Analyze the current job description first"));
        if (!analysis.getInputHash().equals(OpportunityAiFingerprint.of(opportunity)))
            throw error(HttpStatus.CONFLICT, "ANALYSIS_STALE", "Job description changed. Re-analyze before reviewing suggestions");
        if (analysis.getStatus() != OpportunityAiAnalysisStatus.ANALYZED)
            throw error(HttpStatus.CONFLICT, "ANALYSIS_NOT_AVAILABLE", "A completed analysis is required");
        return analysis;
    }
    private OpportunityAiAnalysisResponse currentResponse(Opportunity opportunity, OpportunityAiAnalysis analysis) {
        return analysis.getInputHash().equals(OpportunityAiFingerprint.of(opportunity))
                ? OpportunityAiAnalysisResponse.from(analysis) : OpportunityAiAnalysisResponse.stale(analysis);
    }
    private static OpportunityException error(HttpStatus status, String code, String message) { return new OpportunityException(status, code, message); }
}
