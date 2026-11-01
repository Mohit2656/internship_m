package edu.institution.ims.internship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface InternshipOnboardingRepository extends JpaRepository<InternshipOnboarding, Long> {
    @Query("select o from InternshipOnboarding o where o.student.id=:studentId and o.internshipNumber=1") Optional<InternshipOnboarding> findByStudentId(Long studentId);
    Optional<InternshipOnboarding> findByStudentIdAndInternshipNumber(Long studentId,short internshipNumber);
    List<InternshipOnboarding> findAllByStudentIdOrderByInternshipNumber(Long studentId);
    boolean existsByStudentId(Long studentId);
}
