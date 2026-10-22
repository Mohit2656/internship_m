package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LateAttendancePermissionAuditRepository extends JpaRepository<LateAttendancePermissionAudit, Long> {}
