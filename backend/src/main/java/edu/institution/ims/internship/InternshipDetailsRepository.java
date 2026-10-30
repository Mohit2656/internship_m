package edu.institution.ims.internship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InternshipDetailsRepository extends JpaRepository<InternshipDetails, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InternshipDetails i where i.onboarding.student.id = :studentId")
    Optional<InternshipDetails> findForUpdateByStudentId(Long studentId);
    @EntityGraph(attributePaths = {"onboarding", "onboarding.student"})
    @Query("select i from InternshipDetails i where i.startDate <= :through "
            + "and i.onboarding.workflowStatus = edu.institution.ims.internship.WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE "
            + "and i.onboarding.student.active = true "
            + "and i.onboarding.student.role = edu.institution.ims.user.Role.STUDENT")
    List<InternshipDetails> findEligibleForAbsenceProcessing(LocalDate through);
    Optional<InternshipDetails> findByOnboardingStudentId(Long studentId);
    Optional<InternshipDetails> findByOnboardingId(Long onboardingId);
    boolean existsByOnboardingId(Long onboardingId);
    @EntityGraph(attributePaths = {"onboarding", "onboarding.student"})
    List<InternshipDetails> findAllByOnboardingStudentIdIn(Collection<Long> studentIds);
}
