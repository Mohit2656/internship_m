package edu.institution.ims.opportunity;

import edu.institution.ims.studentprofile.Branch;
import java.util.List;

public record TalentDiscoveryResponse(
        OpportunitySummary opportunity,
        String orderingExplanation,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Candidate> candidates) {

    public record OpportunitySummary(Long id, String companyName, String title, OpportunityStatus status) {}

    public record Candidate(
            Long studentId,
            String studentName,
            String uid,
            Branch branch,
            int semester,
            int exactRequiredSkills,
            int relatedRequiredSkills,
            int exactPreferredSkills,
            StudentOpportunityMatchResponse alignment) {}
}
