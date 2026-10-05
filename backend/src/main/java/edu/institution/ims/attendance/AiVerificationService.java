package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;

@Service
public class AiVerificationService {
    private static final List<AiChallengeType> CHALLENGES = List.of(
            AiChallengeType.BLINK, AiChallengeType.TURN_HEAD_LEFT, AiChallengeType.TURN_HEAD_RIGHT);
    private final AttendanceVerificationSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final UserRepository users;
    private final Clock clock;
    private final Duration sessionDuration;
    private final SecureRandom random = new SecureRandom();

    public AiVerificationService(AttendanceVerificationSessionRepository sessions,
            AttendanceRepository attendance, AttendancePolicyService policies, UserRepository users,
            Clock clock, @Value("${app.attendance.ai-session-duration-seconds:180}") long durationSeconds) {
        if (durationSeconds < 60 || durationSeconds > 300) {
            throw new IllegalArgumentException("AI verification session duration must be between 60 and 300 seconds");
        }
        this.sessions = sessions;
        this.attendance = attendance;
        this.policies = policies;
        this.users = users;
        this.clock = clock;
        this.sessionDuration = Duration.ofSeconds(durationSeconds);
    }

    @Transactional
    public AiVerificationSessionResponse create(UserPrincipal principal) {
        User student = student(principal.id());
        InternshipDetails internship = policies.eligibleInternship(student.getId());
        LocalDate today = LocalDate.now(clock);
        requireWithinInternship(internship, today);
        AttendanceVerificationMethod method = policies.resolve(internship, today).verificationMethod();
        if (method != AttendanceVerificationMethod.PHYSICAL && method != AttendanceVerificationMethod.REMOTE) {
            throw error(HttpStatus.CONFLICT, "EDGE_AI_NOT_APPLICABLE",
                    "Edge verification is not available for today's attendance policy");
        }
        if (attendance.findByStudentIdAndAttendanceDate(student.getId(), today).isPresent()) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED",
                    "Today's attendance has already been submitted");
        }
        Instant now = clock.instant();
        AiChallengeType challenge = CHALLENGES.get(random.nextInt(CHALLENGES.size()));
        AttendanceVerificationSession session = sessions.saveAndFlush(new AttendanceVerificationSession(
                UUID.randomUUID().toString(), student, internship, today, challenge, now, now.plus(sessionDuration)));
        return new AiVerificationSessionResponse(session.getId(), challenge, session.getExpiresAt());
    }

    @Transactional
    public AiVerificationDecision evaluate(UserPrincipal principal, InternshipDetails internship,
            LocalDate attendanceDate, AiVerificationEvidenceRequest request) {
        if (request == null) return AiVerificationDecision.missing();
        AttendanceVerificationSession session = sessions.findForUpdateById(request.sessionId())
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "INVALID_AI_SESSION",
                        "The attendance verification session is invalid"));
        if (!session.getStudent().getId().equals(principal.id())) {
            throw error(HttpStatus.FORBIDDEN, "AI_SESSION_STUDENT_MISMATCH",
                    "The attendance verification session belongs to another student");
        }
        if (!session.getInternship().getId().equals(internship.getId())
                || !session.getAttendanceDate().equals(attendanceDate)) {
            throw error(HttpStatus.CONFLICT, "AI_SESSION_CONTEXT_MISMATCH",
                    "The attendance verification session does not match today's internship");
        }
        if (session.getConsumedAt() != null) {
            throw error(HttpStatus.CONFLICT, "AI_SESSION_ALREADY_CONSUMED",
                    "The attendance verification session has already been used");
        }
        if (session.getChallengeType() != request.challenge()) {
            throw error(HttpStatus.BAD_REQUEST, "AI_CHALLENGE_MISMATCH",
                    "The submitted liveness challenge does not match the server challenge");
        }

        Instant now = clock.instant();
        boolean expired = !now.isBefore(session.getExpiresAt());
        session.consume(now);
        sessions.saveAndFlush(session);

        boolean faceDetected = request.faceCount() > 0;
        boolean singleFace = request.faceCount() == 1;
        VerificationReason reason = expired ? VerificationReason.EDGE_AI_SESSION_EXPIRED
                : !faceDetected ? VerificationReason.EDGE_AI_FACE_NOT_DETECTED
                : !singleFace ? VerificationReason.EDGE_AI_MULTIPLE_FACES
                : !request.qualityPassed() ? VerificationReason.EDGE_AI_QUALITY_FAILED
                : !request.livenessPassed() ? VerificationReason.EDGE_AI_LIVENESS_FAILED
                : VerificationReason.EDGE_AI_VERIFIED;
        boolean accepted = reason == VerificationReason.EDGE_AI_VERIFIED;
        return new AiVerificationDecision(session, session.getChallengeType(), faceDetected, singleFace,
                request.qualityPassed(), request.livenessPassed(), accepted, request.modelVersion(),
                now, reason);
    }

    private User student(Long id) {
        return users.findById(id).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED",
                        "Only a student may create an attendance verification session"));
    }

    private static void requireWithinInternship(InternshipDetails internship, LocalDate date) {
        if (date.isBefore(internship.getStartDate()) || date.isAfter(internship.getEndDate())) {
            throw error(HttpStatus.CONFLICT, "ATTENDANCE_OUTSIDE_INTERNSHIP_PERIOD",
                    "Attendance is available only during the registered internship period");
        }
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
