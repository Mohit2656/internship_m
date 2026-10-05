package edu.institution.ims.attendance;

public record MentorStudentAttendanceResponse(
        Long studentUserId,
        String name,
        String uid,
        String branchLabel,
        Integer semester,
        String companyName,
        String internshipMode,
        String internshipModeLabel,
        HybridWorkMode todayWorkMode,
        AttendanceResponse todayAttendance
) {}
