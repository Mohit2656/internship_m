package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByStudentIdAndAttendanceDate(Long studentUserId, LocalDate attendanceDate);
    List<AttendanceRecord> findAllByStudentIdOrderByAttendanceDateDescServerSubmittedAtDesc(Long studentUserId);
    @EntityGraph(attributePaths = {"student", "internship"})
    List<AttendanceRecord> findAllByStudentIdInAndAttendanceDate(Collection<Long> studentUserIds, LocalDate attendanceDate);
}
