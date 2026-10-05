package edu.institution.ims.attendance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AttendanceVerificationSessionRepository
        extends JpaRepository<AttendanceVerificationSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AttendanceVerificationSession session where session.id = :id")
    Optional<AttendanceVerificationSession> findForUpdateById(@Param("id") String id);
}

