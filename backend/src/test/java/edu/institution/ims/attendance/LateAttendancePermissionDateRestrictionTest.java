package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LateAttendancePermissionDateRestrictionTest {

    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    // Nov 25, 2026 at 14:00 IST (after 12 noon cutoff)
    private final Instant nov25AfterNoon = Instant.parse("2026-11-25T08:30:00Z");
    private final LocalDate nov25 = LocalDate.of(2026, 11, 25);
    private final LocalDate nov26 = LocalDate.of(2026, 11, 26);

    private LateAttendancePermissionRepository permissions;
    private LateAttendancePermissionAuditRepository audit;
    private MentorStudentAssignmentRepository assignments;
    private InternshipDetailsRepository internships;
    private UserRepository users;
    private Clock clock;
    private LateAttendancePermissionService service;

    private User student;
    private User mentor;
    private MentorStudentAssignment assignment;
    private InternshipDetails internship1;
    private InternshipDetails internship2;

    @BeforeEach
    void setUp() {
        permissions = mock(LateAttendancePermissionRepository.class);
        audit = mock(LateAttendancePermissionAuditRepository.class);
        assignments = mock(MentorStudentAssignmentRepository.class);
        internships = mock(InternshipDetailsRepository.class);
        users = mock(UserRepository.class);
        clock = Clock.fixed(nov25AfterNoon, zone);

        service = new LateAttendancePermissionService(
                permissions, audit, assignments, internships, users, clock
        );

        student = mock(User.class);
        when(student.getId()).thenReturn(101L);
        when(student.getRole()).thenReturn(Role.STUDENT);
        when(student.isActive()).thenReturn(true);

        mentor = mock(User.class);
        when(mentor.getId()).thenReturn(501L);
        when(mentor.getRole()).thenReturn(Role.MENTOR);
        when(mentor.isActive()).thenReturn(true);

        assignment = mock(MentorStudentAssignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getStudent()).thenReturn(student);
        when(assignment.getMentor()).thenReturn(mentor);
        when(assignment.isActive()).thenReturn(true);

        internship1 = mock(InternshipDetails.class);
        when(internship1.getId()).thenReturn(11L);

        internship2 = mock(InternshipDetails.class);
        when(internship2.getId()).thenReturn(22L);
    }

    @Test
    @DisplayName("Permission granted for Nov 25 is valid only for Nov 25")
    void testPermissionValidOnlyForGrantedDate() {
        LateAttendancePermission permNov25 = new LateAttendancePermission(assignment, internship1, nov25, nov25AfterNoon);
        when(permissions.findByStudentIdAndInternshipIdAndAttendanceDate(101L, 11L, nov25))
                .thenReturn(Optional.of(permNov25));

        // On Nov 25, activePermission for Nov 25 succeeds
        Optional<LateAttendancePermission> active = service.activePermission(101L, 11L, nov25);
        assertThat(active).isPresent();
        assertThat(active.get().getAttendanceDate()).isEqualTo(nov25);
        assertThat(service.isEnabled(101L, 11L, nov25)).isTrue();

        // But permission for Nov 26 is NOT active
        Optional<LateAttendancePermission> activeNov26 = service.activePermission(101L, 11L, nov26);
        assertThat(activeNov26).isEmpty();
        assertThat(service.isEnabled(101L, 11L, nov26)).isFalse();
    }

    @Test
    @DisplayName("Permission for Nov 25 expires on Nov 26 and cannot be used on next day")
    void testPermissionExpiresWhenDayChangesToNextDay() {
        LateAttendancePermission permNov25 = new LateAttendancePermission(assignment, internship1, nov25, nov25AfterNoon);
        when(permissions.findByStudentIdAndInternshipIdAndAttendanceDate(101L, 11L, nov25))
                .thenReturn(Optional.of(permNov25));

        // Advance clock to Nov 26 09:00 IST
        Clock nov26Clock = Clock.fixed(Instant.parse("2026-11-26T03:30:00Z"), zone);
        LateAttendancePermissionService nov26Service = new LateAttendancePermissionService(
                permissions, audit, assignments, internships, users, nov26Clock
        );

        // On Nov 26, trying to use Nov 25 permission returns empty
        Optional<LateAttendancePermission> onNov26ForNov25 = nov26Service.activePermission(101L, 11L, nov25);
        assertThat(onNov26ForNov25).isEmpty();
        assertThat(nov26Service.isEnabled(101L, 11L, nov25)).isFalse();

        // Nov 26 has no permission granted
        Optional<LateAttendancePermission> onNov26ForNov26 = nov26Service.activePermission(101L, 11L, nov26);
        assertThat(onNov26ForNov26).isEmpty();
        assertThat(nov26Service.isEnabled(101L, 11L, nov26)).isFalse();
    }

    @Test
    @DisplayName("Permission granted for Internship 1 CANNOT authorize Internship 2")
    void testPermissionIsStrictlyInternshipSpecific() {
        LateAttendancePermission permInt1 = new LateAttendancePermission(assignment, internship1, nov25, nov25AfterNoon);
        when(permissions.findByStudentIdAndInternshipIdAndAttendanceDate(101L, 11L, nov25))
                .thenReturn(Optional.of(permInt1));
        when(permissions.findByStudentIdAndInternshipIdAndAttendanceDate(101L, 22L, nov25))
                .thenReturn(Optional.empty());

        // Internship 1 is authorized
        assertThat(service.activePermission(101L, 11L, nov25)).isPresent();
        assertThat(service.isEnabled(101L, 11L, nov25)).isTrue();

        // Internship 2 is NOT authorized
        assertThat(service.activePermission(101L, 22L, nov25)).isEmpty();
        assertThat(service.isEnabled(101L, 22L, nov25)).isFalse();
    }

    @Test
    @DisplayName("Disabled or revoked permission is never active")
    void testDisabledPermissionIsNotActive() {
        LateAttendancePermission perm = new LateAttendancePermission(assignment, internship1, nov25, nov25AfterNoon);
        perm.revoke();
        when(permissions.findByStudentIdAndInternshipIdAndAttendanceDate(101L, 11L, nov25))
                .thenReturn(Optional.of(perm));

        assertThat(service.activePermission(101L, 11L, nov25)).isEmpty();
        assertThat(service.isEnabled(101L, 11L, nov25)).isFalse();
    }

    @Test
    @DisplayName("AttendanceSubmissionWindow remains open all day and closes at midnight in Asia/Kolkata")
    void testSubmissionWindowCutoffInAsiaKolkata() {
        // 11:59 AM IST on Nov 25 -> open
        Clock beforeNoon = Clock.fixed(Instant.parse("2026-11-25T06:29:00Z"), zone);
        assertThat(AttendanceSubmissionWindow.isClosed(nov25, beforeNoon)).isFalse();

        // 12:01 PM IST on Nov 25 -> still open (no noon lock)
        Clock afterNoon = Clock.fixed(Instant.parse("2026-11-25T06:31:00Z"), zone);
        assertThat(AttendanceSubmissionWindow.isClosed(nov25, afterNoon)).isFalse();

        // 23:59 IST on Nov 25 -> still open
        Clock endOfDay = Clock.fixed(Instant.parse("2026-11-25T18:29:00Z"), zone);
        assertThat(AttendanceSubmissionWindow.isClosed(nov25, endOfDay)).isFalse();

        // 00:00 IST on Nov 26 -> closed for Nov 25
        Clock midnightNextDay = Clock.fixed(Instant.parse("2026-11-25T18:30:00Z"), zone);
        assertThat(AttendanceSubmissionWindow.isClosed(nov25, midnightNextDay)).isTrue();
    }
}
