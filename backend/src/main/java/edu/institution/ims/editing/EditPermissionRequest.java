package edu.institution.ims.editing;

import jakarta.validation.constraints.NotNull;

public record EditPermissionRequest(@NotNull EditPermissionScope scope, boolean enabled) {}
