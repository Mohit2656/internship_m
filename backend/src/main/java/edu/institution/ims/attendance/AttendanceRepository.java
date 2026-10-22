package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    @EntityGraph(attributePaths = {"internship", "internship.onboarding"})
    List<AttendanceRecord> findAllByStudentIdInOrderByAttendanceDateAscIdAsc(Collection<Long> studentIds);
    // Native queries intentionally include soft-deleted history: a mentor's deletion must not become an absence.
    @Query(value = "select attendance_date from attendance_records where internship_id = :internshipId", nativeQuery = true)
    List<java.sql.Date> findRecordedDatesIncludingDeleted(Long internshipId);
    @Query(value = "select count(*) from attendance_records where internship_id = :internshipId and attendance_date = :date", nativeQuery = true)
    long countIncludingDeleted(Long internshipId, LocalDate date);
    Optional<AttendanceRecord> findByStudentIdAndAttendanceDate(Long studentUserId, LocalDate attendanceDate);
    List<AttendanceRecord> findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(Long studentUserId);
    @EntityGraph(attributePaths = {"student", "internship"})
    List<AttendanceRecord> findAllByStudentIdInAndAttendanceDate(Collection<Long> studentUserIds, LocalDate attendanceDate);
}
