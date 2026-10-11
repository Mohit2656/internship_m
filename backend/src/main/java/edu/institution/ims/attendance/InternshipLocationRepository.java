package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InternshipLocationRepository extends JpaRepository<InternshipLocation, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select l from InternshipLocation l where l.internship.id = :internshipId")
    Optional<InternshipLocation> findForVerification(@org.springframework.data.repository.query.Param("internshipId") Long internshipId);

    @EntityGraph(attributePaths = {"internship", "confirmedBy"})
    Optional<InternshipLocation> findByInternshipId(Long internshipId);
}
