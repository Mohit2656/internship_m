package edu.institution.ims.mentor;

import java.time.Instant;

public record MentorAccountResponse(
        Long id, Long userId, String name, String employeeId, String instituteEmail,
        String department, String designation, String phone, boolean active,
        boolean mustChangePassword, long assignedStudentCount, Instant createdAt
) {}
