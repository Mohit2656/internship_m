package edu.institution.ims.attendance;

public record ResolvedAttendancePolicy(
        HybridWorkMode todayWorkMode,
        AttendanceVerificationMethod verificationMethod,
        boolean configurationReady
) {}
