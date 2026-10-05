package edu.institution.ims.opportunity;

public interface OpportunityAiClient {
    AiOpportunityResult analyze(String title, String description);
}
