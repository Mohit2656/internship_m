package edu.institution.ims.opportunity;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OpportunityAiAnalysisService {
    private final OpportunityAiPersistenceService persistence;
    private final OpportunityAiClient client;
    public OpportunityAiAnalysisService(OpportunityAiPersistenceService persistence, OpportunityAiClient client) {
        this.persistence = persistence; this.client = client;
    }
    public OpportunityAiAnalysisResponse analyze(Long opportunityId) {
        OpportunityAiCommand command = persistence.begin(opportunityId);
        try {
            AiOpportunityResult result = client.analyze(command.title(), command.description()).validated();
            return persistence.complete(command.analysisId(), result);
        } catch (OpportunityAiClientException e) {
            persistence.fail(command.analysisId(), "AI_SERVICE_UNAVAILABLE");
            throw error("AI_SERVICE_UNAVAILABLE");
        } catch (IllegalArgumentException e) {
            persistence.fail(command.analysisId(), "INVALID_AI_RESPONSE");
            throw error("INVALID_AI_RESPONSE");
        } catch (RuntimeException e) {
            persistence.fail(command.analysisId(), "ANALYSIS_FAILED");
            throw error("OPPORTUNITY_ANALYSIS_FAILED");
        }
    }
    public OpportunityAiAnalysisResponse get(Long opportunityId) { return persistence.get(opportunityId); }
    public OpportunityAiAnalysisResponse confirm(Long opportunityId, Long suggestionId) { return persistence.confirm(opportunityId, suggestionId); }
    public OpportunityAiAnalysisResponse reject(Long opportunityId, Long suggestionId) { return persistence.reject(opportunityId, suggestionId); }
    public OpportunityAiAnalysisResponse confirmAll(Long opportunityId) { return persistence.confirmAll(opportunityId); }
    private static OpportunityException error(String code) {
        return new OpportunityException(HttpStatus.BAD_GATEWAY, code, "We couldn't analyze this job description. Try again.");
    }
}
