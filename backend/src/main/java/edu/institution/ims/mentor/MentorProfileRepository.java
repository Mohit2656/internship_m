package edu.institution.ims.mentor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {
    boolean existsByEmployeeIdIgnoreCase(String employeeId);
    @EntityGraph(attributePaths = "user") List<MentorProfile> findAllByOrderByNameAsc();
    @EntityGraph(attributePaths = "user") Optional<MentorProfile> findByUserId(Long userId);
}
