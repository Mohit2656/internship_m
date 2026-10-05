package edu.institution.ims.internship;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InternshipOnboardingRepository extends JpaRepository<InternshipOnboarding, Long> {
    Optional<InternshipOnboarding> findByStudentId(Long studentId);
    boolean existsByStudentId(Long studentId);
}
