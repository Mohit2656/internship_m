package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InternshipLocationRepository extends JpaRepository<InternshipLocation, Long> {
    @EntityGraph(attributePaths = {"internship", "confirmedBy"})
    Optional<InternshipLocation> findByInternshipId(Long internshipId);
}
