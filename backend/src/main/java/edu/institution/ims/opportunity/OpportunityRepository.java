package edu.institution.ims.opportunity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.*;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findAllByOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Opportunity o where o.id = :id")
    Optional<Opportunity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o from Opportunity o
            where o.status = :status
              and (o.applicationDeadline is null or o.applicationDeadline >= :today)
              and (:search is null or lower(o.companyName) like lower(concat('%', :search, '%'))
                   or lower(o.title) like lower(concat('%', :search, '%')))
              and (:mode is null or o.internshipMode = :mode)
            """)
    Page<Opportunity> findActiveForStudents(@Param("status") OpportunityStatus status, @Param("today") LocalDate today,
            @Param("search") String search, @Param("mode") OpportunityMode mode, Pageable pageable);

    @Query("""
            select o from Opportunity o
            where o.id = :id and o.status = :status
              and (o.applicationDeadline is null or o.applicationDeadline >= :today)
            """)
    Optional<Opportunity> findActiveForStudentById(@Param("id") Long id, @Param("status") OpportunityStatus status,
            @Param("today") LocalDate today);

    @Query("""
            select o from Opportunity o
            where o.status = :status
              and (o.applicationDeadline is null or o.applicationDeadline >= :today)
            order by o.publishedAt desc, o.id desc
            """)
    List<Opportunity> findAllCurrentlyAvailable(@Param("status") OpportunityStatus status, @Param("today") LocalDate today);
}
