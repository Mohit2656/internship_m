package edu.institution.ims.opportunity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface OpportunityAiSkillSuggestionRepository extends JpaRepository<OpportunityAiSkillSuggestion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OpportunityAiSkillSuggestion s join fetch s.analysis a join fetch a.opportunity where s.id = :id")
    Optional<OpportunityAiSkillSuggestion> findByIdForUpdate(@Param("id") Long id);
    List<OpportunityAiSkillSuggestion> findAllByAnalysisIdAndReviewStatusOrderBySuggestedRequirementTypeAscDisplayNameAsc(
            Long analysisId, OpportunityAiReviewStatus status);
}
