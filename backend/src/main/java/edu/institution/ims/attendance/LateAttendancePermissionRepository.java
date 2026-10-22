package edu.institution.ims.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LateAttendancePermissionRepository extends JpaRepository<LateAttendancePermission, Long> {
    @EntityGraph(attributePaths = {"assignment", "authorizedBy"})
    Optional<LateAttendancePermission> findByStudentIdAndAttendanceDate(Long studentId, LocalDate attendanceDate);

    @EntityGraph(attributePaths = {"student", "assignment", "authorizedBy"})
    List<LateAttendancePermission> findAllByStudentIdInAndAttendanceDate(
            List<Long> studentIds, LocalDate attendanceDate);
}
