package edu.institution.ims.attendance;

import java.util.List;

public record HybridScheduleResponse(Long internshipId, List<HybridScheduleEntryResponse> entries) {}
