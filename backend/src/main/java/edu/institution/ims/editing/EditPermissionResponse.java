package edu.institution.ims.editing;

import java.time.Instant;

public record EditPermissionResponse(EditPermissionScope scope, boolean internshipStarted,
        boolean editable, String status, Instant expiresAt) {}
