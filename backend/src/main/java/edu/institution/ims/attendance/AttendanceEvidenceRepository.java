package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AttendanceEvidenceRepository extends JpaRepository<AttendanceEvidence, Long> {
    Optional<AttendanceEvidence> findByAttendanceId(Long attendanceId);
}
