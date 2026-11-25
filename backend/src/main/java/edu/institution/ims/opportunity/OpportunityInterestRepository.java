package edu.institution.ims.opportunity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OpportunityInterestRepository extends JpaRepository<OpportunityInterest, Long> {
    boolean existsByOpportunityIdAndStudentId(Long opportunityId, Long studentId);
    List<OpportunityInterest> findAllByStudentId(Long studentId);
    List<OpportunityInterest> findAllByOpportunityIdOrderByCreatedAtAscIdAsc(Long opportunityId);
}

