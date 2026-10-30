package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    @Query("select r.student.id from AttendanceRecord r where r.id=:id")
    Optional<Long> findStudentIdForReview(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceRecord r where r.id=:id")
    Optional<AttendanceRecord> findForReviewUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @EntityGraph(attributePaths = {"internship", "internship.onboarding", "evidence", "review"})
    List<AttendanceRecord> findAllByStudentIdInOrderByAttendanceDateAscIdAsc(Collection<Long> studentIds);
    // Native queries intentionally include soft-deleted history: a mentor's deletion must not become an absence.
    @Query(value = "select attendance_date from attendance_records where internship_id = :internshipId", nativeQuery = true)
    List<java.sql.Date> findRecordedDatesIncludingDeleted(Long internshipId);
    @Query(value = "select count(*) from attendance_records where internship_id = :internshipId and attendance_date = :date", nativeQuery = true)
    long countIncludingDeleted(Long internshipId, LocalDate date);
    @EntityGraph(attributePaths = {"internship", "evidence", "review"})
    Optional<AttendanceRecord> findByStudentIdAndAttendanceDate(Long studentUserId, LocalDate attendanceDate);
    @EntityGraph(attributePaths = {"internship", "evidence", "review"})
    List<AttendanceRecord> findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(Long studentUserId);
    @EntityGraph(attributePaths = {"student", "internship", "evidence", "review"})
    List<AttendanceRecord> findAllByStudentIdInAndAttendanceDate(Collection<Long> studentUserIds, LocalDate attendanceDate);
}
