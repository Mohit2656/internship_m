package edu.institution.ims.attendance;

import java.time.DayOfWeek;

public record HybridScheduleEntryResponse(DayOfWeek dayOfWeek, HybridWorkMode workMode) {}
