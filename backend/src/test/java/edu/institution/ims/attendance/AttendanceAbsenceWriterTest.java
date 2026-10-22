package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceAbsenceWriterTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOON = Instant.parse("2026-10-19T06:30:00Z");
    private static final long INTERNSHIP_ID = 10L;
    private AttendanceRepository attendance;
    private InternshipDetailsRepository internships;
    private AttendanceCalendarProperties calendar;
    private InternshipDetails internship;

    @BeforeEach
    void setup() {
        attendance = mock(AttendanceRepository.class);
        internships = mock(InternshipDetailsRepository.class);
        calendar = new AttendanceCalendarProperties();
        User student = new User("absence.writer@example.edu", "unused", Role.STUDENT, true);
        ReflectionTestUtils.setField(student, "id", 20L);
        InternshipOnboarding onboarding = new InternshipOnboarding(student);
        onboarding.confirmDiary();
        onboarding.markInternshipDetailsComplete();
        internship = new InternshipDetails(onboarding);
        ReflectionTestUtils.setField(internship, "id", INTERNSHIP_ID);
        internship.apply("Example Ltd", CompanyType.IT, null, "Test address", "1234567890",
                "Supervisor", "Manager", "1234567890", 1, MONDAY.minusDays(7), MONDAY.plusDays(7),
                4, InternshipMode.ONLINE, BigDecimal.ZERO, false);
        when(internships.findById(INTERNSHIP_ID)).thenReturn(Optional.of(internship));
    }

    @Test
    void createsDailyRecordWithExplicitAutomaticOriginAndNoVerificationEvidence() {
        assertThat(writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY)).isTrue();

        ArgumentCaptor<AttendanceRecord> saved = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendance).saveAndFlush(saved.capture());
        AttendanceRecord record = saved.getValue();
        record.createTimestamps();
        assertThat(record.getStudent().getId()).isEqualTo(20L);
        assertThat(record.getInternship().getId()).isEqualTo(INTERNSHIP_ID);
        assertThat(record.getAttendanceDate()).isEqualTo(MONDAY);
        assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.ABSENT);
        assertThat(record.getSubmissionType()).isEqualTo(AttendanceSubmissionType.AUTO_ABSENT);
        assertThat(record.getVerificationBasis()).isEqualTo(AttendanceVerificationBasis.AUTOMATIC);
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.NOT_APPLICABLE);
        assertThat(record.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(record.getEvidence()).isNull();
        assertThat(record.getCreatedAt()).isEqualTo(NOON);
        assertThat(record.getUpdatedAt()).isEqualTo(NOON);
        assertThat(record.getServerSubmittedAt()).isEqualTo(NOON);

        AttendanceResponse response = AttendanceService.response(record);
        assertThat(response.attendanceResult()).isEqualTo(AttendanceResult.ABSENT);
        assertThat(response.submissionType()).isEqualTo(AttendanceSubmissionType.AUTO_ABSENT);
        assertThat(response.verificationBasis()).isEqualTo(AttendanceVerificationBasis.AUTOMATIC);
        assertThat(response.livenessStatus()).isEqualTo(LivenessStatus.NOT_APPLICABLE);
        assertThat(response.evidence()).isNull();
    }

    @Test
    void preservesAnyExistingRecordIncludingSoftDeletedHistoryAndSubmittedEvidence() {
        when(attendance.countIncludingDeleted(INTERNSHIP_ID, MONDAY)).thenReturn(1L);

        assertThat(writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY)).isFalse();

        verify(attendance, never()).saveAndFlush(any());
        verify(attendance, never()).delete(any(AttendanceRecord.class));
    }

    @Test
    void rechecksSubmissionDeadlineBeforeInserting() {
        assertThat(writer(NOON.minusSeconds(1)).createIfMissing(INTERNSHIP_ID, MONDAY)).isFalse();
        assertThat(writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY.plusDays(1))).isFalse();
        verify(attendance, never()).saveAndFlush(any());
    }

    @Test
    void rechecksCalendarExclusionsAndInternshipEligibilityBeforeInserting() {
        calendar.getHolidays().add(MONDAY);
        assertThat(writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY)).isFalse();
        calendar.getHolidays().clear();
        ReflectionTestUtils.setField(internship.getOnboarding().getStudent(), "active", false);
        assertThat(writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY)).isFalse();
        verify(attendance, never()).saveAndFlush(any());
    }

    @Test
    void repeatedInsertDoesNotCreateAnotherRecord() {
        when(attendance.countIncludingDeleted(INTERNSHIP_ID, MONDAY)).thenReturn(0L, 1L);
        AttendanceAbsenceWriter writer = writer(NOON);

        assertThat(writer.createIfMissing(INTERNSHIP_ID, MONDAY)).isTrue();
        assertThat(writer.createIfMissing(INTERNSHIP_ID, MONDAY)).isFalse();

        verify(attendance, times(1)).saveAndFlush(any(AttendanceRecord.class));
    }

    @Test
    void propagatesDatabaseConflictToTheSchedulerWithoutUpdatingAnExistingRow() {
        when(attendance.saveAndFlush(any(AttendanceRecord.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent daily record"));

        assertThatThrownBy(() -> writer(NOON).createIfMissing(INTERNSHIP_ID, MONDAY))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(attendance, never()).save(any(AttendanceRecord.class));
    }

    private AttendanceAbsenceWriter writer(Instant instant) {
        Clock clock = Clock.fixed(instant, ZONE);
        AttendancePolicyService policies = new AttendancePolicyService(
                mock(HybridWorkScheduleRepository.class), internships, attendance, clock, mock(LateAttendancePermissionService.class));
        return new AttendanceAbsenceWriter(attendance, internships, policies, calendar, clock);
    }
}
