package edu.institution.ims.monthlyprogress;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MonthlyProgressReportRepository extends JpaRepository<MonthlyProgressReport, Long> {
    @EntityGraph(attributePaths = {"studentInternship", "studentInternship.onboarding", "studentInternship.onboarding.student", "studentInternship.onboarding.student.studentProfile", "studentInternship.onboarding.student.studentAccount"})
    Optional<MonthlyProgressReport> findById(Long id);

    @EntityGraph(attributePaths = {"studentInternship", "studentInternship.onboarding", "studentInternship.onboarding.student", "studentInternship.onboarding.student.studentProfile", "studentInternship.onboarding.student.studentAccount"})
    List<MonthlyProgressReport> findAllByStudentInternshipIdOrderByReportMonthNumberAsc(Long internshipId);

    @EntityGraph(attributePaths = {"studentInternship", "studentInternship.onboarding", "studentInternship.onboarding.student", "studentInternship.onboarding.student.studentProfile", "studentInternship.onboarding.student.studentAccount"})
    List<MonthlyProgressReport> findAllByStudentInternshipOnboardingStudentIdInOrderBySubmittedAtDesc(Collection<Long> studentIds);

    @Override
    @EntityGraph(attributePaths = {"studentInternship", "studentInternship.onboarding", "studentInternship.onboarding.student", "studentInternship.onboarding.student.studentProfile", "studentInternship.onboarding.student.studentAccount"})
    List<MonthlyProgressReport> findAll();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MonthlyProgressReport r where r.id = :id")
    Optional<MonthlyProgressReport> findForReviewById(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByStudentInternshipIdAndReportMonthNumber(Long internshipId, int monthNumber);
}
