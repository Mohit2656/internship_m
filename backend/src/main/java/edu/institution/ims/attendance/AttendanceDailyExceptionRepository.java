package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceDailyExceptionRepository extends JpaRepository<AttendanceDailyException, Long> {

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    Optional<AttendanceDailyException> findByInternshipIdAndExceptionDateAndExceptionType(
            Long internshipId, LocalDate exceptionDate, AttendanceExceptionType exceptionType);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByInternshipIdAndExceptionDate(Long internshipId, LocalDate exceptionDate);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByInternshipIdOrderByExceptionDateDesc(Long internshipId);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByStudentIdOrderByExceptionDateDesc(Long studentUserId);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByInternshipIdInOrderByExceptionDateDesc(List<Long> internshipIds);

    boolean existsByInternshipIdAndExceptionDateAndExceptionType(
            Long internshipId, LocalDate exceptionDate, AttendanceExceptionType exceptionType);

    boolean existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
            Long internshipId, LocalDate exceptionDate, AttendanceExceptionType exceptionType, AttendanceExceptionStatus status);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByInternshipIdAndStatus(Long internshipId, AttendanceExceptionStatus status);

    @EntityGraph(attributePaths = {"internship", "student", "reviewedBy"})
    List<AttendanceDailyException> findAllByInternshipIdInAndStatus(List<Long> internshipIds, AttendanceExceptionStatus status);
}
