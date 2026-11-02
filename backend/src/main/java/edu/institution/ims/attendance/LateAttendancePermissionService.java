package edu.institution.ims.attendance;

import edu.institution.ims.security.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Compatibility boundary for the retired blanket permission API. Historical grants remain in storage. */
@Service
public class LateAttendancePermissionService {
    public boolean isEnabled(Long studentId, LocalDate date) { return false; }
    public Optional<LateAttendancePermission> activePermission(Long studentId, LocalDate date) { return Optional.empty(); }
    public List<LateAttendancePermissionResponse> forMentor(UserPrincipal principal) {
        requireMentor(principal);
        return List.of();
    }
    public LateAttendancePermissionResponse setForMentor(Long studentId, boolean enabled, UserPrincipal principal) {
        requireMentor(principal);
        throw new AttendanceException(HttpStatus.CONFLICT,"LATE_ATTENDANCE_REQUEST_REQUIRED",
                "Review the student's submitted late attendance request. Blanket attendance permissions are no longer available");
    }
    private static void requireMentor(UserPrincipal principal) {
        if (principal==null || !principal.active() || !"MENTOR".equals(principal.role()))
            throw new AttendanceException(HttpStatus.FORBIDDEN,"MENTOR_REQUIRED","Faculty mentor access is required");
    }
}
