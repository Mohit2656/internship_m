package edu.institution.ims.opportunity;

import java.util.List;

public interface OpportunityMatchClient {
    OpportunityMatchResult match(OpportunityMatchRequest request);
    default List<OpportunityMatchResult> matchAll(List<OpportunityMatchRequest> requests) { return requests.stream().map(this::match).toList(); }
}
