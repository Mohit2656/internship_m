package edu.institution.ims.opportunity;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface OpportunitySkillRepository extends JpaRepository<OpportunitySkill, Long> {
    List<OpportunitySkill> findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(Long opportunityId);
    List<OpportunitySkill> findAllByOpportunityIdInOrderByRequirementTypeAscDisplayNameAsc(Collection<Long> opportunityIds);
    List<OpportunitySkill> findAllByOpportunityIdInAndRequirementTypeOrderByDisplayNameAsc(Collection<Long> opportunityIds, OpportunitySkillType requirementType);
    Optional<OpportunitySkill> findByOpportunityIdAndRequirementTypeAndNormalizedName(Long opportunityId, OpportunitySkillType requirementType, String normalizedName);

    @Query("select distinct s.displayName from OpportunitySkill s where lower(s.normalizedName) = lower(:normalizedName)")
    List<String> findDisplayNamesByNormalizedName(@Param("normalizedName") String normalizedName);

    @Modifying
    @Query("delete from OpportunitySkill s where s.opportunity.id = :opportunityId")
    void deleteAllByOpportunityId(@Param("opportunityId") Long opportunityId);
}
