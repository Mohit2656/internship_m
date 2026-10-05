package edu.institution.ims.opportunity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface OpportunityAiAnalysisRepository extends JpaRepository<OpportunityAiAnalysis, Long> {
    Optional<OpportunityAiAnalysis> findFirstByOpportunityIdOrderByCreatedAtDesc(Long opportunityId);
    @Query("select distinct a from OpportunityAiAnalysis a left join fetch a.signals where a.opportunity.id in :opportunityIds order by a.createdAt desc")
    List<OpportunityAiAnalysis> findAllWithSignalsByOpportunityIdIn(@Param("opportunityIds") Collection<Long> opportunityIds);
    boolean existsByOpportunityIdAndStatus(Long opportunityId, OpportunityAiAnalysisStatus status);
}
