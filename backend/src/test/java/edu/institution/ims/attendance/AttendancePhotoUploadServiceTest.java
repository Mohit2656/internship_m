package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AttendancePhotoUploadServiceTest {
    private AttendanceRepository attendance;
    private AttendanceEvidenceRepository evidence;
    private InternshipDetailsRepository internships;
    private UserRepository users;
    private PhotoMetadataExtractor photoExtractor;
    private AttendancePolicyService policies;
    private Clock clock;
    private AttendanceSubmissionService submissions;
    private AttendanceLocationVerificationService locationVerification;
    private AttendancePhotoUploadService service;

    private User studentUser;
    private UserPrincipal studentPrincipal;
    private InternshipDetails internship;
    private InternshipOnboarding onboarding;
    private InternshipLocation workplace;

    @BeforeEach
    void setUp() {
        attendance = mock(AttendanceRepository.class);
        evidence = mock(AttendanceEvidenceRepository.class);
        internships = mock(InternshipDetailsRepository.class);
        users = mock(UserRepository.class);
        photoExtractor = mock(PhotoMetadataExtractor.class);
        policies = mock(AttendancePolicyService.class);
        clock = Clock.fixed(Instant.parse("2026-09-18T05:00:00Z"), ZoneId.of("Asia/Kolkata"));
        submissions = mock(AttendanceSubmissionService.class);
        locationVerification = mock(AttendanceLocationVerificationService.class);

        service = new AttendancePhotoUploadService(
                attendance, evidence, internships, users, photoExtractor, policies, clock, submissions, locationVerification
        );

        studentUser = mock(User.class);
        when(studentUser.getId()).thenReturn(10L);
        when(studentUser.getRole()).thenReturn(Role.STUDENT);

        studentPrincipal = new UserPrincipal(10L, "student@institution.edu", "hash", true, "STUDENT", false, 1L);
        when(users.findByIdForAttendanceUpdate(10L)).thenReturn(Optional.of(studentUser));

        onboarding = mock(InternshipOnboarding.class);
        when(onboarding.getId()).thenReturn(100L);
        when(onboarding.getWorkflowStatus()).thenReturn(WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);

        internship = new InternshipDetails(onboarding);
        when(internships.findByIdAndOnboardingStudentId(50L, 10L)).thenReturn(Optional.of(internship));

        workplace = mock(InternshipLocation.class);
        when(workplace.getLatitude()).thenReturn(new BigDecimal("18.520400"));
        when(workplace.getLongitude()).thenReturn(new BigDecimal("73.856700"));
        when(workplace.getGeofenceRadiusMeters()).thenReturn(new BigDecimal("200"));
        when(locationVerification.requireApprovedWorkplace(internship)).thenReturn(workplace);
    }

    @Test
    void rejectsPhotoCapturedOnDifferentDate() {
        MockMultipartFile photo = new MockMultipartFile("photo", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PhotoMetadata metadata = new PhotoMetadata(
                LocalDate.of(2026, 9, 17), // yesterday
                LocalTime.of(10, 30),
                new BigDecimal("18.520400"),
                new BigDecimal("73.856700")
        );
        when(photoExtractor.extractAndValidate(photo)).thenReturn(metadata);

        assertThatThrownBy(() -> service.submitUploadedPhoto(studentPrincipal, 50L, photo))
                .isInstanceOf(AttendanceException.class)
                .satisfies(e -> {
                    AttendanceException ae = (AttendanceException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("does not match today's attendance date");
                });
    }

    @Test
    void rejectsPhotoOutsideWorkplaceGeofence() {
        MockMultipartFile photo = new MockMultipartFile("photo", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        // Coords far away (~10 km)
        PhotoMetadata metadata = new PhotoMetadata(
                LocalDate.of(2026, 9, 18),
                LocalTime.of(10, 30),
                new BigDecimal("18.600000"),
                new BigDecimal("73.950000")
        );
        when(photoExtractor.extractAndValidate(photo)).thenReturn(metadata);

        assertThatThrownBy(() -> service.submitUploadedPhoto(studentPrincipal, 50L, photo))
                .isInstanceOf(AttendanceException.class)
                .satisfies(e -> {
                    AttendanceException ae = (AttendanceException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ae.getMessage()).contains("outside the approved workplace radius");
                });
    }

    @Test
    void acceptsValidUploadedPhotoAndMarksPresent() {
        MockMultipartFile photo = new MockMultipartFile("photo", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PhotoMetadata metadata = new PhotoMetadata(
                LocalDate.of(2026, 9, 18),
                LocalTime.of(10, 30),
                new BigDecimal("18.520400"),
                new BigDecimal("73.856700")
        );
        when(photoExtractor.extractAndValidate(photo)).thenReturn(metadata);

        AttendanceRecord record = new AttendanceRecord(
                studentUser, internship, LocalDate.of(2026, 9, 18),
                AttendanceResult.PENDING_MENTOR_REVIEW, VerificationStatus.VERIFIED,
                AttendanceSubmissionType.PHYSICAL_CAPTURE, clock.instant()
        );
        when(submissions.record(eq(studentUser), eq(internship), eq(LocalDate.of(2026, 9, 18)),
                eq(VerificationStatus.VERIFIED), eq(AttendanceSubmissionType.PHYSICAL_CAPTURE))).thenReturn(record);

        AttendanceResponse response = service.submitUploadedPhoto(studentPrincipal, 50L, photo);

        assertThat(response).isNotNull();
        assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
        verify(attendance).saveAndFlush(record);
        verify(submissions).notifyMentor(record);
    }
}
