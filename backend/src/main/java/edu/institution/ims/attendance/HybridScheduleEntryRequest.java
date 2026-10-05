package edu.institution.ims.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record HybridScheduleEntryRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull HybridWorkMode workMode
) {}
