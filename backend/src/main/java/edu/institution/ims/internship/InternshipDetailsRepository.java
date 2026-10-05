package edu.institution.ims.internship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InternshipDetailsRepository extends JpaRepository<InternshipDetails, Long> {
    Optional<InternshipDetails> findByOnboardingStudentId(Long studentId);
    Optional<InternshipDetails> findByOnboardingId(Long onboardingId);
    boolean existsByOnboardingId(Long onboardingId);
    @EntityGraph(attributePaths = {"onboarding", "onboarding.student"})
    List<InternshipDetails> findAllByOnboardingStudentIdIn(Collection<Long> studentIds);
}
