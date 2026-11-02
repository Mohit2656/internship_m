package edu.institution.ims.attendance;

import java.time.Instant;

public record AttendanceAcknowledgementResponse(Long mentorId, Instant seenAt) {}
