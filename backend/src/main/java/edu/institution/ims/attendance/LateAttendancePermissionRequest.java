package edu.institution.ims.attendance;

import jakarta.validation.constraints.NotNull;

public record LateAttendancePermissionRequest(@NotNull Boolean enabled) {}
